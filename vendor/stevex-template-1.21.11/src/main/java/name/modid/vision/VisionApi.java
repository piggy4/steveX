package name.modid.vision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import name.modid.AgentWebSocketServer;
import name.modid.AgentWebSocketServer.WsHandler;
import name.modid.SteveX;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 视觉系统 API —— 人工触发收集 + 保存。
 */
public class VisionApi {

    /** 视觉链路统一超时：深度采集等待 + 渲染线程 resolve/entity 查询（§8），毫秒 */
    private static final long SNAPSHOT_TIMEOUT_MS = 15_000;

    public static void register(final Map<String, WsHandler> handlers) {
        handlers.put("vision/snapshot", params -> snapshot());
        handlers.put("vision/entity", params -> entityQuery(params));
    }

    /**
     * 视觉快照端点 —— v2 Phase 2+（§6.2）：深度图元信息 + 可见对象 + store 统计。
     *
     * <p>接线（§8）：API 线程置采集标志 → 取回深度快照（含同帧实体快照）→ 建 SectionPos 桶 →
     * {@link Unprojector} 全量反投影去重 → {@code Minecraft.execute} 在渲染线程
     * {@link ObjectResolver#resolve} 四路查询 + 三 store 落盘 → latch → 组装 JSON。
     *
     * @return { "ok":true, "width", "height", "depthMin", "depthMax", "nonSkyPixels",
     *           "cameraPos", "timestamp",
     *           "visibleBlockCount", "blockEntityCount", "entityCount",
     *           "blockEntities":[ {pos, typeId, block, state, nbt} ],
     *           "entities":[ {id, uuid, type, pos, rotation, motion, onGround, health,
     *                        item?(v2.34 掉落物), content?(v2.35 展示实体薄摘要)} ],
     *           "storeStats":{ "terrain":{blocks}, "blockEntities":{new,updated,skipped},
     *                          "entities":{entities}, "biomes":{cells,added}(v2.31) } }
     */
    private static Map<String, Object> snapshot() {
        DepthCapture.requestCapture();
        final DepthCapture.DepthSnapshot snap;
        try {
            snap = DepthCapture.awaitSnapshot(SNAPSHOT_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("ok", false, "error", "interrupted");
        }
        if (snap == null) {
            String err = DepthCapture.lastError();
            return Map.of("ok", false, "error",
                    err != null ? err : "depth capture timeout (no frame rendered within 15s)");
        }

        // ---- API 线程：纯数据，无竞态（§8）----
        final long ts = System.currentTimeMillis();
        final Unprojector unproj = new Unprojector(snap);
        final var bucket = ObjectResolver.buildBucket(snap.entities());
        final Unprojector.UnprojectResult hits = unproj.visibleBlockHits(bucket.keySet(), Unprojector.EPSILON);
        // v2.23（§7.11）反向通道：读记忆侧 memory_cells.bin（mtime 门控）→ 待判定记忆格清单。
        // 记忆侧离线 / 文件缺失 → 空清单 → 无删除证据 → 只增不删（优雅降级）。
        final MemoryCellsReader.CellsData cells = new MemoryCellsReader().read();

        // ---- 渲染线程：四路查询 + 减量判定 + store 落盘（§8）----
        final var result = new Object() {
            ObjectResolver.ResolveResult value;
            String error;
        };
        final CountDownLatch latch = new CountDownLatch(1);
        Minecraft.getInstance().execute(() -> {
            try {
                net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
                if (level == null) {
                    result.error = "not in a world";
                } else {
                    result.value = ObjectResolver.resolve(level, snap, unproj, hits, bucket, cells, ts);
                }
            } catch (Exception e) {
                result.error = e.getMessage();
                SteveX.LOGGER.error("[Vision] resolve failed", e);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(SNAPSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return Map.of("ok", false, "error", "timeout waiting for resolve on render thread");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("ok", false, "error", "interrupted");
        }
        if (result.error != null) {
            return Map.of("ok", false, "error", result.error);
        }
        if (result.value == null) {
            return Map.of("ok", false, "error", "resolve produced no result");
        }

        // ---- 组装 JSON（§6.2）----
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float d : snap.depth()) {
            if (d < min) min = d;
            if (d > max) max = d;
        }

        // 诊断日志（INFO）：采集成功但对象为空时，据此区分「深度回读全天空」与「反投影/查询异常」。
        // nonSkyPixels=0 或 depthMin=depthMax=1.0 ⇒ 深度缓冲被读成空（全 1.0）。
        SteveX.LOGGER.info(
                "[Vision] snapshot: nonSkyPixels={}, depthMin={}, depthMax={}, visibleBlocks={}, blockEntities={}, entities={}, cameraPos={}",
                hits.nonSkyPixels(), min, max,
                result.value.visibleBlockCount(), result.value.blockEntityCount(), result.value.entityCount(),
                snap.cameraPos());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("width", snap.width());
        resp.put("height", snap.height());
        resp.put("depthMin", min);
        resp.put("depthMax", max);
        resp.put("nonSkyPixels", hits.nonSkyPixels());
        resp.put("cameraPos", snap.cameraPos().x() + "," + snap.cameraPos().y() + "," + snap.cameraPos().z());
        resp.put("timestamp", snap.timestamp());
        // v2.32：agent 当前维 id（与 terrain.nbt 顶层 currentDimension 一致，语义同 store 落盘）。
        resp.put("dimension", result.value.dimension());
        resp.put("visibleBlockCount", result.value.visibleBlockCount());
        resp.put("blockEntityCount", result.value.blockEntityCount());
        resp.put("entityCount", result.value.entityCount());

        List<Map<String, Object>> blockEntities = new ArrayList<>();
        for (VisionCollector.BlockEntitySnapshot be : result.value.blockEntities().values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pos", be.pos().getX() + "," + be.pos().getY() + "," + be.pos().getZ());
            m.put("typeId", be.typeId());
            m.put("block", be.blockId());
            m.put("state", be.stateProps());
            m.put("nbt", nbtToJson(be.nbt()));
            blockEntities.add(m);
        }
        resp.put("blockEntities", blockEntities);

        List<Map<String, Object>> entities = new ArrayList<>();
        for (VisionCollector.EntityLightSnapshot e : result.value.entities()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id());
            m.put("uuid", e.uuid().toString());
            m.put("type", e.typeId());
            m.put("pos", List.of(e.x(), e.y(), e.z()));
            m.put("rotation", List.of(e.yaw(), e.pitch()));
            m.put("motion", List.of(e.vx(), e.vy(), e.vz()));
            m.put("onGround", e.onGround());
            m.put("health", e.health());
            // v2.34（掉落物记忆）：带 item tag 的 minecraft:item → 暴露物品 id + 堆叠数；
            // components 等详情仍走 Tier-2 vision/entity，保持快照轻量。
            if (e.item() != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.item().getStringOr("id", ""));
                item.put("count", e.item().getIntOr("count", 1));
                m.put("item", item);
            }
            // v2.35（决策点 2 渠道 B）：展示实体薄内容摘要（item/equipment/text/blockId 等，
            // DecorativeSummary 在采集帧与 payload 同帧构建）。仅白名单展示实体且摘要成功时有。
            // 记忆侧复原用的是持久化的整份 payload（entities.nbt "nbt" 键），本字段纯给 agent 看。
            if (e.content() != null) {
                m.put("content", nbtToJson(e.content()));
            }
            entities.add(m);
        }
        resp.put("entities", entities);

        Map<String, Object> storeStats = new LinkedHashMap<>();
        storeStats.put("terrain", result.value.terrainStats());
        storeStats.put("blockEntities", result.value.blockEntityStats());
        storeStats.put("entities", result.value.entityStats());
        // v2.31：生物群系 cell 统计（union 总数 + 本帧新增；新增>0 意味着 biomes.nbt 有更新）
        storeStats.put("biomes", result.value.biomeStats());
        resp.put("storeStats", storeStats);
        return resp;
    }

    /**
     * 按需查询单个实体的全量 NBT（Tier 2，低频）。
     *
     * <p>与 {@code snapshot} 不同，这个端点不做批量收集，只对指定 uuid 序列化一次，
     * 且同一 uuid 在 TTL 内命中缓存直接返回（见
     * {@link VisionCollector#collectEntityNbt(UUID, boolean)}）。
     *
     * @param params { "uuid": "…", "force": true? } —— force=true 跳过缓存强制刷新
     * @return { "ok": true, "uuid": "…", "nbt": {…} } 或 { "ok": false, "error": "…" }
     */
    private static Map<String, Object> entityQuery(final Map<String, Object> params) {
        Object uuidObj = params.get("uuid");
        if (!(uuidObj instanceof String uuidStr) || uuidStr.isBlank()) {
            return Map.of("ok", false, "error", "missing 'uuid' param (string)");
        }
        final UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "error", "invalid uuid: " + uuidStr);
        }
        final boolean force = AgentWebSocketServer.bool(params, "force", false);

