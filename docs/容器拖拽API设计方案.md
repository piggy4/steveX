# 容器拖拽 / 均分 API 设计方案（QuickCraft）

> 状态：**待审阅**（写代码前请先审本节）。
> 关联：`批量时序API设计方案.md`、`视觉api设计方案.md` §5.2（agent 容器编辑，`container/get/slot/button/close`）、
> 现有实现 `vendor/stevex-template-1.21.11/.../name/modid/api/ContainerApi.java` 与 `src/mod/methods.js`。

---

## 1. 背景与动机

现 API 的容器操作只有**单次点击**（`container/slot` 的 `clickType∈{PICKUP,QUICK_MOVE,SWAP,THROW,…}`）。
"把一整叠物品**均分**到 N 个格子"这类动作没有原语，只能靠十几到几十条 `container/slot` 逐 1 补齐，
既笨重又易错（LLM 手写 23 步）。我们讨论后确认：

- 该动作的**正确粒度不是"像素级鼠标拖动"**——MC 容器拖拽不靠鼠标像素回放实现；
- 游戏原生把它编码成**三段式 QuickCraft 点击序列**（begin → 逐格 add → finish），服务端按类型做分配；
- 因此新增能力 = 对容器菜单补一个 **QuickCraft（拖拽）相位发送**原语，由上层（LLM/时序脚本）用
  `begin → add×N → finish` 相位序列自行组装成任意手势。

参考锚点（1.21.11 服务端源码，decompile 可查）：
`AbstractContainerMenu.doClick` 的 `ClickType.QUICK_CRAFT` 分支与
`getQuickcraftType/getQuickcraftHeader/getQuickCraftPlaceCount/isValidQuickcraftType/resetQuickCraft`。

---

## 2. 目标 / 非目标

**目标**
1. 新增底层原语 `container/drag`：能发送 QuickCraft 任一相位（begin/add/finish），任选拖拽类型
   （均分 / 每格 1 / 创造克隆），与真实玩家手势一一对应。
2. 语义与 vanilla 服务端完全一致（同一份状态机），不给服务端加任何特判。

**非目标**
- 不做 OS/像素级鼠标 down/move/up 注入（脆弱、非游戏原生路径，见 §7）。
- 不提供 `drag-spread` 便捷层：均分/每格 1 等动作一律由调用方用 `begin → add×N → finish` 相位序列表达，
  避免在 mod 内维护第二套排程状态机；该便捷层能做的事（§6 即为等价序列）`container/drag` 已全部覆盖。
- 不做 `PICKUP_ALL`（双击拾取）等同容器手势（与拖拽无关，另行评估）。
- 不改动服务端/记忆侧任何逻辑。

---

## 3. 服务端协议（权威行为，实现必须逐条对齐）

### 3.1 三段式状态机

服务端在**每个容器菜单实例**上维护拖拽状态：`quickcraftStatus`（0 空闲 / 1 收集中 / 2 结算）、
`quickcraftType`、`quickcraftSlots`（**Set**，格子集合）。只吃 `ClickType.QUICK_CRAFT` 包：

| 相位 | 收的包 | 服务端做什么 |
|---|---|---|
| begin | button 低 2 位 = 0 | 校验**鼠标携带物非空**；读 type；`quickcraftSlots.clear()`；进入收集态 |
| add | button 低 2 位 = 1，`slot`=真实格 index | 把格子加入集合（须空槽或同类可合并、`mayPlace` 通过、type≠2 时数量 > 集合大小） |
| finish | button 低 2 位 = 2 | 按 type 分配，`resetQuickCraft()` |

**取消条件**（任一条 → 拖拽作废，`quickcraftSlots` 清空）：
- 拖拽中途收到任意**非 QUICK_CRAFT** 点击；
- begin 时鼠标为空；
- 相位顺序错乱（服务端只允许 0→0(begin)、1→1(add)、1→2(finish) 的合法迁移）。

### 3.2 按钮编码

`buttonNum = (type << 2) | phaseHeader`（`getQuickcraftType = mask>>2 & 3`，`getQuickcraftHeader = mask & 3`）。
此处 `buttonNum` 是**线上点击包**的数值；§4.1 的 API 以数字 `phase`（相位 0/1/2）与 `type` 两个参数送出，内部按上式组装，不暴露裸 `button`。

