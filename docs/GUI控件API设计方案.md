# GUI 控件 API 设计方案（地址寻址 + 控件语义分发）

> 状态：**已搁置**（2026-09-18 决定，不实施。「搁置说明」见 §0）。
> 关联：`批量时序API设计方案.md`、`容器拖拽API设计方案.md`、`视觉api设计方案.md` §5.2（agent 容器编辑）、
> 现有实现 `vendor/stevex-template-1.21.11/.../name/modid/api/ContainerApi.java`、`SignApi.java`、`src/mod/methods.js`。
> 起源：工作台"配方书"测试序列（打开配方页 → 剑斧分类 → 仅显示可合成 → 搜索 → 点击配方 → 取产物）
> 用现有 API 无法实现（缺口逐条见 §9.1）。

---

## 0. 搁置说明（2026-09-18）

**结论：整套 `gui/*` 不实施。** 触发搁置的论证：

- **驱动场景（工作台配方书）已被现有 API 覆盖**：合成用 `container/slot` 手工放料（右键 `button:1` 精确放 1 个）
  即是完整可用的路径；配方书实际只多给"省几步"和"游戏权威的配方知识"，前者不值这个成本。
- **成本不对称**：§6.2 的三个 mixin 要伸进 `RecipeBookComponent`/`RecipeBookPage` 的**私有字段**，
  版本升级即失效；外加 §12 那 9 项核实工作。用这个换"少几步调用"不划算。
- **逐个界面复核后几乎不存在非 GUI 替代的缺口**：附魔台/织布机/切石机/制图台 = `container/button`；
  铁砧 = slot + `container/text`；告示牌/书 = 已有 `SignApi`/`BookApi`；村民交易按其协议形态
  （`ServerboundSelectTradePacket` + `selectionHint`）应当走 §3 意义上的**语义通道**而非点控件。
  唯一真缺口是创造模式物品栏的 tab/搜索，而它另有 `/give`（`key/command`）与 `key/pick-item` 替代。

**保留下来的是 §3**：它是这几轮讨论唯一沉淀出的、独立于本方案的判定准则
（"看点控件的视觉状态在客户端有没有服务端回读通道"）。后续任何 API 提案都该先过这条准则——
信标（有回读 ⇒ 语义通道合法）与配方书（搜索/tab/页码无回读 ⇒ 只能走输入路径）是它的正反两个锚点。

**什么情况下重新启用**（任一成立即回到本方案）：

1. 项目目标从"**能完成任务**"转为"**能操作任意界面**"（把 GUI 操作覆盖度本身当作被考察的能力/评测项）——
   此时配方书、创造模式 tab 这类目标成立，不是因为它们不可替代，而是因为"能不能操作这个界面"就是考察对象；
2. 出现一个**具体的、无任何非 GUI 替代**的界面控制需求（第一版就按 §4.4 的角色表增补角色，从 §11 的清单开工）。

**另一条并行候选**：若真实需求其实是"让 agent 知道配方"，正确的解是**只读的配方查询 API**
（`recipe/*`：返回配方 + 材料清单 + 按当前背包判断可否合成），而不是驱动配方书 GUI——
它不需要任何 mixin、不依赖搜索与分页、一次给全部配方而非当前页那 9 个，且顺带消除手工放料的正确性风险。
该方向独立评估，不在本方案范围内。

---

## 1. 背景与动机

现有容器/界面类 API 只有两条通道：

| 通道 | 方法 | 覆盖范围 | 边界 |
|---|---|---|---|
| **容器菜单点击** | `container/slot`、`container/drag` | `AbstractContainerMenu` 的格子与 QuickCraft | 只认 **slot index**，不认任何屏幕控件 |
| **菜单数据按钮** | `container/button` | `menu.clickMenuButton(id, btn)`（附魔台/织布机等**菜单数据**按钮） | 不是 `AbstractWidget`，与屏幕控件无关 |
| **语义包** | `container/beacon` | 直接发 `ServerboundSetBeaconPacket` | 仅信标，且属"语义通道"（判定见 §3） |

也就是说：**屏幕上的控件（按钮、下拉、搜索框、书页）完全没有寻址与点击原语**。
`container/text` 是唯一碰到控件的方法，而它的实现是"找当前屏幕里**已获焦点**的 `EditBox` 然后 `setValue`"——
既要求调用方先把焦点弄对（而把焦点弄对本身就需要控件寻址），又绕过了 `charTyped` 的完整语义。

工作台的配方书正是这个缺口最典型的场景：配方书**是一整套屏幕控件**（开关按钮、分类 tab、过滤按钮、
搜索框、配方按钮、翻页按钮），全部挂在 `CraftingScreen` 的 `children()` 上，现有 API 一个都摸不到。

