# 查询、移动与采集合成：参数和错误契约

更新：2026-10-23。覆盖查询、移动、背包装备合成、世界交互、槽位及恢复等入口；fish 已纳入统一无参校验。本轮补充向前疾跑、goto 相对坐标、findblock 候选数量及钓鱼取消，不引入自动规划。背包／槽位接口正在并行演进，命令数量以共享目录和 command_contract.js 实际定义为准。正式调用仍为单条空白分隔字符串。

## 单一校验入口

`src/commands/command_contract.js` 保存参数数量、格式、范围和规范 usage。命令加载器将该 usage 用于 LLM、网页和生成文档；模块直接调用、已注册 skill 和正式 Agent 调用使用同一校验规则。分类说明来自 `catalog.js`，不额外向 LLM 注入完整验证代码或专用规划逻辑。

Agent 在入队、停止 Brain、清控制键之前校验；包括 `stop`、`dig stop` 的快速通道。非法动作不执行 handler，不停止当前动作，也不占动作队列。执行时依然保留原有前置条件与服务端结果确认。

## 参数规则

| 命令 | 数量及校验 |
| --- | --- |
| pos / hp / hunger / oxygen / gamemode / xp / time / weather / window | 严格 0 个参数 |
| inventory | 无参为分区摘要、不带槽号及完整组件；仅可选 slots，列非空玩家槽位引用，开关窗口不改编号 |
| slotinfo | 单个槽位引用 `inventory:1`、`hotbar:1` 或 `chest:1`。无窗口／越界明确拒绝；只读详情，满耐久／空属性省略，未知收纳内容不当空，嵌套截断明确 |
| stop / wake / respawn | 严格 0 个参数；wake／respawn 还须满足实际睡眠／死亡状态 |
| collect | 可选非负整数掉落物实体 ID；省略为最近目标，不按物品用途选择 |
| attack | 严格 1 个实体名称片段／ID；3 格及遮挡检查；必须指定目标，不默认攻击最近实体 |
| jump | 可选整数 durationMs=1..30000，默认200；到时或取消松键；仅首次按下尝试展翅，可能保持到落地再次起跳，不自动使用烟花；normal/glide 均拒绝 |
| right click | 0..1；hand/off-hand，默认 hand；一次世界右键。副手方块自身激活返回 HAND_NOT_SUPPORTED；活动使用须先 right up，窗口内须 slotclick |
| dig | 仅无参、stop 或 3 个安全整数值坐标；stop 大小写不敏感；无参不移动，坐标超距时受保护接近，再校验准星遮挡与目标状态；1.0 等整数值可用 |
| interactentity | 1..3；名称片段或 ID、normal/at、hand/off-hand；默认 normal／hand，不支持带空格名称。副手原生容器／交易开窗拒绝，不自动回退主手 |
| nearby | 0..2；第 1 位为单个过滤词，第 2 位为整数 radius 1..128；单独数字不会自动解释成半径，应写 nearby all 32 |
| findplayer / follow | 严格 1 个玩家名称／片段；原有大小写不敏感匹配不变，follow 仍要求精确用户名 |
| findblock | 搜索：精确名或 #tag、可选 radius=1..128（默认32）、count=1..16（默认1）；默认兼容单结果顶层字段，并统一返回 blocks/count/returned/found。按距离排序，床头尾不合并，不把候选上限当世界总数。tags/tag 目录模式不接受 radius/count，原截断／未知标签规则不变 |
| recipes | 严格 1 个精确物品注册名 |
| look | angle 严格 2 个原版度数；at 严格 3 个世界坐标；均支持 ~，出队计算。绝对 pitch -90..90，相对结果限幅；旧格式拒绝 |
| goto | 严格3个绝对十进制或 ~ 相对坐标；以出队时脚部坐标解析，再向下取整，原始数值和解析结果均须有限且在安全范围；不支持 ^，不增加自动安全策略 |
| move | 方向 forward/back/left/right/sprint，加可选整数 durationMs 1..30000；sprint=forward+sprint，结束／取消释放双键，非保证速度；move stop 无时长；载具拒绝 sprint |
| fish | 严格无参。默认会话超时30s，同时接受正式动作取消信号；复用上游钓鱼识别的私有事件桥，只转发自身浮漂。取消／超时阻断未来自动动作，清理最多约1s，无自身浮漂证据不盲目右键重抛；只声明收竿请求，不冒称拾取成功 |
| sneak | 严格 1 个 on/off/tap；大小写不敏感；tap 固定 150ms，已按住则不释放；保持状态与移动分离 |
| swing | 0..1；hand/off-hand，默认 hand；旧 right/left 拒绝；只发动画，不产生交互 |
| slotclick | 单个`inventory:1`／`chest:1`引用及可选left/right/shift-left（默认left）。玩家编号不变，容器引用不能代替玩家引用；shift-left要求空光标 |
| moveslot | 两端各一个`inventory:1`／`chest:1`引用，可选count=1..64。省略整堆／交换，指定数量只到空或同组件堆；容量不足拒绝，不丢溢出 |
| putaway | 单个源槽引用；目标自动选主包兼容／空槽，足够空间才开始；拒绝自动获取合成／交易结果 |
| transfer | deposit/withdraw、源槽，可选count=1..64（默认整堆）；存入用玩家引用，取出用储物槽引用，仅储物窗口 |
| wait | 严格 1 个数字串形式的正安全整数 physics ticks；不新增最大等待时长，仍受动作时限约束 |
| craft | 1..4；物品注册名、整数执行次数 1..64、auto/hand/table、正安全整数目录编号；编号不接受 0／前导 0 |
| equip | equip item 物品名 或 equip slot 槽号，可选 hand/head/torso/legs/feet/off-hand（默认 hand）；兼容直接物品名。先关窗口；来源为 `inventory:1..27` 或 `hotbar:1..9`。不同属性返回 EQUIP_SELECTION_REQUIRED，不装备；候选含精简组件及选择槽位 |
| hotbar | 严格 1 个整数索引 `1..9`；拒绝 2abc 等部分数字 |
| unequip | 0..1；目的地同 equip，默认 hand；空间不足拒绝，不自动扔物品 |
| right down | 0..1；hand/off-hand，默认 hand；右键持续使用手持物，仅确认请求，不保证进食、格挡或发射效果 |
| right up | 严格 0 个参数；松开当前使用，不区分主副手；仅确认请求 |
| drop | 精确注册名 [count]，或`slot <playerRef>` [count]；正安全整数数量。按名省略为首堆，显式数量也不混组件；按槽只操作所选堆，不跨槽补足 |

