package name.modid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import name.modid.api.*;
import name.modid.vision.VisionApi;
import net.minecraft.client.Minecraft;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
 * WebSocket 服务 —— 替换原有的 HTTP 通信。
 * 消息协议:
 *   请求 {"id":1, "method":"player", "params":{}}
 *   成功 {"id":1, "ok":true,  "data":{...}}
 *   失败 {"id":1, "ok":false, "error":"message"}
 */
public class AgentWebSocketServer extends WebSocketServer {

    private static final int PORT = 25550;
    /** 入站解析用（{@code fromJson}）。 */
    public static final Gson GSON = new Gson();

    /**
     * 出站序列化用：**开启 serializeNulls**。
     *
     * <p>Gson 默认会**丢弃值为 null 的 map 条目**——实测 gson 2.13.2：
     * {@code {levels:3, primaryEffect:null, after:"x"}} 序列化成 {@code {"levels":3,"after":"x"}}，
     * 键连影子都没有。而本项目需要同时表达两种不同的事实：
     * <ul>
     *   <li>**缺席 ＝ 未知**（客户端不知道）——靠**不 put 该键**（v2.42 起的既定口径）；</li>
     *   <li>**null ＝ 已知为无**（vanilla 自己算出来就是"没有"）——靠 put 该键、值为 null。</li>
     * </ul>
     * 默认序列化器只能表达前者，后者会被静默降级成"未知"，与项目口径直接冲突。
     * 首个真实用例：{@code container/get} 信标的 {@code primaryEffect}/{@code secondaryEffect}
     * （vanilla 用 0 编码"没选效果"）。
     *
     * <p>副作用（已知、且是**修正**）：{@code f3} 的 {@code target.block/fluid/entity} 三键
     * 本来就是被显式 put 成 null 的（{@code F3Api.java:145}，意图是"准星没指到"），
     * 此前一直被 Gson 吞掉、退化成"未知"；开启后它们会真的以 {@code null} 出现。
     */
    public static final Gson GSON_OUT = new GsonBuilder().serializeNulls().create();

    @FunctionalInterface
    public interface WsHandler {
        Object handle(Map<String, Object> params) throws Exception;
    }

    private static final Map<String, WsHandler> handlers = new LinkedHashMap<>();

    static {
        ContinuousApi.register(handlers);
        OneShotApi.register(handlers);
        CameraApi.register(handlers);
        PlayerApi.register(handlers);
        InventoryApi.register(handlers);
        F3Api.register(handlers);
        ProfilerApi.register(handlers);
        StatusApi.register(handlers);
        SoundApi.register(handlers);
        SettingsApi.register(handlers);
        ContainerApi.register(handlers);
        ChatApi.register(handlers);
        SignApi.register(handlers);
        BookApi.register(handlers);
        VisionApi.register(handlers);
    }

    /** 当前正在执行（尚未返回）的 API 方法名集合，用于 status/busy 上报 */
    private static final Set<String> busyMethods = ConcurrentHashMap.newKeySet();
    /** 最近一次请求失败的错误消息（status/error 上报），无失败则为空串 */
    private static volatile String lastError = "";

    public AgentWebSocketServer() {
        super(new InetSocketAddress(PORT));
        setReuseAddr(true);
    }

    public static void launch() {
        var server = new AgentWebSocketServer();
        server.start();
        SteveX.LOGGER.info("[Agent] WebSocket server started on port {}", PORT);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        SteveX.LOGGER.info("[Agent] WebSocket connected: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        SteveX.LOGGER.info("[Agent] WebSocket closed: {} (code={})", conn.getRemoteSocketAddress(), code);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        SteveX.LOGGER.error("[Agent] WebSocket error", ex);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = GSON.fromJson(message, Map.class);
            int id = ((Number) req.get("id")).intValue();
            String method = (String) req.get("method");
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) req.getOrDefault("params", Map.of());

            WsHandler handler = handlers.get(method);
            if (handler == null) {
                sendError(conn, id, "unknown method: " + method);
                return;
            }

            busyMethods.add(method);
            try {
                Object result = handler.handle(params);
                if (result == null) {
                    sendError(conn, id, "handler returned no result");
                    return;
                }
                // A successful non-status request supersedes the previous failure.
                // Keep status itself read-only so callers can inspect the last outcome.
                if (!"status".equals(method)) {
                    lastError = "";
                }
                send(conn, ok(id, result instanceof Map<?, ?> m ? m : Map.of("value", result)));
            } catch (Exception e) {
                SteveX.LOGGER.error("[Agent] Handler error for method {}: {}", method, e.getMessage());
                sendError(conn, id, e.getMessage());
            } finally {
                busyMethods.remove(method);
            }
        } catch (Exception e) {
            SteveX.LOGGER.error("[Agent] Failed to parse message: {}", message, e);
            sendError(conn, 0, "invalid message format");
        }
    }

    @Override
    public void onStart() {
        SteveX.LOGGER.info("[Agent] WebSocket server ready");
    }

    // ==================== Busy / error state ====================

    /** 当前是否有非 status 的 API 方法正在执行 */
    public static boolean isBusy() {
        return busyMethods.stream().anyMatch(m -> !"status".equals(m));
    }

    /** 最近一次请求失败的错误消息（无失败则为空串） */
    public static String lastError() {
        return lastError;
    }

    /** 发送错误响应并记录到 lastError（供 status API 上报） */
    private static void sendError(WebSocket conn, int id, String msg) {
        lastError = msg;
        send(conn, error(id, msg));
    }

    // ==================== 主线程查询辅助 ====================

    /**
     * 在 Minecraft 主线程执行一个动作并阻塞等待完成（最多 timeoutMs）。
     * 统一超时策略：超时打 warn 日志；动作内部无需手动 countDown，
     * 返回 ref.value（可能为 null，由调用方决定兜底或抛错）。
     */
    public static <T> T runOnClient(long timeoutMs, String what, Consumer<Ref<T>> action) {
        Ref<T> ref = new Ref<>();
        CountDownLatch latch = new CountDownLatch(1);
        Minecraft.getInstance().execute(() -> {
            try {
                action.accept(ref);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                SteveX.LOGGER.warn("[Agent] {} timed out after {} ms", what, timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.value;
    }

    /** 跨线程传递动作结果的简单容器 */
    public static final class Ref<T> {
        public T value;
    }

    // ==================== Response builders ====================

    private static void send(WebSocket conn, Map<String, Object> frame) {
        conn.send(GSON_OUT.toJson(frame));
    }

    private static Map<String, Object> ok(int id, Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("ok", true);
        resp.put("data", data);
        return resp;
    }

    private static Map<String, Object> error(int id, String msg) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", id);
        resp.put("ok", false);
        resp.put("error", msg);
        return resp;
    }

    // ==================== Param helpers ====================

    public static boolean bool(Map<String, Object> params, String key, boolean def) {
        Object v = params.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public static int num(Map<String, Object> params, String key, int def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public static double num(Map<String, Object> params, String key, double def) {
        Object v = params.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    // ==================== Formatters ====================

    public static String f1(double v) { return String.format("%.1f", v); }
    public static String f2(double v) { return String.format("%.2f", v); }
    public static String f3(double v) { return String.format("%.3f", v); }
}
