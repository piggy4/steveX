package name.modid.vision;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * 展示实体<b>薄内容摘要</b>（v2.35，决策点 2「渠道 B」，见 docs/展示实体内容记忆设计方案.md §6.4）。
 *
 * <p>作用仅限<b>采集端 snapshot JSON</b>（{@code entities[].content}）给 agent 提供直观可读的
 * 精简内容；记忆世界复原用的是同帧整份 NBT payload（完整可装载，§3），与本摘要无关。
 *
 * <p>为何用「同帧 payload tag + 同 codec 解码」而非实体 getter：Display 各子类的内容 getter
 * （{@code getItem()/getText()} 等）是 <b>private</b> 的，拿不到；反过来 payload 是
 * {@code saveWithoutId} 用 vanilla codec 写出的、本身即可装载的标准格式。因此这里用与保存完全
 * 对称的 codec 在渲染线程（registryAccess 正确）解码 payload 里的片段，得到内容后只取精简字段。
 * 解码整体包在 try/catch 里，任一段失败 → 该类型 content 为 null（内容可选，失败不阻塞主链路）。
 */
public final class DecorativeSummary {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 纯文本摘要最大长度（字符），超出截断加省略号。 */
    private static final int TEXT_MAX = 200;

    /** ItemStack 编码里的 data components 键（形如 {@code {id, count?, components?}}）。 */
    private static final String KEY_COMPONENTS = "components";
    /** 真附魔 component id（{@code ItemEnchantments} = 无界 map「附魔 id → 等级」）。 */
    private static final String COMPONENT_ENCHANTMENTS = "minecraft:enchantments";
    /** {@code DataComponentPatch} 的"移除"键前缀（{@code !minecraft:enchantments} = 该组件被显式移除）。 */
    private static final String COMPONENT_REMOVED_PREFIX = "!";
    /** 摘要里的附魔布尔键名（v2.35 补遗 §6.5）。 */
    private static final String KEY_ENCHANTED = "enchanted";

    private DecorativeSummary() {
    }

    /**
     * 从同帧 payload 构建薄摘要。
     *
     * @param entity     当前实体（仅 painting 用其 public {@code getVariant()} 读画名）
     * @param typeId     实体注册名（{@code minecraft:item_display} 等）
     * @param payload    整份 NBT payload（{@code {id, ...saveWithoutId}}，见 §5），可为 null
     * @param registries 渲染线程 registryAccess（解码 block_state / text / profile 需要）
     * @return 薄摘要 CompoundTag；不支持的类型 / 解码失败 / 无内容 → null
     */
    public static CompoundTag build(
            final Entity entity,
            final String typeId,
            final CompoundTag payload,
            final HolderLookup.Provider registries
    ) {
        if (payload == null) return null;
        try {
            final CompoundTag content = switch (typeId) {
                case "minecraft:item_frame", "minecraft:glow_item_frame" -> itemCarrier(payload, false);
                case "minecraft:item_display" -> itemCarrier(payload, true);
                case "minecraft:armor_stand", "minecraft:mannequin" -> armorStand(payload, typeId, registries);
                case "minecraft:block_display" -> blockDisplay(payload, registries);
                case "minecraft:text_display" -> textDisplay(payload, registries);
                case "minecraft:painting" -> painting(entity);
                default -> null;
            };
            return (content == null || content.isEmpty()) ? null : content;
        } catch (RuntimeException e) {
            LOGGER.debug("[Vision] Failed to build decorative summary for {}: {}", typeId, e.getMessage());
            return null;
        }
    }

    /** item_frame / glow_item_frame / item_display：item 栈（id + count；item_display 另含 mode 名）。 */
    private static CompoundTag itemCarrier(final CompoundTag payload, final boolean withMode) {
        final CompoundTag out = new CompoundTag();
        final CompoundTag item = readItem(payload.getCompoundOrEmpty("Item"));
        if (item != null) out.put("item", item);
        if (withMode) {
            final String mode = payload.getStringOr("item_display", "");
            if (!mode.isBlank()) out.putString("mode", mode);
        }
        return out.isEmpty() ? null : out;
    }

    /** armor_stand / mannequin：非空装备位 equipment{slot:{id,count,enchanted?}} + 整架 enchanted；mannequin 另附 profile 名。 */
    private static CompoundTag armorStand(
            final CompoundTag payload,
            final String typeId,
            final HolderLookup.Provider registries
    ) {
        final CompoundTag out = new CompoundTag();
        final CompoundTag equipment = payload.getCompoundOrEmpty("equipment");
        if (!equipment.isEmpty()) {
            final CompoundTag eq = new CompoundTag();
            boolean anyEnchanted = false;
            for (String slot : equipment.keySet()) {
                CompoundTag item = readItem(equipment.getCompoundOrEmpty(slot));
                if (item != null) {
                    eq.put(slot, item);
                    anyEnchanted |= item.contains(KEY_ENCHANTED);
                }
            }
            if (!eq.isEmpty()) out.put("equipment", eq);
            // 整架级：任一非空槽命中即 true（供 agent 一眼筛，无需遍历 equipment）
            if (anyEnchanted) out.putBoolean(KEY_ENCHANTED, true);
        }
        if ("minecraft:mannequin".equals(typeId)) {
            final String name = decodeProfileName(payload.get("profile"), registries);
            if (name != null && !name.isEmpty()) out.putString("name", name);
        }
        return out.isEmpty() ? null : out;
    }

