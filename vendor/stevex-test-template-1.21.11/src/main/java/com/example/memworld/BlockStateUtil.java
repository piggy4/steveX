package com.example.memworld;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

/**
 * 方块状态序列化工具 —— 与 stevex 视觉采集器保存的格式互操作。
 *
 * <p>源文件中每个方块实体附带：
 * <ul>
 *   <li>{@code block}：方块注册名，如 {@code minecraft:chest}</li>
 *   <li>{@code state}：状态属性表，如 {@code {"facing":"east","waterlogged":"false"}}</li>
 * </ul>
 */
public final class BlockStateUtil {

    private BlockStateUtil() {}

    /** 从保存的 blockId + 状态属性重建 BlockState；未知方块 / 非法属性时回退默认状态。 */
    public static BlockState fromSaved(final String blockId, final Map<String, String> props) {
        if (blockId == null || blockId.isBlank()) return Blocks.AIR.defaultBlockState();

        Identifier id = Identifier.tryParse(blockId);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) return Blocks.AIR.defaultBlockState();

        BlockState state = block.defaultBlockState();
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        for (Map.Entry<String, String> e : props.entrySet()) {
            Property<?> property = definition.getProperty(e.getKey());
            if (property == null) continue;
            state = setValue(state, property, e.getValue());
        }
        return state;
    }

    /**
     * 实心（满形状）且不透明 —— 减量（§7.11）只删这类方块：只有它们会被深度缓冲"看到被挡/被挖掉"，
     * 也只有它们会被写进 cells 文件（{@link MemoryCellReporter}）与接受删除（{@link DeletionApplier}）。
     * 玻璃/栅栏/压力板等非满形状或透明方块不可删（欠删无害，v2.22 同，接受）。
     */
    public static boolean isSolidOpaque(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir()) return false;
        return Block.isShapeFullBlock(state.getShape(level, pos)) && state.canOcclude();
    }

    /** 水族流体（水源/流动同族）——v2.36：Fabulous 下写 translucent 目标、归 translucent 段。 */
    public static boolean isWaterFluid(final BlockState state) {
        return !state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER);
    }

    /** 非水流体（岩浆等）——v2.36：写 main 深度、归 main/opaque 段（Fabulous 下岩浆恒写 main，见 §7.12）。 */
    public static boolean isNonWaterFluid(final BlockState state) {
        return !state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER);
    }

    /**
     * 满格透明方块（v2.36，§7.12 translucent 段非流体成员）——玻璃块/染色玻璃/冰/遮光玻璃/蜂蜜块/
     * 史莱姆块等 {@code isShapeFullBlock} 且 {@code !canOcclude} 的满形状透明方块：真实世界它们写满整格、
     * 在场即自身近面截断射线，与 opaque 判据同前提（§7.12：限制项是"形状是否填满整格"，与透明度无关）。
     * 玻璃板/栅栏/压力板/红石线等非满形状被挡在段外（欠删，§7.12 边界①，双保险防误删）。
     */
    public static boolean isFullTransparentCell(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) return false;
        // v2.37 修补（设计 §4.1 第 2 步）：RenderShape == INVISIBLE 的方块（air/barrier/bubble_column/
        // end_gateway/end_portal/light/liquid/structure_void/moving_piston）渲染足迹为零——若同时满足
        // isShapeFullBlock && !canOcclude（barrier/light/structure_void 等正是如此），旧口径会把它们塞进
        // translucent 段并用整格 bbox 判 → 对零足迹的东西过投票。此谓词独立于本版功能，v2.23/v2.36 亦适用。
        if (!hasModelRenderShape(state)) return false;
        return Block.isShapeFullBlock(state.getShape(level, pos)) && !state.canOcclude();
    }

    // ==================== v2.37（§7.13 / 非满形状方块减量） ====================

    /** 该方块是否由方块模型系统渲染（{@code RenderShape.MODEL}）。{@code INVISIBLE} = 渲染足迹为零。 */
    public static boolean hasModelRenderShape(final BlockState state) {
        return state.getRenderShape() == RenderShape.MODEL;
    }

    /**
     * <b>信号缺失排除表</b>（v2.37 设计 §4.1 第 6 步 / §2.2；v2.38 批 2 扩充，设计 §14.4）—— 深度/几何
     * 判据对它们<b>不可用</b>的方块族，也是全系统<b>唯一</b>的族定义处。
     *
     * <p>进表即"该格不走深度判据，而走<b>状态直读</b>通道"：记忆侧把它们上报进 cells 的信号缺失段
     * （{@code MemoryCellReporter:292}），采集侧 {@code SignalLossCorrector} 直接读真实世界状态裁决
     * （区块已加载 + 读到空气 ⇒ 缺席）。<b>采集侧刻意不持平行名单</b>——族由本表上报什么定义，
     * 两份名单一旦漂移就会出现"哪些格可删"与"哪些格被上报"错配（见 {@code SignalLossCorrector} javadoc）。
     *
     * <p><b>三类入表理由</b>（v2.38 §14 扫测的三输入分桶 + §17 补遗）：
     * <ol>
     *   <li><b>不写已读深度场</b>（v2.37 原表）：绊线 / 绊线钩在 Fabulous 下画进 weather 目标，
     *       两支深度场都不写 ⇒ 活体不产生可读信号 ⇒ 判"消失"必误删活体；</li>
     *   <li><b>有 BE 且非满格</b>（v2.38 批 2 新增 148 个）：这批（箱子族 / 告示牌 / 床 / 旗帜 / 头颅 /
     *       装饰陶罐 / 钟 / 讲台 / 架子 / 铜傀儡像…）几何在 <b>BE renderer</b> 里而不在方块模型系统内
     *       （模型文件无 {@code elements}，甚至不存在）⇒ 几何段过滤恒 0 票（丙），对有渲染器者更糟：
     *       渲染足迹 ⊄ 模型几何 ⇒ 判据会<b>误删活体</b>（乙2）。同时它们非满格 ⇒ 走不了整格现段，
     *       又被 {@link #isShapedDeletableContent} 第 ④ 步（有 BE 即排除）挡住 ⇒ <b>今天在任何段之外</b>。
     *       唯一切实可行的判据就是状态直读。</li>
     *   <li><b>几何可判、但无段落可收</b>（v2.38 §17 补遗新增 6 个：{@code brewing_stand} /
     *       {@code hopper} / {@code comparator} / {@code daylight_detector} / {@code sculk_sensor} /
     *       {@code calibrated_sculk_sensor}）：非满格 ⇒ 不入整格段；有 BE ⇒ 被第 ④ 步挡出几何段；
     *       又不在本表 ⇒ 落进 {@code MemoryCellReporter} 的"其余"列。而它们<b>不是零足迹</b>
     *       （有模型几何、照常渲染）⇒ 敲掉后镜像里留<b>可见幽灵且永不消失</b>，其 BE 记录也永不被 F1 修剪
     *       （该格从不进 deletions / signalLossDeletions）——即 §1.1 那条"记录累积 ⇒ 复活"的机理原样保留。
     *       <b>为何走本表而不塞进几何段</b>（§17.2）：它们的形状恰是几何判据最弱的一类（1/16 平板 /
     *       贴地平面 ⇒ 极小足迹 ⇒ 占屏 &lt; 2 像素 + δ 可行窗口最先闭合），而状态直读与距离、像素数、
     *       渲染配置<b>全无关</b>；且加表零新增代码路径。</li>
     * </ol>
     *
     * <p>本表与几何段<b>定义域互不相交</b>：{@link #isShapedDeletableContent} 第 ⑤ 步就是
     * {@code !isSignalLossBlock}，故入表者自动退出几何谓词，两条通道不可能抢同一格（§14.6 定义域互斥）。
     * <b>版本升级后须重跑</b> {@code scan_be_carriers.py --emit-java}（工作区根，产出本表内容）。
     */
    private static final Set<Block> SIGNAL_LOSS_BLOCKS = Set.of(
            Blocks.TRIPWIRE,        // 绊线：Fabulous 下画进 weather 目标、不写 main/translucent 深度
            Blocks.TRIPWIRE_HOOK,   // 绊线钩（本体几何正常，但其激活/信号语义与绊线同族，一并欠删）

            // ==================== v2.38 批 2（§14.4）：乙2 ∪ 丙 = 148 个 BE 载体方块 ====================
            // 由 scan_be_carriers.py --emit-java 机械产出（字段名取自 Blocks.java 解析，勿手写增删）。
            // banner（32）
            Blocks.BLACK_BANNER, Blocks.BLACK_WALL_BANNER, Blocks.BLUE_BANNER,
            Blocks.BLUE_WALL_BANNER, Blocks.BROWN_BANNER, Blocks.BROWN_WALL_BANNER,
            Blocks.CYAN_BANNER, Blocks.CYAN_WALL_BANNER, Blocks.GRAY_BANNER,
            Blocks.GRAY_WALL_BANNER, Blocks.GREEN_BANNER, Blocks.GREEN_WALL_BANNER,
            Blocks.LIGHT_BLUE_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER, Blocks.LIGHT_GRAY_BANNER,
            Blocks.LIGHT_GRAY_WALL_BANNER, Blocks.LIME_BANNER, Blocks.LIME_WALL_BANNER,
            Blocks.MAGENTA_BANNER, Blocks.MAGENTA_WALL_BANNER, Blocks.ORANGE_BANNER,
            Blocks.ORANGE_WALL_BANNER, Blocks.PINK_BANNER, Blocks.PINK_WALL_BANNER,
            Blocks.PURPLE_BANNER, Blocks.PURPLE_WALL_BANNER, Blocks.RED_BANNER,
            Blocks.RED_WALL_BANNER, Blocks.WHITE_BANNER, Blocks.WHITE_WALL_BANNER,
            Blocks.YELLOW_BANNER, Blocks.YELLOW_WALL_BANNER,
            // bed（16）
            Blocks.BLACK_BED, Blocks.BLUE_BED, Blocks.BROWN_BED,
            Blocks.CYAN_BED, Blocks.GRAY_BED, Blocks.GREEN_BED,
            Blocks.LIGHT_BLUE_BED, Blocks.LIGHT_GRAY_BED, Blocks.LIME_BED,
            Blocks.MAGENTA_BED, Blocks.ORANGE_BED, Blocks.PINK_BED,
            Blocks.PURPLE_BED, Blocks.RED_BED, Blocks.WHITE_BED,
            Blocks.YELLOW_BED,
            // bell（1）
            Blocks.BELL,
            // campfire（2）
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
            // chest（9，含全部铜箱）
            Blocks.CHEST, Blocks.COPPER_CHEST, Blocks.EXPOSED_COPPER_CHEST,
            Blocks.OXIDIZED_COPPER_CHEST, Blocks.WAXED_COPPER_CHEST, Blocks.WAXED_EXPOSED_COPPER_CHEST,
            Blocks.WAXED_OXIDIZED_COPPER_CHEST, Blocks.WAXED_WEATHERED_COPPER_CHEST, Blocks.WEATHERED_COPPER_CHEST,
            // conduit（1）
            Blocks.CONDUIT,
            // copper_golem_statue（8）
            Blocks.COPPER_GOLEM_STATUE, Blocks.EXPOSED_COPPER_GOLEM_STATUE, Blocks.OXIDIZED_COPPER_GOLEM_STATUE,
            Blocks.WAXED_COPPER_GOLEM_STATUE, Blocks.WAXED_EXPOSED_COPPER_GOLEM_STATUE, Blocks.WAXED_OXIDIZED_COPPER_GOLEM_STATUE,
            Blocks.WAXED_WEATHERED_COPPER_GOLEM_STATUE, Blocks.WEATHERED_COPPER_GOLEM_STATUE,
            // decorated_pot（1）
            Blocks.DECORATED_POT,
            // enchanting_table（1）
            Blocks.ENCHANTING_TABLE,
            // ender_chest（1）
            Blocks.ENDER_CHEST,
            // hanging_sign（24）
            Blocks.ACACIA_HANGING_SIGN, Blocks.ACACIA_WALL_HANGING_SIGN, Blocks.BAMBOO_HANGING_SIGN,
            Blocks.BAMBOO_WALL_HANGING_SIGN, Blocks.BIRCH_HANGING_SIGN, Blocks.BIRCH_WALL_HANGING_SIGN,
            Blocks.CHERRY_HANGING_SIGN, Blocks.CHERRY_WALL_HANGING_SIGN, Blocks.CRIMSON_HANGING_SIGN,
            Blocks.CRIMSON_WALL_HANGING_SIGN, Blocks.DARK_OAK_HANGING_SIGN, Blocks.DARK_OAK_WALL_HANGING_SIGN,
            Blocks.JUNGLE_HANGING_SIGN, Blocks.JUNGLE_WALL_HANGING_SIGN, Blocks.MANGROVE_HANGING_SIGN,
            Blocks.MANGROVE_WALL_HANGING_SIGN, Blocks.OAK_HANGING_SIGN, Blocks.OAK_WALL_HANGING_SIGN,
            Blocks.PALE_OAK_HANGING_SIGN, Blocks.PALE_OAK_WALL_HANGING_SIGN, Blocks.SPRUCE_HANGING_SIGN,
            Blocks.SPRUCE_WALL_HANGING_SIGN, Blocks.WARPED_HANGING_SIGN, Blocks.WARPED_WALL_HANGING_SIGN,
            // lectern（1）
            Blocks.LECTERN,
            // shelf（12）
            Blocks.ACACIA_SHELF, Blocks.BAMBOO_SHELF, Blocks.BIRCH_SHELF,
            Blocks.CHERRY_SHELF, Blocks.CRIMSON_SHELF, Blocks.DARK_OAK_SHELF,
            Blocks.JUNGLE_SHELF, Blocks.MANGROVE_SHELF, Blocks.OAK_SHELF,
            Blocks.PALE_OAK_SHELF, Blocks.SPRUCE_SHELF, Blocks.WARPED_SHELF,
            // sign（24）
            Blocks.ACACIA_SIGN, Blocks.ACACIA_WALL_SIGN, Blocks.BAMBOO_SIGN,
            Blocks.BAMBOO_WALL_SIGN, Blocks.BIRCH_SIGN, Blocks.BIRCH_WALL_SIGN,
            Blocks.CHERRY_SIGN, Blocks.CHERRY_WALL_SIGN, Blocks.CRIMSON_SIGN,
            Blocks.CRIMSON_WALL_SIGN, Blocks.DARK_OAK_SIGN, Blocks.DARK_OAK_WALL_SIGN,
            Blocks.JUNGLE_SIGN, Blocks.JUNGLE_WALL_SIGN, Blocks.MANGROVE_SIGN,
            Blocks.MANGROVE_WALL_SIGN, Blocks.OAK_SIGN, Blocks.OAK_WALL_SIGN,
            Blocks.PALE_OAK_SIGN, Blocks.PALE_OAK_WALL_SIGN, Blocks.SPRUCE_SIGN,
            Blocks.SPRUCE_WALL_SIGN, Blocks.WARPED_SIGN, Blocks.WARPED_WALL_SIGN,
            // skull（14）
            Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD, Blocks.DRAGON_HEAD,
            Blocks.DRAGON_WALL_HEAD, Blocks.PIGLIN_HEAD, Blocks.PIGLIN_WALL_HEAD,
            Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD, Blocks.SKELETON_SKULL,
            Blocks.SKELETON_WALL_SKULL, Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL,
            Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD,
            // trapped_chest（1）
            Blocks.TRAPPED_CHEST,

            // ==================== v2.38 §17 补遗：乙1 = 6 个（非满格 + 有模型几何 + 无渲染器） ====================
            // 由 scan_be_carriers.py --emit-java 的 ② 段机械产出（勿手写增删）。入表理由 ③（见上方 javadoc）：
            // 三处谓词合围（第 ④ 步"有 BE 即排除" + 非满格不入整格段 + 不在表）⇒ 改走状态直读。
            // 载荷核查（§17.4）：6 个全在 BlockEntityFieldPolicy 的 STRIP 表 ⇒ block_entities.nbt 里
            // 本就无内容 ⇒ 即时修剪不丢任何东西；hopper / brewing_stand 的物品若被采过则在 containers.nbt，
            // 由决策 G 的延迟修剪 + 决策 J 的墓碑保护（与箱子族同路）。
            Blocks.BREWING_STAND,              // brewing_stand
            Blocks.CALIBRATED_SCULK_SENSOR,    // calibrated_sculk_sensor
            Blocks.COMPARATOR,                 // comparator
            Blocks.DAYLIGHT_DETECTOR,          // daylight_detector
            Blocks.HOPPER,                     // hopper
            Blocks.SCULK_SENSOR                // sculk_sensor
    );

    /** 是否属于信号缺失族（设计 §4.1 第 6 步）。 */
    public static boolean isSignalLossBlock(final Block block) {
        return SIGNAL_LOSS_BLOCKS.contains(block);
    }

    /**
     * v2.37（设计 §4.1）<b>单一上报谓词</b>——记忆侧几何段上报、采集侧几何求交、{@link DeletionApplier}
     * 放行守卫共用同一把尺（同文件、同侧，杜绝口径漂移）：
     *
     * <ol>
     *   <li>该格有<b>方块本体</b>（非空气）；纯水格/纯岩浆格由 {@code getRenderShape() != MODEL} 自动排除
     *       （{@code LiquidBlock} 返回 {@code INVISIBLE}），故含水非满块（有方块本体）仍是候选；</li>
     *   <li>{@code RenderShape == MODEL}（排除 {@code INVISIBLE} 族）；</li>
     *   <li><b>非满形状</b>——满格方块走 §7.11/§7.12 现段（整格盒是它的退化情形，进几何段零收益）；</li>
     *   <li>该格无方块实体（{@code getBlockEntity(pos) == null}）——几何在 BE renderer 里，不在方块模型
     *       系统内，{@code collectParts} 的 quad 数可能为 0 或只盖住底座 = <b>几何低估</b>（唯一方向错的
     *       一类）；</li>
     *   <li>不在信号缺失表（绊线/绊线钩）。</li>
     * </ol>
     *
     * <p><b>几何非空（设计 §4.1 第 5 步）不在此判</b>——它需要客户端烘焙模型（{@code ModelGeometryCache}），
     * 由 {@code MemoryCellReporter.computeCells} 在拿到 quad 清单后自动兜底（quad 数 = 0 → 不进几何段）。
     *
     * <p><b>判定次序是承重的</b>（设计 §4.4）：调用方必须<b>先形状后流体</b>——先问本谓词，命中者直接入
     * 几何段（无视 waterlogged），只有非候选格才走 §7.12 现分支。否则 waterlogged 栅栏会先被 translucent
     * 水段吞走，复现 v2.36 的"活本体被整格置空"误删缺陷。
     */
    public static boolean isShapedDeletableContent(final Level level, final BlockPos pos, final BlockState state) {
        if (state.isAir()) return false;                                   // ① 有方块本体
        if (!hasModelRenderShape(state)) return false;                     // ② RenderShape.MODEL
        if (isSignalLossBlock(state.getBlock())) return false;             // ⑤ 信号缺失族
        if (Block.isShapeFullBlock(state.getShape(level, pos))) return false; // ③ 满格走现段
        return level.getBlockEntity(pos) == null;                          // ④ 无 BE
    }

    /**
     * v2.36 减量可删内容（§7.12）：实心不透明 ∪ 流体（水/岩浆）∪ 满格透明 —— 与 cells 上报口径一致。
     * {@link DeletionApplier} 的删除放行守卫用它：只删"当前内容属于 cells 可报类别"的格（内容对得上才删），
     * 薄物/非满形状（玻璃板/栅栏/压力板/红石线等）被挡在可删集外，防 §7.11 边界①误删回归。
     */
    public static boolean isDeletableContent(final Level level, final BlockPos pos, final BlockState state) {
        final FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) return true; // 水 / 岩浆均在可删集（被证明消失即可置空）
        if (state.isAir()) return false;
        return isSolidOpaque(level, pos, state) || isFullTransparentCell(level, pos, state);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState setValue(
            final BlockState state, final Property<?> property, final String value
    ) {
        Property<T> typed = (Property<T>) property;
        Optional<T> parsed = typed.getValue(value);
        if (parsed.isEmpty()) {
            // 保存端用的是 String.valueOf(枚举常量)，可能存的是枚举名（如 "SINGLE"），
            // 而 getValue 只匹配 getSerializedName()（如 "single"）。这里兜底按枚举名匹配。
            for (T possible : typed.getPossibleValues()) {
                if (String.valueOf(possible).equals(value)) {
                    parsed = Optional.of(possible);
                    break;
                }
            }
        }
        return parsed.map(v -> state.setValue(typed, v)).orElse(state);
    }
}
