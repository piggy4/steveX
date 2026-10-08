# DeepSeek Harness（dsh）已实现功能总结

> **阅读对象**：准备在 `deepseek-harness-master` 基础上改造、编写自己 agent 的工程师。
> **调查对象**：`src/agent/deepseek-harness-master/`（下文相对路径均以此为根）。
> **快照版本**：`@deepseek-ai/dsh-root` **0.2.0-rc.2**，最新 Agent Note 日期 2026-09-28。
> **调查日期**：2026-10-02。
> **本文性质**：状态快照 + 改造地图，**不是**使用手册。凡"文档宣称但代码对不上"的地方，集中记在第 13 节。

---

## 0. 一句话结论

dsh 不是一个"LLM 调工具的循环"，而是**一整个可组装的 agent 产品**：Cordis 插件树之上，模型适配器、工具注册表、会话日志、agent 循环本身全都是可替换插件；已经实现了完整的会话持久化（带格式版本与迁移链）、并发工具流水线、上下文压缩、沙箱与审批、子智能体与团队、后台任务、定时任务、MCP 接入、Web/Electron GUI、TypeScript/Python 双 SDK。

对 steveX 的意义：**它是一座可以拆的零件库，但不是一个可以直接塞进 Minecraft 的现成方案**——它的执行世界（文件系统 / shell / 浏览器）与 steveX 的执行世界（一个 mod 的 WebSocket JSON-RPC，51 个方法）没有任何交集，接入点必然是新写一个能力 seam + 一组工具。

---

## 1. 项目概况

