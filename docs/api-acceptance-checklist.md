# API 分类清单与人工验收台账

更新：2026-10-25。按当前源码目录整理：**60 个命令（15 query、39 action、6 creative）**，另列 **22 个 HTTP 路由模板**（Brain 操作展开为两行）。分类来源为 `src/commands/catalog.js`，实际用法以运行版本的 `GET /api/commands` 为准。

本文仅保留当前接口、最新验收状态与待测项，不保留失效入口和逐轮调试流水。

分类与 LLM／网页共享目录一致：collect 属于移动控制；dig、right、attack、interactentity、swing、fish 属于世界交互与物品使用（swing 仅动画）；craft 属于背包、装备与合成；book 属于窗口与界面，say 单列通信，客户端管理单列且默认隐藏。look 为 angle／at／block，均返回当前交互射线反馈。

## 1. 状态与证据

- 2026-11-01：浏览器玩家标签投影、选中空槽、say 精简、黄心耗尽及 findplayer 旧实体防护专项修复；最大血量沿用 main 实现并补 1.21.11 实体包回归。自动覆盖不代表真实游戏／模型复测，说明及可填写 DeepSeek 密钥的脚本见 [本次反馈回归](api-validation/api-bug-feedback.md)。
- 同日维护者补测：真实 1.21.11＋Map-A/B/C＋Edge 通过标签寻路／终点同步、默认和药水／自定义最大血量、黄心 8→5→0、选中空槽、长 say、目标及观察者跨维度。补修首次登录注册表默认上限及 respawn 追踪世代，全库 **586/586**。真实 DeepSeek 未调用；残留吸收药水而黄心为零的边界仍为自动覆盖，详见上述记录。

- 有界任务循环后最新全库自动回归 **481/481**；下方458项为此前批次。本轮无真实模型／游戏复测，当前语义见[任务运行](task-runtime.md)，记录见[调度验收](api-validation/task-runtime-validation.md)。
- 2026-10-24更新：后续全库回归486/486；真实Minecraft 1.21.11＋DeepSeek完成单步／继续、暂停后与运行中追加、暂停、取消、调用／时长预算及角色隔离补测。首次模型重复inventory，补测成功报告背包；不作为稳定成功率。详见上述调度验收新增实测段。
- 同日复杂合成实测：8场景181次模型请求，严格完成5/8；7个可行场景中6个物品达成，缺料场景正确拒绝；两个多目标场景物品齐全但未报告DONE，一个红石场景未全达成。无craft执行故障；详见[新任务循环复杂合成](api-validation/task-crafting-validation-2026-10-24.md)。

- **实测**：当前入口有真实游戏记录，仅覆盖行内列明场景，不代表所有变体通过。
- **摘要通过**：沿用最近一次真实验收结论；原始过程日志已清理或归档，不代表本轮重新测试。
- **自动局部**：有参数、mock 或协议回归，不代表游戏效果已验证。
- **待测**：尚无对应成功路径的专项实测；已测接口的剩余边界列在末栏。
- 自动回归与真实游戏验收分开计数；统一挖掘已完成独立玩家 6 项游戏实测，未调用 LLM。其余接口不因此升级状态；新增边界见各行待测项。
- 疾跑／相对寻路／多候选搜索／钓鱼取消独立回归18/18、实机8/8，见 [专项记录](api-validation/motion-search-fish-validation.md)；各自通过范围不扩张为全部API实测。
- 最新工作区全库自动回归 **458/458**；固定玩家槽号／详情／搬运新语法完整实机 **16/16**，反馈／同步／工作站专项实机 **10/10**，见 [统一反馈与工作站验收](api-validation/window-feedback-validation.md)。Web 双 agent 背包／装备／容器 **9/9** 实机及附魔浮层刷新专项通过，见 [Web 验收](api-validation/web-inventory-validation.md)。视角 A→B→A 实机、本地模拟 provider 链路及1次真实 DeepSeek 输入输出／双 agent 隔离已测，见 [调试验收](api-validation/web-inspector-validation.md)。这些结果不代表所有工作站配方、异常网络或低 TPS 通过。
- 最新第5至8类复测见 [交互与复杂合成复测](api-validation/interaction-crafting-recheck.md)：交互／槽位16项、工作站10项、原生开窗7项、聊天书本6项、装备／鼠标／挖掘／钓鱼取消6项均通过。模型复杂合成独立计数，不以API场景数代替模型成绩；仍不代表全部窗口材料／交易组合、低TPS通过。

