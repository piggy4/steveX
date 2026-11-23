# Mineflayer 特殊交互 API

本轮在 Mineflayer 身体端补全工作站交互，不依赖 Fabric 渲染端、DSH 或额外 jar。环境：Minecraft 1.21.11、Mineflayer 4.39.0、prismarine-windows 2.10.0；未变更依赖版本。

## 调用入口

- `GET /api/commands`：返回命令名称、类型、说明与用法。
- `POST /api/agents/:name/command`：执行命令，JSON 请求体为 `{"command":"window"}`。
- `window`：只读查询当前窗口，返回结构化 `window` 字段及 JSON 文本 `output`，不暂停 Brain。
- 写操作沿用 Agent 的动作仲裁；所有窗口均属于指定 Mineflayer agent，而非共享 Fabric 观察者。

先用 `look block x y z` 瞄准，再用 `right click` 打开。`window` 返回逐槽摘要和 `ref`、可用选项及已知开窗来源；例如用 `slotinfo inventory:1` 或 `slotinfo loom:banner` 查详情。工作站材料用 `inventory:1..27` 或 `hotbar:1..9`，开关窗口不改引用。完整规则见 [槽位引用](slot-references.md)。

## 命令

| 场景 | 调用 | 说明 |
| --- | --- | --- |
| 附魔 | `right click` | 打开附魔台 |
| 附魔 | `enchant put <背包槽>`、`enchant lapis <背包槽>` | 放入目标与青金石 |
| 附魔 | `enchant choose <0..2>`、`enchant take` | 选择可用附魔并取回目标；选项见 `window.enchantments` |
| 铁砧 | `right click` | 支持正常、开裂、损坏铁砧 |
| 铁砧 | `anvil rename <背包槽> <名称>` | 改名，名称长度 1..35 |
| 铁砧 | `anvil combine <背包槽1> <背包槽2> [名称]` | 合并／修复两个物品，消耗材料和经验 |
| 熔炉 | `right click` | 普通熔炉、烟熏炉、高炉 |
| 熔炉 | `furnace input <背包槽> [数量]`、`furnace fuel <背包槽> [数量]` | 默认各放 1 个 |
| 熔炉 | `furnace take furnace:input` / `furnace take furnace:fuel` / `furnace take furnace:output` | 取回相应槽位物品 |
| 其他工作站 | `right click` | 类型见下表 |
| 通用搬运 | `moveslot inventory:1 loom:banner [数量]` | 两端独立槽位引用；默认整组，内部转换固定玩家编号 |
| 通用取出 | `station take <工作站槽>` | 取回输入或输出槽；不接受背包槽 |
| 切石／织布 | `station select <索引>` | 选择配方／图案按钮；需等待服务端选中属性及输出物品 |
| 合成器 | `station crafter <crafter:1..9> enabled` / `disabled` | 切换空槽开关，光标也必须为空；不负责红石触发 |
| 信标 | `station beacon <主效果> [副效果或none]` | 需有效金字塔及`beacon:payment` 中的支付物；校验层级 |
| 村民 | `right click`、`window`、`trade <索引> [次数]` | 对准村民后打开，读取真实报价；拒绝不存在、禁用、超剩余次数的交易 |
| 结束 | `close` | 关闭当前窗口 |

信标主效果：`speed`、`haste`、`resistance`、`jump_boost`、`strength`；副效果为 `none`、`regeneration` 或与主效果相同。服务端仍负责最终规则校验。

## 通用工作站槽位（1.21.11）

| 类型 | 工作站类型（非命令参数） | 工作站槽位 |
| --- | --- | --- |
| 酿造台 | `brewing` | `brewing:bottle1`／`bottle2`／`bottle3`、`brewing:ingredient`、`brewing:fuel` |
| 织布机 | `loom` | `loom:banner`、`loom:dye`、`loom:pattern`、`loom:result` |
| 切石机 | `stonecutter` | `stonecutter:input`、`stonecutter:result` |
| 锻造台 | `smithing` | `smithing:template`、`smithing:base`、`smithing:addition`、`smithing:result` |
| 砂轮 | `grindstone` | `grindstone:top`、`grindstone:bottom`、`grindstone:result` |
| 制图台 | `cartography` | `cartography:map`、`cartography:material`、`cartography:result` |
| 信标 | `beacon` | `beacon:payment` |
| 合成器 | `crafter` | `crafter:1..9`、只读 `crafter:preview` |

