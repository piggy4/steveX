# 批量时序 API 设计方案（大脑端统一延时/序列）

> **定位**：把"延时 + 有序步骤"从 8090 面板的临时文本语法（`delay:NN` 前缀）提升为
> **steveX 大脑端的一等 HTTP API**，浏览器、curl、脚本、未来 LLM 工具调用走同一套规范。
>
> **版本记录**
> - **v1（定稿，实现待审阅）**：定案
>   - 新增 `POST /api/mod/batch`：一次提交有序步骤数组，8090 服务端按序逐条透传 mod，步骤间延时统一用**步前 `waitMs`** 字段表达（纯等待步 = 只有 `waitMs` 没有 `method`）。
>   - 执行权从浏览器移到大脑端：浏览器不再逐条 `await`、不再自己掐表；**中止与"已按住按键"的兜底松开统一由服务端负责**，消灭两处重复实现（`agents.js` 与 `control_panel.html` 各写一份）。
>   - 经用户确认**不做** `holdMs` 一键按住的便捷糖：按住→等待→松开仍需显式写成多步（最通用）。
>   - 后台执行 + `batchId` 轮询查询 + stop 接口（不做单条 HTTP 长连承载整段序列，避免代理超时/断连误停）。
>   - 路由注册顺序约束：`/api/mod/batch*` 三条路由必须注册在 `POST /api/mod/*splat` 通配**之前**（Express 按注册序匹配，否则 `batch` 会被当成方法名透传）。

## 1. 背景与动机

现状事实（均已核实）：

1. **`delay:NN` 只是浏览器文本解析器的 hack，不是 API。**
   - 8090 面板 [agents.js:580-607](src/web/public/pages/agents.js) 用 `parseBatchLine` 正则
     `/^delay:(\d+)\s+/` 从每行文字里抠出等待，再在 [agents.js:644-686](src/web/public/pages/agents.js)
     `runModBatch` 里逐条 `await` 调 `POST /api/mod/:method` 并按序执行。**延时时序只活在浏览器 JS 里。**
   - 遗留 `control_panel.html`（直连 mod WS 25550）在 `parseLine`/`batchCall`
     （[control_panel.html:294-331](control_panel.html)）里**又抄了一份几乎一样的解析/执行/延时**。
2. **8090 HTTP 透传层没有时序/序列概念**（[mod_routes.js:15-38](src/web/server/mod_routes.js)）：
   一次一调。谁要用 curl / 未来 LLM 工具走 API，都表达不了"按住→等 3000→松开"，只能自己在客户端掐表。
3. **"按键按住期间的兜底松开"逻辑也分散在浏览器**：`agents.js` 维护
   `continuousKeyMethods`/`activeKeys`，batch 结束/中止时并发补发 `pressed:false`
   （[agents.js:532-578](src/web/public/pages/agents.js)）。API 层没有这个保证。

## 2. 目标 / 非目标

**目标**
- 一个 API 承载"有序步骤 + 步间延时 + 中止 + 按键兜底"，所有调用方统一。
- 8090 面板 "Mod Batch" 改为提交结构化步骤 JSON（每行一个 step），`delay:` 前缀废除。
- 服务端是时序的唯一权威：浏览器/curl/未来 LLM 得到**同一语义**。

**非目标**
- 不做 mod 端改动（mod 仍是单方法 WS JSON-RPC，能力不变）。
- 不做 `holdMs` 一键按住糖（用户已确认只要步前 `waitMs`）。
- 不改单次透传路径（`POST /api/mod/:method` 与 `POST /api/mod` 保持原样）。
- `control_panel.html`（直连 mod 的遗留原始 WS 调试器）**本期不迁移、保留可用**：
  它不经过大脑 API 面，属遗留调试工具，后续可整体退役；README 标注其仍用旧语法。

## 3. 步骤契约（Step）

统一规范：**每个步骤 = "先等 `waitMs`，再（可选）执行一次 mod 调用"**。`delay:` 前缀的语义
（执行该行前先等）被无损吸收为 `waitMs` 字段。

```
Step =
  | 调用步  { method: string, params?: object, waitMs?: int≥0 }   // waitMs>0：先等再调用
  | 纯等待步 { waitMs: int>0 }                                     // method 缺省；纯暂停
```

- `waitMs` 缺省 = 0（立即）。负值/非整数 → 400。
- 纯等待步要求 `waitMs > 0`；放序列末尾即"尾部延时"。
- `method` 不做服务端白名单校验（沿用透传层宽松语义，未知方法由 mod 返回错误），但必须为字符串。
- `params` 沿用透传层规则：非数组对象，缺省 `{}`。

**等价改写示例**（Mod Batch 文本框）：

| 旧（delay: 前缀） | 新（统一 step JSON） |
|---|---|
| `{"method":"key/up","params":{"pressed":true}}` | 同左 |
| `delay:3000 {"method":"key/up","params":{"pressed":false}}` | `{"waitMs":3000,"method":"key/up","params":{"pressed":false}}` |
| 只想要一个停顿 | `{"waitMs":3000}` |
| `delay:500 {"method":"player","params":{}}` | `{"waitMs":500,"method":"player","params":{}}` |