当前保留证据位于本地忽略目录，不随 Git 提交：

| 证据 | 最新覆盖 |
| --- | --- |
| `runs/api-acceptance-server/api-refinement-1792748834590.json`、`runs/latest-feedback-tests.txt` | 紧凑槽位语法16项实机及全库自动回归；固定玩家编号和物品详情保持 |
| `runs/api-acceptance-server/window-feedback-1792749007472.json` | 最新10项：指定槽丢弃、工作站反馈、快照超时恢复及真实结果格式核对 |
| `runs/api-acceptance-server/api-refinement-1792743700415.json` | 最新 12/12：攻击归因、副手实体交互、指定掉落物 ID、窗口搬运／点击及满箱拒绝 |
| `runs/api-acceptance-server/rightclick-1792730421694.json` | 右键内部路径历史实测 19/19：主副手放置、半砖、门床、船、刷怪蛋、潜行、喂食、弩等；不代表改名后的入口全量复测 |
| `runs/api-acceptance-server/window-native-1792739386986.json` | 新 right 入口原生开窗及动态目录 7/7；不代表窗口内操作组合全部实测 |
| `runs/api-acceptance-server/findblock-tags-1792738485631.json` | 原生标签世界搜索及方块状态 9/9；标签目录子命令仅自动覆盖 |
| `runs/api-acceptance-server/jump-duration-1792737309015.json` | 持续上浮、落地后起跳、取消松键 3/3 |
| `runs/sneak-acceptance/sneak-1792730751382.json` | 持续潜行与船／矿车上下车 12 组通过，含服务端潜行及乘客关系核对 |
| `runs/api-acceptance-server/current-commands-unit.log` | 最新全库自动回归；不替代真实游戏验收 |

旧验收仅保留有效结论，不再引用已删除 JSON。专项说明：[右键](api-validation/rightclick-validation-2026-10-23.md)、[潜行](api-validation/sneak-validation-2026-10-23.md)、[工作站](mineflayer-workstations.md)、[参数契约](command-contract.md)。专项文档中的历史轮次不覆盖本清单的最新状态。

## 2. 调用与判定

### 调用对象与 LLM 可见性

“默认可用”仅表示默认进入 Brain 工具目录，不表示测试已通过、调用必然成功或具备服务端权限；LLM 仍须主动选择命令，不会自动执行查询。

| 分类／接口 | 调用对象 | LLM 可见性（默认配置） |
| --- | --- | --- |
| 第 3 类：除 window 外的 14 个查询（含 slotinfo） | LLM、网页人工、外部程序 | **默认可用**：slotinfo window 仍要求实际窗口，不自动回退 |
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
- 玩家槽号始终固定：合成结果0、2×2合成1..4、装备5..8、主包9..35、快捷栏36..44、副手45；hotbar 的0..8仍是选择索引，不是槽号。开容器后 inventory 9..44 内部自动映射到原生窗口，不改变公开编号。window 作用域只代表容器／工作站自己的槽；关闭后该作用域明确拒绝，不回退。
- `inventory` 默认无槽号／完整组件；`inventory slots` 列固定玩家非空槽及选中的空快捷栏槽；`slotinfo inventory:9` 或 `slotinfo window:0` 查详情。`window` 中每一槽都有slotSpace，玩家区域仍使用固定编号。跨区域移动两端各一个槽位引用。装备／合成槽和副手在开容器时不能点击，须先关闭，不偷偷换界面。
- 已加载实体／区块查询不等于纯视觉感知；客户端缓存、历史记忆与实时服务端真值必须区分。