本方案给出一个**通用的屏幕控件控制 API**（`gui/*`），并以此为配方书等场景建一套**角色注册表**。

---

## 2. 目标 / 非目标

**目标**

1. **地址寻址，不暴露坐标**：调用方只说"点什么"（`target: "recipebook.toggle"`），
   坐标由 API 从**目标控件自身的公开几何**（`getX()/getY()/getWidth()/getHeight()`）现算。语法里没有 `x`/`y` 参数。
2. **触发指定的点击事件，而不是"等价效果"**：每个控件类型都派发到 vanilla 里**跑完整语义的那个入口**
   （§5 分发表），使"操作完成 ⇒ 界面真的变了"成为恒等式，而不是巧合。
3. **可断言**：配 `gui/widgets` 状态快照，每一步都能证明 UI 确实发生了预期变化（§9.3 逐步断言表）。
4. **零服务端改动**：纯客户端 mod；对 vanilla 服务端（WeGame/原版）一律可用。

**非目标**

- 不做 OS/像素级鼠标注入（与 `容器拖拽API设计方案.md` §7 同理：坐标注入脆弱且与游戏机制脱节）。
- 不做通用 UI 遍历器（"找所有按钮并点一遍"）：只覆盖 §4.4 注册表列出的角色 + 一个通用兜底。
- 不引入"语义捷路径"绕过控件（例如直接调 `RecipeBookComponent.toggleVisibility()` 而不管按钮）——
  唯一例外是 §6.3 的**回退**机制，且必须在 `gui/widgets` 中标注该次点击走的是回退路径。
- 不改服务端/记忆侧任何逻辑。

---

## 3. 判定准则：什么时候允许走"语义通道"

这是本项目多次踩坑后定的准则，也是本方案选择"输入路径"的唯一理由：

> **看点控件的那个视觉状态，在客户端有没有服务端回读通道。**
> - 有 → 语义包与真人操作在**状态上等价**（信标可作此例）；
> - 没有 → 语义包会与界面脱节，**必须走输入路径**。

**反例（有回读 ⇒ 语义通道合法）——信标按钮点亮：**

```
ServerboundSetBeaconPacket
  → 服务端 BeaconMenu.updateEffects 写 beaconData.set(1/2, encodeEffect(...))
  → ClientboundContainerSetDataPacket
  → 客户端 BeaconScreen$1.dataChanged 写 BeaconScreen.primary / secondary
  → 下一 tick containerTick() → updateButtons() → setSelected / visible / active
```
即 `container/beacon` 之后按钮**必然**亮起，因为状态源在服务端。

**配方书的回读情况（部分有）：**

| 状态 | 回读通道 | 结论 |
|---|---|---|
| 书开/关 | `RecipeBookComponent.tick()` 每 tick 比对 `isVisible() != isVisibleAccordingToBookData()`，读 `ClientRecipeBook.isOpen(type)` | ✅ 有回读（服务端 `ClientboundRecipeBookSettingsPacket` 回写） |
| 是否"仅显示可合成" | 同上，`isFiltering()` 读 `ClientRecipeBook.isFiltering(type)` | ✅ 有回读（数据层；按钮视觉待游戏内确认） |
| 搜索文本 | 无（纯客户端 `EditBox` 值） | ❌ 无 |
| 当前分类 tab | 无（纯客户端 `selectedTab`） | ❌ 无 |
| 当前页 / 已点配方 | 无（纯客户端 `RecipeBookPage` 状态） | ❌ 无 |

后四项**不可能**补齐回读：那需要在服务端保存这些状态并由服务端发包回写，而客户端 mod 无法给 vanilla 服务端加包。
但注意——**它们本来就不需要回读**：只要走输入路径，客户端状态就是**被真实点击写入**的，
`gui/widgets` 直接读客户端权威状态即可断言（§9.3）。回读只在"用语义包冒充点击"时才成为必需。

**结论**：`gui/*` 一律走**输入路径**（`widget.mouseClicked` / 组件级 `mouseClicked`），不使用语义包。

---

## 4. API 契约

新增分类 `gui`，三个方法（与 `container/*` 平级，同侧注册）。

### 4.1 `gui/widgets` —— 枚举 + 状态快照

无参数。返回当前屏幕的**可寻址控件清单**（`screen.children()` 递归展开，含配方书内部控件）：

```json
{"status":"ok","screen":"CraftingScreen","widgets":[
  {"index":0,"role":"recipebook.toggle","type":"ImageButton","x":168,"y":66,"w":20,"h":18,
   "visible":true,"active":true,"message":"","value":null},
  {"index":1,"role":"recipebook.tab:crafting","type":"RecipeBookTabButton","selected":true, ...},
  {"index":7,"role":"recipebook.filter","type":"ImageButton","selected":false, ...},
  {"index":8,"role":"recipebook.search","type":"EditBox","focused":true,"value":""},
  {"index":12,"role":"recipebook.recipe:minecraft:diamond_sword","type":"RecipeButton",
   "item":"minecraft:diamond_sword","craftable":true,"page":0}
]}
```

