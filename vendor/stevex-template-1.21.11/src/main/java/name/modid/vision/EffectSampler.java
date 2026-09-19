package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * 药水效果采样信箱（v2.42，见 docs/实体属性观测面设计方案.md §12）。
 *
 * <p><b>解决什么问题</b>：药水效果<b>不在客户端同步链路上</b>（§3.2——vanilla 只同步
 * {@code DATA_EFFECT_PARTICLES}，即只有颜色），故观测端 {@code getActiveEffects()} 对旁观实体恒为空集。
 * 但单机 / 自己是局域网主机时，<b>服务端权威数据就在同一个 JVM 里</b>（集成服务器）——本类把那份数据
 * 搬到观测端的渲染线程手上。
 *
 * <p><b>为什么必须是"采样"而不是"查询"</b>：采集跑在渲染线程，集成服务器跑在自己的 tick 线程，而
 * {@code LivingEntity.getActiveEffects()} 返回的是<b>活视图</b>（{@code activeEffects.values()}，
 * 背后是同一个 HashMap）⇒ 跨线程直接迭代 = {@code ConcurrentModificationException} 或撕裂读。
 * 所以本类是<b>"每 tick 采样 → 不可变信箱 → 渲染线程只读"</b>：
 *
 * <pre>
 *   服务端 tick 线程                          │  渲染线程（采集帧）
 *   ─────────────────────────────────────────┼──────────────────────────
 *   {@link #sampleAll}                        │  {@link LivingSummary#collectEffects}
 *     └ 遍历全部 ServerLevel / 全部实体        │    └ {@link #instances(UUID)}
 *         └ 有效果的活体 → 深拷贝成样本        │         ← 只读不可变数据，零竞态
 *     → 整体替换 {@link #mailbox}（volatile）  │
 * </pre>
 *
 * <p><b>为什么样本要深拷贝</b>：{@code MobEffectInstance} 是<b>可变对象</b>——源实体的持续时间每 tick
 * 都在减少。持有活引用 = 读到撕裂数据；而 {@code new MobEffectInstance(copy)} 也<b>不能用</b>——它的
 * 拷贝构造只搬 {@code effect} 引用 + 5 个标量（{@code setDetailsFrom}，{@code MobEffectInstance.java:121}），
 * {@code hiddenEffect} 被整个漏掉、仍是活引用。故用本类的不可变 record。
 *
 * <p><b>刻意不带 {@code hiddenEffect}</b>：它在 vanilla 里是"同种效果被更强的顶替时，把旧的存起来，
 * 等新的到期后恢复"的内部记账（{@code MobEffectInstance.java:140-142} / :256-266），
 * <b>且 {@code private} 无 getter</b>，读不到。丢弃它在<b>本系统里无后果</b>：记忆侧实体一律<b>冻结不 tick</b>
 * （设计 §5.1）⇒ 持续时间不会倒计时 ⇒ 永远走不到"主效果到期、提升隐藏效果"那一步。
 * 记录于此，是为了让后来者知道这是<b>有据的取舍</b>而非遗漏。
 *
 * <p><b>单机专属</b>：连真实服务器时本类的信箱永远为空且 {@link #available()} 为 false，读取面据此
 * 报告 {@code effectsSource: "unavailable"}——"未知"与"没有效果"必须可区分（§12.4 拍板 1）。
 */
public final class EffectSampler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 顶层 {@code effectsSource} 取值：数据来自同 JVM 的集成服务器。 */
    public static final String SOURCE_INTEGRATED = "integrated_server";
    /** 顶层 {@code effectsSource} 取值：本 JVM 没有服务器 ⇒ {@code effects} 一律"未知"。 */
    public static final String SOURCE_UNAVAILABLE = "unavailable";

    /**
     * 一份不可变的药水效果样本 —— 只保留 {@code MobEffectInstance.CODEC} 需要的字段。
     *
     * <p>字段与 CODEC 的对应：{@code effect}(id) / {@code duration} / {@code amplifier} /
     * {@code ambient} / {@code visible} / {@code showIcon}。（{@code hidden_effect} 见类 javadoc：
     * 刻意不带。）
     *
     * <p>{@code Holder<MobEffect>} 本身不可变，可以安全跨线程共享；其余全为标量。
     */
    public record EffectSample(
            Holder<MobEffect> effect,
            int duration,
            int amplifier,
            boolean ambient,
            boolean visible,
            boolean showIcon
    ) {}

    /**
     * 信箱：实体 uuid → 该实体当前全部效果。<b>整体替换、内容只读</b>，故 volatile 写入即安全发布。
     *
     * <p>按 uuid 索引（全局唯一，无需维度键）。<b>只登记有效果的实体</b>——绝大多数实体没有效果，
     * 故常规世界里这份 map 极小甚至为空。
     */
    private static volatile Map<UUID, List<EffectSample>> mailbox = Map.of();

    /** 本 JVM 内是否有服务器发布过样本（= 集成服务器在跑）。见 {@link #sourceName()}。 */
    private static volatile boolean available;

    private EffectSampler() {
    }

    /**
     * 清空信箱并标记数据源不可用。由 {@code ServerLifecycleEvents.SERVER_STOPPED} 调用。
     *
     * <p><b>必须有</b>：否则"玩完单机 → 退出 → 连真实服务器"会继续读到上一个世界的陈旧数据，
     * 把"未知"伪装成"已知"。
     */
    public static void reset() {
        mailbox = Map.of();
        available = false;
    }

    /** 本 JVM 是否跑着（或跑过）服务器 —— 即药水效果是否可得。 */
    public static boolean available() {
        return available;
    }

    /** 读取面顶层 {@code effectsSource} 的取值，见 {@link #SOURCE_INTEGRATED}/{@link #SOURCE_UNAVAILABLE}。 */
    public static String sourceName() {
        return available ? SOURCE_INTEGRATED : SOURCE_UNAVAILABLE;
    }

    /**
     * 取该实体当前全部效果的<b>重建副本</b>。
     *
     * <p>返回的是新建的 {@code MobEffectInstance}（调用方只读或交给 CODEC 编码，不得留存跨帧引用）。
     * 实体不在信箱里 / 数据源不可用 → 空列表。
     */
    public static List<MobEffectInstance> instances(final UUID uuid) {
        final List<EffectSample> samples = mailbox.get(uuid);
        if (samples == null) return List.of();
        final List<MobEffectInstance> out = new ArrayList<>(samples.size());
        for (EffectSample s : samples) out.add(toInstance(s));
        return out;
    }

    // ==================== 服务端侧：采样 ====================

    /**
     * 服务端 tick 钩子（{@code ServerTickEvents.END_SERVER_TICK}）：采样全部维度的活体效果并发布。
     *
     * <p><b>只在服务端 tick 线程调用</b>——这是本类唯一的写入口，读侧（渲染线程）只读。
     */
    public static void sampleAll(final MinecraftServer server) {
        Map<UUID, List<EffectSample>> next = null;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof LivingEntity living)) continue;
                    // isEmpty() 在 HashMap.values() 视图上是 O(1)，没有任何效果时完全不触碰集合。
                    if (living.getActiveEffects().isEmpty()) continue;
                    final List<EffectSample> samples = new ArrayList<>();
                    for (MobEffectInstance instance : living.getActiveEffects()) {
                        samples.add(toSample(instance));
                    }
                    if (next == null) next = new HashMap<>();
                    next.put(living.getUUID(), List.copyOf(samples));
                }
            }
        } catch (RuntimeException e) {
            // 采样失败不应打断服务端 tick；保留旧信箱（最多陈旧一帧）。
            LOGGER.debug("[Vision] effect sampling failed: {}", e.getMessage());
            return;
        }
        // 短路：从来就没有效果、当前也没有、且不是在报告"服务器刚起来" ⇒ 不动 volatile。
        // （首个 tick 的 available=false 会使短路不成立，于该 tick 建立"服务器在跑"这个事实。）
        if (next == null && available && mailbox.isEmpty()) return;
        mailbox = next == null ? Map.of() : Map.copyOf(next);
        available = true;
    }

    private static EffectSample toSample(final MobEffectInstance instance) {
        return new EffectSample(
                instance.getEffect(),
                instance.getDuration(),
                instance.getAmplifier(),
                instance.isAmbient(),
                instance.isVisible(),
                instance.showIcon());
    }

    private static MobEffectInstance toInstance(final EffectSample sample) {
        return new MobEffectInstance(
                sample.effect(),
                sample.duration(),
                sample.amplifier(),
                sample.ambient(),
                sample.visible(),
                sample.showIcon());
    }
}
