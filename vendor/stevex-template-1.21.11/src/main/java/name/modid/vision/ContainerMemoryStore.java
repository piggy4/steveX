package name.modid.vision;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;

/**
 * 容器 / 末影箱内容记忆持久化（设计 §5.2.2，v2.28 → v2.30；v2.32 按维分桶；v2.48 region 分片）。
 *
 * <p>与视觉 L1 store（{@link VisionBlockEntityStore} 每帧整文件覆盖写）不同：本 store 是
 * <b>交互提交路径的唯一写者、低频事件驱动</b>——只在一次容器会话提交（close/commit）时
 * 整文件 read-modify-write，无竞态（§5.2.2 定案 A 的"独立文件单写者"理由）。
 *
 * <p><b>v2.38 追加第二个写路径</b>（设计 §4.1 F1 / §7.1 决策 G）：{@link #applyDeletions}
 * 在采集侧 resolve 内修剪被证明消失的容器的记录。<b>"唯一写者"因此在字面上不再成立</b>，但
 * "无竞态"的结构理由<b>不变</b>——两条路径跑在<b>同一个线程</b>上：交互提交来自客户端屏幕关闭时的
 * 提交，resolve 经 {@code Minecraft.getInstance().execute(...)} 派发到<b>渲染/客户端主线程</b>
 * 执行（{@code VisionApi}）。故写序列仍是单线程的，整文件 read-modify-write 不需要额外同步。
 *
 * <p>v2.32（世界类型区分）：per-pos {@code containers} 按<b>维度</b>分桶，末影箱（{@code enderInventory}）
 * 是<b>玩家态</b>、真实 MC 中跨全部维同一份 → 全局不随维分桶。
 *
 * <p><b>v2.48：region 分片</b>（设计 §11，用户 2026-10-07 定案）。分片前是单文件 {@code containers.nbt}；
 * 分片后：
 *
 * <pre>{@code
 * stevex/vision/containers/
 *   _index.nbt                 // 维 → 区键 → updatedAt（记忆端的门控信号，§11.9.3）
 *   ender.nbt                  // v2.29 末影箱玩家态：跨维全局、无坐标，进不了任何区
 *   minecraft_overworld/
 *     r.0.0.nbt                // { version, dimension, regionX, regionZ, updatedAt, containers: {...} }
 * }</pre>
 *
 * <p><b>末影箱单独一个文件</b>不只是"没坐标所以放不进去"：它与 per-pos 容器的<b>写入时机互不相干</b>
 * ——{@code setEnder} 只在末影会话提交时触发，而 per-pos 记录在每次容器提交时更新。分文件后，
 * 一次普通箱子提交不会去碰末影箱那份文件，反之亦然。
 *
 * <p>文件格式（NBT；区文件内 {@code containers} 的键值形态与分片前<b>逐字同形</b>）：
 * <pre>{@code
 * // containers/minecraft_overworld/r.0.0.nbt
 * { "version": 1, "dimension": "minecraft:overworld", "regionX": 0, "regionZ": 0,
 *   "updatedAt": 1720000000000,
 *   "containers": {
 *     "x,y,z": { "typeId": ..., "block": ..., "state": {...},
 *                "items": [ {"slot": 0, "item": <ItemStack.CODEC 编码 tag>}, ... ] }, ...
 *   } }
 *
 * // containers/ender.nbt —— 键名与分片前的顶层段完全一致，记忆端解析代码零改动
 * { "version": 1, "enderInventory": { "items": [ ... ] } }
 * }</pre>
 *
 * <p><b>不再持有 {@code currentDimension}</b>：分片前它是文件顶层字段（"最近一次 per-pos 提交所属维"），
 * 但那是<b>整份文件</b>的属性；分片后"最近一次写入"落在哪个区文件上，问哪个文件都答不上来。
 * 记忆端从不读它（{@code ContainerMemoryApplier} 只取 {@code containers} 桶与 {@code enderInventory}），
 * 故随分片一并去掉，不为一句无人消费的元数据造第三个文件。
 */
