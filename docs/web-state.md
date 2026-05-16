# steveX Web 面板状态文档

> 最后更新：2026-05-17
> 对应代码：`src/web/`

---

## 一、架构总览

```
浏览器 (Web Panel)
    │
    ├── HTTP 请求 ──→ Express (app.js → routes.js) ──→ AgentManager
    │
    ├── WebSocket ──→ ws.js (事件总线桥接) ──→ AgentManager.eventBus
    │
    └── 静态文件 ←── public/ (index.html + style.css + ES Module app.js)
```

---

## 二、后端结构 (`src/web/server/`)

### 2.1 文件职责

| 文件 | 职责 |
|------|------|
| `src/web/server.js` | HTTP 服务入口，创建 Express + WebSocket 并监听端口 |
| `src/web/server/app.js` | Express 应用工厂，挂载静态文件 + 路由 |
| `src/web/server/routes.js` | REST API 路由定义 |
| `src/web/server/ws.js` | WebSocket 服务，桥接 AgentManager.eventBus → 浏览器 |

### 2.2 REST API 清单

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| `GET` | `/api/status` | 获取所有 Agent 状态 + uptime | ✅ |
| `POST` | `/api/reload` | 重载配置文件 | ✅ |
| `POST` | `/api/agents/:name/connect` | 连接指定 Agent | ✅ |
| `POST` | `/api/agents/:name/disconnect` | 断开指定 Agent | ✅ |
| `POST` | `/api/agents/:name/command` | 向指定 Agent 发送指令 | ✅ |

### 2.3 WebSocket 事件类型

| 事件 | 方向 | 说明 | 实现 |
|------|------|------|------|
| `snapshot` | 服务器→客户端 | 连接时立即推送 + 每 3 秒定时推送 | ✅ |
| `agent:connect` | 服务器→客户端 | Agent 上线通知 | ✅ |
| `agent:disconnect` | 服务器→客户端 | Agent 下线通知 | ✅ |
| `agent:command:start` | 服务器→客户端 | 命令开始执行 | ✅ |
| `agent:command:done` | 服务器→客户端 | 命令执行完成（含 ok/error/output） | ✅ |
| `agent:llm:input` | 服务器→客户端 | LLM 输入（预留，Phase 1 使用） | ✅ |
| `agent:llm:output` | 服务器→客户端 | LLM 输出（预留，Phase 1 使用） | ✅ |

---

## 三、前端结构 (`src/web/public/`)

### 3.1 文件树

```
public/
├── index.html          # 主页面 (侧边栏 + 顶栏 + 控制栏 + Agent 列表)
├── style.css           # 全局样式 (CSS variables + 响应式 + log-panel)
├── app.js              # ES Module 入口
├── lib/
│   ├── utils.js        # 工具函数 (escapeHtml, truncate)
│   ├── state.js        # 全局响应式状态 (agents/logs/filters + pub/sub)
│   ├── api.js          # HTTP API 客户端封装
│   ├── ws-client.js    # WebSocket 客户端
│   └── icons.js        # SVG 图标集 + hydrateIcons
└── pages/
    └── agents.js       # Agents 页面 (渲染 + 事件处理)
```

### 3.2 前端架构说明

**方案：ES Modules，零依赖，浏览器原生按需加载**

`index.html` 通过 `<script type="module" src="/app.js">` 加载入口，所有依赖通过 `import`/`export` 自动解析。

**数据流：**

```
API/WebSocket ──→ state.js (全局状态) ──→ subscribe ──→ 重新渲染
                                      ──→ 直接调用 renderAgents()
```

**核心设计：Smart Diff 渲染**
- `agents.js` 的 `renderAgents()` 不整体替换 innerHTML，而是：
  - 已存在的卡片 → 仅更新动态字段 (online 状态、username)
  - 新增的 Agent → 创建新卡片附加
  - 已删除的 Agent → 移除对应卡片
- 日志面板也采用追加式渲染，每次只添加新条目

### 3.3 模块职责

