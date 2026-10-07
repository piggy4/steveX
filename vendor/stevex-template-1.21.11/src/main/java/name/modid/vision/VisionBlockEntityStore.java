package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * 方块实体 NBT 持久化存储 —— 基于文件的增量保存（跨时间累积 union，按维分桶）。
 *
 * <p>行为：
 * <ul>
 *   <li>新方块实体 → 写入存储</li>
 *   <li>NBT 未变化  → 跳过</li>
 *   <li>NBT 已变化  → 更新存储</li>
 * </ul>
 *
 * <p>v2.32（世界类型区分）：内容按<b>维度</b>分桶——换维采集只更新自己那维的子图，其余维条目原样保留，
 * 坐标天然不再跨维碰撞。
 *
 * <p><b>v2.48：region 分片</b>（设计 §11，用户 2026-10-07 定案）。分片前是单文件
 * {@code block_entities.nbt}，每帧有变化就整份覆盖写；分片后：
 *
 * <pre>{@code
 * stevex/vision/block_entities/
 *   _index.nbt                 // 维 → 区键 → updatedAt（记忆端的门控信号，§11.9.3）
 *   _pose.nbt                  // agent 姿态：维级标量，全维一份（见下）
 *   minecraft_overworld/
 *     r.0.0.nbt                // { dimension, regionX, regionZ, updatedAt, blockEntities: {...} }
 *   minecraft_the_nether/
 *     r.0.0.nbt
 * }</pre>
 *
 * <p><b>为什么姿态必须单独一个文件</b>（实施期发现，设计 §11.5 未预见）：{@code agentPos/agentYaw/
 * agentPitch/agentFov/dayTime} 是<b>维级标量</b>——没有坐标，进不了区文件；而 agent 一走动它就变，
 * <b>每帧都变</b>。若把它塞进 {@code _index.nbt}，索引的 mtime 会跟着每帧刷新，记忆端的门控
 * （"索引没变就不读"，§11.9.2 的"≈零"前提）当场作废。放进任意一个区文件则更糟：姿态会随 agent
 * 跨区移动而在区之间"搬家"。故给它一个自己的文件——两个变量的<b>变化频率不同，就该分属不同文件</b>，
 * 这样门控的粒度才对得上：索引只标记"哪个区的方块实体变了"（罕见），姿态文件只标记"agent 在哪"（每帧）。
 *
 * <p>区文件自报 {@code dimension} 的理由见 {@link VisionRegions} 类 javadoc——目录名不可逆，
 * 而采集端启动时必须读完所有区文件才能重建内存 union。
 */