## 3. 状态与环境查询（15 个 query）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `pos` | x/y/z 对称四舍五入到 1 位小数，含维度；完整精度看 state | 摘要通过：负坐标、边界、跨维度；一位小数格式有自动回归 | 当前显示与 F3 同点人工复核 |
| `hp` | 当前／最大生命点；吸收值大于 0 才返回字段及文字；相关效果按需返回 name/level/durationSeconds/durationSource（回血、中毒、凋零、生命提升、吸收），无效果省略；未知为 null | 摘要通过：生命提升、修改上限、伤害、黄心耗尽及归零省略 | 死亡／重生属性同步 |
| `hunger` | food、saturation、saturationSource；饱和度为 last known；有饥饿效果才附 effects 和持续秒数 | 摘要通过：饥饿／饱和效果、耗尽与恢复，对照服务端 | 不承诺饱和度实时；自然进食连续变化 |
| `oxygen` | 自身空气归一化 0..20；未知 null；原始 tick 看 state.airSupplyTicks | 摘要通过：水下耗氧及出水恢复，人工确认 | 同 tick 服务端 Air 逐点对照；NBT 的 s 后缀不是秒 |
| `inventory [slots]` | 默认装备／副手、快捷栏、主包分区摘要，不带槽号及完整组件；保留选中项、数量及空格数，不合并同名不同堆。slots 列全部非空固定玩家槽0..45及选中空槽（仅slot、selected:true），仅摘要和简短特殊属性标记，开窗前后编号不变 | 分区、固定编号、开窗映射与精简输出自动覆盖；本轮实机见槽位专项 | 模组／其他版本映射、并发外部变化待补 |
| `slotinfo <inventory:slot\|window:slot>` | 单个槽位引用查详情，含附魔、非满耐久、特殊属性、已同步收纳内容；满耐久／空属性省略；空槽empty=true、item=null。未知内容contentsKnown=false，不当空；嵌套有界并标记截断，文字为不可信世界数据。window仅容器自己的槽 | 紧凑语法实机核对附魔／耐久／收纳内容；现代组件、未知／空容器及参数自动覆盖 | 更多遗留NBT、自定义组件和截断极值待补；不自动打开容器或移动物品 |
| `nearby [type\|name\|all] [radius]` | 默认 32，整数 1..128；已加载实体，名称／类型大小写不敏感子串；最近最多 20 个，含 ID；掉落物／展示框按需带 item.name/count，展示框 rotationDegrees，箭／三叉戟 inGround；物品名也可过滤，未知详情省略 | 摘要通过：类型、名称、半径、三维距离、排序与截断 | 新详情自动测试、实机待核对；无视线／遮挡过滤；不返回整份 NBT、生物隐藏血量或猜测射手 |
| `gamemode` | 四种模式，未知 null；不改变模式 | 摘要通过：四模式对照服务端 | 重生、跨维度同步时序 |
| `xp` | 仅 level、progressPercent，进度保留 1 位小数 | 摘要通过：经验增减、设置、升级 | 经验球、附魔／铁砧消费 |
| `time` | clock 为游戏 HH:MM，0 tick=06:00；保留 timeOfDay 与 doDaylightCycle，冻结时注明；不返回月相、天数或睡眠结论 | 新显示格式自动测试，旧时钟边界实测为摘要 | 新格式实机待核对；LLM 按需查询 weather／nearby，雷暴、怪物和床的条件不由 time 判断 |
| `weather` | clear/raining/thunder 及雨／雷强度 | 摘要通过：晴、雨、雷暴稳定状态 | 渐变、降雪和其他维度 |
| `window` | 当前窗口类型、ID，带作用域的物品／空槽清单；玩家槽固定 inventory 9..44，窗口自身为 window；只返回物品摘要，详情用 slotinfo。按需光标和工作站选项／进度 | 原开窗已有实测；新作用域及编号映射自动覆盖，本轮实机见专项 | 更多窗口、切换后历史槽引用待补；关闭后明确拒绝 |
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
| `attack <entityName\|id>` | 必须指定目标；精确同名优先，再片段取最近；3 格、遮挡及转向后目标复核，无寻路／敌我判断。hit=true 仅服务端 damage_event 目标及来源均匹配自己；未发包拒绝为 false，发出后 1000ms 无归因证据为 null（未知，不是未命中），不读取目标隐藏血量 | 新目标、归因、取消及未知反馈有自动回归；专项记录见 [接口优化验收](api-validation/api-refinement-validation.md) | 多人同时攻击、低 TPS、特殊实体待补；时间窗不能证明本次独占因果关系 |
| `interactentity <entityName\|id> [normal\|at] [hand\|off-hand]` | 辅助转向，默认 normal／hand；4.5 格及遮挡检查，无寻路重试；副手正确发包并保留潜行。主手 normal 村民／容器矿车原生开窗，副手该路径拒绝 | 实测：副手小麦 2→1、服务端牛 InLove>0；原主手上下车／喂食有摘要 | 移动目标、at 盔甲架、剪毛／交易实机待补；非开窗请求不等于效果完成 |
| `swing [hand\|off-hand]` | 默认 hand；只发手臂动画，不是鼠标键，无任何交互；旧 right/left 参数已移除 | 当前主副手参数实测：命令成功、背包与准星方块不变；未用动画包证明交互 | 其他客户端动画呈现仍可人工观察；不是攻击或破坏成功证据 |
| `fish` | 主手持竿自动抛竿／等咬钩／收竿；复用上游识别，独立事件会话只接受自身浮漂。默认会话30s，另受动作时限约束；取消停止事件转发，最多约1s清理，只在确认自身浮漂时尝试收竿，无盲目重抛。反馈 castSent/reelRequested/bobberId/cleanup；不保证捕获或库存增加 | 实测：水中取消、干地超时，均确认浮漂移除、后续命令可继续；归属过滤、迟到生成、断线／换物、迟到事件隔离自动覆盖 | 自然咬钩到实际收获的完整链路、多人同时钓鱼和低 TPS 待测；浮漂移除未知时先观察，不盲重试 |

