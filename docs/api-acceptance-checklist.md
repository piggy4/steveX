# API 分类清单与人工验收台账

依据当前 `GET /api/commands` 整理。槽位用法见 [槽位引用](slot-references.md)。

## 1. 验收口径

实测仅覆盖列出的场景；「摘要通过」表示有实机结论但未在本轮复测，「自动局部」不代表通过实机验收。其余情况见各行待测项。

## 2. 调用与判定

### 调用对象与 LLM 可见性

“默认可用”仅表示默认进入 Brain 工具目录，不表示测试已通过、调用必然成功或具备服务端权限；LLM 仍须主动选择命令，不会自动执行查询。

| 分类／接口 | 调用对象 | LLM 可见性（默认配置） |
| --- | --- | --- |
| 第 3 类：除 window 外的 14 个查询（含 slotinfo） | LLM、网页人工、外部程序 | **默认可用**：查询容器槽位要求对应窗口已打开，不自动回退 |
| 第 3 类：window | LLM、网页人工、外部程序 | **按需暴露**：存在容器／工作站窗口或本地书本 UI 时出现 |
| 第 4 类：移动控制全部 9 个 | LLM、网页人工、外部程序 | **默认可用** |
| 第 5 类：世界交互全部 6 个 | LLM、网页人工、外部程序 | **默认可用** |
| 第 6 类：背包装备合成全部 5 个 | LLM、网页人工、外部程序 | **默认可用** |
| 第 7 类：slotclick、moveslot、putaway | LLM、网页人工、外部程序 | **默认可用**：无容器窗口时可操作自身背包；本地书本 UI 打开时隐藏 |
| 第 7 类：close | LLM、网页人工、外部程序 | **按需暴露**：有容器／工作站窗口或本地书本 UI 时出现 |
| 第 7 类：transfer | LLM、网页人工、外部程序 | **按需暴露**：仅受支持的储物窗口（箱子、漏斗、潜影盒等），书本及工作站不暴露；执行时仍检查 |
| 第 7 类：book | LLM、网页人工、外部程序 | **按需暴露**：持书 right click 打开本地书本 UI；已签名书只显示 read／done，不显示 write |
| 第 8 类：furnace、anvil、enchant、trade | LLM、网页人工、外部程序 | **按需暴露**：仅当前窗口类型对应的命令；不是看到工作站就自动暴露 |
| 第 8 类：station | LLM、网页人工、外部程序 | **按需暴露**：仅受支持的工作站窗口；select 仅织布机／切石机，crafter／beacon 仅各自窗口 |
| 第 9 类：wake、respawn | LLM、网页人工、外部程序 | **按需暴露**：观测到睡眠时显示 wake，health<=0 时显示 respawn；执行时再次检查，白名单不绕过状态 |
| 第 10 类：say | LLM、网页人工、外部程序 | **默认可用**：可发送 /命令，是否获准由 Minecraft 服务端决定；实验角色不应授予 OP |
| 第 11 类：客户端管理全部 4 个 | 人工／后台；显式配置后也可供 LLM 使用 | **仅人工／后台使用（默认）**：不进入默认 Brain 目录 |
| 第 12 类：创造与特权全部 7 个 | 人工／后台；显式配置后也可供 LLM 使用 | **仅人工／后台使用（默认）**：包括 kind=action 的 setcommandblock |
| 第 13 类：全部 HTTP 路由及 WebSocket | 网页、人工脚本、后台／外部程序 | **仅人工／后台使用**：Brain 未接入通用 HTTP、共享渲染或记忆查询工具 |

- 这里的“按需”由当前窗口／书本或睡眠／死亡状态决定，不是模型申请权限。Brain 每轮刷新目录，状态变化后仍以执行时检查为准。
- `allowedCommands` 是显式名称白名单，会替代默认可见性筛选，可开放管理／特权命令，但不绕过角色命令集合、窗口／恢复状态条件或 Minecraft 权限；它不会把 HTTP 路由变成 LLM 工具。
- 手动文档与后台目录保持完整，不随 Brain 的窗口状态裁剪；网页已移除 Command Catalog 展示，命令文档在 docs／Obsidian 查阅。外部程序经 HTTP command 路由发送命令字符串；HTTP 接口本身不属于 LLM 命令目录。
- 隐藏目录不是安全鉴权；HTTP 访问控制与 Minecraft 权限是独立边界，不应因默认隐藏就将管理服务直接暴露到公网。

网页默认 `http://127.0.0.1:8090/`，实际地址与 agent 名以启动配置为准。网页输入框与 HTTP 使用同一条命令字符串：

