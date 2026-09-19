package name.modid.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v2.37 七次修订（设计 §15）<b>信号缺失族校正通道</b> —— 用"真实世界状态直读"判定绊线 / 绊线钩
 * 是否已消失，产出与几何段<b>并列</b>的裁决清单。
 *
 * <p><b>为什么需要它</b>（§15.1）：绊线在 Fabulous 下画进 weather 目标、不写任何已读深度场
 * （§10.8）⇒ 活体不产生可读信号 ⇒ 深度判据下"消失了"与"活着"<b>不可区分</b> ⇒ 判消失必误删活体，
 * 故自 v2.23 起被整族排除（{@code BlockStateUtil.SIGNAL_LOSS_BLOCKS}）。但这一族的问题不是"判不出"，
 * 而是<b>深度判据的输入不存在</b>——而"该格现在有没有绊线"在真实世界侧<b>可以直接读</b>。本类把这一族
 * 的判据由<b>深度推断</b>换成<b>状态直读</b>：证据类型是"直接事实"，与深度场、视锥、图形配置全无关。
 *
 * <p><b>为什么本类里没有"信号缺失族名单"</b>：族的定义完全在<b>记忆侧</b>——{@code MemoryCellReporter}
 * 上报什么格，本就是它按 {@code isSignalLossBlock} 筛过的结果。采集侧因此<b>不需要也禁止</b>持有一份
 * 平行名单（两份名单一旦漂移，"哪些格可删"就会与"哪些格被上报"错配；而这正是本类要避免的那类 bug）。
 * 本类只回答一个与方块种类无关的问题：<b>"该格在真实世界里是不是空气"</b>：
 *
 * <pre>{@code
 *   读到空气      → 缺席（该格在现实里没了）
 *   读到任何其它  → 保留（不裁决：在场，或换成了别的东西——后者由正向观测通道覆盖，§15.4 P3）
 * }</pre>
 *
 * <p><b>四条硬规则</b>（每条都是"方向错就误删"的那种，见 §15.4）：
 * <ol>
 *   <li><b>读不到 ≠ 不存在（P1）</b>：未加载区块 / 越界高度的 {@code getBlockState} <b>不抛异常、
 *       也不返回 {@code AIR}</b>，而是返回 {@code VOID_AIR}（{@code Level.getBlockState} →
 *       {@code getChunkAt} → {@code ClientChunkCache} 的 {@code emptyChunk} → {@code EmptyLevelChunk}）。
 *       更凶的是：{@code Blocks.VOID_AIR} 在注册时就带 {@code .air()}（{@code Blocks.java:5211}），
 *       故 <b>{@code VOID_AIR.isAir() == true}</b>——"读不到"与"真的是空气"在 {@code isAir()} 这个
 *       谓词下<b>完全同义</b>。因此守卫有两个，且<b>次序不可换</b>：先 {@code level.isLoaded(pos)}
 *       （vanilla 已给的正是这条：{@code isInValidBounds && ChunkSource.hasChunk}，而
 *       {@code hasChunk} 的语义就是 FULL），再单独判 {@code VOID_AIR}，最后才轮到 {@code isAir()}。</li>
 *   <li><b>不得用"本次可见集里没有它"当证据（P2）</b>：本通道的证据是状态读，与"看不看得见"无关。
 *       候选集由记忆侧给定，本类<b>不枚举、不做视锥筛选</b>，覆盖条件用"区块已加载"而非"看得见"——
 *       整条通道因此对渲染帧零依赖（不读深度、不读 PBO、与 Fabulous 无关）。</li>
 *   <li><b>以状态为准，且只保留"删除"半个动作（P3）</b>：读到非空气一律<b>不裁决</b>。本通道只被授权
 *       证明"不在"，没有被授权证明"在"；镜像里该格的内容由正向观测通道在下一帧覆盖——在这里顺手改内容
 *       会造出"镜像里有个从未被看见的方块"的假记忆，破坏"记忆 = 见过的世界"这条根本语义。</li>
 *   <li><b>只在真实世界采集帧成立（P4）</b>：调用方必须保证本帧读的是真实世界。这不是本通道新增的
 *       前提——自 v2.32 起 {@code terrain.nbt} 的整套语义（活动维 = agent 当前维）就建立于此；否则
 *       {@code getBlockState} 读的全是镜像内容、整套系统自指。</li>
 * </ol>
 *
 * <p><b>调用前提</b>：{@code candidates} 必须是<b>与当前帧同维</b>的 cells 段（调用方
 * {@code ObjectResolver} 已按维标签过滤，与几何段同一道门 {@code dimensionOk}）；跨维喂入会拿本维的
 * 世界状态去判别的维的坐标——那是本类唯一防不住的方向，故写在此处。
 *
 * <p><b>纯函数</b>：不落盘、不打日志、不依赖本类以外的可变状态——记账逐档回给调用方，由
 * {@code ObjectResolver} 统一打（与 {@code DeletionJudge} 的 {@code deltaHist} / {@code shapedUnjudged}
 * 同款：判据纯净，诊断归调用方）。可在渲染线程执行（纯读，无分配热点）。
 */