## 6. 背包、装备与合成（5 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `equip item <itemName> [destination]` / `equip slot <slot> [destination]` / `equip <itemName> [destination]` | 名称精确匹配；默认 hand，可 head/torso/legs/feet/off-hand；槽号为关闭窗口后的背包／快捷栏 9..44。同名附魔、耐久、NBT 或组件不同返回 EQUIP_SELECTION_REQUIRED 及 candidates，不装备；相同属性忽略堆叠数量，取首槽；不选最优装备 | 当前equip slot实测：附魔钻石头盔装备／卸下与服务端组件一致；候选选择11项自动测试，原按名主副手有历史实测 | 同名多组件候选、满包及异常装备实机待补；候选是观测快照，执行时读取当前槽位，无自动回退 |
| `hotbar <slot>` | 整数 0..8，不是窗口槽号 | 实测：潜行夹具选槽；摘要通过：0/8、空手、非法参数 | 高延迟切换后使用 |
| `unequip [hand\|head\|torso\|legs\|feet\|off-hand]` | 默认 hand；满包拒绝，不自动丢物 | 摘要通过：盔甲／副手回包、空槽、满包无损拒绝 | 不可卸装备、并发库存变化 |
| `drop <itemName> [count]` / `drop slot <slot> [count]` | 按名默认首堆，显式数量只跨同组件堆；slot 使用固定玩家9..44，只操作所选堆、不跨槽补足，默认该堆全部 | 实测：指定组件堆部分丢弃、默认全部、超量拒绝及其他堆不变；按名路径自动回归 | 满包光标、网络异常；不保证掉落后安全或一定生成可拾取实体 |
| `craft <itemName> [times] [auto\|hand\|table] [recipeIndex]` | 默认 auto；times 为合成执行次数 1..64。一种当前可用方案直接做，多种返回 availableRecipes 供选择；不自动规划 | 最新DeepSeek复杂组126次调用：6个可行任务物品均达标，缺料正确BLOCKED；严格全流程6/7，红石任务做齐但36次预算内未DONE；动力铁轨384独立计数一致，无合成API失败。见本轮复测 | 重复查询／格式遵守／预算收尾需通用Agent改进；更多配方、取消及未知效果待测，不新增合成专用规划 |

