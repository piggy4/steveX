package name.modid.vision;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;

/**
 * 容器内容记忆的采集侧会话化状态机（设计 §5.2.3，v2.30）。
 *
 * <p>采集侧是纯 WS 事件驱动（没有每帧采集循环，客户端容器方块实体副本为空），而"打开容器"由
 * {@code key/use-once} 异步触发（{@code KeyMapping.click}），没有同步的"已打开"事件可用。
 * 因此本类在 {@code END_CLIENT_TICK} 每帧观察屏幕状态转移，把"容器 GUI 打开"绑定到它正对的
 * 可交互容器方块（crosshair 命中），在会话期间缓存打开的菜单内容，会话结束时组装提交：
 *
 * <ol>
 *   <li><b>打开绑定</b>：新 {@link AbstractContainerScreen} 出现且非玩家背包 → 用当前
 *       crosshair 方块命中校验 {@code blockId ∈ 容器族}（与 §5.2.2 STRIP 列表同源）；
 *       命中才建立会话（记 pos / blockId / ender 标志 / 绑定 screen）。</li>
 *   <li><b>内容来源</b>：客户端方块实体副本为空，内容只能从打开的菜单读——遍历
 *       {@code menu.slots}，直到 {@code slot.container == player.inventory} 前的连续区段
 *       即该容器的完整槽位（序号 = 容器本地槽，含空格）。每 tick 缓存一份快照。</li>
 *   <li><b>提交触发</b>：两条路径组同样的记录——(a) WS {@code container/close} 在
 *       {@code player.closeContainer()} 前同步读最终内容提交（主路径）；(b) 屏幕自行关闭
 *       （距离/服务器原因）时 tick 用最近缓存兜底提交。同一容器键 latest-wins（定案 C）。</li>
 *   <li><b>double 拆分</b>：double-chest 菜单 54 槽，region 槽 0-26 = 右半、27-53 = 左半
 *       （与点击哪侧无关，几何属性）。提交时扫水平邻居找互补半 → 按每半坐标各写一条记录；
 *       27 槽（单半）则写当前半 + 删陈旧伙伴键（迁移）。</li>
 *   <li><b>末影分流</b>：{@code minecraft:ender_chest} → 顶层 {@code enderInventory} 玩家态
 *       （27 槽），不写 per-pos 记录。</li>
 *   <li><b>维度归属</b>（v2.32）：会话绑定（bind）瞬间取 {@code mc.level.dimension()} 记入会话；
 *       提交时把 per-pos 记录归到该维的桶（open→commit 同维，跨维传送不可能发生在容器菜单打开时）。</li>
 *   <li><b>物品序列化</b>：{@link ItemStack#CODEC} + {@code NbtOps}（与记忆侧解析对称，
 *       1.21.11 无 parse/save 便捷方法）。</li>
 * </ol>
 */
public final class ContainerMemoryTracker {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 容器族：与 §5.2.2 采集/分类清单同源（捕获族 = BlockEntityFieldPolicy.STRIP 的容器行）。 */
    private static final Set<String> CONTAINER_FAMILY = Set.of(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:ender_chest",
            "minecraft:barrel", "minecraft:dispenser", "minecraft:dropper", "minecraft:hopper",
            "minecraft:shulker_box", "minecraft:crafter", "minecraft:brewing_stand",
            "minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace");

    /** 仅这两者可成 double（其余都是单块容器，region = 自身槽位）。 */
    private static final Set<String> DOUBLE_CAPABLE = Set.of("minecraft:chest", "minecraft:trapped_chest");

    /** double-chest 菜单：region 槽 0-26 = 右半本地槽，27-53 = 左半本地槽（CompoundContainer 拆分）。 */
    private static final int DOUBLE_MENU_SIZE = 54;
    private static final int HALF_SIZE = 27;

    private static final ContainerMemoryStore STORE = ContainerMemoryStore.get();

    /** 当前绑定的容器会话（null = 无）。只在客户端线程访问。 */
    private static BoundSession session;

    /**
     * WS {@code container/close} 已提交过的 GUI：服务器关箱确认前该 GUI 还开着，禁止逐帧重绑
     * （否则会对同一个正在关闭的容器二次提交）。屏幕真正消失后自动清除。
     */
    private static Screen ignoreScreen;

    private ContainerMemoryTracker() {}

    // ==================== 每帧观察（由 SteveXClient END_CLIENT_TICK 注册） ====================