| 模块 | 导入 | 导出 | 说明 |
|------|------|------|------|
| `app.js` | state, ws, api, icons, agents | — | 入口：初始化 WS、首屏加载、全局事件绑定 |
| `lib/state.js` | — | getState, setState, subscribe, patchAgent, addLog | 全局单一数据源 + 发布订阅 |
| `lib/api.js` | state | fetchStatus, reloadConfig, connectAgent, disconnectAgent, sendCommand | HTTP REST 封装 |
| `lib/ws-client.js` | state | initWebSocket | WebSocket 客户端 + 事件分发 |
| `lib/utils.js` | — | escapeHtml, truncate | 纯工具函数 |
| `lib/icons.js` | — | iconMap, hydrateIcons | SVG 图标注册与注入 |
| `pages/agents.js` | state, api, utils, icons | renderAgents, initAgents | Agent 卡片渲染 + 交互事件 |

### 3.4 全局状态结构 (`state.js`)

```javascript
{
  agents: [],                    // Agent 数组，每个只含 { name, online, username? }
  uptimeSec: 0,                  // 服务端 uptime (秒)
  wsConnected: false,            // WebSocket 连接状态
  filters: {
    query: '',                   // 搜索关键词
    status: 'all',               // all | online | offline
    sortBy: 'name'               // name | status | health
  },
  logs: {                        // 日志存储，按 agent 名称分组
    'agent-1': [ LogEntry, ... ],
    'agent-2': [ LogEntry, ... ]
  }
}
```

---

## 四、功能对照表

### 4.1 已实现功能（来自 `web_public_old/app.js` 的真实逻辑）

| 功能 | 触发方式 | 后端 API | 前端处理 |
|------|----------|----------|----------|
| 初始状态加载 | 页面加载 | `GET /api/status` | `api.fetchStatus()` → `state.setState()` |
| 重载配置 | 点击 Reload Config 按钮 | `POST /api/reload` | `api.reloadConfig()`，1.5s 后自动刷新 |
| 连接 Agent | 点击 Connect 按钮 | `POST /api/agents/:name/connect` | `api.connectAgent()` |
| 断开 Agent | 点击 Disconnect 按钮 | `POST /api/agents/:name/disconnect` | `api.disconnectAgent()` |
| 发送命令 | 命令输入框 + Send 按钮 | `POST /api/agents/:name/command` | `api.sendCommand()`，结果写入日志 |
| WebSocket 实时推送 | 自动连接 | ws.js eventBus 桥接 | `ws-client.js` 分发到 state/addLog |
| Agent 状态实时更新 | WS snapshot / agent:connect / agent:disconnect | ws.js 自动推送 | `patchAgent()` 更新 online 字段 |
| 命令日志实时推送 | WS agent:command:start / done | ws.js 自动推送 | `addLog()` 追加到对应 agent 日志 |
| LLM 日志实时推送 | WS agent:llm:input / output | ws.js 自动推送 | `addLog()` 追加到对应 agent 日志 |
| 日志面板 | 每张 agent 卡片内嵌 | — | `renderLogPanel()` 追加渲染，最多 200 条 |
| WebSocket 断线重连 | WS 断开后自动 | — | 2 秒后 `location.reload()` |
| 搜索过滤 | 搜索框输入 | — | 按 agent.name 过滤 |
| 状态筛选 | 下拉选择 all/online/offline | — | 过滤 agents |
| 排序 | 下拉选择 name/status/health | — | 排序 agents（health 使用占位符值） |

### 4.2 占位功能（UI 存在但未接入后端）

| 功能 | 当前行为 | 计划 |
|------|----------|------|
| **New Agent** 按钮 | `alert('Coming soon')` | Phase 1 后期实现动态创建 Agent |
| **Send Message (to LLM Planner)** 按钮 | `alert('Coming soon')` | Phase 1 接入 LLM 聊天 |
| 侧边栏非 Agents 页面 | `alert('Coming soon')` | 各页面按需实现 |
| Health 显示 | 固定占位 `20 / 20` | Phase 1 从 API 读取真实血量 |
| Game Mode 显示 | 固定占位 `Survival` | 从 API 读取 |
| Model 显示 | 固定占位 `deepseek-v4-flash` | 从配置文件读取 |
| Position 显示 | 固定占位 `x: ~, y: ~, z: ~` | 从 API 读取真实坐标 |
| Current Action 显示 | 固定占位 `Idle` | 从 Agent 状态读取 |