| type | 手势 | 语义 | 均分规则 | 线上包 buttonNum（begin/add/finish） |
|---|---|---|---|---|
| 0 | 左键拖 | **均分** | 每格 `⌊总数/格数⌋`，**余数留在鼠标** | 0 / 1 / 2 |
| 1 | 右键拖 | **每格放 1** | 每格 1 | 4 / 5 / 6 |
| 2 | 创造拖 | **克隆**（无限材料） | 每格 `maxStack` | 8 / 9 / 10 |

（type 2 仅 `player.hasInfiniteMaterials()` 合法，生存下 begin 即取消。）

### 3.3 结算规则（type 0，我们主要用）

伪码即服务端原逻辑：
```
placeCount = ⌊鼠标原始数量 / quickcraftSlots.size()⌋
remaining  = 鼠标原始数量
对 quickcraftSlots 每格：
    newCount = min(placeCount + 该格已有同物数量, maxStack)
    remaining -= (newCount - 已有)
该格 = 新数量
鼠标剩余 = remaining            // 余数留在鼠标，服务端不自动放下
```
要点：
- `quickcraftSlots` 是 Set → **同格重复 add 只算一次**；type 0 下每格均等，顺序无关。
- 只圈 1 格时服务端会退化成一次 PICKUP（整组/按 type 放），无需特判。
- 目标格**必须是空格或同类有空间**；不同物品类型不会合并。

---

## 4. API 契约

在现有 `container/slot` 同侧新增一个方法（分类 `container`）。

### 4.1 底层：`container/drag`

逐相位发送，与真实手势逐帧等价。`container/drag` 是**纯原语：只把相位包发到当前打开的容器，
不负责取物/放回物品**——拾取源格、余数落格等一律由调用方用 `container/slot` 完成。参数：

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `phase` | 是 | int | 相位：0=begin / 1=add / 2=finish |
| `slot` | add 必填 | int | 目标槽 index（`container/get` 的 `slot`）；begin/finish 省略该字段 |
| `type` | 否 | int | 0=均分（默认）/1=每格 1/2=创造克隆 |

数值与 §3.2 协议一一对应，服务端收到时直接组装 `buttonNum = (type << 2) | phase`。
返回 `{status:"ok"}`；phase 超出 0..2、type 超出 0..2 或 slot 越界均返回错误。

**前置条件与约束（调用方负责）**：
- **begin 前鼠标必须已携带目标物品**——`container/drag` 不负责取物；来源可为上一动作遗留的
  `carriedItem`，或先用 `container/slot {clickType:0}` 拾取源格（§6）；
- 同一容器、同一拖拽内 begin→add×N→finish 顺序，期间**不得夹任何其它点击方法**；
- 相邻两条拖拽消息之间**至少间隔 1 tick**（原因见 §5 坑 1；批量时序脚本的 `waitMs` 天然满足）。

一个拖拽动作在调用方侧即为一条完整相位序列，例如"把源格整叠均分到 N 格"：
`PICKUP(src) → drag(begin) → drag(add,t1) → … → drag(add,tN) → drag(finish)`（§6 给完整示例）。
不在 mod 内新增便捷封装——所有上层手势都落到这条序列上。

---

## 5. 实现方案（`ContainerApi`）

### 5.1 改动位置

- `vendor/stevex-template-1.21.11/.../name/modid/api/ContainerApi.java`：handlers 注册 `container/drag`；
- `src/mod/methods.js`：补 schema 声明（描述/zh/paramDefs），供面板与文档一致。

### 5.2 相位发送核心（与现有 `slotClick` 同路径）

```java
handlers.put("container/drag", params -> drag(params));

private static void sendQuickCraftPhase(Minecraft mc, int type, int phase, int slot) {
    int buttonNum = (type << 2) | phase;               // type:0均分 1每格1 2克隆
    mc.gameMode.handleInventoryMouseClick(
        mc.player.containerMenu.containerId, slot, buttonNum,
        ClickType.QUICK_CRAFT, mc.player);
}
```

`drag(params)` 校验并透传：`phase` 须 0..2（0=begin/1=add/2=finish），`type` 缺省 0；
begin/finish 未带 `slot` 时传 -999（服务端忽略该值）。`sendQuickCraftPhase` 即按 `(type<<2)|phase` 出包。
在 `CLICK_TYPES` 旁补 `QUICK_CRAFT` 常量；现有 `slotClick` 不变。

