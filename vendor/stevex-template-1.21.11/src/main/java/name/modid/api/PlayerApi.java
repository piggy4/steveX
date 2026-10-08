package name.modid.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import name.modid.AgentWebSocketServer;
import name.modid.AgentWebSocketServer.WsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;

/** player API —— 生命值/饱食度/药水效果/状态标志/属性表 */
public class PlayerApi {

    private record PlayerData(
        double health, double maxHealth,
        int food, float saturation,
        int armor, float armorToughness, int air,
        int xpLevel, float xpProgress,
        boolean onGround, boolean inWater,
        boolean sprinting, boolean sneaking,
        List<Map<String, Object>> effects,
        Map<String, Object> attributes
    ) {}

    public static void register(Map<String, WsHandler> handlers) {
        handlers.put("player", params -> {
            PlayerData d = take();
            if (d == null) throw new RuntimeException("player not connected");

            Map<String, Object> vitals = new LinkedHashMap<>();
            vitals.put("health",    AgentWebSocketServer.f1(d.health));
            vitals.put("maxHealth", AgentWebSocketServer.f1(d.maxHealth));
            vitals.put("food",      d.food);
            vitals.put("saturation", AgentWebSocketServer.f1(d.saturation));
            vitals.put("xpLevel",   d.xpLevel);
            vitals.put("xpProgress", AgentWebSocketServer.f2(d.xpProgress));
            vitals.put("armor",     d.armor);
            vitals.put("armorToughness", AgentWebSocketServer.f1(d.armorToughness));
            vitals.put("air",       d.air);

            Map<String, Boolean> flags = new LinkedHashMap<>();
            flags.put("onGround",   d.onGround);
            flags.put("inWater",    d.inWater);
            flags.put("sprinting",  d.sprinting);
            flags.put("sneaking",   d.sneaking);

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("vitals",     vitals);
            map.put("flags",      flags);
            map.put("effects",    d.effects);
            map.put("attributes", d.attributes);
            return map;
        });
    }