**标准示例（用户确认的典型手势："等待 3s → 按住前进 2s → 松开"）**——纯等待步 + press/release
成对调用，正是本模型下"持续按住 N 毫秒"的写法：

```
{"waitMs":3000}                                     // t=0~3000  等待
{"method":"key/up","params":{"pressed":true}}      // t=3000    按住前进（key/up = W 前进）
{"waitMs":2000}                                     // t=3000~5000  保持按住
{"method":"key/up","params":{"pressed":false}}     // t=5000    松开
```

## 4. API 契约

### 4.1 POST `/api/mod/batch` —— 启动一段序列

请求体：

```jsonc
{
  "steps": [ {"method":"key/up","params":{"pressed":true}},
             {"waitMs":3000},
             {"method":"key/up","params":{"pressed":false}} ],
  "stopOnError": false        // 可选，默认 false = 某步 mod 调用失败只记录、继续（与现浏览器 batch 一致）
}
```

语义：
- 立即返回 `batchId`，**后台串行执行**（不等整段完成）。理由：单条 HTTP 长连承载数秒~数分钟的
  序列易被代理/浏览器超时，且断连即误停；后台 + 轮询更稳、也更适合非浏览器调用方。
- 严格串行：每步 `await` 完再下一步；`waitMs` 后才发调用。次序保证 = 单连接 + 逐步 await。
- **按键兜底**：维护 `activePressed`（`pressed:true` 的连续键方法加入，`pressed:false` 移出）；
  序列结束 / stop / 连接断开时，对仍按住的键并发补发 `pressed:false`（尽力而为，短超时，
  与现 [agents.js:565-578](src/web/public/pages/agents.js) 语义一致，只是挪到服务端统一做）。
- **并发**：全局单槽——同一时刻只允许一个 batch 在跑；已有 → `409` 并回 `runningId` 供调用方先 stop。
- **stop 时序语义**：只能在步骤边界停止。正在"等待 waitMs"或未开始的步立即取消；若某步的
  mod 调用已发出则等它在途返回（mod 调用无法掐断，与浏览器现状一致：abort 也只是丢响应、请求可能已到 mod）。

响应：

| 状态码 | 场景 | body |
|---|---|---|
| 200 | 已开始 | `{ ok:true, batchId }` |
| 400 | 参数错（steps 非法等） | `{ ok:false, error }` |
| 409 | 已有 batch 在跑 | `{ ok:false, error, runningId }` |
| 502 | mod 未连接 | `{ ok:false, error:'Mod not connected' }` |

### 4.2 GET `/api/mod/batch/:batchId` —— 查询进度

响应 `200`：

```jsonc
{
  "ok": true,
  "batch": {
    "batchId": "…",
    "status": "running",            // running | done | stopped | failed
    "stopOnError": false,
    "startedAt": 1730000000000,
    "finishedAt": null,             // terminal 后非空
    "steps": [
      { "index":0, "waitMs":0, "method":"key/up", "params":{"pressed":true},
        "state":"done",             // pending | waiting | running | done | skipped
        "result": {"ok":true,"data":{…}} }      // state=done 才有；失败为 {ok:false,error}
      // …每步含 waitStartMs / callStartMs / finishMs 时间戳（便于前端/调用方显示"等待中"）
    ]
  }
}
```

未知 `batchId` → `404 { ok:false, error }`。

### 4.3 POST `/api/mod/batch/:batchId/stop` —— 中止

- 请求停止；在途步跑完后停，未开始/等待中的步置 `skipped`；触发按键兜底松开。
- 幂等：已 terminal 再 stop → `200` 原状态；未知 id → `404`。

### 4.4 路由注册顺序（关键约束）

`POST /api/mod/batch` 与 `POST /api/mod/batch/:id/stop` 都是 POST，会被
`mod_routes.js` 里已注册的 `POST /api/mod/*splat` 通配吞掉（把 `batch` 当地图方法名）。
**三条 `/api/mod/batch*` 路由必须放在 `registerModRoutes` 内、splat 通配之前注册。**
`GET /api/mod/batch/:id` 不受影响（透传仅 POST），但为可读性一起排在前面。

## 5. 服务端实现结构

- **新文件 `src/web/server/mod_batch.js`**：
  - module 级 store `Map<batchId, Run>` + `currentRunId`（全局单槽锁）。
  - `startBatch(manager, { steps, stopOnError })`：校验 → 返回 `batchId`（或 409 语义）。
  - 内部 `run()`：逐步 `step.waitMs ? sleep(waitMs)` → `client.call(method, params)` → 记录 result；
    维护 `activePressed`；结束/stop/断连统一走 `finish()` 兜底松键。
  - `getBatch(id)` / `stopBatch(id)`；`stopBatch` 置标志，让 sleep 提前 resolve。
  - 通过 `manager.eventBus.emit('batch:step'|'batch:done', …)` 广播（供 ws.js 后续可做实时推送；本期前端用轮询，事件先留口）。
