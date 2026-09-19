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
 * 容器 / 末影箱内容记忆持久化（设计 §5.2.2，v2.28 → v2.30；v2.32 按维分桶）。
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
 * 真正变化的是"低频"：修剪在<b>有判删发生</b>时才写（与 {@link VisionBlockEntityStore} 同为条件写），
 * 且一次修剪 = 一次整文件写（而非每帧）。
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：per-pos {@code containers}
 * 按<b>维度</b>分桶（内层 map 由 {@code byDim} 承载），末影箱（{@code enderInventory}）是<b>玩家态</b>、
 * 真实 MC 中跨全部维同一份 → 保持在文件顶层<b>全局</b>不随维分桶。文件顶层
 * {@code { "version", "currentDimension", "worlds": { <dim>: { "containers": {...} } }, "enderInventory" }}。
 *
 * <p>文件格式（NBT，契约见 §5.2.2「文件契约」 + v2.32 §3.1）：
 * <pre>{@code
 * { version: 1,
 *   currentDimension: "minecraft:overworld",
 *   worlds: {
 *     "minecraft:overworld": { containers: {
 *       "x,y,z": { "typeId": ..., "block": ..., "state": {...},
 *                  "items": [ {"slot": 0, "item": <ItemStack.CODEC 编码 tag>}, ... ] }, ...
 *     } },
 *     "minecraft:the_nether": { containers: { ... } }
 *   },
 *   enderInventory: { "items": [ ... ] }   // v2.29 玩家态（可选段，顶层全局）
 * } }</pre>
 */
