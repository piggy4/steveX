package name.modid.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import name.modid.AgentWebSocketServer;
import name.modid.AgentWebSocketServer.WsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;

/** inventory API —— 物品栏含 22 字段组件数据 */
public class InventoryApi {

    public static void register(Map<String, WsHandler> handlers) {
        handlers.put("inventory", params -> {
            List<Map<String, Object>> items = inventory();
            if (items == null) throw new RuntimeException("player not connected");
            return Map.of("slots", items);
        });
    }

    private static List<Map<String, Object>> inventory() {
        return AgentWebSocketServer.runOnClient(2_000, "Inventory query", ref -> {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            List<Map<String, Object>> items = new ArrayList<>();
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (!stack.isEmpty()) {
                    int slotId = i == 40 ? -1 : i >= 36 ? 100 + i - 36 : i;
                    items.add(slotItem(slotId, stack));
                }
            }
            ref.value = items;
        });
    }

    static Map<String, Object> slotItem(int slot, net.minecraft.world.item.ItemStack stack) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("slot",     slot);
        item.put("id",       net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        item.put("count",    stack.getCount());
        item.put("maxStack", stack.getMaxStackSize());

        if (stack.isDamageableItem()) {
            item.put("durability",    stack.getMaxDamage() - stack.getDamageValue());
            item.put("maxDurability", stack.getMaxDamage());
        }
        if (stack.has(DataComponents.CUSTOM_NAME))
            item.put("name", stack.getHoverName().getString());

        var rarity = stack.get(DataComponents.RARITY);
        if (rarity != null) item.put("rarity", rarity.name().toLowerCase());

        var ench = stack.get(DataComponents.ENCHANTMENTS);
        if (ench == null || ench.isEmpty()) ench = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            for (var e : ench.entrySet())
                enchantments.put(e.getKey().getRegisteredName(), e.getIntValue());
            item.put("enchantments", enchantments);
        }

        var enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable != null) item.put("enchantable", enchantable.value());

        var repairCost = stack.get(DataComponents.REPAIR_COST);
        if (repairCost != null && repairCost > 0) item.put("repairCost", repairCost);

        if (stack.has(DataComponents.UNBREAKABLE)) item.put("unbreakable", true);

        var food = stack.get(DataComponents.FOOD);
        if (food != null) {
            Map<String, Object> fd = new LinkedHashMap<>();
            fd.put("nutrition", food.nutrition());
            fd.put("saturation", AgentWebSocketServer.f2(food.saturation()));
            if (food.canAlwaysEat()) fd.put("canAlwaysEat", true);
            item.put("food", fd);
        }

        var attrs = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attrs != null) {
            List<Map<String, Object>> mods = new ArrayList<>();
            for (var entry : attrs.modifiers()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("attribute", entry.attribute().getRegisteredName());
                m.put("amount", AgentWebSocketServer.f2(entry.modifier().amount()));
                m.put("operation", entry.modifier().operation().getSerializedName());
                m.put("slot", entry.slot().getSerializedName());
                mods.add(m);
            }
            if (!mods.isEmpty()) item.put("attributeModifiers", mods);
        }

        var tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            Map<String, Object> tl = new LinkedHashMap<>();
            tl.put("defaultMiningSpeed", AgentWebSocketServer.f1(tool.defaultMiningSpeed()));
            tl.put("damagePerBlock", tool.damagePerBlock());
            item.put("tool", tl);
        }

        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) item.put("equippable", equippable.slot().getName());

        var consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable != null) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("consumeSeconds", AgentWebSocketServer.f1(consumable.consumeSeconds()));
            cs.put("animation", consumable.animation().getSerializedName());
            item.put("consumable", cs);
        }

        var cooldown = stack.get(DataComponents.USE_COOLDOWN);
        if (cooldown != null) {
            Map<String, Object> cd = new LinkedHashMap<>();
            cd.put("seconds", AgentWebSocketServer.f1(cooldown.seconds()));
            if (cooldown.cooldownGroup().isPresent()) cd.put("group", cooldown.cooldownGroup().get().toString());
            item.put("cooldown", cd);
        }

        var remainder = stack.get(DataComponents.USE_REMAINDER);
        if (remainder != null)
            item.put("useRemainder", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(remainder.convertInto().getItem()).toString());

        if (stack.has(DataComponents.BLOCKS_ATTACKS)) item.put("blocksAttacks", true);
        if (stack.has(DataComponents.GLIDER)) item.put("glider", true);

        var repairable = stack.get(DataComponents.REPAIRABLE);
        if (repairable != null) {
            List<String> items = new ArrayList<>();
            for (var ri : repairable.items())
                items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ri.value()).toString());
            if (!items.isEmpty()) item.put("repairable", items);
        }

        var cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) {
            Map<String, Object> cmdData = new LinkedHashMap<>();
            if (!cmd.floats().isEmpty()) cmdData.put("floats", cmd.floats());
            if (!cmd.flags().isEmpty()) cmdData.put("flags", cmd.flags());
            if (!cmd.strings().isEmpty()) cmdData.put("strings", cmd.strings());
            if (!cmd.colors().isEmpty()) cmdData.put("colors", cmd.colors());
            if (!cmdData.isEmpty()) item.put("customModelData", cmdData);
        }

        // v2.46 bannerPatterns：旗帜图案层。此前 slotItem 不吐该组件，带图案的旗帜与素旗在
        // API 里完全同形（同 id / 同 count）——而织布机的全部方案正是"同一面旗 + 不同图案"，
        // 缺这段则 container/get 的 patterns[] 会退化成 32 个彼此无法区分的条目。
        // 图案是持有者可见的（物品模型与 tooltip 都显示），故与观察边界一致；
        // color 用 DyeColor 的序列化名（red / blue …），与本文件其余"注册名"口径一致。
        // 无图案层时**不写该键**（缺省即常态）。
        var bannerPatterns = stack.get(DataComponents.BANNER_PATTERNS);
        if (bannerPatterns != null && !bannerPatterns.layers().isEmpty()) {
            List<Map<String, Object>> bannerLayers = new ArrayList<>();
            for (var layer : bannerPatterns.layers()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("pattern", layer.pattern().getRegisteredName());
                l.put("color",   layer.color().getName());
                bannerLayers.add(l);
            }
            item.put("bannerPatterns", bannerLayers);
        }

        return item;
    }
}