字段约定（能取到才出，取不到省略——与 `InventoryApi.slotItem` 的 `name` 同风格）：

| 字段 | 来源 | 用途 |
|---|---|---|
| `role` | 角色注册表（§4.4） | 断言的**稳定锚点**；未注册的控件 `role` 为 `null` |
| `type` | `getClass().getSimpleName()` | 分发依据，也是"这是什么控件"的答案 |
| `x/y/w/h` | `getX()/getY()/getWidth()/getHeight()` | 只读展示，**不是** API 入参 |
| `visible` / `active` | `isVisible()` / `isActive()` | 断言"界面真的变了"的主力字段 |
| `message` | `getMessage().getString()` | 按文本寻址/断言的依据 |
| `value` / `focused` | `EditBox.getValue()` / `isFocused()` | 文本输入的断言依据 |
| `selected` | `RecipeBookTabButton`（`AbstractWidget` 选中态） | 分类/过滤按钮的断言依据 |
| `item` / `craftable` / `page` | `RecipeButton.getDisplayStack()` / `RecipeCollection.isCraftable` | 配方定位与断言 |

该方法的第二职责是**给每一步一个可断言的证据**：任何一条 `gui/click` 之后都应当能指着一两个字段说"它变了"。

### 4.2 `gui/click` —— 点击

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `target` | 是 | string | 角色名或兜底地址（§4.4） |
| `value` | 否 | string | 角色的限定值（tab 名 / 搜索词等） |
| `index` | 否 | int | 同角色多命中时取第 N 个（默认 0） |
| `button` | 否 | int | 0=左键（默认）/1=右键；仅对认按钮的控件有意义 |
| `doubleClick` | 否 | bool | 默认 false |

返回 `{status:"ok", target, dispatched:"RecipeBookComponent.mouseClicked", widget:{…命中控件的快照…}}`。
`dispatched` 字段把"**实际走了哪个入口**"讲清楚（§5 表的分发结果），便于排障与断言；
找不到目标、或被 `isActive()/isVisible()` 拦下时返回 `{status:"error", reason:"…"}` 而**不静默**。

### 4.3 `gui/text` —— 文本输入

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `target` | 是 | string | 目标 `EditBox` 的角色或地址 |
| `text` | 是 | string | 要输入的文本 |
| `clear` | 否 | bool | 默认 true：先清空原内容 |
| `mode` | 否 | string | `"type"`（默认，逐码点 `charTyped`）/ `"set"`（`setValue` 快路径） |

- `"type"`：先 `widget.mouseClicked(中心)`（真人第一步就是点一下搜索框）+ `setFocused(true)`，
  再对每个码点 `screen.charTyped(new CharacterEvent(cp, 0))`。**这是真人的路径**，完整走
  `EditBox.charTyped` → `insertText` → `onValueChange` → `responder`（配方书即 `checkSearchStringUpdate`），
  从而 `isEditable()`/长度上限/输入过滤全部生效。
- `"set"`：直接 `setValue(text)`，与现有 `container/text` 行为一致，作为"某屏幕的 EditBox 挂了输入过滤
  或中文输入法场景"的兜底。**默认不用它**，因为它跳过 `charTyped`（本方案的立身之本就是"走真实入口"）。

> 中文说明：真人用输入法输入"钻石剑"在 MC 里最终也是落到 `EditBox.insertText`。两条路在 `EditBox` 层面等价，
> 故 `"type"` 对 CJK 同样可用；`"set"` 的价值只在于规避屏幕自带的 `filter`。

### 4.4 角色注册表

**职责**：把"控件引用"翻译成调用方能说的名字，并在 `gui/widgets` 里回填 `role` 供断言。