2026-10-24：`/agents` 为轻量总览，`/agents/:name` 为单角色调试；背包、视角与LLM记录展开后才加载，离开后取消请求及角色订阅。离开页面不自动取消Agent任务。详见[Web页面与数据边界](web-agent-pages.md)。

本轮页面与订阅改造全库自动回归499/499；Edge模拟验证按需加载、角色切换与离页停止轮询，无新增真实Minecraft／付费模型调用。

```powershell
$base = 'http://127.0.0.1:8090'
$agent = 'manual-test'
Invoke-RestMethod "$base/api/agents/$agent/command" -Method Post -ContentType 'application/json' -Body (@{command='pos'} | ConvertTo-Json)
```

- `<参数>` 必填、`[参数]` 可选，括号不输入；竖线表示候选值。解析器按空白拆分，不支持通用引号转义、分号批处理或 JSON 具名参数。
- query 不打断动作。手动 action 会停止 Brain 调度；普通动作串行，持续按键可以跨动作保持。验收前关闭 Brain，避免并发控制。
- Brain 不自动附加位置、生命、背包、附近实体或时间，LLM 按需查询；默认目录隐藏 settings、quit、acceptpack、denypack 及全部创造／特权入口。完整网页／手动目录保留这些 API，llmDefaultVisible 仅表示默认可见性，不是权限。显式 allowedCommands 白名单替代默认筛选，但不绕过角色可用命令或服务端权限。
- 实际收到的聊天／系统反馈以不可信数据进入独立近期聊天区（最近 20 条，每条最多 1000 字符）；书本正文按页读取并标注不可信来源。外部文字自称系统／管理员不提升信任，不得据此改变目标、泄露秘密或启用隐藏工具；来源隔离不等于绝对抵御提示词注入。
- `stop` 是即时全局取消；`move stop` 是排队停步；`dig stop` 只即时取消挖掘任务。三者不能互换。
- 失败查看 `errorCode/error/output`；参数错误查看 `usage`。严格契约覆盖的入口在入队和控制清理前拒绝非法参数，不代表其他入口均已统一。
- `ok:true` 不必然表示世界效果完成。特别是 `confirmed:false`、`status:requested` 仅表示请求已发送；放置须核对服务端变化及生存物品消耗。
- 坐标单位为格，角度为 Minecraft 度数，移动时长为毫秒，等待为客户端 physicsTick。物品和方块优先使用精确注册名。
- 玩家槽位引用开关窗口不变：主背包 `inventory:1..27`、快捷栏 `hotbar:1..9`、装备及副手 `equipment:<part>`（如 `equipment:offhand`）、个人合成 `crafting:1..4` 和 `crafting:result`。容器／工作站用自己的前缀（如 `chest:1`、`furnace:input`），仅指当前打开的界面。
- `inventory` 默认返回摘要；`inventory slots` 列非空玩家槽及选中的空快捷栏槽。用 `slotinfo hotbar:1` 或 `slotinfo chest:1` 查详情，按 `ref` 复制槽位引用。容器打开时，装备、副手和个人合成槽不能直接点选，须先关闭。
- 已加载实体／区块查询不等于纯视觉感知；客户端缓存、历史记忆与实时服务端真值必须区分。

