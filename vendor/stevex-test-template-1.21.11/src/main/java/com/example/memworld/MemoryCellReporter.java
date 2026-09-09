package com.example.memworld;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * v2.23（§7.11）反向通道上报器 —— 把记忆世界"当前存在"的<b>实心不透明块 + 冻结实体占用格</b>
 * 按距离球过滤后写成 {@code memory_cells.bin}，供采集侧 {@code DeletionJudge} 逐块深度判定。
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
 *   <li><b>opaque/main 段</b> = 实心不透明块 + 冻结实体占用格 + <b>写 main 深度的流体（岩浆）</b>
 *       —— 一律在采集侧 <b>main 深度场</b>上判定（岩浆恒写 main、任意配置可判）；</li>
 *   <li><b>translucent 段</b> = <b>水</b>（写 translucent 目标）+ <b>满格透明方块</b>
 *       （玻璃块/染色玻璃/冰/遮光玻璃等：{@code isShapeFullBlock && !canOcclude}）—— 在采集侧按当前
 *       图形配置路由：Fabulous 且 translucent 目标在场 → translucent 深度场判定；Fancy/Fast（写 main）
 *       → 并入 main 场判据。段内不含岩浆（岩浆走 main 段，保证 Fabulous 第二路 PBO 软失败时仍可删）；
 *       不含玻璃板/栅栏/压力板等非满形状透明块（须 §7.13 几何过滤，v2.36 不纳入、欠删）。</li>
 * </ul>
 *
 * <p>文件格式（小端，与采集侧 {@code MemoryCellsReader} 对应；version = 3）：
 * <pre>{@code
 *   [0..3]   magic "SCEL"
 *   [4]      version = 3
 *   [5..8]   int 维 id 字节长度 L（UTF-8）
 *   [9..9+L) UTF-8 dimensionId（活动维）
 *   [..]     int removalPixelThreshold   （采集侧删除判定阈值，随通道下发，单一来源）
 *   [..]     double removalMaxRayDist    （信息性：距离球过滤半径）
 *   [..]     int opaqueCount
 *   [..]     opaqueCount × long（BlockPos.asLong；实心不透明 + 冻结实体占用格 + 岩浆）
 *   [..]     byte removalTranslucentEnabled（v2.36 translucent 场开关，采集侧据此路由）
 *   [..]     int translucentCount
 *   [..]     translucentCount × long（BlockPos.asLong；水 + 满格透明）
 * }</pre>
 * <p>version ≤ 2 旧文件：无 translucent 段 → 采集侧只按 opaque/main 段删，行为等同 v2.23（无迁移负担）。
 *
 * <p>文件路径 = 源 terrain.nbt 所在目录的 {@code memory_cells.bin}（采集侧读同一路径）。
 * 记忆侧离线不写 → 采集侧无删除证据 → 只增不删（优雅降级）。
 */
