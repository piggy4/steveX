package name.modid.vision;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * 活体（{@code LivingEntity}）属性摘要（v2.41，见 docs/实体属性观测面设计方案.md）。
 *
 * <p><b>一份来源，两个消费者，两种厚度</b>（§2）——本类同时提供这两个口径，采集帧在渲染线程一次调用：
 *
 * <ul>
 *   <li>{@link #buildSave} <b>复原口径（全量）</b> → 落盘 {@code entities.nbt} 的 {@code living} 键 →
 *       记忆侧 {@code EntityRestorer.applyLiving} 复原。装备落<b>整份 {@code ItemStack.CODEC} tag</b>、
 *       名字落 <b>Component tag</b>、效果落<b>完整 {@code MobEffectInstance} tag</b>：少一个组件
 *       就会复原出"没附魔的同款甲"＝镜像里的<b>假事实</b>（§4.1）。</li>
 *   <li>{@link #buildView} <b>可见口径（薄）</b> → 透传 → {@code vision/snapshot} 的实体级键 →
 *       agent 感知（§3）。这一面是<b>观察边界</b>：agent 只能"看到"。</li>
 * </ul>
 *
 * <p><b>落盘厚不构成越界</b>：已立规则「落盘保真 ≠ 读取面可读，边界只在读取面执行，store 永远不是
 * 读取面」。两个口径不是妥协，是两个不同的问题。
 *
 * <p>与 {@link DecorativeSummary} 对称：那个是<b>展示实体</b>的薄摘要，本类是<b>活体</b>的。区别在于
 * 本类手上有活实体，所以可见口径直读 getter（零解码）；复原口径才走 codec 编码。
 *
 * <p><b>每帧执行</b>：两个方法都由 {@code LevelRendererMixin} 在采集帧（{@code isCaptureRequested} 门控内）
 * 调用，不是只在 {@code vision/snapshot} 请求时。故装备只编码非空槽（绝大多数生物仅 1~2 槽），
 * 名字/效果空则整键不写（§4.2）。
 */
public final class LivingSummary {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ==================== 落盘（复原口径）键名 ====================

    /** 装备槽表：{@code {<slot.getName()>: <ItemStack.CODEC tag>}}，仅非空槽。 */
    private static final String KEY_EQUIPMENT = "equipment";
    /** 自定义名 Component tag（{@code ComponentSerialization.CODEC}）；<b>仅 {@code hasCustomName()} 时</b>。 */
    private static final String KEY_CUSTOM_NAME = "customName";
    /** 名字牌是否渲染（与 {@link #KEY_CUSTOM_NAME} 配对；仅自定义名存在时写）。 */
    private static final String KEY_CUSTOM_NAME_VISIBLE = "customNameVisible";
    /**
     * 激活效果列表（{@code MobEffectInstance.CODEC} 完整 tag，与 vanilla {@code active_effects} 同格式）。
     *
     * <p>落盘侧<b>不过滤</b> {@code isVisible()}——复原保真优先（§4.1）；过滤只发生在读取面。
     * <p>v2.42：数据源为 {@link EffectSampler}（集成服务器），连真实服务器时取不到，见该类 javadoc。
     */
    private static final String KEY_EFFECTS = "effects";
    /** Whether {@link #KEY_EFFECTS} is authoritative, including an authoritative empty list. */
    private static final String KEY_EFFECTS_KNOWN = "effectsKnown";
    /** 幼年（仅 true 时写）。 */
    private static final String KEY_BABY = "baby";
    /** 姿态（{@code Pose#getSerializedName()}；仅非 {@code standing} 时写——建实体默认即站立）。 */
    private static final String KEY_POSE = "pose";
    /** 是否正在渲染火焰（仅 true 时写）。 */
    private static final String KEY_ON_FIRE = "onFire";

    // ==================== 可见（薄）口径键名 ====================

    /** 物品注册名（薄装备条目，与 {@code content.equipment} 同款）。 */
    private static final String KEY_ID = "id";
    /** 堆叠数（仅 >1 时写）。 */
    private static final String KEY_COUNT = "count";
    /** 真附魔（判据 A，仅 true 时写）。 */
    private static final String KEY_ENCHANTED = "enchanted";
    /** 名字牌文本（薄口径，仅 {@code shouldShowName()} 时写）。 */
    private static final String KEY_NAME = "name";
    /** 最大生命值（薄口径，恒写；超观测例外，见 §8.4）。 */
    private static final String KEY_MAX_HEALTH = "maxHealth";

    private LivingSummary() {
    }

    // ==================== 数据源：药水效果（v2.42，集成服务器信箱） ====================

    /**
     * 采集实体身上的药水效果。**唯一的取数点**——v2.41 曾把这里置空占位（当时判定效果不在客户端
     * 同步链路上，§3.2），v2.42 不改签名、只换实现，两个口径（{@link #buildSave} / {@link #buildView}）
     * 同时生效，两处调用点一行未动。
     *
     * <p><b>数据来源</b>：{@link EffectSampler}——单机 / 自己是局域网主机时，服务端权威数据在同一个
     * JVM 里（集成服务器），由 {@code ServerTickEvents.END_SERVER_TICK} 每 tick 采样进不可变信箱。
     * <b>不能</b>在这里直接写 {@code living.getActiveEffects()}：本方法跑在渲染线程，而
     * {@code getActiveEffects()} 返回的是<b>活视图</b>，跨线程迭代会 CME / 撕裂读（§12.2）。
     *
     * <p><b>单机专属</b>：连真实服务器时信箱恒为空 ⇒ 本方法返回空列表，此时"空"意味着<b>未知</b>而非
     * "没有效果"，读取面据 {@link EffectSampler#sourceName()} 报告 {@code effectsSource} 以资区分
     * （§12.4 拍板 1）。
     *
     * @param living 目标活体
     * @return 该实体当前全部效果（重建副本）；数据源不可用 / 实体不在信箱 → 空列表
     */
    private static List<MobEffectInstance> collectEffects(final LivingEntity living) {
        return EffectSampler.instances(living.getUUID());
    }

    // ==================== 复原口径（全量，落盘） ====================

    /**
     * 构建<b>复原口径</b>的 {@code living} 复合键（全量，可完整复原生物外观与状态）。
     *
     * <p>必须在渲染线程调用（读实体状态 + {@code registryAccess} 均有竞态）。
     *
     * @param entity     目标实体；非 {@link LivingEntity} → null
     * @param registries 渲染线程 {@code registryAccess}（编码物品栈 / 名字 / 效果需要）
     * @return {@code {equipment?, customName?, customNameVisible?, effects?, baby?, pose?, onFire?}}；
     *         非活体 → null。活体<b>恒非 null</b>（可以是空 compound），见下方"为何空也返回非 null"
     */
    public static CompoundTag buildSave(final Entity entity, final HolderLookup.Provider registries) {
        if (!(entity instanceof LivingEntity living)) return null;
        try {
            final CompoundTag out = new CompoundTag();

            // 装备：只编码非空槽（§4.2 成本控制）。整份 ItemStack.CODEC tag —— 落薄摘要会复原出
            // "同款但无附魔"的甲，等于在镜像里写下一条假事实（§4.1）。
            final CompoundTag equipment = new CompoundTag();
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                final ItemStack stack = living.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                final CompoundTag tag = VisionCollector.encodeItemStack(stack, registries);
                if (tag != null) equipment.put(slot.getName(), tag);
            }
            if (!equipment.isEmpty()) out.put(KEY_EQUIPMENT, equipment);

            // 名字牌：落 <b>Component</b> 而非纯文本（颜色/格式也是可见的），且必须是 customName
            // 而非 getName()——对没自定义名的生物 getName() 返回类型描述（"Zombie"），落它会让
            // 复原体凭空长出名字牌。
            if (living.hasCustomName()) {
                final Tag nameTag = ComponentSerialization.CODEC
                        .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), living.getCustomName())
                        .resultOrPartial(err -> LOGGER.debug("[Vision] name encode error: {}", err))
                        .orElse(null);
                if (nameTag != null) {
                    out.put(KEY_CUSTOM_NAME, nameTag);
                    out.putBoolean(KEY_CUSTOM_NAME_VISIBLE, living.isCustomNameVisible());
                }
            }

            // 效果：完整 MobEffectInstance tag（id + 等级 + 时长 + 若干标志）——复原需要全部，
            // 尤其时长：冻结实体不 tick（§5.1），一次应用即永久保持。
            // 落盘侧**不过滤** isVisible()：隐藏粒子的效果也是事实，复原必须保真（§4.1）。
            // （过滤只发生在读取面，见 buildView。）
            if (EffectSampler.available()) {
                final ListTag effects = new ListTag();
                for (MobEffectInstance instance : collectEffects(living)) {
                    MobEffectInstance.CODEC
                            .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), instance)
                            .resultOrPartial(err -> LOGGER.debug("[Vision] effect encode error: {}", err))
                            .ifPresent(effects::add);
                }
                // The marker distinguishes an authoritative empty list from an unavailable data source.
                out.putBoolean(KEY_EFFECTS_KNOWN, true);
                if (!effects.isEmpty()) out.put(KEY_EFFECTS, effects);
            }

            // 幼年：不只外观——碰撞盒尺寸经 memory_cells.bin 反向通道参与减量判定（§4.1）。
            if (living.isBaby()) out.putBoolean(KEY_BABY, true);
            // 姿态：仅非 standing（构造出的实体默认即站立，省字节）。
            final Pose pose = living.getPose();
            if (pose != Pose.STANDING) out.putString(KEY_POSE, pose.getSerializedName());
            if (living.displayFireAnimation()) out.putBoolean(KEY_ON_FIRE, true);

            // 为何空也返回非 null（而非"空即省键"）：键的<b>存在与否</b>必须只编码"是不是活体"这一个事实。
            // 若空即省键，则"属性从有变无"（药水效果过期 / 装备被拿走 / 火灭了）与"本来就没有"在
            // 文件里长得一样 ⇒ 记忆侧无从区分，此前应用过的属性会永远留在镜像里成为假事实。
            // 子键仍遵循"缺省即常态"只写非缺省值；本键本身的存在性就是那个区分位。
            return out;
        } catch (RuntimeException e) {
            LOGGER.debug("[Vision] Failed to build living save for {}: {}", living.getUUID(), e.getMessage());
            return null;
        }
    }

    // ==================== 可见口径（薄，snapshot JSON） ====================

    /**
     * 构建<b>可见口径</b>的 {@code livingView} 复合键（薄，仅 agent 能看到的东西）。
     *
     * <p>全部为实体 getter 直读，<b>零解码、不碰 codec</b>。必须在渲染线程调用。
     *
     * <p>各键的判据就是"肉眼看不看得见"本身（§3）：装备 = 非空槽（判据 A 真附魔）、名字 =
     * {@code shouldShowName()}（名字牌的渲染谓词）、效果 = 种类且<b>过滤</b> {@code isVisible()}
     * （没有粒子的效果看不见，v2.42 拍板 2）、幼年 = {@code isBaby()}、姿态、
     * 着火 = {@code displayFireAnimation()}。
     *
     * @param entity 目标实体；非 {@link LivingEntity} → null
     * @return {@code {equipment?, name?, effects?, baby?, maxHealth, pose, onFire?}}；异常 → null
     */
    public static CompoundTag buildView(final Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        try {
            final CompoundTag out = new CompoundTag();

            final CompoundTag equipment = new CompoundTag();
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                final ItemStack stack = living.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                final CompoundTag item = new CompoundTag();
                item.putString(KEY_ID, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                if (stack.getCount() > 1) item.putInt(KEY_COUNT, stack.getCount());
                // 判据 A：ItemStack.isEnchanted() = ENCHANTMENTS 非空（是"有光效"的子集 ⇒ 只会少给）
                if (stack.isEnchanted()) item.putBoolean(KEY_ENCHANTED, true);
                equipment.put(slot.getName(), item);
            }
            if (!equipment.isEmpty()) out.put(KEY_EQUIPMENT, equipment);

            // 名字：判据 = shouldShowName()（Entity: isCustomNameVisible()；Player 恒 true）。
            // 这正是名字牌的渲染谓词本身，所以它字面上就是"肉眼看不看得见"。
            if (living.shouldShowName()) {
                out.putString(KEY_NAME, living.getName().getString());
            }

            // 效果：只给种类（id），不给持续时间/等级——粒子不显示它们。
            // **过滤 isVisible()**（v2.42 拍板 2）：hideParticles 的效果没有粒子，而本面的定义就是
            // "肉眼看得见"，过滤才自洽。落盘侧不过滤（见 buildSave）——一厚一薄，各按各的判据。
            // 注意"本键缺席"在数据源不可用（连真实服务器）时意味着**未知**而非"无效果"：
            // 那种情况由 snapshot 顶层的 effectsSource 区分（§12.4 拍板 1）。
            final ListTag effects = new ListTag();
            for (MobEffectInstance instance : collectEffects(living)) {
                if (!instance.isVisible()) continue;
                effects.add(StringTag.valueOf(
                        BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).toString()));
            }
            if (!effects.isEmpty()) out.put(KEY_EFFECTS, effects);

            if (living.isBaby()) out.putBoolean(KEY_BABY, true);
            out.putFloat(KEY_MAX_HEALTH, living.getMaxHealth()); // 超观测例外（§8.4），恒写
            out.putString(KEY_POSE, living.getPose().getSerializedName()); // 恒写（已拍板）
            if (living.displayFireAnimation()) out.putBoolean(KEY_ON_FIRE, true);

            return out;
        } catch (RuntimeException e) {
            LOGGER.debug("[Vision] Failed to build living view for {}: {}", living.getUUID(), e.getMessage());
            return null;
        }
    }
}
