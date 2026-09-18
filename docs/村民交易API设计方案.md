# 村民交易 API 设计方案（`container/select-trade`）

> 状态：**已定稿并已实施**（2026-09-18 审阅通过；同日完成编码，`compileJava` 通过，改动清单见 §10。
> 待办：§11 的五项源码行为在游戏内确认一次）。
> 关联：`容器拖拽API设计方案.md`、`视觉api设计方案.md` §5.2（agent 容器编辑）、`GUI控件API设计方案.md`
>（已搁置；本方案刻意**不**走控件点击那条路，理由见 §12）。
> 现有实现 `vendor/stevex-template-1.21.11/.../name/modid/api/ContainerApi.java` 与 `src/mod/methods.js`。

---

## 1. 背景与动机

村民交易测试序列（右键村民 → 选中第 2 笔交易 → 结算两次 → 产物放回物品栏 → 搬回余料 → 关闭）里，
**只有"选中指定交易"这一步现有 API 做不到**，其余全部已覆盖：

| 步骤 | 现有能力 | 状态 |
|---|---|---|
| 右键打开交易界面 | `key/use-once` | ✅ |
| 读交易列表 / 断言格内容 | `container/get`（`trades[]` 已实现：`inputA`/`inputB`/`result`/`uses`/`maxUses`/`xp`） | ✅ |
| **选中第 N 笔** | 无任何方法发出 `ServerboundSelectTradePacket` | ❌ **本方案补这个** |
| 自动把付款物搬进支付格 | 服务端 `handleSelectTrade` 内的 `tryMoveItems`，不需要客户端 API | ✅ |
| 取结算格 / 放进背包 / 搬回余料 | `container/slot`（`clickType:0`） | ✅ |
| 退出界面 | `container/close` | ✅ |

参考锚点（1.21.11，decompile/javap 可查）：
`MerchantScreen` 的交易行点击、`ServerGamePacketListenerImpl.handleSelectTrade`、
`MerchantMenu.setSelectionHint/tryMoveItems/moveFromInventoryToPaymentSlot`、
`MerchantContainer.updateSellItem/getRecipeFor`、`MerchantResultSlot.onTake`。

---

## 2. 目标 / 非目标

**目标**

1. 新增 `container/select-trade {index}`：选中村民交易列表中第 `index` 笔，与真人点击交易行**逐行等价**。
2. 零服务端改动、**零 mixin/accessor**（三处依赖全是公开 API，见 §3.5）。
3. 选中结果**可读回**：`container/get` 的 `slot 0/1/2` 即服务端权威状态（见 §3.4）。

**非目标**

- 不做"按物品名自动找交易"：`container/get` 已给 `trades[]`，定位交给上层（LLM/时序脚本）。
- 不做交易列表滚动、不做交易行按钮的控件点击（后者属于已搁置的 `gui/*`，见 §12）。
- 不改服务端/记忆侧逻辑；不改 `container/get` 现有字段（`selectedTrade` 的处置见 §8）。
- 不做 `container/buy`（"选中 + 结算"打包）——结算由 `container/slot {slot:2}` 完成，不新增第二套语义。

---

## 3. 协议权威行为（实现必须逐条对齐）

### 3.1 真人点击交易行（客户端只做两件事）

```java
// MerchantScreen：界面里唯一一处发送 ServerboundSelectTradePacket
this.menu.setSelectionHint(this.shopItem);                                            // ① 本地对齐
this.minecraft.getConnection().send(new ServerboundSelectTradePacket(this.shopItem));  // ② 通知服务端
```

### 3.2 服务端收到包

```java
// ServerGamePacketListenerImpl.handleSelectTrade
if (player.containerMenu instanceof MerchantMenu menu && menu.stillValid(player)) {
    menu.setSelectionHint(index);   // → tradeContainer.setSelectionHint → updateSellItem()：重算结算格
    menu.tryMoveItems(index);       // ← 自动填充支付格（纯服务端行为）
} else {
    LOGGER.debug("Player {} interacted with invalid menu {}", ...);   // 失败只写日志，客户端无回执
}
```