public final class MemoryCellReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("stevex-test/memory");
    private static final byte[] MAGIC = {'S', 'C', 'E', 'L'};
    /** v2.32：格式版本升到 2（头部在 version 之后追加 UTF-8 维 id 段；version=1 旧文件无维标签）。
     *  v2.36（§7.12）：版本升到 3（opaque/main longs 之后追加 translucent 开关 byte + translucent 段）。 */
    private static final int VERSION = 3;

    /** 一次上报的两段集合（main/opaque 段与 translucent 段，见类 javadoc §7.12 分段）。 */
    private record Segments(Set<Long> main, Set<Long> translucent) {}

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
        return new Segments(Set.of(), Set.of());
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
        // 内容指纹门控：两段集合都未变才不重写（含 translucent 开关随写入内容一起指纹化）
        if (segments.main().equals(lastSegments.main())
                && segments.translucent().equals(lastSegments.translucent())) {
            return;
        }

        final Path file = config.resolveMemoryCellsFile();
        if (file == null) return;
        if (writeAtomic(file, dimension, segments, config.removalPixelThreshold,
                config.removalMaxRayDist, config.removalTranslucentEnabled)) {
            lastSegments = segments;
            LOGGER.info("[MemoryWorld] Wrote {} main + {} translucent memory cells [{}] to {} (threshold={}, "
                    + "maxDist={}, translucentEnabled={})",
                    segments.main().size(), segments.translucent().size(), dimension, file,
                    config.removalPixelThreshold, config.removalMaxRayDist, config.removalTranslucentEnabled);
        }
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
     * 重算待上报两段格集（§7.11 / v2.32 / v2.36）：按"该格现实写哪张深度场"分类（与采集侧路由同口径，
     * 见类 javadoc）。Over-inclusive：只缩距离，不做视锥。
     * <ul>
     *   <li><b>main 段</b>：实心不透明块 + 冻结实体占用格 + 非水流体（岩浆，恒写 main）；</li>
     *   <li><b>translucent 段</b>：水 + 满格透明方块（玻璃块/冰等，Fabulous 写 translucent 目标）。</li>
     * </ul>
     */
    private Segments computeCells(final ServerLevel level, final String dimension) {
        final Vec3 agent = agentPos(level);
        if (agent == null) return emptySegments();
        final double r2 = config.removalMaxRayDist * config.removalMaxRayDist;
        final Set<Long> main = new HashSet<>();
        final Set<Long> translucent = new HashSet<>();

        // ① 方块：先距离过滤（便宜），再读一次世界状态判定分段。只取该维已应用方块。
        for (BlockPos pos : terrain.appliedBlocks(dimension)) {
            final double dx = pos.getX() + 0.5 - agent.x;
            final double dy = pos.getY() + 0.5 - agent.y;
            final double dz = pos.getZ() + 0.5 - agent.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            final BlockState state = level.getBlockState(pos);
            if (BlockStateUtil.isSolidOpaque(level, pos, state)) {
                main.add(pos.asLong());            // 实心不透明 → main
            } else if (BlockStateUtil.isNonWaterFluid(state)) {
                main.add(pos.asLong());            // 岩浆等非水流体 → main（恒写 main，任意配置可判）
            } else if (BlockStateUtil.isWaterFluid(state)) {
                translucent.add(pos.asLong());     // 水 → translucent
            } else if (BlockStateUtil.isFullTransparentCell(level, pos, state)) {
                translucent.add(pos.asLong());     // 满格透明（玻璃块/冰）→ translucent
            }
            // 非满形状透明（玻璃板/栅栏/压力板/薄物）与空气：不纳入任何段（欠删无害，§7.12 边界①）
        }

        // ② 冻结实体占用格（AABB 覆盖的所有格，距离过滤）→ 一律 main（实体写 main，见 §7.11）。只取该维已放置实体。
        for (Entity e : entities.entities(dimension)) {
            if (e.isRemoved()) continue;
            final AABB box = e.getBoundingBox();
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
                        main.add(BlockPos.asLong(x, y, z));
                    }
                }
            }
        }
        return new Segments(main, translucent);
    }

    // ==================== 原子写 ====================

    /** 原子写（临时文件 + rename，半截写防护同 §7.4）。失败返回 false（调用方不推进指纹）。
     *  <p>v2.36 布局（见类 javadoc）：version=3 起在 opaque 段后追加 translucentEnabled byte 与
     *  translucent 段；两段用 {@link Set} 无序语义，集合相等即内容相等（指纹门控只比集合）。
     *  字节序与采集侧 {@code MemoryCellsReader} 一致：{@link ByteOrder#LITTLE_ENDIAN}。 */
    private static boolean writeAtomic(final Path target, final String dimension, final Segments segments,
                                       final int threshold, final double maxRayDist,
                                       final boolean translucentEnabled) {
        try {
            Files.createDirectories(target.getParent());
            final Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            final byte[] dimBytes = dimension == null ? new byte[0] : dimension.getBytes(StandardCharsets.UTF_8);
            // 布局：magic(4) + ver(1) + dimLen(4) + dim + 16（threshold/maxRayDist）+ opaqueCount(4)
            //       + opaque×8 + translucentEnabled(1) + translucentCount(4) + translucent×8
            final int opaque = segments.main().size();
            final int translucent = segments.translucent().size();
            final ByteBuffer buf = ByteBuffer.allocate(
                    30 + dimBytes.length + (opaque + translucent) * 8
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
            Files.write(tmp, buf.array());
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
