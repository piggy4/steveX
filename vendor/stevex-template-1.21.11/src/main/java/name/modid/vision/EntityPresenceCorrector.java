package name.modid.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v2.37（设计 §7.14）<b>实体在场校正通道</b> —— 对记忆侧上报的<b>冻结实体占用格</b>直读真实世界，
 * 判定"该格现实中还有没有实体"，产出与几何段<b>并列</b>的裁决清单（{@code terrain.nbt} 的
 * {@code entityDeletions} 键）。
 *
 * <p><b>为什么需要它</b>（§7.14 根因）：实体删除的旧判据要求冻结实体 AABB 覆盖的<b>全部</b>格都被
 * 深度判据证明为空，而 {@code DeletionJudge} 的首句 {@code currentTerrain.contains(pos) → continue}
 * 使<b>可见方块所在格永不入 {@code deletions}}</b> ⇒ 与非满方块（营火 / 耕地 / 雪层 / 台阶…）共格的
 * 冻结实体<b>永久残留</b>；落点在整方块顶则落入空气格、可证明为空、正常删——这正是现场"有概率失效"
 * 的全部含义。根因不是漏了某个谓词，而是<b>证据类型选错</b>：{@code deletions} 证明的是"这格没有
 * 实心不透明方块"，而实体可以存在于空气格。<b>"该格现实中还有没有实体"本身是可直接读的事实</b>，
 * 与深度场、视锥、图形配置全无关——与 §15 信号缺失族那条路线同构：换证据类型，而非修补谓词。
 *
 * <p><b>为什么实体格必须与 {@code deletions} 拆开（不并入）</b>：{@code deletions} 对实体不仅不健全、
 * 而且有一面是"误删活体"的隐蔽通道——实体渲染几何远小于其 AABB，格内空余区的射线会投票"越过"，
 * 于是"活着的实体"也能被深度证据判成"消失"。本通道被授权<b>独占</b>实体裁决后，那条通道被彻底撤出
 * （实体格不再进 main 段、不再喂深度判据），实体判据从此与 Fabulous / PBO / render distance 解耦。
 *
 * <p><b>四条硬规则</b>（每条都是"方向错就误删"的那种，与 §15.4 P1~P4 逐条对应）：
 * <ol>
 *   <li><b>读不到 ≠ 不存在（P1）</b>：区块未加载时 {@code getEntities} 返回空列表，与"真的没有实体"
 *       <b>完全同形</b>（{@code LevelEntityGetter.get} 只扫已加载的实体段）。故
 *       {@code level.isLoaded(pos)} 是本类<b>唯一</b>新增前提，且必须是主守卫——未加载一律不裁决
 *       （欠删）。这是本通道唯一的"假空"来源，也是 {@link Stats#unloaded} 存在的理由。</li>
 *   <li><b>不得用"本次可见集里没有它"当证据（P2）</b>：证据是世界直读，与"看不看得见"无关。候选集由
 *       记忆侧给定，本类<b>不枚举、不做视锥筛选</b>——{@code visibleEntityCells} 只作为<b>恒不应触发
 *       的双保险</b>参与（见 {@link Stats#visibleSkipped}），不作为覆盖条件。</li>
 *   <li><b>只保留"删除"半个动作（P3）</b>：查到实体一律<b>不裁决</b>——包括"换成了别的实体"。本通道
 *       只被授权证明"不在"；镜像里该格的内容由正向观测通道在下一帧覆盖。</li>
 *   <li><b>只在真实世界采集帧成立（P4）</b>：调用方必须保证本帧读的是真实世界（§15.4 P4 同款）。</li>
 * </ol>
 *
 * <p><b>本地玩家必须从查询里排除（承重，别删）</b>：两个理由，缺一不可——
 * <ol>
 *   <li><b>不排除就修不好本次事故</b>：报告的复现路径是"营火弹出掉落物 → <b>玩家走过去拾取</b>"，
 *       拾取那一刻玩家就站在该格上。若玩家自身算"占用"，该格永远判不出"空" ⇒ 这副镜像副本永不消失
 *       ——正是要修的 bug 原样复现。</li>
 *   <li><b>"玩家站在这儿"对实体裁决零信息量</b>：本地玩家是<b>agent 自身</b>，在镜像里由镜像世界
 *       自己的玩家 + §7.11 的"相机格"条款代表，<b>不是</b>一个冻结实体。判断"我那个冻结副本还在不在"
 *       与"我本人站不站在这儿"无关。</li>
 * </ol>
 * 残余：若镜像中确有本地玩家的冻结副本（仅当采集侧在第三人称 / 睡眠相机下把 LocalPlayer 采进快照），
 * 它不再被"我站在这儿"挡住——其可见帧仍由记忆侧 {@code currentEntityUuids} 跳过集挡住；不可见帧
 * 与 §7.11 v2.22"相机格无条件证据"的既有语义一致（把自我副本当作非镜像内容清掉），本版不额外处理。
 *
 * <p><b>为什么查询盒要外扩 {@link #QUERY_EPSILON}</b>：{@code AABB.intersects} 是<b>严格</b>比较
 * （{@code min < other.max && max > other.min}），零体积 AABB（marker 盔甲架等）恰好落在格边界时
 * 与格盒<b>不相交</b> ⇒ 活体会被误判成"不存在"。故外扩一个极小量把退化情形捞回来。
 * <b>不外扩到"容忍位移"</b>：实体走开后在原格残留的镜像副本本来就该删（与旧判据的整方块落点行为
 * 一致），拿宽容差去救它反而会把它永久留住。
 *
 * <p><b>纯函数</b>：不落盘、不打日志、不依赖本类以外的可变状态——记账逐档回给调用方，由
 * {@code ObjectResolver} 统一打（与 {@code SignalLossCorrector} / {@code DeletionJudge} 同款）。
 * 可在渲染线程执行（纯世界读，与同线程的 {@code getBlockState} 同类）。
 *
 * <p><b>调用前提</b>：{@code candidates} 必须是<b>与当前帧同维</b>的 cells 实体段（调用方
 * {@code ObjectResolver} 已按维标签过滤，与几何段 / 信号缺失段同一道门 {@code dimensionOk}）；
 * 跨维喂入会拿本维的实体表去判别的维的坐标——那是本类唯一防不住的方向。
 */
public final class EntityPresenceCorrector {

    /** 查询盒外扩量（见类 javadoc"为什么查询盒要外扩"）：只用来救零体积 AABB 在格边界上的退化相交，
     *  远小于任何实体的可感知位移，故不会把"走开 0.001 格"的残留副本留住。 */
    private static final double QUERY_EPSILON = 1.0e-3;

    /** 恒真的实体过滤器：本通道与实体种类 / 状态无关，只要有实体在那格里就一律不裁决（P3）。 */
    private static final Predicate<Entity> ANY_ENTITY = e -> true;

    private EntityPresenceCorrector() {
    }

    /**
     * 一次校正的结果。
     *
     * @param absent 被裁决"该格现实中已无实体"的格（区块已加载 ∧ 查询为空）——进 {@code terrain.nbt} 的
     *               {@code entityDeletions} 并列键，由记忆侧 {@code DeletionApplier} 的实体通道执行
     * @param stats  逐格记账（{@link Stats#unloaded} 是 P1 那个坑的唯一在线指示器）
     */
    public record Result(List<BlockPos> absent, Stats stats) {}

    /**
     * 逐格记账。
     *
     * <p>与 {@code SignalLossCorrector.Stats} 的约定不同：本类<b>单列</b> {@code absent} 一档，而不靠
     * "差额即缺席"——本通道里"发现实体"（{@code occupied}，常态）与"裁决缺席"（{@code absent}，动作）
     * 是<b>两个都值得直接看</b>的数，尤其在排查"该删不删"时要一眼看出是"没候选"还是"候选都被占着"。
     *
     * @param candidates     本轮候选格数（记忆侧上报的冻结实体占用格）
     * @param unloaded       因区块未加载而跳过的格数。<b>非零是正常且必需的</b>（玩家一走远就会发生）；
     *                       它若恒为 0 而候选恒非空，反而说明 {@code isLoaded} 守卫没生效（P1）。
     *                       本地玩家为 null（不在世界内）时整批也计入本档——同属"读不可信 ⇒ 不裁决"
     * @param occupied       查到实体的格数（在场 / 换成了别的实体）。<b>这是常态档</b>——活着的镜像副本
     *                       每轮都会落在这里
     * @param visibleSkipped 因命中"本帧可见实体占格"而跳过的格数。这是<b>恒不应触发的双保险</b>
     *                       （跨源交叉核对：可见实体必然在 {@code level} 实体表里 ⇒ 查询必非空）。
     *                       非零要查——但注意一个良性来源：快照 AABB 是<b>渲染帧插值</b>后的盒，快移
     *                       实体的插值盒可能多覆盖一格而该格此刻已空，这不算矛盾。
     *                       <b>持续 / 大面积非零</b>才是"可见集与实体直读不一致"的信号
     * @param absent         裁决缺席的格数（= 删除证据）
     */
    public record Stats(int candidates, int unloaded, int occupied, int visibleSkipped, int absent) {}

    /**
     * 对记忆侧上报的实体占用格做实体检查询（设计 §7.14.1）。
     *
     * @param level             当前客户端世界（<b>必须是真实世界</b>，见类 javadoc P4）
     * @param candidates        记忆侧 cells 实体段的格（空 → 直接返回空结果，零世界读）
     * @param visibleEntityCells 本帧可见实体（{@code DepthCapture} 快照的 AABB）覆盖的格，用作恒不应
     *                           触发的双保险。传空集亦可（退化为无该层保护），但调用方应尽量给
     * @return 缺席格 + 记账
     */
    public static Result correct(final Level level, final List<BlockPos> candidates,
                                 final Set<BlockPos> visibleEntityCells) {
        if (candidates.isEmpty()) {
            // 空段是常态（镜像中无冻结实体时恒空）→ 零世界读、零分配，保持每帧零成本
            return new Result(List.of(), new Stats(0, 0, 0, 0, 0));
        }
        // 本地玩家（agent 自身）从查询中排除——承重，理由见类 javadoc。为 null 说明不在世界内，
        // 此时不做任何裁决（没有可靠的"排除谁"语义，宁可欠删）
        final Entity self = Minecraft.getInstance().player;
        if (self == null) {
            return new Result(List.of(), new Stats(candidates.size(), candidates.size(), 0, 0, 0));
        }
        final List<BlockPos> absent = new ArrayList<>();
        int unloaded = 0;
        int occupied = 0;
        int visibleSkipped = 0;
        for (BlockPos pos : candidates) {
            // ① 双保险：本帧已看见实体占着这格 ⇒ 不删。与 DeletionJudge 的可见集闸门同款；两者跨源，
            //    正常永不触发（触发即矛盾，计入 visibleSkipped 供排查）
            if (visibleEntityCells.contains(pos)) {
                visibleSkipped++;
                continue;
            }
            // ② P1 主守卫：区块未加载 → 实体表里看不到任何东西 → 读不到 ≠ 不存在 → 不裁决（默认保留）
            if (!level.isLoaded(pos)) {
                unloaded++;
                continue;
            }
            // ③ 实体检查询：查到任何实体（除 agent 自身）⇒ 不裁决（P3）
            if (anyEntityIn(level, self, pos)) {
                occupied++;
                continue;
            }
            // ④ 区块已加载 ∧ 查询为空 ⇒ 该格现实中确无实体（本通道唯一被授权证明的事实）
            absent.add(pos);
        }
        return new Result(absent, new Stats(candidates.size(), unloaded, occupied, visibleSkipped, absent.size()));
    }

    /** 单格 AABB 实体检查询（外扩 {@link #QUERY_EPSILON}，理由见类 javadoc）；{@code except} 恒非空。 */
    private static boolean anyEntityIn(final Level level, final Entity except, final BlockPos pos) {
        final AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).inflate(QUERY_EPSILON);
        return !level.getEntities(except, box, ANY_ENTITY).isEmpty();
    }
}