### 3.3 `tryMoveItems(index)`：先退旧、再填新

| 顺序 | 行为 |
|---|---|
| 0 | `index < 0 || getOffers().size() <= index` → 直接返回（**越界是静默 return，不是报错**） |
| 1 | 支付格 0 非空 → `moveItemStackTo(paymentA, 3, 39, true)` **退回背包**；失败则 return |
| 2 | 支付格 1 非空 → 同上 |
| 3 | 支付格 1 空 → 取 `offers.get(index)`，`moveFromInventoryToPaymentSlot(0, costA)`，`costB.ifPresent(→ slot 1)` |

`moveFromInventoryToPaymentSlot(paymentSlot, cost)`：遍历背包 menu `3..38`（含热键栏，即**任意格**），
对每个 `cost.test(stack)` 的叠按 `min(maxStack - 支付格已有, 该叠数量)` 搬运，直到支付格满（64）或遍历完
——即**把支付格填到尽可能满，可跨多叠凑**，不是只放本次所需数量。
（推论：背包绿宝石总数 ≥64 ⇒ 支付格是满叠；总数不足 64 ⇒ 支付格 = 能搬到的全部；
所以"绿宝石放在哪一格"无所谓，交易一次只扣 9，余下的留给下一次。）

**因此"重复选中同一 index"是安全且有用的**：余料先退回背包，再重新填满——可作为"给下一笔交易补货"的手段（§6 坑 7）。

### 3.4 `getRecipeFor(a, b, hint)` —— 为什么必须发包

```java
if (hint > 0 && hint < size()) {
    MerchantOffer offer = get(hint);
    return offer.satisfiedBy(a, b) ? offer : null;   // ← hint>0：只认这一笔
}
for (offer : this) if (offer.satisfiedBy(a, b)) return offer;   // ← hint==0：线性扫描，返回第一笔被满足的
```

两个结论：

1. **手工放料替代不了选中**：`selectionHint` 默认 0，线性扫描会命中"列表里第一笔能被这叠付款满足的交易"。
   若第一笔是"5 绿宝石换苹果"，你放 9 个绿宝石它会给你苹果（`ItemCost.test` 是 `≥` 比较）。
2. **选中状态有强读回**：`index > 0` 时结算格只能由 `offers[index]` 生成——所以
   **`container/get` 看到 `slot 2` 是书架，就等于"服务端确实选中了第 index 笔"**。这是本方案的可验证性来源。

### 3.5 依赖的公开 API（⇒ 不需要 mixin）

| 依赖 | 可见性 | 用途 |
|---|---|---|
| `MerchantMenu.setSelectionHint(int)` | `public` | 本地对齐（与 §3.1 ① 同） |
| `MerchantMenu.getOffers()` | `public` | 越界校验、结果回显 |
| `ServerboundSelectTradePacket(int)` | `public` 构造 | 发包 |

`MerchantMenu.tryMoveItems(int)` 也是 `public`，但**不需要调用**：服务端收到包后会自己调（§3.2）。

### 3.6 槽位编号（`MerchantMenu`）

| menu slot | 内容 |
|---|---|
| 0 / 1 | 支付格 A / B（`x=136/162`） |
| 2 | 结算格（`MerchantResultSlot`，不可放） |
| 3..29 | 物品栏 Inventory **9..35** |
| 30..38 | 快捷栏 |

即与铁砧同布局：**Inventory `i` → menu `i - 6`**（物品栏第 9 格 = menu 3，第 10 格 = menu 4）。

### 3.7 结算（`MerchantResultSlot.onTake`）

```
checkTakeAchievements(结果物)
activeOffer = slots.getActiveOffer()
if (activeOffer != null)
    if (offer.take(slot0, slot1) || offer.take(slot1, slot0))   // 消耗付款，两种顺序都试
        merchant.notifyTrade(offer); player.awardStat(TRADED_WITH_VILLAGER);
        slots.setItem(0, …); slots.setItem(1, …);               // → setChanged → slotsChanged → updateSellItem()
```