    private static PlayerData take() {
        return AgentWebSocketServer.runOnClient(2_000, "Player query", ref -> {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            var fd = p.getFoodData();

            List<Map<String, Object>> effects = new ArrayList<>();
            for (var e : p.getActiveEffects()) {
                Map<String, Object> ef = new LinkedHashMap<>();
                ef.put("id",      net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).toString());
                ef.put("level",   e.getAmplifier() + 1);
                ef.put("seconds", e.getDuration() / 20);
                ef.put("visible", e.isVisible());
                effects.add(ef);
            }

            ref.value = new PlayerData(
                p.getHealth(), p.getMaxHealth(),
                fd.getFoodLevel(), fd.getSaturationLevel(),
                p.getArmorValue(),
                (float)(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) != null
                    ? p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS) : 0.0),
                p.getAirSupply(),
                p.experienceLevel, p.experienceProgress,
                p.onGround(), p.isInWater(),
                p.isSprinting(), p.isCrouching(),
                effects,
                readAttributes(p)
            );
        });
    }

    /**
     * 玩家属性表（v2.50，{@code player} 的 {@code attributes} 键）。
     *
     * <p><b>为什么需要它</b>：物品侧的攻击力已经可读（{@code inventory}/{@code container/get} 的
     * {@code slots[].attributeModifiers}，铁剑＝{@code {minecraft:attack_damage, 5.0, add_value, mainhand}}），
     * 但那只是**修饰符**；界面上的"攻击伤害 6"要再加**玩家自身**的基础值
     * （{@code Player.createAttributes} 的 {@code ATTACK_DAMAGE 1.0}，Player.java:213）。此前这一半无处可读。
     *
     * <p><b>为什么不能用 {@code getSyncableAttributes()} 枚举</b>：服务端只下发
     * {@code Attribute.isClientSyncable()} 为真的属性（ServerEntity.java:282-288/345-350），而该字段在
     * {@code Attribute} 里**默认 false**、必须显式 {@code setSyncable(true)} ——{@code ATTACK_DAMAGE}
     * 恰好没有这一句（Attributes.java:14）⇒ <b>它不在同步集里</b>。用同步集枚举会正好漏掉本键的
     * **首要目标**。故这里遍历注册表 + {@code getAttribute()}：后者对该玩家不存在的属性返回 null
     * （{@code AttributeMap.getInstance:45-47} 的 {@code computeIfAbsent} + supplier 返回 null 时不插入），
     * 拿到的正是"该玩家 supplier 里的全部属性"，顺序＝注册顺序、稳定。
     *
     * <p><b>客户端值的来源</b>：base 来自本地 supplier；修饰符来自本地装备（装备包同步）与本地效果
     * （{@code LivingEntity.onEffectAdded:1061} 把效果自带的属性修饰符写进同一张表）⇒ 正常游戏下与服务端
     * 一致。但**非同步属性**（{@code attack_damage}/{@code attack_knockback}/{@code follow_range}/
     * {@code tempt_range}）在服务端用 {@code /attribute} 改过 base 时客户端收不到更新——这是已知限制，
     * 不是实现缺陷，别把它当"实时同步的权威值"。
     *
     * <p><b>每项给什么</b>：{@code base} ＝不含任何修饰符的基础值（＝"玩家自身"的贡献，合成公式的正确
     * 操作数）；{@code value} ＝{@code getValue()} 的最终值（已含装备与效果，且已过 {@code sanitizeValue}
     * 的 clamp）；{@code modifiers} 仅非空时给，每项 {@code {id, amount, operation}}——与
     * {@link InventoryApi#slotItem} 的 {@code attributeModifiers[]} 同名同义（那个多一个 {@code slot}
     * ＝生效装备槽），可直接对照。运算顺序见 {@code AttributeInstance.calculateValue:147-164}：
     * {@code (base + Σadd_value) × (1 + Σadd_multiplied_base) × Π(1 + add_multiplied_total)}。
     *
     * <p><b>易错点</b>：若某物品**已在主手**，{@code value} 里**已经含**它的修饰符，别再叠加一次；
     * {@code base + 物品修饰符} 只适用于"这物品还没上手，我想知道拿上会是多少"。
     *
     * <p><b>{@code modifiers[].id} 能读出什么</b>：{@code minecraft:base_attack_damage}/
     * {@code base_attack_speed} ＝主手物品（{@code Item.BASE_ATTACK_DAMAGE_ID:105-106}）；
     * {@code minecraft:effect.*} ＝药水效果；随机 UUID 形 ＝{@code /attribute} 或插件。
     * {@code AttributeModifier} 只有 {@code id/amount/operation} 三个字段、**不带来源对象**，只能给到 id 这一层。
     *
     * <p><b>⚠️ 属性 ≠ 伤害</b>：{@code Player.attack:967-1000} 里 {@code getAttributeValue(ATTACK_DAMAGE)}
     * 只是**第一行**，之后还有锋利等附魔（{@code getEnchantedDamage}）、攻击冷却、暴击 ×1.5、
     * {@code getAttackDamageBonus}。本键只保证给到那一行；"这一剑砍多少"要调用方自己叠。
     * **力量药水不在此列**——它是 {@code attack_damage} 的属性修饰符
     * （{@code MobEffects.STRENGTH:41-45}，+3.0/级 {@code add_value}），已经体现在 {@code value} 里。
     *
     * <p>属性表是**完整已知集合**（不适用"缺席＝未知"那套编码规则）：该玩家没有的属性直接不出现、
     * **不写 null**；{@code modifiers} 为空时整个键省略（空数组没有信息量，同 {@code slots[]} 只列非空格）。
     */
    private static Map<String, Object> readAttributes(final LivingEntity entity) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var holder : BuiltInRegistries.ATTRIBUTE.listElements().toList()) {
            var inst = entity.getAttribute(holder);
            if (inst == null) continue;   // 该玩家没有这个属性（不在其 supplier 里）
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("base",  AgentWebSocketServer.f2(inst.getBaseValue()));
            entry.put("value", AgentWebSocketServer.f2(inst.getValue()));
            var modifiers = inst.getModifiers();
            if (!modifiers.isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (var m : modifiers) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("id",        m.id().toString());
                    one.put("amount",    AgentWebSocketServer.f2(m.amount()));
                    one.put("operation", m.operation().getSerializedName());
                    list.add(one);
                }
                entry.put("modifiers", list);
            }
            out.put(holder.getRegisteredName(), entry);
        }
        return out;
    }
}