## 3. 状态与环境查询（15 个 query）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `pos` | x/y/z 对称四舍五入到 1 位小数，含维度；完整精度看 state | 摘要通过：负坐标、边界、跨维度；一位小数格式有自动回归 | 当前显示与 F3 同点人工复核 |
| `hp` | 当前／最大生命点；吸收值大于 0 才返回字段及文字；相关效果按需返回 name/level/durationSeconds/durationSource（回血、中毒、凋零、生命提升、吸收），无效果省略；未知为 null | 摘要通过：生命提升、修改上限、伤害、黄心耗尽及归零省略 | 死亡／重生属性同步 |
| `hunger` | food、saturation、saturationSource；饱和度为 last known；有饥饿效果才附 effects 和持续秒数 | 摘要通过：饥饿／饱和效果、耗尽与恢复，对照服务端 | 不承诺饱和度实时；自然进食连续变化 |
| `oxygen` | 自身空气归一化 0..20；未知 null；原始 tick 看 state.airSupplyTicks | 摘要通过：水下耗氧及出水恢复，人工确认 | 同 tick 服务端 Air 逐点对照；NBT 的 s 后缀不是秒 |
| `inventory [slots]` | 默认装备／副手、快捷栏、主包分区摘要，不带槽号及完整组件；保留选中项、数量及空格数，不合并同名不同堆。slots 列全部非空玩家槽位引用及选中空槽（含 `ref`、`selected:true`），仅摘要和简短特殊属性标记，开窗前后编号不变 | 实测：开窗前后玩家槽位引用一致 | 模组／其他版本映射、并发外部变化待补 |
| `slotinfo <ref>` | 单个槽位引用查详情，含附魔、非满耐久、特殊属性、已同步收纳内容；满耐久／空属性省略；空槽empty=true、item=null。未知内容contentsKnown=false，不当空；嵌套有界并标记截断，文字为不可信世界数据。容器详情只查询当前界面的槽位 | 紧凑语法实机核对附魔／耐久／收纳内容 | 更多遗留NBT、自定义组件和截断极值待补；不自动打开容器或移动物品 |
| `nearby [type\|name\|all] [radius]` | 默认 32，整数 1..128；已加载实体，名称／类型大小写不敏感子串；最近最多 20 个，含 ID；掉落物／展示框按需带 item.name/count，展示框 rotationDegrees，箭／三叉戟 inGround；物品名也可过滤，未知详情省略 | 摘要通过：类型、名称、半径、三维距离、排序与截断 | 新详情自动测试、实机待核对；无视线／遮挡过滤；不返回整份 NBT、生物隐藏血量或猜测射手 |
| `gamemode` | 四种模式，未知 null；不改变模式 | 摘要通过：四模式对照服务端 | 重生、跨维度同步时序 |
| `xp` | 仅 level、progressPercent，进度保留 1 位小数 | 摘要通过：经验增减、设置、升级 | 经验球、附魔／铁砧消费 |
| `time` | clock 为游戏 HH:MM，0 tick=06:00；保留 timeOfDay 与 doDaylightCycle，冻结时注明；不返回月相、天数或睡眠结论 | 新显示格式自动测试，旧时钟边界实测为摘要 | 新格式实机待核对；LLM 按需查询 weather／nearby，雷暴、怪物和床的条件不由 time 判断 |
| `weather` | clear/raining/thunder 及雨／雷强度 | 摘要通过：晴、雨、雷暴稳定状态 | 渐变、降雪和其他维度 |
| `window` | 当前窗口类型、ID，带作用域的物品／空槽清单；玩家槽用 `inventory:`／`hotbar:`，容器与工作站用各自的前缀；`openedFrom` 给出已知开窗来源；只返回物品摘要，详情用 slotinfo。按需光标和工作站选项／进度 | 实测：开窗来源与目标箱子坐标一致 | 更多窗口、切换后历史槽引用待补；关闭后明确拒绝 |
| `findplayer <name>` | 大小写不敏感；精确同名优先再片段；未追踪或失效实体省略坐标，已追踪坐标附当前维度 | 摘要通过：同名片段、离线、远处未追踪、正负坐标 | 跨维度 |
| `findblock <blockName\|#tag> [radius] [count]` / `findblock tags [filter]` / `findblock tag <#tag>` | radius 默认32、整数1..128；count 默认1、整数1..16。返回最近优先 blocks、count、returned、found；默认单个兼容原顶层字段。床头／床尾不合并，不判断用途或状态可用性；数量是返回上限，不是世界匹配总数。tags/tag 目录模式不接受 radius/count | 实测：三种原木及 axis、两种颜色床的头尾4候选、默认单个／空结果；见 [移动搜索钓鱼专项](api-validation/motion-search-fish-validation.md)；原标签状态9项证据保留 | 无视线过滤；标签未同步报错；高范围压力及其他版本待补 |
| `recipes <itemName>` | 一个精确物品名的全部合成配方；availabilityChecked=false，不按当前材料过滤，不含烧炼 | 摘要通过：新版 DeepSeek 基础及复杂合成任务中的配方查询 | 更多动态配方；不能当作当前可执行方案列表 |