使用状态与运动分开：move/jump/look/goto/follow、wait 和查询保留使用；切换物品与互斥交互动作仍释放，stop／超时／取消保持完整安全停止。right up 按动作队列执行，不新增抢占。保持使用不代表格挡效果已确认，须结合方向及后续状态；覆盖摘要见 [背包与持续使用](api-validation/inventory-use-coverage.md)。

有限坐标和角度接受十进制科学记数法，不接受 0x／Infinity／NaN／部分数值字符串。duration、radius、times、ticks 和 recipeIndex 不接受指数或小数写法。枚举值大小写不敏感，会先规范为小写；注册名允许 minecraft: 前缀及大小写，不接受其他命名空间。没有引入通用引号／具名参数／多条命令支持。

## 统一失败格式

槽位引用见 [当前槽位规范](slot-references.md)。玩家引用开关窗口不变；储物与工作站引用只指当前界面。`transfer` 仍须提供带类别的源引用：存入用 `inventory:` 或 `hotbar:`，取出用当前储物槽。工作站材料使用玩家引用，`station take`／`station crafter` 使用工作站引用。选项索引、交易报价索引不是槽号。

动作反馈以 `status`、`confirmed`、`evidence` 为准：`requested` 仅发送请求，`completed` 表示该接口定义的效果被观测到，不等于整个任务完成。公开 right／interactentity 不再重复输出 requested／confirmation 别名。窗口动作先后读取服务端库存快照，只输出发生变化的槽位及光标；失败／取消仍保留已观测变化，最终快照未知时 changes=null，不能当成零变化。窗口搬运不会为了恢复光标而偷偷丢物；取消或中途错误后须先查询 window。

attack 返回 `hit:true|false|null`：true 为等待窗内目标实体的服务端 damage_event 且 sourceCauseId 指向自身；false 为检查拒绝、未发出攻击；null 为已发送但无可归因证据。不能仅凭 entityHurt 动画、挥手、目标消失或超时判定命中／未命中；不读取生物隐藏血量。多人并发、此前自己的其他伤害来源等仍可能共享归因，协议不提供本次攻击的独立事务号，不声称严格逐击因果确认。取消后若已有归因证据仍保留 hit=true，但动作整体可为失败。

装备选择：`equip item diamond_pickaxe`（也可省略 item）按精确名称匹配。属性相同则装备首槽，数量差异不构成歧义；附魔／耐久／NBT／组件不同则返回 `EQUIP_SELECTION_REQUIRED`、精简 `candidates`（带 `ref`） 和选择提示，不改变装备。LLM 使用例如 `equip slot inventory:4 off-hand` 明确选槽；只接受 `inventory:1..27` 或 `hotbar:1..9`，装备及副手槽不作为来源，开窗或光标持物时拒绝。槽位在执行时读取，不是锁定候选快照，不自动回退到其他物品；完全相同的属性仍可直接装备，不进行最优装备选择。

失败结果有 `ok:false`、`errorCode`、`error`、`output`，保留参数usage及已观察的部分效果。Brain历史统一保存JSON结果，包括confirmed/status/hit/changes/candidates/nextQuery等，不只取output；完全重复的查询JSON不重复发送，非重复文本放message，不截断结果。不会每轮自动查询状态。见 [槽位引用与失败反馈](slot-references.md)。

