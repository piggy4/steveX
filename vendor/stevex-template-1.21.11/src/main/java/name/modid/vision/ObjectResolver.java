package name.modid.vision;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * 对象查询（Phase 3，§5）：在<b>渲染线程</b>把 {@link Unprojector} 的反投影结果解析为可见对象。
 *
 * <p>四路查询：
 * <ol>
 *   <li><b>方块</b>（§5.1）：去重点直查 blockstate + air 近侧回退（带 v2.10 实体相交验证）；</li>
 *   <li><b>方块实体</b>（§5.2）：搭方块便车 {@code saveWithFullMetadata}，壁挂朝向过滤（v2.10）；
 *       NBT 再按 typeId 白名单剥 L2 交互内部（§5.2.1，v2.27 {@link BlockEntityFieldPolicy}）；</li>
 *   <li><b>实体</b>（§5.3）：SectionPos 桶 + 闭区间 contains + 深度排序 + 肢体判别（v2.11）；</li>
 *   <li><b>半透明 / 绊线方块</b>（§5.4，仅 Fabulous，v2.12；两深度锚点 v2.24/v2.26）：工序 B
 *       （首层透明面深度 pass）用 translucent 目标深度逐像素精确落位首层半透明；工序 C（v2.26
 *       重写：区间射线推进正向归属）仅对存在透明区间（translucentDepth &lt; mainDepth）的像素沿
 *       射线推进到首个不透明面，逐格上报实际穿过的每个可见半透明格（含全部嵌套层），跨 section
 *       palette maybeHas 整节跳步；区间外的独立绊线（weather 目标、不写两套深度）由轻量候选补齐
 *       （保留 v2.12/v2.24 采集能力）。工序 C 对工序 B 已放的首层面格只跳过。</li>
 * </ol>
 *
 * <p>必须在渲染线程调用（经 {@code Minecraft.execute}，§8）：读 blockstate / section / NBT 序列化
 * 均有竞态。纯几何判据（contains / slab 求交 / 深度比较）可在 API 线程，但为简单起见统一在本类。
 */
