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
     *           "effectsSource"(v2.42：{@code "integrated_server"} | {@code "unavailable"}),
     *           "visibleBlockCount", "blockEntityCount", "entityCount",
     *           "blockEntities":[ {pos, typeId, block, state, nbt} ],
     *           "entities":[ {id, uuid, type, pos, rotation, motion, onGround, health,
     *                        item?(v2.34 掉落物 {id,count}；v2.40 + enchanted?),
     *                        living 属性(v2.41，仅 LivingEntity):
     *                          equipment?{slot:{id,count?,enchanted?}}, name?, effects?(见 effectsSource),
     *                          baby?, maxHealth, pose, onFire?,
     *                        content?(v2.35 展示实体薄摘要)} ],
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
        // v2.39（§4.2.1 决策 R-1）：落格量单列（LANDING_EPSILON = 1/128），与 ObjectResolver 的
        // 探针 ε 解耦——后者用于 air 回退恢复 / 实体盒膨胀，语义是"探到相邻格"，不随落格量下调。
        final Unprojector.UnprojectResult hits = unproj.visibleBlockHits(bucket.keySet(), Unprojector.LANDING_EPSILON);
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
        // landingBoundary（v2.39，§4.2.1）：落格推移改变了所属格的像素数 = 压在格界平面上的面
        // 的屏幕占比。它与 ε 无关地只反映场景内容；若相邻帧间大幅抖动 ⇒ ε 已低于量化下界。
        SteveX.LOGGER.info(
                "[Vision] snapshot: nonSkyPixels={}, depthMin={}, depthMax={}, landingBoundary={}(ε={}), visibleBlocks={}, blockEntities={}, entities={}, cameraPos={}",
                hits.nonSkyPixels(), min, max,
                hits.boundaryResolvedPixels(), Unprojector.LANDING_EPSILON,
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
        // v2.42（§12.4 拍板 1）：药水效果的数据源可用性是**进程级**事实（不是逐实体的），故在顶层
        // 报一次即可。unavailable ⇒ 实体级 effects 键的缺席意味着"未知"，不是"没有效果"。
        resp.put("effectsSource", EffectSampler.sourceName());

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
            // v2.41（实体属性观测面，见 docs/实体属性观测面设计方案.md §3）：活体属性平铺为实体级键。
            // 逐键用带类型的取值而非 nbtToJson —— ByteTag 经 nbtToJson 得到的是数字 1 而不是 true
            // （tag.asString() 对 ByteTag 返回空，最终落到 asNumber()）。
            if (e.livingView() != null) {
                putLivingView(m, e.livingView());
            }
            // v2.34（掉落物记忆）：带 item tag 的 minecraft:item → 暴露物品 id + 堆叠数。
            // v2.40：+ 真附魔（判据 A = ENCHANTMENTS 非空，与 entities[].content 共用同一实现，
            // 见 DecorativeSummary#isEnchanted）。附魔 id/等级、自定义名、盒内容等 components 细节
            // 仍不在本面暴露——本面是"看到"级（id/堆叠数/是否附魔）。
            if (e.item() != null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.item().getStringOr("id", ""));
                item.put("count", e.item().getIntOr("count", 1));
                if (DecorativeSummary.isEnchanted(e.item())) {
                    item.put("enchanted", true);
                }
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
     * 按 uuid 查单个实体的<b>落盘条目</b>（v2.40：数据源 = 采集端 {@code entities.nbt} 的内存镜像）。
     *
     * <p>v2.39 及以前本端点是 Tier-2 <b>直读活体</b>（对任意实体 {@code saveWithoutId}），范围取
     * {@code level.entitiesForRendering()}（无视锥裁剪）、厚度为整份 NBT（含装备槽完整组件）。
     * v2.40 改为查 {@link VisionEntityStore#findEntity}：
     * <ul>
     *   <li><b>范围</b>自动收敛为"本帧被渲染的实体"（store 由采集管线视锥链写入、每次采集整体覆写
     *       当前维桶）⇒ 移出视锥 / 实体消失 / 不在当前维 → 查不到；</li>
     *   <li><b>语义</b>由 inspect（实时直读）变为 recall（<b>回忆上一帧看见过的对象</b>）。</li>
     * </ul>
     *
     * <p>v2.46：查询只返回采集时随条目保存的薄投影。复原侧的完整 {@code item}/{@code nbt}/
     * {@code living} 载荷不会穿过本 API；已知集应由显式交互 API 建模，不能靠复原文件旁路泄露。
     * 查询还要求本进程已完成至少一次采集，且最近采集维度与客户端当前维度一致。
     *
     * @param params { "uuid": "…" }——取自 {@code vision/snapshot.entities[].uuid}
     *               （v2.40：{@code force} 随直读缓存一并失效，不再读取）
     * @return { "ok": true, "uuid": "…", "effectsSource": "…", "nbt": { id, type, pos, motion,
     *           rotation, onGround, health, item?(薄摘要), content?(薄摘要), living?(薄摘要) } }
     *           或 { "ok": false, "error": "…" }
     */
    private static Map<String, Object> entityQuery(final Map<String, Object> params) {
        Object uuidObj = params.get("uuid");
        if (!(uuidObj instanceof String uuidStr) || uuidStr.isBlank()) {
            return Map.of("ok", false, "error", "missing 'uuid' param (string)");
        }
        final String uuid;
        try {
            // 规范化（大写/短横线省略等一律归一）→ 与 store 的键形态（UUID#toString）一致
            uuid = UUID.fromString(uuidStr).toString();
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "error", "invalid uuid: " + uuidStr);
        }

        var result = new Object() {
            CompoundTag nbt;
            String error;
        };
        CountDownLatch latch = new CountDownLatch(1);

        // store 的 worlds 由采集管线在渲染线程整体覆写（VisionEntityStore#sync）→ 查询同线程执行，
        // 避免读到半更新态（§8）。纯内存查表，不再序列化任何实体。
        Minecraft.getInstance().execute(() -> {
            try {
                final var level = Minecraft.getInstance().level;
                final String dimension = level == null ? null : level.dimension().identifier().toString();
                result.nbt = VisionCollector.getEntityStore().findEntity(uuid, dimension);
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
            return Map.of("ok", false, "error",
                    "entity not found in entity store (not visible in the last capture frame, or not in the current dimension)");
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("ok", true);
        resp.put("uuid", uuid);
        resp.put("effectsSource", EffectSampler.sourceName());
        resp.put("nbt", nbtToJson(result.nbt));
        return resp;
    }

    // ==================== 活体属性（v2.41） ====================

    /**
     * 把 {@link LivingSummary#buildView} 构建的薄摘要展开成 {@code vision/snapshot.entities[]} 的
     * <b>实体级键</b>（见 docs/实体属性观测面设计方案.md §3）。
     *
     * <p>出现条件（"缺省即常态"或"恒写"，§3 表）：
     * <ul>
     *   <li>{@code equipment} — 8 槽中非空者；整组为空 ⇒ <b>无该键</b>。
     *       {@code {slot: {id, count?, enchanted?}}}，与 {@code entities[].content.equipment} 同形</li>
     *   <li>{@code name} — 仅 {@code shouldShowName()}（名字牌的渲染谓词）</li>
     *   <li>{@code effects} — 非空才出现；只给<b>种类 id</b>，不含持续/等级。
     *       <b>过滤 {@code isVisible()}</b>（没有粒子的效果看不见，v2.42 拍板 2）。
     *       ⚠️ <b>数据源是集成服务器（v2.42，见设计 §12）</b>——连接真实服务器时本键<b>恒缺席</b>，
     *       此时"缺席"意味着<b>未知</b>而非"没有效果"，须结合顶层 {@code effectsSource} 判读</li>
     *   <li>{@code baby} — 仅 true</li>
     *   <li>{@code maxHealth} — 恒有。⚠️ <b>超观测例外</b>（数值型，与 {@code health} 同款，§8.4）</li>
     *   <li>{@code pose} — 恒写（{@code Pose#getSerializedName()}，18 值）</li>
     *   <li>{@code onFire} — 仅 true</li>
     * </ul>
     *
     * <p>全部经带类型的 getter 取值（不走 {@link #nbtToJson}）：NBT 的布尔是 ByteTag，
     * {@code nbtToJson} 会把它渲染成数字 {@code 1} 而非 {@code true}。
     */
    private static void putLivingView(final Map<String, Object> m, final CompoundTag view) {
        final CompoundTag equipment = view.getCompoundOrEmpty("equipment");
        if (!equipment.isEmpty()) {
            final Map<String, Object> equip = new LinkedHashMap<>();
            for (String slot : equipment.keySet()) {
                final CompoundTag it = equipment.getCompoundOrEmpty(slot);
                final Map<String, Object> one = new LinkedHashMap<>();
                one.put("id", it.getStringOr("id", ""));
                final int count = it.getIntOr("count", 1);
                if (count > 1) one.put("count", count);
                if (it.getBooleanOr("enchanted", false)) one.put("enchanted", true);
                equip.put(slot, one);
            }
            m.put("equipment", equip);
        }
        if (view.contains("name")) {
            m.put("name", view.getStringOr("name", ""));
        }
        final ListTag effects = view.getListOrEmpty("effects");
        if (!effects.isEmpty()) {
            final List<String> ids = new ArrayList<>();
            for (Tag t : effects) {
                t.asString().ifPresent(ids::add);
            }
            m.put("effects", ids);
        }
        if (view.getBooleanOr("baby", false)) {
            m.put("baby", true);
        }
        m.put("maxHealth", view.getFloatOr("maxHealth", 0f));
        m.put("pose", view.getStringOr("pose", "standing"));
        if (view.getBooleanOr("onFire", false)) {
            m.put("onFire", true);
        }
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