| 角色 | 命中对象 | 默认入口 |
|---|---|---|
| `recipebook.toggle` | `AbstractRecipeBookScreen.recipeBookToggleButton` | `widget.mouseClicked` |
| `recipebook.tab` + `value:"<crafting\|combat\|…>"` | `RecipeBookComponent.tabButtons` 中 `getTab()==value` 者 | `widget.mouseClicked` |
| `recipebook.filter` | `RecipeBookComponent.filterButton` | `widget.mouseClicked` |
| `recipebook.search` | `RecipeBookComponent.searchBox` | `EditBox.mouseClicked` + `charTyped`（§4.3） |
| `recipebook.recipe` + `item:"<itemId>"` | `RecipeBookPage` 当前页的 `RecipeButton`，`getDisplayStack()` 匹配 | **`RecipeBookComponent.mouseClicked`**（§5.2） |
| `recipebook.next-page` / `recipebook.prev-page` | `RecipeBookPage` 的 forward/back 按钮 | `widget.mouseClicked`（无绑定则回退 `nextPage()/previousPage()`，§6.3） |
| `button` + `message:"<文本>"` | `screen.children()` 中 `message` 匹配的 `AbstractButton` | `widget.mouseClicked` |
| `widget` + `index:N` | `gui/widgets` 里的第 N 个控件 | 按 `type` 查 §5 分发表 |
| `screen.<name>` | 预留：屏幕级动作（如 `screen.close`） | 走对应屏幕方法 |

第一版只**要求**实现 `recipebook.*`（因为它是驱动本方案的场景）；其余角色按需增补——
注册表是纯数据，增补角色不改分发逻辑。

---

## 5. 分发表：每种控件走哪个入口

这是本方案的技术核心。**为什么不能一律 `widget.mouseClicked`**：

```
AbstractWidget.mouseClicked(event, doubleClick):
    if (!isActive())                    return false;   // 灰按钮点不动 —— 正确行为
    if (!isValidClickButton(buttonInfo)) return false;
    if (!isMouseOver(x, y))              return false;   // 坐标必须落在控件矩形内
    playDownSound(...)
    onClick(event, doubleClick)                          // ← 关键
```
而 `AbstractWidget.onClick` 是**空实现**（`return;`），`AbstractButton.onClick` 才转 `onPress(input)`。
于是控件分三类：

| 控件类型 | 入口 | 说明 |
|---|---|---|
| `Button` / `ImageButton` / `Checkbox` / `CycleButton` | `widget.mouseClicked(自心中点, false)` | 自带 `onPress`，**一次调用即完整语义**。配方书开关、tab、过滤、翻页按钮属此列 |
| `EditBox` | `widget.mouseClicked(…, false)` + `setFocused(true)` + `charTyped` 序列 | 点击负责取焦与光标，文本另走 §4.3 |
| **`RecipeButton`** | **`recipeBookComponent.mouseClicked(该按钮中心点, false)`** | ⚠️ 见 §5.2，本方案的关键决策 |

### 5.1 坐标从哪来（"不暴露坐标"≠"不用坐标"）

调用方给的只有 `target`；API 内部对命中的控件算：

```java
double cx = w.getX() + w.getWidth()  / 2.0;   // 控件自身公开几何
double cy = w.getY() + w.getHeight() / 2.0;
var event = new MouseButtonEvent(cx, cy, new MouseButtonInfo(button, 0));
```
`isMouseOver` 用的是**屏幕坐标系**，而 `getX()/getY()` 正是屏幕坐标——两者天然一致，
所以"点在控件正中"是恒成立的，不需要任何屏幕布局常量，也不需要 GUI scale 换算。
**坐标是实现的内部细节，不是契约的一部分**：调用方永远不写坐标（这是与 `容器拖拽API设计方案.md` §7 拒绝像素注入的分界线）。

### 5.2 关键决策（已定稿）：配方按钮走 `RecipeBookComponent.mouseClicked`

`RecipeButton` **没有**覆写 `onClick` / `mouseClicked` / `onPress`（三处都不是它），
所以调它自己的 `mouseClicked` 只会返回一个命中布尔值、**什么也不做**。
它的语义分散在两层：

```
RecipeBookPage.mouseClicked(event, xOrigin, yOrigin, w, h, doubleClick):
    … 命中判定（含 xOffset/yOffset 偏移）…
    写 lastClickedRecipe / lastClickedRecipeCollection      // ← 副作用①

RecipeBookComponent.mouseClicked(event, doubleClick):
    if (!isVisible() || player.isSpectator()) return false;
    if (recipeBookPage.mouseClicked(event, xOffset, yOffset, width, height, doubleClick)) {
        读 lastClickedRecipe / lastClickedRecipeCollection
        recipeBookPage.setInvisible()
        if (tryPlaceRecipe(...))        // ← 副作用②：ghostSlots.clear() + ServerboundPlaceRecipePacket
            setVisible(false)           // ← 副作用③：配方书自动收起
        return true;
    }
```

**决策 (i)**：把 `gui/click {target:"recipebook.recipe"}` 派发到**最外层的、跑完整语义的那个入口** ——
`RecipeBookComponent.mouseClicked(eventAtButtonCenter, false)`。理由：