    /** block_display：解码 block_state → 方块注册名。 */
    private static CompoundTag blockDisplay(final CompoundTag payload, final HolderLookup.Provider registries) {
        final Tag blockStateTag = payload.get("block_state");
        if (!(blockStateTag instanceof CompoundTag compound)) return null;
        final BlockState state = BlockState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), compound)
                .resultOrPartial(err -> LOGGER.debug("[Vision] block_state decode error: {}", err))
                .orElse(null);
        if (state == null) return null;
        final CompoundTag out = new CompoundTag();
        out.putString("blockId", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        return out;
    }

    /** text_display：解码 text → 纯文本（截断到 {@link #TEXT_MAX}）。 */
    private static CompoundTag textDisplay(final CompoundTag payload, final HolderLookup.Provider registries) {
        final Tag textTag = payload.get("text");
        if (textTag == null) return null;
        final Component component = ComponentSerialization.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), textTag)
                .resultOrPartial(err -> LOGGER.debug("[Vision] text decode error: {}", err))
                .orElse(null);
        if (component == null) return null;
        String text = component.getString();
        if (text.length() > TEXT_MAX) text = text.substring(0, TEXT_MAX) + "…";
        if (text.isEmpty()) return null;
        final CompoundTag out = new CompoundTag();
        out.putString("text", text);
        return out;
    }

    /** painting：用实体 public getVariant() 读画作注册名。 */
    private static CompoundTag painting(final Entity entity) {
        if (!(entity instanceof Painting painting)) return null;
        final ResourceKey<PaintingVariant> key = painting.getVariant().unwrapKey().orElse(null);
        if (key == null) return null;
        final CompoundTag out = new CompoundTag();
        out.putString("art", key.identifier().toString());
        return out;
    }

    /** 解码 mannequin profile → 玩家名（decode 失败 → null，不阻塞）。 */
    private static String decodeProfileName(final Tag profileTag, final HolderLookup.Provider registries) {
        if (!(profileTag instanceof CompoundTag compound)) return null;
        final ResolvableProfile profile = ResolvableProfile.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), compound)
                .resultOrPartial(err -> LOGGER.debug("[Vision] profile decode error: {}", err))
                .orElse(null);
        return profile == null ? null : profile.name().orElse(null);
    }

    /**
     * 从 ItemStack.CODEC 编码 tag（{@code {id, count?, components?}}，与 v2.34 掉落物 / 容器条目
     * 同一路径）只读精简字段。空栈 / 无 id → null。
     *
     * <p>v2.35 补遗（§6.5）：真附魔时另写 {@code enchanted: true}（仅 true 时写，与 {@code count} 同款省字节）。
     */
    private static CompoundTag readItem(final CompoundTag stackTag) {
        if (stackTag == null || stackTag.isEmpty()) return null;
        final String id = stackTag.getStringOr("id", "");
        if (id.isBlank()) return null;
        final CompoundTag out = new CompoundTag();
        out.putString("id", id);
        final int count = stackTag.getIntOr("count", 1);
        if (count > 1) out.putInt("count", count);
        if (isEnchanted(stackTag)) out.putBoolean(KEY_ENCHANTED, true);
        return out;
    }

    /**
     * 真附魔判定（v2.35 补遗，判据 A，见 docs/展示实体内容记忆设计方案.md §6.5）：
     * 等价于 {@code ItemStack.isEnchanted()} = {@code DataComponents.ENCHANTMENTS} 非空。
     *
     * <p><b>刻意不用</b> {@code ItemStack.hasFoil()}（"有光效"）：vanilla 给附魔金苹果 / 附魔之瓶 /
     * 成书 / 下界之星 / <b>空白附魔书</b> / 末地水晶等<b>无附魔</b>物品硬设了
     * {@code minecraft:enchantment_glint_override}（磁石指针另覆写 {@code isFoil}），用"光效"会把它们
     * 误报成附魔。附魔书的附魔在 {@code stored_enchantments}（另一个 component）⇒ 本判据下为 false，
     * 是既定边界（要看书内附魔走 Tier-2 {@code vision/entity?uuid=} 的整份 NBT）。
     *
     * <p>纯 tag 读：不解码 ItemStack、不碰 registry、不产生对象分配（沿用本类零解码风格）。
     * 任一环节不是预期的 compound 形状 → 一律判 false（内容字段失效不阻塞主链路）。
     *
     * <p>v2.40：包级开放 —— {@code vision/snapshot} 的掉落物 {@code entities[].item} 判据与展示实体
     * {@code content} 共用此实现（同一份判据不漂移），故不设 {@code private}。
     */
    static boolean isEnchanted(final CompoundTag stackTag) {
        final Tag componentsTag = stackTag.get(KEY_COMPONENTS);
        if (!(componentsTag instanceof CompoundTag components)) return false;
        if (components.contains(COMPONENT_REMOVED_PREFIX + COMPONENT_ENCHANTMENTS)) return false;
        final Tag enchantmentsTag = components.get(COMPONENT_ENCHANTMENTS);
        if (!(enchantmentsTag instanceof CompoundTag enchantments)) return false;
        // ItemEnchantments.CODEC = 无界 map「附魔 id → 等级(1..255)」；有条目即真附魔
        for (String enchantmentId : enchantments.keySet()) {
            if (enchantments.getIntOr(enchantmentId, 0) > 0) return true;
        }
        return false;
    }
}