`MerchantMenu.slotsChanged(container)` → `tradeContainer.updateSellItem()`（**不含** `tryMoveItems`）。
`updateSellItem()`：支付格 0 为空 ⇒ 结算格清空；否则按 `selectionHint` 重算结算格。
⇒ 付款还够时**结算格会自动补出下一笔**，所以"连点两次结算格 = 两次交易"成立，**中途无需重新选中**。

### 3.8 关闭界面（`MerchantMenu.removed`）

`trader.setTradingPlayer(null)`，随后按存活/断线分支处理支付格余料（归还或掉落）。**余料不会凭空消失**，
所以 §7 序列里"把余料搬回物品栏第 9 格"是为了**落点确定**，不是为了防丢失（细节见 §11）。

---

## 4. API 契约

在 `container` 分类新增一个方法（与 `container/beacon` 同侧、同风格）。

### 4.1 `container/select-trade`

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `index` | 是 | int | 交易下标，与 `container/get` 的 `trades[]` 下标**同一套编号** |

返回：

| 情形 | 返回 |
|---|---|
| 成功 | `{"status":"ok","index":1,"result":{"id":"minecraft:bookshelf","count":1}}`（`result` = 该笔交易的产物，便于调用方立即确认选对了哪笔） |
| 无界面 | `{"status":"error","message":"no screen"}` |
| 非交易界面 | `{"status":"error","message":"not a merchant menu"}` |
| `index` 越界 | `{"status":"error","message":"index out of range","index":index,"size":N}` |
| 无连接 | `{"status":"error","message":"no connection to server"}` |
| 1s 内没轮到 UI 线程 | `{"status":"error","message":"select trade timed out"}`（不会假装成功） |

### 4.2 契约语义

- **等价性**：等价于"用鼠标点了交易列表的第 `index` 行"，即 §3.1 的两步——本地 `setSelectionHint` + 发 `ServerboundSelectTradePacket`。
- **不是原子交易**：本方法**只选中**，不消耗任何物品；真正的交易仍由 `container/slot {slot:2, clickType:0}` 完成（与真人一致）。
- **可重复调用**：同一 `index` 连调安全（§3.3），副作用是支付格被"退旧 + 重新填满"。
- **前置**：当前必须开着 `MerchantMenu`（即已右键村民）。玩家的格内容由服务端改写，`container/get` 刷新。

---

## 5. 实现方案（`ContainerApi`）

### 5.1 改动位置

- `vendor/stevex-template-1.21.11/.../name/modid/api/ContainerApi.java`：注册 `container/select-trade` 并实现；
- `src/mod/methods.js`：补 schema 声明（描述 / zh / paramDefs）。

### 5.2 核心代码（**已实施**，2026-09-18）

```java
// register(): handlers.put("container/select-trade", params -> selectTrade(params));

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
        menu.setSelectionHint(index);                       // ① 本地对齐（§3.1 ①）
        conn.send(new ServerboundSelectTradePacket(index)); // ② 服务端 setSelectionHint + tryMoveItems（§3.2）

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("index",  index);
        ok.put("result", InventoryApi.slotItem(-1, menu.getOffers().get(index).getResult()));
        ref.value = ok;
    });
    return result != null ? result : Map.of("status", "error", "message", "select trade timed out");
}
```

要点：

- **走 `runOnClient`（同步等 UI 线程任务完成），不是 `setBeacon` 那种"只投递不等"**：§4.1 的三种前置错误
  都必须在 UI 线程里当场判定并返回，异步投递的 `out.v` holder 会与调用线程竞态（拿不到 → 只能谎报 ok）。
  注意这**只等客户端 UI 线程的一个 tick 内任务**，服务端结果仍然通过 `container/get` 异步可见；