1. 副作用 ②③ 只存在于这一层，**它们正是用户要看到的界面变化**（材料进格 + 书收起）；
2. 该入口内部**自己会再调 `RecipeBookPage.mouseClicked`**，等于自动包含副作用 ①；
3. 它用的是**组件自己的 `xOffset/yOffset/width/height`**，与命中判定天然自洽——
   绕开它自己去调 `RecipeBookPage.mouseClicked` 反而要手算偏移，是把内部细节泄漏到 API 层；
4. 它是"人类点这个按钮时发生的**全部事情**"的忠实封装，符合 §2 目标 2。

代价（诚实记录）：这条路径**不带 `doubleClick` 语义**（恒为 `false`）——配方书单双击无区别，可接受。

### 5.3 未命中/被拦下的处理

`dispatch` 返回 `false` 时按 §5 表逐条给原因（`inactive` / `not-visible` / `out-of-bounds` / `recipe-not-found`），
以 `{status:"error", reason:…}` 返回。**绝不静默**：这是本方案与"像素注入"相比最大的可用性优势。

---

## 6. 寻址与定位

### 6.1 两层寻址

1. **屏幕层**：`screen.children()` 递归（`AbstractContainerScreen` 的子控件、`AbstractRecipeBookScreen` 的开关按钮都在此）。
   这一层**不需要任何 mixin**——`children()` 与 `getMessage()/getX()/getVisible()` 全是公开 API。
2. **组件层**：配方书内部（tab 列表、搜索框、过滤按钮、配方按钮列表、翻页按钮、组件几何）
   需要在 `RecipeBookComponent` / `RecipeBookPage` / `AbstractRecipeBookScreen` 上取引用 → 用 **Mixin accessor**。

### 6.2 Mixin accessor 清单（沿用 `AbstractSignEditScreenAccessor` / `BookEditScreenAccessor` 的既有做法）

| Accessor | 取什么 | 用途 |
|---|---|---|
| `AbstractRecipeBookScreenAccessor` | `recipeBookComponent`、`recipeBookToggleButton` | 开关按钮的身份 + 组件引用 |
| `RecipeBookComponentAccessor` | `searchBox`、`filterButton`、`tabButtons`、`selectedTab`、`recipeBookPage`、`xOffset`、`yOffset`、`width`、`height` | 角色注册表与组件级 `mouseClicked` 的召唤 |
| `RecipeBookPageAccessor` | 配方按钮列表、forward/back 按钮、`lastClickedRecipe*` | 按 item 定位配方、翻页、断言 |

> 取不到引用时的**回退**（§6.3）只依赖公开 API，保证 accessor 因映射变动失效时不至于全盘不可用。

### 6.3 回退机制（明确标注，不静默）

| 角色 | 首选 | 回退 |
|---|---|---|
| `recipebook.toggle` | `widget.mouseClicked` | `recipeBookComponent.toggleVisibility()`（public） |
| `recipebook.tab` | `widget.mouseClicked` | `tabButton.onPress(input)` |
| `recipebook.filter` | `widget.mouseClicked` | `filterButton.onPress(input)` |
| `recipebook.search` | `mouseClicked` + `charTyped` | `searchBox.setValue(text)`（即 `mode:"set"`） |
| `recipebook.recipe` | `recipeBookComponent.mouseClicked` | **无回退**（§5.2 的三个副作用无处可替代，故必须走该入口） |
| `recipebook.next/prev-page` | `widget.mouseClicked` | `RecipeBookPage.nextPage()/previousPage()`（accessor） |

走回退时，`gui/click` 的返回里 `dispatched` 字段写明回退路径，且 `gui/widgets` 快照仍用于断言——
即"退化"是可见的，不是隐形的。

### 6.4 按 item 定位配方 + 自动翻页

```
1. 取当前页配方按钮列表（RecipeBookPageAccessor）
2. 对每个按钮 getDisplayStack() 的 itemId 与目标比较；命中 ⇒ 返回该按钮
3. 未命中 ⇒ 点一次 next-page（§5.1 的坐标从按钮自身几何来），等 1 tick，回到 2
4. 翻满一轮仍未命中 ⇒ {status:"error", reason:"recipe-not-found", pages 已翻:n}
```
**约束**：搜索框有内容时配方书本身就在过滤，通常第 1 页即可命中；自动翻页上限设 20 页防止死循环。
翻页后必须 `waitMs ≥ 50`（1 tick）再扫描，原因同 `容器拖拽API设计方案.md` §5.3（组件状态在 tick 里刷新）。

---

## 7. 实现方案（新增 `GuiApi.java`）

### 7.1 改动位置