## 4. 朝向、移动与动作控制（9 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `look angle <yaw> <pitch>` / `look at <x> <y> <z>` / `look block <x> <y> <z>` | angle 用原版度数；at 是精确空间点，不自动加半格；block 是整数网格，按首个支持形状框中心瞄准，~ 相对向下取整的脚部网格。按钮用状态相关选取框，其他用社区碰撞框，shapeSource 明示来源，无形状报错。三个模式均返回 4.5m 内首个方块／实体的 crosshair（命中点、距离、方块面），无命中 null；block 另有 targetMatched。ok 只代表转头，不移动／点击／寻找可见面 | 2026-10-25 同一真实存档：箱子开窗、按钮 powered 服务端确认、上下半砖及 at／angle 射线、石墙遮挡且右键不穿墙通过；自动覆盖实体遮挡、缺形状、超距及参数 | 更多无碰撞选取框／复杂形状、低 TPS 与移动中瞄准；不声称已具备完整原版 outline 数据库 |
| `move <forward\|back\|left\|right\|sprint\|stop> [durationMs]` | 默认1000ms，整数1..30000；sprint 同时按 forward+sprint，结束／取消释放双键，实际速度受游戏条件限制。stop 无时长、排队停步并保留潜行／持物使用；载具仍拒绝 sprint | 实测：900ms 步行约4.08格、疾跑约5.03格；取消释放双键且后续命令可执行；原潜行摘要保留 | 低饥饿／碰撞／水中疾跑、载具实际方向、低 TPS；不自动防跌落 |
| `sneak <on\|off\|tap>` | on 持续、off 松开、tap 短按 150ms；已保持时 tap 不松开 | 实测：12 组含服务端潜行、转向／移动保持、工作台放置、船矿车上下车 | 已保持时须 off 再 tap 才产生新边沿；长途路径、其他载具 |
| `jump [durationMs]` | 默认 200ms，整数 1..30000；保持空格直到到时／取消松开，不等落地。仅首次按下尝试展翅，不自动使用烟花 | 时长校验、保持中落地不重发展翅、取消松键自动覆盖；真实 3/3：上浮 Y=100→105.2、落地后重复起跳、主动取消松键；见 [持续空格验收](api-validation/jump-duration-validation.md) | 低 TPS、复杂流体、鞘翅实机仍待补；持续保持可能落地后再次起跳 |
| `stop` | 即时取消活动／排队动作、停 Brain、清控制及保持状态 | 实测：潜行释放；摘要通过：等待、挖掘、寻路、跟随中断 | 原生窗口操作取消 |
| `goto <x> <y> <z>` | 绝对坐标或 ~ 相对脚部坐标；出队时采样当前坐标，解析后向下取整；不支持 ^。采用已配置路径器策略，不等同 dig/collect 的受保护接近；完成核对实际到达 | 实测：goto ~3 ~ ~1 到达解析目标，并记录服务端 Pos；负坐标、混合坐标及排队后解析自动覆盖；原绕障摘要保留 | 气泡柱、加载边界、复杂地形；超时不等于证明无路 |
| `follow <playerName>` | 精确用户名不区分大小写，约 2 格动态跟随；返回 RUNNING | 摘要通过：移动、离线、远距离卸载后松键及重新跟随 | 切换目标；卸载后不会追旧坐标 |
| `wait <ticks>` | 正整数客户端 physicsTick，不是墙钟秒数 | 摘要通过：正常等待、非法参数与 stop；新右键测试中使用 | 低 TPS、真实断线清理 |
| `collect [entityId]` | 指定已加载掉落物 ID；省略为最近掉落。自身拾取事件与库存正增量才成功，禁破坏寻路，不按用途选物 | 实测：指定较远 diamond ID，自身拾取及库存 +2；原最近目标路径有摘要实测 | 抢拾、移动目标待补；部分拾取只报告实际增量 |

## 5. 世界交互与物品使用（6 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `dig` / `dig <x> <y> <z>` / `dig stop` | 无参只挖准星、不移动；坐标为安全整数，超距时受保护接近，不挖路／搭建，再瞄准校验遮挡与目标；stop 即时取消挖掘及接近，不取消无关移动 | 既有实测 6/6 保留；2026-10-25 新增出生点保护／OP 对照、两次拒绝后按真实服务端更新恢复缓存、关闭测试服保护后非 OP 同坐标挖掘成功；服务端方块核对通过，全库自动测试 532/532 | 其他保护插件、动态变化、更多工具／材质、低 TPS；不把本地空气预测当作成功，无服务端更新不猜测修复；不自动选工具或保证拾取 |
| `right <click\|down> [hand\|off-hand]` / `right up` | click 单次准星交互／放置；down 持续使用持物，非世界连点；up 无手参数，报告 hadActiveUse、released、实际 hand，未知为 null；本地松开不等于服务端效果确认 | 当前入口实测：木板放置与消耗、7种原生开窗、主副手书本、副手盾down／up及无活动重复up | 低TPS、其他物品及动态放置边界待补；盾牌本轮未重测减伤 |
| `attack <entityName\|id>` | 必须指定目标；精确同名优先，再片段取最近；3 格、遮挡及转向后目标复核，无寻路／敌我判断。hit=true 仅服务端 damage_event 目标及来源均匹配自己；未发包拒绝为 false，发出后 1000ms 无归因证据为 null（未知，不是未命中），不读取目标隐藏血量 | 实测：指定目标的伤害归因；无敌目标未误报命中 | 多人同时攻击、低 TPS、特殊实体待补；时间窗不能证明本次独占因果关系 |
| `interactentity <entityName\|id> [normal\|at] [hand\|off-hand]` | 辅助转向，默认 normal／hand；4.5 格及遮挡检查，无寻路重试；副手正确发包并保留潜行。主手 normal 村民／容器矿车原生开窗，副手该路径拒绝 | 实测：副手小麦 2→1、服务端牛 InLove>0；原主手上下车／喂食有摘要 | 移动目标、at 盔甲架、剪毛／交易实机待补；非开窗请求不等于效果完成 |
| `swing [hand\|off-hand]` | 默认 hand；只发手臂动画，不是鼠标键，无任何交互；旧 right/left 参数已移除 | 当前主副手参数实测：命令成功、背包与准星方块不变；未用动画包证明交互 | 其他客户端动画呈现仍可人工观察；不是攻击或破坏成功证据 |
| `fish` | 主手持竿自动抛竿／等咬钩／收竿；复用上游识别，独立事件会话只接受自身浮漂。默认会话30s，另受动作时限约束；取消停止事件转发，最多约1s清理，只在确认自身浮漂时尝试收竿，无盲目重抛。反馈 castSent/reelRequested/bobberId/cleanup；不保证捕获或库存增加 | 实测：水中取消、干地超时，均确认浮漂移除、后续命令可继续；归属过滤、迟到生成、断线／换物、迟到事件隔离自动覆盖 | 自然咬钩到实际收获的完整链路、多人同时钓鱼和低 TPS 待测；浮漂移除未知时先观察，不盲重试 |

