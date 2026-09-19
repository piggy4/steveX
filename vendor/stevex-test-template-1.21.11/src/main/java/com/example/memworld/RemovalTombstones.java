package com.example.memworld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * v2.38（设计 §7.1 <b>决策 J</b>）<b>会话级减量墓碑集</b> —— 记录"本会话被减量通道实删过的格"。
 *
 * <p><b>它解决什么</b>：容器通道（{@link ContainerMemoryApplier}）有一条"世界为空气 → 自足放置
 * 记录里的 block+state"的回放路径，与减量通道（{@link DeletionApplier}）方向相反。若用<b>当帧
 * deletions</b> 当 guard 去挡回放，guard 会<b>自取消</b>，两条通道按 poll 周期对打（§7.1 J 的三步推导）：
 * 第 N 帧镜像有箱、现实无箱 ⇒ 判删 ⇒ 删除 + 当帧 guard 挡住回放；第 N+1 帧镜像已是空气 ⇒ 记忆侧
 * <b>不再上报该 cell</b> ⇒ deletions 空 ⇒ guard 空 ⇒ 容器通道照常回放 ⇒ 镜像又有箱 ⇒ 回到第 N 帧。
 * 振荡周期 = poll 间隔。
 *
 * <p>故 guard 必须是<b>跨帧存活</b>的：墓碑一旦写入，只由下面两条之一清除——
 * <ol>
 *   <li><b>现实重新观测到该格是那个容器</b>（{@code terrain.blocks} 里该 pos 的 blockId 等于记录的
 *       blockId）：盒子在现实里回来了 ⇒ 记录重新是权威，恢复正常回放（§7.1 J 的清除条件，
 *       这也是"假阳性之后玩家又建回容器"的恢复路径）；</li>
 *   <li><b>世界（服务器）重启</b>：见 {@link DeletionApplier#onServerStart()} 的 {@code clearAll()}。
 *       <b>内存态、不落盘</b>——重启后墓碑为空 ⇒ 最坏只是回放一次记录（与 v2.37 行为相同），
 *       不会造成永久性差异。</li>
 * </ol>
 *
 * <p><b>只含减量通道实删过的格</b>：写入点唯一，在 {@link DeletionApplier#clearBlock}（两条删除
 * 通道共用的置空点）。故冷启动语义不受影响——墓碑为空时容器通道行为与 v2.37 逐位相同。
 *
 * <p><b>线程</b>：只由服务器线程访问（{@link MemoryWorldManager} 的 tick 序内），与两个 applier 同款，
 * 故无同步开销。
 */
public final class RemovalTombstones {

    private static final RemovalTombstones INSTANCE = new RemovalTombstones();

    /** 维度 → 已实删格（{@code BlockPos.asLong}）。 */
    private final Map<String, Set<Long>> byDim = new HashMap<>();

    private RemovalTombstones() {
    }

    public static RemovalTombstones get() {
        return INSTANCE;
    }

    /** 记录一格被减量通道实删（{@link DeletionApplier#clearBlock} 调用）。 */
    public void record(final String dimension, final BlockPos pos) {
        byDim.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos.asLong());
    }

    /** 该格是否在本会话被实删过（容器通道的自足回放据此跳过）。 */
    public boolean contains(final String dimension, final BlockPos pos) {
        final Set<Long> set = byDim.get(dimension);
        return set != null && set.contains(pos.asLong());
    }

    /** 清除一格（现实重新观测到该格是记录里的容器 ⇒ 记录重新是权威，§7.1 J 清除条件）。 */
    public void clear(final String dimension, final BlockPos pos) {
        final Set<Long> set = byDim.get(dimension);
        if (set != null) set.remove(pos.asLong());
    }

    /** 清空全部维（世界启动 / 切换；墓碑是会话态、不落盘）。 */
    public void clearAll() {
        byDim.clear();
    }

    /** 诊断：某维当前墓碑数。 */
    public int size(final String dimension) {
        final Set<Long> set = byDim.get(dimension);
        return set == null ? 0 : set.size();
    }
}