| 项目 | 值 |
|---|---|
| 名称 | DeepSeek Harness，CLI 名 `dsh` |
| 出品 | DeepSeek AI |
| 版本 | `0.2.0-rc.2`（开发者预览，**未来会有破坏兼容性变更**） |
| 许可 | MIT |
| 框架 | [Cordis](https://github.com/cordiverse/cordis)，论文《A Programming Paradigm for Spatiotemporal Composability》 |
| 语言/构建 | TypeScript（ESM everywhere），pnpm workspace（`pnpm@11.7.0`），tsc 出类型 + tsdown 打包 |
| Node 要求 | `^22.19.0 \|\| >=24.0.0` |
| 规模 | 55 个包组、约 4868 个 TS 文件 / 8.8 万行、1501 个测试文件、43 篇顶层文档 + 约 80 篇子系统文档 |
| 设计记录 | `.agents/notes/` 下 **525 篇 implemented** 设计笔记（architecture 201 / feature 132 / process 69 / bug-fix 63 / testing 33 / simplification 27） |

安全定位（[SAFETY.zh.md](SAFETY.zh.md)）：**未经安全审计**，能执行模型生成的代码与命令、加载第三方插件；沙箱与审批只是降低风险，不保证隔离。

---

## 2. 架构骨架

### 2.1 一切皆插件（Cordis）

- 插件向共享上下文贡献**服务**（`ctx.<key>`）、**类型化事件**、**可逆副作用**。
- **没有需要打补丁的特权内核**：扩展方式是把插件挂到别的插件旁边。
- **注册即副作用**：一切贡献走 `ctx.effect()` / `ctx.on()`，注册函数的返回值是 disposer，插件卸载即撤销（[AGENTS.md:133](AGENTS.md#L133)）。

### 2.2 profile / bundle / patch 三层组装

运行中的 `dsh` 是一棵**按序叠加**出来的插件树（实现：[apps/cli/src/profile-boot.ts:197-208](apps/cli/src/profile-boot.ts#L197) 的 `composeProfile`）：

```text
空 entry 列表
  → ① profile 声明的各 bundle 的 cordis.patch.yml（按 profile 列出顺序）
  → ② profile 自身的 cordis.patch.yml
  → ③ $DSH_HOME/cordis.patch.yml（home 级，优先级高于 profile 自身）
  → ④ --patch 覆盖层（可重复）
  → ⑤ telemetry 开关
```

- **profile**：Harness home 中的具名组装，随发行版交付 `web` / `headless` / `sdk` / `sdk-minimal` / `acp` 五个模板（[packages/boot/app-boot/src/profile.ts:179-193](packages/boot/app-boot/src/profile.ts#L179)）；`desktop` 是保留 profile，CLI 拒绝启动或 dump 它。
- **bundle**：可安装的 patch 层。`dsh-base` 是共享第一层（模型适配器、工具、持久化、沙箱与审批、设置、凭据、遥测），`dsh-web-app` / `dsh-headless` / `dsh-sdk-app` / `dsh-acp-app` 各自再加一层；`dsh-sdk-minimal` 是刻意例外——完整显式配置树，不叠 base。
- 一条 patch 按 `id` 定位条目并**替换其整个 config**，或插入新条目。
- `dsh --dump-config` 打印当前机器实际组装的配置树（[apps/cli/src/dump-config.ts:32-42](apps/cli/src/dump-config.ts#L32)），不 boot、不求值 `!!js`——**改造前先 dump 一遍，看清单再决定 patch 哪里**。

### 2.3 事件三域

选对事件域是大多数改动的第一个决定（[docs/architecture.zh.md:74-84](docs/architecture.zh.md#L74)）：

| 域 | 性质 | 用途 |
|---|---|---|
| **会话事件**（`turn/*`、`step/*`、`user/message`、`assistant/message`、`tool/*`…） | 追加到日志、经 `session/event` 广播的**持久事实** | 需要在重载后仍然存在的事实 |
| **Agent 事件**（`agent/*`） | 携带活跃 `Agent` 的**实时扩展点** | 观察或拦截正在进行的工作 |
| **能力事件**（`fs/*`、`tools/*`、`telemetry/*`） | 向某个 seam 附加策略与适配器 | 无导入循环地挂策略 |

事件模式分三种：**waterfall**（监听器必须调 `next()` 才继续，可改写或短路）、**serial**（依次执行，无 `next()`）、**emit**（只读广播）。生产方/消费方全表见 [docs/event-producer-consumer.zh.md](docs/event-producer-consumer.zh.md)。

### 2.4 轮次与步骤

- **步骤**＝一次模型请求 + 它触发的工具执行；**轮次**＝零或多个步骤，在领取首条输入前打开、不再欠工作时关闭。
- 精确顺序（[docs/architecture.zh.md:92-111](docs/architecture.zh.md#L92)，实现 [packages/core/agent-loop/src/agent.ts:296](packages/core/agent-loop/src/agent.ts#L296)）：

```text
turn/start
  领取 next-step 输入 + 一条排队消息
  组装提示词片段 + 工具 schema，投影运行时上下文
  agent/pre-step（waterfall）          可 reject，可改写已领取消息
  step/start
  agent/request → prepareCall()        两者取消都不提交系统提示词与用户消息
  协调 system/message；提交 user/message；按需记 request/header、request/context
  派生并冻结模型历史
  llm/stream → agent/assistant-stream（start / chunk* / end）
    → assistant/message | assistant/attempt
  tool/call* → tools/pre-execute → tools/execute → tools/post-execute → tool/result*
  step/end
  还欠请求 或 next-step 有输入 → 领取下一步
agent/turn-stopping（serial）→ turn/end
```

- **工具调度**（[packages/core/agent-loop/src/tool-calls.ts:60](packages/core/agent-loop/src/tool-calls.ts#L60)）：独占调用成屏障，并发安全的调用走有界池（`maxParallelToolCalls`），但**策略判定与结果顺序保持模型给出的顺序**。
- **取消**：协作式 `AbortSignal`；`turn/end` 记 `{kind:'aborted', reason}`，已流式输出的文本以 `interrupted:true` 保留。
- **失败步骤补记**（[packages/core/agent-loop/src/agent.ts:342-353](packages/core/agent-loop/src/agent.ts#L342)）：为没有结果的调用补一条错误结果——已有 `tool/call` 的记 `TOOL_OUTCOME_UNKNOWN`，完全没记录的记 `TOOL_NOT_STARTED`。

### 2.5 会话日志：模型可见即已记录

这是全项目最强的架构不变量（[AGENTS.md:138](AGENTS.md#L138)）：**任何到达模型请求的东西都必须能从会话日志重建**；新增模型可见输入就必须要新增会话事件。

- `deriveMessages()`（[packages/core/session/src/index.ts:860](packages/core/session/src/index.ts#L860)）从日志投影出模型历史，只依据 **surface 子集**事件（携带 `surfaceOp: append|replace`）。
- `assistant/message`＝成功或被中断的模型消息（带 usage、完整紧凑 stream）；`assistant/attempt`＝失败/重试/取消且**不产生模型历史**的尝试。进程若在 settlement 前硬中断，不会留下持久 attempt stream。

---

## 3. 核心运行时（`packages/core/*`）

### 3.1 服务 API 速查

| `ctx` 键 | 提供者 | 关键方法 |
|---|---|---|
| `ctx.sessions` | `SessionStore`（[core/session/src/index.ts:925](packages/core/session/src/index.ts#L925)） | `create/prepare/enter/announce`、`flush`、`get/list/fork`、`registerMessageProjection` |
| `ctx.systemPrompt` | `SystemPrompt`（[core/system-prompt/src/index.ts:558](packages/core/system-prompt/src/index.ts#L558)） | `section/context/tools/variable/suppressRuntimeContext`、`assemble` |
| `ctx.tools` | `ToolRuntime`（[core/tools/src/index.ts](packages/core/tools/src/index.ts)） | `register`(:1063) / `restrict`(:1097) / `guard`(:1136) / `presentAs`(:974) / `schemas`(:1260) / `executionMode`(:1303) / `execute`(:1369) |
| `ctx.agents` | `AgentRegistry`（[core/agent/src/index.ts](packages/core/agent/src/index.ts)） | `create/resume/get/list/roots`、发起者作用域 `withInitiator/requireInitiator` |
| `ctx.agentLoop` | `AgentLoop`（[core/agent-loop/src/index.ts:330](packages/core/agent-loop/src/index.ts#L330)） | `create`(:652) / `createAgent`(:714) / `resume`(:807) |
| `ctx.sessionProjections` | `SessionProjectionRegistry`（**在 `packages/session/`，不在 core**，[:199](packages/session/session-projection/src/index.ts#L199)） | `register/onChanged/snapshot/stateOf/checkpoint/restore` |

### 3.2 Agent 接口与 `agent/*` 事件

`Agent`（[core/agent/src/runtime-types.ts](packages/core/agent/src/runtime-types.ts)）：`id / options / session / inbox / status / ctx`，生命周期 `cancel(cause, {keepInbox}) / whenIdle / runMaintenance / send / followup / steer / inject`。

四种投递方式的语义差别很关键：

| 方法 | 生效时机 | 是否唤醒 |
|---|---|---|
| `send(target, wakeup)` | 由调用方决定 | 由调用方决定 |
| `followup` | 下一轮 | 是 |
| `steer` | 下一步 | 是 |
| `inject` | 下一步 | **否** |

事件清单（模式见 [docs/event-producer-consumer.zh.md:16-26](docs/event-producer-consumer.zh.md#L16)）：

- **waterfall**：`agent/pre-step`(:320)、`agent/request`(:337)、`agent/request-error`(:353)
- **serial**：`agent/created`(:261)、`agent/turn-stopping`(:381)
- **emit**：`agent/disposed`、`agent/status`、`agent/inbox/{inserted,claimed,discarded}`、`agent/assistant-stream`、`agent/error`

注意两点：`agent/pre-step` 是**请求推导前唯一**的 waterfall；`agent/request-error` 监听器返回 `{kind:'retry'}` **且不调 `next()`** 即触发恢复，这是上下文超限自动重试的挂载点。

### 3.3 工具注册表与执行守卫流水线

- schema 进入提示词：`schemas()` 只白名单 `name / description / parameters`，经 `ctx.systemPrompt.tools()` 提供方流入提示词组装。
- **作用域化**：`restrict({allow, deny})` 为单个 scope 过滤全局工具集合（多个 restriction 取交集）；被过滤掉的全局工具**既不出现在提示词中，也拒绝执行**，与不存在无法区分。子 agent 自己注册的工具不受该过滤影响。
- 完整流水线（[docs/tool-execution-pipeline.zh.md:8-65](docs/tool-execution-pipeline.zh.md#L8)）：

```text
tool/call（日志）
  → tools/pre-execute   waterfall，返回 PreToolDecision: allow | deny | cancel | ask，可短路
  → ToolGuard 链        单调，只能 deny 或返回 undefined
  → tools/execute       around 包装，可替换信号，不可移除
  → projectContent
  → tools/post-execute  waterfall，PostToolDecision: accept（替换 content/value）| block
  → finalizeContent
  → tools/result（只读 emit）→ tool/result（持久）
```

三个 waterfall 各可改写一次，这是"不碰循环就能给所有工具加策略"的位置。

### 3.4 系统提示词组装

- 来源四类：`PromptSection`（静态或上下文函数，带 order）、`PromptContext`（动态，成为**持久 user 快照**）、工具 schema 提供方、变量。
- `assemble()` 合并全局 + 作用域、排序、跑 `system-prompt/assemble` waterfall，再强制**唯一 complete 段**。
- 产物经 `renderPrompt()` 插值后，以循环的 `system/message` surface 节点提交——**提示词只通过 `system/message` 历史传递**；渲染为空会清除所有生效的系统节点（模型不再看到旧提示词）。
- **缓存前缀**由"分段仅追加/原地替换"决定 KV 命中率，这是提示词改动的性能红线。

### 3.5 preset / context / compaction / guard

| 子系统 | 做什么 |
|---|---|
| `packages/preset` | `ctx.agentPresets` 按会话挂载声明式子插件 scope，实现**按会话换能力集合**；工具/提示词片段经 `agent.ctx` 作用域注册，宿主共享同一个 loop |
| `packages/context` | 注入模型可见上下文：`agent-instructions`（AGENTS.md / CLAUDE.md）、`session-reference`、`file-reference`、`time-context`、`tmux-context`，**全部是持久 user 角色快照** |
| `packages/compaction` | `compaction-basic` 在 `agent/pre-step` 按阈值压缩（默认 `floor(min(W×0.8, W−O−65536))`），并在 `agent/request-error` 上对 `CONTEXT_WINDOW_EXCEEDED` 恢复重试；产物是 `compaction/start → summary → 替换 user/message → end` 的日志标记对，先写 start 当持久锁 |
| `packages/guard` | `repeat-tool-reminder`（在 `tools/post-execute` 数精确重复，3/5/8 次追加提醒，**只建议不阻断**）+ `timeout-policy`（用工具自己的 `timeoutMs` 包装 `tools/execute`，超时产出 `TOOL_TIMEOUT` 错误结果） |

### 3.6 扩展点清单（"新行为该挂哪"）

| 目标 | 机制 |
|---|---|
| 加模型提供方 | 在 `ctx.llm` 注册适配器 |
| 加模型可见能力 | `defineTool({name, description, parameters, output, execute})` + `ctx.tools.register`；schema 自动进提示词 |
| 让某会话用不同能力集 | 组装 agent preset（服务行需 `isolate` realm） |
| 加 shell 执行 | 注册 `ctx.shell` 后端 |
| 加持久终端 | 注册 `ctx.terminals` 后端 + `dsh-tool-terminal` |
| 加文件系统访问或策略 | 注册 `ctx.fs` 提供方，或监听 `fs/*` |
| 加用户命令 | `ctx.commands` 注册（**不需模型轮次**即可分派） |
| 后台任务 | `ctx.jobs` 注册；`job_*` 工具读写 |
| 拦截请求 / 工具 / 轮次 | 相应的 `agent/*`、`tools/*` 事件；`agent/turn-stopping` 可停止轮次 |
| 加模型可见上下文 | `agent.inject()`，落到下一次获准请求 |
| 加持久会话状态 | 扩展 `SessionEventMap`，从日志渲染与回放 |
| 新存储后端 | 实现 `SessionPersistence`（`create/open/flush/stat/list`） |
| 限定到单个 agent | 用该 agent 的 `agent.ctx` |

---

## 4. 模型可见工具全清单

来自生成的 [docs/tool-catalog.zh.md](docs/tool-catalog.zh.md)（完整性由 `pnpm run verify-tool-catalog` 守卫）。按能力分组：

### 4.1 文件与搜索

| 工具 | 包 | 说明 |
|---|---|---|
| `read` `write` `edit` `read_image` | `fs/tool-fs` | 走 `ctx.fs`；先读后写策略由 `fs-observation-policy` 以 `fs/*` 门禁添加，**不改 schema**；`read_image` 需 `ctx.attachments` 且路由须声明图片输入 |
| `glob` `grep` | `fs/tool-fs-search` | 走 `ctx.subprocess` spawn **随包携带的 ripgrep**，不经 shell；结果超上限经 `ctx.spillStore` 落盘 |
| `str_replace_editor` | `fs/tool-str-replace-editor` | 独立的查看/创建/唯一字面量替换/按行插入工具，base 未挂载 |

### 4.2 执行与终端

| 工具 | 包 | 说明 |
|---|---|---|
| `bash` `pwsh` | `shell/tool-bash`、`shell/tool-pwsh` | 一次性执行；带 `run_in_background`（有 `ctx.jobs` 时）注册为后台任务 |
| `bash` `pwsh`（持久变体，同名） | `shell/tool-bash-persistent`、`tool-pwsh-persistent` | 按所有者隔离的**持久 PTY** 会话 |
| `terminal_open/send/read/signal/close/list` | `terminal/tool-terminal` | 6 个 PTY 工具，需选择启用；**schema 不含 TUI、具名按键序列、调整尺寸、跨 agent 共享** |
| `run_code` | `core/tools`（PTC） | 见 §5.4 |

### 4.3 规划、目标与状态

| 工具 | 包 | 说明 |
|---|---|---|
| `todo_write` | `todo/tool-todo` | 会话所有的整体替换清单；`allowParallelInProgress` 是**必填无默认**的配置项 |
| `exit_plan_mode` | `plan/tool-plan-mode` | 规划未激活时仍留在 schema 中（避免工具目录抖动），执行路径拒绝模式外调用 |
| `create_goal` `get_goal` `update_goal` | `goal/tool-goal` | 同会话持久目标；create/edit/pause/resume 要求**直接来自人类的根权限** |
| `skill` | `skill/tool-skill` | 技能目录与加载 |
| `ask_user_question` | `interaction/tool-ask-user` | 默认阻塞；`mode: timed` 才有前台超时与 pending 结果 |

### 4.4 多智能体与后台任务

| 工具 | 包 | 说明 |
|---|---|---|
| `subagent`（可配名）、`subagent_fork` | `subagent/tool-subagent` | 委派；`list_subagent_models` 做模型发现 |
| `send_message` `interrupt_agent` `list_agents` | `subagent/tool-subagent-control` | 控制**可继续**后台 subagent；`list_agents` 另由 `/list-agents` 插件提供 |
| `job_kill` `job_list` `job_output` | `jobs/tool-jobs` | **与任务种类无关**：后台 bash、PTY 发送、subagent 都靠这 3 个工具读写 |
| `spawn_teammate` `wait_agent` `team_task_create/get/list/update` | `experimental/tool-agent-team` | 实验性 Agent Teams，共 9 个工具（另含 `send_message`/`interrupt_agent`/`list_agents`） |

### 4.5 编排、Web 与其它

| 工具 | 包 | 说明 |
|---|---|---|
| `workflow` | `workflow/tool-workflow` | 模型写 JS 编排脚本 |
| `ralph` | `workflow/tool-ralph` | 固定目标的迭代循环（base 里 `disabled: true`） |
| `web_fetch` `web_search` | `web/tool-web` | 提供方选择置于 `ctx.web` 之后，换后端不改 schema |
| `present` | `deliverables/tool-present` | 显式声明交付文件 |
| `load_workspace_dependencies` | `workflow/tool-workspace-dependencies` | — |
| `list_mcp_resources` `list_mcp_resource_templates` `read_mcp_resource` | `mcp/mcp-resources` | MCP 资源读取 |
| `plugin_manager` | `boot/plugin-manager` | 启停/安装/卸载插件，**需 danger-full-access 或逐次批准**；base 默认 `disabled` |
| `cordis_inspect_list` `cordis_inspect_query` | `extensions/tool-cordis` | 只读运行时检查，供 agent 写插件前查 API |
| `session_search` `session_trace` `session_event_read/search/trace` | `session-query/tool-session-query` | 5 个只读工具，按调用 agent 的会话做逐结果授权 |
| `lsp` | `lsp/tool-lsp` | 无提供方时返回结构化 `LSP_UNAVAILABLE`，**不改 schema** |
| `schedule_create/list/update/delete` | `schedule` | 定时后续，需选择启用 |
| `stagehand_navigate/act/observe/extract/screenshot/tabs` | `experimental/browser-use-stagehand-native` | 浏览器自动化，实验性 |

---

## 5. 执行世界与能力 seam

### 5.1 seam 全表

一个 **seam ＝ 可替换能力**，必须含三种角色：Service Definition（拥有 `ctx.<key>` 的 Cordis `Service`，**不是 TypeScript interface**）、Service Provider、Consumer。单一角色本身不算 seam（[docs/glossary.zh.md](docs/glossary.zh.md)）。

| seam | Service Definition | `ctx` 键 | 随产品提供的 Provider | 主要 Consumer |
|---|---|---|---|---|
| 文件系统 | `FileSystem`（abstract，[fs/fs/src/index.ts:87](packages/fs/fs/src/index.ts#L87)） | `ctx.fs` | `fs-local`、`fs-sandbox`、`fs-ssh` | `tool-fs`、`lsp-stdio`、`fs-observation-policy` |
| Shell | `ShellExecutor`（abstract，[shell/shell/src/index.ts:64](packages/shell/shell/src/index.ts#L64)） | `ctx.shell` | `bash-local`、`bash-sandbox`、`pwsh-local`、`pwsh-sandbox` | `tool-bash`、`tool-pwsh`、hooks |
| 子进程 | `SubprocessRuntime`（abstract，[subprocess/subprocess/src/index.ts:117](packages/subprocess/subprocess/src/index.ts#L117)） | `ctx.subprocess` | `subprocess-local`、`subprocess-ssh` | shell 两个本地后端、`terminal-bash`、`tool-fs-search`、`lsp-stdio` |
| 终端 | `TerminalSessionService`（[terminal/terminal/src/index.ts:105](packages/terminal/terminal/src/index.ts#L105)） | `ctx.terminals` | `terminal-bash` | `tool-terminal`、持久 bash/pwsh 工具 |
| 沙箱 | `SandboxProvider`（abstract，[sandbox/sandbox/src/index.ts:159](packages/sandbox/sandbox/src/index.ts#L159)） | `ctx.sandbox` + `ctx.sandboxPolicy` | `sandbox-local`、`sandbox-ssh` | shell、fs、terminal、subprocess 消费方 |
| SSH | `SshConnection`（[ssh/ssh/src/index.ts:47](packages/ssh/ssh/src/index.ts#L47)） | `ctx.ssh` | —（是连接所有者，不是 seam） | `fs-ssh`、`subprocess-ssh`、`sandbox-ssh` |
| LSP | `Lsp`（registry，[lsp/lsp/src/index.ts:82](packages/lsp/lsp/src/index.ts#L82)） | `ctx.lsp` | `lsp-stdio` | `tool-lsp` |
| 浏览器 | `BrowserUseRegistry`（[browser-use/browser-use/src/index.ts:16](packages/browser-use/browser-use/src/index.ts#L16)） | `ctx.browserUse` | stagehand-native、playwright-mcp、chrome-devtools-mcp、cua-driver-mcp | 各 provider 自带工具 |
| 桌面 | `ComputerUseRegistry`（[computer-use/computer-use/src/index.ts:16](packages/computer-use/computer-use/src/index.ts#L16)） | `ctx.computerUse` | 同名独占注册 | 同上 |
| PTC 运行时 | `PtcRuntime`（abstract，[ptc-runtime/ptc-runtime/src/index.ts:104](packages/ptc-runtime/ptc-runtime/src/index.ts#L104)） | `ctx.ptcRuntime` | `ptc-runtime-node`、`experimental-ptc-runtime-python` | `run_code` |
| 工作流 | `WorkflowEngine`（abstract，[workflow/workflow/src/index.ts](packages/workflow/workflow/src/index.ts)） | `ctx.workflowEngine` | `workflow-ptc` | `tool-workflow`、`tool-ralph` |

> 注意 `BrowserUseRegistry` / `ComputerUseRegistry` 只有 `register(name)`，**没有操作 API**——真正的操作能力由各 provider 自己带工具。

### 5.2 为什么"换提供方 = 整体搬家"能成立

消费方只依赖 seam、从不依赖具体 provider：

- `bash-local` 静态注入 `['subprocess']`（[shell/bash-local/src/index.ts:98](packages/shell/bash-local/src/index.ts#L98)），以 `bash -c` 方式走 `ctx.subprocess`；
- `terminal-bash` 注入 subprocess；
- `lsp-stdio` 注入 `['fs','lsp','subprocess']`（[lsp/lsp-stdio/src/index.ts:47](packages/lsp/lsp-stdio/src/index.ts#L47)）——源码用 `ctx.fs` 读、服务器用 `ctx.subprocess` 起；
- `tool-fs` 走 `ctx.fs`。

所以把 `fs-local` / `subprocess-local` 换成 `fs-ssh` + `subprocess-ssh`（+ `sandbox-ssh`），**四个消费方的坐标一起搬到远端**，无需 provider 专用 fork。这正是 `ctx` 注入式依赖的兑现。

### 5.3 沙箱

- 模式三档：`read-only | workspace-write | danger-full-access`（[sandbox/sandbox/src/index.ts:30](packages/sandbox/sandbox/src/index.ts#L30)）；**Provider 只接受前两档**（`ConfinedSandboxMode`）。
- 消费方在 spawn 前把**确切的 argv**（不是 shell 字符串；shell 消费方传 `['bash','-c',cmd]`）交给 `await ctx.sandbox.confine(argv, policy)`，取回 `ConfinedArgv{argv, enforcement, denialSignatures, runnerFailureRules}`；`enforcement` 为 `full | partial`；无后端时抛 `SANDBOX_UNAVAILABLE`。
- 后端按平台选：linux `['bwrap','landlock']`、darwin `['seatbelt']`、win32 `['windows-acl']`。
- 策略统一由 `ctx.sandboxPolicy.resolve` 提供，**bash 与 fs 共用同一个 workspace root**。

### 5.4 PTC（Programmatic Tool Calling）

- 运行时执行"模型写的程序 + 宿主异步绑定"：每个 `PtcBindingNamespace` 成为程序里的一个全局对象，参数与返回值必须无损 JSON。
- `run_code`（[core/tools/src/ptc.ts:333](packages/core/tools/src/ptc.ts#L333)）把注册表里的工具桥接成程序内的 `tools.*` 异步调用；**子调用重新进入完整且受守卫保护的工具流水线**，并按原生并发约定调度。
- 展示模式 `native | ptc | both`：`ptc` 下模型**只看到 `run_code` + 生成的 SDK 章节**，直接调用其它工具名会被拒；但 `run_code` 内部的子派发仍可用全部可见工具。

### 5.5 workflow vs ralph

| | `workflow` | `ralph` |
|---|---|---|
| 脚本来源 | **模型写**任意 JS 编排脚本 | **部署固定**（[workflow/tool-ralph/src/index.ts:88](packages/workflow/tool-ralph/src/index.ts#L88)） |
| 模型给的参数 | `{script, meta, args}` | `{objective, maxRounds}` |
| 每轮 | 脚本自行 `agent()` 派生子 agent | 每轮一个**全新**结构化子 agent，只带不可变目标 + 上轮**有界 handoff** |
| 定位 | 通用可编程编排 | 固定迭代循环 |

---

## 6. 多智能体与委派

### 6.1 subagent seam 与 6 个提供方

`ctx.subagents` 是**按名称注册的多提供方注册表**：`start / startContinuable / sendMessage / interrupt / listChildren / listDescendants / registerProvider`。`SubagentProvider` 含 `name`、`capabilities`、`inheritsParentContext`、可选 `agentRouteDefaults`、`start()`，以及**可选 `prepareContinuable()`（方法存在即代表具备该能力）**。

随产品提供的 6 个（默认名）：`spawn`（进程内新建）、`fork`（进程内、继承父历史）、`acp`、`codex`、`claude-code`、`dsh-sdk`（委派给别的产品/独立运行时）。这就是"同一个接口之后从新建子 agent 到把轮次委派给另一个产品"的兑现。

### 6.2 一次性 / 可继续 / Agent Teams

| 形态 | 特征 | 工具 |
|---|---|---|
| **一次性** | 有 `result` 与 `dispose`，无 steering、无恢复，仅前台 | `subagent_fork` |
| **可继续** | 持久子 Session + 至多一个进程内 Activation；FIFO inbox 是唯一队列；支持冷恢复、`sendMessage`（**仅相邻 parent↔child**）、`interrupt` | `subagent`、`send_message`、`interrupt_agent`、`list_agents` |
| **Agent Teams**（实验性） | 隐式 Root Team：一个 Lead + 若干 teammate，**共享同一 cwd 与文件系统**；**会禁用普通 subagent 委派** | 9 个 `team_*` / `spawn_teammate` / `wait_agent` 等 |

### 6.3 持久结构

- subagent 目录：父 Session 日志经 `subagentCatalog` projection 回放。
- Agent Teams（全在 Lead Session 日志，经 `agentTeam` projection 回放）：
  - **roster** `TeamMemberSnapshot`（phase: `provisioning → active | failed`）；
  - **mailbox** `TeamMessageSnapshot`（先存 queued，target 持久化后写 ack；queued − delivered 即恢复邮箱）；
  - **任务板** `TeamTaskSnapshot`（`revision` 是 CAS；`blockedBy` 是无环 DAG；`writeScopes` 只是提示，**不是锁**）。

---

## 7. 自动化与自主性

| 子系统 | 状态存哪 | 由谁驱动 | 模型工具 | 默认 |
|---|---|---|---|---|
| **goal** | 所属 Session 日志的 `goal/change` 事件 | `goal-round-driver` 在 quiescence 时自动续跑下一 Round | `get_goal` / `create_goal` / `update_goal` + `/goal` 命令 | 开 |
| **schedule** | **宿主级** `schedule` storage domain 的 `tasks` 表（不是 Session 日志） | 宿主定时器，到期恢复原 Session 并投递 follow-up | `schedule_*` ×4 | **需显式启用** |
| **jobs** | 进程本地 `LocalJobRegistry`，**不持久** | 生产者调 `ctx.jobs.start()` | `job_list` / `job_output` / `job_kill` | 开 |
| **todo** | Session 日志的 `todo/write`（整体替换） | 投影 + 不变量 | `todo_write` | 开 |
| **plan** | Session 日志的 `plan/mode`（**软指引，非强制**） | `ctx.planMode` 在 pre-step 追加 | `exit_plan_mode` + `/plan` | 开 |
| **skill** | **无持久化**，来自文件系统发现（rank 100…600） | `ctx.skills` 分层注册表（host + per-scope） | `skill` | 开 |

目标有 `active / paused / blocked / complete` 四个阶段与 `maxGoalRounds` 预算；**目标激活态（armed/disarmed）有意不参与持久回放**——恢复或 fork 后必须再经一次人类授权的变更才会自动干活。Ralph 的 Round 计数器归策略所有，不统计会话里的每个轮次。

---

## 8. 持久化、检索与扩展机制

### 8.1 会话持久化与格式版本

- **`SessionPersistence` 抽象 seam**：`create / open / flush / stat / list`，逐会话 `SessionHandle` 承载 `read / append / flush / close`（单写者所有权；崩溃不截断被中断的轮次）。
- **后端只有 JSONL**：`dsh-session-persistence-jsonl`（Zstd 帧带 checksum，或裸行）。header（`SessionHeader`）与日志分离。
- **版本**（实测）：代码写入器 `SESSION_FORMAT_VERSION = 4`（[core/session/src/types.ts:89](packages/core/session/src/types.ts#L89)），文档 `latestFinalizedVersion: 4` 但 **`latestReleasedVersion: 3`** —— 即 **V4 尚未在已发布产品数据上被证实**，改造持久化时这是要留意的风险点。
- v0…v4 的历史 schema 在 `docs/persistence-changes/historical-formats/`；**迁移链是独立包**：`session-format-v0-to-v1` → `v1-to-v2` → `v2-to-v3` → `v3-to-v4`。
- 已提交的 generation 路径**绝不重命名、替换或删除**；新版本写为版本命名的后继。历史迁移**拒绝未知事件类型**（连 `ignorable` 也拒）。

### 8.2 projection（客户端读模型 seam）

`ctx.sessionProjections.register(ProjectionDefinition)` 注册纯同步单元 `init / apply / wire`；注册表**只订阅一次** `session/event` 驱动所有单元。host 消费方用 `stateOf()` 读单个类型化状态，载体用 `snapshot()` 批量取裁剪后的客户端视图。另有 `ctx.sessionProjectionCache` 持久化 checkpoint。

**注意**：host 读取方要么在激活时要求该服务，要么明确失败；贡献方**不可以为缺失的 host 值静默提供默认值**。

### 8.3 session-query

`ctx.sessionQuery` 统一 seam：精确读取、provider 无关过滤、**全文搜索分页**（`searchSessions` / `searchEvents`）、谱系 `traceSession`、事件关系。FTS 由 `session-query-sqlite` 提供，但**默认 `openAt: never`（搜索禁用）**，精确读取/标题/谱系仍可用。

### 8.4 storage / spill / attachment / workspace

| | 解决什么 |
|---|---|
| `ctx.storage` | **非会话**存储中枢：只做后端注册与数据形式挂载，不做 IO；后端 `json` / `sqlite`；`ctx.storageDomain` 按 `DomainSpec`（name/version/layout/tables，zod schema）打开领域，发 `domain/changed` |
| `ctx.spillStore` | **大工具输出**落盘，返回不透明 locator（`glob` 超上限就走它） |
| `ctx.attachments` | **二进制图片字节**内容寻址存在日志外，日志里只写引用（`read_image` 走它） |
| `ctx.workspaceRegistry` | Workspace 实体，**宿主侧、对模型不可见**，经 storage domain 持久，按 `SessionHeader.cwd` 规范路径校验成员 |

### 8.5 MCP / hooks / extensions / webhook

- **MCP**：每个 server 一个 `dsh-mcp-client` 连接插件，把外部工具适配成原生工具，命名 `mcp__<serverName>__<rawName>`；支持 stdio 与 Streamable HTTP；**需显式配置**。
- **hooks**：`hook-protocol` 共享引擎 + `hooks-claude-code`（支持 SessionStart / UserPromptSubmit / PreToolUse / PostToolUse / Stop / SubagentStop）与 `hooks-codex`（5 个事件）。**只运行同步 command hook**，可阻止提示词/工具或补充上下文。
- **extensions（运行时自修改）**：`ctx.dynamicCordisRunner` 的 `define / run / stop / undefine` 让 agent **写含 host + client 两半的 Cordis 包**；`cordis_inspect_*` 供写前查 API；持久化安装走 `plugin_manager`。**默认 disabled**。
- **webhook**：`ctx.webhookRuntime` 即发即弃；`VerifiedWebhookDelivery` → `WebhookRule.run` → 可选 `WebhookSessionRequest` → 创建普通根 Session（cwd = Workspace），follow-up 的 `source.kind = webhook`。GitHub 适配器注册独立路由。

---

## 9. 应用形态与人机界面

### 9.1 CLI 与 profile

`apps/cli` 是**唯一受支持的 Node 应用启动器**。解析器只认领自己的 flag，之后所有 token 原样交给被启动的 app；`dsh <name>` 自动重写为 `dsh --profile <name>`。

四种模式：`profile` / `dump-config` / `dump-config-schema` / `plugin`。Launcher flag：`--profile`、`--from-default-profile`、`--patch`（可重复）、`--dump-config`、`--dump-config-schema`、`--dump-default-config`；`dsh plugin --profile <name> <pnpm args>` 把参数转发给 profile 目录内的 pnpm。

**规则**：只有 `dsh` profile 能启动受支持的 Node 应用；包 bin、demo、公开 SDK 的 argv 逃生口都**被显式禁止**（由 `scripts/verify-application-entrypoints.ts` 守卫）。

### 9.2 Web / Desktop

- **服务端**：`host/webserver`（`node:http` 插件，提供 `ctx.webServer`，具名路由 + fallback 席位）、`host/frontend-static`（认领 fallback 提供 SPA dist）、`client/connection`（`/api` HTTP bridge、信任校验、取消与 RPC carrier）。
- **浏览器端**：`apps/web` 只是 Vite 壳（产物是 `dist/`，不是独立应用）；真正的浏览器侧在 `packages/client/*`（`ui-*` 功能包、`modules`、`connection`、`slots`、`store`）。
- Host 把 `window.__DSH_BOOT__` 注入页面，`client-modules` 生成 entry 图后在**同一文档内**激活客户端插件。
- **Electron 桌面**：`apps/desktop` 从 `dsh-app://app/` 载入页面并经 IPC 转发 HTTP/WebSocket；`apps/desktop-host` 是私有 Node-mode Host 进程，启动 `desktop` profile（默认端口 **19387**）。

### 9.3 GUI 扩展点与 RPC

- **RPC / typert**：业务 service 用 `@Remote` / `@RemoteScope` 标注方法，Typert **generator** 以 `tsconfig.host.json` 为种子生成严格的 descriptor / codec / 声明合并；**registry/loader** 装进 `ctx.typert`；客户端方法挂到 `ctx.remote.<ns>`，调用走 `connection.rpc.call('/api', '<ns>/<method>')`，**流式走 `/api/remote.mux` WebSocket**。
- **slot**：`ui-slots` 是类型化的 React 组合注册表（`ctx.slots.register / inject`），`ui-renderer` 是唯一渲染 root。
- **加一个新 UI 卡片**：声明/注入 child slot + `ctx.slots.register`，并注册 `ConversationNodeDefinition`（把 Session 事件按 `(kind, id)` 折叠成 target snapshot）。
- **客户端文案是 locale 所有的**：`verify-client-ui-i18n` 拒绝硬编码文案，产品文本必须走类型化字典 + `t`。

### 9.4 模型接入

- **seam**：`LlmRuntime`，`ctx.llm.registerAdapter(providers, adapter)`；`LlmAdapter.stream(options): AsyncIterable<StreamChunk>`；原始 chunk 由共享 `BlockAssembler` 折叠成消息与工具调用块。
- **已实现的适配器包**：`llm-deepseek`（route `deepseek-official`）、`llm-pi-ai`（通用 pi-ai，多 route）、`llm-deepseek-api-key`、`llm-deepseek-account`、`llm-retry`、`token-meter`、`plugin-package-inventory-deepseek`。
- **模型目录**（实测 [llm/llm-deepseek/src/models.ts:6-21](packages/llm/llm-deepseek/src/models.ts#L6)）：`deepseek-flash`（DeepSeek-V41-Flash，**声明支持 image 输入**，`systemPromptUpdate: 'in-history'`）与 `deepseek-v4-pro`；是可替换的建议目录。
- **推理强度**：`off / low / high / max`。
- **DeepSeek wire 扩展**：HTTP 头 `x-deepseek-harness-user-id / -session-id / -compact`，正文 `dsh_plugin_packages`、`dsh_session_log`；由 `deepseek-llm-api-extensions` 注册提供方、`request-extensions.ts` 组装，2xx 后跑 `accept()` 事务。
- **凭据优先级**（[credentials/credentials-local/src/index.ts:6-9](packages/credentials/credentials-local/src/index.ts#L6)）：继承的进程环境 > 调用目录 `.env` > `$DSH_HOME/.env` > `$DSH_HOME/.credentials.yaml`；**空存储值即视为不存在**。

### 9.5 审批、权限预设与遥测

- **审批**：`ApprovalOutcome` 闭合为 `allowed-once | rejected | cancelled | unavailable`，**失败一律拒绝**；`ApprovalPolicy` 只有 `ask | never`，`never` 在 waterfall **之前**就强制 `rejected`。`ctx.approval.request()` 需处于开放轮次，并追加 `approval/asked|decided` 审计事件。
- **权限预设**：默认两组——`workspace-write`（sandbox `workspace-write` + approval `ask`）与 `danger-full-access`（sandbox `danger-full-access` + approval `never`），把两种策略绑成一个选择器；`custom` / `auto` 保留。
- **联动**：升级目标只有 workspace-write / danger-full-access，越界需 **`allowed-once`** 才升级。
- **遥测**：`ctx.otel` 工厂；产品埋点仅 **Desktop 采集**且受启动开关控制，**Web 客户端不采集**。

---

## 10. 开发、测试与质量门禁

```sh
pnpm install / build / typecheck / lint / duplication
pnpm run test            # 单元
pnpm run test:coverage   # CI 覆盖率门禁：packages/*/*/src 逐文件 100%
pnpm run test:e2e        # 真实 API，无 DEEPSEEK_API_KEY 自跳过
pnpm run test:snapshot   # 无密钥回放录制会话，走随附 profile
pnpm run doc-sync        # 文档门禁（scripts/run-gates.ts）
pnpm dsh --profile headless "task"   # 从源码跑一个任务
```

- **vitest 分层**：单元 / e2e（真实 API）/ expected（`*.expected.e2e.ts`）/ snapshot（录制回放）/ web（Chromium 快照）/ web-perf / web-stress / bench。
- **自定义门禁**（`scripts/run-gates.ts`）：`verify-type-equiv`（文档里的 `ts type-equiv` 必须与源码符号一致）、`verify-cordis-catalog` / `verify-client-catalog` / `verify-config-catalog` / `verify-dependency-catalog`（生成物新鲜度）、`verify-module-graph`、`verify-translation-pairing`、`verify-md-links/wrap`、`verify-doc-refs`、`verify-export-jsdoc`、`verify-client-ui-i18n`、`verify-package-readme-*` 等。
- **双语 i18n 机制**：一对文档 = `foo.md` + `foo.zh.md` + `foo.i18n.yaml`（按标题分节存 en/zh hash），`pnpm run verify-translation-pairing` 强制结构一一对应。**改动任一语言都要同步三件套**。
- **测试支撑包**：`agent-loop-testkit`、`llm-mock-server`、`llm-replay`、`remote-mock`、`session-snapshot`、`loader-smoke`、`client-runtime`。

### 改造时必须遵守的仓库约定（摘自顶层 [AGENTS.md](AGENTS.md)）

1. **注册即副作用**：一切贡献走 `ctx.effect()` / `ctx.on()`，注册函数返回 disposer。
2. **Waterfall 监听器必须调 `next()`** 才能委托下去，否则短路。
3. **模型可见 ⟺ 已记录**：新增模型可见输入必须新增会话事件。
4. **插件优先，不改循环**：新行为挂到已记录的扩展点；改 `agent-loop` 必须同步更新 `docs/architecture.md`。
5. **能力 seam 三角色齐备**，只在角色独立演进时才拆包。
6. **显式优于隐式**：默认值必须是归属实现里显式的 `resolve(request): Spec` 步骤，**不能**是 `run()` 里藏着的 `?? default`。
7. **插件里不许有硬编码可调项**：会随部署变化的都要是可在 `cordis.yml` 改的 `Config` 字段。
8. **跨边界不透明 id 用 branded 类型**（`Branded<B>`），不用裸 `string`。
9. **类型化同进程边界信任 TypeScript**，只在 parser/config/队列/模型 JSON/持久化/worker/进程/线路边界做运行时校验。
10. **ESM everywhere**，跨包用包名、包内相对导入带 `.ts` 后缀。
11. **客户端 UI 文案归 locale 所有**，禁止硬编码。
12. 有持久决策价值才写 Agent Note；`FIXME`/`TODO`/`XXX` 按紧急度分。

---

## 11. 默认启用 vs 需要显式开启

| | 内容 |
|---|---|
| **base 默认启用** | subagent + `spawn`/`fork` 提供方、`subagent`/`subagent_fork`/`tool-subagent-control`/`list-agents`、jobs、todo、goal + driver + command + tool、plan-mode、skill + skill-filesystem + tool-skill、storage / projection / projection-cache、session-query-sqlite（**搜索关闭**）、mcp-resources、spill、compaction（`compaction-basic` 等）、**`repeat-tool-reminder`**、审批 + 权限预设、`web_search`/`web_fetch` |
| **base 默认关闭** | `tool-ralph`、`skill-badge`、`tool-plugin-manager` + `plugin-manager` |
| **组内部分挂载** | `guard/` 只挂了 `repeat-tool-reminder`（[:456](packages/bundle/base/cordis.patch.yml#L456)）；`guard/timeout-policy` **未在任何 bundle 中挂载**，要用得自己 patch |
| **必须显式启用** | Agent Teams（`experimental/agent-team-profile`）、Schedule（`experimental/schedule-bundle`）、Webhook、MCP client、extensions / cordis 工具（经 `cordis` preset）、SQLite 内容搜索（覆盖 `openAt`）、`terminal`/`tool-terminal`、`lsp`/`tool-lsp`、`str_replace_editor`、持久 bash/pwsh 工具、browser-use / computer-use |
| **平台差异** | base 用 `fs-sandbox`（**不是** `fs-local`）；win32 挂 `pwsh-sandbox` |

---

## 12. 与 steveX 的关系

### 12.1 steveX 现状（作为对照）

| 位置 | 现状 |
|---|---|
| [src/agent/agent.js](src/agent/agent.js)（167 行） | `SteveXAgent`：一条 mod WS 连接 + **每 3 秒轮询** `player`/`f3`/`status` 刷新缓存；无推理、无工具、无循环 |
| [src/agent/agent_manager.js](src/agent/agent_manager.js)（172 行） | 多 agent 的 mod 连接管理 |
| [src/llm/deepseek_client.js](src/llm/deepseek_client.js)（50 行） | 单次 `chat(messages)`：无工具调用、无流式、无重试、无 token 计量 |
| [src/mod/methods.js](src/mod/methods.js) | 51 个 mod 方法（含 `player`/`f3`/`inventory`/`container/*`/`key/*`/`vision/*`…） |
| [src/web/](src/web/) | Express 8090 面板 + `/api/mod/*` 透传 + WS 广播 |

### 12.2 可以直接复用/借鉴的部分

1. **工具注册表 + 执行流水线**（`ctx.tools` + `pre-execute`/`guard`/`post-execute`）——把 51 个 mod 方法注册成工具后，审批、超时、结果改写、并发调度全部白拿。
2. **会话日志与 `deriveMessages()`**——"模型可见即已记录"这条不变量正好对上 steveX 已有的"记忆世界/观察边界"关切；日志即事实来源，回放即重建。
3. **LLM seam**（`registerAdapter`）——替换成 steveX 自己的 DeepSeek 客户端，或直接复用 `llm-deepseek` + `llm-retry` + `token-meter`。
4. **compaction / guard / spill**——长会话压缩、重复调用提醒、大输出落盘，都是现成的。
5. **审批与权限预设**——MC 里有大量不可逆动作（挖方块、丢物品、发包），这套 `ask | never` + 沙箱模式可直接映射成"危险动作需批准"。
6. **jobs**——`run_in_background` 的后台任务模型对"长动作（走过去、等着烧）"很合适。
7. **profile/bundle/patch 组装机制**——steveX 的能力可以做成自己的 bundle，而不是 fork 主仓。
8. **Agent Note 工作流与门禁**——如果 steveX 想长期维护，这套文档/门禁纪律值得抄。

### 12.3 不能直接搬、必须自己写的部分

| 缺口 | 说明 |
|---|---|
| **执行世界完全无关** | dsh 的 seam 是 fs / shell / subprocess / LSP / 浏览器；steveX 的世界是"一个 Minecraft 客户端里的 mod，通过 WS JSON-RPC 暴露 51 个方法"。**没有任何现成 seam 可以复用**，需要新设计一个能力 seam（例如 `ctx.game/ctx.body`）+ 一组 `game_*` 工具。 |
| **感知是拉取式且低频** | 现在 steveX 是 3 秒轮询；dsh 的心智模型是"工具被调用时才观察"。要让模型看到世界状态，要么注册成模型可见上下文（`ctx.systemPrompt.context()`）随请求快照，要么做成显式工具。 |
| **realtime / 时序语义** | 51 个方法里有 `key/*`（按下/松开）与 `批量时序 API` 这类有持续时间的动作，dsh 的工具模型是"一次调用一次结果"；持续动作用 jobs + terminal 式会话（`ctx.terminals` 那种持久会话）更贴合。 |
| **世界状态的持久化语义** | dsh 的持久化是会话日志 + storage domain，而 steveX 的世界状态是 mod 落盘的 `.nbt` / `memory_cells.bin`；两套"真源"必须明确谁说话（建议：游戏侧落盘仍是世界真源，dsh 会话日志只记"模型看到了什么/做了什么"）。 |
| **规模** | 4868 个 TS 文件、逐文件 100% 覆盖率的 CI 门禁、双语三件套——直接 fork 的维护成本远高于"把它当作参考实现，在 steveX 里重写一个薄版"。 |

### 12.4 两条可选路线

- **路线 A：把 dsh 当零件库（推荐先做）**。只摘三块：① 工具注册 + 守卫流水线的心智模型；② Agent 循环的 turn/step 顺序与"补记失败工具结果"；③ 会话日志 + `deriveMessages()` 的投影思想。在 steveX 的 Node 侧自己实现一个薄版（几百行量级），先让"感知 → 决策 → 调 mod → 观察结果"闭环跑起来。
- **路线 B：真 fork dsh，写一个 `dsh-game` bundle**。新增 `game` seam（Service Definition）+ `mod-ws` Provider + `tool-game` Consumer，把 51 个方法包成工具，用 profile patch 组装自己的 profile。**好处**：审批、jobs、compaction、GUI、SDK 全白拿；**代价**：要遵守它的全部约定与门禁（ESM、branded id、逐文件 100% 覆盖率、双语三件套、Agent Note），且升级要跟着上游的破坏性变更走——而它现在还是 0.2.0-rc。

---

## 13. 文档与代码不一致之处（读代码时的坑）

以下是本次核对中发现的偏差，**以代码为准**：

1. **会话格式版本**：写入器是 `SESSION_FORMAT_VERSION = 4`、`latestFinalizedVersion: 4`，但 `latestReleasedVersion: 3` —— V4 迁移尚未在已发布产品数据上被证实。
2. **Desktop 是否使用 webServer**：`docs/subsystems/web-server.zh.md:5` 称 Electron 用 `file://` 加载、不用该服务器；但 `apps/desktop-host/src/index.ts` 明确以 `--port 19387` 启动含 webServer 的 `desktop` profile，`apps/desktop/README.zh.md:3-4` 也说是把 HTTP/WS 转发给已认证的 Web Host。**两处互相矛盾，代码支持后者**。
3. **`dsh tui`**：`apps/cli/src/args.ts:90-100` 的帮助示例出现 `dsh tui`，但仓库里**没有任何 `tui` bundle**（`profile.ts:179-193` 无 tui），注释自己也写了"assuming the tui profile is installed"——是示例，不是已交付形态。
4. **subagent 的 `followup()`**：web-app patch 的注释把 subagent 说成有跨会话 `followup()` 表面，但 seam 的公开方法实为 `sendMessage()`；`followup` 只在工具层/收件箱语义里出现。
5. **能力表漏项**：`docs/capability-seams.zh.md` 列 shell 提供方时漏了 `packages/shell/pwsh-sandbox`（base 在 win32 挂它）。
6. **`SessionStartSource` 的 `'clear'/'compact'`**：值已保留但**没有发出方**，文档自己也承认。
7. **`fs-observation-policy`**：文档称其为"可选插件"，但 base 实际默认挂载。

---

## 14. 建议的下一步

1. **先 dump 一遍配置树**：在有 Node 22+ 的环境 `pnpm install && pnpm dsh --profile web --dump-config`，看真实清单再决定改造面。
2. **明确要摘哪条路线**（§12.4 的 A 还是 B）——这决定后续所有工作的形态。
3. **若走 A**：先定义 steveX 侧的 seam 边界（游戏世界 seam 的 Service Definition 长什么样），再定工具清单（51 个方法哪些要暴露给模型、哪些只是内部实现细节）。
4. **若走 B**：先读 [docs/cookbook/adding-a-package.zh.md](docs/cookbook/adding-a-package.zh.md)、[adding-a-tool.zh.md](docs/cookbook/adding-a-tool.zh.md)、[adding-an-llm-adapter.zh.md](docs/cookbook/adding-an-llm-adapter.zh.md)，再从最小 bundle 起步。
5. **无论哪条**：把"世界状态的真源在哪"先定死，避免会话日志与 mod 落盘 `.nbt` 双真源打架。