## 6. 背包、装备与合成（5 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `equip item <itemName> [destination]` / `equip slot <slot> [destination]` / `equip <itemName> [destination]` | 名称精确匹配；默认 hand，可 head/torso/legs/feet/off-hand；先关窗口；来源为 `inventory:1..27` 或 `hotbar:1..9`。同名附魔、耐久、NBT 或组件不同返回 EQUIP_SELECTION_REQUIRED 及 candidates，不装备；相同属性忽略堆叠数量，取首槽；不选最优装备 | 当前equip slot实测：附魔钻石头盔装备／卸下与服务端组件一致 | 同名多组件候选、满包及异常装备实机待补；候选是观测快照，执行时读取当前槽位，无自动回退 |
| `hotbar <slot>` | 整数 `1..9`，用于选中快捷栏 | 新编号实测第 9 格 | 高延迟切换后使用 |
| `unequip [hand\|head\|torso\|legs\|feet\|off-hand]` | 默认 hand；满包拒绝，不自动丢物 | 摘要通过：盔甲／副手回包、空槽、满包无损拒绝 | 不可卸装备、并发库存变化 |
| `drop <itemName> [count]` / `drop slot <slot> [count]` | 按名默认首堆，显式数量只跨同组件堆；先关窗口，按槽使用 `inventory:1..27` 或 `hotbar:1..9`，只操作所选堆、不跨槽补足，默认该堆全部 | 实测：指定组件堆部分丢弃、默认全部、超量拒绝及其他堆不变 | 满包光标、网络异常；不保证掉落后安全或一定生成可拾取实体 |
| `craft <itemName> [times] [auto\|hand\|table] [recipeIndex]` | 默认 auto；times 为合成执行次数 1..64。一种当前可用方案直接做，多种返回 availableRecipes 供选择；不自动规划 | 最新DeepSeek复杂组126次调用：6个可行任务物品均达标，缺料正确BLOCKED；严格全流程6/7，红石任务做齐但36次预算内未DONE；动力铁轨384独立计数一致，无合成API失败。见本轮复测 | 重复查询／格式遵守／预算收尾需通用Agent改进；更多配方、取消及未知效果待测，不新增合成专用规划 |

