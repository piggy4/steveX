package name.modid.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import name.modid.AgentWebSocketServer;
import name.modid.AgentWebSocketServer.WsHandler;
import name.modid.vision.ContainerMemoryTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.*;

/**
 * 容器 API —— 读取/操作当前打开的容器 GUI。
 * 包含方法：get / slot / button / close / text / drag / beacon
 */
public class ContainerApi {

    private static final Map<Integer, ClickType> CLICK_TYPES = Map.of(
        0, ClickType.PICKUP,
        1, ClickType.QUICK_MOVE,
        2, ClickType.SWAP,
        3, ClickType.CLONE,
        4, ClickType.THROW,
        5, ClickType.QUICK_CRAFT,
        6, ClickType.PICKUP_ALL
    );

    public static void register(Map<String, WsHandler> handlers) {
        handlers.put("container/get",    params -> getContainer());
        handlers.put("container/slot",   params -> slotClick(params));
        handlers.put("container/drag",   params -> drag(params));
        handlers.put("container/beacon", params -> setBeacon(params));
        handlers.put("container/button", params -> buttonClick(params));
        handlers.put("container/close",  params -> closeContainer());
        handlers.put("container/text",   params -> setText(params));
    }

    // ==================== get ====================

    private static Map<String, Object> getContainer() {
        Map<String, Object> container = AgentWebSocketServer.runOnClient(2_000, "Container query", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null) return;
            var menu = p.containerMenu;

            Map<String, Object> data = new LinkedHashMap<>();
            var screen = mc.screen;
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
                data.put("type", "none");
                data.put("slots", List.of());
            } else {
                String type;
                if (menu instanceof InventoryMenu) {
                    type = "inventory";
                } else {
                    type = net.minecraft.core.registries.BuiltInRegistries.MENU.getKey(menu.getType()).getPath();
                }
                data.put("type", type);
                data.put("containerId", menu.containerId);
                data.put("stateId", menu.getStateId());

                // slots
                List<Map<String, Object>> slots = new ArrayList<>();
                for (Slot slot : menu.slots) {
                    if (slot.hasItem()) {
                        slots.add(InventoryApi.slotItem(slot.index, slot.getItem()));
                    }
                }
                data.put("slots", slots);

                // carried item
                if (!menu.getCarried().isEmpty()) {
                    data.put("carriedItem", InventoryApi.slotItem(-2, menu.getCarried()));
                }

                // type-specific data
                switch (menu) {
                    case AbstractFurnaceMenu fm -> {
                        data.put("burnProgress", AgentWebSocketServer.f2(fm.getBurnProgress()));
                        data.put("litProgress",  AgentWebSocketServer.f2(fm.getLitProgress()));
                    }
                    case EnchantmentMenu em -> {
                        List<Integer> costs = new ArrayList<>();
                        List<Integer> clues = new ArrayList<>();
                        List<Integer> levels = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            costs.add(em.costs[i]);
                            clues.add(em.enchantClue[i]);
                            levels.add(em.levelClue[i]);
                        }
                        data.put("costs",        costs);
                        data.put("enchantClue",  clues);
                        data.put("levelClue",    levels);
                        data.put("goldCount",    em.getGoldCount());
                        data.put("enchantSeed",  em.getEnchantmentSeed());
                    }
                    case BeaconMenu bm -> {
                        data.put("levels", bm.getLevels());
                    }
                    case BrewingStandMenu bsm -> {
                        data.put("fuel",         bsm.getFuel());
                        data.put("brewingTicks", bsm.getBrewingTicks());
                    }
                    case AnvilMenu am -> data.put("cost", am.getCost());
                    case MerchantMenu mm -> {
                        data.put("traderLevel", mm.getTraderLevel());
                        data.put("traderXp",    mm.getTraderXp());
                        data.put("selectedTrade", 0);
                        List<Map<String, Object>> trades = new ArrayList<>();
                        for (var offer : mm.getOffers()) {
                            Map<String, Object> trade = new LinkedHashMap<>();
                            trade.put("inputA",  InventoryApi.slotItem(-1, offer.getBaseCostA()));
                            trade.put("inputB",  InventoryApi.slotItem(-1, offer.getCostB()));
                            trade.put("result",  InventoryApi.slotItem(-1, offer.getResult()));
                            trade.put("uses",    offer.getUses());
                            trade.put("maxUses", offer.getMaxUses());
                            trade.put("xp",      offer.getXp());
                            trades.add(trade);
                        }
                        data.put("trades", trades);
                    }
                    case LecternMenu lm -> {
                        data.put("page", lm.getPage());
                    }
                    case LoomMenu lom -> {
                        data.put("selectedPattern", lom.getSelectedBannerPatternIndex());
                    }
                    case StonecutterMenu sm -> {
                        data.put("selectedRecipe", sm.getSelectedRecipeIndex());
                        data.put("visibleRecipes", sm.getNumberOfVisibleRecipes());
                    }
                    default -> {}
                }
            }
            ref.value = data;
        });
        return container != null ? container : Map.of("type", "none", "slots", List.of());
    }

    // ==================== slot click ====================

    private static Map<String, Object> slotClick(Map<String, Object> params) {
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;

            int slotId   = AgentWebSocketServer.num(params, "slot", 0);
            int button   = AgentWebSocketServer.num(params, "button", 0);    // 0=left, 1=right
            int clickIdx = AgentWebSocketServer.num(params, "clickType", 0); // 0=PICKUP, 1=QUICK_MOVE, ...
            var clickType = CLICK_TYPES.getOrDefault(clickIdx, ClickType.PICKUP);

            mc.gameMode.handleInventoryMouseClick(p.containerMenu.containerId, slotId, button, clickType, p);
        });
        return Map.of("status", "ok");
    }

    // ==================== quickcraft drag ====================

    /**
     * 发送一个 QuickCraft 拖拽相位包（container/drag）。
     * 纯相位原语：只发当前相位包，不负责拾取/放回物品——取物是调用方前置动作
     * （通常先 container/slot PICKUP 源格，或鼠标已遗留携带物）。
     * phase: 0=begin 1=add 2=finish；type: 0=均分(默认) 1=每格放1 2=创造克隆。
     * begin/finish 省略 slot（内部发 -999，服务端忽略）；add 必填真实格 index。
     * 约束（调用方负责）：同一次拖拽 begin→add×N→finish 顺序，期间不得夹其它点击；
     * 相邻两相位间 ≥1 tick（stateId 需逐帧刷新）。
     */
    private static Map<String, Object> drag(Map<String, Object> params) {
        int phase = AgentWebSocketServer.num(params, "phase", -1);
        int type  = AgentWebSocketServer.num(params, "type", 0);
        if (phase < 0 || phase > 2) {
            return Map.of("status", "error", "message", "invalid phase (0=begin 1=add 2=finish): " + phase);
        }
        if (type < 0 || type > 2) {
            return Map.of("status", "error", "message", "invalid type (0=spread 1=one-per-slot 2=clone): " + type);
        }

        int slot = AgentWebSocketServer.num(params, "slot", -999);
        String error = AgentWebSocketServer.runOnClient(1_000, "Container drag", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null || !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
                ref.value = "no container screen open";
                return;
            }
            var menu = p.containerMenu;
            if (phase == 1 && (slot < 0 || slot >= menu.slots.size())) {
                ref.value = "add phase requires a real slot index 0.." + (menu.slots.size() - 1) + ", got " + slot;
                return;
            }
            int buttonNum = (type << 2) | phase; // type:0均分 1每格1 2克隆，见 §3.2
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, buttonNum, ClickType.QUICK_CRAFT, p);
        });
        return error == null ? Map.of("status", "ok") : Map.of("status", "error", "message", error);
    }

    // ==================== beacon（信标效果） ====================

    /**
     * 设置/切换信标效果（container/beacon）。等价 vanilla 信标 GUI 的"点效果图标 + 点√"：
     * 效果图标点击只改客户端本地字段、不发任何包，只有 √ 才把 primary/secondary 合成
     * 一个 ServerboundSetBeaconPacket 发出并关界面；此处直接发送该包（纯原语：不负责
     * 关界面、不放支付物）。
     * params: primary / secondary = 效果注册 id（如 "minecraft:haste"），缺省或空 = 该槽不设。
     * 前置（调用方负责）：信标界面已开，且支付物已放入支付槽（menu slot 0）——服务端
     * BeaconMenu.updateEffects 在支付槽无物时为空操作。1 级信标主效果可选 haste / speed，二级留空。
     */
    private static Map<String, Object> setBeacon(Map<String, Object> params) {
        String error = AgentWebSocketServer.runOnClient(1_000, "Container beacon", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null || !(mc.screen instanceof BeaconScreen)) {
                ref.value = "beacon screen not open";
                return;
            }
            var menu = p.containerMenu;
            if (!(menu instanceof net.minecraft.world.inventory.BeaconMenu)) {
                ref.value = "not a beacon menu open";
                return;
            }
            var conn = mc.getConnection();
            if (conn == null) {
                ref.value = "no connection to server";
                return;
            }
            Optional<Holder<MobEffect>> primary = beaconEffect(params, "primary");
            Optional<Holder<MobEffect>> secondary = beaconEffect(params, "secondary");
            if (primary == null || secondary == null) {
                ref.value = "unknown effect id (use a registry id like 'minecraft:haste')";
                return;
            }
            conn.send(new ServerboundSetBeaconPacket(primary, secondary));
        });
        return error == null ? Map.of("status", "ok") : Map.of("status", "error", "message", error);
    }

    /** 解析效果注册 id → Holder；缺省/空串 = 空 Optional；格式或 id 无效返回 null。 */
    private static Optional<Holder<MobEffect>> beaconEffect(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        String name = raw instanceof String s ? s : "";
        if (name.isBlank()) return Optional.empty();
        Identifier id = Identifier.tryParse(name);
        if (id == null) return null;
        var holder = BuiltInRegistries.MOB_EFFECT.get(id);
        if (holder.isEmpty()) return null;
        return Optional.of(holder.get());
    }

    // ==================== button click ====================

    private static Map<String, Object> buttonClick(Map<String, Object> params) {
        Boolean accepted = AgentWebSocketServer.runOnClient(1_000, "Container button", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;
            var menu = p.containerMenu;
            int btn = AgentWebSocketServer.num(params, "button", 0);
            ref.value = menu.clickMenuButton(p, btn);
        });
        return Map.of("status", "ok", "accepted", Boolean.TRUE.equals(accepted));
    }

    // ==================== close ====================

    // ==================== text input ====================

    private static Map<String, Object> setText(Map<String, Object> params) {
        String text = (String) params.getOrDefault("text", "");
        Minecraft.getInstance().execute(() -> {
            var screen = Minecraft.getInstance().screen;
            if (screen != null) {
                for (var child : screen.children()) {
                    if (child instanceof EditBox eb && eb.isFocused()) {
                        eb.setValue(text);
                        break;
                    }
                }
            }
        });
        return Map.of("status", "ok");
    }

    // ==================== close ====================

    private static Map<String, Object> closeContainer() {
        Minecraft.getInstance().execute(() -> {
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
                // v2.30（§5.2.3）：关箱前先同步读最终内容提交（主提交路径），再关容器菜单。
                // 若绑定会话为方块容器 → 写 containers.nbt；否则（背包/工作台等非容器族）为空操作。
                ContainerMemoryTracker.commitFromClose();
                mc.player.closeContainer();
            }
        });
        return Map.of("status", "ok");
    }

}