## 7. 窗口与界面（6 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `slotclick <inventory:slot\|window:slot> [left\|right\|shift-left]` | 单个槽位引用，例如 inventory:9 或 window:0；默认 left，right 拆半／放一件，shift-left 快移。固定玩家编号不变，旧分离参数／裸槽号拒绝 | 紧凑语法实测：箱内拆半、放入、快移及光标反馈 | 更多工作站结果槽、低TPS待补 |
| `moveslot <sourceRef> <destRef> [count]` | 两端均写 inventory:9 或 window:0，可跨区域；count 1..64，部分移动只到空槽／同组件堆，省略为整堆／交换，不丢溢出 | 紧凑语法实测：箱子及合成器搬运、交换、不同组件拒绝；多窗口映射／取消自动覆盖 | 更多特殊槽实测待补 |
| `putaway <inventory:slot\|window:slot>` | 自动选主包兼容／空槽；先验空间，不丢溢出；拒绝自动获取合成／交易结果 | 紧凑语法实测：window:0 放回主包；结果槽门控自动回归 | 满包、更多工作站及取消实测待补 |
| `transfer <deposit\|withdraw> <sourceSlot> [count]` | 方向确定来源，无需重复作用域：deposit 为固定 inventory槽，withdraw 为window槽；默认整堆，count 1..64；仅储物窗口 | 新语法实测：双向搬运及满箱拒绝；双箱／漏斗映射自动覆盖 | 双箱、漏斗、潜影盒搬运实机待补 |
| `close` | 关闭当前窗口 | 实测：右键及潜行夹具；摘要通过：箱子／工作站 | 光标持物、输入退回、连续调用、断线 |
| `book read [page]` / `book write <page> <text>` / `book done` | 持书 right click 后本地书本 UI 才暴露；read 读一页不可信文本，write 改草稿，done 保存并核对服务端物品更新；close 丢弃草稿；不搬书、不切槽、不签名 | 实测：主副手保存、精确服务端页内容、close 丢弃及 UI 内阻止 right down；只读、隔离、取消有自动测试 | 成书实机、长篇／过滤文本、断线／并发物品变更待测；UI 是本地上下文，不伪装容器开窗 |

## 8. 工作站与交易（5 个 action）