- **按键元数据单源化**：`continuousKeyMethods` 列表从 `agents.js` 提出为共享常量
  （如 `src/mod/pressable_methods.js`，`module.exports = { PRESSABLE_METHODS }`），服务端 runner 引用；
  `agents.js` 不再需要自己维护（其 batch 路径删除，见 §6）。
- **`mod_routes.js`**：文件顶部 `registerModRoutes(app, manager)` 内先注册 batch 三条路由
  （新路由入口可放 `mod_batch.js` 的 `registerBatchRoutes(app, manager)`，在 mod_routes 里先调用）。
- **`ws.js`**：本期不加新广播（轮询够用）；`mod_batch.js` 已 emit 的 `batch:*` 事件留待后续接实时推送。← 若审阅认为要实时推送再纳入。

## 6. 前端改动（8090 Mod Batch 面板，`agents.js`）

删除（batch 路径内）：
- `delay:` 前缀正则解析（`parseBatchLine`）；
- `waitForDelay`、`activeKeys`、`continuousKeyMethods`、`releaseActiveKeys`、
  `batchControllers`/AbortController 的编排逻辑——全部由服务端接管。

改为：
- 每行 = 一个 Step JSON（`#`/`//` 注释行与空行跳过，沿用现状）。
- Run → `POST /api/mod/batch {steps}` → 拿 `batchId` → 每 ~400ms `GET` 轮询一次 →
  把新出现的 done 步结果追加进结果区（含 waitMs/等待态显示，标 `… wait Nms → line N method`）。
- terminal（done/stopped/failed）→ 收尾显示 `batch finished/stopped`，恢复 Run、隐藏 Stop。
- Stop 按钮 → `POST …/stop`（不再走浏览器 abort）。
- 面板标题与 placeholder（[agents.js:216-223](src/web/public/pages/agents.js)）同步改为新 step 写法。
- "Call Mod Method" 单次面板原定**不动**；后续已改为 **Mod API 查询器**（搜索下拉 + step JSON
  模板/填参，只读不执行；执行走 Mod Batch），见 `docs/已实现内容.md` §2.6 ——本句不再成立。

## 7. 兼容与文档迁移

- `delay:NN` 前缀在 8090 Mod Batch **不再解析**——含旧前缀的行解析报错，提示改 `waitMs`（避免两套并存）。
- 同步更新引用旧语法处：`README.md` 调试/批量示例、`docs/PR11-人工测试指南.md` §6（Batch Runner
  用例改为新 step JSON）、`docs/已实现内容.md` 追加一条。
- `control_panel.html`（直连 WS 遗留）保留旧语法不动，README 标注其不受本期统一约束。

## 8. 测试（人工 + curl 冒烟）

沿用 PR11 §6 的用例形态，换成新语法：

1. **顺序与延时**：
   ```text
   {"method":"status","params":{}}
   {"waitMs":300,"method":"player","params":{}}
   ```
   通过：按序两条成功；结束时 `batch finished`；Run 恢复、Stop 隐藏。
2. **等待期即时停止**：`{"method":"status"} / {"waitMs":5000,"method":"player"}` → Run 后在等待期 Stop →
   不等满 5s 立即 `batch stopped`，第二条 `player` 不执行（skipped）。
3. **持续按键清理**：`{"method":"key/up","params":{"pressed":true}}` + `{"waitMs":500}` 结束 →
   结束/停止时输出 `released key/up`，玩家不持续移动。
4. **并发拒绝**：一个 batch 运行时再 POST 一个 → `409` + `runningId`；stop 后再次 POST → `200`。
5. **curl 冒烟**：`POST /api/mod/batch` → `GET /api/mod/batch/:id` 轮询 → `POST …/stop`。

## 9. 代码改动清单（审阅确认范围）

| 文件 | 改动 |
|---|---|
| `src/web/server/mod_batch.js` | 新增：runner + 单槽锁 + store + stop + 按键兜底 |
| `src/mod/pressable_methods.js` | 新增：`PRESSABLE_METHODS` 共享常量 |
| `src/web/server/mod_routes.js` | 顶部先注册 batch 三条路由（在 `*splat` 之前） |
| `src/web/public/pages/agents.js` | Mod Batch 面板改结构化步骤 + 轮询 + stop |
| `src/web/public/lib/api.js` | 增 `startBatch/getBatch/stopBatch` 三个 fetch helper |
| `README.md` | 批量示例/调试段落更新 |
| `docs/PR11-人工测试指南.md` | §6 用例改新语法 |
| `docs/已实现内容.md` | 追加本特性记录 |
| `src/web/server/ws.js` | （可选，默认不做）接 `batch:*` 实时推送 |

> 交互主文档：[视觉api设计方案.md](视觉api设计方案.md) 面向视觉采集/记忆体系；本文档面向大脑控制面，二者平行。