例如制图：若地图和纸在主背包前两格，执行 `moveslot inventory:1 cartography:map 1` 和 `moveslot inventory:2 cartography:material 1`。确认 `cartography:result` 有结果后，执行 `station take cartography:result`。

`window.brewing` 提供剩余酿造 tick 和燃料次数；`window.furnace` 提供燃烧／进度 tick；`window.disabledSlots` 提供合成器槽位开关。tick 字段不是墙钟倒计时，进程卡顿时不要据此假设真实完成时间。

## 兼容修复与边界

- 在窗口打开时修正 prismarine-windows 2.10.0 的旧锻造台布局，避免把结果槽 3 误认成背包。仅针对 1.21.11。
- 打开窗口期间，`inventory` 查询及 `/state` 从当前窗口的背包区域读取，避免原背包缓存滞后，公开槽位由 `ref` 标识。
- 村民使用原生 `openVillager` 初始化报价，不再把普通 `openEntity` 返回的窗口当成已初始化交易窗口。
- 织布／切石选择、合成器开关、信标配置使用小范围协议适配，限制为 1.21.11；等待服务端窗口属性确认。选中索引已有缓存时，也必须有结果物品才认为选择完成。
- 协议适配支持取消、关窗／断线失败及 5 秒确认超时。超时不代表服务端未执行，应先查 `window`，不要盲目重试支付或取物。
- 原生 Mineflayer 的搬运、附魔、铁砧、交易等操作未必可中途取消。动作仲裁继续等原操作结束，不会因超时提前放行另一个写操作。
- 尚未实现完整配方／图案枚举、自动选配方、多步骤生产规划；`select` 需要调用方给出正确索引。也未声称归档 Fabric 分支的所有交互功能已迁移。
- 这不是服务器全局 NBT 查询；只能读取当前身体已打开窗口可见的信息。窗口记忆按 agent/session 隔离，不等于实时全世界状态。

## 验证与代码位置

当前槽位语法与实机记录见 [槽位引用](slot-references.md)。失败或取消时查看 `observedEffect` 和 `changes`；最终状态未知时重新查询，不盲目重试。

- 协议／窗口适配：`src/commands/workstations.js`。
- 原生操作封装：`src/commands/workstation_native.js`。
- 命令入口：`src/commands/actions/{right,station,enchant,anvil,furnace,trade}.js` 与 `src/commands/queries/window.js`。
- 回归测试：`npm test`，工作站专项 `node --test test/workstations.test.js`。
- 真实服务器测试记录：本地 `runs/workstations-smoke/results.json`（忽略目录，不提交）。测试使用独立本地世界、管理员准备材料，验证生存模式 Mineflayer 操作链路，不验证自主规划能力。

2026-10-21 验收：`npm test` 共 87 项通过；真实服务器 13/13 项通过，包括熔炼取物、铁砧改名与合并、附魔、地图扩展、切石、织布、合成器开关、下界合金锻造、砂轮去附魔、酿造、信标支付和村民交易。未另行实测高炉／烟熏炉、全部附魔组合、完整配方索引及四层信标组合。测试服务器已保存退出，未启动图形客户端。

初测暴露的锻造台槽位、切石选择缓存与窗口背包缓存问题已修复。交易复测曾命中测试场景尚未消失的旧村民而超时，等待旧实体清理后重跑完整流程通过；不能据此把原生实体交互超时视为已全面解决。

## 上游依据

- [Mineflayer 官方 API](https://github.com/PrismarineJS/mineflayer/blob/master/docs/api.md)：优先复用原生附魔、铁砧、熔炉、交易及窗口搬运。
- [minecraft-data 1.21.11 协议](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.21.11/protocol.json)：窗口按钮、槽位开关、信标包字段。
- [上游酿造接口 PR #3313](https://github.com/PrismarineJS/mineflayer/pull/3313)：检索时已关闭、未合并，本轮未把该 PR 当成已发布能力。
