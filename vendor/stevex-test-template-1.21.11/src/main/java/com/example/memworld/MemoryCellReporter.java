package com.example.memworld;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.23（§7.11）反向通道上报器 —— 把记忆世界"当前存在"的<b>实心不透明块</b>与<b>冻结实体占用格</b>（v2.37 §7.14 起实体格<b>独立成段</b>）
 * 按距离球过滤后写成 {@code memory_cells.bin}，供采集侧 {@code DeletionJudge} 逐块深度判定
 * + {@code EntityPresenceCorrector} 逐实体格直读判定。
 *
 * <p>设计 §7.11 反向通道：
 * <ul>
 *   <li><b>集合来源</b>：实心不透明块取 {@link TerrainRestorer} 的已应用表（纯累积的全部已放置块，
 *       再按当前世界状态判定实心+不透明）；冻结实体占用格取 {@link EntityRestorer} 已放置实体的
 *       AABB 覆盖格；</li>
 *   <li><b>距离球过滤</b>：只上报 {@code |cell − agentPos| ≤ removalMaxRayDist}（默认 96）的格。
 *       <b>Over-inclusive</b>：只缩距离、不做精确视锥——采集侧用真实投影矩阵判定，越界格无像素命中、
 *       自然跳过，过滤只为了缩小清单；</li>
 *   <li><b>触发</b>：世界变化（restorer mutationVersion 变化）|| 姿态变化 &gt; ε（球随 agent 移动 →
 *       球内格集变化）|| 每 {@code memoryCellsWriteIntervalTicks}（默认 10）兜底；</li>
 *   <li><b>内容指纹门控</b>：内容未变不重写；</li>
 *   <li><b>原子写</b>：临时文件 + rename（半截写防护，同 §7.4），mtime 只在成功后推进。</li>
 * </ul>
 *
 * <p>v2.32（世界类型区分，见 docs/世界类型区分与镜像复原设计方案.md §4.6/§5.4）：cells 按<b>活动维</b>
 * 求取并带<b>维标签</b>上报——只算 {@code level.dimension()} 对应维的方块/实体（调用方 manager 只传
 * 活动维的 ServerLevel），采集侧只对 {@code dimension == 本快照维} 的 cells 做删除判定；镜像落后时
 * cells 仍标上一维 → 采集侧宁缺勿滥、绝不跨维误删。
 *
 * <p>v2.36（§7.12）：格式升 <b>version = 3</b>，cells 集合按<b>判据场分段</b>上报，与采集侧 §5.4
 * "该格现实写哪张场"路由同口径：
 * <ul>
 *   <li><b>opaque/main 段</b> = 实心不透明块 + <b>写 main 深度的流体（岩浆）</b>
 *       —— 一律在采集侧 <b>main 深度场</b>上判定（岩浆恒写 main、任意配置可判）；
 *       实体占用格自 v2.37（§7.14）起移出本段，见下；</li>
 *   <li><b>translucent 段</b> = <b>水</b>（写 translucent 目标）+ <b>满格透明方块</b>
 *       （玻璃块/染色玻璃/冰/遮光玻璃等：{@code isShapeFullBlock && !canOcclude}）—— 在采集侧按当前
 *       图形配置路由：Fabulous 且 translucent 目标在场 → translucent 深度场判定；Fancy/Fast（写 main）
 *       → 并入 main 场判据。段内不含岩浆（岩浆走 main 段，保证 Fabulous 第二路 PBO 软失败时仍可删）；
 *       不含玻璃板/栅栏/压力板等非满形状透明块（须 §7.13 几何过滤，v2.36 不纳入、欠删）。</li>
 * </ul>
 *
 * <p>v2.37 七次修订（§15）：格式升 <b>version = 5</b>，末尾追加<b>信号缺失段</b>——绊线 / 绊线钩
 * （该族在 Fabulous 下画进 weather 目标、不写任何已读深度场，故不喂深度判据）。采集侧对这段不做
 * 深度判定，而是直读真实世界状态判"是否已消失"（§15.2 段②）。<b>本段是采集侧唯一的族来源</b>：
 * 采集侧刻意不持族名单，"哪些格可删"与"哪些格被上报"因此永远不会错配。
 *
 * <p>v2.37（§7.14 实体减量欠删修正，方案 B）：格式升 <b>version = 6</b>，末尾追加<b>实体段</b>
 * —— 冻结实体占用格<b>从 main 段整段移出</b>，与方块格彻底分家：
 * <ul>
 *   <li><b>为什么要分家（缺陷根因）</b>：实体删除的旧判据要求冻结实体 AABB 覆盖的<b>全部</b>格都被
 *       深度判据证明为空，而 {@code DeletionJudge} 的首句 {@code currentTerrain.contains(pos) → continue}
 *       使<b>可见方块所在格永不入 deletions</b> ⇒ 与非满方块（营火 / 耕地 / 雪层 / 台阶…）共格的
 *       冻结实体<b>永久残留</b>（落点在整方块顶则落入空气格、可证明为空、正常删——这就是现场
 *       "有概率失效"的全部含义）。</li>
 *   <li><b>为什么换判据而非修补</b>：{@code deletions} 对实体是<b>错的代理</b>——实体渲染几何远小于
 *       其 AABB，格内空余区的射线会投票"越过"，它既是"该删不删"的来源、也是"误删活体"的隐蔽通道。
 *       本版把实体格整段撤出深度判据：采集侧对实体段<b>不做深度判定</b>，改为直读真实世界状态
 *       （同 §15 信号缺失族那条路线）。</li>
 *   <li><b>逐步实体追踪门限（承重设计）</b>：服务端只把 {@code clientTrackingRange} 区块内的实体推给
 *       玩家——<b>范围外实体活着也不在客户端实体表里</b>，采集侧"查不到"会与"真的没了"同形。
 *       故本侧按 {@code min(removalMaxRayDist, (clientTrackingRange − 1) × 16)} 先筛实体，只上报
 *       门限内实体的占用格。该知识只有本侧能算（本侧持 {@code EntityType}，采集侧刻意不持族/范围
 *       名单，避免两份名单漂移——与信号缺失段"族由本段定义"同一设计）。−1 区块余量同 G2 闸门思想。</li>
 * </ul>
 *
 * <p>文件格式（小端，与采集侧 {@code MemoryCellsReader} 对应；version = 6）：
 * <pre>{@code
 *   [0..3]   magic "SCEL"
 *   [4]      version = 5
 *   [5..8]   int 维 id 字节长度 L（UTF-8）
 *   [9..9+L) UTF-8 dimensionId（活动维）
 *   [..]     int removalPixelThreshold   （采集侧删除判定阈值，随通道下发，单一来源）
 *   [..]     double removalMaxRayDist    （信息性：距离球过滤半径）
 *   [..]     int opaqueCount
 *   [..]     opaqueCount × long（BlockPos.asLong；实心不透明 + 岩浆；v2.37 §7.14 起<b>不再含实体占用格</b>）
 *   [..]     byte removalTranslucentEnabled（v2.36 translucent 场开关，采集侧据此路由）
 *   [..]     int translucentCount
 *   [..]     translucentCount × long（BlockPos.asLong；水 + 满格透明）
 *   [..]     long spriteEpoch             （v2.37 几何段；本文件引用的 sprite 表版本）
 *   [..]     int shapedCount
 *   [..]     shapedCount × { long pos.asLong; ushort blockIdLen; UTF-8 blockId;
 *                            byte quadCount; quadCount × 96B（12 顶点 + 8 UV + 3 法线 float，1 sprite int） }
 *   [..]     int signalLossCount          （v2.37 七改；镜像中当前在场的信号缺失族格数，空段恒写 0）
 *   [..]     signalLossCount × { long BlockPos.asLong; ushort blockIdLen; UTF-8 blockId }
 *   [..]     int entityCellCount          （v2.37 §7.14 实体段；镜像中冻结实体占用格，空段恒写 0）
 *   [..]     entityCellCount × long（BlockPos.asLong；<b>本段是最后一段</b>）
 * }</pre>
 * <p>version ≤ 2 旧文件：无 translucent 段 → 采集侧只按 opaque/main 段删，行为等同 v2.23（无迁移负担）。
 * <p>version = 3/4：分别缺几何段 / 信号缺失段 → 采集侧对应段取空集（欠删，方向安全）。
 * <p>version = 5：无实体段 → 采集侧该通道恒无候选 ⇒ <b>实体恒欠删</b>（方向安全，行为可辨识）。
 * 版本号严格递进、采集侧按版本分支，故旧文件永不被误读成新布局。
 *
 * <p>文件路径 = 源 terrain.nbt 所在目录的 {@code memory_cells.bin}（采集侧读同一路径）。
 * 记忆侧离线不写 → 采集侧无删除证据 → 只增不删（优雅降级）。
 */