public final class ObjectResolver {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * <b>沿射线推的探针距离</b>（实体盒相交探针 / 薄方块三连判的 {@code W ± dir·ε} / 前向扫描的
     * 最小长度）。值 0.05。
     *
     * <p><b>它不再承担 {@code W} 的还原</b>：{@code W = nudged − dir·ε} 是落格推移的逆运算，
     * 必须使用<b>落格时实际传入的 ε</b>（现由 {@link Unprojector.UnprojectResult#landingEpsilon()}
     * 携带）。两者同值时代码看不出差别，一旦落格量单独下调，拿本常量去还原就会沿射线偏
     * {@code 0.05 − 1/128 = 0.0422} 格——薄几何体（1/64）上直接穿到它后面那块。
     *
     * <p><b>为什么不随落格量一起下调</b>（v2.39 决策 R-1，设计 §4.2.1 四常量表）：落格量的约束来自
     * "面到本格出口的厚度"（薄贴花 1/64 ⇒ 必须小），而本常量的作用是"从表面点探到相邻格"。实体盒
     * 膨胀 0.5 与 0.05 是同一把尺的两处，改动会让 v2.10 的穿透防护出现假阳性回归。两者失效面
     * 不相交，故拆成两个常量、各自演进。
     */
    private static final double PROBE_EPSILON = 0.05;
    /** §5.4 深度比较容差（float32 量化，v2.11：仅 ≤~100 格内成立）。 */
    private static final double DELTA = 0.05;
    /**
     * {@link #forwardScanIsAir} 的迭代步长（v2.11 肢体判别 b/c 项的前向空扫）。
     * <b>同值不同义</b>：下调它只让扫描更细，代价是迭代数线性上升（扫描长 {@code |W−cam|+1} 格
     * ⇒ 0.05 时约 20 次、1/128 时约 128 次，6.4×）。故它与 {@link #PROBE_EPSILON} 分开登记，
     * 免得将来"全局调小 ε"顺手把这条热循环也乘上 6.4×。
     */
    private static final double SCAN_STEP = 0.05;
    /** §5.4 屏幕空间栅格合并的栅格尺寸（像素）；v2.26 主路径（区间射线推进）已删除该有损合并，仅降级回退沿用。 */
    private static final double GRID = 8.0;

    private ObjectResolver() {
    }

    /**
     * 执行四路查询 + v2.23 减量判定，并把结果写入三个 store（§6.1）。
     *
     * @param cells v2.23/v2.36：记忆侧反向通道上报的待判定记忆格（main 段 + translucent 段两分段，
     *              §7.12）+ translucent 场开关 + 删除阈值（{@code MemoryCellsReader} 读取）
     * @return 可见对象 + store 统计（供 API 组装 JSON）
     */
    public static ResolveResult resolve(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Unprojector.UnprojectResult hits,
            final Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> bucket,
            final MemoryCellsReader.CellsData cells,
            final long timestamp
    ) {
        final Vec3 cam = snap.cameraPos();

        // v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md）：当前帧所属维 id。
        // 一次快照不可能跨维，故本帧所有 store 落盘、响应 dimension、cells 维过滤共用同一 id。
        final String dimensionId = level.dimension().identifier().toString();

        // ① 方块直查 + ② 方块实体（搭方块便车）
        final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain = new LinkedHashMap<>();
        final Map<BlockPos, VisionCollector.BlockEntitySnapshot> blockEntities = new LinkedHashMap<>();
        queryBlocks(level, snap, hits, bucket, cam, terrain, blockEntities, timestamp);

        // Fabulous（useShaderTransparency）：半透明方块（§5.4）与半透明掉落物（工序 D）的独立通道
        // 均仅在此配置执行；Fancy/Fast 下两者写 main 深度、由 §5.1/§5.3 主路径拾取。
        final boolean fabulous = Minecraft.useShaderTransparency();

        // ④ 半透明方块 —— 仅 Fabulous（v2.10 配置分支）；Fancy/Fast 下跳过、由 §5.1 直查
        // v2.24（两深度锚点，§5.4）：工序 B 用 translucent 深度逐像素精确落位首层半透明；
        // v2.26 工序 C 重写为区间射线推进（含全部嵌套层 + 区间内绊线）+ 独立绊线候选。
        // translucentDepth 读不到（第二路 PBO 降级）→ 工序 B 空集、工序 C 回退候选驱动精筛
        // （行为等同 v2.23/v2.24）。
        if (fabulous) {
            final boolean hasInterval = snap.hasTranslucentDepth();
            final LongSet firstSurface = hasInterval
                    ? queryFirstTranslucent(level, snap, unproj, cam, terrain, timestamp)
                    : new LongOpenHashSet();
            final int terrainBefore = terrain.size();
            final TripwireDiag tripwireDiag = new TripwireDiag();
            if (hasInterval) {
                queryTranslucent(level, snap, unproj, cam, terrain, timestamp, firstSurface, tripwireDiag);
            } else {
                queryTranslucentFallback(level, snap, unproj, cam, terrain, timestamp, firstSurface);
            }
            // v2.24 诊断：工序 B 放了多少首层半透明、工序 C 补了多少（嵌套 + 绊线）。
            LOGGER.info("[Vision] translucent: firstSurface(B)={}, refinedAdded(C)={}, terrainTotal={}, hasTranslucentDepth={}",
                    firstSurface.size(), terrain.size() - terrainBefore, terrain.size(), hasInterval);
            // v2.37 八次修订（§15.10）诊断：区间外单绊线候选通道（§5.4）逐闸门计数。本通道此前
            // **完全静默**——采集不到绊线时无法区分"某道闸门挡下"与"根本没进循环"，也无法区分
            // "形状与渲染模型不符"（noShapeHit）与"被遮挡/判据不过"（occluded）。一行一帧（本行与
            // 上面那行同频，均按采集帧而非渲染帧打）。
            LOGGER.info("[Vision] tripwire: {}", hasInterval
                    ? tripwireDiag.summary()
                    : "channel skipped（无 translucent 深度 → 降级路径 queryTranslucentFallback 接管，绊线随其候选枚举采集）");
        }

        // ③ 实体正向像素归属
        final List<VisionCollector.EntityLightSnapshot> entities = queryEntities(level, snap, hits, bucket);

        // v2.25（§5.3.1）工序 D：半透明材质掉落物（ItemEntity）正向归属——仅 Fabulous。
        // Fabulous 下玻璃/药水类掉落物画进独立 item_entity 目标、不写 main 深度（§3.1.1），
        // §5.3 深度归属拿不到它们（W 永远落不到其盒上）；但它们已在实体快照中（含 AABB），
        // 复用 §5.4 统一判定式 Z_opaque ≥ t_entry − δ 判定可见性。Fancy/Fast 下掉落物写 main
        // 深度、§5.3 已覆盖，本工序跳过（与 §5.4 配置分支同构）。
        if (fabulous) {
            final int beforeDrops = entities.size();
            final Set<UUID> reported = new HashSet<>(entities.size() * 2 + 1);
            for (VisionCollector.EntityLightSnapshot v : entities) reported.add(v.uuid());
            queryTranslucentDrops(snap, unproj, cam, reported, entities);
            // v2.25 诊断：工序 D 补了多少半透明掉落物。
            LOGGER.info("[Vision] drops(D): added={}, entityTotal={}",
                    entities.size() - beforeDrops, entities.size());
        }

        // v2.23（§7.11）：减量判定——对记忆侧反向通道上报的记忆格逐块深度判定，产出被证明消失的
        // 格（deletions）。在四路查询之后执行：currentTerrain = 本次可见集，先跳过可见格（双保险 +
        // 优化），不可见格才走逐块判定。记忆侧离线（无 cells）→ 空清单 → 无删除证据 → 只增不删。
        // v2.32：cells 头部带维标签 → 只对「维度与本快照一致」的 cells 做判定；旧 version=1 文件无
        // 维标签（dimension 为空）→ 空清单（宁可不删、不跨维误删；镜像瞬态落后时优雅降级为只增）。
        //
        // v2.36（§7.12）：cells 分两段——main 段（实心不透明 + 冻结实体占用格 + 岩浆）恒走 main 场判；
        // translucent 段（水 + 满格透明）按「渲染配置 + 该格现实写哪张场」路由（判据场选择，§7.12）：
        //   Fabulous && hasTranslucentDepth && translucentEnabled → translucent 场判（testTranslucent）；
        //   Fancy/Fast（无独立 translucent 目标，水/满格透明写 main）→ 并入现有 main 场判据（test）；
        //   Fabulous && !hasTranslucentDepth（第二路 PBO 软失败）→ translucent 段判据空集（幽灵暂留，
        //     水/满格透明绝不可喂 main 场——恒假消失，优雅降级、恢复后自愈）；岩浆走 main 段不受影响。
        final boolean dimensionOk = cells.dimension() != null && !cells.dimension().isEmpty()
                && cells.dimension().equals(dimensionId);
        final List<BlockPos> deletions = new ArrayList<>();
        final List<BlockPos> translucentDeletions = new ArrayList<>();
        if (dimensionOk) {
            // main 段 → main 场（§7.11 原样判据）
            deletions.addAll(DeletionJudge.test(snap, unproj, cells.cells(), cells.pixelThreshold(), terrain.keySet()));
            // translucent 段 → 按当前图形配置路由（与 §5.4 采集通道同口径，不得混用）
            if (!cells.translucentCells().isEmpty()) {
                if (fabulous) {
                    if (snap.hasTranslucentDepth() && cells.translucentEnabled()) {
                        translucentDeletions.addAll(DeletionJudge.testTranslucent(
                                snap, unproj, cells.translucentCells(), cells.pixelThreshold(), terrain.keySet()));
                    }
                    // Fabulous && (!hasTranslucentDepth || !translucentEnabled) → translucent 段空集
                } else {
                    // Fancy/Fast：水/满格透明写 main → 并入现有 main 场判据
                    translucentDeletions.addAll(DeletionJudge.test(
                            snap, unproj, cells.translucentCells(), cells.pixelThreshold(), terrain.keySet()));
                }
            }
            deletions.addAll(translucentDeletions);
        }

        // v2.37（§3.2 / §5.2）几何段：非满形状方块（栅栏 / 铁栏杆 / 墙 / 玻璃板 / 十字植物…）走
        // "射线 ∩ 记忆形状 + sprite 掩码" 的精确判据（DeletionJudge.testShaped），替代整格判据——
        // 整格判据会把"射线只穿过格内空余区"也计为一票越过，对方块还在的情形造成误删（§1）。
        //
        // 两处 fail-closed（全部走欠删）：
        //   ① cells 的 spriteEpoch 与本地已载入的 memory_sprites.bin 不符（记忆侧刚换 epoch / 文件
        //      尚未落盘 / 本端还没读到）⇒ 整段不作判——下标→掩码的对应关系不可信，宁可不判；
        //   ② 单格解引用失败（方块 id 未知 / 渲染层不可判 / 该格 quad 全不可用）⇒ 该格跳过。
        final List<BlockPos> shapedDeletions = new ArrayList<>();
        final int shapedJudged;
        // 诊断（设计 §10 第 16 条）：本轮几何段的 h_max → δ_cell 直方图。
        // 判据 2b 的 δ 是**逐格**的（§3.2 2b-δ）：δ ≥ h_max 时该格按构造永不投票，而"删了几个"
        // 完全反推不出这件事（不删既可能是"真没删"也可能是"判据不可满足"）⇒ 必须直接打出来，
        // 并与 §3.2 的表逐行核对。按 h_max 分组而非逐格打：h_max 是模型属性，同种方块必然同值，
        // 故一"组"恰好就是那张表的一"行"——既等价又不会刷屏（712 格 → 个位数行）。
        final Map<Float, Integer> deltaHist = new TreeMap<>();
        // 诊断（设计 §10 第 18 条 / 决策 Q）：本轮因 δ 可行窗口闭合而跳过的格数。跳过恒是欠删
        // （安全），但它<b>永不投票</b>——不记账就与"判了但没删"无法区分，会被误读成识别准确。
        final int[] shapedUnjudged = new int[1];
        if (dimensionOk && !cells.shapedCells().isEmpty()) {
            final SpriteTableCache sprites = SpriteTableCache.get();
            if (sprites.hasEpoch(cells.spriteEpoch())) {
                final List<ShapedCellData.ResolvedCell> resolved = new ArrayList<>(cells.shapedCells().size());
                for (ShapedCellData.ShapedCell sc : cells.shapedCells()) {
                    final ShapedCellData.ResolvedCell rc = resolveShapedCell(sc, sprites, fabulous,
                            snap.hasTranslucentDepth(), cells.translucentEnabled());
                    if (rc != null) {
                        resolved.add(rc);
                        deltaHist.merge(rc.shapeMaxY(), 1, Integer::sum);
                    }
                }
                shapedJudged = resolved.size();
                shapedDeletions.addAll(DeletionJudge.testShaped(
                        snap, unproj, resolved, cells.pixelThreshold(), terrain.keySet(), shapedUnjudged));
            } else {
                shapedJudged = -1; // 整段作废（epoch 不符 / sprite 表不可得）
                // local=0 表示本端**没有**可用的 sprite 表（文件缺失 / 解析失败——多为格式错位），
                // 而非"只是还没刷新"；两者都会让本轮所有非满形状方块判不出消失，故必须能一眼区分。
                LOGGER.info("[Vision] shaped geometry skipped: sprite epoch mismatch (cells={}, local={}{})",
                        cells.spriteEpoch(), sprites.epoch(),
                        sprites.epoch() == 0L ? " = 本端无可用 sprite 表（缺失/解析失败）" : "");
            }
        } else {
            shapedJudged = 0;
        }
        deletions.addAll(shapedDeletions);

        // v2.37 七次修订（§15）信号缺失族校正通道：绊线 / 绊线钩在 Fabulous 下画进 weather 目标、
        // 不写任何已读深度场（§10.8）⇒ 深度判据对它们"活体无信号"：判"活着"与"消失了"不可区分，
        // 故自 v2.23 起整族排除出几何判据（§2.2 行 1）。但该族缺的不是"可判性"而是**判据的输入**——
        // "该格现实里还是不是绊线"在真实世界侧可以直接读。本通道把判据由**深度推断**换成**状态直读**：
        //   区块未加载 / VOID_AIR ⇒ 不裁决（读不到 ≠ 不存在，P1，默认方向 = 保留）；
        //   非空气 ⇒ 不裁决（在场，或换成了别的东西——后者由正向观测通道覆盖，P3）；
        //   空气 ⇒ 缺席。
        // **并列**而非并入 deletions：两条通道各有独立守卫（deletions 走 isDeletableContent ∪
        // isShapedDeletableContent；本通道走记忆侧的 isSignalLossBlock），合并会让记忆侧只能用一道
        // 守卫、同时放宽两道保险（§15.2 的核心结构决定）。本通道对渲染帧零依赖——不读深度、不读 PBO、
        // 与 Fabulous / translucentEnabled 全无关，故不受此处任何图形配置分支影响。
        // 采集侧**刻意不持**信号缺失族名单：族由记忆侧上报什么定义，"以状态为准"这一条与方块种类无关。
        final List<BlockPos> signalLossDeletions = new ArrayList<>();
        final SignalLossCorrector.Stats signalLossStats;
        if (dimensionOk && !cells.signalLossCells().isEmpty()) {
            final SignalLossCorrector.Result slr =
                    SignalLossCorrector.correct(level, cells.signalLossCells(), terrain.keySet());
            signalLossDeletions.addAll(slr.absent());
            signalLossStats = slr.stats();
        } else {
            signalLossStats = null; // 记忆侧离线 / 空段 / 跨维 → 无候选，无话可说
        }

        // v2.36/v2.37 诊断：main/translucent/shaped 三段各自判删数 + 几何段可判格数
        LOGGER.info("[Vision] deletions: main={}, translucent={}, shaped={}/{} judged (total={}, fabulous={}, "
                        + "hasTranslucentDepth={}, translucentEnabled={})",
                deletions.size() - translucentDeletions.size() - shapedDeletions.size(),
                translucentDeletions.size(), shapedDeletions.size(), shapedJudged,
                deletions.size(), fabulous, snap.hasTranslucentDepth(), cells.translucentEnabled());

        // v2.37 七次修订（§15.7 / §10 第 19 条）诊断：信号缺失族校正通道逐格记账。
        // 本通道的四档跳过全部**方向安全**（跳过 = 不裁决 = 欠删），但它们的含义完全不同，混在一起就
        // 无法与"判了但没删"区分——尤其这三档：
        //   unloaded：P1 守卫真的在干活。非零正常且必需（玩家走远即发生）；候选非空却恒为 0，反而要查。
        //   voidAir ：守卫未覆盖到的环境差异。**正常应恒 0**，非零即 P1 那个坑露出了新路径。
        //   visibleSkipped：恒不应触发的双保险。非零即"可见集与状态直读不一致"，唯一在线矛盾指示器。
        // 空段（无候选）时 signalLossStats 为 null，不打——否则每帧一行零。
        if (signalLossStats != null) {
            LOGGER.info("[Vision] signalLoss: candidates={}, absent={}, unloaded={}, voidAir={}, "
                            + "stillPresent={}, visibleSkipped={}",
                    signalLossStats.candidates(), signalLossDeletions.size(), signalLossStats.unloaded(),
                    signalLossStats.voidAir(), signalLossStats.stillPresent(), signalLossStats.visibleSkipped());
        }

        // v2.37 诊断（设计 §10 第 16 条）：δ 逐格化的实际落点。判据 2b 为 Z ≥ t_shape_exit + δ_cell，
        // 而 δ_cell = min(0.05, 0.5·h_max) —— h_max ≤ 0.05 的"贴地平铺贴花"（红石粉 / 落叶）在旧口径
        // 下按构造不可满足。此行直接给出"本轮每类高度各拿多少 δ"，用于和 §3.2 的表逐行核对——
        // 尤其确认红石粉/落叶落在 0.0078 而不是 0.0500。整段作废时（shapedJudged<0）无话可说，不打。
        if (!deltaHist.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            for (Map.Entry<Float, Integer> e : deltaHist.entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                // Locale.ROOT：默认 locale 用逗号作小数点（如 de_DE）时，"0,0156" 会与分组间的
                // 逗号分隔符混淆，日志失去可读性（且这条日志是给人逐行核对 §3.2 表的）。
                sb.append(String.format(java.util.Locale.ROOT, "h_max=%.4f→δ_cell=%.4f×%d格",
                        e.getKey(), DeletionJudge.deltaFor(e.getKey()), e.getValue()));
            }
            LOGGER.info("[Vision] shaped δ breakdown (δ_global={}, noise_c={}): {}; 窗口闭合跳过={}格（欠删，§14.5）",
                    DeletionJudge.DELTA_GLOBAL, DeletionJudge.DELTA_NOISE_C, sb, shapedUnjudged[0]);
        }

        // v2.31（生物群系，见 docs/生物群系复原设计方案.md）：在三 store 落盘前为群系通道采样。
        // 候选 = 本帧全部可见方块（此刻 terrain 已含 §5.4 半透明/绊线补采）+ 相机 cell 锚点
        // （脚下是空气、悬空时仍保证玩家所在 cell 有记录，使记忆世界雾/天空色正确）。
        // VisionBiomeStore 内部按 4×4×4 quart cell 去重（同 cell 群系必然相同——游戏存储保证，
        // 故绝不逐方块写）、只解析 union 之外的新 cell、单调整体写 biomes.nbt。
        final List<BlockPos> biomeSamplePoints = new ArrayList<>(terrain.size() + 1);
        biomeSamplePoints.addAll(terrain.keySet());
        biomeSamplePoints.add(BlockPos.containing(cam)); // 相机 cell 锚点（cam 恒非空）
        final VisionBiomeStore.Stats biomeStats = VisionCollector.getBiomeStore().sync(level, biomeSamplePoints, dimensionId);

        // 三 store 落盘（§6.1）；agent 视角随 agentPos 一并落盘（v2.15）
        // v2.18：agent 坐标改为相机（眼睛）双精度坐标（游戏精度），不再取整到方块，
        // 记忆世界据此把玩家眼睛精确还原到 agent 采集时的位置。
        final Vec3 agentPos = cam;
        final float agentYaw = snap.yaw();
        final float agentPitch = snap.pitch();
        // v2.19：agent 基础 FOV 一并落盘，供记忆世界同步视场角（动态 FOV 已烤进投影矩阵）。
        final int agentFov = snap.fov();
        // v2.21：采集时刻世界时间一并落盘（§7.10），记忆世界据此 setDayTime 对齐昼夜。
        final long worldTime = snap.dayTime();
        // v2.23：deletions 随 terrain.nbt 顶层落盘，记忆世界 DeletionApplier 据此减量（§7.11）。
        // v2.37 七次修订（§15.3）：signalLossDeletions 作为**并列键**随同一份 terrain.nbt 落盘
        // （不并入 deletions，见上）；记忆侧 DeletionApplier 用并列通道按各自的守卫执行。
        final Map<String, Object> terrainStats = VisionCollector.getTerrainStore().sync(
                terrain, deletions, signalLossDeletions, agentPos, agentYaw, agentPitch, agentFov,
                worldTime, dimensionId);
        final Map<String, Integer> beStats = VisionCollector.getStore().sync(
                blockEntities, agentPos, agentYaw, agentPitch, agentFov, worldTime, dimensionId);
        final Map<String, Object> entityStats = VisionCollector.getEntityStore().sync(
                entities, agentPos, agentYaw, agentPitch, agentFov, worldTime, dimensionId);

        return new ResolveResult(terrain, blockEntities, entities, deletions, dimensionId,
                terrainStats, beStats, entityStats,
                Map.of("cells", biomeStats.cells(), "added", biomeStats.added()));
    }

    /** 由实体快照构建 SectionPos 桶（§5.3 粗过滤；桶与命中盒统一 inflate 0.5，v2.10）。 */
    public static Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> buildBucket(
            final List<DepthCapture.EntitySnapshotData> entities
    ) {
        final Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> bucket = new Long2ObjectOpenHashMap<>();
        for (DepthCapture.EntitySnapshotData e : entities) {
            final AABB box = e.box().inflate(0.5);
            final int minSX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
            final int maxSX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX));
            final int minSY = SectionPos.blockToSectionCoord(Mth.floor(box.minY));
            final int maxSY = SectionPos.blockToSectionCoord(Mth.floor(box.maxY));
            final int minSZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
            final int maxSZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ));
            for (int sx = minSX; sx <= maxSX; sx++) {
                for (int sy = minSY; sy <= maxSY; sy++) {
                    for (int sz = minSZ; sz <= maxSZ; sz++) {
                        bucket.computeIfAbsent(SectionPos.asLong(sx, sy, sz), k -> new ArrayList<>()).add(e);
                    }
                }
            }
        }
        return bucket;
    }

    // ==================== §5.1 / §5.2 方块 + 方块实体 ====================

    private static void queryBlocks(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector.UnprojectResult hits,
            final Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> bucket,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final Map<BlockPos, VisionCollector.BlockEntitySnapshot> blockEntities,
            final long timestamp
    ) {
        // 落格推移的实际用量（= 逐像素 nudged 的推进量）：回退还原 W 必须用它，不能用 PROBE_EPSILON
        // ——见 PROBE_EPSILON 的 javadoc（两者同值时代码无差别，落格量单独下调后立刻致命）。
        final double landingEps = hits.landingEpsilon();
        for (var e : hits.blockHits().long2ObjectEntrySet()) {
            final BlockPos pos = BlockPos.of(e.getLongKey());
            final Vec3 nudged = e.getValue();
            final BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                recordBlock(level, pos, state, cam, terrain, blockEntities, timestamp);
            } else {
                // air 近侧回退（§4.2/§5.1）：
                final Vec3 diff = nudged.subtract(cam);
                final double len = diff.length();
                if (len < 1e-9) continue;
                final Vec3 dir = diff.scale(1.0 / len);
                // 恢复原始表面点 W = nudged − dir·ε（ε = 落格时的实际推进量，见上）
                final double wx = nudged.x - dir.x * landingEps;
                final double wy = nudged.y - dir.y * landingEps;
                final double wz = nudged.z - dir.z * landingEps;
                // v2.10 实体相交验证：W 落在桶内任一实体盒内 ⇒ 记录面是实体表面（薄实体贴墙/肢体）
                // ⇒ 跳过回退，防穿透采集后方被遮挡方块。
                if (intersectsAnyEntity(wx, wy, wz, bucket)) continue;
                final BlockPos cand = BlockPos.containing(wx, wy, wz);
                final BlockState candState = level.getBlockState(cand);
                if (!candState.isAir()) {
                    recordBlock(level, cand, candState, cam, terrain, blockEntities, timestamp);
                }
                // 仍 air → 丢弃（实体表面，§5.3 正向查询独立处理）
            }
        }
    }

    private static void recordBlock(
            final ClientLevel level,
            final BlockPos pos,
            final BlockState state,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final Map<BlockPos, VisionCollector.BlockEntitySnapshot> blockEntities,
            final long timestamp
    ) {
        if (!terrain.containsKey(pos)) {
            terrain.put(pos, new VisionCollector.TerrainBlockSnapshot(
                    pos, VisionCollector.blockId(state), VisionCollector.stateProps(state), timestamp));
        }
        // §5.2 方块实体：搭方块便车（方块可见 → 方块实体可见）
        if (state.hasBlockEntity() && !blockEntities.containsKey(pos)) {
            // v2.10 壁挂朝向过滤：壁挂式 BE 朝向背对相机且无可视反射 → 不序列化（方块背面可见、BE 正面被挡的假阳性）
            if (isWallFacingAway(state, pos, cam)) return;
            final BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return;
            final String typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType()).toString();
            // v2.27（§5.2.1）：saveWithFullMetadata 是"客户端副本同步面上界"，仍须按 typeId 白名单剥离
            // L2 交互内部（容器物品/装饰罐内藏物/可疑方块未揭示物等）——不可直接把整包塞进快照。
            final CompoundTag nbt = BlockEntityFieldPolicy.filter(typeId, be.saveWithFullMetadata(level.registryAccess()));
            blockEntities.put(pos, new VisionCollector.BlockEntitySnapshot(
                    pos, typeId, VisionCollector.blockId(state), VisionCollector.stateProps(state), nbt, timestamp));
        }
    }

    /**
     * v2.10 简化朝向过滤（v2.14 收紧作用范围）：仅对<b>壁挂式</b>方块实体（告示牌 / 悬挂告示牌 /
     * 横额 / 头颅——{@code WallSignBlock}/{@code WallHangingSignBlock}/{@code WallBannerBlock}/
     * {@code WallSkullBlock}）在水平朝向背对相机（视线点积 &lt; 0）时跳过序列化。
     *
     * <p>⚠️ 不得对所有带 {@code HORIZONTAL_FACING} 的方块实体套用——箱子 / 熔炉 / 木桶 / 漏斗 /
     * 发射器 / 潜影盒等同样有该属性，但它们并非壁挂式、内容与朝向无关，方块可见即应序列化
     * （套用会把这些可见 BE 的 NBT 整条漏掉，v2.14）。
     */
    private static boolean isWallFacingAway(final BlockState state, final BlockPos pos, final Vec3 cam) {
        final Block block = state.getBlock();
        if (!(block instanceof WallSignBlock
                || block instanceof WallHangingSignBlock
                || block instanceof WallBannerBlock
                || block instanceof WallSkullBlock)) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            final Vector3f n = facing.step();
            final double dot = (cam.x - (pos.getX() + 0.5)) * n.x()
                    + (cam.y - (pos.getY() + 0.5)) * n.y()
                    + (cam.z - (pos.getZ() + 0.5)) * n.z();
            return dot < 0.0;
        }
        return false;
    }

    /** W 是否落在桶内任一实体盒内（v2.10 实体相交验证，罕见路径，遍历全部可接受）。 */
    private static boolean intersectsAnyEntity(
            final double wx, final double wy, final double wz,
            final Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> bucket
    ) {
        for (List<DepthCapture.EntitySnapshotData> list : bucket.values()) {
            for (DepthCapture.EntitySnapshotData e : list) {
                if (containsInflated(e, wx, wy, wz, PROBE_EPSILON)) return true;
            }
        }
        return false;
    }

    // ==================== §5.3 实体（正向像素归属） ====================

    private static List<VisionCollector.EntityLightSnapshot> queryEntities(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector.UnprojectResult hits,
            final Long2ObjectOpenHashMap<List<DepthCapture.EntitySnapshotData>> bucket
    ) {
        final List<VisionCollector.EntityLightSnapshot> out = new ArrayList<>();
        final Set<UUID> seen = new HashSet<>();
        final Vec3 cam = snap.cameraPos();
        final double camX = cam.x, camY = cam.y, camZ = cam.z;

        for (Vec3 w : hits.entityCandidatePoints()) {
            final long sec = SectionPos.asLong(
                    SectionPos.blockToSectionCoord(w.x),
                    SectionPos.blockToSectionCoord(w.y),
                    SectionPos.blockToSectionCoord(w.z));
            final List<DepthCapture.EntitySnapshotData> list = bucket.get(sec);
            if (list == null) continue; // 快速路径：绝大多数像素 O(1) 桶 miss 跳过

            final double dx = w.x - camX, dy = w.y - camY, dz = w.z - camZ;
            final double lenW = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (lenW < 1e-9) continue;
            final Vec3 dir = new Vec3(dx / lenW, dy / lenW, dz / lenW);

            for (DepthCapture.EntitySnapshotData e : list) {
                final AABB box = e.box();
                // 相机在实体盒内 → 可见（v2.8 早退）
                if (closedContains(box, camX, camY, camZ)) {
                    addEntity(e, seen, out);
                    continue;
                }
                // 侧向对齐：W ∈ box.inflate(0.5)（v2.10 统一 inflate；闭区间防半开区间把盒 max 面拒之门外，v2.9）
                if (!containsInflated(e, w.x, w.y, w.z, 0.5)) continue;
                // 深度排序：射线与未外扩盒的近交点（手写带符号 slab 求交，v2.9；null = 不穿盒）
                final Double tEntry = slabEntry(cam, dir, box);
                if (tEntry == null) {
                    // 肢体判别 A（v2.11 收紧）：记录面在盒外 → 非他体 + 非薄方块 + 前向空扫
                    if (!limbDiscriminationA(level, w, dir, lenW, e, list)) continue;
                    addEntity(e, seen, out);
                } else if (lenW >= tEntry) {
                    // 记录面在实体本体或其近面之后 → 可见
                    addEntity(e, seen, out);
                } else {
                    // 肢体判别 B（v2.11 收紧）：记录面在盒前 → 非他体 + 前向扫至盒近面
                    if (!limbDiscriminationB(level, w, dir, tEntry - lenW, e, list)) continue;
                    addEntity(e, seen, out);
                }
            }
        }
        return out;
    }

    private static void addEntity(
            final DepthCapture.EntitySnapshotData e,
            final Set<UUID> seen,
            final List<VisionCollector.EntityLightSnapshot> out
    ) {
        if (!seen.add(e.uuid())) return; // 多像素命中同一实体 → 去重
        // v2.34：item（掉落物物品栈 tag，快照帧已编码）随轻量快照一并上报，null 为非 item 实体。
        // v2.35：payload/content（展示实体整份 NBT + 薄摘要）同样在采集帧已编码、随快照透传，
        //         ObjectResolver 只做纯数据搬运（不触游戏，§8）。
        out.add(new VisionCollector.EntityLightSnapshot(
                e.id(), e.uuid(), e.typeId(),
                e.x(), e.y(), e.z(),
                e.yaw(), e.pitch(),
                e.vx(), e.vy(), e.vz(),
                e.onGround(), e.health(), e.item(), e.payload(), e.content()));
    }

    // ==================== §5.3.1 半透明掉落物（工序 D，v2.25） ====================

    /**
     * v2.25 工序 D（§5.3.1）：半透明材质掉落物（ItemEntity）正向归属。
     *
     * <p>Fabulous 下玻璃/药水类掉落物画进独立 item_entity 目标、不写 main 深度（§3.1.1），§5.3
     * 的 W 深度归属永远落不到其盒上 → 漏。但它们已在 {@code extractVisibleEntities} 实体快照中
     * （含 partialTick 插值 AABB，v2.10）。缺的只是可见性判定——用 §5.4 统一判定式
     * {@code Z_opaque ≥ t_entry − δ} 正向枚举盒覆盖的全部像素（三况统一：背景有不透明面 /
     * 贴天空 / 被不透明遮挡），复用 §5.3 盒/slab 求交与 §5.4 bbox 投影几何，无需新增深度目标。
     *
     * <p>候选 = 快照中未被 §5.3 报告的 ItemEntity。半透明掉落物必在此集合（§5.3 天然够不到）；
     * 被不透明物遮挡的不透明掉落物也在，判定式会正确判不可见——不预过滤，正确性等价、
     * 免渲染层判定 API 依赖。
     *
     * @param reported §5.3 已报告的实体 uuid 集合；本工序只处理不在其内的 ItemEntity，可见者加入
     * @param out      追加可见掉落物的实体列表（与 §5.3 同一 store / 响应）
     */
    private static void queryTranslucentDrops(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final Set<UUID> reported,
            final List<VisionCollector.EntityLightSnapshot> out
    ) {
        for (DepthCapture.EntitySnapshotData e : snap.entities()) {
            // 掉落物大类判定用共享谓词（VisionCollector#isItemEntity，注册表派生的单一事实来源），
            // 与采集端 item 载荷产端（LevelRendererMixin）保持一致。
            if (!VisionCollector.isItemEntity(e.typeId())) continue;
            if (reported.contains(e.uuid())) continue;
            if (isDropVisible(snap, unproj, cam, e)) {
                addEntity(e, reported, out);
            }
        }
    }

    /**
     * 工序 D：单个掉落物盒的可见性判定（§5.4 统一判定式，v2.25）。
     *
     * <p>候选盒 = 实体快照 AABB（partialTick 插值，v2.10）。⓪ 相机在盒内 → 可见早退；
     * ① 投影 8 角 → 屏幕 bbox（超集，像素中心约定 v2.11），显式裁剪到屏幕范围
     * （越界像素 {@code depthAt} 钳 1.0 会静默读成天空 → 假阳性，§4.2）；② 逐像素
     * "射线-AABB（带符号 slab）+ 主深度还原 {@code Z_opaque} 比较"——存在一个像素使
     * {@code Z_opaque ≥ t_entry − δ} 即可见（首个命中 break）。
     */
    private static boolean isDropVisible(
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final DepthCapture.EntitySnapshotData e
    ) {
        final AABB box = e.box();
        // 零/退化盒（marker 盔甲架同款，v2.11）→ 不判相交，防除零/NaN
        if (box.getXsize() <= 1e-9 || box.getYsize() <= 1e-9 || box.getZsize() <= 1e-9) return false;
        // ⓪ 相机在盒内（踩在掉落物堆里）→ 可见，早退
        if (closedContains(box, cam.x, cam.y, cam.z)) return true;
        final int width = snap.width();
        final int height = snap.height();
        // ① 投影 8 角 → 屏幕 bbox（超集，像素中心约定 v2.11）；任一角在相机背后 → 不入 bbox
        final double minX = box.minX, minY = box.minY, minZ = box.minZ;
        final double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        final double[] cornersX = {minX, minX, minX, minX, maxX, maxX, maxX, maxX};
        final double[] cornersY = {minY, minY, maxY, maxY, minY, minY, maxY, maxY};
        final double[] cornersZ = {minZ, maxZ, minZ, maxZ, minZ, maxZ, minZ, maxZ};
        float minPx = Float.MAX_VALUE, maxPx = -Float.MAX_VALUE;
        float minPy = Float.MAX_VALUE, maxPy = -Float.MAX_VALUE;
        int projected = 0;
        for (int i = 0; i < 8; i++) {
            final float[] pp = unproj.projectToScreen(new Vec3(cornersX[i], cornersY[i], cornersZ[i]), width, height);
            if (pp == null) continue;
            projected++;
            minPx = Math.min(minPx, pp[0]); maxPx = Math.max(maxPx, pp[0]);
            minPy = Math.min(minPy, pp[1]); maxPy = Math.max(maxPy, pp[1]);
        }
        // 全角不可投影 → 盒在相机背后 / 完全出屏 → 不可见。bbox 只是像素迭代范围，
        // 真伪由逐像素射线-AABB + 深度比较决定，bbox 缩小只降召回、不引入假阳性。
        if (projected == 0) return false;
        final int x0 = Math.max(0, (int) Math.floor(minPx));
        final int x1 = Math.min(width - 1, (int) Math.ceil(maxPx));
        final int y0 = Math.max(0, (int) Math.floor(minPy));
        final int y1 = Math.min(height - 1, (int) Math.ceil(maxPy));
        // ② 逐像素：射线-AABB（带符号 slab）+ 主深度还原 Z_opaque（贴天空 → ∞）比较
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                final Vec3 dir = unproj.pixelRay(px, py);
                if (dir == null) continue;
                final Double tEntry = slabEntry(cam, dir, box);
                if (tEntry == null) continue; // 射线不穿盒（bbox 超集 → continue 防误判）
                final float d = snap.depthAt(px, py);
                final double zOpaque;
                if (d >= unproj.dFar()) {
                    zOpaque = Double.POSITIVE_INFINITY; // 贴天空 → 可见
                } else {
                    final Vec3 w = unproj.unprojectPixel(px, py, d);
                    if (w == null) continue;
                    zOpaque = w.distanceTo(cam);
                }
                if (zOpaque >= tEntry - DELTA) return true; // 首个可见像素 break
            }
        }
        return false;
    }

    /** v2.11 肢体判别 A：W 在盒外（射线不穿盒）时的三项旁证，全过才判可见。 */
    private static boolean limbDiscriminationA(
            final ClientLevel level, final Vec3 w, final Vec3 dir, final double lenW,
            final DepthCapture.EntitySnapshotData self, final List<DepthCapture.EntitySnapshotData> list
    ) {
        // a. 非他体：W 不在桶内任一其他实体盒内
        if (!notOtherEntity(w, self, list)) return false;
        // b. 非薄方块：cell(W − dir·ε) 与 cell(W) 均非薄方块（空气不算薄），且 cell(W + dir·ε) 为空气
        final BlockPos minus = BlockPos.containing(w.x - dir.x * PROBE_EPSILON, w.y - dir.y * PROBE_EPSILON, w.z - dir.z * PROBE_EPSILON);
        final BlockPos at = BlockPos.containing(w.x, w.y, w.z);
        final BlockPos plus = BlockPos.containing(w.x + dir.x * PROBE_EPSILON, w.y + dir.y * PROBE_EPSILON, w.z + dir.z * PROBE_EPSILON);
        if (isThinBlock(level, minus) || isThinBlock(level, at)) return false;
        if (!level.getBlockState(plus).isAir()) return false;
        // c. 前向空扫：W 向远处，上限 |W−camPos| + max(2ε, 1 格)
        return forwardScanIsAir(level, w, dir, lenW + Math.max(2.0 * PROBE_EPSILON, 1.0));
    }

    /** v2.11 肢体判别 B：记录面在盒前（伸过洞口的肢体）。 */
    private static boolean limbDiscriminationB(
            final ClientLevel level, final Vec3 w, final Vec3 dir, final double scanDist,
            final DepthCapture.EntitySnapshotData self, final List<DepthCapture.EntitySnapshotData> list
    ) {
        if (!notOtherEntity(w, self, list)) return false;
        return forwardScanIsAir(level, w, dir, scanDist);
    }

    /** 非他体：W 不在桶内任一<b>其他</b>实体盒（inflate 0.5）内。 */
    private static boolean notOtherEntity(
            final Vec3 w, final DepthCapture.EntitySnapshotData self,
            final List<DepthCapture.EntitySnapshotData> list
    ) {
        for (DepthCapture.EntitySnapshotData other : list) {
            if (other == self) continue;
            if (containsInflated(other, w.x, w.y, w.z, 0.5)) return false;
        }
        return true;
    }

    /** 薄方块判定（压力板/按钮/红石线/雪层/铁轨等）；空气不算薄。 */
    private static boolean isThinBlock(final ClientLevel level, final BlockPos pos) {
        final BlockState st = level.getBlockState(pos);
        if (st.isAir()) return false;
        return !Block.isShapeFullBlock(st.getShape(level, pos));
    }

    /** 前向空扫：沿 dir 从 W 向远处扫（步长 {@link #SCAN_STEP}）到 limitDist，一路空气返回 true。 */
    private static boolean forwardScanIsAir(final ClientLevel level, final Vec3 w, final Vec3 dir, final double limitDist) {
        double d = SCAN_STEP;
        while (d <= limitDist + 1e-6) {
            if (!level.getBlockState(BlockPos.containing(w.x + dir.x * d, w.y + dir.y * d, w.z + dir.z * d)).isAir()) {
                return false;
            }
            d += SCAN_STEP;
        }
        return true;
    }

    // ==================== v2.37 非满形状几何段路由（§5.2） ====================

    /**
     * 把一条几何段记录解引用成可判形态：查方块注册 id → 定渲染层 → 定 {@code ALPHA_CUTOUT} 阈值与
     * 判据场 → 逐 quad 解引用 sprite 掩码。
     *
     * <p><b>为什么路由必须在采集侧做（§4.3.4）</b>：渲染层的选择依赖<b>本端</b>的图形设置
     * （尤其 {@code cutoutLeaves}：树叶在 CUTOUT 与 SOLID 之间切换），记忆侧无从得知。方块 id 因此是
     * <b>承重字段</b>而非诊断字段。
     *
     * <p><b>逐层阈值（§3.2 2a 第 2 条 / §5.2）</b>——即 {@code terrain.fsh} 里 {@code ALPHA_CUTOUT}
     * 宏的实际取值：
     * <ul>
     *   <li>{@link ChunkSectionLayer#SOLID}：无 {@code ALPHA_CUTOUT} define → 阈值 0（任何 texel 都写深度）；</li>
     *   <li>{@link ChunkSectionLayer#CUTOUT}：0.5；</li>
     *   <li>{@link ChunkSectionLayer#TRANSLUCENT}：0.01；</li>
     *   <li>{@link ChunkSectionLayer#TRIPWIRE}：<b>不可判</b>——绊线画进 weather 目标，既不写 main 深度
     *       也不写 translucent 深度，没有任何一条深度场能反映它（§7.12 边界）。</li>
     * </ul>
     *
     * <p><b>TRANSLUCENT 的判据场（与 §5.4 同口径，不得混用）</b>：Fabulous 且两路深度可用且记忆侧
     * 开了 translucent 场 → 该格写的是 translucent 场，读 translucent 深度；Fancy/Fast（无独立
     * 半透明目标，半透明写 main 深度）→ 读 main 场；Fabulous 但第二路 PBO 降级 → <b>不可判</b>
     * （绝不回退 main 场：判据场不同源时读到的深度不是该方块的表层深度，比较无意义 → 恒假消失）。
     *
     * @return 可判形态；该格不可判 / 无一条 quad 可用 → null（欠删，调用方跳过）
     */
    private static ShapedCellData.@Nullable ResolvedCell resolveShapedCell(
            final ShapedCellData.ShapedCell cell,
            final SpriteTableCache sprites,
            final boolean fabulous,
            final boolean hasTranslucentDepth,
            final boolean translucentEnabled
    ) {
        final Identifier id = Identifier.tryParse(cell.blockId());
        if (id == null) return null;
        final Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null) return null; // 本端没有这个方块（版本/模组不一致）→ 不可判

        final ChunkSectionLayer layer;
        try {
            layer = ItemBlockRenderTypes.getChunkRenderType(block.defaultBlockState());
        } catch (Throwable t) {
            return null;
        }

        final float alphaThr;
        final boolean translucentField;
        if (layer == ChunkSectionLayer.SOLID) {
            alphaThr = 0.0f;
            translucentField = false;
        } else if (layer == ChunkSectionLayer.CUTOUT) {
            alphaThr = 0.5f;
            translucentField = false;
        } else if (layer == ChunkSectionLayer.TRANSLUCENT) {
            alphaThr = 0.01f;
            if (fabulous) {
                if (!hasTranslucentDepth || !translucentEnabled) return null;
                translucentField = true;
            } else {
                translucentField = false;
            }
        } else {
            return null; // TRIPWIRE（不写任何深度场）/ 未知层
        }

        final List<ShapedCellData.ResolvedQuad> quads = new ArrayList<>(cell.quads().length);
        // 设计 §3.2 2b-δ：取**该格全部已解析 quad** 顶点在格局部的最大 y（不是"可用 quad"的）。
        // 用全部而非过滤后的，是取更保守的一侧：被掩码剔除的 quad 会让"可用几何"变矮，若据它缩 δ
        // 就等于"因数据缺失而放宽判据"，方向不安全。全部顶点的 max 与"该方块模型的高度"同义，
        // 且它与判据的实际语义自洽——形状越矮，§3.2 2b 能拿到的深度跳变上限就越小，余量必须同步缩小。
        float shapeMaxY = 0.0f;
        for (ShapedCellData.Quad q : cell.quads()) {
            for (int vi = 0; vi < 4; vi++) {
                final float y = q.vy(vi);
                if (y > shapeMaxY) shapeMaxY = y;
            }
        }
        for (ShapedCellData.Quad q : cell.quads()) {
            final SpriteTableCache.Mask mask = sprites.maskForIndex(q.spriteIndex());
            if (mask == null) continue; // 掩码不可得（越界 / 占位 / 动画 / 损坏）→ 该 quad 不参与
            quads.add(new ShapedCellData.ResolvedQuad(q, mask.alpha(), mask.width(), mask.height()));
        }
        if (quads.isEmpty()) return null; // 全部 quad 不可用 → 该格不可判（欠删）
        return new ShapedCellData.ResolvedCell(cell.pos(), alphaThr, translucentField,
                quads.toArray(new ShapedCellData.ResolvedQuad[0]), shapeMaxY);
    }

    // ==================== §5.4 半透明 / 绊线方块（仅 Fabulous） ====================

    /**
     * v2.12：§5.4 候选谓词 = TRANSLUCENT ∪ TRIPWIRE。绊线在 Fabulous 下与半透明同为
     * "main 深度看穿"（画进 weather 目标、不写 main 深度），复用同一精筛通道采集；
     * Fancy/Fast 下两者都写主深度、由 §5.1 主路径拾取，本谓词不会被用到。
     * 勿用 isTranslucent()——红石线/下界传送门等 isTranslucent()==false 却走 TRANSLUCENT 层，
     * 须与 §3.1.1 同一把尺。
     */
    private static boolean isSemiTransparentLayer(final BlockState state) {
        // 与 §3.1.1 / SectionCompiler 同一把尺（v2.26 修复）：方块层（getChunkRenderType）只查
        // TYPE_BY_BLOCK，不含 Blocks.WATER → 水会漏判为 SOLID；SectionCompiler 对非空流体按
        // 流体层 getRenderLayer(FluidState) 入组（水 → TRANSLUCENT），故须先判流体。岩浆流体层
        // 为 SOLID → 返回 false（写主深度、由 §5.1 拾取），与渲染事实一致。
        final FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            return ItemBlockRenderTypes.getRenderLayer(fluid) == ChunkSectionLayer.TRANSLUCENT;
        }
        final ChunkSectionLayer layer = ItemBlockRenderTypes.getChunkRenderType(state);
        return layer == ChunkSectionLayer.TRANSLUCENT || layer == ChunkSectionLayer.TRIPWIRE;
    }

    /**
     * v2.24 工序 B（§5.4 首层透明面深度 pass）：直接用 translucent 目标深度逐像素精确落位
     * 首层半透明表面，O(pixels)、无射线-AABB。与工序 C 成对：B 负责"恰好是首个可见表面"
     * 的半透明（水面 / 玻璃 / 染色玻璃正对相机的表面），C 负责嵌套半透明 + 绊线残留。
     *
     * <p>每像素两个锚点：主深度 m = 首个不透明表面距离，translucent 深度 t = 首个半透明
     * 表面距离（Fabulous 下两读同帧同对齐，不变量 t ≤ m）。像素分类（v2.24）：
     * <ul>
     *   <li>{@code t < m} ⇒ 半透明层在前（近锚点 = 水面/玻璃面，远锚点 = 水底/玻璃后不透明体）；
     *       把 t 反投影回世界坐标、落格，经 {@link #isSemiTransparentLayer} 验证后入 terrain。</li>
     *   <li>{@code t == m} ⇒ 该像素前方无非半透明层（不透明表面即首个表面）→ 工序 B 无产出。</li>
     * </ul>
     *
     * <p>去重双保险：像素间去重（{@code BlockPos.asLong → LongSet}）+ 块内去重
     * （{@link #addTerrain} 的 putIfAbsent）。注意 translucent 深度与主深度是<b>两套</b>渲染
     * 目标，非半透明像素的 t 是主深度拷贝（LEQUAL 深度写入），故 {@code t == m} 恒为
     * "无半透明在前"，不会把不透明表面误采进工序 B。
     *
     * @return 已放置的首层半透明方块 asLong 集合（可能为空）；工序 C 据此整候选跳过
     */
    private static LongSet queryFirstTranslucent(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp
    ) {
        final int width = snap.width();
        final int height = snap.height();
        final float[] depth = snap.depth();
        final float[] translucent = snap.translucentDepth();
        final LongSet placed = new LongOpenHashSet(512);

        for (int y = 0; y < height; y++) {
            final int row = y * width;
            for (int x = 0; x < width; x++) {
                final int idx = row + x;
                final float m = depth[idx];
                final float t = translucent[idx];
                // 分类：t == m → 无非半透明层在前；t < m → 半透明层在前（近锚点 = 水面/玻璃面）
                if (!(t < m)) continue;
                // 天空 / 远平面极限深度 → 该像素无反投影点，跳过
                final Vec3 s = unproj.unprojectPixel(x, y, t);
                if (s == null) continue;
                final BlockPos pos = BlockPos.containing(s);
                // 跨像素去重（同一水面的多像素命中同格）；块内由 addTerrain putIfAbsent 兜底
                if (!placed.add(pos.asLong())) continue;
                // 落格后验证半透明层（与 §3.1.1 同一把尺；t<m 分类下实际几乎恒真）
                final BlockState st = level.getBlockState(pos);
                if (!isSemiTransparentLayer(st)) continue;
                addTerrain(terrain, pos, st, timestamp);
            }
        }
        return placed;
    }

    /**
     * v2.26 工序 C（§5.4 重写）：双锚点<b>区间射线推进正向归属</b>，取代 v2.24"候选枚举 + 8px
     * 栅格合并 + 残留精筛"。
     *
     * <p>v2.24 候选粗筛含完整性漏洞——8px 屏幕栅格合并（同格只留最近，v2.11）把嵌套半透明候选
     * （深水柱中间水格、多层玻璃里层）在精筛之前丢弃，深湖/海洋无法完整复现水柱（§10.2）。本版
     * 弃用候选集：仅对存在透明区间（translucentDepth &lt; mainDepth）的像素，沿射线体素推进到首个
     * 不透明面（Z_opaque），跨 section 用 palette {@code maybeHas} 整节跳步、逐格按统一判定式
     * {@code t_entry ≤ Z_opaque − δ} 上报射线实际穿过的<b>每一个</b>可见半透明格（含全部嵌套层），
     * 总量 = 答案体积、无有损合并。首层面格由工序 B 精确落位，本推进只跳过不重采。
     *
     * <p>绊线（TRIPWIRE 画进 weather 目标、不写 main/translucent 深度，§3.1.1）：区间内的绊线随
     * 推进枚举；<b>区间外</b>的单绊线（无半透明在前 → t==m，本推进不覆盖）由
     * {@link #queryTripwireCandidates} 独立候选补齐（保留 v2.12/v2.24 采集能力）。
     */
    private static void queryTranslucent(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp,
            final LongSet firstSurfacePlaced,
            final TripwireDiag diag
    ) {
        final int width = snap.width();
        final int height = snap.height();
        final float[] depth = snap.depth();
        final float[] translucent = snap.translucentDepth();
        // 全像素共享的"已定论格"集合：预置首层面（工序 B 已放，推进只跳过），推进中可见格入集。
        final LongSet settled = new LongOpenHashSet(firstSurfacePlaced.size() + 32);
        settled.addAll(firstSurfacePlaced);

        for (int y = 0; y < height; y++) {
            final int row = y * width;
            for (int x = 0; x < width; x++) {
                final int idx = row + x;
                final float m = depth[idx];
                final float t = translucent[idx];
                // 无透明区间（t == m：首个表面即不透明面）→ 无半透明嵌套层可归属，跳过。
                // 区间外单绊线也在此（t==m）——由 queryTripwireCandidates 独立补齐。
                if (!(t < m)) continue;
                final Vec3 dir = unproj.pixelRay(x, y);
                if (dir == null) continue;
                final double zOpaque;
                if (m >= unproj.dFar()) {
                    // 贴天空：Z_opaque = +∞ → 推进上界 = 该像素远平面（近地平线像素远平面距离更大）
                    zOpaque = unproj.farPlaneDistance(x, y);
                } else {
                    final Vec3 w = unproj.unprojectPixel(x, y, m);
                    if (w == null) continue;
                    zOpaque = w.distanceTo(cam);
                }
                rayWalkTranslucent(level, cam, dir, zOpaque, settled, terrain, timestamp);
            }
        }

        // 区间外的独立绊线候选（v2.26 保留 v2.12/v2.24 采集能力）：绊线稀疏，palette 驱动枚举 +
        // 统一判定式精筛，无需 8px 有损合并。
        queryTripwireCandidates(level, snap, unproj, cam, terrain, timestamp, diag);
    }

    /**
     * 降级回退（v2.26 保留）：translucent 目标深度读不到（第二路 PBO 软失败）→ 无 t<m 区间可分，
     * 回退 v2.24 候选驱动精筛（候选枚举 + 8px 栅格合并 + 逐像素射线-AABB），行为等同 v2.23/v2.24。
     */
    private static void queryTranslucentFallback(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp,
            final LongSet firstSurfacePlaced
    ) {
        final int width = snap.width();
        final int height = snap.height();
        final LevelRenderer lr = Minecraft.getInstance().levelRenderer;

        // ---- 候选粗筛（v2.7/v2.10/v2.11）：遍历渲染距离内、已编译可见 section ----
        final int renderDist = Minecraft.getInstance().options.getEffectiveRenderDistance();
        final SectionPos camSec = SectionPos.of(BlockPos.containing(cam));
        final int minSecY = level.getMinSectionY();
        final int maxSecY = level.getMaxSectionY();
        final List<BlockPos> candidates = new ArrayList<>(1024);

        for (int sz = camSec.z() - renderDist; sz <= camSec.z() + renderDist; sz++) {
            for (int sx = camSec.x() - renderDist; sx <= camSec.x() + renderDist; sx++) {
                final LevelChunkSection[] sections = getSections(level, sx, sz);
                if (sections == null) continue;
                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    final int idx = level.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= sections.length) continue;
                    final LevelChunkSection section = sections[idx];
                    if (section == null || section.hasOnlyAir()) continue;
                    // palette 驱动（v2.7）：先快速判断该 section 是否有 TRANSLUCENT / TRIPWIRE 状态
                    if (!section.getStates().maybeHas(ObjectResolver::isSemiTransparentLayer)) {
                        continue;
                    }
                    // 粗筛须过滤未编译/不可见 section（v2.10）：数据在但未渲染 → 深度=远处 → 假阳性
                    final BlockPos sectionOrigin = SectionPos.of(sx, sy, sz).origin();
                    if (!lr.isSectionCompiledAndVisible(sectionOrigin)) continue;
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                final BlockState st = section.getBlockState(lx, ly, lz);
                                if (!isSemiTransparentLayer(st)) continue;
                                candidates.add(sectionOrigin.offset(lx, ly, lz));
                            }
                        }
                    }
                }
            }
        }

        // ---- 候选预裁剪 + 屏幕栅格合并（v2.11）：中心投影跳过屏外/亚像素，同格只留最近 ----
        final Map<Long, BlockPos> grid = new HashMap<>();
        final Map<Long, Double> gridDist = new HashMap<>();
        for (BlockPos pos : candidates) {
            final Vec3 center = pos.getCenter();
            final float[] pp = unproj.projectToScreen(center, width, height);
            if (pp == null) continue;
            final long key = ((long) ((int) (pp[0] / GRID)) << 32) | ((int) (pp[1] / GRID) & 0xffffffffL);
            final double distSq = center.distanceToSqr(cam);
            final Double prev = gridDist.get(key);
            if (prev == null || distSq < prev) {
                grid.put(key, pos);
                gridDist.put(key, distSq);
            }
        }

        // ---- 精筛（§5.4 工序 C，v2.24 语义）：逐像素"射线-AABB + 深度比较" ----
        // 首层半透明候选（firstSurfacePlaced 内）整候选跳过（可见性已由 translucent 深度证明）；
        // 嵌套半透明与绊线不在集合内 → 照常精筛。降级模式下 firstSurface 为空，跳过不生效。
        for (BlockPos pos : grid.values()) {
            if (firstSurfacePlaced.contains(pos.asLong())) continue;
            fineFilterTranslucent(level, snap, unproj, pos, cam, terrain, timestamp);
        }
    }

    private static LevelChunkSection[] getSections(final ClientLevel level, final int chunkX, final int chunkZ) {
        final var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        return chunk == null ? null : chunk.getSections();
    }

    /** section 状态（v2.26 区间推进）：其后无渲染内容（终止射线） / 无透明（整节跳过） / 含透明（逐格）。 */
    private static final int SEC_TERMINATE = 0;
    private static final int SEC_EMPTY = 1;
    private static final int SEC_HAS = 2;

    /** TRIPWIRE 层判定（v2.26 区间外绊线候选专用；§3.1.1 同一把尺）。 */
    private static boolean isTripwireLayer(final BlockState state) {
        return ItemBlockRenderTypes.getChunkRenderType(state) == ChunkSectionLayer.TRIPWIRE;
    }

    /**
     * v2.26 评估 section 是否可推进。编译可见性校验（v2.10 粗筛同款）防"数据在但未渲染 → 假阳性"；
     * 单快照内 section 状态不变，用 {@code verifiedVisible} 缓存避免对同一节重复查询 + 分配。
     *
     * <p>{@code isCameraSection=true}（起点节）：相机节必被渲染（玩家身处其中），跳过编译可见性校验——
     * 相机可能站/游泳在未标记可见的节里（半透明环绕），校验失败会让整条射线漏报。
     */
    private static int sectionState(
            final ClientLevel level, final LevelRenderer lr,
            final int secX, final int secY, final int secZ,
            final boolean isCameraSection, final LongSet verifiedVisible
    ) {
        if (!isCameraSection) {
            final long secKey = SectionPos.asLong(secX, secY, secZ);
            if (!verifiedVisible.contains(secKey)) {
                if (!lr.isSectionCompiledAndVisible(new BlockPos(secX << 4, secY << 4, secZ << 4))) {
                    return SEC_TERMINATE; // 未编译 / 不可见 → 其后无渲染内容，射线终止
                }
                verifiedVisible.add(secKey);
            }
        }
        final var chunk = level.getChunkSource().getChunkNow(secX, secZ);
        if (chunk == null) return SEC_TERMINATE;
        final int idx = level.getSectionIndexFromSectionY(secY);
        if (idx < 0 || idx >= chunk.getSections().length) return SEC_TERMINATE;
        final LevelChunkSection section = chunk.getSections()[idx];
        if (section == null || section.hasOnlyAir()) return SEC_EMPTY;
        return section.getStates().maybeHas(ObjectResolver::isSemiTransparentLayer) ? SEC_HAS : SEC_EMPTY;
    }

    /**
     * v2.26 区间射线推进（§5.4 工序 C）：从相机沿射线逐格推进到 Z_opaque，逐格归属射线实际穿过的
     * 可见半透明格（含全部嵌套层）。voxel DDA（Amanatides &amp; Woo）步进 + 跨节时 palette
     * {@code maybeHas} 整节跳步（非透明节 O(1)、未编译节终止射线）；每格成本 = 1 次 LongSet 查询，
     * 未定论格才 getBlockState + 形状求交。
     *
     * <p>{@code settled} 为"已定论格"（首层面 ∪ 已证可见透明格 ∪ 已证非透明格）：命中的非透明格
     * 入集（永不透明、后续像素跳过）；命中但未证可见的<b>透明</b>格不入集（δ 边界可见性依赖各像素
     * 自己的 Z_opaque，须逐像素重判，见 isCellVisible）。
     */
    private static void rayWalkTranslucent(
            final ClientLevel level,
            final Vec3 origin, final Vec3 dir,
            final double zOpaque,
            final LongSet settled,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp
    ) {
        final LevelRenderer lr = Minecraft.getInstance().levelRenderer;
        final LongSet verifiedVisible = new LongOpenHashSet(256);
        final double ox = origin.x, oy = origin.y, oz = origin.z;
        final double dx = dir.x, dy = dir.y, dz = dir.z;

        int x = Mth.floor(ox);
        int y = Mth.floor(oy);
        int z = Mth.floor(oz);

        // ---- voxel DDA 步进状态（每轴向"到下一格边界的距离"）----
        final int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        final int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        final int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);
        final double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        final double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        final double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = tDeltaX == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY : (dx > 0 ? (x + 1.0 - ox) : (ox - x)) * tDeltaX;
        double tMaxY = tDeltaY == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY : (dy > 0 ? (y + 1.0 - oy) : (oy - y)) * tDeltaY;
        double tMaxZ = tDeltaZ == Double.POSITIVE_INFINITY
                ? Double.POSITIVE_INFINITY : (dz > 0 ? (z + 1.0 - oz) : (oz - z)) * tDeltaZ;

        int secX = SectionPos.blockToSectionCoord(x);
        int secY = SectionPos.blockToSectionCoord(y);
        int secZ = SectionPos.blockToSectionCoord(z);
        int st = sectionState(level, lr, secX, secY, secZ, true, verifiedVisible);

        double t = 0.0;
        while (true) {
            // ---- 定位到含透明节起点：跳过 EMPTY 节（O(1) 整节），TERMINATE 终止射线 ----
            while (st == SEC_EMPTY) {
                final double tExit = sectionExitT(ox, oy, oz, dx, dy, dz, secX, secY, secZ);
                if (tExit >= zOpaque) return;
                final double tJump = tExit + 1e-7;
                t = tJump;
                x = Mth.floor(ox + dx * tJump);
                y = Mth.floor(oy + dy * tJump);
                z = Mth.floor(oz + dz * tJump);
                tMaxX = tDeltaX == Double.POSITIVE_INFINITY
                        ? Double.POSITIVE_INFINITY : (dx > 0 ? (x + 1.0 - ox) : (ox - x)) * tDeltaX;
                tMaxY = tDeltaY == Double.POSITIVE_INFINITY
                        ? Double.POSITIVE_INFINITY : (dy > 0 ? (y + 1.0 - oy) : (oy - y)) * tDeltaY;
                tMaxZ = tDeltaZ == Double.POSITIVE_INFINITY
                        ? Double.POSITIVE_INFINITY : (dz > 0 ? (z + 1.0 - oz) : (oz - z)) * tDeltaZ;
                secX = SectionPos.blockToSectionCoord(x);
                secY = SectionPos.blockToSectionCoord(y);
                secZ = SectionPos.blockToSectionCoord(z);
                st = sectionState(level, lr, secX, secY, secZ, false, verifiedVisible);
            }
            if (st == SEC_TERMINATE) return; // 其后无渲染内容（远平面 / 渲染距离边界）

            // ---- st == SEC_HAS：逐格处理 ----
            final long key = BlockPos.asLong(x, y, z);
            if (!settled.contains(key)) {
                final BlockState bs = level.getBlockState(new BlockPos(x, y, z));
                if (isSemiTransparentLayer(bs)) {
                    if (isCellVisible(level, origin, dir, x, y, z, bs, zOpaque)) {
                        addTerrain(terrain, new BlockPos(x, y, z), bs, timestamp);
                        settled.add(key);
                    }
                    // 透明但未证可见（δ 边界）：不入集，后续像素按各自 Z_opaque 重判
                } else {
                    settled.add(key); // 非透明格永不透明，定论
                }
            }
            // 推进一格（最早跨越的轴向）
            double tNext;
            if (tMaxX < tMaxY && tMaxX < tMaxZ) { tNext = tMaxX; x += stepX; tMaxX += tDeltaX; }
            else if (tMaxY < tMaxZ) { tNext = tMaxY; y += stepY; tMaxY += tDeltaY; }
            else { tNext = tMaxZ; z += stepZ; tMaxZ += tDeltaZ; }
            if (tNext > zOpaque) return; // 越过首个不透明面，其后被遮挡
            t = tNext;

            // 跨节检测（位运算判坐标落在节边界，免除法；两补码负数同样成立）
            final boolean crossedX = stepX != 0 && (x & 15) == (stepX > 0 ? 0 : 15);
            final boolean crossedY = stepY != 0 && (y & 15) == (stepY > 0 ? 0 : 15);
            final boolean crossedZ = stepZ != 0 && (z & 15) == (stepZ > 0 ? 0 : 15);
            if (crossedX || crossedY || crossedZ) {
                if (crossedX) secX = SectionPos.blockToSectionCoord(x);
                if (crossedY) secY = SectionPos.blockToSectionCoord(y);
                if (crossedZ) secZ = SectionPos.blockToSectionCoord(z);
                st = sectionState(level, lr, secX, secY, secZ, false, verifiedVisible);
            }
        }
    }

    /** 射线从当前 section 出界的距离（三轴中最早跨越的节边界，O(1)）；仅对当前所在节调用。 */
    private static double sectionExitT(
            final double ox, final double oy, final double oz,
            final double dx, final double dy, final double dz,
            final int secX, final int secY, final int secZ
    ) {
        final double minX = secX * 16.0, maxX = (secX + 1) * 16.0;
        final double minY = secY * 16.0, maxY = (secY + 1) * 16.0;
        final double minZ = secZ * 16.0, maxZ = (secZ + 1) * 16.0;
        double t = Double.POSITIVE_INFINITY;
        if (dx > 0) t = Math.min(t, (maxX - ox) / dx);
        else if (dx < 0) t = Math.min(t, (minX - ox) / dx);
        if (dy > 0) t = Math.min(t, (maxY - oy) / dy);
        else if (dy < 0) t = Math.min(t, (minY - oy) / dy);
        if (dz > 0) t = Math.min(t, (maxZ - oz) / dz);
        else if (dz < 0) t = Math.min(t, (minZ - oz) / dz);
        return t;
    }

    /**
     * v2.37 九次修订（§15.11.5，2026-09-12）：<b>世界坐标</b>形状盒的唯一来源 —— 把格内方块的"实际
     * 渲染形状"平移到该格的世界位置。流体按 {@code fluid.getHeight} 缩放 Y_max（{@code getShape} 对
     * 流体返回满格，满格盒会让"射线穿过格内空气部分"在天空背景下误判可见，v2.10 语义）。
     *
     * <p><b>为什么必须由本方法统一提供</b>：{@code state.getShape(level, pos)} 返回的是<b>格局部</b>
     * 坐标——vanilla {@code BlockBehaviour.getShape} 直接转发 {@code block.getShape(state,level,pos,ctx)}，
     * 不含 {@code move(pos)}；补这一下是调用方的责任（vanilla 自己在 {@code TripWireBlock:168} 写
     * {@code getShape(...).bounds().move(pos)}）。本类下方三处都与<b>世界空间</b>的相机/射线求交，
     * 直接用局部盒等于在<b>世界原点</b>摆一个 y≈0.1 的单位盒：沙漠里 y=65 的绊线因此被判成"距相机
     * 74 格、而场景深度只有 1.8 格" → 永远 {@code occluded}。这正是 §5.4 两个通道长期静默的根因
     * （§15.11.2 四环证据链），也是半透明族（水/玻璃/睡莲）自 v2.10 起从未真正生效的原因（§15.11.4）。
     *
     * <p><b>不要合并进来的两处</b>：{@link #isThinBlock} 与记忆侧 {@code BlockStateUtil} 的
     * {@code Block.isShapeFullBlock(getShape(...))} —— 它们把形状与<b>局部</b>的 {@code Shapes.block()}
     * 比较，口径本就是局部；搬到世界坐标反而会错（§15.11.7 表）。判定式与阈值（δ 等）不在本方法职责内。
     */
    private static List<AABB> worldShapeAabbs(final ClientLevel level, final BlockPos pos, final BlockState state) {
        final FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            final float h = fluid.getHeight(level, pos);
            return List.of(new AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0, pos.getY() + h, pos.getZ() + 1.0));
        }
        return state.getShape(level, pos).move(pos.getX(), pos.getY(), pos.getZ()).toAabbs();
    }

    /**
     * v2.26 统一判定式（§5.4）：单个透明格对给定射线是否可见——射线实际穿过该格"实际渲染形状"
     * （水按 fluid.getHeight 缩放 Y_max、普通透明块按 getShape，v2.10 同款）且
     * {@code t_entry ≤ Z_opaque − δ}（带符号 slab，相机在形状内 → t_entry 为负 → 可见）。
     */
    private static boolean isCellVisible(
            final ClientLevel level,
            final Vec3 origin, final Vec3 dir,
            final int x, final int y, final int z,
            final BlockState state, final double zOpaque
    ) {
        final BlockPos pos = new BlockPos(x, y, z);
        // v2.37 九次修订（§15.11.5）：形状盒必须是世界坐标（origin/dir 都是世界空间；zOpaque 是相机
        // 到首个不透明面的距离），由 worldShapeAabbs 统一提供。局部盒会让本判定恒不成立（§15.11.2）。
        final List<AABB> shapes = worldShapeAabbs(level, pos, state);
        if (shapes.isEmpty()) return false;
        final double tEntry = minSlabEntry(origin, dir, shapes);
        return tEntry != Double.MAX_VALUE && tEntry <= zOpaque - DELTA;
    }

    /**
     * v2.26 区间外独立绊线候选（保留 v2.12/v2.24 采集能力，§5.4）：绊线画进 weather 目标、不写
     * main/translucent 深度（§3.1.1），无半透明在前的单绊线像素 t==m、不在区间推进覆盖内。绊线
     * 稀疏，走"编译可见 section 的 palette 枚举 + 统一判定式精筛"（复用 {@link #fineFilterTranslucent}），
     * 无 8px 有损合并（绊线不成体量，无需性能 hack）。
     */
    private static void queryTripwireCandidates(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp,
            final TripwireDiag diag
    ) {
        final int renderDist = Minecraft.getInstance().options.getEffectiveRenderDistance();
        final SectionPos camSec = SectionPos.of(BlockPos.containing(cam));
        final int minSecY = level.getMinSectionY();
        final int maxSecY = level.getMaxSectionY();
        final LevelRenderer lr = Minecraft.getInstance().levelRenderer;
        diag.camSection = camSec;
        diag.renderDist = renderDist;

        for (int sz = camSec.z() - renderDist; sz <= camSec.z() + renderDist; sz++) {
            for (int sx = camSec.x() - renderDist; sx <= camSec.x() + renderDist; sx++) {
                final var chunk = level.getChunkSource().getChunkNow(sx, sz);
                if (chunk == null) continue;
                diag.chunks++;
                final LevelChunkSection[] sections = chunk.getSections();
                for (int sy = minSecY; sy <= maxSecY; sy++) {
                    final int idx = level.getSectionIndexFromSectionY(sy);
                    if (idx < 0 || idx >= sections.length) continue;
                    final LevelChunkSection section = sections[idx];
                    if (section == null || section.hasOnlyAir()) continue;
                    diag.nonAirSections++;
                    if (!section.getStates().maybeHas(ObjectResolver::isTripwireLayer)) continue;
                    diag.paletteHit++;
                    final BlockPos sectionOrigin = SectionPos.of(sx, sy, sz).origin();
                    if (!lr.isSectionCompiledAndVisible(sectionOrigin)) {
                        diag.notVisible++;
                        diag.sampleSection(sectionOrigin);
                        continue;
                    }
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                final BlockState st = section.getBlockState(lx, ly, lz);
                                if (!isTripwireLayer(st)) continue;
                                diag.cells++;
                                tripwireProbe(level, snap, unproj, sectionOrigin.offset(lx, ly, lz),
                                        cam, terrain, timestamp, diag);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * v2.37 八次修订（§15.10 诊断）：绊线候选的"精筛 + 记账"包装 —— 逻辑与直接调
     * {@link #fineFilterTranslucent} 完全一致，只是把终止原因码计入 {@link TripwireDiag}，
     * 并给未采到的候选留样本（位置 + 形状 y 区间 + 原因）。仅绊线通道用：
     * 本通道此前<b>完全静默</b>，"一个都没采到"既可能是闸门挡下、也可能是根本没进循环，
     * 不记账就无法区分。记账本身不改判定（半透明网格通道仍走原路径）。
     */
    private static void tripwireProbe(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final BlockPos pos,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp,
            final TripwireDiag diag
    ) {
        final int code = fineFilterTranslucent(level, snap, unproj, pos, cam, terrain, timestamp);
        if (code == FR_VISIBLE || code == FR_CAM_INSIDE) {
            diag.added++;
            return;
        }
        switch (code) {
            case FR_SHAPE_EMPTY: diag.shapeEmpty++; break;
            case FR_PROJ_NONE: diag.projNone++; break;
            case FR_NO_SHAPE_HIT: diag.noShapeHit++; break;
            case FR_OCCLUDED: diag.occluded++; break;
            default: diag.rayNull++; break;
        }
        // 样本（至多 3 条）：未采到的格打印其形状 y 区间——形状与渲染模型不一致时（薄片浮在高处等）
        // 正是从这两个数上直接看出来。
        // v2.37 九次修订（§15.11.5）：区间改为**世界坐标**并显式标注（旧样本打的是局部坐标：沙漠里
        // y=65 的绊线显示 shapeY=[0.0625,0.15625]，正是根因的现场证据，§15.11.1）；流体同样入样。
        if (diag.samples.size() < 3) {
            final BlockState st = level.getBlockState(pos);
            final List<AABB> sh = worldShapeAabbs(level, pos, st);
            if (sh.isEmpty()) {
                diag.sample(pos + " shape=(empty) -> " + reasonName(code));
            } else {
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (AABB b : sh) { lo = Math.min(lo, b.minY); hi = Math.max(hi, b.maxY); }
                diag.sample(pos + " shapeY(world)=[" + lo + "," + hi + "] -> " + reasonName(code));
            }
        }
    }

    /**
     * v2.37 八次修订（§15.10 诊断）：绊线候选通道的逐闸门计数（一次性，随采集帧构造）。
     *
     * <p>闸门次序（{@link #queryTripwireCandidates}）：chunks（chunk 已载入）→ nonAirSections
     * （节非全空）→ paletteHit（节 palette 含 TRIPWIRE 层）→ notVisible（编译可见性闸门）→
     * cells（实际枚举到的绊线格）→ 精筛终止原因码（added / shapeEmpty / projNone / noShapeHit /
     * occluded / rayNull）。哪一级骤降，就是哪一级挡下的。
     */
    private static final class TripwireDiag {
        int chunks, nonAirSections, paletteHit, notVisible, cells, added;
        int shapeEmpty, projNone, noShapeHit, occluded, rayNull;
        SectionPos camSection = SectionPos.of(0, 0, 0);
        int renderDist;
        final List<String> samples = new ArrayList<>(3);
        final List<BlockPos> hiddenSections = new ArrayList<>(2);

        void sample(final String s) { samples.add(s); }
        void sampleSection(final BlockPos origin) {
            if (hiddenSections.size() < 2) hiddenSections.add(origin);
        }

        /** 单行摘要（§15.10：一行看出哪级闸门挡下的）。 */
        String summary() {
            final StringBuilder sb = new StringBuilder(256);
            sb.append("camSection=").append(camSection.x()).append(',').append(camSection.y())
                    .append(',').append(camSection.z()).append(", renderDist=").append(renderDist);
            sb.append(", chunks=").append(chunks).append(", nonAirSections=").append(nonAirSections);
            sb.append(", paletteHit=").append(paletteHit).append(", notVisible=").append(notVisible);
            if (!hiddenSections.isEmpty()) sb.append(" sections=").append(hiddenSections);
            sb.append(", cells=").append(cells).append(", added=").append(added);
            sb.append(", shapeEmpty=").append(shapeEmpty).append(", projNone=").append(projNone);
            sb.append(", noShapeHit=").append(noShapeHit).append(", occluded=").append(occluded);
            sb.append(", rayNull=").append(rayNull);
            if (!samples.isEmpty()) sb.append(", samples=").append(samples);
            return sb.toString();
        }
    }

    // v2.37 八次修订（§15.10 诊断）：精筛的终止原因码。**纯诊断**，不改变任何判定：
    // 返回值仅供绊线候选通道记账用（半透明网格通道忽略之），用于把"这条通道一个都没采到"
    // 拆成"哪一道闸门挡下的"——本函数此前完全静默，是绊线采集失败无法定位的直接原因。
    private static final int FR_VISIBLE = 0;      // 找到可见像素 → 已 addTerrain
    private static final int FR_CAM_INSIDE = 1;   // 相机在形状内 → 已 addTerrain
    private static final int FR_SHAPE_EMPTY = 2;  // getShape 为空（无形状可判）
    private static final int FR_PROJ_NONE = 3;    // 8 角全不可投影（相机背后 / 全出屏）
    private static final int FR_NO_SHAPE_HIT = 4; // 无任一像素的射线穿过形状（bbox 超集内全落空）
    private static final int FR_OCCLUDED = 5;     // 射线穿过形状，但所有像素深度比较不过（被挡）
    private static final int FR_RAY_NULL = 6;     // 逐像素射线 / 反投影失败（投影矩阵不可用）

    private static String reasonName(final int code) {
        switch (code) {
            case FR_VISIBLE: return "visible";
            case FR_CAM_INSIDE: return "camInside";
            case FR_SHAPE_EMPTY: return "shapeEmpty";
            case FR_PROJ_NONE: return "projNone";
            case FR_NO_SHAPE_HIT: return "noShapeHit";
            case FR_OCCLUDED: return "occluded";
            case FR_RAY_NULL: return "rayNull";
            default: return "?" + code;
        }
    }

    private static int fineFilterTranslucent(
            final ClientLevel level,
            final DepthCapture.DepthSnapshot snap,
            final Unprojector unproj,
            final BlockPos pos,
            final Vec3 cam,
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final long timestamp
    ) {
        final BlockState state = level.getBlockState(pos);
        // v2.10：候选盒 = 实际渲染形状（玻璃板/红石线为薄片；流体按 fluid.getHeight 缩放 Y_max）；
        // v2.37 九次修订（§15.11.5）：改为**世界坐标**副本——由 worldShapeAabbs 统一提供（局部盒会
        // 让本通道的深度判据恒不成立，绊线 + 半透明族全部静默，§15.11.2/§15.11.4）。
        final List<AABB> shapes = worldShapeAabbs(level, pos, state);
        if (shapes.isEmpty()) return FR_SHAPE_EMPTY;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (AABB s : shapes) {
            minX = Math.min(minX, s.minX); minY = Math.min(minY, s.minY); minZ = Math.min(minZ, s.minZ);
            maxX = Math.max(maxX, s.maxX); maxY = Math.max(maxY, s.maxY); maxZ = Math.max(maxZ, s.maxZ);
        }
        // ⓪ 相机在方块形状内（游泳/站玻璃里）→ 可见（v2.8 早退）
        if (closedContains(minX, minY, minZ, maxX, maxY, maxZ, cam.x, cam.y, cam.z)) {
            addTerrain(terrain, pos, state, timestamp);
            return FR_CAM_INSIDE;
        }

        // ① 投影 8 角 → 屏幕 bbox（超集；任一角在相机背后 → 无法构建有效范围 → 保守跳过）
        final int width = snap.width();
        final int height = snap.height();
        float minPx = Float.MAX_VALUE, maxPx = -Float.MAX_VALUE;
        float minPy = Float.MAX_VALUE, maxPy = -Float.MAX_VALUE;
        final double[] cornersX = {minX, minX, minX, minX, maxX, maxX, maxX, maxX};
        final double[] cornersY = {minY, minY, maxY, maxY, minY, minY, maxY, maxY};
        final double[] cornersZ = {minZ, maxZ, minZ, maxZ, minZ, maxZ, minZ, maxZ};
        int projected = 0;
        for (int i = 0; i < 8; i++) {
            final float[] pp = unproj.projectToScreen(new Vec3(cornersX[i], cornersY[i], cornersZ[i]), width, height);
            if (pp == null) continue; // 相机背后 / 出屏的角不入 bbox；部分角在相机背后时不再整盒跳过
            projected++;
            minPx = Math.min(minPx, pp[0]); maxPx = Math.max(maxPx, pp[0]);
            minPy = Math.min(minPy, pp[1]); maxPy = Math.max(maxPy, pp[1]);
        }
        // 全角不可投影 → 盒在相机背后 / 完全出屏 → 不可见。bbox 只是像素迭代范围，
        // 真伪由逐像素射线-AABB + 深度比较决定，故 bbox 缩小只降召回、不引入假阳性。
        if (projected == 0) return FR_PROJ_NONE;
        // ② 循环前显式裁剪到屏幕范围（v2.10）：越界像素 depthAt 钳到 1.0 会静默读成天空 → 假阳性
        final int x0 = Math.max(0, (int) Math.floor(minPx));
        final int x1 = Math.min(width - 1, (int) Math.ceil(maxPx));
        final int y0 = Math.max(0, (int) Math.floor(minPy));
        final int y1 = Math.min(height - 1, (int) Math.ceil(maxPy));

        // v2.37 八次修订（§15.10 诊断）：区分"一条射线都没穿过形状"与"穿过了但全被挡"——
        // 前者指向形状/模型不一致（几何问题），后者指向深度判据（正常遮挡）。仅用于终止原因码。
        boolean hitAny = false;
        boolean projFailed = false;
        for (int py = y0; py <= y1; py++) {
            for (int px = x0; px <= x1; px++) {
                final Vec3 dir = unproj.pixelRay(px, py);
                if (dir == null) { projFailed = true; continue; }
                final double tEntry = minSlabEntry(cam, dir, shapes);
                if (tEntry == Double.MAX_VALUE) continue; // 射线不穿形状（bbox 超集 → continue 防误判）
                hitAny = true;
                final float d = snap.depthAt(px, py);
                final double zOpaque;
                if (d >= unproj.dFar()) {
                    zOpaque = Double.POSITIVE_INFINITY; // 贴天空 → 可见
                } else {
                    final Vec3 w = unproj.unprojectPixel(px, py, d);
                    if (w == null) { projFailed = true; continue; }
                    zOpaque = w.distanceTo(cam);
                }
                if (zOpaque >= tEntry - DELTA) {
                    addTerrain(terrain, pos, state, timestamp);
                    return FR_VISIBLE; // 首个可见像素 break（提前退出）
                }
            }
        }
        return hitAny ? FR_OCCLUDED : (projFailed ? FR_RAY_NULL : FR_NO_SHAPE_HIT);
    }

    private static double minSlabEntry(final Vec3 origin, final Vec3 dir, final List<AABB> shapes) {
        double best = Double.MAX_VALUE;
        for (AABB s : shapes) {
            final Double t = slabEntry(origin, dir, s);
            if (t != null) best = Math.min(best, t);
        }
        return best;
    }

    private static void addTerrain(
            final Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            final BlockPos pos, final BlockState state, final long timestamp
    ) {
        if (!terrain.containsKey(pos)) {
            terrain.put(pos, new VisionCollector.TerrainBlockSnapshot(
                    pos, VisionCollector.blockId(state), VisionCollector.stateProps(state), timestamp));
        }
    }

    // ==================== 几何助手 ====================

    /** 闭区间包含（v2.9：AABB.contains 半开区间会把落在盒 max 面的画/画框像素拒之门外）。 */
    private static boolean closedContains(final AABB b, final double x, final double y, final double z) {
        return x >= b.minX && x <= b.maxX && y >= b.minY && y <= b.maxY && z >= b.minZ && z <= b.maxZ;
    }

    private static boolean closedContains(
            final double minX, final double minY, final double minZ,
            final double maxX, final double maxY, final double maxZ,
            final double x, final double y, final double z
    ) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** 实体盒外扩 pad 后的闭区间包含（免 AABB 分配）。 */
    private static boolean containsInflated(
            final DepthCapture.EntitySnapshotData e, final double x, final double y, final double z, final double pad
    ) {
        final AABB b = e.box();
        return x >= b.minX - pad && x <= b.maxX + pad
                && y >= b.minY - pad && y <= b.maxY + pad
                && z >= b.minZ - pad && z <= b.maxZ + pad;
    }

    /**
     * 手写 slab 求交（v2.9：vanilla {@code AABB.clip} 不支持——起点在盒内返回 empty、终点在盒面
     * s==1.0 亦拒）。返回<b>带符号</b>近交点距离 t_entry：起点在盒内为负；射线不穿盒返回 null。
     * v2.11：零/负外扩盒（marker 盔甲架 0×0）退化 → 直接返回 null，防除零/NaN。
     */
    private static Double slabEntry(final Vec3 origin, final Vec3 dir, final AABB box) {
        if (box.getXsize() <= 1e-9 || box.getYsize() <= 1e-9 || box.getZsize() <= 1e-9) return null;
        final double ox = origin.x, oy = origin.y, oz = origin.z;
        double tmin = Double.NEGATIVE_INFINITY;
        double tmax = Double.POSITIVE_INFINITY;

        if (Math.abs(dir.x) < 1e-12) {
            if (ox < box.minX || ox > box.maxX) return null;
        } else {
            double t1 = (box.minX - ox) / dir.x;
            double t2 = (box.maxX - ox) / dir.x;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dir.y) < 1e-12) {
            if (oy < box.minY || oy > box.maxY) return null;
        } else {
            double t1 = (box.minY - oy) / dir.y;
            double t2 = (box.maxY - oy) / dir.y;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        if (Math.abs(dir.z) < 1e-12) {
            if (oz < box.minZ || oz > box.maxZ) return null;
        } else {
            double t1 = (box.minZ - oz) / dir.z;
            double t2 = (box.maxZ - oz) / dir.z;
            if (t1 > t2) { final double t = t1; t1 = t2; t2 = t; }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) return null;
        }
        return tmin;
    }

    // ==================== 结果 ====================

    /** 一次 resolve 的结果：可见对象 + v2.23 deletions + v2.32 维 + store 统计。 */
    public record ResolveResult(
            Map<BlockPos, VisionCollector.TerrainBlockSnapshot> terrain,
            Map<BlockPos, VisionCollector.BlockEntitySnapshot> blockEntities,
            List<VisionCollector.EntityLightSnapshot> entities,
            /**
             * v2.23：被证明消失的记忆格（随 terrain.nbt 顶层 {@code deletions} 键落盘，供记忆侧减量）。
             *
             * <p>v2.37 七次修订（§15）：<b>只含几何段</b>（main + translucent + shaped）——信号缺失族
             * 校正通道的裁决走 {@code sync} 的 {@code signalLossDeletions} 并列键，<b>刻意不并入本字段</b>
             * （两条通道在记忆侧各有独立守卫，合并会同时放宽两道保险，§15.2）。本字段供 API 组装
             * JSON / 统计用；{@code VisionApi} 不消费它。
             */
            List<BlockPos> deletions,
            /** v2.32：本帧所属维 id（agent 当前维；随 snapshot 响应顶层 + 各 store currentDimension 落盘）。 */
            String dimension,
            Map<String, Object> terrainStats,
            Map<String, Integer> blockEntityStats,
            Map<String, Object> entityStats,
            /** v2.31：生物群系采样统计 { "cells": union 总数, "added": 本帧新增 }（storeStats.biomes）。 */
            Map<String, Integer> biomeStats
    ) {
        public int visibleBlockCount() {
            return terrain.size();
        }

        public int blockEntityCount() {
            return blockEntities.size();
        }

        public int entityCount() {
            return entities.size();
        }
    }
}
