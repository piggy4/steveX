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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 容器 API —— 读取/操作当前打开的容器 GUI。
 * 包含方法：get / slot / button / close / text / drag / beacon / select-trade
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
        handlers.put("container/select-trade", params -> selectTrade(params));
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
                        // v2.43 enchantName：把 enchantClue 的注册表数值 id 解成可读附魔名。
                        // 客户端 menu 的 enchantClue 是 holders.getId(ench.enchantment())
                        // （EnchantmentMenu:98），裸数字 id 对 agent 无意义——"第 3 个选项是什么附魔"
                        // 必须靠反查。vanilla 的 EnchantmentScreen:158-161 正是用同一句 lookupOrThrow
                        // 反查 Holder，此处逐句对齐。
                        // 取注册名而非 Enchantment.getFullname 的本地化显示名：与本 API 其余部分
                        // （slotItem 的 enchantments、视觉侧 equipment/effects）一律用注册名的惯例一致。
                        // 选项不可用（costs[i]==0 ⇒ enchantClue[i]==-1）时解不出 ⇒ 该项为 null，
                        // 保持与 costs/levelClue 同长同序，下标一一对应。
                        var enchantLookup = mc.level == null ? null
                                : mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            costs.add(em.costs[i]);
                            clues.add(em.enchantClue[i]);
                            levels.add(em.levelClue[i]);
                            names.add(enchantLookup == null ? null
                                    : enchantLookup.get(em.enchantClue[i])
                                            .map(h -> h.getRegisteredName())
                                            .orElse(null));
                        }
                        data.put("costs",        costs);
                        data.put("enchantClue",  clues);
                        data.put("enchantName",  names);
                        data.put("levelClue",    levels);
                        data.put("goldCount",    em.getGoldCount());
                        // v2.44 bookshelves：有效书架数。**不是菜单状态，是世界状态**——菜单不持有
                        // 附魔台坐标（客户端 ContainerLevelAccess 是 NULL），只能靠准星定位。
                        final Integer bookshelves = countBookshelves(mc);
                        if (bookshelves != null) data.put("bookshelves", bookshelves);
                        data.put("enchantSeed",  em.getEnchantmentSeed());
                    }
                    case BeaconMenu bm -> {
                        final int beaconLevels = bm.getLevels();
                        data.put("levels", beaconLevels);
                        // v2.47 effects：信标可选效果的**全量列表**。此前该分支只有 levels——
                        // agent 因此既不知道有哪些效果可设，也不知道当前等级下界面上哪几个能点。
                        //
                        // 列表**不在 BeaconMenu 里**，而是静态常量 BeaconBlockEntity.BEACON_EFFECTS
                        // （4 组 2/2/1/1 共 6 个，BeaconBlockEntity.java:53-58），每个信标、每个存档都一样
                        // ⇒ **不需要服务端回读**，也不依赖 mc.level——与切石机 recipes / 织布机 patterns 的关键差别。
                        //
                        // canBePrimary/canBeSecondary 复刻 GUI 门禁（BeaconScreen.java:69-101 + :216
                        // `active = tier < levels`）：主效果列 = 四组全部，某效果可点 ⟺ tier < levels；
                        // 副效果列**只放第 4 组**（即 regeneration），且 levels ≥ 4 时才有这一列。
                        // 注意 tier 是**纯 UI 门禁**：服务端不过滤 tier
                        // （BeaconBlockEntity.filterEffect:111 只按 VALID_EFFECTS 过滤，applyEffects:238
                        // 只看 levels 决定范围/时长）⇒ container/beacon 能设出界面上点不到的效果，
                        // 故这两个布尔是 agent 判断"界面允不允许"的唯一依据。
                        final List<Map<String, Object>> beaconEffects = new ArrayList<>();
                        for (int tier = 0; tier < BeaconBlockEntity.BEACON_EFFECTS.size(); tier++) {
                            final boolean canBeSecondary = tier == 3 && beaconLevels >= 4;
                            for (var effect : BeaconBlockEntity.BEACON_EFFECTS.get(tier)) {
                                Map<String, Object> e = new LinkedHashMap<>();
                                e.put("effect",         effect.getRegisteredName());
                                e.put("tier",           tier);
                                e.put("canBePrimary",   tier < beaconLevels);
                                e.put("canBeSecondary", canBeSecondary);
                                beaconEffects.add(e);
                            }
                        }
                        data.put("effects", beaconEffects);
                        // 当前选中：BeaconMenu 的 DataSlot 1/2，由服务端同步到客户端。
                        // vanilla 用 0 编码"没选"（BeaconMenu.decodeEffect:107）⇒ 这里**显式写 null**：
                        // 它表示"已知当前没有效果"，与"未知"（不写该键）是两件不同的事。
                        data.put("primaryEffect",   effectName(bm.getPrimaryEffect()));
                        data.put("secondaryEffect", effectName(bm.getSecondaryEffect()));
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
                        // v2.46 patterns：织布方案列表的**具体内容**（此前只有 selectedPattern
                        // 这个下标，agent 无从知道第 N 号是什么图案，只能盲点）。
                        //
                        // 数据本来就在客户端：selectablePatterns 是 LoomMenu 的**字段**（不是
                        // DataSlot），由客户端自己的 slotsChanged → getSelectablePatterns()
                        // 算出——vanilla 的 LoomScreen:131 正是读它画那 4×4 个按钮。故这里不是
                        // 新开同步通道，只是把已有数据接出来。
                        //
                        // 图标取"选它就会得到的那面旗"（即 LoomMenu.setupResultSlot:263-280 的
                        // 算式：旗帜槽那面旗 + 本图案 + 染料槽的颜色），而**不是** LoomScreen 按钮
                        // 上那面固定灰底的预览旗。理由：灰底是界面为中性预览造的，世上并不存在
                        // 那样一面旗，写进读取面就是**假事实**；"选它得到什么"才是真话。
                        //
                        // **下标必须与 button 号一一对应**（agent 用 container/button {button:i}
                        // 选第 i 个方案），故**逐个输出、绝不跳过**。
                        //
                        // 与切石机的两点不同：① 按钮号是**全局**下标——界面 4×4 一次只显示 16 个、
                        // 要滚动才看得全 32 个，但 LoomScreen:193-194 已把 startRow 折进 index，
                        // 故本列表给全量、点按钮**不需要先滚动**；② 图案槽为空时列表来自
                        // BannerPatternTags.NO_ITEM_REQUIRED，放进图案物则改由该物的
                        // PROVIDES_BANNER_PATTERNS 决定——两种都由 vanilla 自己算。
                        //
                        // 空列表是**权威事实**（selectablePatterns 确实是空的：旗帜或染料缺一即
                        // 如此，见 LoomMenu.slotsChanged:185-189），照写 []；mc.level 为 null 则
                        // **不写该键**（缺席=未知）。二者口径同切石机 recipes。
                        if (mc.level != null) {
                            final var selectable  = lom.getSelectablePatterns();
                            final var bannerStack = lom.getBannerSlot().getItem();
                            final var dyeStack    = lom.getDyeSlot().getItem();
                            if (selectable.isEmpty()) {
                                data.put("patterns", List.of());
                            } else if (!bannerStack.isEmpty() && dyeStack.getItem() instanceof DyeItem dyeItem) {
                                final List<Map<String, Object>> patterns = new ArrayList<>();
                                for (var pattern : selectable) {
                                    patterns.add(InventoryApi.slotItem(-1,
                                            bannerWithPattern(bannerStack, pattern, dyeItem.getDyeColor())));
                                }
                                data.put("patterns", patterns);
                            }
                            // 图案非空却缺旗帜/染料：造不出一面真旗 ⇒ 不写该键（缺席=未知）
                        }
                    }
                    case StonecutterMenu sm -> {
                        data.put("selectedRecipe", sm.getSelectedRecipeIndex());
                        data.put("visibleRecipes", sm.getNumberOfVisibleRecipes());
                        // v2.45 recipes：切割方案列表的**具体内容**（此前只有 visibleRecipes 这个数量）。
                        // 列表本来就在客户端：它是 StonecutterMenu 的 recipesForInput **字段**（不是
                        // DataSlot），由客户端自己的 level.recipeAccess().stonecutterRecipes()
                        // .selectByInput(...) 算出——vanilla 的 StonecutterScreen:82-91 正是从客户端
                        // menu 读它画那 12 个按钮，并用同一句 optionDisplay().resolveForFirstStack(...)
                        // 解析出图标。故这里不是新开同步通道，只是把已有数据接出来。
                        //
                        // 取 GUI 上显示的**图标**（optionDisplay）而非 Recipe#assemble 的结果：
                        // 与所见一致，且对任何 SlotDisplay 变体都成立。
                        //
                        // **下标必须与 button 号一一对应**（agent 用 container/button {button:i} 选
                        // 第 i 个方案），故**逐个输出、绝不跳过**——即使某个图标解析为空气
                        // （SlotDisplay.Empty → count:0）也照占一位，否则下标会错位。
                        // 条目数应恒等于 visibleRecipes。
                        //
                        // mc.level 为 null 时**不写该键**（缺席=未知，与 bookshelves/effects 同口径）：
                        // 写 [] 会被读成"没有方案"这一断言，而 visibleRecipes 才是那个事实的权威来源。
                        if (mc.level != null) {
                            final var slotCtx = SlotDisplayContext.fromLevel(mc.level);
                            final List<Map<String, Object>> recipes = new ArrayList<>();
                            for (var entry : sm.getVisibleRecipes().entries()) {
                                recipes.add(InventoryApi.slotItem(-1,
                                        entry.recipe().optionDisplay().resolveForFirstStack(slotCtx)));
                            }
                            data.put("recipes", recipes);
                        }
                    }
                    default -> {}
                }
            }
            ref.value = data;
        });
        return container != null ? container : Map.of("type", "none", "slots", List.of());
    }

    /**
     * 附魔台的有效书架数（v2.44，{@code container/get} 的 {@code bookshelves} 键）。
     *
     * <p><b>为何要自己算</b>：vanilla 把这个数当**局部变量**用（{@code EnchantmentMenu.slotsChanged}
     * 里的 {@code int bookcases}），既不存字段也不进 DataSlot ⇒ <b>根本不上同步链路</b>，
     * 客户端副本里没有这个值，只能重算。
     *
     * <p><b>但不是"复刻服务端算法"</b>：直接用 vanilla 自己的公开判定
     * {@link EnchantingTableBlock#isValidBookShelf}——同一句在客户端的
     * {@code EnchantingTableBlock.animateTick} 里本来就每帧跑（算附魔粒子），读的正是客户端自己
     * 的世界副本。所以这里没有"与服务端逐字节对齐"的负担，也不随版本偏移。
     *
     * <p><b>坐标从哪来</b>：只能来自准星。客户端 menu 的 {@code ContainerLevelAccess} 是
     * {@code NULL}（见 {@code buttonClick} 的说明），拿不到 pos；{@code ContainerMemoryTracker.bind}
     * 定位方块容器用的是同一手法。附魔界面锁鼠标视角 ⇒ 实际恒命中，但<b>不假定它成立</b>：
     * {@code camera/turn} 可以在界面开着时转动视角，那时准星已经是别的方块，照算就会给出
     * "别的方块周围的书架数"＝假事实。故必须验证方块类型。
     *
     * <p><b>取不到返回 {@code null}，由调用方不写该键</b>——缺席＝<b>未知</b>，与 v2.42 的
     * {@code effects} 同口径。绝不返回 0：0 是一个断言（"周围没有书架"），而这里的事实是
     * "没法判定"。
     *
     * <p>注意本值是**原始计数**；vanilla 在 {@code EnchantmentTableBlock.getEnchantmentCost}
     * 里把它 clamp 到 15，所以 &gt;15 与 15 对附魔等级等效。
     *
     * @return 有效书架数；准星非方块 / 世界未加载 / 准星所指不是附魔台 → {@code null}
     */
    private static Integer countBookshelves(final Minecraft mc) {
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return null;
        final var level = mc.level;
        if (level == null) return null;
        final BlockPos pos = bhr.getBlockPos();
        if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) return null;
        int count = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, pos, offset)) count++;
        }
        return count;
    }

    /**
     * 造一面"选了该图案就会得到"的旗：旗帜槽那面旗 + 该图案 + 该染料色。
     *
     * <p>逐句对齐 vanilla 的 {@code LoomMenu.setupResultSlot}（{@code LoomMenu.java:263-280}）
     * ——那条正是产物格的算式：{@code copyWithCount(1)}，再把图案层**追加**到旗帜原有图案
     * 之后（{@code addAll(layers).add(pattern, color)}）。产物格只算被选中的那一个，本方法
     * 对方案列表里的每一个各算一次。
     *
     * <p>刻意**不用** {@code LoomScreen} 按钮上那面灰底预览旗（{@code LoomScreen:178} 用
     * {@code DyeColor.GRAY} 填底）：那是界面为了中性预览造的，世上并不存在那样一面旗，
     * 写进读取面即成假事实。
     */
    private static ItemStack bannerWithPattern(final ItemStack banner,
                                               final Holder<BannerPattern> pattern,
                                               final DyeColor color) {
        final ItemStack result = banner.copyWithCount(1);
        result.update(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY,
                layers -> new BannerPatternLayers.Builder().addAll(layers).add(pattern, color).build());
        return result;
    }

    /**
     * 效果 Holder → 注册名（如 {@code minecraft:haste}）；{@code null} 原样返回。
     *
     * <p>信标的"当前没选效果"在 vanilla 里编码为 {@code 0}（{@code BeaconMenu.decodeEffect:107}），
     * 语义是**已知为空**而非未知，故经此助手转成 {@code null} 写进 JSON
     * ——落盘要真的出现 {@code null}，这依赖出站 Gson 的 {@code serializeNulls}，
     * 见 {@code AgentWebSocketServer.GSON_OUT}。
     */
    private static String effectName(final Holder<MobEffect> effect) {
        return effect == null ? null : effect.getRegisteredName();
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

    // ==================== select trade（村民交易） ====================

    /**
     * 选中村民交易列表中第 index 笔（container/select-trade）。等价 vanilla 交易界面点击第 index 行：
     * ① 本地 setSelectionHint（对齐结算格预览）+ ② 发 ServerboundSelectTradePacket，服务端在
     * handleSelectTrade 里做 setSelectionHint + tryMoveItems（把付款物从背包搬进支付格，填到满叠）。
     * 本方法**只选中、不消耗物品**——真正的交易仍由 container/slot { slot:2 } 完成（可连续点，结算格会自动补货）。
     * params: index = 交易下标，与 container/get 的 trades[] 同一套编号（0 起，越界报错）。
     * 前置（调用方负责）：交易界面已开；村民未走远/未失效——服务端 stillValid 失败只写日志、客户端无回执，
     * 表现为"包发了但支付格没变"。
     */
    private static Map<String, Object> selectTrade(Map<String, Object> params) {
        int index = AgentWebSocketServer.num(params, "index", -1);
        Map<String, Object> result = AgentWebSocketServer.runOnClient(1_000, "Container select trade", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (p == null || !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
                ref.value = Map.of("status", "error", "message", "no screen");
                return;
            }
            if (!(p.containerMenu instanceof MerchantMenu menu)) {
                ref.value = Map.of("status", "error", "message", "not a merchant menu");
                return;
            }
            if (index < 0 || index >= menu.getOffers().size()) {
                ref.value = Map.of("status", "error", "message", "index out of range",
                                   "index", index, "size", menu.getOffers().size());
                return;
            }
            var conn = mc.getConnection();
            if (conn == null) {
                ref.value = Map.of("status", "error", "message", "no connection to server");
                return;
            }
            menu.setSelectionHint(index);                               // ① 本地对齐（§3.1 ①）
            conn.send(new ServerboundSelectTradePacket(index));         // ② 服务端 setSelectionHint + tryMoveItems

            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("status", "ok");
            ok.put("index",  index);
            ok.put("result", InventoryApi.slotItem(-1, menu.getOffers().get(index).getResult()));
            ref.value = ok;
        });
        return result != null ? result : Map.of("status", "error", "message", "select trade timed out");
    }

    // ==================== button click ====================

    /**
     * 点击容器按钮（container/button）——必须发服务端包，本地调用不产生任何效果。
     *
     * <p>本地 {@code menu.clickMenuButton} <b>只当预检闸门用</b>：客户端 menu 的
     * {@code ContainerLevelAccess} 是 {@code NULL}（{@code MenuType} 的 create 走
     * {@code ContainerLevelAccess.NULL}），其 {@code evaluate} 直接返回 {@code Optional.empty()}，
     * 于是 {@code clickMenuButton} 里那段真正干活的 {@code this.access.execute(...)} 在客户端空转；
     * 只有服务端 menu 持有带 level/pos 的 access。vanilla 的附魔界面正是这么用的
     * （{@code EnchantmentScreen} 把返回值当"要不要发包"的判断，真正生效的是紧随其后的发包），
     * 故此处逐句对齐。
     *
     * <p>{@code handleInventoryButtonClick} 发 {@code ServerboundContainerButtonClickPacket}，
     * 服务端 {@code handleContainerButtonClick} 在服务端 menu 上跑 {@code clickMenuButton} 并
     * {@code broadcastChanges()}。
     *
     * <p><b>返回值语义</b>：{@code accepted} 只表示"本地预检通过且已发包"，<b>不是</b>执行结果
     * ——服务端无论成功失败都不回包。判定是否真的生效必须靠随后的 {@code container/get} 观察
     * （例：附魔台看附魔位物品是否长出 enchantments、青金石是否被扣）。
     */
    private static Map<String, Object> buttonClick(Map<String, Object> params) {
        Boolean accepted = AgentWebSocketServer.runOnClient(1_000, "Container button", ref -> {
            var mc = Minecraft.getInstance();
            var p = mc.player;
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;
            var menu = p.containerMenu;
            int btn = AgentWebSocketServer.num(params, "button", 0);
            if (!menu.clickMenuButton(p, btn)) return; // 预检不通过：vanilla 同样不发包
            mc.gameMode.handleInventoryButtonClick(menu.containerId, btn);
            ref.value = true;
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