- **新增** `vendor/stevex-template-1.21.11/.../name/modid/api/GuiApi.java`：注册 `gui/widgets` / `gui/click` / `gui/text`。
- **新增** `.../name/modid/mixin/` 下 §6.2 的三个 accessor。
- **新增** `.../name/modid/api/gui/WidgetRoles.java`：角色注册表（纯数据 + 匹配函数），与分发逻辑解耦。
- **修改** `src/mod/methods.js`：补三个方法的 schema（描述 / zh / paramDefs），与面板一致。
- **不动** `ContainerApi.java`（`container/text` 保留原行为，不删不改，避免破坏既有序列）。

### 7.2 分发核心（伪码）

```java
static Map<String,Object> click(Map<String,Object> p) {
    var mc = Minecraft.getInstance();
    var screen = mc.screen;
    if (screen == null) return err("no-screen");
    Widget hit = WidgetRoles.resolve(screen, p);            // §4.4 + §6.1，返回引用 + 命中的分发方式
    if (hit == null) return err("target-not-found");

    return switch (hit.kind()) {
        case WIDGET -> {                                     // Button/ImageButton/tab/filter/toggle
            double cx = hit.w().getX() + hit.w().getWidth()/2.0;
            double cy = hit.w().getY() + hit.w().getHeight()/2.0;
            boolean ok = hit.w().mouseClicked(
                new MouseButtonEvent(cx, cy, new MouseButtonInfo(button(p), 0)), doubleClick(p));
            yield ok ? ok("widget.mouseClicked", hit) : err(reasonOf(hit));
        }
        case RECIPE -> {                                     // §5.2 决策 (i)
            RecipeBookComponent c = hit.recipeBook();
            RecipeButton b = hit.recipeButton();
            double cx = b.getX() + b.getWidth()/2.0, cy = b.getY() + b.getHeight()/2.0;
            boolean ok = c.mouseClicked(new MouseButtonEvent(cx, cy, new MouseButtonInfo(0, 0)), false);
            yield ok ? ok("RecipeBookComponent.mouseClicked", hit) : err("recipe-click-rejected");
        }
        case EDITBOX -> { /* §4.3：mouseClicked + setFocused */ }
    };
}
```

**线程**：全部经 `mc.execute(() -> …)` 回到渲染线程执行（与 `ContainerApi.setText` 同约定），
因为 `children()` / `mouseClicked` 都是 UI 线程状态。

### 7.3 与既有 API 的关系

- `container/text` **不废弃**：它服务于"已经设好焦点的 EditBox"这类既有序列；
  `gui/text` 是它的超集（自带取焦 + 真实按键通道）。文档中标注推荐用 `gui/text`。
- `container/button` **不废弃**：它打的是 `menu.clickMenuButton`（附魔台/织布机），与控件 API 无重叠。
- `container/beacon` **不动**：它是 §3 准则下**合法**的语义通道（有回读），不需要改成点击。

---

## 8. 坑（实现期必须处理）

1. **`RecipeButton` 自身 `mouseClicked` 是哑的**（§5.2）——这是最容易写出"status:ok 但界面没动"的地方，
   必须是 `RecipeBookComponent.mouseClicked`。`dispatched` 字段就是为了让这种错误一眼可见。
2. **偏移**：配方书内部控件的屏幕坐标 = 组件坐标 + `xOffset/yOffset`；一律用**控件自身** `getX()/getY()` 取，
   不要自己算偏移，否则与 `isMouseOver` 的判定不一致（会得到 `out-of-bounds`）。
3. **`isActive()` 门控**：材料不足时配方按钮 `active=false`，点击返回 false 属**正确**行为；
   错误信息要区分 `inactive` 与 `not-found`，否则调用方会误判为 API 坏了。
4. **书收起是一条副作用，不是失败**：`tryPlaceRecipe` 成功 ⇒ 书自动 `setVisible(false)`，
   断言时不要把它当成"界面崩了"。
5. **tick 时序**：tab 切换、过滤切换、翻页之后，控件集合由 `RecipeBookComponent` 在 tick 里 `updateCollections` 刷新，
   `waitMs ≥ 50` 后再 `gui/widgets`；搜索同理（`responder` 同步调用，但配方列表重排跟着 tick 走）。
6. **搜索文本无服务端回读**（§3）**但可断言**：断言的是 `EditBox.getValue()`，即客户端权威状态。
   不要在文档/测试里写"搜索状态回读"——那是错的。
7. **`gui/widgets` 的耗时**：`children()` 递归 + 每控件一个 map，屏幕控件数十个级别，无性能问题；
   但不要在每 tick 调用（无必要）。

---

## 9. 使用示例

### 9.1 现有 API 的缺口（工作台配方书序列逐条）

