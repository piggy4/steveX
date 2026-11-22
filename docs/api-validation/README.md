# API 验收记录

更新：2026-10-23。当前入口与待测状态统一查阅 [API 验收清单](../api-acceptance-checklist.md)，不要从旧验收场景反推当前命令目录。本文档目录只存验收结果与覆盖摘要；接口定义、契约、架构与交接文档留在 docs 根目录。

| 记录 | 类型与范围 |
| --- | --- |
| [WQT API diagnostics follow-up](wqt-api-validation-2026-11-03.md) | Enchanted digging, output-slot preflight, cursor feedback and stable indices; 592 automated tests, 8 real Minecraft/DeepSeek scenario attempts |
| [Scoped reliability maintenance](maintenance-2026-10-25.md) | Exception cleanup, entity rechecks, atomic config saves, local ETags, drag handling, dependency/test discovery cleanup; 546 automated checks |
| [Held-use request boundaries](held-use-request-validation-2026-10-25.md) | User-confirmed manual acceptance; selected-hand requests, unsupported tools, instant vs sustained lifecycles; 26 automated inventory/use checks |
| [Book gift and repeated inventory queries](book-gift-context-validation-2026-10-25.md) | Real DeepSeek two-page save and drop; server evidence and human screenshot; steps 24–26 audited against actual context, repetition distinguished from missing feedback and manual Continue |
| [Look feedback and World controls](look-and-world-controls.md) | Three look modes, shape-source limitations, real chest/button/slab/obstruction validation, human-only map controls |
| [Dig server protection and cache recovery](dig-server-protection.md) | Vanilla 1.21.11 protection/OP comparison, two real rejected digs with cache recovery, final non-OP success; 532 automated tests passed |
| [新任务循环复杂合成](task-crafting-validation-2026-10-24.md) | Minecraft＋DeepSeek八场景181次请求，严格5/8；区分物品达成与结束声明，保留预算耗尽与重复查询问题 |
| [Compact agent workbench](web-compact-layout.md) | 紧凑布局、字体、Configs切换与过期加载修复；Edge桌面预览核对，4K／手机截图待测 |
| [有界任务循环与调度](task-runtime-validation.md) | 全库481项自动回归、3项HTTP模拟、Edge任务生命周期核对；无付费模型／Minecraft实测 |
| [交互、背包、窗口和工作站复测](interaction-crafting-recheck.md) | 当前22个命令入口的代表场景、独立DeepSeek复杂合成、SteveX身份及精确版本；保留未测边界 |
| [Web 视角与 LLM 调试](web-inspector-validation.md) | 真实 A→B→A RGB、无焦点按需渲染、本地模拟 provider 全链路；1次真实 DeepSeek 输入输出及双 agent 隔离通过 |
| [Web 只读背包与物品详情](web-inventory-validation.md) | 双 agent 9项实机、附魔浮层及刷新专项；全库450项回归；装备开窗滞后与特殊材质限制单列 |
| [统一反馈、库存同步与工作站](window-feedback-validation.md) | 全库448项自动回归、紧凑语法16项实机和工作站／反馈10项实机；保留固定编号及组件详情，不新增规划策略 |
| [固定玩家槽号与按需详情](slot-scope-validation.md) | 紧凑语法16项完整实机；固定编号／详情查询及搬运；工作站新编号另见统一反馈专项 |
| [疾跑、相对寻路、多候选搜索与钓鱼取消](motion-search-fish-validation.md) | 最新8项实机、18项独立回归；自然咬钩完整收获等边界仍待测 |
| [目标、窗口与反馈优化](api-refinement-validation.md) | 最新 12 项实机：攻击归因、副手喂食、指定掉落物、槽位拆分／交换／存取、满箱拒绝；其余边界单列 |
| [统一世界右键](rightclick-validation-2026-10-23.md) | 内部路径历史 19 组实测，含主副手、潜行、交互优先级和使用互斥；不冒充新入口全量复测 |
| [持续潜行与上下车](sneak-validation-2026-10-23.md) | 当前入口 12 组实测，含船／矿车服务端乘客关系 |
| [统一挖掘入口](dig-validation-2026-10-23.md) | 准星／坐标挖掘、遮挡、封闭路径、接近取消共 6 组实测 |
| [持续空格](jump-duration-validation.md) | 水中持续上浮、落地后再次起跳、取消松键共 3 组实测 |
| [统一开窗与动态目录](window-opening-validation.md) | 7 类窗口原生初始化与开关窗口后的 LLM 目录实测 |
| [聊天来源与书本界面](chat-book-validation.md) | 6 组真实服务端核对：聊天、私聊、权限反馈、主副手保存与草稿丢弃；提示词注入防护为来源隔离，不宣称绝对安全 |
| [方块原生标签与状态](findblock-tags-validation.md) | 不同原木、床颜色与状态、楼梯朝向及含水、含水半砖共 9 组实测 |
| [DeepSeek 配方与合成](craft-llm-validation-2026-10-23.md) | 新版 recipes/craft 基础 5 项、复杂 7 项；保留限制，不作为稳定成功率 |
| [地形移动](terrain-live-validation.md) | 最近地形实验覆盖与缺口，不宣称与原版客户端物理完全一致 |
| [背包与持续使用覆盖](inventory-use-coverage.md) | 合并背包、盾牌、消耗品、满包返还记录；历史实测摘要，不冒充当前名称全量重测 |
| [特殊放置覆盖](placement-coverage.md) | 合并特殊方块与实体放置结论；区分当前右键已测和内部实现历史覆盖 |

## 维护规则

当前右键入口统一为 right click/down/up。报告中的既有物理效果沿用此前实际测试结论；新入口已另测 7 类窗口，其他路径的统一分发和释放反馈有自动回归，不把说明迁移当作全量实机复测。right up 无手参数，返回 hadActiveUse/released/hand（未知为 null），不确认服务端效果。

- 同一接口更新已有记录，移除已被替代的轮次、过期入口说明和不存在的原始日志路径；有独立价值的边界合并保留。
- 改名或合并不等于完成复测；明确标记“当前入口实测”“历史摘要”“自动局部”“待测”。
- 原始 JSON、完整提示词、服务输出、世界存档与配置放本地忽略的 runs，不放 docs，不提交密钥或个人信息。
- 本目录内源代码和 runs 路径均按仓库根目录解释；Markdown 文档链接按当前文件位置解释。
- 各专项明确区分新增游戏测试与已有证据整理；聊天与书本本轮有游戏调用、无模型调用。旧 PR #13/#14 原型验收日志已删除；迁移背景与当前边界见 [迁移核查](../research-migration-parity.md)。
