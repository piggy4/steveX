package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
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