public class ContainerMemoryStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    /** v2.48：分片后的 store 目录名（原单文件名去掉 {@code .nbt}）。 */
    private static final String STORE_DIR_NAME = "containers";
    /** v2.48：分片前的单文件（构造时若存在则拆分迁移并退役）。 */
    private static final String LEGACY_FILE_NAME = "containers.nbt";
    /** v2.48：末影箱玩家态文件（跨维全局、无坐标；缺席 = 记忆端不动本地末影箱）。 */
    private static final String ENDER_FILE_NAME = "ender.nbt";

    private static final String KEY_VERSION = "version";
    private static final String KEY_CONTAINERS = "containers";
    private static final String KEY_ENDER_INVENTORY = "enderInventory";
    private static final String KEY_TYPE_ID = "typeId";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEM = "item";
    private static final String KEY_SLOT = "slot";

    private static final ContainerMemoryStore INSTANCE = new ContainerMemoryStore();

    /** v2.48：维度 → 区键 → 该区的容器记录镜像（区键 = "rx,rz"，由方块坐标 {@code >> 9} 导出）。 */
    private final Map<String, Map<String, RegionBucket>> byDim = new LinkedHashMap<>();

    /** v2.48：索引镜像（维 → 区键 → updatedAt）。区文件是权威，本表是它的派生，随写就地更新。 */
    private final Map<String, Map<String, Long>> index = new LinkedHashMap<>();

    /** v2.29：末影箱玩家态是否存在（有记录段才写/覆写；无 → 记忆侧不动本地末影箱）。 */
    private boolean enderPresent;
    private final List<SlotTag> enderItems = new ArrayList<>();

    /** v2.48：本次提交被写脏的区（维 → 区键集）——{@link #upsert}/{@link #remove} 累积，{@link #save} 消费。 */
    private final Map<String, Set<String>> dirtyRegions = new LinkedHashMap<>();
    /** v2.48：末影箱文件是否被写脏。 */
    private boolean enderDirty;

    private final Path storeDir;
    private final Path enderFilePath;
    private final Path legacyFilePath;

    private ContainerMemoryStore() {
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        this.storeDir = dir.resolve(STORE_DIR_NAME);
        this.enderFilePath = storeDir.resolve(ENDER_FILE_NAME);
        this.legacyFilePath = dir.resolve(LEGACY_FILE_NAME);
        load();
        migrateLegacyIfPresent();
    }

    public static ContainerMemoryStore get() {
        return INSTANCE;
    }

    // ==================== 公开接口（提交路径调用，随后 save()） ====================

    /** upsert 一条 per-pos 容器记录（double 每半一条；覆盖该键旧内容 = latest-wins，定案 C）。 */
    public void upsert(final String dimension, final String posKey, final String typeId, final String blockId,
                       final Map<String, String> state, final List<SlotTag> items) {
        final String regionKey = regionKeyOf(dimension, posKey);
        if (regionKey == null) return;
        final RegionBucket bucket = byDim.computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .computeIfAbsent(regionKey, k -> new RegionBucket());
        bucket.containers.put(posKey, new StoredContainer(typeId, blockId, state, List.copyOf(items)));
        markDirty(dimension, regionKey);
    }

    /** 删除一条 per-pos 记录（double↔single 迁移：删伙伴旧键）。 */
    public void remove(final String dimension, final String posKey) {
        final String regionKey = regionKeyOf(dimension, posKey);
        if (regionKey == null) return;
        final Map<String, RegionBucket> regions = byDim.get(dimension);
        final RegionBucket bucket = regions == null ? null : regions.get(regionKey);
        if (bucket != null && bucket.containers.remove(posKey) != null) {
            markDirty(dimension, regionKey);
        }
    }

    /** 覆写顶层末影箱玩家态（27 格 latest-wins；末影会话提交时调用；玩家态跨维全局、不分桶）。 */
    public void setEnder(final List<SlotTag> items) {
        enderPresent = true;
        enderItems.clear();
        enderItems.addAll(items);
        enderDirty = true;
    }

    /**
     * 一次提交完成后的落盘（低频事件驱动；索引 mtime 变化 = 记忆侧重读信号）。
     *
     * <p><b>只写被写脏的那几个区</b>（+ 末影箱文件，若这次动过它）。分片前这里整份覆盖写——容器记录
     * 散布在整个世界里，一次开箱的代价是重写所有维的所有箱子记录；现在一次开箱只写它所在的那个区。
     */
    public void save() {
        final List<VisionRegions.RegionWrite> writes = new ArrayList<>();
        for (Map.Entry<String, Set<String>> de : dirtyRegions.entrySet()) {
            writes.addAll(buildWrites(de.getKey(), de.getValue()));
        }
        dirtyRegions.clear();
        if (!writes.isEmpty()) {
            try {
                VisionRegions.commit(storeDir, writes, index);
                LOGGER.info("[Vision] Container memory saved: {} record(s) over {} region file(s) of {} dimension(s) → {}",
                        size(), writes.size(), byDim.size(), storeDir);
            } catch (IOException e) {
                LOGGER.error("[Vision] Failed to save container memory {}: {}", storeDir, e.getMessage());
            }
        }
        if (enderDirty) {
            enderDirty = false;
            writeEnder();
        }
    }

    public int size() {
        int total = 0;
        for (Map<String, RegionBucket> regions : byDim.values()) {
            for (RegionBucket b : regions.values()) {
                total += b.containers.size();
            }
        }
        return total;
    }

    // ==================== v2.38（§7.1 决策 G·H·I）：减量修剪（F1 的容器落点） ====================

    /**
     * 延迟修剪的代数 K（设计 §7.1 决策 G）—— 配置键 {@code config/stevex/vision.json} 的
     * {@code containerPruneGenerations}，默认 4、下限 2。文件缺失 / 键缺失 / 非法 → 保持当前值。
     */
    private static final int DEFAULT_PRUNE_GENERATIONS = 4;
    /** K 的下限：1 代际 = 当帧即删，等于没有延迟，"观测到活体即撤销"的窗口被抹掉。 */
    private static final int MIN_PRUNE_GENERATIONS = 2;
    private static final String CONFIG_FILE_NAME = "config/stevex/vision.json";
    private static final String KEY_PRUNE_GENERATIONS = "containerPruneGenerations";

    /**
     * 待修剪计数（决策 G，<b>内存态、不落盘</b>）：维度 → posKey → 已连续 N 个代际"未被观测到活体"。
     * 重启后计数从零开始 ⇒ 最坏只是把修剪再推迟 K 代际（安全方向）。
     */
    private final Map<String, Map<String, Integer>> pendingDel = new LinkedHashMap<>();

    private int pruneGenerations = DEFAULT_PRUNE_GENERATIONS;
    private FileTime configMtime;

    /**
     * v2.38（设计 §4.1 <b>F1</b> / §7.1 <b>决策 G·H·I</b>）<b>唯一删除原语</b>在本 store 的落点 ——
     * <b>延迟</b>修剪被"判据证明已消失"的格的容器记录。
     *
     * <p><b>为什么是延迟而非即时</b>（决策 G 的核心不对称）：本 store 的载荷（{@code Items} 内容）
     * <b>不可重建</b>——它是<b>交互事件</b>的产物（玩家开箱时才读到 {@code menu.slots}），而
     * {@code BlockEntityFieldPolicy} 刻意 STRIP 容器族的 {@code Items}（无交互内部一律不采）⇒ 观测层面
     * 永远拿不回来。故同样是"判据命中"，BE 记录可以即时删（可再观测重建），容器记录必须给一个
     * <b>可撤销窗口</b>：每代际重新确认，<b>观测到活体立即撤销</b>，连续 K 个代际未观测到才真删。
     *
     * <p><b>计数器只看采集侧自己的观测</b>（{@code observed} = {@code terrain.keySet()}，本帧可见集），
     * <b>不得</b>依赖该格再次出现在 deletions 里：镜像侧删格后记忆侧就不再上报该 cell
     * （{@code MemoryCellReporter} 只报镜像里存在的块）⇒ deletions 会自然消失 ⇒ 靠 deletions 计数
     * <b>永远数不到 2</b>。这是决策 G 必须写死的点。
     *
     * <p><b>入参取并集</b>（决策 H）：{@code expected} = {@code deletions ∪ signalLossDeletions}，
     * 与 BE store 同源同判据（"某格被判删"这一事实对容器通道同样成立）。
     *
     * <p><b>块身份复核</b>（决策 I）：value 非空（信号缺失段携带 blockId）时要求与记录逐字相等才计数
     * ——箱子族正是经该段进来的，故这条对加表路线是承重的。
     *
     * <p><b>代际 = 一次本方法调用</b>（= 一次 resolve / 采集帧）。
     *
     * @param dimension 目标维（只作用于该维子图）
     * @param expected  本代际被判删的格 → 该格上报的 blockId（无身份信息段用空串）
     * @param observed  本帧已观测到的方块格（{@code terrain.keySet()}）——"观测到活体 ⇒ 撤销"的依据
     * @return 统计 { "pruned", "pending", "cancelled", "kept" }
     */
    public Map<String, Integer> applyDeletions(final String dimension,
                                              final Map<BlockPos, String> expected,
                                              final Set<BlockPos> observed) {
        final Map<String, RegionBucket> regions = byDim.get(dimension);
        if (regions == null || regions.isEmpty() || expected.isEmpty()) {
            return Map.of("pruned", 0, "pending", 0, "cancelled", 0, "kept", 0);
        }
        refreshPruneGenerations();

        final Set<String> observedKeys = new HashSet<>(Math.max(16, observed.size() * 2));
        for (BlockPos p : observed) observedKeys.add(keyOf(p));

        final Map<String, Integer> pending = pendingDel.computeIfAbsent(dimension, k -> new LinkedHashMap<>());
        final Set<String> countedThisGen = new HashSet<>();
        final Set<String> touchedRegions = new LinkedHashSet<>();
        int pruned = 0, cancelled = 0, kept = 0;

        // ① 本代际被判删、且记录仍在 → 计数 +1；达 K 代际 → 真删 + 强制落盘。
        //    （被判删的格必不在本帧可见集内：判据的可见集闸门 + 信号缺失通道的 ① 都保证了这一点。）
        for (Map.Entry<BlockPos, String> e : expected.entrySet()) {
            final String key = keyOf(e.getKey());
            final String regionKey = VisionRegions.regionKeyOfBlock(e.getKey());
            final RegionBucket bucket = regions.get(regionKey);
            final StoredContainer rec = bucket == null ? null : bucket.containers.get(key);
            if (rec == null) {
                pending.remove(key);   // 记录已不在（上一次修剪成功 / 交互覆盖）→ 计数无意义
                continue;
            }
            final String reportedId = e.getValue();
            if (reportedId != null && !reportedId.isBlank() && !reportedId.equals(rec.blockId)) {
                kept++;                // 决策 I：身份不符 ⇒ 不计数、不修剪（欠删方向安全）
                pending.remove(key);
                continue;
            }
            countedThisGen.add(key);
            if (pending.merge(key, 1, Integer::sum) >= pruneGenerations) {
                bucket.containers.remove(key);
                touchedRegions.add(regionKey);
                pending.remove(key);
                pruned++;
            }
        }

        // ② 已有 pending 但本代际未再被判删（= 镜像侧已删格 ⇒ deletions 自然消失）→ 只看观测：
        //    观测到活体 ⇒ 撤销（这正是"假阳性且之后被看到"的正常结局）；仍未观测到 ⇒ 继续计数。
        for (Iterator<Map.Entry<String, Integer>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<String, Integer> pe = it.next();
            final String key = pe.getKey();
            if (countedThisGen.contains(key)) continue;           // ① 已计过，不得重复计
            if (observedKeys.contains(key)) {
                it.remove();                                     // 观测到活体 ⇒ 撤销（记录保留）
                cancelled++;
                continue;
            }
            // pending 只按 posKey 记数（不落盘），故这里从键反推所在区——区是纯函数，反推无损。
            final BlockPos pos = VisionRegions.parsePosKey(key);
            final String regionKey = pos == null ? null : VisionRegions.regionKeyOfBlock(pos);
            final RegionBucket bucket = regionKey == null ? null : regions.get(regionKey);
            if (bucket == null || !bucket.containers.containsKey(key)) {
                it.remove();                                     // 记录已消失 ⇒ 计数无意义
                continue;
            }
            if (pe.setValue(pe.getValue() + 1) >= pruneGenerations) {
                bucket.containers.remove(key);
                touchedRegions.add(regionKey);
                it.remove();
                pruned++;
            }
        }

        if (pruned > 0) {
            saveRegions(dimension, touchedRegions);   // 真删必须落盘（否则下一次交互写回时记录又活了）
        }
        return Map.of("pruned", pruned, "pending", pending.size(), "cancelled", cancelled, "kept", kept);
    }

    /** K 的热重载（mtime 门控，与 {@link DecorativeConfig} 同款约定；文件缺失 / 非法 → 保持当前值，不告警）。 */
    private void refreshPruneGenerations() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return;
        final Path file = mc.gameDirectory.toPath().resolve(CONFIG_FILE_NAME);
        try {
            final FileTime mtime = Files.getLastModifiedTime(file);
            if (mtime.equals(configMtime)) return;
            configMtime = mtime;
            final JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonObject()) return;
            final JsonElement v = root.getAsJsonObject().get(KEY_PRUNE_GENERATIONS);
            if (v == null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) return;
            final int k = Math.max(MIN_PRUNE_GENERATIONS, v.getAsInt());
            if (k != pruneGenerations) {
                pruneGenerations = k;
                LOGGER.info("[Vision] Container prune delay K = {} generation(s) (from {})", k, file);
            }
        } catch (IOException | RuntimeException e) {
            // 可选配置文件：缺失 / 非法一律保持当前值（默认 4），与 DecorativeConfig 的"可选"约定一致
        }
    }

    private static String keyOf(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 当前生效的修剪延迟 K（代际）—— 供采集侧诊断行打印（§10 第 15 条）。 */
    public int pruneGenerations() {
        return pruneGenerations;
    }

    // ==================== 内部：落盘 ====================

    /** 一个区的容器记录镜像 + 该区最后一次写盘的墙钟毫秒。 */
    private static final class RegionBucket {
        final Map<String, StoredContainer> containers = new LinkedHashMap<>();
        long updatedAt;
    }

    /** posKey → 所在区键；posKey 不可解析（上游 bug，理论上不会发生）→ 记警告并返回 null。 */
    private static String regionKeyOf(final String dimension, final String posKey) {
        final BlockPos pos = VisionRegions.parsePosKey(posKey);
        if (pos == null) {
            LOGGER.warn("[Vision] Malformed container posKey '{}' (dim={}) — dropped", posKey, dimension);
            return null;
        }
        return VisionRegions.regionKeyOfBlock(pos);
    }

    private void markDirty(final String dimension, final String regionKey) {
        dirtyRegions.computeIfAbsent(dimension, k -> new LinkedHashSet<>()).add(regionKey);
    }

    /** 构建若干区的落盘条目（不写盘）。{@code updatedAt} 在此刷新为该区本次的写盘时刻。 */
    private List<VisionRegions.RegionWrite> buildWrites(final String dimensionId, final Set<String> regionKeys) {
        final Map<String, RegionBucket> regions = byDim.get(dimensionId);
        if (regions == null) return List.of();
        final long now = System.currentTimeMillis();
        final List<VisionRegions.RegionWrite> writes = new ArrayList<>(regionKeys.size());
        for (String regionKey : regionKeys) {
            final int[] rxrz = VisionRegions.parseRegionKey(regionKey);
            final RegionBucket bucket = regions.get(regionKey);
            if (rxrz == null || bucket == null) continue;
            bucket.updatedAt = now;
            final CompoundTag containersTag = new CompoundTag();
            for (Map.Entry<String, StoredContainer> e : bucket.containers.entrySet()) {
                containersTag.put(e.getKey(), e.getValue().toNbt());
            }
            final CompoundTag root = VisionRegions.regionRoot(dimensionId, rxrz[0], rxrz[1], now,
                    KEY_CONTAINERS, containersTag);
            root.putInt(KEY_VERSION, 1);   // 分片前的文件级元数据，按 §11.5 冗余进每个区文件
            writes.add(new VisionRegions.RegionWrite(dimensionId, rxrz[0], rxrz[1], root));
        }
        return writes;
    }

    /** 单维立即落盘（修剪路径用；提交路径走 {@link #save} 以便跨维合并成一次索引写）。 */
    private void saveRegions(final String dimensionId, final Set<String> regionKeys) {
        final List<VisionRegions.RegionWrite> writes = buildWrites(dimensionId, regionKeys);
        if (writes.isEmpty()) return;
        try {
            VisionRegions.commit(storeDir, writes, index);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to save container regions {}: {}", storeDir, e.getMessage());
        }
    }

    /**
     * 落末影箱玩家态。文件<b>缺席</b>即"记忆侧不动本地末影箱"的信号——故 {@code enderPresent} 为假时
     * 什么都不写，而不是写一个空壳让记忆侧去猜。
     */
    private void writeEnder() {
        if (!enderPresent) return;
        final CompoundTag ender = new CompoundTag();
        ender.put(KEY_ITEMS, itemsListTag(enderItems));
        final CompoundTag root = new CompoundTag();
        root.putInt(KEY_VERSION, 1);
        root.put(KEY_ENDER_INVENTORY, ender);
        try {
            Files.createDirectories(storeDir);
            UnionSaveScheduler.writeAtomic(root, enderFilePath);
            LOGGER.info("[Vision] Ender inventory saved: {} stack(s) → {}", enderItems.size(), enderFilePath);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to save ender inventory {}: {}", enderFilePath, e.getMessage());
        }
    }

    private static ListTag itemsListTag(final List<SlotTag> items) {
        ListTag list = new ListTag();
        for (SlotTag it : items) {
            CompoundTag e = new CompoundTag();
            e.putInt(KEY_SLOT, it.slot());
            if (it.item() != null) e.put(KEY_ITEM, it.item());
            list.add(e);
        }
        return list;
    }

    private static void readItemsInto(final ListTag list, final List<SlotTag> out) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i).orElse(null);
            if (e == null) continue;
            int slot = e.getIntOr(KEY_SLOT, -1);
            CompoundTag item = e.getCompoundOrEmpty(KEY_ITEM);
            if (slot < 0 || item.isEmpty()) continue;
            out.add(new SlotTag(slot, item));
        }
    }

    // ==================== 加载与迁移 ====================

    private void load() {
        loadRegions();
        loadEnder();
        rebuildIndexIfNeeded();
    }

    /** 读全部区文件 → 内存镜像（跨会话累积）。单区坏了只丢那一个区，不阻断启动。 */
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
                final CompoundTag containersTag = root.getCompoundOrEmpty(KEY_CONTAINERS);
                for (String key : containersTag.keySet()) {
                    bucket.containers.put(key, StoredContainer.fromNbt(containersTag.getCompoundOrEmpty(key)));
                }
                bucket.updatedAt = VisionRegions.updatedAtOf(root);
                byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>())
                        .put(VisionRegions.regionKey(rxrz[0], rxrz[1]), bucket);
                files++;
            }
        }
        if (files > 0) {
            LOGGER.info("[Vision] Loaded {} container records over {} region file(s) of {} dimension(s) from {}",
                    size(), files, byDim.size(), storeDir);
        }
    }

    /** 读末影箱玩家态（键名与分片前顶层段一致，记忆端解析代码零改动）。 */
    private void loadEnder() {
        if (!Files.isRegularFile(enderFilePath)) return;
        try {
            final CompoundTag root = NbtIo.readCompressed(enderFilePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;
            final CompoundTag ender = root.getCompoundOrEmpty(KEY_ENDER_INVENTORY);
            if (ender.isEmpty()) return;
            enderPresent = true;
            readItemsInto(ender.getListOrEmpty(KEY_ITEMS), enderItems);
            LOGGER.info("[Vision] Loaded ender inventory: {} stack(s) from {}", enderItems.size(), enderFilePath);
        } catch (IOException | RuntimeException e) {
            // v2.48.1：连 RuntimeException 一起接——截断 gzip 流抛的是非受检的 ReportedNbtException。
            LOGGER.error("[Vision] Failed to load ender inventory {}: {}", enderFilePath, e.getMessage());
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
     * 分片前单文件的拆分迁移（§11.13 第 4 项）：读旧 {@code containers.nbt} → per-pos 记录按区拆写、
     * 末影箱另存 → 旧文件退役改名。
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

            for (Map.Entry<String, CompoundTag> de : WorldsFile.read(root).worlds().entrySet()) {
                final String dim = de.getKey();
                final Map<String, RegionBucket> regions = byDim.computeIfAbsent(dim, k -> new LinkedHashMap<>());
                final Set<String> touched = touchedByDim.computeIfAbsent(dim, k -> new LinkedHashSet<>());
                final CompoundTag containersTag = de.getValue().getCompoundOrEmpty(KEY_CONTAINERS);
                for (String key : containersTag.keySet()) {
                    final BlockPos pos = VisionRegions.parsePosKey(key);
                    if (pos == null) continue;
                    final String regionKey = VisionRegions.regionKeyOfBlock(pos);
                    final RegionBucket rb = regions.computeIfAbsent(regionKey, k -> new RegionBucket());
                    rb.containers.put(key, StoredContainer.fromNbt(containersTag.getCompoundOrEmpty(key)));
                    rb.updatedAt = now;
                    touched.add(regionKey);
                }
            }

            // 末影箱在旧文件<b>顶层</b>（不在任何维桶里）。旧版单维文件由 WorldsFile 把整份正文包成
            // overworld 桶，但那个"桶"就是 root 本身 ⇒ 从 root 顶层读在两种形态下都成立。
            final CompoundTag ender = root.getCompoundOrEmpty(KEY_ENDER_INVENTORY);
            if (!ender.isEmpty()) {
                enderPresent = true;
                enderItems.clear();
                readItemsInto(ender.getListOrEmpty(KEY_ITEMS), enderItems);
                writeEnder();
            }

            int migrated = 0;
            for (Map.Entry<String, Set<String>> de : touchedByDim.entrySet()) {
                saveRegions(de.getKey(), de.getValue());   // 与提交路径同一条落盘路径
                migrated += de.getValue().size();
            }
            LOGGER.info("[Vision] Migrated legacy {} → {} region file(s) (enderPresent={}) under {}",
                    LEGACY_FILE_NAME, migrated, enderPresent, storeDir);
            VisionRegions.retireLegacyFile(legacyFilePath);
        } catch (Exception e) {
            // 迁移失败不阻塞启动：旧文件保持原地（未退役），下次启动重试；本次以内存里的部分并集继续。
            LOGGER.warn("[Vision] Failed to migrate legacy {}: {}", LEGACY_FILE_NAME, e.getMessage());
        }
    }

    // ==================== 数据结构 ====================

    /** 一格：菜单容器槽号 + 已编码 item tag（ItemStack.CODEC；仅非空格）。 */
    public record SlotTag(int slot, CompoundTag item) {}

    private static final class StoredContainer {
        final String typeId;
        final String blockId;
        final Map<String, String> state;
        final List<SlotTag> items;

        StoredContainer(final String typeId, final String blockId,
                        final Map<String, String> state, final List<SlotTag> items) {
            this.typeId = typeId;
            this.blockId = blockId;
            this.state = state;
            this.items = items;
        }

        CompoundTag toNbt() {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_TYPE_ID, typeId);
            entry.putString(KEY_BLOCK, blockId);
            CompoundTag st = new CompoundTag();
            state.forEach(st::putString);
            entry.put(KEY_STATE, st);
            entry.put(KEY_ITEMS, itemsListTag(items));   // 记忆侧按 entry.getListOrEmpty(KEY_ITEMS) 读
            return entry;
        }

        static StoredContainer fromNbt(final CompoundTag entry) {
            Map<String, String> state = new LinkedHashMap<>();
            CompoundTag st = entry.getCompoundOrEmpty(KEY_STATE);
            for (String k : st.keySet()) {
                state.put(k, st.getStringOr(k, ""));
            }
            List<SlotTag> items = new ArrayList<>();
            readItemsInto(entry.getListOrEmpty(KEY_ITEMS), items);
            return new StoredContainer(
                    entry.getStringOr(KEY_TYPE_ID, ""),
                    entry.getStringOr(KEY_BLOCK, ""),
                    state,
                    items
            );
        }
    }
}