        var result = new Object() {
            CompoundTag nbt;
            String error;
        };
        CountDownLatch latch = new CountDownLatch(1);

        Minecraft.getInstance().execute(() -> {
            try {
                result.nbt = VisionCollector.collectEntityNbt(uuid, force);
            } catch (Exception e) {
                result.error = e.getMessage();
                SteveX.LOGGER.error("[Vision] entityQuery failed", e);
            } finally {
                latch.countDown();
            }
        });

        try {
            if (!latch.await(SNAPSHOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return Map.of("ok", false, "error", "timeout waiting for render thread");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("ok", false, "error", "interrupted");
        }

        if (result.error != null) {
            return Map.of("ok", false, "error", result.error);
        }
        if (result.nbt == null) {
            return Map.of("ok", false, "error", "entity not found or not serializable");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("uuid", uuid.toString());
        resp.put("nbt", nbtToJson(result.nbt));
        return resp;
    }

    // ==================== NBT → JSON ====================

    /** 将 NBT 递归转换为可被 Gson 序列化的普通 Java 结构。 */
    private static Object nbtToJson(final Tag tag) {
        if (tag == null) return null;
        if (tag instanceof CompoundTag compound) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : compound.keySet()) {
                out.put(key, nbtToJson(compound.get(key)));
            }
            return out;
        }
        if (tag instanceof ListTag list) {
            List<Object> out = new ArrayList<>();
            for (Tag element : list) {
                out.add(nbtToJson(element));
            }
            return out;
        }
        // 标量：字符串 → 数组 → asNumber()（保留原始数值类型，无损转换）
        return tag.asString().map(s -> (Object) s)
                .or(() -> tag.asByteArray().map(a -> (Object) a))
                .or(() -> tag.asIntArray().map(a -> (Object) a))
                .or(() -> tag.asLongArray().map(a -> (Object) a))
                .or(() -> tag.asNumber().map(n -> (Object) n))
                .orElse(tag.toString());
    }
}