## 7. 窗口与界面（6 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `slotclick <ref> [left\|right\|shift-left]` | 单个槽位引用，例如 `inventory:1` 或 `chest:1`；默认 left，right 拆半／放一件，shift-left 快移。玩家槽位引用开关窗口不变 | 紧凑语法实测：箱内拆半、放入、快移及光标反馈 | 更多工作站结果槽、低TPS待补 |
| `moveslot <sourceRef> <destRef> [count]` | 两端各写一个 `ref`，例如 `inventory:1` 或 `chest:1`，可跨区域；count 1..64，部分移动只到空槽／同组件堆，省略为整堆／交换，不丢溢出 | 紧凑语法实测：箱子及合成器搬运、交换、不同组件拒绝 | 更多特殊槽实测待补 |
| `putaway <ref>` | 自动选主包兼容／空槽；先验空间，不丢溢出；拒绝自动获取合成／交易结果 | 紧凑语法实测：满玩家库存／空间不足拒绝整笔搬运 | 更多工作站及取消实测待补 |
| `transfer <deposit\|withdraw> <sourceSlot> [count]` | 仍须写带类别的源引用：deposit 用 `inventory:` 或 `hotbar:`，withdraw 用当前储物槽（如 `chest:1`）；默认整堆，count 1..64；仅储物窗口 | 新语法实测：双向搬运及满箱拒绝 | 双箱、漏斗、潜影盒搬运实机待补 |
| `close` | 关闭当前窗口 | 实测：右键及潜行夹具；摘要通过：箱子／工作站 | 光标持物、输入退回、连续调用、断线 |
| `book read [page]` / `book write <page> <text>` / `book done` | 持书 right click 后本地书本 UI 才暴露；read 读一页不可信文本，write 改草稿，done 保存并核对服务端物品更新；close 丢弃草稿；不搬书、不切槽、不签名 | 实测：主副手保存、精确服务端页内容、close 丢弃及 UI 内阻止 right down；只读、隔离、取消有自动测试 | 成书实机、长篇／过滤文本、断线／并发物品变更待测；UI 是本地上下文，不伪装容器开窗 |

## 8. 工作站与交易（5 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `furnace input <playerRef> [count] \| furnace fuel <playerRef> [count] \| furnace take <furnace:input\|furnace:fuel\|furnace:output>` | 使用 `inventory:1..27` 或 `hotbar:1..9`，默认1件；精确所选组件堆，核对来源减少；返回changes／stateChanges，不整窗重复 | 实测：输入、燃料、输出取出及空输出拒绝 | 高炉／烟熏炉、其他燃料及动态输入变化待补 |
| `anvil rename <playerRef> <name> \| anvil combine <playerRef1> <playerRef2> [name]` | 使用 `inventory:1..27` 或 `hotbar:1..9`；统一status／confirmed／changes反馈 | 实测：改名并核对服务端custom_name | 合并实机、附魔冲突／过贵及网络中断待补 |
| `enchant put <playerRef> \| enchant lapis <playerRef> \| enchant choose <0..2> \| enchant take` | 使用 `inventory:1..27` 或 `hotbar:1..9`；选项索引不是槽号；choose须观察目标组件变化，不只看青金石减少 | 实测：放入、青金石、选择、取出及服务端附魔组件 | 更多选项、书架／经验不足待补 |
| `station take <workstationRef>\|select <index>\|crafter <crafter:1..9> <enabled\|disabled>\|beacon <primary> [secondary\|none]` | 搬运用 `moveslot`；take/crafter只引用工作站槽；select不是槽号。返回槽位／可见属性变化 | 实测：moveslot搬运、合成器禁用／重复禁用／启用；重复已满足状态不额外发操作包 | 其他配方／图案、信标、满包／取消实机待补 |
| `trade <index> [times]` | 索引从0起，默认1次，1..64；先开窗；核对交易产物净增，失败保留已观察变化 | 实测：0号报价两次交易、无效报价拒绝及服务端背包核对 | 双输入、折扣、涨价、缺货／补货、缺料、输入与产物同物品的净增口径待补 |

## 9. 生存与恢复（2 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `wake` | 仅睡眠时向 LLM 暴露，执行再次门控；返回请求，不冒称起床完成 | 非睡眠拒绝与目录变化自动回归；成功路径实机待测 | 先用床右键入睡，核对起床位置 |
| `respawn` | 仅观测 health<=0 时暴露，执行再次门控；返回请求，不冒称重生完成 | 非死亡拒绝与目录变化自动回归；成功路径实机待测，高风险 | 自动重生可能无需此命令；Brain 在线判断不因死亡直接停止，实机恢复仅隔离存档 |

## 10. 通信（1 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `say <message>` | 聊天或任意 /命令，含 OP 命令；权限交给原版服务器，私聊用 say /tell 玩家 文本。发送不等于执行／送达 | 实测：权限拒绝、命令成功、自己及其他玩家发言、私聊；实际收到的反馈进入独立不可信聊天区 | 不保证服务端发送所有反馈；模型提示词注入攻击测试待做 |

## 11. 客户端管理（4 个 action）