- 不自己调 `tryMoveItems`（服务端会调，客户端调只会造成双份本地预测）；
- 不做 `stillValid` 检查：那是**服务端**的判定，客户端拦它反而会掩盖"玩家走远了"的真相（§6 坑 8）。

---

## 6. 坑

1. **只发包 vs 本地对齐**：不调 `menu.setSelectionHint` 也能成交（服务端照做），但本地结算格预览会与真人操作不同。
   两步都做 ⇒ 逐行等价真人（§3.1），本方案采用。
2. **`index` 是 `getOffers()` 的下标，不是"界面上第几个可见行"**：`MerchantScreen` 一屏只显示 7 行、可滚动。
   上层应始终用 `container/get` 的 `trades[]` 下标，不要用肉眼行号。
3. **`index == 0` 含义特殊**：`hint == 0` 在服务端是"无提示"（线性扫描第一笔被满足的），不是"精确选中第 0 笔"。
   实务上通常等价，但**不要把 0 当作"精确选中"的语义**；上层要选第 0 笔就老实传 0。
4. **支付格会被填到满叠**（§3.3）：一次可能从背包搬走 64 个绿宝石。调用方要预期支付格出现一大叠，
   交易结束后显式搬回（§7 第 8~9 步）。
5. **支付格被扣空 ⇒ 结算格清空 ⇒ 静默失败**：绿宝石不够两次交易时，第二次点结算格是"点了没反应"。
   **每次点结算格前先断言 `slot 2` 非空**（§7 断言④⑤）。
6. **`isOutOfStock()`**：该笔已用完 ⇒ 结算格不生成，同样表现为"点了没反应"。断言同上。
7. **重复选中会退回旧付款**（§3.3 步 1/2）：`先选 A 再选 B` 不会把 A 的付款留在支付格；
   反过来，也不能靠"多选几次"来囤付款。
8. **服务端失败无回执**：`stillValid` 失败只写服务端 debug 日志。表现是"包发了但格子没变"——
   排障时先看 `container/get` 的 slot 0 是否变成满叠（没变 = 服务端没接受）。
9. **`getBaseCostA()` 不是实际价**：`container/get` 的 `inputA` 用 `getBaseCostA()`（不含需求加价）。
   用它的 `count` **定位**第几笔交易可以，用它**断言**支付格内容会错。

---

## 7. 使用示例

**前置**：只需背包（含热键栏）里**至少有 18 个绿宝石，放在哪一格都行**——支付物由服务端
`tryMoveItems` 自动从 menu `3..38` 任意格搬运（可跨多叠凑，§3.3），所以**不需要**预先摆到第 9 格。
下面序列的落点（第 9/10 格）是我们**指定**的产物与余料去处，与绿宝石原本在哪无关。

```jsonc
{"waitMs":3000}
{"method":"key/use-once","params":{}}                            // 右键打开交易界面
{"waitMs":800}
{"method":"container/get","params":{}}                           // 断言①：trades[] 里 result.id=="minecraft:bookshelf" 的下标（示例 1）

{"method":"container/select-trade","params":{"index":1}}                // ★ 选中第 2 笔（自动从任意格搬付款）
{"waitMs":300}
{"method":"container/get","params":{}}                           // 断言②：slot 0 = 绿宝石 min(64, 背包总数)；slot 2 = 书架（= 选中生效的读回证据）

{"method":"container/slot","params":{"slot":2,"clickType":0}}      // 取第 1 个书架到鼠标
{"waitMs":200}
{"method":"container/get","params":{}}                           // 断言③：carriedItem = 书架×1
{"method":"container/slot","params":{"slot":4,"clickType":0}}      // 放进物品栏第 10 格（menu 4）
{"waitMs":200}
{"method":"container/get","params":{}}                           // 断言④：slot 4 = 书架×1；slot 2 = 书架（已自动补出第 2 个）

{"method":"container/slot","params":{"slot":2,"clickType":0}}      // 取第 2 个书架
{"waitMs":200}
{"method":"container/slot","params":{"slot":4,"clickType":0}}      // 放进第 10 格 → 合并
{"waitMs":200}
{"method":"container/get","params":{}}                           // 断言⑤：slot 4 = 书架×2

{"method":"container/slot","params":{"slot":0,"clickType":0}}      // 取走支付格余料
{"waitMs":200}
{"method":"container/slot","params":{"slot":3,"clickType":0}}      // 放回物品栏第 9 格（menu 3）
{"waitMs":200}
{"method":"container/get","params":{}}                           // 断言⑥：slot 3 = 绿宝石×N；slot 0 空
{"method":"container/close","params":{}}
```