### 5.3 坑 1（必须处理）：stateId 需逐帧推进

每次点击包都带 `menu.getStateId()`。真人拖拽天然逐帧发（按下帧 begin、划过每格一帧 add、松开帧 finish），
每次都能先收到服务端回执刷新 stateId。若**同帧连发**多个包，后发者带的是旧 stateId，
可能被判"丢点击"而静默丢弃。因此把"≥1 tick 间隔"写进契约（§4.1），由批量时序脚本的 `waitMs`
保证——调用方逐条发 begin/add×N/finish 时，每条之间 `waitMs ≥ 50`。

### 5.4 坑 2：相位与槽位合法性

- `add` 的 `slot` 必须是真实 index（服务端 `slots.get(slotIndex)`，-999 会越界）；
- `begin/finish` 的 slot 服务端忽略，统一传 -999；
- 拖拽中途夹任何其它点击（`container/slot` 等）会 cancel——调用方须把整段拖拽作为独占序列执行，
  不夹其它方法、不并发其它拖拽。

---

## 6. 使用示例

63 铁锭在 `src`，均分到 t1/t2/t3（先 `container/get` 拿真实 index）：

```
container/slot {slot: src, clickType: 0}              // PICKUP 取整叠上鼠标（begin 前置条件）
container/drag {phase:0, type:0}                      // phase0=begin，type0 均分
container/drag {phase:1, type:0, slot:t1}             // phase1=add；每条间隔 ≥1 tick（waitMs≥50）
container/drag {phase:1, type:0, slot:t2}
container/drag {phase:1, type:0, slot:t3}
container/drag {phase:2, type:0}                      // phase2=finish，结算
```

结果：三格各 `⌊63/3⌋=21`，鼠标空。若总量不被整除（如 64），每格 21、余 1 留在鼠标
（可再补一条 `container/slot` PICKUP/右键把鼠标余数放到任意格）。

等价地，"每格放 1"用 `type:1`（右键拖）、创造克隆用 `type:2`；一个手势 = PICKUP + 4~5 条
`container/drag`。均分、每格 1、克隆三类动作都落在同一条相位序列上，因此无需独立的便捷方法。

---

## 7. 不做"像素鼠标注入"的原因

MC 容器拖拽分发在**协议/容器菜单层**，GUI 的像素拖动只是把
"按在哪、划过哪些格、在哪松"翻译成上述三段包的前端；注入 OS down/move/up 需逐帧把光标坐标喂给
`AbstractContainerScreen` 的命中判定，依赖 GUI 内部坐标与帧时机，脆弱且与游戏实际机制脱节，
故不采纳为第一版。若后续确需"任意玩家手势"，再评估二次封装。

---

## 8. 测试（人工 + curl 冒烟）

1. **均分整除**：src 放 63，targets 三空格，按 §6 序列执行 → 三格 21、鼠标空。
2. **均分有余数**：src 放 64 → 三格 21、余 1 在鼠标（补 `container/slot` PICKUP 放走）。
3. **每格 1**：src 放 5 → `container/drag` type:1 对两个目标格 add → 两格各 1、鼠标 3。
4. **中途打断**：begin 后插一条 `container/slot` → 后续 add/finish 无效（服务端已 cancel，回归正常点击）。
5. **时序安全**：两条 `container/drag` 同帧连发（waitMs < 1 tick）会被服务端静默丢弃；≥1 tick 逐条则结果正确。
6. 面板 8090 `Call Mod Method` 冒烟 `container/drag`。

---

## 9. 代码改动清单（审阅确认范围）

- [x] `stevex-template/.../name/modid/api/ContainerApi.java`：注册 `container/drag`（`QUICK_CRAFT` 常量在 `CLICK_TYPES` 已存在，无需新增）。
- [x] `steveX/src/mod/methods.js`：schema 声明 `container/drag`（描述/zh/paramDefs）。
- [x] 上述两项已于 2026-09-07 实施，`compileJava` 通过（方法数 48→49，已同步 test_mod_api.js / README / 已实现内容.md）。

**已定稿**：方法名 `container/drag`；参数全用数字——`phase` 0=begin/1=add/2=finish，`type`
0=均分/1=每格 1/2=克隆，begin/finish 省略 `slot`，移除 `button`；`container/drag` 为纯相位原语、
不负责取物，取物是调用方前置步骤（§4.1）。