| # | 用户要的动作 | 现有 API | 结论 |
|---|---|---|---|
| 1 | 打开工作台 | `key/use-once` | ✅ |
| 2 | 点配方按钮展开配方页 | 无控件寻址 | ❌ |
| 3 | 点"剑斧"分类 | 无 | ❌ |
| 4 | 点"仅显示可合成" | 无 | ❌ |
| 5 | 搜索栏输入"钻石剑" | `container/text` 仅在**已聚焦**时可用，且取不到搜索框焦点 | ❌（勉强半可用） |
| 6 | 点击"钻石剑"配方 | 无 | ❌ |
| 7 | 取出产物到物品栏 11 | `container/slot` | ✅ |

新 API 后 2–6 全部补齐，且**每步可断言**。

### 9.2 完整序列（工作台 → 钻石剑）

```jsonc
{"waitMs":3000}
{"method":"key/use-once","params":{}}                                  // 打开工作台
{"waitMs":600}
{"method":"gui/widgets","params":{}}                                   // 断言①：screen=CraftingScreen，有 recipebook.toggle

{"method":"gui/click","params":{"target":"recipebook.toggle"}}          // 展开配方页
{"waitMs":200}
{"method":"gui/widgets","params":{}}                                   // 断言②：recipebook.tab / search / filter 出现

{"method":"gui/click","params":{"target":"recipebook.tab","value":"combat"}}   // 剑斧类
{"waitMs":200}
{"method":"gui/widgets","params":{}}                                   // 断言③：该 tab selected=true，recipe 集合变化

{"method":"gui/click","params":{"target":"recipebook.filter"}}          // 仅显示可合成
{"waitMs":200}
{"method":"gui/widgets","params":{}}                                   // 断言④：filter 选中态翻转，recipe 全部 craftable=true

{"method":"gui/text","params":{"target":"recipebook.search","text":"钻石剑"}}
{"waitMs":200}
{"method":"gui/widgets","params":{}}                                   // 断言⑤：search.value=="钻石剑"，recipe 收窄到钻石剑

{"method":"gui/click","params":{"target":"recipebook.recipe","item":"minecraft:diamond_sword"}}
{"waitMs":200}
{"method":"container/get","params":{}}                                 // 断言⑥：3×3（menu 1..9）已放好材料
{"method":"gui/widgets","params":{}}                                   // 断言⑦：配方书 visible=false（自动收起）

{"method":"container/slot","params":{"slot":0,"clickType":0}}           // 取产物（第一次点击：拾起）
{"waitMs":200}
{"method":"container/slot","params":{"slot":12,"clickType":0}}          // 放入 Inventory 11（= 工作台 menu 12）
{"waitMs":200}
{"method":"container/get","params":{}}                                 // 断言⑧：menu 12 = 钻石剑
{"method":"container/close","params":{}}
```

### 9.3 逐步断言表（"界面真的变了"的证据）

| 步骤 | 断言字段 | 期望 | 若不符 ⇒ |
|---|---|---|---|
| ① 打开工作台 | `screen` | `CraftingScreen` | GUI 没开（`key/use-once` 没对准方块） |
| ② 点开关 | `recipebook.tab`/`search`/`filter` 出现且 `visible=true` | 出现 | 分发没走通（检查 `dispatched`） |
| ③ 点 tab | 命中 tab `selected=true`；`recipe:*` 集合变化 | 变化 | tab 值名不对（`value` 拼写） |
| ④ 点过滤 | `recipebook.filter.selected` 翻转；`recipe:*` 全 `craftable=true` | 翻转 | 回读走的是 `ClientRecipeBook`，翻转会**异步**一 tick 才到 |
| ⑤ 输入 | `recipebook.search.value == "钻石剑"`；`recipe:*` 收窄 | 一致 | `mode:"type"` 被 `filter` 拦下 ⇒ 换 `mode:"set"` |
| ⑥ 点配方 | `container/get` 的 menu 1..9 出现钻石/木棍 | 出现 | 按钮 `active=false`（材料不足）或派发错入口 |
| ⑦ 书收起 | 配方书控件 `visible=false` | 收起 | `tryPlaceRecipe` 返回 false（同⑥） |
| ⑧ 取产物 | menu 12 = 钻石剑 | 一致 | 物品栏 11 ≠ menu 12（编号见 `InventoryApi` 约定） |

> 编号提醒：`CraftingMenu` 里 0=产物、1..9=3×3、10..36=Inventory 9..35、37..45=hotbar，
> 故 **Inventory 11 = menu 12**；钻石剑竖直配方（`X/X/S`）在 menu 1、4 放钻石、menu 7 放木棍。

---

## 10. 测试