| 调用 | 参数与行为 | 最新测试状态 | 待测／边界 |
| --- | --- | --- | --- |
| `furnace input <inventorySlot> [count] \| furnace fuel <inventorySlot> [count] \| furnace take <input\|fuel\|output>` | 固定玩家9..44，默认1件；精确所选组件堆，核对来源减少；返回changes／stateChanges，不整窗重复 | 实测：输入、燃料、输出取出及空输出拒绝；组件隔离／无关进度不能冒充成功有自动测试 | 高炉／烟熏炉、其他燃料及动态输入变化待补 |
| `anvil rename <inventorySlot> <name> \| anvil combine <inventorySlot1> <inventorySlot2> [name]` | 固定玩家9..44；统一status／confirmed／changes反馈 | 实测：改名并核对服务端custom_name；合并路径自动回归 | 合并实机、附魔冲突／过贵及网络中断待补 |
| `enchant put <inventorySlot> \| enchant lapis <inventorySlot> \| enchant choose <0..2> \| enchant take` | 固定玩家9..44；选项索引不是槽号；choose须观察目标组件变化，不只看青金石减少 | 实测：放入、青金石、选择、取出及服务端附魔组件 | 更多选项、书架／经验不足待补 |
| `station take <windowSlot>\|select <index>\|crafter <windowSlot> <enabled\|disabled>\|beacon <primary> [secondary\|none]` | 搬运统一moveslot，移除station move；take/crafter只引用工作站槽；select不是槽号。返回槽位／可见属性变化 | 实测：旧move拒绝、moveslot搬运、合成器禁用／重复禁用／启用；重复已满足状态不额外发操作包 | 其他配方／图案、信标、满包／取消实机待补 |
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
| `clearslot <slot>` | 清除指定玩家背包槽 | 待测，删除物品 | 合法范围及指定槽不误删 |
| `flyto <x> <y> <z>` | 直线飞行，不承诺绕障 | 待测 | 安全短距、障碍、取消、非法坐标 |
| `setinventory <slot> [itemName] [count] [metadata]` | 无物品名则清槽；数量默认 1、metadata 默认 0 | 待测，改物品 | 组件支持、数量／槽位、服务器拒绝 |
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

2026-10-25 持续使用补充：用户确认人工验收通过；`right down` 不回退另一只手，不可用工具不发包／不占使用锁，瞬时或未知请求不保留长按锁；冲突返回活动手与物品，客户端请求不冒充服务端效果。库存／使用自动测试 26/26；已装填弩射击按瞬时请求验收。见 [持续使用记录](api-validation/held-use-request-validation-2026-10-25.md)。

2026-10-25 写书赠书专项：`book write` 两页草稿、`book done` 精确页内容服务端确认、走近玩家后 `drop writable_book` 库存 -1 通过；人工截图核对第一页与两页结构。不据此宣称指定玩家拾取、签名成书或自主任务结束通过。`inventory` 第 24／25／26 步为三次独立模型选择，结果相同，后两次输入已含前次结果且未截断，保存确认与目标仍在；记录为冗余核验，不归因为 API 丢反馈、Continue 要求重查或上下文容量不足。未改 Brain／提示词，完整记录见 [写书、丢书与上下文验收](api-validation/book-gift-context-validation-2026-10-25.md)。

World 人工控制：左键姓名标签保留状态弹层；右键姓名标签选择在线受管 Agent；选中后右键高精度地图方块发送 goto 到其上方一格，左键发送 dig。拖动不触发；Deselect 退出。地图可能滞后，实际执行仍由 API 校验；不会自动调用 LLM。Low 是 BlueMap 原有 Resolution 默认值，没有另建渲染。离页／后台会取消选择与浏览器请求，不等同取消已提交的游戏动作。2026-10-25 Edge 实际点击移动／挖掘及桌面／手机截图通过，用户人工验收确认通过；本轮专项 34/34，完整回归另有并行修改相关失败，见 [验收记录](api-validation/look-and-world-controls.md)。

- 容器基础：slotclick → moveslot → putaway → transfer，逐项对照服务端库存和光标，覆盖满箱／满包／特殊组件。
- 持续交互：right down/right up 的未覆盖消耗品、fish、床右键入睡与 wake。
- 动态实体与载具：interactentity 的 at、attack 范围／伤害、乘坐时 move 的方向与停止、空中 jump 展翅。
- 生命周期与高风险：respawn、quit／重连、资源包、创造及命令方块，使用隔离角色和存档。
- 渲染／记忆：共享渲染长期切换、跨维度、各类 NBT 源查询，核对角色隔离与观测时间。

以后验收直接更新对应行的最新结论与剩余边界，不再在文件顶部追加旧版数量或重复流水。原始日志留本地忽略目录，提交前排除密钥、运行日志和个人配置。