public class VisionBlockEntityStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    /** v2.48：分片后的 store 目录名（原单文件名去掉 {@code .nbt}）。 */
    private static final String STORE_DIR_NAME = "block_entities";
    /** v2.48：分片前的单文件（构造时若存在则拆分迁移并退役）。 */
    private static final String LEGACY_FILE_NAME = "block_entities.nbt";
    /** v2.48：agent 姿态文件（维级标量，全维一份；**不进**索引，见类 javadoc）。 */
    private static final String POSE_FILE_NAME = "_pose.nbt";

    private static final String KEY_BLOCK_ENTITIES = "blockEntities";
    private static final String KEY_TYPE_ID = "typeId";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_NBT = "nbt";
    private static final String KEY_AGENT_POS = "agentPos";
    private static final String KEY_AGENT_YAW = "agentYaw";
    private static final String KEY_AGENT_PITCH = "agentPitch";
    private static final String KEY_AGENT_FOV = "agentFov";
    /** v2.21：采集时刻世界时间（dayTime），记忆世界据此对齐昼夜（§7.10）。 */
    private static final String KEY_WORLD_TIME = "dayTime";
    private static final String KEY_TIMESTAMP = "timestamp";

    /** v2.48：维度 → 区键 → 该区的方块实体镜像（区键 = "rx,rz"，由方块坐标 {@code >> 9} 导出）。 */
    private final Map<String, Map<String, RegionBucket>> byDim = new LinkedHashMap<>();

    /** v2.32：维度 → 该维最后一次采集时的 agent 姿态，落 {@code _pose.nbt}。 */
    private final Map<String, Pose> poseByDim = new LinkedHashMap<>();

    /** v2.48：索引镜像（维 → 区键 → updatedAt）。区文件是权威，本表是它的派生，随写就地更新。 */
    private final Map<String, Map<String, Long>> index = new LinkedHashMap<>();

    /** v2.32：最近一次采集所属维（随姿态落 {@code _pose.nbt} 顶层；信息性，记忆端不消费）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    private final Path storeDir;
    private final Path poseFilePath;
    private final Path legacyFilePath;

    public VisionBlockEntityStore() {
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.storeDir = dir.resolve(STORE_DIR_NAME);
        this.poseFilePath = storeDir.resolve(POSE_FILE_NAME);
        this.legacyFilePath = dir.resolve(LEGACY_FILE_NAME);
        load();
        migrateLegacyIfPresent();
    }

    // ==================== 公开接口 ====================

    /**
     * 将一批快照与已有存储对比，仅写入新增或变化的条目（限当前维子图）；同时把本次采集时
     * agent 所在坐标记录为<b>当前维</b>桶的最新 {@code agentPos}。
     *
     * @param snapshots 当前帧收集到的方块实体快照
     * @param agentPos 采集时观察者的相机（眼睛）双精度坐标（游戏精度），可为 null
     * @param agentYaw 采集时观察者水平朝向（度）
     * @param agentPitch 采集时观察者俯仰朝向（度）
     * @param agentFov 采集时观察者基础视场角（整数度，游戏精度）
     * @param worldTime 采集时世界时间（dayTime，游戏时间单位；无世界时 -1，v2.21）
     * @param dimensionId v2.32：采集时所在维 id，决定更新哪个维的子图 / 姿态
     * @return 统计信息 { "new": n, "updated": n, "skipped": n }
     */
    public Map<String, Integer> sync(final Map<BlockPos, VisionCollector.BlockEntitySnapshot> snapshots,
                                     final Vec3 agentPos,
                                     final float agentYaw,
                                     final float agentPitch,
                                     final int agentFov,
                                     final long worldTime,
                                     final String dimensionId) {
        int added = 0, updated = 0, skipped = 0;

        if (!dimensionId.equals(currentDimension)) {
            currentDimension = dimensionId;
        }

        // 当前维姿态：只在发生变化时置脏（agent 一走动就变 ⇒ 本文件按帧刷新，这与区文件完全不同频，
        // 也正是它必须独立成文件的原因）。换维时 newAgentPos 与旧姿态必然不同 ⇒ 新维首次出现必落盘。
        boolean poseDirty = false;
        final String newAgentPos = agentPosKey(agentPos);
        final Pose pose = poseByDim.get(dimensionId);
        if (pose == null || !pose.agentPos.equals(newAgentPos)
                || Math.abs(agentYaw - pose.agentYaw) > 0.001f
                || Math.abs(agentPitch - pose.agentPitch) > 0.001f
                || agentFov != pose.agentFov
                || worldTime != pose.dayTime) {
            poseByDim.put(dimensionId, new Pose(newAgentPos, agentYaw, agentPitch, agentFov, worldTime));
            poseDirty = true;
        }

        final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dimensionId, k -> new LinkedHashMap<>());
        final Set<String> touchedRegions = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, VisionCollector.BlockEntitySnapshot> entry : snapshots.entrySet()) {
            final BlockPos pos = entry.getKey();
            final VisionCollector.BlockEntitySnapshot snapshot = entry.getValue();
            final String key = posToKey(pos);
            final String regionKey = VisionRegions.regionKeyOfBlock(pos);
            final RegionBucket bucket = regions.get(regionKey);
            final StoredEntry existing = bucket == null ? null : bucket.entries.get(key);

            if (existing != null) {
                // 已存在 → 比较类型 / 方块 / 状态 / NBT
                if (entryEquals(existing, snapshot)) {
                    skipped++;
                    continue;
                }
                existing.typeId = snapshot.typeId();
                existing.blockId = snapshot.blockId();
                existing.stateProps = Map.copyOf(snapshot.stateProps());
                existing.nbt = snapshot.nbt() != null ? snapshot.nbt().copy() : new CompoundTag();
                existing.timestamp = snapshot.timestamp();
                touchedRegions.add(regionKey);
                updated++;
            } else {
                final CompoundTag nbtCopy = snapshot.nbt() != null ? snapshot.nbt().copy() : new CompoundTag();
                regions.computeIfAbsent(regionKey, k -> new RegionBucket()).entries.put(key, new StoredEntry(
                        nbtCopy,
                        snapshot.typeId(),
                        snapshot.blockId(),
                        Map.copyOf(snapshot.stateProps()),
                        snapshot.timestamp()
                ));
                touchedRegions.add(regionKey);
                added++;
            }
        }

        // 只写真正变更的那几个区（+ 索引），姿态另走一份文件。两条路径互不牵连：
        // agent 走动只刷新姿态文件，索引 mtime 纹丝不动 ⇒ 记忆端不会因为"agent 挪了一步"去重扫区块。
        if (!touchedRegions.isEmpty()) {
            writeRegions(dimensionId, touchedRegions);
        }
        if (poseDirty) {
            writePose();
        }

        return Map.of("new", added, "updated", updated, "skipped", skipped);
    }

    /**
     * v2.38（设计 §4.1 <b>F1</b> / §7.1 <b>决策 H·I</b>）<b>唯一删除原语</b>在本 store 的落点 —— 持久修剪
     * 被"判据证明已消失"的格的方块实体记录。
     *
     * <p><b>为什么必须做</b>（§1.1 的对称性断裂）：本 store 是<b>增量 union、永不自删</b>的累积文件；方块被
     * 减量删掉后记录仍在 ⇒ 记忆侧回放把方块复活（告示牌/箱子/床的根因）。判据只管"方块本体没了"，本方法
     * 让"判据命中"这一事实落到记录上，从而把复活根因消除在持久层（而非靠记忆侧每帧挡）。
     *
     * <p><b>入参取并集</b>（决策 H）：调用方传入的是 {@code deletions ∪ signalLossDeletions}——两条通道
     * 的候选在同一 resolve 内都已就位。只覆盖 {@code deletions} 会让"信号缺失表加进来的方块"删了方块却
     * 留下负载（§14.4 的加表路线就此失效）。
     *
     * <p><b>块身份复核</b>（决策 I）：{@code expected} 的 value 是该格本代际由记忆侧上报的 blockId
     * （信号缺失段携带；main/translucent/shaped 段不带 ⇒ 空串）。非空时要求与记录<b>逐字相等</b>才修剪
     * ——防"同 pos 换了另一种方块"时误伤记录。<b>空串不作额外要求</b>：这些段的上报谓词与记忆侧执行守卫
     * 同族（上报 ⇒ 记忆侧持有该块 ⇒ 其守卫必放行），故"上报即持有"已构成决策 I 要的构造性保证。
     *
     * <p><b>不延迟</b>（决策 G）：BE 载荷是"每帧观测、可重建"的（{@code ObjectResolver.recordBlock} 在该
     * 方块再次可见时同帧回填），被误修剪的代价 = 一轮闪烁且可自愈 ⇒ 即时修剪。**与之相反**，
     * {@link ContainerMemoryStore} 的容器载荷不可重建，故那边是延迟修剪。
     *
     * <p><b>落盘时机</b>：本方法自行落盘（F1 的承诺是"同帧落盘"）——只写真正被修剪到的区。
     *
     * @param dimension 目标维（修剪只作用于该维子图）
     * @param expected  待修剪格 → 该格本代际上报的 blockId（无身份信息段用空串）
     * @return 统计 { "pruned": 已修剪, "kept": 身份不符/记录仍在而保留, "missing": 本就无记录 }
     */
    public Map<String, Integer> applyDeletions(final String dimension, final Map<BlockPos, String> expected) {
        final Map<String, RegionBucket> regions = byDim.get(dimension);
        if (regions == null || regions.isEmpty() || expected.isEmpty()) {
            return Map.of("pruned", 0, "kept", 0, "missing", 0);
        }
        int pruned = 0, kept = 0, missing = 0;
        final Set<String> touchedRegions = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, String> e : expected.entrySet()) {
            final String key = posToKey(e.getKey());
            final String regionKey = VisionRegions.regionKeyOfBlock(e.getKey());
            final RegionBucket bucket = regions.get(regionKey);
            final StoredEntry existing = bucket == null ? null : bucket.entries.get(key);
            if (existing == null) {
                missing++;
                continue;
            }
            final String reportedId = e.getValue();
            if (reportedId != null && !reportedId.isBlank() && !reportedId.equals(existing.blockId)) {
                kept++;   // 决策 I：身份不符 ⇒ 不修剪（欠删方向安全）
                continue;
            }
            bucket.entries.remove(key);
            touchedRegions.add(regionKey);
            pruned++;
        }
        if (pruned > 0) {
            writeRegions(dimension, touchedRegions);   // 同帧落盘（F1 的持久性承诺在此兑现）
        }
        return Map.of("pruned", pruned, "kept", kept, "missing", missing);
    }

    /** 存储中已有的方块实体总数（跨全部维）。 */
    public int size() {
        int total = 0;
        for (Map<String, RegionBucket> regions : byDim.values()) {
            for (RegionBucket b : regions.values()) {
                total += b.entries.size();
            }
        }
        return total;
    }

    /** 清除全部存储（内存 + 整棵区目录 + 姿态文件，跨全部维）。 */
    public void clear() {
        byDim.clear();
        poseByDim.clear();
        index.clear();
        currentDimension = WorldsFile.LEGACY_DIMENSION;
        VisionRegions.deleteRecursively(storeDir);
    }

    // ==================== 内部 ====================

    /** 一个区的方块实体镜像 + 该区最后一次写盘的墙钟毫秒。 */
    private static final class RegionBucket {
        final Map<String, StoredEntry> entries = new LinkedHashMap<>();
        long updatedAt;
    }

    /** 只写本帧真正变更的那几个区，最后写索引（顺序由 {@link VisionRegions#commit} 钉死）。 */
    private void writeRegions(final String dimensionId, final Set<String> regionKeys) {
        final Map<String, RegionBucket> regions = byDim.get(dimensionId);
        if (regions == null) return;
        final long now = System.currentTimeMillis();
        final List<VisionRegions.RegionWrite> writes = new ArrayList<>(regionKeys.size());
        for (String regionKey : regionKeys) {
            final int[] rxrz = VisionRegions.parseRegionKey(regionKey);
            final RegionBucket bucket = regions.get(regionKey);
            if (rxrz == null || bucket == null) continue;
            bucket.updatedAt = now;
            final CompoundTag beTag = new CompoundTag();
            for (Map.Entry<String, StoredEntry> e : bucket.entries.entrySet()) {
                final StoredEntry v = e.getValue();
                final CompoundTag entry = new CompoundTag();
                entry.putString(KEY_TYPE_ID, v.typeId);
                entry.putString(KEY_BLOCK, v.blockId);
                entry.put(KEY_STATE, propsToNbt(v.stateProps));
                entry.put(KEY_NBT, v.nbt);
                entry.putLong(KEY_TIMESTAMP, v.timestamp);
                beTag.put(e.getKey(), entry);
            }
            writes.add(new VisionRegions.RegionWrite(dimensionId, rxrz[0], rxrz[1],
                    VisionRegions.regionRoot(dimensionId, rxrz[0], rxrz[1], now, KEY_BLOCK_ENTITIES, beTag)));
        }
        try {
            VisionRegions.commit(storeDir, writes, index);
            LOGGER.debug("[Vision] Saved {} block entities: {}/{} region(s) of {} dim(s) → {}",
                    size(), writes.size(), regions.size(), byDim.size(), storeDir);
        } catch (IOException ex) {
            LOGGER.error("[Vision] Failed to save store {}: {}", storeDir, ex.getMessage());
        }
    }

    /** 落 agent 姿态（全维一份，每帧可能刷新；<b>不经索引</b>，故不会扰动记忆端的区块门控）。 */
    private void writePose() {
        final Map<String, CompoundTag> worlds = new LinkedHashMap<>();
        for (Map.Entry<String, Pose> e : poseByDim.entrySet()) {
            final Pose p = e.getValue();
            final CompoundTag bucket = new CompoundTag();
            bucket.putString(KEY_AGENT_POS, p.agentPos);
            bucket.putFloat(KEY_AGENT_YAW, p.agentYaw);
            bucket.putFloat(KEY_AGENT_PITCH, p.agentPitch);
            bucket.putInt(KEY_AGENT_FOV, p.agentFov);
            bucket.putLong(KEY_WORLD_TIME, p.dayTime);
            worlds.put(e.getKey(), bucket);
        }
        try {
            Files.createDirectories(storeDir);
            UnionSaveScheduler.writeAtomic(WorldsFile.wrap(currentDimension, worlds), poseFilePath);
        } catch (IOException ex) {
            LOGGER.warn("[Vision] Failed to save agent pose {}: {}", poseFilePath, ex.getMessage());
        }
    }

    private static String posToKey(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 观察者眼睛坐标 → 存储字符串（双精度，游戏精度）；未知（null）时存空串。 */
    private static String agentPosKey(final Vec3 v) {
        return v == null ? "" : Double.toString(v.x) + "," + Double.toString(v.y) + "," + Double.toString(v.z);
    }

    private static boolean nbtEquals(final CompoundTag a, final CompoundTag b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /** 方块状态属性表 → NBT。 */
    private static CompoundTag propsToNbt(final Map<String, String> props) {
        CompoundTag tag = new CompoundTag();
        props.forEach(tag::putString);
        return tag;
    }

    /** NBT → 方块状态属性表（旧文件无该字段时返回空表）。 */
    private static Map<String, String> propsFromNbt(final CompoundTag tag) {
        Map<String, String> props = new LinkedHashMap<>();
        for (String key : tag.keySet()) {
            props.put(key, tag.getStringOr(key, ""));
        }
        return props;
    }

    /** 比较条目的全部字段（类型 / 方块 / 状态 / NBT），决定是否跳过或更新。 */
    private static boolean entryEquals(
            final StoredEntry existing,
            final VisionCollector.BlockEntitySnapshot snapshot
    ) {
        return existing.typeId.equals(snapshot.typeId())
                && existing.blockId.equals(snapshot.blockId())
                && existing.stateProps.equals(snapshot.stateProps())
                && nbtEquals(existing.nbt, snapshot.nbt());
    }

    // ==================== 加载与迁移 ====================

    private void load() {
        loadRegions();
        loadPose();
        rebuildIndexIfNeeded();
    }

    /** 读全部区文件 → 内存镜像（跨会话累积）。单个区坏了只丢那一个区，不阻断启动。 */
    private void loadRegions() {
        int files = 0;
        for (Path dimDir : VisionRegions.listDimDirs(storeDir)) {
            final String dirName = dimDir.getFileName().toString();
            for (Path file : VisionRegions.listRegionFiles(dimDir)) {
                final int[] rxrz = VisionRegions.parseRegionFileName(file.getFileName().toString());
                if (rxrz == null) continue;
                final CompoundTag root = VisionRegions.readRegion(file, dirName, rxrz[0], rxrz[1]);
                if (root == null) continue;
                final String dim = root.getStringOr(VisionRegions.KEY_DIMENSION, "");
                final RegionBucket bucket = new RegionBucket();
                final CompoundTag beTag = root.getCompoundOrEmpty(KEY_BLOCK_ENTITIES);
                for (String key : beTag.keySet()) {
                    final CompoundTag entry = beTag.getCompoundOrEmpty(key);
                    bucket.entries.put(key, new StoredEntry(
                            entry.getCompoundOrEmpty(KEY_NBT),
                            entry.getStringOr(KEY_TYPE_ID, ""),
                            entry.getStringOr(KEY_BLOCK, ""),
                            propsFromNbt(entry.getCompoundOrEmpty(KEY_STATE)),
                            entry.getLongOr(KEY_TIMESTAMP, 0L)
                    ));
                }
                bucket.updatedAt = VisionRegions.updatedAtOf(root);
                byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>())
                        .put(VisionRegions.regionKey(rxrz[0], rxrz[1]), bucket);
                files++;
            }
        }
        if (files > 0) {
            LOGGER.info("[Vision] Loaded {} stored block entities over {} region file(s) of {} dimension(s) from {}",
                    size(), files, byDim.size(), storeDir);
        }
    }

    /** 读 agent 姿态文件（缺席 / 损坏 → 各维无姿态，下一帧采集即重建）。 */
    private void loadPose() {
        if (!Files.isRegularFile(poseFilePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(poseFilePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            r.worlds().forEach((dim, bucket) -> poseByDim.put(dim, new Pose(
                    bucket.getStringOr(KEY_AGENT_POS, ""),
                    bucket.getFloatOr(KEY_AGENT_YAW, 0.0f),
                    bucket.getFloatOr(KEY_AGENT_PITCH, 0.0f),
                    bucket.getIntOr(KEY_AGENT_FOV, 0),
                    bucket.getLongOr(KEY_WORLD_TIME, -1L)
            )));
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.warn("[Vision] Failed to load agent pose {}: {}", poseFilePath, e.getMessage());
        }
    }

    /**
     * 索引是<b>派生</b>：从区文件各自的 {@code updatedAt} 重建；与盘上索引不符才补写。
     * 补写为了自愈（崩在"区文件已写、索引未写"之间 / 索引被删），不符判断为了避免每次启动都白动
     * 索引 mtime（那会让记忆端白跑一轮 diff）。
     */
    private void rebuildIndexIfNeeded() {
        final Map<String, Map<String, Long>> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, RegionBucket>> de : byDim.entrySet()) {
            final Map<String, Long> m = new LinkedHashMap<>();
            de.getValue().forEach((regionKey, bucket) -> m.put(regionKey, bucket.updatedAt));
            rebuilt.put(de.getKey(), m);
        }
        index.putAll(rebuilt);
        final Map<String, Map<String, Long>> onDisk = VisionRegions.readIndex(storeDir);
        if (!VisionRegions.indexEquals(rebuilt, onDisk)) {
            try {
                VisionRegions.writeIndex(storeDir, index);
                LOGGER.info("[Vision] Region index rebuilt from region files → {}", storeDir);
            } catch (IOException e) {
                LOGGER.warn("[Vision] Failed to rebuild region index {}: {}", storeDir, e.getMessage());
            }
        }
    }

    /**
     * 分片前单文件的拆分迁移（§11.13 第 4 项）：读旧 {@code block_entities.nbt} → 按区拆写 + 姿态另存
     * → 旧文件退役改名。
     *
     * <p>与 {@link #load()} 是<b>并集</b>关系而非二选一：迁到一半被杀进程，下次启动能接着迁完
     * （重复并入幂等）。迁完才退役旧文件，顺序不可反。
     */
    private void migrateLegacyIfPresent() {
        if (!Files.isRegularFile(legacyFilePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(legacyFilePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final long now = System.currentTimeMillis();
            final Map<String, Set<String>> touchedByDim = new LinkedHashMap<>();
            boolean poseMigrated = false;

            for (Map.Entry<String, CompoundTag> de : WorldsFile.read(root).worlds().entrySet()) {
                final String dim = de.getKey();
                final CompoundTag bucket = de.getValue();

                // 姿态（旧文件把它放在桶顶层）→ 并入姿态镜像
                if (bucket.contains(KEY_AGENT_POS)) {
                    poseByDim.put(dim, new Pose(
                            bucket.getStringOr(KEY_AGENT_POS, ""),
                            bucket.getFloatOr(KEY_AGENT_YAW, 0.0f),
                            bucket.getFloatOr(KEY_AGENT_PITCH, 0.0f),
                            bucket.getIntOr(KEY_AGENT_FOV, 0),
                            bucket.getLongOr(KEY_WORLD_TIME, -1L)
                    ));
                    poseMigrated = true;
                }

                final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
                final Set<String> touched = touchedByDim.computeIfAbsent(dim, k -> new LinkedHashSet<>());
                final CompoundTag beTag = bucket.getCompoundOrEmpty(KEY_BLOCK_ENTITIES);
                for (String key : beTag.keySet()) {
                    final BlockPos pos = VisionRegions.parsePosKey(key);
                    if (pos == null) continue;
                    final CompoundTag entry = beTag.getCompoundOrEmpty(key);
                    final String regionKey = VisionRegions.regionKeyOfBlock(pos);
                    final RegionBucket rb = regions.computeIfAbsent(regionKey, k -> new RegionBucket());
                    rb.entries.put(key, new StoredEntry(
                            entry.getCompoundOrEmpty(KEY_NBT),
                            entry.getStringOr(KEY_TYPE_ID, ""),
                            entry.getStringOr(KEY_BLOCK, ""),
                            propsFromNbt(entry.getCompoundOrEmpty(KEY_STATE)),
                            entry.getLongOr(KEY_TIMESTAMP, 0L)
                    ));
                    rb.updatedAt = now;
                    touched.add(regionKey);
                }
            }

            int migrated = 0;
            for (Map.Entry<String, Set<String>> de : touchedByDim.entrySet()) {
                writeRegions(de.getKey(), de.getValue());   // 与增量路径同一条落盘路径
                migrated += de.getValue().size();
            }
            if (poseMigrated) {
                writePose();
            }
            LOGGER.info("[Vision] Migrated legacy {} → {} region file(s) (poseMigrated={}) under {}",
                    LEGACY_FILE_NAME, migrated, poseMigrated, storeDir);
            VisionRegions.retireLegacyFile(legacyFilePath);
        } catch (Exception e) {
            // 迁移失败不阻塞启动：旧文件保持原地（未退役），下次启动重试；本次以内存里的部分并集继续。
            LOGGER.warn("[Vision] Failed to migrate legacy {}: {}", LEGACY_FILE_NAME, e.getMessage());
        }
    }

    // ==================== 内部数据结构 ====================

    /** 某维最后一次采集时的 agent 姿态（落 {@code _pose.nbt} 的维桶）。 */
    private record Pose(String agentPos, float agentYaw, float agentPitch, int agentFov, long dayTime) {}

    private static class StoredEntry {
        CompoundTag nbt;
        String typeId;
        String blockId;
        Map<String, String> stateProps;
        long timestamp;

        StoredEntry(final CompoundTag nbt, final String typeId,
                    final String blockId, final Map<String, String> stateProps,
                    final long timestamp) {
            this.nbt = nbt;
            this.typeId = typeId;
            this.blockId = blockId;
            this.stateProps = stateProps;
            this.timestamp = timestamp;
        }
    }
}