调用对象：人工／后台。LLM 默认隐藏；仅显式 allowedCommands 可开放，不因此取得服务器权限。

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `settings <key> <value>` | 布尔／数字／文本类型转换，修改客户端设置 | 待测 | key 合法性与服务端实际效果；不要任意改正式角色 |
| `quit [reason]` | 默认退出原因，断开本角色 | 命令专项待测 | 动作清理、状态及重连；不能用 HTTP 断线代验 |
| `acceptpack` | 接受服务端资源包请求 | 待测，有下载副作用 | 仅可信包，回执、失败及缓存 |
| `denypack` | 拒绝资源包 | 待测 | 可选／强制包；强制拒绝可能被踢 |

## 12. 创造与特权工具（7 个入口）

调用对象：人工／后台。LLM 默认隐藏；显式开放后仍需实际模式／权限。

此分类含 1 个 action 与 6 个 creative，全部从 Brain 默认目录隐藏，包括 kind=action 的 setcommandblock。显式 allowedCommands 可选择开放，仍需服务端权限／模式；目录可见性不是权限控制。

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `setcommandblock <x> <y> <z> <command>` | 修改已有命令方块；须对应权限／模式，不负责放置 | 待测，高风险 | 原生参数适配、服务端权限与实际内容 |
| `clearinventory` | 清空角色背包 | 待测，删除物品 | 专用测试角色核对装备／副手和残留 |
| `clearslot <playerRef>` | 清除指定玩家背包槽 | 待测，删除物品 | 合法范围及指定槽不误删 |
| `flyto <x> <y> <z>` | 直线飞行，不承诺绕障 | 待测 | 安全短距、障碍、取消、非法坐标 |
| `setinventory <playerRef> [itemName] [count] [metadata]` | 无物品名则清槽；数量默认 1、metadata 默认 0 | 待测，改物品 | 组件支持、数量／槽位、服务器拒绝 |
| `startfly` | 开启创造飞行／零重力 | 专项待测 | 模式权限、实际浮空与后续恢复 |
| `stopfly` | 恢复重力 | 专项待测，有坠落风险 | 安全地面、落地和模式切换 |

## 13. HTTP 与共享渲染／记忆

本节所有路由及下述 WebSocket **仅供人工／后台／外部程序调用，未作为 Brain 工具暴露**。读取 state、渲染结果或历史记忆不等于这些内容已自动提供给 LLM；经 command 路由发出的命令另按第 2 节区分可见性。

`:name` 为配置中的 agent 名。这里的状态仅针对路由本身，不把命令 handler 的通过自动算作整条 HTTP 链路通过。

