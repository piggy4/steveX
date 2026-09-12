package com.example.memworld;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.23（§7.11）记忆世界<b>减量</b>执行器 —— 消费采集侧 {@code DeletionJudge} 的删除判定。
 *
 * <p>v2.23 起删除判定<b>完全移到采集侧</b>（§7.11 反向通道）：记忆侧只上报"当前存在"的实心不透明
 * 块 + 冻结实体占用格（{@link MemoryCellReporter} → {@code memory_cells.bin}），采集侧对每个 cell
 * 做 §5.4 式逐块投影 + 深度判定，证明被射线整格越过（{@code Z_opaque ≥ t_far − δ}，越过计数
 * ≥ {@code removalPixelThreshold}）→ 判消失 → 写入 terrain.nbt 顶层 {@code deletions}。本类只负责
 * <b>执行</b>：
 *
 * <ol>
 *   <li><b>方块删除</b>：对每个 deletion 格，若当前实心+不透明且不在本次可见集 → 静默置空
 *       （flags 818）并清除该位置旧方块实体记录（{@link MemoryRestorer#clearStale}）；</li>
 *   <li><b>相机格快路径</b>：相机所在格无条件尝试删除（受当前可见集防护）——DeletionJudge 跳过
 *       "相机在格内"的 cell，故相机格不在 deletions 里，须单独处理（保留 v2.22 语义）；</li>
 *   <li><b>冻结实体清理</b>：不在当前帧实体快照内、且<b>全部 AABB 占用格</b> ∈
 *       {@code entityDeletions} ∪ {相机格}（v2.37 §7.14 起；采集侧<b>直读真实世界</b>证明"该格现实中
 *       已无实体"）→ {@link EntityRestorer#discard}。旧证据 {@code deletions}（方块深度推断）自本版
 *       <b>整条撤出</b>实体判定，理由见 {@code apply} ③。</li>
 * </ol>
 *
 * <p>关键正确性约束（§7.11 / v2.32）：
 * <ul>
 *   <li><b>执行顺序</b>：先增量后减量——增量把本次可见集构建进 applied 表后，减量才拿
 *       「当前可见集」作假阳性防护；反序会把删掉的方块经指纹同步立即重新放回。</li>
 *   <li><b>假阳性防护</b>：当前地形集（{@code terrain.blocks}，本次可见方块）绝不可删。</li>
 *   <li><b>内容守卫</b>：deletion 主循环删 {@code isDeletableContent}（v2.36 放宽：实心不透明 ∪ 流体 ∪
 *       满格透明）；相机格快路径保守只删实心不透明（v2.22 语义）。两类都只放行 cells 可报口径内容，
 *       非满形状透明仍欠删（§7.12 边界①，双保险防误删）。</li>
 *   <li><b>无跨帧累积</b>：删除阈值（≥2 像素）由采集侧 {@code DeletionJudge} 每帧独立判定，
 *       记忆侧不投票、不累计。</li>
 *   <li><b>v2.32 限活动维</b>：{@link MemoryWorldManager} 只用活动维的 ServerLevel 驱动本类；
 *       terrain / 实体跳过集都已是该维内容，删除、清 BE、实体清理全部在该维内执行——跨维数据
 *       永不进入减量判定（采集侧 cells 另带维标签双保险，见 §4.6）。</li>
 * </ul>
 *
 * <p><b>v2.37 七次修订（§15）并列通道</b>：除上述 deletions 通道（证据 = 采集侧<b>深度推断</b>）外，
 * 新增信号缺失族通道（证据 = 采集侧<b>真实世界状态直读</b>，绊线 / 绊线钩），源键为
 * {@code terrain.signalLossDeletions}（terrain.nbt 中与 {@code deletions} 并列的键）。两条通道
 * <b>在执行侧也完全分立</b>——各自的内容守卫（{@link #deleteBlock} 的
 * {@code isDeletableContent ∪ isShapedDeletableContent} 与 {@link #deleteSignalLossBlock} 的
 * {@code isSignalLossBlock}）定义域互不相交，合并成一条就会把守卫一并放宽、让双保险失效（§15.2）。
 * 唯一的交叉点是冻结实体清理用的"已证空格集"，而该集<b>刻意不含</b>本通道的裁决（绊线可与实体同格
 * 共存，"绊线没了"对实体占用零信息量，并进去会误删活实体）——见 {@code apply} ③。
 *
 * <p><b>v2.37（§7.14）第三条并列通道 —— 实体在场</b>：源键 {@code terrain.entityDeletions}
 * （terrain.nbt 与另两条并列的键），证据 = 采集侧 {@code EntityPresenceCorrector} 对真实世界的
 * <b>实体检查询</b>。它<b>只喂实体清理</b>（③），不喂方块删除（①②）：实体占据的格常常是仍然存在的
 * 方块（掉落物落在营火上、生物站在耕地上），并进方块通道就是对活方块整格删除。而与③的关系是
 * <b>替换</b>而非并列——③ 的证据集自本版起不再含 {@code deletions}（§7.14.1）。
 */
public final class DeletionApplier {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");

    /** 方块实体通道：删除方块后清除该位置旧 BE 记录，防 block_entities.nbt 增量重放（v2.10 语义）。 */
    private final MemoryRestorer beRestorer;

    /** 实体通道：删除冻结实体。 */
    private final EntityRestorer entities;

    public DeletionApplier(final MemoryRestorer beRestorer, final EntityRestorer entities) {
        this.beRestorer = beRestorer;
        this.entities = entities;
    }

    /** 服务器（世界）启动 / 切换时调用，清空状态。 */
    public void onServerStart() {
        // v2.38（§7.1 决策 J）：墓碑集是<b>会话态</b>、不落盘——重启后清空（最坏只是容器通道回放一次
        // 记录，与 v2.37 行为相同；不会造成永久性差异）。见 {@link RemovalTombstones}。
        RemovalTombstones.get().clearAll();
        LOGGER.info("[MemoryWorld] Deletion applier ready");
    }

    /**
     * 对一帧 TerrainData 的三条删除清单执行删除（{@code deletions} + v2.37 的
     * {@code signalLossDeletions} + v2.37 §7.14 的 {@code entityDeletions}，各走各的守卫与落点，
     * 见类 javadoc）。terrain 为 null（本轮无新数据）或减量关闭 → 直接返回（空闲成本≈0）。
     *
     * <p>v2.32：只对传入 level（= 活动维，见 {@link MemoryWorldManager}）执行——删除 / 清 BE /
     * 实体清理全部限定在该维内。
     *
     * @param terrain 本次读取的 TerrainData（含 deletions / signalLossDeletions / entityDeletions /
     *                blocks / cameraPos）；null → 跳过
     * @param currentEntityUuids 当前帧实体快照 uuid 集（当前可见实体，删除跳过集）
     */
    public void apply(final ServerLevel level, final TerrainRestorer.TerrainData terrain,
                      final Set<UUID> currentEntityUuids) {
        final MemoryConfig config = MemoryConfig.get();
        if (!config.removalEnabled || terrain == null) return;

        final String dimension = level.dimension().identifier().toString();
        final List<BlockPos> deletions = terrain.deletions();
        final Set<BlockPos> currentTerrain = terrain.blocks().keySet();
        final Vec3 cam = terrain.cameraPos();
        final BlockPos cameraCell = cam != null ? BlockPos.containing(cam.x, cam.y, cam.z) : null;

        int deleted = 0;
        // v2.38（§7.1 决策 I 的兜底诊断）：② 主循环的逐档记账。**必须不含相机格快路径**——快路径的
        // 成功数并进 deleted 会把失配数掩盖掉 1，而这条告警的全部价值就在于"收了清单但没删成"必须
        // 精确可见（半态 = 采集侧已修剪记录、记忆侧却没删方块）。
        int deletionVisibleSkipped = 0;
        int deletedByDeletions = 0;

        // ① 相机格快路径：无条件尝试删除，当前可见集防护防抖振（贴墙时格内方块在当前可见集，删除会被增量重建）
        if (cameraCell != null && !currentTerrain.contains(cameraCell)) {
            if (deleteBlock(level, dimension, cameraCell, false)) deleted++;
        }

        // ② 对每个 deletion 格：假阳性防护 + 按内容放行（§7.12 放宽至流体/满格透明）→ 静默置空 + 清 BE
        for (BlockPos pos : deletions) {
            if (currentTerrain.contains(pos)) {
                deletionVisibleSkipped++;
                continue;
            }
            if (deleteBlock(level, dimension, pos, true)) {
                deleted++;
                deletedByDeletions++;
            }
        }

        // v2.38（§7.1 决策 I）**失配告警**：候选非空、既未被可见集跳过、又没删成 ⇒ 守卫拒删。两个可能的
        // 原因方向相反，故必须响：(a) 假阳性（记忆侧守卫正确挡下，采集侧若也修剪了记录 = 半态，见决策 I）；
        // (b) 镜像该格已被前一轮 / 玩家改成别的东西（同理）。采集侧"已修剪数"= 0 时是 (b)，> 0 时是 (a)。
        final int refused = deletions.size() - deletionVisibleSkipped - deletedByDeletions;
        if (refused > 0) {
            LOGGER.warn("[MemoryWorld] Deletion mismatch [{}]: {} candidates, {} visible-skipped, "
                            + "{} deleted, {} REFUSED by guard (half-state risk if vision pruned them; §7.1 I)",
                    dimension, deletions.size(), deletionVisibleSkipped, deletedByDeletions, refused);
        }

        // ②b v2.37 七次修订（§15.2 段③）信号缺失族**并列通道**：绊线 / 绊线钩的"状态直读证明消失"
        //   （采集侧 {@code SignalLossCorrector} 读真实世界状态产出；本侧只执行）。
        //
        //   **为什么并列、不并进②的主循环**：主循环的守卫是 isDeletableContent ∪
        //   isShapedDeletableContent——绊线两条都不满足（正是它被 §4.1 第⑤步排除的原因）。若为让它过去
        //   而把 isSignalLossBlock 加进那个守卫，等于对**所有**删除来源一起放宽：任何来源只要误报
        //   "这格是绊线"就能删掉该格绊线，"只删 cells 上报口径内容"这条双保险就此失效（且影响的不只是
        //   绊线，而是②的全部判定）。分立两通道 ⇒ 两道守卫各自完整，且定义域互不相交（§15.5）。
        //
        //   本通道守卫（deleteSignalLossBlock）与②同强度：**只删镜像里当前确实是该族方块的格**——
        //   镜像里已是空气 / 已是别的方块 → 不动（欠删无害）。故误报的代价仍只是欠删，不是误删。
        //
        //   可见集跳过与②同款：镜像里该格还在本次可见集 ⇒ 现实里也在 ⇒ 不该删（双保险）。这两者本就
        //   同源（采集侧可见集来自正向观测、本通道候选来自"现实不再可见"），正常不会同时成立。
        final List<BlockPos> signalLossDeletions = terrain.signalLossDeletions();
        int deletedSignalLoss = 0;
        int signalLossVisibleSkipped = 0;
        for (BlockPos pos : signalLossDeletions) {
            if (currentTerrain.contains(pos)) {
                signalLossVisibleSkipped++;
                continue;
            }
            if (deleteSignalLossBlock(level, dimension, pos)) deletedSignalLoss++;
        }

        // ③ 冻结实体清理：不在当前可见集、且全部占用格已证空 → 移除（限活动维）
        //    证据集 = **entityDeletions ∪ {相机格}**（v2.37 §7.14；旧为 deletions ∪ {相机格}）。
        //    - **为什么必须换证据**：deletions 证明的是"这格没有实心不透明方块"，而实体可以存在于空气格
        //      ——它本来就不是实体占用的证据。更要命的是源头：非满方块（营火 / 耕地 / 雪层 / 台阶…）所在格
        //      **永不入 deletions**（采集侧 DeletionJudge 首句 currentTerrain.contains(pos) → continue，而
        //      currentTerrain 是本快照的可见方块）⇒ 与非满方块共格的冻结实体<b>永久残留</b>；落点在整方块顶
        //      则落入空气格、可证明为空、正常删——现场"营火弹出的掉落物消失判定有概率失效"的全部含义
        //      （§7.14 根因）。
        //    - **为什么不再并列保留 deletions**：实体裁决已由采集侧 EntityPresenceCorrector 直读"该格
        //      现实中还有没有实体"独占（实体占用格自 cells v6 起不再进 main 段、不再喂深度判据）。保留旧
        //      证据只会把那两条老毛病留在原地：既欠删（上面那条），又可能误删活体（实体渲染几何 ≪ AABB，
        //      格内空余区的射线会投票"越过"）。
        //    - **为什么仍含相机格**：v2.22 语义，相机就在格内是最强证据。采集侧的实体查询<b>排除 agent
        //      自身</b>（"我站在这儿"对"我那个冻结副本还在不在"零信息量），故实体通道的证据里永远不会有
        //      这一格，必须在此单独补——否则站在冻结实体上的实体永远删不掉。
        //    - **为什么不含 signalLossDeletions**（看似该加，实则会误删活实体）：那条通道证明的是"该格
        //      现实里没有绊线了"，而绊线不阻挡实体（可同格共存）⇒ 对实体占用**零信息量**；并进去会让站在
        //      原地的活实体因脚下那格曾被绊线占过而被判"占用格全空"遭 {@code discard}（§15.5 硬边界）。
        final Set<BlockPos> provenEmpty = new HashSet<>(terrain.entityDeletions());
        if (cameraCell != null) {
            provenEmpty.add(cameraCell);
        }

        int discarded = 0;
        int entityVisibleSkipped = 0;
        int entityKept = 0;
        for (UUID uuid : entities.uuids(dimension)) {
            if (currentEntityUuids.contains(uuid)) {
                entityVisibleSkipped++;
                continue;
            }
            if (entities.allCellsEmpty(dimension, uuid, provenEmpty)) {
                entities.discard(dimension, uuid);
                discarded++;
            } else {
                entityKept++;
            }
        }

        if (deleted > 0 || discarded > 0) {
            LOGGER.info("[MemoryWorld] Deletion apply [{}]: deleted {} blocks, discarded {} entities ({} deletions from judge)",
                    dimension, deleted, discarded, deletions.size());
        }
        // v2.37（§7.14）诊断：实体通道单独打（与①②/②b 分开——四条通道的证据类型、守卫、失败含义全不同，
        // 混一行就无法判断是哪条在动）。候选非空才打（无冻结实体时该段恒空，打了就是每 tick 一行零）。
        // 关键读数：candidates>0 而 discarded=0 → 实体通道没在动，三种原因按概率：
        //   ① kept>0：镜像副本仍「未被证空」（常态——活着的副本，或采集侧 occupied 档）；
        //   ② 采集侧 unloaded 档（区块未加载，欠删；看采集侧 entityPresence 行）；
        //   ③ 采集侧版本 < cells v6 / 本侧读不到 entityDeletions 键（升级未配套 ⇒ 通道静默空转）。
        final List<BlockPos> entityDeletions = terrain.entityDeletions();
        if (!entityDeletions.isEmpty()) {
            LOGGER.info("[MemoryWorld] Entity-presence channel [{}]: {} candidate cells → {} discarded, "
                            + "{} kept, {} visible-skipped (frozen entities in dim: {})",
                    dimension, entityDeletions.size(), discarded, entityKept, entityVisibleSkipped,
                    discarded + entityKept + entityVisibleSkipped);
        }
        // v2.37 七次修订（§15.7）诊断：信号缺失通道单独打（与②的主日志分开——两条通道的证据类型、
        // 守卫、失败含义全不同，混在一行就无法判断是哪条在动）。候选非空才打：本通道常态是空段，
        // 打了就是每 tick 一行零。候选非空而删除数为 0 时该行尤其重要（守卫挡下 / 可见集跳过 / 停摆）。
        if (!signalLossDeletions.isEmpty()) {
            LOGGER.info("[MemoryWorld] Signal-loss channel [{}]: {} candidates → {} deleted, {} visible-skipped",
                    dimension, signalLossDeletions.size(), deletedSignalLoss, signalLossVisibleSkipped);
            final int slRefused = signalLossDeletions.size() - signalLossVisibleSkipped - deletedSignalLoss;
            if (slRefused > 0) {
                // 与②同款失配告警（决策 I）：守卫只删"镜像里当前确实是该族方块"的格。非零 = 镜像该格
                // 已是空气 / 已被换成别的东西 —— 若采集侧同时修剪了记录，就是半态；本版批 2 加表后
                // 这条通道承载箱子族，故它的失配比②更值得看。
                LOGGER.warn("[MemoryWorld] Signal-loss mismatch [{}]: {} candidates, {} visible-skipped, "
                                + "{} deleted, {} REFUSED by guard (half-state risk if vision pruned them; §7.1 I)",
                        dimension, signalLossDeletions.size(), signalLossVisibleSkipped, deletedSignalLoss, slRefused);
            }
        }
    }

    /**
     * 静默删除一个方块（v2.21 静默放置语义，flags = 818），并清除该位置<b>活动维</b>的旧方块实体
     * 记录（防 block_entities.nbt 增量重放 ghosting，见 {@link MemoryRestorer#clearStale}）。
     *
     * <p>内容放行守卫由 {@code allowWideContent} 决定：
     * <ul>
     *   <li>{@code false}（相机格快路径，保留 v2.22 语义）：只删实心+不透明方块；已是空气 / 非满形状 /
     *       半透明 → 返回 false（欠删无害）；</li>
     *   <li>{@code true}（deletion 主循环，v2.37 放宽）：删 {@link BlockStateUtil#isDeletableContent}
     *       ∪ {@link BlockStateUtil#isShapedDeletableContent} —— 实心不透明 ∪ 流体（水/岩浆）∪ 满格透明
     *       ∪ <b>非满形状方块（本版新增）</b>。两把尺与 cells 上报口径逐条对齐；
     *       未纳入几何段的（绊线 / {@code INVISIBLE} 族 / BE 非满块 / 几何为空的）仍被挡在可删集外
     *       （欠删，双保险防误删）。</li>
     * </ul>
     *
     * <p><b>守卫的正确性依赖 §4.4 的分段次序，必须与 {@code MemoryCellReporter.computeCells} 同批成立</b>
     * （设计 §6）：{@code isDeletableContent} 对<b>任何含流体的格</b>直接返回 true
     * （{@code BlockStateUtil:85-86} 的 {@code if (!fluid.isEmpty()) return true;} 短路）。因此"含水非满块
     * 不会被整格误删"<b>不是</b>守卫本身保证的，而是靠"先形状后流体"——waterlogged 栅栏只进几何段、
     * 不进 translucent 水段，于是"水段格被证明消失"必然意味着那格<b>没有方块本体</b>，整格置空才成立。
     */
    private boolean deleteBlock(final ServerLevel level, final String dimension, final BlockPos pos,
                                final boolean allowWideContent) {
        try {
            final BlockState state = level.getBlockState(pos);
            final boolean ok = allowWideContent
                    ? (BlockStateUtil.isDeletableContent(level, pos, state)
                        || BlockStateUtil.isShapedDeletableContent(level, pos, state))
                    : BlockStateUtil.isSolidOpaque(level, pos, state);
            if (!ok) return false;
            return clearBlock(level, dimension, pos);
        } catch (Exception e) {
            LOGGER.warn("[MemoryWorld] Deletion failed to delete {}: {}", pos, e.getMessage());
            return false;
        }
    }

    /**
     * v2.37 七次修订（§15.2 段③）信号缺失族并列通道的删除守卫 —— <b>只删镜像里当前确实属于该族的格</b>。
     *
     * <p>与 {@link #deleteBlock} 的守卫<b>定义域互不相交</b>：绊线 / 绊线钩既非
     * {@code isDeletableContent}（无流体、非实心不透明、非满格透明），也非
     * {@code isShapedDeletableContent}（该谓词第⑤步就是 {@code !isSignalLossBlock}，§15.5）。故本方法
     * 不可能与②删同一格，也不可能从②手里"接管"任何格。
     *
     * <p>读到空气 / 其它方块 → false（欠删无害）：本通道由采集侧状态直读驱动，理论上候选格必然仍是
     * 该族；但镜像可能已被②或前一轮本通道改过（时序窗，§15.4 P4），故守卫仍在——它是"记忆侧只能删
     * 自己确实持有的东西"这条双保险的落点，不能省。
     */
    private boolean deleteSignalLossBlock(final ServerLevel level, final String dimension, final BlockPos pos) {
        try {
            final BlockState state = level.getBlockState(pos);
            if (!BlockStateUtil.isSignalLossBlock(state.getBlock())) return false;
            return clearBlock(level, dimension, pos);
        } catch (Exception e) {
            LOGGER.warn("[MemoryWorld] Signal-loss deletion failed to delete {}: {}", pos, e.getMessage());
            return false;
        }
    }

    /**
     * 静默置空 + 清除该位置<b>活动维</b>的旧方块实体记录（flags = 818，v2.21 静默放置语义 / §7.9 陷阱①）。
     *
     * <p>两条删除通道共用：置空与清 BE 必须<b>成对</b>发生——漏清 BE 会让 {@code block_entities.nbt}
     * 增量重放把幽灵 BE 挂到已置空的格上（v2.10 语义）。抽成一处即为此：新增通道时不可能只做一半。
     */
    private boolean clearBlock(final ServerLevel level, final String dimension, final BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS);
        beRestorer.clearStale(dimension, pos);
        // v2.38（§7.1 决策 J）：登记会话级墓碑——容器通道据此跳过"世界为空气 → 自足回放记录"的路径，
        // 否则它与本通道按 poll 周期对打（箱子闪烁）。写入点收敛在此处 = 两条删除通道共用同一个置空点，
        // 新增通道时不可能只做一半（与上面"置空与清 BE 必须成对"同一约定）。
        RemovalTombstones.get().record(dimension, pos);
        return true;
    }
}