跑完后绿宝石会**分成两处**（都与"原本在哪"无关，纯由上面两步指定）：

- 物品栏第 9 格 = 支付格余料（`1 中该叠数量 - 18`，跨叠凑齐时可能 < 原本那叠）；
- 原来那叠所在的格子 = 剩余部分。

两条附带约束：

- **落点格必须"空或同类"**：第 9/10 格是用 `PICKUP` 放的，若格里已有**不同种类**物品会变成"交换"
  （鼠标反过来拿着那件东西）。第 9/10 格不空就先 `container/get` 换两个空槽当落点。
- **绿宝石不足 18** ⇒ 第二次点结算格静默失败（§6 坑 5），断言②（或开跑前的 `inventory`）能提前看出来。

### 7.1 逐步断言表

| # | 断言 | 不符时的解释 |
|---|---|---|
| ① | `trades[i].result.id == minecraft:bookshelf` | 村民职业/等级不符，或该笔不存在 |
| ② | `slot 0` 为绿宝石（数量 = `min(64, 背包绿宝石总数)`）、`slot 2` 为书架 | slot 0 没变 ⇒ 服务端没接受（§6 坑 8）；slot 2 空 ⇒ 付款不足或已脱销 |
| ③ | `carriedItem.id == minecraft:bookshelf` | 结算格当时是空的（§6 坑 5/6） |
| ④ | `slot 4 = 书架×1` 且 `slot 2` 又出现书架 | 自动补货链断了 ⇒ 付款已扣空 |
| ⑤ | `slot 4 = 书架×2` | 第二次取/放没生效（鼠标上还挂着东西？） |
| ⑥ | `slot 3` 为绿宝石、`slot 0` 空 | 余料没搬干净（关闭时服务端仍会处理，但落点不确定，§3.8） |

---

## 8. 可选增强：真实 `selectedTrade` 回读（**已决定不做**）

`container/get` 里 `selectedTrade` 目前是**硬编码 0**（占位）。要变成真实值需要 accessor：
`MerchantScreen.shopItem` 与 `MerchantMenu.tradeContainer` 都是 private。

**2026-09-18 决定：不接受，保持硬编码。** 理由：§3.4 已说明 `slot 0/1/2` 足以证明"选中生效"
（`index > 0` 时结算格只能由 `offers[index]` 生成），`selectedTrade` 对测试序列没有增量价值。
若将来出现"界面被其它操作改动后需要重新同步选中"的需求，再回来补 accessor。

---

## 9. 测试

1. **冒烟**：面板 8090 `Call Mod Method` 调 `container/select-trade {index:0}`，看返回与支付格变化。
2. **全序列**：§7 逐条执行 + 每步断言。
3. **边界**：无界面 / 非交易界面 / `index` 越界（含负数）→ 各自返回 §4.1 的错误，**不静默**。
4. **幂等**：同一 `index` 连调两次 → 支付格仍是满叠（退旧 + 重填，§3.3）。
5. **选中真的改变了交易**（最强证据）：`index:0` 与 `index:1` 各做一次，断言 `slot 2` 的产物**不同**。
6. **不回归**：既有 `container/*`（铁砧改名、信标、工作台手工放料）序列复跑。

---

## 10. 代码改动清单（审阅确认范围）