public final class SignalLossCorrector {

    private SignalLossCorrector() {
    }

    /**
     * 一次校正的结果。
     *
     * @param absent 被裁决"缺席"的格（真实世界该格是空气，且区块已加载）——进 {@code terrain.nbt} 的
     *               {@code signalLossDeletions} 并列键，由记忆侧 {@code DeletionApplier} 的并列通道执行
     * @param stats  逐格记账（设计 §15.7 第 3 条要求"未加载跳过"可观测——它是 P1 那个坑的唯一在线指示器）
     */
    public record Result(List<BlockPos> absent, Stats stats) {}

    /**
     * 逐格记账。四档跳过的格数<b>相加不等于</b> {@code candidates}：差额即"裁决缺席"的格数
     * （= {@code absent.size()}），故不必再单列一档。
     *
     * @param candidates     本轮候选数（记忆侧上报的在场信号缺失族格）
     * @param unloaded       因区块未加载 / 越界而跳过的格数。<b>非零是正常且必需的</b>（玩家一走远就会
     *                       发生）；它若恒为 0 而候选恒非空，反而说明 {@code isLoaded} 守卫没生效
     * @param voidAir        读到 {@code VOID_AIR} 而跳过的格数。{@code isLoaded} 守卫已覆盖"未加载 /
     *                       越界"两条主路径，故本计数<b>正常应为 0</b>；非零即为环境差异的信号，须查（P1）
     * @param stillPresent   真实世界读到空气以外的方块（在场 / 换成了别的东西）而保留的格数。这是本通道
     *                       的常态档——绊线没被破坏时它每轮都等于候选数
     * @param visibleSkipped 因命中"本帧可见集"而跳过的格数。这是<b>恒不应触发的双保险</b>：两者同源
     *                       （都来自 {@code level.getBlockState}），可见格必然非空气。非零即说明
     *                       "可见集与状态直读不一致"，是要查的信号，故单列一档而不并进 {@code stillPresent}
     */
    public record Stats(int candidates, int unloaded, int voidAir, int stillPresent, int visibleSkipped) {}

    /**
     * 对记忆侧上报的信号缺失族格做状态直读裁决（设计 §15.2 段②）。
     *
     * @param level      当前客户端世界（<b>必须是真实世界</b>，见类 javadoc P4）
     * @param candidates 记忆侧 cells 信号缺失段的格（空 → 直接返回空结果，零世界读）
     * @param visible    本帧已观测到的方块格集（{@code terrain.keySet()}），用作恒不应触发的双保险
     * @return 缺席格 + 记账
     */
    public static Result correct(final Level level, final List<MemoryCellsReader.SignalLossCell> candidates,
                                 final Set<BlockPos> visible) {
        if (candidates.isEmpty()) {
            // 空段是常态（绊线本身稀疏，多数世界 0 格）→ 零世界读、零分配，保持每帧零成本
            return new Result(List.of(), new Stats(0, 0, 0, 0, 0));
        }
        final List<BlockPos> absent = new ArrayList<>();
        int unloaded = 0;
        int voidAir = 0;
        int stillPresent = 0;
        int visibleSkipped = 0;

        for (MemoryCellsReader.SignalLossCell c : candidates) {
            final BlockPos pos = c.pos();
            // ① 双保险：本帧已看见（非空气）⇒ 不删。与 DeletionJudge 的可见集闸门同款；两者同源，
            //    正常永不触发（触发即矛盾，计入 visibleSkipped 供排查）
            if (visible.contains(pos)) {
                visibleSkipped++;
                continue;
            }
            // ② P1 主守卫：区块未加载 / 越界 → 读不到 ≠ 不存在 → 不裁决（默认方向 = 保留）
            if (!level.isLoaded(pos)) {
                unloaded++;
                continue;
            }
            final BlockState state = level.getBlockState(pos);
            // ③ P1 兜底 + 记账：VOID_AIR 的 isAir() 为 true（Blocks.java:5211 的 .air()），
            //    故本判必须排在 isAir() 之前，否则"读不到"会被当成"缺席"——方向就反了
            if (state.is(Blocks.VOID_AIR)) {
                voidAir++;
                continue;
            }
            // ④ 非空气 ⇒ 保留（在场 / 换成了别的东西，后者交正向观测通道，P3）
            if (!state.isAir()) {
                stillPresent++;
                continue;
            }
            absent.add(pos);
        }
        return new Result(absent, new Stats(candidates.size(), unloaded, voidAir, stillPresent, visibleSkipped));
    }
}