public final class MemoryCellReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");
    private static final byte[] MAGIC = {'S', 'C', 'E', 'L'};
    /** v2.32：格式版本升到 2（头部在 version 之后追加 UTF-8 维 id 段；version=1 旧文件无维标签）。
     *  v2.36（§7.12）：版本升到 3（opaque/main longs 之后追加 translucent 开关 byte + translucent 段）。
     *  v2.37（§7.13）：版本升到 4（translucent 段之后追加 long spriteEpoch + int shapedCount + 几何段）。
     *  v2.37 七次修订（§15）：版本升到 5（几何段之后追加 int signalLossCount + 信号缺失段）。
     *  v2.37（§7.14 实体减量欠删修正）：版本升到 6（信号缺失段之后追加 int entityCellCount + 实体段）。
     *  <p><b>为什么必须升版而不能"并入 v4"</b>：采集侧 {@code MemoryCellsReader.parse} 的末尾是
     *  {@code if (sp != bytes.length) return null}（严格不留尾巴，防格式错位被静默当成功）。
     *  在 v4 尾后追加段落而不升版 ⇒ 旧版采集侧读到多余字节即整份作废（cells 全丢、恒不删）；
     *  反之升到 5 ⇒ 旧版采集侧因版本不符而拒绝读取（{@code ver >= 5} 不认识），同样是"宁可不删"，
     *  但**行为可辨识**（有版本号可查）。故升版是唯一正确的做法，见 {@code MemoryCellsReader} 中
     *  "若将来再追加段落，须同时升版并按版本分支，勿放宽此检查"的既有约定。 */
    private static final int VERSION = 6;

    /** 一条几何段条目（v2.37，设计 §4.3.2）：格 + 方块注册 id + quad 面清单。
     *  {@code fp} = 内容指纹（顶点/UV/法线/sprite 下标 + blockId 的哈希），供指纹门控比较
     *  ——{@code Quad} 内含 {@code float[]}，record 的自动 equals 不会深比较，故不直接比数组。 */
    private record ShapedEntry(String blockId, ModelGeometryCache.Quad[] quads, int fp) {}

    /** 一次上报的五段集合（main/opaque 段、translucent 段、v2.37 几何段、v2.37七改 信号缺失段、
     *  v2.37 §7.14 实体段，见类 javadoc）。
     *  <p>信号缺失段的值取 blockId（而非只存格）：采集侧**刻意不持**族名单（族由本侧上报什么定义），
     *  该字符串用于诊断日志与将来在本侧加族时的可追溯性；采集侧的判据与方块种类无关。
     *  <p>实体段（{@code entity}）只存格、不存实体 id / 类型：采集侧的判据是"这格现实中还有没有实体"
     *  （与是哪个实体无关），存 id 反而会把"同格站着别的实体"错判成"我那个实体还在"。
     *  ——两个方向都合规（欠删），但存格更简单且与既有各段同形。 */
    private record Segments(Set<Long> main, Set<Long> translucent, Map<Long, ShapedEntry> shaped,
                           Map<Long, String> signalLoss, Set<Long> entity) {}

    private final TerrainRestorer terrain;
    private final EntityRestorer entities;

    private final MemoryConfig config = MemoryConfig.get();
    private int ticks;
    private int lastMutationVersion = -1;
    private Segments lastSegments = emptySegments();

    public MemoryCellReporter(final TerrainRestorer terrain, final EntityRestorer entities) {
        this.terrain = terrain;
        this.entities = entities;
    }

    private static Segments emptySegments() {
        return new Segments(Set.of(), Set.of(), Map.of(), Map.of(), Set.of());
    }

    /** 服务器（世界）启动 / 切换时调用，清空指纹与版本。 */
    public void onServerStart() {
        ticks = 0;
        lastMutationVersion = -1;
        lastSegments = emptySegments();
        LOGGER.info("[MemoryWorld] Cell reporter ready");
    }

    /** 每服务器 tick 调用：按需重算 + 原子写 cells 文件（只对传入的活动维 level）。 */
    public void tick(final ServerLevel level) {
        if (!config.removalEnabled) return; // 减量关闭 → 不写 cells → 纯累积
        if (level.players().isEmpty()) return; // v2.32：活动维尚无玩家（跨维传送过渡 tick）→ 无法定位
        // agent（球心），本轮跳过且不推进指纹——绝不把“无 cells”误上报成“记忆为空”（采集侧会宁缺勿滥）。

        final String dimension = level.dimension().identifier().toString();
        final int version = terrain.mutationVersion() + entities.mutationVersion();
        ticks++;
        // 触发：世界变化（mutationVersion 变化）|| 姿态变化（球内格集变化，随每次重算内容指纹体现）
        //        || 每 memoryCellsWriteIntervalTicks 兜底
        if (ticks % Math.max(1, config.memoryCellsWriteIntervalTicks) != 0 && version == lastMutationVersion) {
            return;
        }
        lastMutationVersion = version;

        final Segments segments = computeCells(level, dimension);
        // 内容指纹门控：五段集合都未变才不重写（含 translucent 开关随写入内容一起指纹化）
        // v2.37 七次修订（§15.7 第 8 条）：**信号缺失段必须入指纹**。漏掉它 → 镜像里绊线变了
        // （被拆掉 / 新拉了一条）而其余三段未变 → 本侧不重写文件 → 采集侧读到的候选恒为旧集 →
        // 整条通道**静默空转**（方向安全，但功能全无、且没有任何错误迹象，是最难查的一类失效）。
        if (segments.main().equals(lastSegments.main())
                && segments.translucent().equals(lastSegments.translucent())
                && sameShaped(segments.shaped(), lastSegments.shaped())
                && segments.signalLoss().equals(lastSegments.signalLoss())
                // v2.37（§7.14）：**实体段必须入指纹**（同 §15.7 第 8 条的坑）：改动落点全是实体时
                // （镜像里掉落物被拾取 / 生物被清掉）其余四段未变 → 漏掉本段则文件不重写 → 采集侧
                // 读到的实体候选恒为旧集 → 直读通道**静默空转**（方向安全、功能全无、无错误迹象）。
                && segments.entity().equals(lastSegments.entity())) {
            return;
        }

        final Path file = config.resolveMemoryCellsFile();
        if (file == null) return;

        // v2.37（设计 §4.3.1）**写入次序硬约束**：先写 sprite 文件（新 epoch + 全量表），再写 cells。
        // 反序会出现"cells 引用新 epoch 的下标、磁盘上却还是旧 epoch 的表"的窗口，采集侧会 fail-closed
        // 整段跳过（欠删，方向安全但白丢一轮）；正序则新表先落地，cells 引用的下标必然有效。
        // SpriteAlphaTable.flushIfDirty 只在表内容变化（首次用到新 sprite / 资源重载）时真正重写
        // ——暖机后本文件 mtime 不变，这正是"增量式"的落点（§10 第 15 条验收）。
        final ModelGeometryCache geometry = ModelGeometryCache.get();
        final Path spriteFile = config.resolveMemorySpritesFile();
        if (spriteFile != null) {
            geometry.sprites().flushIfDirty(spriteFile);
        }

        if (writeAtomic(file, dimension, segments, config.removalPixelThreshold,
                config.removalMaxRayDist, config.removalTranslucentEnabled, geometry.sprites().epoch())) {
            lastSegments = segments;
            LOGGER.info("[MemoryWorld] Wrote {} main + {} translucent + {} shaped + {} signalLoss + {} entity cells "
                            + "[{}] to {} (threshold={}, maxDist={}, translucentEnabled={}, spriteEpoch={})",
                    segments.main().size(), segments.translucent().size(), segments.shaped().size(),
                    segments.signalLoss().size(), segments.entity().size(), dimension, file,
                    config.removalPixelThreshold,
                    config.removalMaxRayDist, config.removalTranslucentEnabled, geometry.sprites().epoch());
        }
    }

    /** 几何段指纹比较：{@code Quad} 内含数组，须按 {@code fp} 逐格比对（不深比数组）。 */
    private static boolean sameShaped(final Map<Long, ShapedEntry> a, final Map<Long, ShapedEntry> b) {
        if (a.size() != b.size()) return false;
        for (Map.Entry<Long, ShapedEntry> e : a.entrySet()) {
            final ShapedEntry other = b.get(e.getKey());
            if (other == null || other.fp() != e.getValue().fp()) return false;
        }
        return true;
    }

    /**
     * 客户端 tick：驱动 {@link ModelGeometryCache} 烘焙排队中的方块几何（设计 §4.2 第 2 步——
     * 模型必须在客户端线程取，服务器 tick 只读缓存）。
     *
     * <p>由 {@code MemoryWorldManager.onClientTick} 调用。烘焙期同时完成 sprite 的 intern
     * （读 alpha 掩码也在客户端线程），故本方法返回后 {@code SpriteAlphaTable} 可能变脏，
     * 由下一个服务器 tick 的 {@link #tick} 负责按序落盘。
     */
    public static void tickClient() {
        ModelGeometryCache.get().tickClient();
    }

    // ==================== 集合重算 ====================

    /** 当前记忆世界的 agent 位置（眼睛坐标）；玩家未就绪返回 null（无法过滤 → 跳过本轮上报）。 */
    private static Vec3 agentPos(final ServerLevel level) {
        final List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return null;
        final ServerPlayer p = players.get(0);
        return new Vec3(p.getX(), p.getEyeY(), p.getZ());
    }

    /**
     * 重算待上报三段格集（§7.11 / v2.32 / v2.36 / v2.37）：按"该格现实写哪张深度场"分类（与采集侧
     * 路由同口径，见类 javadoc）。Over-inclusive：只缩距离，不做视锥。
     * <ul>
     *   <li><b>几何段（v2.37 新增）</b>：非满形状 + {@code RenderShape.MODEL} + 无 BE + 模型几何非空
     *       —— 按「quad 面清单 + sprite alpha 掩码」精确求交判（设计 §2.1/§4.1）；</li>
     *   <li><b>main 段</b>：实心不透明块 + 非水流体（岩浆，恒写 main）；<b>v2.37 §7.14 起实体占用格
     *       不再并入本段</b>（旧并入是 §7.14 欠删缺陷的来源之一：实体格进了深度判据，而实体格含可见
     *       方块时永远证明不出"空"，见类 javadoc）；</li>
     *   <li><b>translucent 段</b>：水 + 满格透明方块（玻璃块/冰等，Fabulous 写 translucent 目标）；</li>
     *   <li><b>信号缺失段（v2.37 七次修订新增，§15；v2.38 批 2 与 §17 补遗扩充）</b>：
     *       {@link BlockStateUtil#isSignalLossBlock} 命中的方块——v2.37 只有绊线 / 绊线钩（Fabulous 下
     *       画进 weather 目标、不写任何已读深度场）；v2.38（§14.4）扩至 <b>148 个 BE 载体方块</b>
     *       （箱子族 / 告示牌 / 床 / 旗帜 / 头颅 / 装饰陶罐 / 钟 / 讲台 / 架子 / 铜傀儡像…），它们的几何
     *       在 BE renderer 里、模型文件无 {@code elements} ⇒ 深度/几何判据对本族不可用；同日 §17 补遗
     *       再扩 <b>6 个乙1 方块</b>（{@code brewing_stand} / {@code hopper} / {@code comparator} /
     *       {@code daylight_detector} / {@code sculk_sensor} / {@code calibrated_sculk_sensor}），
     *       它们的几何<b>可</b>判，但三处谓词合围（第 ④ 步"有 BE 即排除" + 非满格 + 不在表）⇒ 无段可收。
     *       三族的共同点：走本段 = <b>不喂深度判据</b>，采集侧改为直读真实世界状态判其是否消失（§15.2 段②）。
     *       本段是"这族块在镜像里还在场"的唯一权威来源——采集侧不持族名单，族由本段上报什么定义。</li>
     * </ul>
     *
     * <p><b>判定次序是承重的（设计 §4.4，"先形状后流体"）</b>：必须先问几何谓词——命中者直接入几何段、
     * <b>无视 waterlogged</b>；只有<b>非候选格</b>才走现有流体/满格透明分支。<b>严禁"先流体后形状"</b>：
     * 否则 waterlogged 栅栏会先被 translucent 水段吞走，到不了几何谓词，复现 v2.36 的"活本体被整格
     * 置空"误删缺陷（§1.3）。两个段互斥由本次序保证。
     *
     * <p>信号缺失段的插入点不承重（三段定义域互不相交，§15.5）：绊线走
     * {@code isShapedDeletableContent} 的第 ⑤ 步必然返回 false、也非实心不透明/流体/满格透明，
     * 故放在形状分支之后与放在最后完全等价。放在形状分支之后只为就近可读。
     */
    private Segments computeCells(final ServerLevel level, final String dimension) {
        final Vec3 agent = agentPos(level);
        if (agent == null) return emptySegments();
        final double r2 = config.removalMaxRayDist * config.removalMaxRayDist;
        final Set<Long> main = new HashSet<>();
        final Set<Long> translucent = new HashSet<>();
        final Map<Long, ShapedEntry> shaped = new HashMap<>();
        final Map<Long, String> signalLoss = new HashMap<>();
        final Set<Long> entity = new HashSet<>();
        final ModelGeometryCache geometry = ModelGeometryCache.get();

        // ① 方块：先距离过滤（便宜），再读一次世界状态判定分段。只取该维已应用方块。
        for (BlockPos pos : terrain.appliedBlocks(dimension)) {
            final double dx = pos.getX() + 0.5 - agent.x;
            final double dy = pos.getY() + 0.5 - agent.y;
            final double dz = pos.getZ() + 0.5 - agent.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            final BlockState state = level.getBlockState(pos);
            // ★ 次序：先形状（§4.4）——命中者直接入几何段，不再按流体分流，保证 waterlogged 非满块
            //   只进几何段、不进 translucent 水段（两段互斥）。§4.1 第 5 步"几何非空"在此兜底：
            //   未烘焙完（null）或几何为空（quad 数 = 0 / 全部法线退化 / alpha 表不可得）→ 不进文件。
            if (BlockStateUtil.isShapedDeletableContent(level, pos, state)) {
                final ModelGeometryCache.Quad[] quads = geometry.get(state, pos);
                if (quads != null && quads.length > 0) {
                    final String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    shaped.put(pos.asLong(),
                            new ShapedEntry(blockId, quads, fingerprint(blockId, quads)));
                }
                continue;
            }
            // ★ v2.37 七次修订（§15）：信号缺失族（绊线 / 绊线钩）→ 独立段。
            //   这一族在 Fabulous 下画进 weather 目标、不写任何已读深度场，**活体无信号** ⇒ 深度判据
            //   下"活着"与"消失了"不可区分 ⇒ 若像其余块那样入 main/translucent 段，采集侧会恒判它
            //   "消失"（一格活体都不放过）。故自 v2.23 起整族排除；本版改为**换判据**而非继续排除：
            //   采集侧对这一段不做深度判定，而是直接读真实世界状态（§15.2 段②）。
            //   与几何段/流体段的次序无关（三段定义域互不相交，§15.5）：{@code isShapedDeletableContent}
            //   的第 ⑤ 步就是 {@code !isSignalLossBlock}，绊线走完形状谓词必然返回 false 落到这里；
            //   而它既非实心不透明、也非流体、也非满格透明（故原先自然落进"其余"被丢弃）。
            if (BlockStateUtil.isSignalLossBlock(state.getBlock())) {
                signalLoss.put(pos.asLong(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                continue;
            }
            if (BlockStateUtil.isSolidOpaque(level, pos, state)) {
                main.add(pos.asLong());            // 实心不透明 → main
            } else if (BlockStateUtil.isNonWaterFluid(state)) {
                main.add(pos.asLong());            // 岩浆等非水流体 → main（恒写 main，任意配置可判）
            } else if (BlockStateUtil.isWaterFluid(state)) {
                translucent.add(pos.asLong());     // 水 → translucent
            } else if (BlockStateUtil.isFullTransparentCell(level, pos, state)) {
                translucent.add(pos.asLong());     // 满格透明（玻璃块/冰）→ translucent
            }
            // 其余（空气 / RenderShape.INVISIBLE）不纳入任何段（欠删无害）。
            // v2.38 批 2（§14.4）后本行的"BE 非满块"不再属于此列：那 148 个方块已由
            // {@link BlockStateUtil#isSignalLossBlock} 在上面的独立段收走；同日 §17 补遗后乙1 的 6 个
            // （有几何但被第 ④ 步挡出几何段）也一并收走 ⇒ 本行确实只剩空气与 INVISIBLE 族（零足迹）。
            // （信号缺失族原在此列，v2.37 七次修订起改入上面的独立段，见 §15）
        }

        // ② 冻结实体占用格（AABB 覆盖的所有格）→ v2.37 §7.14 **独立实体段**（不再进 main 段）。
        //    两道过滤，次序承重：先**整实体**粒度的追踪门限（见 entityGate——超出者客户端根本没有该实体，
        //    采集侧会与"真的没了"同形），再逐格距离球（与方块段同口径，只为缩小清单）。
        for (Entity e : entities.entities(dimension)) {
            if (e.isRemoved()) continue;
            final AABB box = e.getBoundingBox();
            final double gate = entityGate(e.getType());
            if (gate <= 0 || nearestDist(box, agent) > gate) continue;
            final int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX);
            final int minY = Mth.floor(box.minY), maxY = Mth.floor(box.maxY);
            final int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        final double dx = x + 0.5 - agent.x;
                        final double dy = y + 0.5 - agent.y;
                        final double dz = z + 0.5 - agent.z;
                        if (dx * dx + dy * dy + dz * dz > r2) continue;
                        entity.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        return new Segments(main, translucent, shaped, signalLoss, entity);
    }

    /**
     * 逐实体追踪门限（v2.37 §7.14，承重）：服务端按 {@code clientTrackingRange}（区块）决定把哪些实体
     * 推给哪个玩家——<b>范围外的实体活着也不在客户端实体表里</b>。采集侧要"直读该格还有没有实体"，
     * 就必须先保证"查得到"这件事成立，否则"查不到"会被判成"已消失"（误删活体，危险方向）。
     *
     * <p>故取 {@code min(removalMaxRayDist, max(0, (clientTrackingRange − 1) × 16))}：
     * <ul>
     *   <li><b>−1 区块余量</b>：边界按区块量化，且客户端卸载/服务端停推并非同一瞬间，内收 1 区块避免
     *       蹭着边界判（与 G2 渲染距离闸门 {@code (rd − 2) × 16} 同一思想，余量更大是因为这里还叠了
     *       服务端推送延迟）；</li>
     *   <li><b>removalMaxRayDist 封顶</b>：玩家类型 32 区块、投掷物 16 区块之类会让清单无意义膨胀，
     *       且超大距离本就超出方块判据的可靠区（§7.14 残余边界 1，欠删，方向安全）。</li>
     * </ul>
     * 该知识只有本侧能算（本侧持 {@code EntityType}）：<b>采集侧刻意不持范围/族名单</b>，与信号缺失段
     * "族由上报什么定义"同一设计，避免两份名单漂移。
     */
    private double entityGate(final EntityType<?> type) {
        final int rangeChunks = Math.max(0, type.clientTrackingRange() - 1);
        return Math.min(config.removalMaxRayDist, rangeChunks * 16.0);
    }

    /** 点到 AABB 的最近距离（与采集侧 G2 闸门的 {@code nearestDist} 同口径：最近点是全实体各点距离的
     *  下界，"最近点都在门内"才是保守的那一侧——用中心点会把贴着脸的大实体误判出门外）。 */
    private static double nearestDist(final AABB box, final Vec3 p) {
        final double dx = Math.max(Math.max(box.minX - p.x, p.x - box.maxX), 0.0);
        final double dy = Math.max(Math.max(box.minY - p.y, p.y - box.maxY), 0.0);
        final double dz = Math.max(Math.max(box.minZ - p.z, p.z - box.maxZ), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 几何段内容指纹（blockId + 全部 quad 的顶点/UV/法线/sprite 下标）。 */
    private static int fingerprint(final String blockId, final ModelGeometryCache.Quad[] quads) {
        int h = blockId.hashCode();
        for (ModelGeometryCache.Quad q : quads) {
            h = h * 31 + java.util.Arrays.hashCode(q.vertices());
            h = h * 31 + java.util.Arrays.hashCode(q.uvs());
            h = h * 31 + Float.floatToIntBits(q.nx());
            h = h * 31 + Float.floatToIntBits(q.ny());
            h = h * 31 + Float.floatToIntBits(q.nz());
            h = h * 31 + q.spriteIndex();
        }
        return h;
    }

    // ==================== 原子写 ====================

    /** 原子写（临时文件 + rename，半截写防护同 §7.4）。失败返回 false（调用方不推进指纹）。
     *  <p>v2.37 布局（见类 javadoc）：version=4 起在 translucent 段后追加 long spriteEpoch 与几何段；
     *  version=5（七次修订）在几何段后再追加 int signalLossCount + 信号缺失段；
     *  version=6（§7.14）最后再追加 int entityCellCount + 实体段。
     *  各段用 {@link Set}/{@link Map} 无序语义，集合/指纹相等即内容相等。
     *  字节序与采集侧 {@code MemoryCellsReader} 一致：{@link ByteOrder#LITTLE_ENDIAN}。 */
    private static boolean writeAtomic(final Path target, final String dimension, final Segments segments,
                                       final int threshold, final double maxRayDist,
                                       final boolean translucentEnabled, final long spriteEpoch) {
        try {
            Files.createDirectories(target.getParent());
            final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            final byte[] dimBytes = dimension == null ? new byte[0] : dimension.getBytes(StandardCharsets.UTF_8);
            // 布局：magic(4) + ver(1) + dimLen(4) + dim + 16（threshold/maxRayDist）+ opaqueCount(4)
            //       + opaque×8 + translucentEnabled(1) + translucentCount(4) + translucent×8
            //       + spriteEpoch(8) + shapedCount(4) + shaped 几何段
            //       + signalLossCount(4) + signalLoss 信号缺失段
            //       + entityCellCount(4) + entity 实体段
            // 固定部分（不含 dim 与各变长段）= 4+1+4+4+8+4+1+4+8+4+4+4 = 50
            final int opaque = segments.main().size();
            final int translucent = segments.translucent().size();
            final Map<Long, ShapedEntry> shaped = segments.shaped();
            final Map<Long, String> signalLoss = segments.signalLoss();
            // 几何段字节数：每格 8(pos) + 2(blockIdLen) + blockId + 1(quadCount) + quadCount×96
            int shapedBytes = 0;
            for (Map.Entry<Long, ShapedEntry> e : shaped.entrySet()) {
                shapedBytes += 8 + 2 + e.getValue().blockId().getBytes(StandardCharsets.UTF_8).length + 1
                        + e.getValue().quads().length * 96;
            }
            // 信号缺失段字节数：每格 8(pos) + 2(blockIdLen) + blockId（同几何段的两段式，无 quad）
            int signalLossBytes = 0;
            for (String blockId : signalLoss.values()) {
                signalLossBytes += 8 + 2 + blockId.getBytes(StandardCharsets.UTF_8).length;
            }
            final ByteBuffer buf = ByteBuffer.allocate(
                    50 + dimBytes.length + (opaque + translucent + segments.entity().size()) * 8
                            + shapedBytes + signalLossBytes
            ).order(ByteOrder.LITTLE_ENDIAN);
            buf.put(MAGIC);                    // [0..3]
            buf.put((byte) VERSION);           // [4]
            buf.putInt(dimBytes.length);       // [5..8]
            buf.put(dimBytes);                 // [9..9+L)
            buf.putInt(threshold);             // threshold
            buf.putDouble(maxRayDist);         // maxRayDist
            buf.putInt(opaque);                // opaqueCount
            for (long c : segments.main()) {   // opaque × long
                buf.putLong(c);
            }
            buf.put((byte) (translucentEnabled ? 1 : 0)); // translucentEnabled
            buf.putInt(translucent);           // translucentCount
            for (long c : segments.translucent()) {       // translucent × long
                buf.putLong(c);
            }
            // v2.37 几何段（设计 §4.3.2）
            buf.putLong(spriteEpoch);          // 本文件引用的 sprite 表 epoch
            buf.putInt(shaped.size());         // shapedCount
            for (Map.Entry<Long, ShapedEntry> e : shaped.entrySet()) {
                final ShapedEntry entry = e.getValue();
                final byte[] idBytes = entry.blockId().getBytes(StandardCharsets.UTF_8);
                buf.putLong(e.getKey());       // BlockPos.asLong
                buf.putShort((short) idBytes.length);
                buf.put(idBytes);              // block 注册 id（采集侧据此路由判据场 + 定 alpha 阈值）
                // quadCount 是 byte：上限 255。超限只写前 255 条——几何变**小**方向（欠删，安全），
                // 且写入条数与计数严格一致（多写会让采集侧解析错位，那是危险方向）。
                final int quadCount = Math.min(255, entry.quads().length);
                buf.put((byte) quadCount);
                for (int qi = 0; qi < quadCount; qi++) {
                    final ModelGeometryCache.Quad q = entry.quads()[qi];
                    for (float v : q.vertices()) buf.putFloat(v); // 12 × float：p0..p3，格内局部 [0,1]
                    for (float v : q.uvs()) buf.putFloat(v);      // 8 × float：sprite 局部 [0,1]
                    buf.putFloat(q.nx());                         // 3 × float：单位法线（自算）
                    buf.putFloat(q.ny());
                    buf.putFloat(q.nz());
                    buf.putInt(q.spriteIndex());                  // int：本 epoch 的 sprite 表下标
                }
            }
            // v2.37 七次修订（§15.3）信号缺失段。归零的 signalLossCount（本世界常态）也必须写——
            // 它是"无该族在场"的权威断言，而非缺省。
            buf.putInt(signalLoss.size());     // signalLossCount
            for (Map.Entry<Long, String> e : signalLoss.entrySet()) {
                final byte[] idBytes = e.getValue().getBytes(StandardCharsets.UTF_8);
                buf.putLong(e.getKey());       // BlockPos.asLong
                buf.putShort((short) idBytes.length);
                buf.put(idBytes);              // block 注册 id（如 minecraft:tripwire；诊断 + 将来加族用）
            }
            // v2.37（§7.14）实体段：**本段是最后一段**（采集侧解析到此处即文件尾）。同样地，归零的
            // entityCellCount 是"镜像中无冻结实体占用格"的权威断言，而非缺省——不可省略。
            buf.putInt(segments.entity().size());  // entityCellCount
            for (long c : segments.entity()) {     // entity × long
                buf.putLong(c);
            }
            final byte[] bytes = new byte[buf.position()];
            buf.flip();
            buf.get(bytes);
            Files.write(tmp, bytes);
            // ATOMIC_MOVE 尽力而为；失败时回退 REPLACE_EXISTING（同目录 rename 通常原子）
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("[MemoryWorld] Failed to write cells file {}: {}", target, e.getMessage());
            return false;
        }
    }
}