库存快照默认等待上限2000ms，支持取消、断线／窗口切换／重生检测，清理自有监听；启动时也替换上游craft/book使用的快照入口。最终核对使用独立有界等待，即使动作已取消也保留可观察的部分变化；无法核对为changes:null，不伪造空数组。此修复不代表所有原生工作站的经验／选项等待都能立即取消，执行器仍不提前释放未结束的原生操作。

`drop slot <slot> [count]`只接受 `inventory:1..27` 或 `hotbar:1..9`，须先关闭窗口，默认所选整堆，超量拒绝、不跨其他堆补足；按名drop保留原行为。两者仅确认库存减少，不保证世界掉落安全。

```json
{
  "ok": false,
  "errorCode": "INVALID_ARGUMENTS",
  "error": "[INVALID_ARGUMENTS] hand must be hand | off-hand. Usage: swing [hand|off-hand]",
  "output": "[INVALID_ARGUMENTS] hand must be hand | off-hand. Usage: swing [hand|off-hand]",
  "usage": "swing [hand|off-hand]",
  "argument": "hand"
}
```

参数错误统一为 INVALID_ARGUMENTS。其他代码说明：

| 类别 | 代码与语义 |
| --- | --- |
| 查询条件 | NO_WINDOW、UNKNOWN_BLOCK、UNKNOWN_ITEM、RECIPE_CATALOG_UNAVAILABLE |
| 目标／资源状态 | NO_TARGET、TARGET_NOT_LOADED、TARGET_CHANGED、TARGET_NOT_DIGGABLE、UNSUPPORTED_ITEM、NO_DROPPED_ITEMS、PLAYER_NOT_TRACKED |
| 寻路 | PATHFINDER_NOT_READY、PATH_NOT_FOUND、PATH_TIMEOUT、PATH_INTERRUPTED、PATH_NOT_REACHED；规划失败不等于数学上证明无路 |
| 动作确认 | DIG_NOT_CONFIRMED、PICKUP_NOT_CONFIRMED；原 craft 与内部方块／实体放置具体业务码保留；right click 普通交互请求可 ok:true、confirmed:false，须读 confirmation/status 区别真实效果 |
| 生命周期 | NOT_CONNECTED、DISCONNECTED、CANCELLED、ACTION_TIMEOUT；使用结构化超时标记，不靠英文消息猜测动作是否超时 |
| 未细分异常 | QUERY_FAILED／ACTION_FAILED；保留原错误，不冒充已知缺材料、无路径或零效果 |

找不到匹配玩家／方块／实体仍可 `ok:true` 加空结果，不把正常空查询改成异常。未知状态的 null／? 和饱和度 last known 语义不变。六个 gather 接口保留 action/status/confirmed/target/changes/evidence；非法参数确认尚未开始，changes=[]，craft 另保留 phase=preflight／effect=none。已经执行后的失败或取消仍可能有变化，不强行改成空数组。

合成候选列表、nextQuery、部分完成数量、已知库存差值和未知库存 null 均保留；外层超时覆盖错误码时也不丢内部部分效果。未知命令／空命令等分发前错误不属于某个已选中的接口；HTTP 命令体为空或不是字符串时统一返回 INVALID_ARGUMENTS，不触发 .trim 类型异常。

## 验收

- `test/command_contract.test.js` 新增 40 项：31 接口数量校验、非法数值／枚举／命名空间、规范化、skill 一致性、错误归一化幂等、保留部分效果、无效动作不影响调度、超时与 HTTP 输入类型。
- 全库 256/256 自动测试通过；旧 wait 替身测试改为检查结构化失败，而不是依赖原始异常抛出。原先使用非法空参数的调度替身改为合法调用，仍验证原有队列／取消行为。
- 原版 1.21.11 HTTP 实测 8 组通过：31 接口拒绝多余参数、15 组非法值、14 查询调用形式、空结果与未知注册项、非法 stop 不打断而合法 stop 可取消、合法基础动作与大写枚举、无效合成零消耗及 HAND 合成 4 木板、批量合成取消仍保留实际消耗与部分产出且无延迟继续执行。窗口未打开时按 NO_WINDOW 计为预期失败，不称为成功开窗。
- 批量取消时，逐次配方确认计数可能为 0，但最终服务端库存已确认产出 4 木板、消耗 1 原木；这两个指标不是同一概念，不能用 completedExecutions=0 断言没有效果。错误归一化保留该事实，并避免重复叠加 [CANCELLED] 前缀。
- 最终本地证据 `runs/api-acceptance-server/command-contract-1792692879291.json`，原始日志不提交。未重新跑全部游戏物理／物品变体，也未重新调用付费 LLM；本轮重点是契约和执行入口。测试后清空测试物品、停止动作并恢复朝向，未控制人工玩家。
- 未提交、未推送。逐项历史记录见 [API 验收清单](api-acceptance-checklist.md)，执行效果语义见 [采集合成结果契约](gather-build-contract.md)。