### 4.3 UI 功能清单

| 元素 | 状态 |
|------|------|
| 侧边栏导航 (8 个入口) | ✅ 全部渲染，非 Agents 页弹 Coming soon |
| 品牌标识 (steveX + Debug Console) | ✅ |
| WS 状态指示 (顶栏 + 侧边栏) | ✅ 实时反映 wsConnected |
| Uptime 显示 | ✅ 服务端 uptime + 客户端 ticker |
| 搜索框 | ✅ 实时过滤 |
| 状态筛选下拉 | ✅ all / online / offline |
| 排序下拉 | ✅ name / status / health |
| New Agent 按钮 | ⛔ Coming soon |
| Reload Config 按钮 | ✅ 带点击动画 |
| Agent 卡片 | ✅ 精美 UI + 真实 API 交互 |
| Agent 卡片 - 状态指示 (dot + label) | ✅ |
| Agent 卡片 - Username 显示 | ✅ 从 API 读取 |
| Agent 卡片 - Stats 面板 (6 项) | ✅ 5 项占位 + Status 项实时 |
| Agent 卡片 - Connect/Disconnect 按钮 | ✅ 调用真实 API |
| Agent 卡片 - Send Command 面板 | ✅ 调用真实 API |
| Agent 卡片 - Send Message 面板 | ⛔ Coming soon |
| Agent 卡片 - Log Panel | ✅ 实时追加，最多 200 条 |
| 空状态提示 | ✅ "No agents match the current filters." |
| 响应式布局 | ✅ 1280px / 860px / 560px 断点 |

---

## 五、UI 设计规范

### 5.1 设计令牌 (CSS Variables)

```css
--bg: #fbfaf8              /* 主背景 */
--panel: #ffffff           /* 面板背景 */
--text: #171513            /* 主文字 */
--muted: #7b746d           /* 次要文字 */
--accent: #d76625          /* 强调色（橙色） */
--success: #43ad51         /* 成功/在线色（绿色） */
--sidebar-width: 252px     /* 侧边栏宽度 */
```

### 5.2 布局

```
┌──────────────┬─────────────────────────────────────────┐
│   Sidebar    │  Topbar (title + uptime + ws + reload)  │
│   (252px)    ├─────────────────────────────────────────┤
│              │  Controls (search + filter + sort + btn) │
│  nav items   ├─────────────────────────────────────────┤
│  ×8          │                                         │
│              │  Agent Cards (列表)                      │
│              │  ┌─────────────────────────────────────┐│
│              │  │ Agent Header (name + status + btns) ││
│              │  │ Stats Panel │ Command Panel         ││
│              │  │ Log Panel                           ││
│              │  └─────────────────────────────────────┘│
│  ws status   │                                         │
└──────────────┴─────────────────────────────────────────┘
```

### 5.3 日志条目样式

```
log-entry.cmd-start   → "[CMD] → command"
log-entry.cmd-done    → "[CMD] ← command\noutput"
log-entry.cmd-error   → "[CMD] ✕ command\noutput"  (红色)
log-entry.llm-input   → "[LLM] → model\nprompt..."
log-entry.llm-output  → "[LLM] ← model\nresponse..."
```

每次渲染带时间戳 `HH:MM:SS`，prompt/response 超过 2000 字符自动截断。

---

## 六、后续规划

### 立即
- [x] 从 `web_public_old/` 迁移真实 WebSocket + API 逻辑到新 UI 框架
- [x] 按功能拆分为独立 ES Module 文件
- [ ] 修复 `agents.js` 中未使用的 `iconMap` import

### Phase 1
- [ ] 接入真实 Agent 状态字段 (health/mode/position/action)
- [ ] 实现 Send Message (to LLM Planner) 功能
- [ ] 实现 New Agent 功能
- [ ] 实现 Overview/World/Tasks/Configs/Logs 独立页面
- [ ] 添加 LLM 对话聊天面板

### Phase 2+
- [ ] 多 Agent 协作视图
- [ ] Git 历史查看器
- [ ] Capsule 管理界面
- [ ] Benchmark 运行面板