    /** 每帧驱动：屏幕转移 → 绑定/结束会话；会话期间缓存菜单内容。全部在主线程执行。 */
    public static void onClientTick(final Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            session = null;
            return;
        }
        Screen screen = mc.screen;
        boolean containerOpen = isBlockContainerOpen(mc);

        // 绑定会话的屏幕已变化（关闭 / 换成别的 GUI）→ 兜底提交最近缓存，结束会话
        if (session != null && screen != session.screen) {
            commitRegion(mc, session.cache, session.cacheSize);
            session = null;
        }
        // ignoreScreen（WS 已提交、等待服务器关箱确认的 GUI）消失后清除
        if (ignoreScreen != null && screen != ignoreScreen) {
            ignoreScreen = null;
        }

        // 无会话且打开了容器 GUI → 用 crosshair 绑定真实方块。容器 GUI 锁鼠标视角，crosshair 固定，
        // 未绑定时逐帧重试到命中或关闭即可（比边沿触发更抗"打开瞬间的那一帧竞态"）。
        if (session == null && containerOpen && screen != ignoreScreen) {
            bind(mc, screen);
        }

        // 会话期间刷新内容快照（关闭提交 / 兜底提交的数据源）
        if (session != null) {
            Region region = readRegion(mc);
            session.cache = region.items();
            session.cacheSize = region.size();
        }
    }

    /** 只有"方块容器"菜单才算：GUI 是容器屏幕、且菜单不是玩家背包。 */
    private static boolean isBlockContainerOpen(final Minecraft mc) {
        Player p = mc.player;
        if (p == null) return false;
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;
        AbstractContainerMenu menu = p.containerMenu;
        return !(menu instanceof InventoryMenu);
    }

    /** 新容器 GUI 刚打开时，用 crosshair 方块命中把会话绑定到真实方块（无同步"打开"事件）。 */
    private static void bind(final Minecraft mc, final Screen screen) {
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        String blockId = VisionCollector.blockId(state);
        if (!CONTAINER_FAMILY.contains(blockId) && !(state.getBlock() instanceof ShulkerBoxBlock)) return;
        // v2.32：会话维度在绑定瞬间取定（菜单打开期间不可能跨维；commit 时以它分桶）。
        String dimension = mc.level.dimension().identifier().toString();
        session = new BoundSession(pos, blockId, blockId.equals("minecraft:ender_chest"), dimension, screen);
        LOGGER.info("[Vision] Container session bound: {} at {} (dim={})", blockId, pos, dimension);
    }

    // ==================== 区域读取 ====================

    /** 打开菜单的容器区段：从第一个非玩家背包槽到玩家背包前的连续槽；序号 = 容器本地槽。 */
    private static Region readRegion(final Minecraft mc) {
        Player p = mc.player;
        if (p == null) return Region.EMPTY;
        AbstractContainerMenu menu = p.containerMenu;
        if (menu instanceof InventoryMenu) return Region.EMPTY;
        Container playerInv = p.getInventory();

        List<RegionItem> items = new ArrayList<>();
        int pos = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv) break;   // 玩家背包开始 → 容器区段结束
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) items.add(new RegionItem(pos, stack.copy()));
            pos++;
        }
        return new Region(items, pos);
    }

    // ==================== 提交入口 ====================

    /**
     * WS {@code container/close} 路径：在 {@code player.closeContainer()} 重置菜单前，
     * 同步读最终内容提交（主路径）。无绑定会话时为空操作。
     */
    public static void commitFromClose() {
        Minecraft mc = Minecraft.getInstance();
        if (session == null) return;
        Region region = readRegion(mc);   // 此刻菜单仍是目标容器（close 尚未执行）
        commitRegion(mc, region.items(), region.size());
        ignoreScreen = session.screen;    // 该 GUI 仍开着（等服务器关箱确认），禁止逐帧重绑二次提交
        session = null;
    }

    /** 组装并写入 store：按会话类型（末影 / double / 单块容器）分派，之后整文件落盘。 */
    private static void commitRegion(final Minecraft mc, final List<RegionItem> items, final int size) {
        BoundSession s = session;
        if (s == null) return;
        if (size <= 0) return;   // 菜单已不是该容器（读不到区段）→ 放弃，避免空快照覆盖真实记录/末影态
        try {
            if (s.ender) {
                writeEnder(mc, s, items);
            } else if (DOUBLE_CAPABLE.contains(s.blockId)) {
                if (size == DOUBLE_MENU_SIZE) {
                    writeDouble(mc, s, items);
                } else if (size == HALF_SIZE) {
                    writeSingleOrMigrate(mc, s, items);
                } else {
                    LOGGER.warn("[Vision] Container session at {}: unexpected region size {}; skip", s.pos, size);
                }
            } else {
                // 单块非 double 容器：本地槽 = region 序号
                writeRecord(mc, s.dimension, s.pos, s.blockId, items);
            }
            STORE.save();
        } catch (RuntimeException e) {
            LOGGER.warn("[Vision] Container session commit at {} failed: {}", s.pos, e.getMessage());
        }
    }

    // ==================== 组装 ====================

    /**
     * 末影箱会话（§5.2.2 定案 D）：覆写顶层 enderInventory 玩家态（27 槽本地槽 = region 序号），
     * 同时写一条该绑定格的 per-pos 出现记录（{@code items=[]}），保证记忆世界在该坐标有可开的
     * 末影箱外壳（即使地形视觉漏采），打开它即读到这份全局末影箱记忆。
     */
    private static void writeEnder(final Minecraft mc, final BoundSession s, final List<RegionItem> items) {
        List<ContainerMemoryStore.SlotTag> tags = new ArrayList<>();
        for (RegionItem it : items) {
            if (it.slot() >= 0 && it.slot() < HALF_SIZE) {
                CompoundTag tag = encodeItem(it.stack());
                if (tag != null) tags.add(new ContainerMemoryStore.SlotTag(it.slot(), tag));
            }
        }
        STORE.setEnder(tags);
        writeRecord(mc, s.dimension, s.pos, s.blockId, List.of());
        LOGGER.info("[Vision] Committed ender inventory ({} stacks) + occurrence at {} (dim={})",
                tags.size(), s.pos, s.dimension);
    }

    /**
     * double-chest（54 槽菜单）会话：region 0-26 = 右半、27-53 = 左半。扫水平邻居找互补半，
     * 按两半各自坐标各写一条记录（每半本地槽 = region 槽 − 半偏移）。伙伴找不到则退化为
     * 写绑定半自身内容 + 告警。
     */
    private static void writeDouble(final Minecraft mc, final BoundSession s, final List<RegionItem> items) {
        Level level = mc.level;
        BlockState boundState = level.getBlockState(s.pos);
        BlockPos partnerPos = findPartner(level, s.pos, s.blockId, boundState);
        if (partnerPos == null) {
            // 菜单 54 但世界无伙伴（理论不可达）：退化为只写绑定半（按绑定半类型取侧）
            List<RegionItem> own = boundType(boundState) == ChestType.RIGHT
                    ? takeSide(items, 0)
                    : takeSide(items, HALF_SIZE);
            writeRecord(mc, s.dimension, s.pos, s.blockId, own);
            LOGGER.warn("[Vision] Double menu at {} but no partner chest found; wrote bound half only", s.pos);
            return;
        }

        BlockState rightState;
        BlockState leftState;
        BlockPos rightPos;
        BlockPos leftPos;
        if (boundType(boundState) == ChestType.RIGHT) {
            rightState = boundState;        rightPos = s.pos;
            leftState = level.getBlockState(partnerPos);  leftPos = partnerPos;
        } else {
            rightState = level.getBlockState(partnerPos); rightPos = partnerPos;
            leftState = boundState;         leftPos = s.pos;
        }

        writeRecord(mc, s.dimension, rightPos, VisionCollector.blockId(rightState), takeSide(items, 0));
        writeRecord(mc, s.dimension, leftPos, VisionCollector.blockId(leftState), takeSide(items, HALF_SIZE));
        LOGGER.info("[Vision] Committed double {} halves {} / {} (dim={})",
                s.blockId, rightPos, leftPos, s.dimension);
    }

    /**
     * 单半（27 槽）会话：可能是"本来单 chest"，也可能是 double 被拆成一半的残留半。
     * 写当前半内容，并按 chest 连接方向删掉陈旧伙伴键（迁移，§5.2.3 ③ 27 槽情形）。
     */
    private static void writeSingleOrMigrate(final Minecraft mc, final BoundSession s, final List<RegionItem> items) {
        BlockState state = mc.level.getBlockState(s.pos);
        if (!state.isAir() && VisionCollector.blockId(state).equals(s.blockId)
                && chestType(state) != null && chestType(state) != ChestType.SINGLE) {
            Direction conn = ChestBlock.getConnectedDirection(state);
            STORE.remove(s.dimension, posKey(s.pos.relative(conn)));
        }
        writeRecord(mc, s.dimension, s.pos, s.blockId, items);
    }

    /** 写一条 per-pos 记录：本地槽 = region 序号，blockId/state/typeId 取世界当前方块。 */
    private static void writeRecord(final Minecraft mc, final String dimension, final BlockPos pos,
                                    final String blockId, final List<RegionItem> items) {
        BlockState state = mc.level.getBlockState(pos);
        Map<String, String> props = state.isAir() ? Map.of() : VisionCollector.stateProps(state);
        List<ContainerMemoryStore.SlotTag> tags = new ArrayList<>();
        for (RegionItem it : items) {
            CompoundTag tag = encodeItem(it.stack());
            if (tag != null) tags.add(new ContainerMemoryStore.SlotTag(it.slot(), tag));
        }
        STORE.upsert(dimension, posKey(pos), typeIdFor(state, blockId), blockId, props, tags);
    }

    // ==================== 辅助 ====================

    /** 取 region 里属于某一半的条目，并换算成半内本地槽。 */
    private static List<RegionItem> takeSide(final List<RegionItem> items, final int offset) {
        List<RegionItem> side = new ArrayList<>();
        for (RegionItem it : items) {
            if (it.slot() >= offset && it.slot() < offset + HALF_SIZE) {
                side.add(new RegionItem(it.slot() - offset, it.stack()));
            }
        }
        return side;
    }

    /** 在 4 个水平邻居里找与绑定半互补的同种 chest（同 block、facing 相对、type 互补）。 */
    private static BlockPos findPartner(final Level level, final BlockPos pos,
                                        final String blockId, final BlockState boundState) {
        ChestType boundType = chestType(boundState);
        if (boundType == null || boundType == ChestType.SINGLE) return null;
        Direction facing = boundState.getValue(ChestBlock.FACING);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos np = pos.relative(dir);
            if (!level.isLoaded(np)) continue;
            BlockState ns = level.getBlockState(np);
            if (ns.isAir()) continue;
            if (!VisionCollector.blockId(ns).equals(blockId)) continue;
            ChestType nt = chestType(ns);
            if (nt == null || nt == ChestType.SINGLE) continue;
            if (boundType != nt && ns.getValue(ChestBlock.FACING) == facing) return np;
        }
        return null;
    }

    /** 读取 chest 的 type 属性；非 chest 方块返回 null。 */
    private static ChestType chestType(final BlockState state) {
        if (state.getBlock() instanceof ChestBlock) {
            return state.getValue(ChestBlock.TYPE);
        }
        return null;
    }

    private static ChestType boundType(final BlockState state) {
        return chestType(state);
    }

    /** blockId 对应方块实体注册 id；世界方块在时反查注册表，否则用 blockId 兜底。 */
    private static String typeIdFor(final BlockState state, final String fallbackBlockId) {
        if (state != null && !state.isAir()) {
            for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
                if (type.isValid(state)) {
                    Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
                    if (id != null) return id.toString();
                }
            }
        }
        return fallbackBlockId;
    }

    /** ItemStack → {id,count,components?}（ItemStack.CODEC + NbtOps，与记忆侧对称）。失败返回 null。 */
    private static CompoundTag encodeItem(final ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            HolderLookup.Provider regs = mc.level.registryAccess();
            DataResult<net.minecraft.nbt.Tag> res =
                    ItemStack.CODEC.encodeStart(regs.createSerializationContext(NbtOps.INSTANCE), stack);
            Optional<net.minecraft.nbt.Tag> tag = res.result();
            if (tag.isPresent() && tag.get() instanceof CompoundTag c) return c;
        } catch (RuntimeException e) {
            LOGGER.debug("[Vision] Failed to encode item {}: {}", stack, e.getMessage());
        }
        return null;
    }

    private static String posKey(final BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 数据结构 ====================

    /** 绑定的打开会话（只在客户端线程访问）。 */
    private static final class BoundSession {
        final BlockPos pos;
        final String blockId;
        final boolean ender;
        /** v2.32：会话所属维 id（bind 瞬间取定；per-pos 记录按它分桶）。 */
        final String dimension;
        final Screen screen;
        List<RegionItem> cache = List.of();
        int cacheSize;

        BoundSession(final BlockPos pos, final String blockId, final boolean ender,
                     final String dimension, final Screen screen) {
            this.pos = pos;
            this.blockId = blockId;
            this.ender = ender;
            this.dimension = dimension;
            this.screen = screen;
        }
    }

    /** 区域里的一格：容器本地槽 + ItemStack 快照（缓存拷贝）。 */
    private record RegionItem(int slot, ItemStack stack) {}

    /** 一次菜单区段读取：非空条目 + 区段槽数（含空格）。 */
    private record Region(List<RegionItem> items, int size) {
        static final Region EMPTY = new Region(List.of(), 0);
    }
}