1. **冒烟**：面板 8090 `Call Mod Method` 跑 `gui/widgets`，看 `role` 回填是否正确。
2. **§9.2 全序列**：手动逐条执行 + 每步 `gui/widgets` 断言。
3. **门控正确性**：材料不足时点配方 ⇒ `{status:"error", reason:"inactive"}` 且界面不变（**不是**静默 ok）。
4. **非配方书屏幕**：在铁砧/信标/背包里 `gui/widgets` ⇒ 不报错，`role` 全为 `null`，`gui/click {target:"button",message:"…"}` 能点到通用按钮。
5. **回退路径**：临时改角色表强制走 §6.3 回退 ⇒ `dispatched` 字段如实反映，界面结果一致。
6. **自动翻页**：搜一个排在后面的配方（或清空搜索）⇒ 自动翻页命中，`pages` 计数合理。
7. **不回归**：既有 `container/*`、`key/*` 序列（铁砧改名、信标）逐条复跑。

---

## 11. 代码改动清单（**未实施**；搁置中，仅作重启时的工作面）

- [ ] 新增 `.../name/modid/api/GuiApi.java`（`gui/widgets`、`gui/click`、`gui/text`）。
- [ ] 新增 `.../name/modid/api/gui/WidgetRoles.java`（角色注册表 + 匹配 + item 定位 + 自动翻页）。
- [ ] 新增 `.../name/modid/mixin/AbstractRecipeBookScreenAccessor.java`。
- [ ] 新增 `.../name/modid/mixin/RecipeBookComponentAccessor.java`。
- [ ] 新增 `.../name/modid/mixin/RecipeBookPageAccessor.java`。
- [ ] 修改 `src/mod/methods.js`：三个方法 schema。
- [ ] 编译 `compileJava`，方法数同步 `test_mod_api.js` / `README` / `已实现内容.md`。
- [ ] 不动 `ContainerApi.java` 任何现有行为。

**待用户确认的定稿项**：方法名 `gui/widgets|click|text`；`recipebook.recipe` 走 `RecipeBookComponent.mouseClicked`（决策 (i)）；
`gui/text` 默认 `mode:"type"`；角色表第一版只做 `recipebook.*` + `button`/`widget` 兜底。

---

## 12. 实现期需核实的源码清单（1.21.11）

| 项 | 要确认什么 | 影响 |
|---|---|---|
| `RecipeBookComponent` 字段可见性 | `searchBox/filterButton/tabButtons/selectedTab/recipeBookPage/xOffset/yOffset/width/height` 哪些是 private | accessor 的必要范围 |
| `RecipeBookComponent.filterButton` 实际类型 | 是 `RecipeBookTabButton` 还是 `ImageButton` | 只影响 `selected` 断言字段能否取到；分发不受影响（都走 `mouseClicked`） |
| `RecipeBookPage` forward/back 按钮 | 构造时是否已绑定 `onPress`（`b -> nextPage()`） | 决定首选/回退（§6.3） |
| `RecipeBookPage` 配方按钮列表 | 字段名与元素类型（`List<RecipeButton>`？） | item 定位实现 |
| `AbstractRecipeBookScreen` | `recipeBookComponent` / `recipeBookToggleButton` 字段名与可见性 | accessor |
| `RecipeButton` | `getDisplayStack()` / `getCollection()` / `getCurrentRecipe()` 是否 public | item 定位实现 |
| `RecipeBookComponent.mouseClicked` | 完整分支（`isVisible`/`isSpectator` 门控、`tryPlaceRecipe` 返回值处理） | §5.2 与 §8 坑 4 的措辞 |
| 输入事件构造签名 | `MouseButtonEvent(double,double,MouseButtonInfo)`、`MouseButtonInfo(int,int)`、`CharacterEvent(int,int)` | 编译期即验证 |
| `Screen.charTyped` | 在 1.21.11 是否仍为 `charTyped(CharacterEvent)` | §4.3 `type` 通道 |

> 上述任一项与文档描述不符 ⇒ **回来改本节并同步 §5/§6**，再动代码。

---

## 13. 决策记录

| 决策 | 结论 | 依据 |
|---|---|---|
| **是否实施** | **搁置（2026-09-18）** | §0：驱动场景已被 `container/slot` 手工放料覆盖，成本不对称 |
| 通道选择 | 输入路径（`mouseClicked`），不用语义包 | §3：配方书关键状态无服务端回读 |
| 坐标 | API 不暴露，内部由控件自身几何现算 | 用户要求"直接触发指定的点击事件，更灵活"；§5.1 |
| 配方按钮入口 | `RecipeBookComponent.mouseClicked`（决策 (i)） | §5.2：`RecipeButton` 无 `onClick`，副作用在外层 |
| 文本通道 | 默认 `charTyped`，`setValue` 作兜底 | §4.3：走真实入口，长度/过滤门控生效 |
| 回读 | 不新增服务端回读；用 `gui/widgets` 客户端状态断言 | §3：无回读的项**不需要**回读 |