| 方法与路径 | 调用与返回边界 | 最新测试状态／待测 |
| --- | --- | --- |
| `POST /api/agents/:name/planner/message` | 仅人工Web／后台，不向LLM暴露；message至4000字符，mode=once／continuous；operation=new清历史或append保留目标／历史／累计预算，可传taskId防过期操作；默认32次／300秒 | HTTP与Edge模拟通过；本轮无真实模型／游戏验收；见[任务运行](task-runtime.md) |
| `POST /api/agents/:name/planner/control` | 仅人工Web／后台，不向LLM暴露；operation=continue／pause／cancel／complete／block／wait；可传mode与taskId，继续不重置预算；完成是人工声明 | 自动生命周期、取消、预算及隔离覆盖；Edge核对继续／人工完成／预算耗尽，真实任务待测 |
| `GET /api/status` | 默认完整角色状态；`view=summary` 为轻量摘要，`agent=<name>` 限定单角色；无 body | 摘要／角色过滤自动测试，Edge页面路由与按需请求通过；真实掉线恢复待扩展 |
| `GET /api/commands` | 当前 name/kind/category/description/usage 目录 | 目录自动校验；当前 60 入口的运行服务核对待重载；不是完整参数 schema |
| `GET /api/skills` | 局部 skillRegistry | 专项待测；不代替完整命令目录 |
| `GET /api/config/environment` | 读取环境配置文件，不是完整合并配置 | 待测；可能含密钥，禁止记录完整响应 |
| `POST /api/config/environment` | `{"config":{...}}`，约 512KiB 上限；写文件，不自动 reload | 待测；备份并避免覆盖密钥 |
| `POST /api/reload` | 重载配置，可断开原角色／渲染服务 | 摘要通过：重载后重连；配置失败恢复待测 |
| `POST /api/agents/:name/connect` | 按配置连接；ok 不代表已 spawn | 摘要通过：重载后重连；重复连接及未知角色待测 |
| `POST /api/agents/:name/disconnect` | 断开指定角色 | 摘要通过；运行中取消／写盘排空待测 |
| `POST /api/agents/:name/command` | `{"command":"pos"}`；失败通常 HTTP 400 | 实测：右键 19 组及潜行 12 组经正式 HTTP 入口 |
| `POST /api/world-map/command` | 仅人工 World；`{uuid,mapId,action:"goto"或"dig",position:{x,y,z}}`；校验在线受管 UUID、地图、维度、整数范围；goto 到方块上方 y+1，复用常规命令执行层；不向 LLM 暴露 | 自动覆盖路由绑定／参数／跨维度拒绝；地图可能过时，不能绕过执行检查 |
| `GET /api/agents/:name/state` | 精确坐标、属性、控制、背包等客户端状态 | 实测：右键／潜行夹具读取；不是实时服务端全知状态 |
| `GET /api/agents/:name/inventory-preview` | 只读：固定玩家背包／快捷栏、装备副手、光标与当前容器；开窗装备标注 last-known；不自动开窗或读取后台真值 | 双真实 agent 9项实机及浮层刷新专项通过；多agent／重连／只读隔离已核对 |
| `GET /api/agents/:name/debug` | 默认全部；`section=vision` 不含LLM记录，`section=llm` 不含视角元数据，`section=all` 两者；ETag；不自动触发采集或模型 | 范围隔离、按需加载、离页取消自动测试及Edge模拟验证；历史真实DeepSeek证据另列，不算本轮复测 |
| `GET /api/agents/:name/observation/:frameId/rgb` | 仅该角色当前session最新RGB，不接受任意文件路径 | 真实Fabric A→B→A成功、错误角色URL拒绝；详情见[调试验收](api-validation/web-inspector-validation.md) |
| `GET /api/item-textures/:version/:name` | 本地导入材质，只允许受限版本／物品名；无图404，网页回退文字 | 1.21.11 本地图标与缺图回退已测；附魔闪光／药水染色／特殊模型未还原 |
| `GET /api/agents/:name/memory/containers` | actor/session 隔离的历史开窗记录，含来源和观测时间 | 摘要通过：开箱和落盘；历史不是当前库存，旧数据无损转换仍待完善 |
| `POST /api/agents/:name/brain/start` | 使用该角色在线状态及本地 LLM 配置 | 自动局部；摘要：缺 Key 拒绝；有效配置的路由专项待测，不读取密钥 |
| `POST /api/agents/:name/brain/stop` | 停 Brain 调度，不等同全局动作 stop | 自动局部；真实前后台动作归属待验 |
| `GET /api/renderer/status` | 关闭时 enabled=false，开启查询共享渲染服务 | 集成摘要通过、自动局部；当前运行实测待补 |
| `POST /api/agents/:name/observe` | 无必填 body；指定角色视角进入共享采集队列 | 摘要通过：alpha→beta→alpha、并发隔离；长期切换和跨维度待测 |
| `POST /api/agents/:name/nbt/query` | `{path,fields,filters?,limit?}`；path 为 entities/containers/block_entities/biomes/terrain；fields 必填非空数组，limit 默认 64、1..512 | 自动夹具／隔离／错误覆盖；各源真实查询待测；数据来自采集落盘，不是服务端透视 |

WebSocket 同端口默认仅推送轻量状态；发送 `{"type":"subscribe","agent":"角色名"}` 才接收该角色命令事件，agent=null取消；不再广播完整LLM文本，日志展开时走HTTP。角色订阅／取消及无原文广播已自动验证；长期断线恢复待测。不提供另一套命令执行接口，也不是权限鉴权机制。

## 14. 下一步待测顺序



World 人工控制：实测右键姓名标签选择 Agent，右键高精度地图方块移动、左键挖掘；拖动不触发。地图可滞后，执行时仍校验；离页取消浏览器请求，不取消已提交动作。

- 容器基础：真实 1.21.11 已验证目标仅余 2 格堆叠容量时搬 5 个、满箱搬入均拒绝且无变化；搬运中取消返回实际槽位／光标变化；中途关窗停止后续点击并标记结果未知。满背包及仅余 2 个堆叠容量时，`transfer withdraw` 和 `putaway` 实测均拒绝整笔搬运，服务端物品与客户端光标无变化。
- 切窗恢复：真实 DeepSeek 单轮通过；过期动作被拦截，重新查询后转回正确箱子。服务端确认目标箱收到 2 根、快捷栏剩 3 根，未观察到误写。不代表稳定成功率。
- 持续交互：right down/right up 的未覆盖消耗品、fish、床右键入睡与 wake。
- 动态实体与载具：interactentity 的 at、attack 范围／伤害、乘坐时 move 的方向与停止、空中 jump 展翅。
- 生命周期与高风险：respawn、quit／重连、资源包、创造及命令方块，使用隔离角色和存档。
- 渲染／记忆：共享渲染长期切换、跨维度、各类 NBT 源查询，核对角色隔离与观测时间。