public class ContainerMemoryStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "stevex/vision";
    private static final String FILE_NAME = "containers.nbt";
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

    /** v2.32：内存镜像：维度 → posKey("x,y,z") → 容器记录；启动时 load、提交时 upsert/remove。 */
    private final Map<String, Map<String, StoredContainer>> byDim = new LinkedHashMap<>();

    /** v2.29：末影箱玩家态是否存在（有记录段才写/覆写；无 → 记忆侧不动本地末影箱）。 */
    private boolean enderPresent;
    private final List<SlotTag> enderItems = new ArrayList<>();

    /** v2.32：最近一次提交所属维（文件顶层 currentDimension；信息性，末影箱提交不改变它）。 */
    private String currentDimension = WorldsFile.LEGACY_DIMENSION;

    private final Path filePath;
    private boolean dirty;

    private ContainerMemoryStore() {
        this.filePath = resolveFilePath();
        load();
    }

    public static ContainerMemoryStore get() {
        return INSTANCE;
    }

    // ==================== 公开接口（提交路径调用，随后 save()） ====================

    /** upsert 一条 per-pos 容器记录（double 每半一条；覆盖该键旧内容 = latest-wins，定案 C）。 */
    public void upsert(final String dimension, final String posKey, final String typeId, final String blockId,
                       final Map<String, String> state, final List<SlotTag> items) {
        if (!dimension.equals(currentDimension)) {
            currentDimension = dimension; // 顶层 currentDimension = 最近一次 per-pos 提交所属维
        }
        byDim.computeIfAbsent(dimension, k -> new LinkedHashMap<>())
                .put(posKey, new StoredContainer(typeId, blockId, state, List.copyOf(items)));
        dirty = true;
    }

    /** 删除一条 per-pos 记录（double↔single 迁移：删伙伴旧键）。 */
    public void remove(final String dimension, final String posKey) {
        Map<String, StoredContainer> containers = byDim.get(dimension);
        if (containers != null && containers.remove(posKey) != null) {
            dirty = true;
        }
    }

    /** 覆写顶层末影箱玩家态（27 格 latest-wins；末影会话提交时调用；玩家态跨维全局、不分桶）。 */
    public void setEnder(final List<SlotTag> items) {
        enderPresent = true;
        enderItems.clear();
        enderItems.addAll(items);
        dirty = true;
    }

    /** 一次提交完成后的整文件落盘（低频事件驱动；mtime 变化 = 记忆侧重读信号）。 */
    public void save() {
        if (!dirty) return;
        CompoundTag root = new CompoundTag();
        Map<String, CompoundTag> buckets = new LinkedHashMap<>();
        for (var de : byDim.entrySet()) {
            CompoundTag bucket = new CompoundTag();
            CompoundTag containersTag = new CompoundTag();
            for (var e : de.getValue().entrySet()) {
                containersTag.put(e.getKey(), e.getValue().toNbt());
            }
            bucket.put(KEY_CONTAINERS, containersTag);
            buckets.put(de.getKey(), bucket);
        }
        root.putInt(KEY_VERSION, 1);
        root.putString(WorldsFile.KEY_CURRENT_DIMENSION, currentDimension);
        root.put(WorldsFile.KEY_WORLDS, wrapWorlds(buckets));
        if (enderPresent) {
            CompoundTag ender = new CompoundTag();
            ender.put(KEY_ITEMS, itemsListTag(enderItems));
            root.put(KEY_ENDER_INVENTORY, ender);
        }
        try {
            NbtIo.writeCompressed(root, filePath);
            LOGGER.info("[Vision] Container memory saved: {} containers across {} dimension(s), enderPresent={} → {}",
                    size(), byDim.size(), enderPresent, filePath);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to save container memory {}: {}", filePath, e.getMessage());
        }
        dirty = false;
    }

    public int size() {
        int total = 0;
        for (Map<String, StoredContainer> containers : byDim.values()) {
            total += containers.size();
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
        final Map<String, StoredContainer> containers = byDim.get(dimension);
        if (containers == null || containers.isEmpty() || expected.isEmpty()) {
            return Map.of("pruned", 0, "pending", 0, "cancelled", 0, "kept", 0);
        }
        refreshPruneGenerations();

        final Set<String> observedKeys = new HashSet<>(Math.max(16, observed.size() * 2));
        for (BlockPos p : observed) observedKeys.add(keyOf(p));

        final Map<String, Integer> pending = pendingDel.computeIfAbsent(dimension, k -> new LinkedHashMap<>());
        final Set<String> countedThisGen = new HashSet<>();
        int pruned = 0, cancelled = 0, kept = 0;

        // ① 本代际被判删、且记录仍在 → 计数 +1；达 K 代际 → 真删 + 强制落盘。
        //    （被判删的格必不在本帧可见集内：判据的可见集闸门 + 信号缺失通道的 ① 都保证了这一点。）
        for (Map.Entry<BlockPos, String> e : expected.entrySet()) {
            final String key = keyOf(e.getKey());
            final StoredContainer rec = containers.get(key);
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
                containers.remove(key);
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
            if (!containers.containsKey(key)) {
                it.remove();                                     // 记录已消失 ⇒ 计数无意义
                continue;
            }
            if (pe.setValue(pe.getValue() + 1) >= pruneGenerations) {
                containers.remove(key);
                it.remove();
                pruned++;
            }
        }

        if (pruned > 0) {
            dirty = true;
            save();          // 真删必须落盘（否则下一次交互写回时记录又活了）
            dirty = false;
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

    // ==================== 内部 ====================

    private static CompoundTag wrapWorlds(final Map<String, CompoundTag> buckets) {
        CompoundTag worldsTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : buckets.entrySet()) {
            worldsTag.put(e.getKey(), e.getValue());
        }
        return worldsTag;
    }

    private static Path resolveFilePath() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to create directory {}: {}", dir, e.getMessage());
        }
        return dir.resolve(FILE_NAME);
    }

    private void load() {
        if (!Files.exists(filePath)) {
            LOGGER.info("[Vision] No existing container memory file, starting fresh.");
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(filePath, NbtAccounter.unlimitedHeap());
            if (root == null) return;

            WorldsFile.Result r = WorldsFile.read(root);
            currentDimension = r.currentDimension();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                Map<String, StoredContainer> containers = new LinkedHashMap<>();
                CompoundTag containersTag = e.getValue().getCompoundOrEmpty(KEY_CONTAINERS);
                for (String key : containersTag.keySet()) {
                    containers.put(key, StoredContainer.fromNbt(containersTag.getCompoundOrEmpty(key)));
                }
                byDim.put(e.getKey(), containers);
            }

            // 末影箱玩家态在顶层（旧/新格式相同位置；WorldsFile 对旧文件 wrap 整份正文为桶时，
            // enderInventory 会混进 overworld 桶——故必须从原始 root 顶层读，而非从桶读）。
            CompoundTag ender = root.getCompoundOrEmpty(KEY_ENDER_INVENTORY);
            if (!ender.isEmpty()) {
                enderPresent = true;
                readItemsInto(ender.getListOrEmpty(KEY_ITEMS), enderItems);
            }
            LOGGER.info("[Vision] Loaded {} container records across {} dimension(s) from {}",
                    size(), byDim.size(), filePath);
        } catch (IOException e) {
            LOGGER.error("[Vision] Failed to load container memory {}: {}", filePath, e.getMessage());
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