- [x] `ContainerApi.java`：`handlers.put("container/select-trade", …)` + `selectTrade(params)`。
- [x] `src/mod/methods.js`：`container/select-trade` 的 schema（描述/zh/paramDefs），分组标题 `容器（7）`→`（8）`，
      文件头方法总数 `50`→`51`。
- [x] `compileJava` 通过（`build/classes/java/main/name/modid/api/ContainerApi.class` 已重编）；
      方法数同步 `test_mod_api.js`（5 处）/ `README.md`（7 处）/ `已实现内容.md`（§1.2 标题 + 容器表 + 返回结构）。
- [x] 不新增 mixin / accessor；不改动其它方法行为。
- [x] **实现偏差（已回写 §5.2 与 §4.1）**：原拟的 `mc.execute` + `out.v` holder 改为 `runOnClient`——
      否则 §4.1 的三种前置错误拿不到（竞态），只能谎报 `ok`；返回体另补"无连接"与"超时"两行。

**定稿项（2026-09-18 审阅通过）**：

1. **方法名 `container/select-trade`**——比原先拟的 `container/trade` 更无歧义：本方法**只选中、不执行交易**
   （§4.2），`trade` 会被误读成"执行一次交易"。
2. **参数名 `index`**（与 `container/get` 的 `trades[]` 下标同一套编号）。
3. **返回带 `result`**（该笔交易的产物，便于调用方当场自证选对了哪一笔）。
4. **§8 不做**：保持 `selectedTrade` 硬编码，不新增 accessor。

---

## 11. 实现期需核实的源码清单（1.21.11）

| 项 | 要确认什么 | 影响 |
|---|---|---|
| `MerchantMenu.setSelectionHint` / `getOffers` / `ServerboundSelectTradePacket(int)` | 是否 public | 已核为 public；若映射变动则需 accessor |
| `setChanged → slotsChanged → updateSellItem` 链 | 交易后结算格**必然**自动补出 | §3.7 与断言④（本方案按字节码推出，建议游戏内确认一次） |
| `moveFromInventoryToPaymentSlot` 搬运量 | 支付格是否真被"跨叠填到尽可能满"（≥64 时 = 满叠） | §3.3 与断言②（同上） |
| `MerchantMenu.removed` | 存活/未断线分支下余料落点（背包还是掉落） | §3.8 的措辞；不影响序列正确性 |
| 是否存在其它写入 `selectionHint` 的路径 | 例如 `ClientboundMerchantOffersPacket` 之后 | §6 坑 3 的措辞 |

> 上述任一项与文档描述不符 ⇒ **回来改本节并同步 §3/§6**，再动代码。

---

## 12. 决策记录

| 决策 | 结论 | 依据 |
|---|---|---|
| 通道选择 | **语义包** `ServerboundSelectTradePacket` | §3.4：选中状态有服务端读回（`slot 2` 只能由 `offers[index]` 生成），符合 `semantic-vs-input-channel-rule` |
| 是否点交易行控件 | **不点** | 交易行点击的语义就是"本地对齐 + 发包"两行，没有额外副作用；走控件反而要重开已搁置的 `gui/*`（`GUI控件API设计方案.md` §0） |
| 是否新增 "选中+结算" 打包方法 | **不新增** | 结算与真人一致地由 `container/slot {slot:2}` 完成，避免第二套语义 |
| 依赖面 | 只用公开 API，零 mixin | §3.5 |
| 命名 | `container/select-trade` + `index` | 与 `container/get` 的 `trades[]` 下标对齐；`select-` 前缀消除"执行交易"的歧义 |
| `selectedTrade` 回读 | **不做**，保持硬编码 | §8：`slot 0/1/2` 已足以证明选中生效 |
| 返回时机 | **同步等 UI 线程任务**（`runOnClient`）而非只投递 | §10 偏差条：异步 holder 无法实现 §4.1 的错误契约，会谎报 `ok`；服务端结果仍由 `container/get` 异步看 |
