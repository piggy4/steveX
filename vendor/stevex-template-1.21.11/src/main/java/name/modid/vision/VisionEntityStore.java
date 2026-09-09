package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 实体 NBT 持久化存储 —— 每次采集整体覆写<b>当前维</b>的桶（快照式，仿 {@link VisionTerrainStore}）。
 *
 * <p><b>只存轻量物理状态，不存每实体全量 NBT</b>——全量 NBT 走
 * {@link VisionCollector#collectEntityNbt} 按需查询，避免 GC 风暴。
 * 记忆世界侧用这份文件 + {@code scannedSections} 权威集做新增 / 移动 / 移除。
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：文件按维度分桶，顶层
 * {@code { "currentDimension", "worlds": { <dim>: <本维正文> } }}；正文形态与旧版逐字一致、每维一份，
 * agent 姿态字段同样移入桶内。旧版单维文件由 {@link WorldsFile} 自动视为 overworld 桶。
 *
 * <p>文件格式（NBT）：
 * <pre>{@code
 * {
 *   "currentDimension": "minecraft:overworld",
 *   "worlds": {
 *     "minecraft:overworld": {
 *       "agentPos": "100.5,65.62,96.0",
 *       "agentYaw": -45.0,
 *       "agentPitch": 10.0,
 *       "agentFov": 70,
 *       "dayTime": 6000,
 *       "timestamp": 1720000000,
 *       "entities": {
 *         "<uuid>": { "id": 42, "type": "minecraft:zombie", "pos": [x, y, z],
 *                     "motion": [vx, vy, vz], "rotation": [yaw, pitch],
 *                     "onGround": 1b, "health": 20.0f },
 *         // v2.35（展示实体）：额外携带 "nbt": { id, ...saveWithoutId } 整份可装载 payload
 *         // （type ∈ 采集白名单时有；帧画/展示实体/盔甲架等）
 *       }
 *     },
 *     "minecraft:the_nether": { ... }
 *   }
 * }
 * }</pre>
 *
 * <p>v2（GPU 深度缓冲驱动）：不再写 {@code scannedSections}——记忆世界侧读到空集合 →
 * 移除权威永不触发 → 自动变成<b>纯累积语义</b>（只增不删，见设计 §6.1/§7.1）。
 */
public class VisionEntityStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "entities.nbt";
    private static final String KEY_AGENT_POS = "agentPos";
    private static final String KEY_AGENT_YAW = "agentYaw";
    private static final String KEY_AGENT_PITCH = "agentPitch";
    private static final String KEY_AGENT_FOV = "agentFov";
    private static final String KEY_TIMESTAMP = "timestamp";
    /** v2.21：采集时刻世界时间（dayTime），记忆世界据此对齐昼夜（§7.10）。 */
    private static final String KEY_WORLD_TIME = "dayTime";
    private static final String KEY_ENTITIES = "entities";
    private static final String KEY_ID = "id";
    private static final String KEY_TYPE = "type";
    private static final String KEY_POS = "pos";
    private static final String KEY_MOTION = "motion";
    private static final String KEY_ROTATION = "rotation";
    private static final String KEY_ON_GROUND = "onGround";
    private static final String KEY_HEALTH = "health";
    /** v2.34：掉落物（type=minecraft:item）的物品栈（ItemStack.CODEC 编码 tag；仅非空时有）。 */
    private static final String KEY_ITEM = "item";
    /** v2.35（展示实体内容记忆）：展示实体条目整份可装载 NBT payload（{id, ...saveWithoutId}）；仅白名单类型且有。 */
    private static final String KEY_NBT = "nbt";

    private final Path filePath;

    /** v2.32：分桶镜像（维 id → 该维最后一次快照的桶正文），构造时从既有文件读入、每次 sync 整体写回。 */
    private final Map<String, CompoundTag> worlds = new LinkedHashMap<>();
    /** v2.32：最近一次写入所属维（文件顶层 currentDimension）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    public VisionEntityStore() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.filePath = dir.resolve(FILE_NAME);
        loadExisting();
    }

    /**
     * 用本次采集结果整体覆写<b>当前维</b>的实体桶（v2：内容 = 本次<b>可见</b>实体；其余维桶保留）。
     *
     * @param entities 本次可见实体轻量快照
     * @param agentPos 采集时观察者的相机（眼睛）双精度坐标（游戏精度），可为 null
     * @param agentYaw 采集时观察者水平朝向（度）
     * @param agentPitch 采集时观察者俯仰朝向（度）
     * @param agentFov 采集时观察者基础视场角（整数度，游戏精度）
     * @param worldTime 采集时世界时间（dayTime，游戏时间单位；无世界时 -1，v2.21）
     * @param dimensionId v2.32：采集时所在维 id，决定写哪个桶
     * @return 统计信息 { "entities": n }
     */
    public Map<String, Object> sync(
            final List<VisionCollector.EntityLightSnapshot> entities,
            final Vec3 agentPos,
            final float agentYaw,
            final float agentPitch,
            final int agentFov,
            final long worldTime,
            final String dimensionId
    ) {
        currentDimension = dimensionId;
        CompoundTag bucket = new CompoundTag();
        bucket.putString(KEY_AGENT_POS, agentPosKey(agentPos));
        bucket.putFloat(KEY_AGENT_YAW, agentYaw);
        bucket.putFloat(KEY_AGENT_PITCH, agentPitch);
        bucket.putInt(KEY_AGENT_FOV, agentFov);
        bucket.putLong(KEY_WORLD_TIME, worldTime);
        bucket.putLong(KEY_TIMESTAMP, System.currentTimeMillis());

        CompoundTag entitiesTag = new CompoundTag();
        for (VisionCollector.EntityLightSnapshot e : entities) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(KEY_ID, e.id());
            entry.putString(KEY_TYPE, e.typeId());
            entry.put(KEY_POS, doubleList(e.x(), e.y(), e.z()));
            entry.put(KEY_MOTION, doubleList(e.vx(), e.vy(), e.vz()));
            entry.put(KEY_ROTATION, floatList(e.yaw(), e.pitch()));
            entry.putBoolean(KEY_ON_GROUND, e.onGround());
            entry.putFloat(KEY_HEALTH, e.health());
            // v2.34：掉落物条目携带物品栈 tag（非 item 实体 / 空栈 → 无该键）。
            if (e.item() != null) {
                entry.put(KEY_ITEM, e.item());
            }
            // v2.35：展示实体条目携带整份可装载 NBT payload（非白名单类型 / 无 → 无该键）。
            // 内容复原（§7.2）由记忆侧按 uuid 整份装载；此 tag 也天然承担"内容变更"指纹（§7.3）。
            if (e.payload() != null) {
                entry.put(KEY_NBT, e.payload());
            }
            entitiesTag.put(e.uuid().toString(), entry);
        }
        bucket.put(KEY_ENTITIES, entitiesTag);

        worlds.put(dimensionId, bucket);
        writeFile();

        return Map.of("entities", entities.size());
    }

    // ==================== 内部 ====================

    /** 整体覆盖写（当前维桶已更新，其余维桶由内存镜像带出）。 */
    private void writeFile() {
        try {
            NbtIo.writeCompressed(WorldsFile.wrap(currentDimension, worlds), filePath);
            LOGGER.debug("[Vision] Saved entities: {} dimension bucket(s), current={} → {}",
                    worlds.size(), currentDimension, filePath);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save entity store: {}", ex.getMessage());
        }
    }

    /** 构造时读入既有文件 → 分桶内存镜像（跨会话保留各维最后快照；旧版单维文件自动回退 overworld）。 */
    private void loadExisting() {
        if (!Files.exists(filePath)) return;
        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            worlds.putAll(r.worlds());
            LOGGER.info("[Vision] Loaded entity store: {} dimension bucket(s) from {} (current={})",
                    worlds.size(), filePath, currentDimension);
        } catch (Exception e) {
            LOGGER.warn("[Vision] Failed to load entity store {}: {}", filePath, e.getMessage());
        }
    }

    private static String agentPosKey(final Vec3 v) {
        return v == null ? "" : Double.toString(v.x) + "," + Double.toString(v.y) + "," + Double.toString(v.z);
    }

    private static ListTag doubleList(final double... values) {
        ListTag list = new ListTag();
        for (double v : values) {
            list.add(DoubleTag.valueOf(v));
        }
        return list;
    }

    private static ListTag floatList(final float... values) {
        ListTag list = new ListTag();
        for (float v : values) {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}
