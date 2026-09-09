package com.example.memworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 容器内容记忆通道（设计 §5.2.2，v2.28 → v2.29 → v2.32 按维分桶）。
 *
 * <p>与视觉通道（{@link MemoryRestorer} / {@link TerrainRestorer} / {@link EntityRestorer}）
 * 独立的交互内容通道：只读单个 {@code containers.nbt}（采集侧交互会话提交的"容器/末影箱内容
 * 记忆"）。容器内容属于 L2 交互层，绝不混入 L1 视觉 {@code block_entities.nbt}。
 *
 * <p>文件契约（由采集侧写入、本类读取，两段式单写入者）：
 * <pre>{@code
 * { version: 1,
 *   currentDimension: "minecraft:overworld",          // v2.32 文件最近写入维
 *   worlds: {                                         // v2.32 per-pos 容器按维分桶
 *     "minecraft:overworld": { containers: {
 *       "x,y,z": { "typeId": "minecraft:chest",       // 方块实体 id（建 BE / loadStatic 用）
 *                  "block": "minecraft:chest",        // 方块注册名
 *                  "state": {"facing":"east","type":"single"},  // 状态属性
 *                  "items": [ {"slot": 0, "item": <ItemStack 编码>}, … ] } } }, // 槽位 0..getContainerSize()-1
 *     "minecraft:the_nether": { containers: { … } }
 *   },
 *   enderInventory: { "items": [ {"slot": 0, "item": …}, … ] }   // v2.29：末影箱=玩家态，跨维同一份 → 顶层全局
 * } }</pre>
 *
 * <p>item 序列化与 1.21.11 对齐：本版本无 {@code ItemStack.parse/save(registryAccess, tag)} 便捷方法，
 * 用 {@link ItemStack#CODEC} 经 {@code NbtOps} 编解码（采集侧写入侧与记忆侧解析侧保持同一路径）。
 *
 * <p>世界侧语义（§5.2.2 定案 A/B/C/D）：
 * <ul>
 *   <li><b>每条记录是只读权威引用</b>——每轮 reconcile 把记录内容覆写到世界对应容器；玩家改动被还原。</li>
 *   <li>容器按坐标重建：世界为空气 → 用记录 block+state 自足放置并挂 BE；世界方块同记录但缺 BE →
 *       补挂 BE（{@code BlockEntity.loadStatic} 最小 {id,x,y,z}）；世界方块冲突（terrain 视觉胜）→ 跳过并告警。</li>
 *   <li>末影箱是玩家态（§5.2.2 v2.29）：世界任意末影箱都显示同一内容，只能写本地玩家
 *       {@code getEnderChestInventory()}（无按块填充可能）。记录 = 全局 + 只读，v1 接受。</li>
 * </ul>
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md §5.2.3）：{@code containers}
 * 按<b>维度</b>分桶读取；每轮 reconcile 只覆写 {@code level.dimension()} 对应维的容器（本通道由
 * {@link MemoryWorldManager} 用活动维 ServerLevel 驱动）——主世界容器坐标永不写下界、反之亦然。
 * 末影箱是<b>玩家态</b>（跨维全局同一份），故仍在<b>文件原始 root 顶层</b>读取（旧版单维文件里
 * 它也在顶层；若从 WorldsFile 包的 overworld 桶里读，旧文件末影段会被错误埋进桶内）。
 */
public class ContainerMemoryApplier {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    private static final String KEY_VERSION = "version";
    private static final String KEY_CONTAINERS = "containers";
    private static final String KEY_ENDER_INVENTORY = "enderInventory";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_ITEM = "item";
    private static final String KEY_SLOT = "slot";
    private static final String KEY_BLOCK = "block";
    private static final String KEY_STATE = "state";
    private static final String KEY_TYPE_ID = "typeId";

    /** 一次读取成功的源文件 mtime（mtime 门控，同 §7.4）；未变 → 不读不解压。 */
    private FileTime lastMtime;

    /** 最近一次成功读取的文件内容（缓存：每轮 reconcile 据此覆写，不依赖文件重读）。 */
    private FileData current = FileData.EMPTY;

    /** 单次文件代际内已告警过的 key（世界冲突 / 缺 typeId 等），换新文件内容时清空，避免每轮刷屏。 */
    private final Set<String> warned = new HashSet<>();

    private int ticks;
    private int missingSourceCounter;

    /** 服务器（世界）启动 / 切换时调用，清空已应用状态。 */
    public void onServerStart() {
        lastMtime = null;
        current = FileData.EMPTY;
        warned.clear();
        ticks = 0;
        LOGGER.info("[MemoryWorld] Container memory applier ready");
    }

    /** 命令触发：强制重新读取源文件（须同时清 mtime 门控）。 */
    public void forceRefresh() {
        lastMtime = null;
        warned.clear();
    }

    /**
     * 驱动一次轮询：mtime 变化时重读文件；之后（默认）每轮 reconcile 覆写世界，捕获"BE 稍后才由视觉
     * 通道放置 / 玩家改动被还原"等情况。文件缺失时暂停（同 {@link MemoryRestorer}）。
     *
     * <p>v2.32：每轮只 reconcile {@code level.dimension()} 对应维的容器（加全局末影箱）。
     */
    public void tick(final ServerLevel level) {
        MemoryConfig config = MemoryConfig.get();
        if (ticks++ % Math.max(1, config.pollIntervalTicks) != 0) return;

        Path source = config.resolveContainerFile();
        if (source == null || !Files.exists(source)) {
            if (missingSourceCounter++ % 30 == 0) {
                LOGGER.warn("[MemoryWorld] Container file missing, updates paused (gameDir={}). "
                        + "Set 'containerFile' in config/stevex-test/memory.json.",
                        config.gameDirectory());
            }
            lastMtime = null;     // 文件重新出现后自然触发首次读取
            current = FileData.EMPTY;
            return;
        }
        missingSourceCounter = 0;

        final FileTime mtime;
        try {
            mtime = Files.getLastModifiedTime(source);
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to stat container file {}: {}", source, e.getMessage());
            return;
        }

        boolean changed = false;
        if (!mtime.equals(lastMtime)) {
            FileData data = readFile(source, level.registryAccess());
            if (data == null) return; // 写入半截等 → 保留旧 mtime，下轮重试
            lastMtime = mtime;        // 只在成功读取后才推进
            current = data;
            warned.clear();
            changed = true;
        }

        if (current.isEmpty()) return;

        // 文件变化 → 必然 reconcile（覆写语义，保证与采集同步）；文件未变但容器记录存在 →
        // 按配置每轮 reconcile（捕获延迟放置的 BE / 还原玩家改动）。
        if (changed || config.containerReconcileOnPoll) {
            reconcile(level, current, changed);
        }
    }

    // ==================== 覆写 / 应用 ====================

    /**
     * v2.32：只覆写传入 level（= 活动维，见 {@link MemoryWorldManager}）对应维的容器 + 全局末影箱。
     * 其它维的容器记录留在文件缓存，镜像切回该维时再覆写。
     */
    private void reconcile(final ServerLevel level, final FileData data, final boolean warnConflicts) {
        final String dimension = level.dimension().identifier().toString();
        Map<BlockPos, PosRecord> containers = data.containersByDim().get(dimension);
        if (containers != null) {
            for (Map.Entry<BlockPos, PosRecord> e : containers.entrySet()) {
                applyPos(level, dimension, e.getKey(), e.getValue(), warnConflicts);
            }
        }
        if (data.enderPresent()) {
            applyEnder(level, data.enderItems());
        }
    }

    /**
     * 把一条 per-pos 容器记录覆写到世界。情形分支：
     * <ol>
     *   <li>已有 {@link Container} BE → 直接填充（权威覆写，槽位逐格比对）；</li>
     *   <li>已有 BE 但非 {@link Container}（如末影箱这类不可填充占位）→ 外壳已在，无可填充，不动；</li>
     *   <li>BE 缺失：世界方块与记录相同 → 补挂 BE；世界为空气 → 自足放置记录 block+state
     *       （静默标记，同 §7.9 陷阱 ①）并补挂 BE；世界方块与记录不同 → terrain 视觉胜，跳过。</li>
     * </ol>
     */
    private void applyPos(final ServerLevel level, final String dimension, final BlockPos pos, final PosRecord rec,
                          final boolean warnConflicts) {
        BlockState worldState = level.getBlockState(pos);
        if (!worldState.isAir()) {
            String worldId = BuiltInRegistries.BLOCK.getKey(worldState.getBlock()).toString();
            if (!worldId.equals(rec.blockId())) {
                if (warnConflicts) {
                    warnOnce("conflict@" + dimension + "/" + pos,
                            "[MemoryWorld] Pos {} [{}]: world block {} != recorded {}; skip (terrain visual wins)",
                            pos, dimension, worldId, rec.blockId());
                }
                return;
            }
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            fill(c, rec.items(), dimension + "@" + pos);
            return;
        }
        if (be != null) return; // 非容器 BE 占位（如末影箱）：不可按块填充，外壳已由视觉放置

        if (!worldState.isAir()) {
            attach(level, dimension, pos, worldState, rec);
            return;
        }

        // 世界为空气：自足放置。自建块只可能是非实心容器，不是 DELETION 候选，不会与减量冲突。
        if (rec.blockId().isBlank()) {
            warnOnce("noBlock@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: air & record without block; skip", pos, dimension);
            return;
        }
        BlockState state = BlockStateUtil.fromSaved(rec.blockId(), rec.state());
        if (state.isAir()) {
            warnOnce("airState@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: cannot rebuild block {} state; skip",
                    pos, dimension, rec.blockId());
            return;
        }
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        attach(level, dimension, pos, state, rec);
    }

    /** 补挂 BE（调用处保证 BE 缺失）：用记录 typeId（缺则按方块反查）loadStatic 最小 nbt，成功后填充。 */
    private void attach(final ServerLevel level, final String dimension, final BlockPos pos, final BlockState state,
                        final PosRecord rec) {
        String typeId = rec.typeId();
        if (typeId.isBlank()) typeId = beTypeIdFor(state);
        if (typeId == null) {
            warnOnce("noType@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: cannot attach BE for block {} (no typeId / BE type); skip",
                    pos, dimension, rec.blockId());
            return;
        }
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", typeId);
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        BlockEntity be = BlockEntity.loadStatic(pos, state, nbt, level.registryAccess());
        if (be == null) {
            warnOnce("attachFail@" + dimension + "/" + pos,
                    "[MemoryWorld] Pos {} [{}]: BlockEntity.loadStatic failed (type {}); skip",
                    pos, dimension, typeId);
            return;
        }
        be.setLevel(level);
        level.setBlockEntity(be);
        if (be instanceof Container c) {
            fill(c, rec.items(), dimension + "@" + pos);
        }
    }

    /** 按方块状态反查它的方块实体注册 id（typeId 缺失时兜底，遍历 BE 注册表匹配 validBlocks）。 */
    private static String beTypeIdFor(final BlockState state) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (type.isValid(state)) {
                Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                return id == null ? null : id.toString();
            }
        }
        return null;
    }

    /**
     * 把记录槽位覆写到容器。逐格比对，仅写差异格（避免无谓 setChanged 刷 dirty）。
     * 记录范围外的槽位一律清空（记录 = 该容器内容的完整权威快照）。
     */
    private void fill(final Container c, final List<ItemEntry> items, final String where) {
        int size = c.getContainerSize();
        Map<Integer, ItemStack> expect = new HashMap<>();
        for (ItemEntry e : items) {
            if (e.slot() >= 0 && e.slot() < size && !e.stack().isEmpty()) {
                expect.put(e.slot(), e.stack());
            }
        }
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            ItemStack want = expect.get(i);
            ItemStack got = c.getItem(i);
            ItemStack wantOrEmpty = want == null ? ItemStack.EMPTY : want;
            if (!sameStack(got, wantOrEmpty)) {
                c.setItem(i, wantOrEmpty.copy());
                changed = true;
            }
        }
        if (changed) {
            c.setChanged();
            LOGGER.info("[MemoryWorld] Container filled at {}", where);
        }
    }

    /** 末影箱 = 玩家态（§5.2.2 v2.29）：写入本地玩家的末影箱清单。无玩家时本轮跳过（下轮再试）。 */
    private void applyEnder(final ServerLevel level, final List<ItemEntry> items) {
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        fill(players.get(0).getEnderChestInventory(), items, "player ender inventory");
    }

    private static boolean sameStack(final ItemStack a, final ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    // ==================== 读取源文件 ====================

    /**
     * 读取容器文件 → v2.32 各维 per-pos 容器表 + 顶层末影箱玩家态。
     *
     * <p>末影箱必须从<b>原始 root 顶层</b>读（旧版单维文件它也在顶层；若经
     * {@link WorldsFile#read} 的 legacy 回退，整份 root 会变成 overworld 桶、把末影段埋进桶内，
     * 与采集侧 load() 从原始 root 读末影段的约定对称）。旧版单维文件的 {@code containers} 键在
     * root 顶层，经 legacy 回退后恰为该 overworld 桶的顶层 → 解析路径一致。
     */
    private FileData readFile(final Path source, final HolderLookup.Provider registries) {
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.unlimitedHeap());
            if (root == null) return FileData.EMPTY;

            // v2.32：per-pos 容器按维分桶。
            WorldsFile.Result r = WorldsFile.read(root);
            Map<String, Map<BlockPos, PosRecord>> containersByDim = new LinkedHashMap<>();
            for (Map.Entry<String, CompoundTag> e : r.worlds().entrySet()) {
                CompoundTag containersTag = e.getValue().getCompoundOrEmpty(KEY_CONTAINERS);
                Map<BlockPos, PosRecord> containers = new LinkedHashMap<>();
                for (String key : containersTag.keySet()) {
                    BlockPos pos = parsePos(key);
                    if (pos == null) continue;
                    CompoundTag entry = containersTag.getCompoundOrEmpty(key);
                    String typeId = entry.getStringOr(KEY_TYPE_ID, "");
                    String blockId = entry.getStringOr(KEY_BLOCK, "");
                    Map<String, String> state = readState(entry.getCompoundOrEmpty(KEY_STATE));
                    List<ItemEntry> items = readItems(entry.getListOrEmpty(KEY_ITEMS), registries);
                    containers.put(pos, new PosRecord(typeId, blockId, state, items));
                }
                containersByDim.put(e.getKey(), containers);
            }

            // v2.29：末影箱玩家态始终在文件顶层（跨维全局；新/旧格式同位置）。
            CompoundTag ender = root.getCompoundOrEmpty(KEY_ENDER_INVENTORY);
            boolean enderPresent = !ender.isEmpty() && ender.contains(KEY_ITEMS);
            List<ItemEntry> enderItems = enderPresent
                    ? readItems(ender.getListOrEmpty(KEY_ITEMS), registries)
                    : List.of();

            return new FileData(containersByDim, enderPresent, enderItems);
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to read container file {}: {}", source, e.getMessage());
            return null;
        }
    }

    /**
     * 逐条解析物品列表：{slot, item} → 槽位 + {@link ItemStack}（item 缺失 / 无法解析的条目置空并告警一次）。
     * item 用 {@link ItemStack#CODEC} + {@code NbtOps} 解析，与 1.21.11 采集侧写入路径对称。
     */
    private List<ItemEntry> readItems(final ListTag list, final HolderLookup.Provider registries) {
        List<ItemEntry> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i).orElse(null);
            if (e == null) continue;
            int slot = e.getIntOr(KEY_SLOT, -1);
            if (slot < 0) continue;
            CompoundTag itemTag = e.getCompoundOrEmpty(KEY_ITEM);
            if (itemTag.isEmpty()) continue;
            ItemStack stack = parseItem(itemTag, registries);
            if (stack.isEmpty()) {
                warnOnce("badItem@" + slot + "#" + itemTag.hashCode(),
                        "[MemoryWorld] Unparsable item at slot {}: {}", slot, brief(itemTag));
            }
            out.add(new ItemEntry(slot, stack));
        }
        return out;
    }

    private static ItemStack parseItem(final CompoundTag tag, final HolderLookup.Provider registries) {
        try {
            return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                    .resultOrPartial(err -> LOGGER.warn("[MemoryWorld] Item decode error: {}", err))
                    .orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    private static Map<String, String> readState(final CompoundTag stateTag) {
        Map<String, String> state = new LinkedHashMap<>();
        for (String k : stateTag.keySet()) {
            state.put(k, stateTag.getStringOr(k, ""));
        }
        return state;
    }

    private static BlockPos parsePos(final String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new BlockPos(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            );
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String brief(final CompoundTag tag) {
        String s = tag.toString();
        return s.length() <= 100 ? s : s.substring(0, 100) + "…";
    }

    private void warnOnce(final String key, final String fmt, final Object... args) {
        if (warned.add(key)) {
            LOGGER.warn(fmt, args);
        }
    }

    // ==================== 数据结构 ====================

    /** 一个槽位的记录：全局槽位 0..getContainerSize()-1 + 已解析好的 ItemStack（解析失败为 EMPTY）。 */
    private record ItemEntry(int slot, ItemStack stack) {}

    /** 一条 per-pos 容器记录：BE id（建/挂 BE）+ 方块 + 状态 + 槽位内容。 */
    private record PosRecord(String typeId, String blockId, Map<String, String> state, List<ItemEntry> items) {}

    /**
     * v2.32 一次文件读取结果：各维 per-pos 容器表 + 末影箱玩家态（v2.29，顶层全局）。
     * {@code enderPresent=false} 表示文件未含末影箱段 → 不动玩家末影箱（避免覆写本地已有内容）；
     * 为 true（即使空物品表）→ 记录 = 权威快照，reconcile 会清空还原。
     */
    private record FileData(Map<String, Map<BlockPos, PosRecord>> containersByDim,
                            boolean enderPresent, List<ItemEntry> enderItems) {
        static final FileData EMPTY = new FileData(Map.of(), false, List.of());

        boolean isEmpty() {
            return containersByDim.isEmpty() && !enderPresent;
        }
    }
}
