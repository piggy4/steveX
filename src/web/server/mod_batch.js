const crypto = require('crypto')
const { isPressableMethod } = require('../../mod/pressable_methods')

/**
 * 批量时序执行器（设计 docs/批量时序API设计方案.md v1）。
 *
 * 把"有序步骤 + 步间延时"做成 8090 大脑端的一等能力，替代浏览器端 delay: 前缀 hack：
 *
 * - POST /api/mod/batch         启动一段序列（后台串行执行，立即返回 batchId）
 * - GET  /api/mod/batch/:id     查询进度（每步 state/result）
 * - POST /api/mod/batch/:id/stop  中止（等待中的步立即取消；已发出的 mod 调用在途返回后停）
 *
 * 步骤契约：
 *   调用步   { method, params?, waitMs? }   waitMs = 执行前先等（毫秒）
 *   纯等待步 { waitMs }                     method 缺省；仅暂停
 *
 * 时序保证：
 *   - 严格串行：每步 await 完再下一步，次序 = 单连接 + 逐步 await。
 *   - 全局单槽：同一时刻只允许一个 running batch（防两段序列在单连接上交错）。
 *   - 按键兜底：track pressed:true 的连续键，结束时对仍按住的并发补发 pressed:false。
 *   - stopOnError=false（默认）单步 mod 错误只记录继续；true 则失败即停。
 *   - 连接性故障（未连接/断连/发送失败）无论 stopOnError 都整段终止（status=failed）。
 */

/** 已结束 batch 保留时长（让迟到的轮询/调用方能读到终态），随后从内存清掉。 */
const RETENTION_MS = 5 * 60 * 1000

const runs = new Map() // batchId -> run

// ==================== 启动 ====================

/**
 * 校验并启动一段序列。同步返回结果对象（不 await 执行，执行在后台 runLoop）。
 * @returns {{ok:true,batchId:string} | {ok:false,http:number,error:string,runningId?:string}}
 */
function startBatch(manager, body) {
  const raw = (body && typeof body === 'object' && !Array.isArray(body)) ? body : {}
  const norm = normalizeSteps(raw.steps)
  if (norm.error) return { ok: false, http: 400, error: norm.error }

  const running = runningBatch()
  if (running) {
    return { ok: false, http: 409, error: 'a batch is already running', runningId: running.id }
  }

  const client = manager.getModClient ? manager.getModClient() : null
  if (!client || !client.isConnected()) {
    return { ok: false, http: 502, error: 'Mod not connected' }
  }

  const run = {
    id: crypto.randomUUID(),
    status: 'running',                 // running | done | stopped | failed
    stopOnError: raw.stopOnError === true,
    startedAt: Date.now(),
    finishedAt: null,
    steps: norm.steps,
    activePressed: new Set(),          // 当前按住中的连续键方法名
    releasedKeys: [],                  // 结束/中止时兜底松开成功的键
    releaseErrors: [],
    stopRequested: false,
    _stopWaitFns: []
  }
  runs.set(run.id, run)

  // 后台执行；runLoop 内所有终态路径都走到 finish()（await 兜底松键后置 finishedAt）
  runLoop(manager, run).catch(() => finish(manager, run, 'failed'))

  return { ok: true, batchId: run.id }
}

/** 步骤 schema 校验 + 归一化。 */
function normalizeSteps(rawSteps) {
  if (!Array.isArray(rawSteps) || rawSteps.length === 0) {
    return { error: 'steps must be a non-empty array' }
  }
  const steps = []
  for (let i = 0; i < rawSteps.length; i++) {
    const s = rawSteps[i]
    if (!s || typeof s !== 'object' || Array.isArray(s)) {
      return { error: `steps[${i}] must be an object` }
    }
    const waitMs = s.waitMs == null ? 0 : s.waitMs
    if (!Number.isInteger(waitMs) || waitMs < 0) {
      return { error: `steps[${i}].waitMs must be a non-negative integer` }
    }

    if (typeof s.method === 'string' && s.method) {
      const params = s.params && typeof s.params === 'object' && !Array.isArray(s.params) ? s.params : {}
      steps.push({ index: i, waitMs, method: s.method, params, state: 'pending', result: null })
    } else if (s.method == null) {
      if (waitMs <= 0) return { error: `steps[${i}]: a pure-wait step needs waitMs > 0` }
      steps.push({ index: i, waitMs, method: null, params: null, state: 'pending', result: null })
    } else {
      return { error: `steps[${i}].method must be a non-empty string` }
    }
  }
  return { steps }
}

function runningBatch() {
  for (const run of runs.values()) {
    if (run.status === 'running') return run
  }
  return null
}

// ==================== 执行 ====================

async function runLoop(manager, run) {
  const steps = run.steps
  for (let i = 0; i < steps.length; i++) {
    if (run.stopRequested) {
      markFrom(run, i, 'skipped')
      break
    }
    const step = steps[i]

    // 1) 步前等待（纯等待步的"动作"就是这个等待本身）
    if (step.waitMs > 0) {
      step.state = 'waiting'
      step.waitStartMs = Date.now()
      const waited = await waitStopable(run, step.waitMs)
      if (!waited) {
        // stop 在等待期间到达：当前步未执行 → 置 skipped，整段按 stopped 收尾
        markFrom(run, i, 'skipped')
        await finish(manager, run, 'stopped')
        return
      }
    }
    if (run.stopRequested) {
      markFrom(run, i, 'skipped')
      break
    }

    // 纯等待步：等待结束即完成
    if (step.method == null) {
      step.state = 'done'
      step.finishMs = Date.now()
      continue
    }

    // 2) mod 调用步
    const client = manager.getModClient ? manager.getModClient() : null
    if (!client || !client.isConnected()) {
      step.result = { ok: false, error: 'Mod not connected' }
      step.state = 'done'
      step.finishMs = Date.now()
      markFrom(run, i + 1, 'skipped')
      await finish(manager, run, 'failed')
      return
    }

    step.state = 'running'
    step.callStartMs = Date.now()
    if (isPressableMethod(step.method) && step.params.pressed !== false) {
      run.activePressed.add(step.method)
    }
    const res = await client.call(step.method, step.params || {})
    if (res.ok && isPressableMethod(step.method) && step.params.pressed === false) {
      run.activePressed.delete(step.method)
    }

    step.result = res.ok ? { ok: true, data: res.data } : { ok: false, error: res.error || 'unknown mod error' }
    step.state = 'done'
    step.finishMs = Date.now()

    if (!res.ok) {
      // 连接性故障 → 整段终止；普通 mod 错误 → 由 stopOnError 决定
      if (isConnectivityError(res)) {
        markFrom(run, i + 1, 'skipped')
        await finish(manager, run, 'failed')
        return
      }
      if (run.stopOnError) {
        markFrom(run, i + 1, 'skipped')
        await finish(manager, run, 'failed')
        return
      }
    }
  }

  // 走完整个序列（或 stop 打断）后的正常终态
  await finish(manager, run, run.stopRequested ? 'stopped' : 'done')
}

/** 可被 stop 提前打断的等待：正常等待结束 resolve(true)；stop 打断 resolve(false)。 */
function waitStopable(run, ms) {
  return new Promise((resolve) => {
    let timer = null
    const removeFn = () => {
      run._stopWaitFns = run._stopWaitFns.filter((fn) => fn !== onStop)
    }
    const onStop = () => {
      if (timer) clearTimeout(timer)
      removeFn()
      resolve(false)
    }
    run._stopWaitFns.push(onStop)
    timer = setTimeout(() => {
      removeFn()
      resolve(true)
    }, ms)
  })
}

/**
 * 终态收尾（唯一入口，runLoop 的所有 return 都经它，await 保证 releasedKeys 先于
 * finishedAt 就绪）：兜底松开仍按住的连续键 → 发布终态 → 定时清理。
 */
async function finish(manager, run, status) {
  if (run.finishedAt != null) return

  const client = manager.getModClient ? manager.getModClient() : null
  const held = [...run.activePressed]
  if (held.length) {
    // Keep the execution slot until every release settles.
    const results = await Promise.all(
      held.map(async (method) => {
        try {
          const r = client && client.isConnected()
            ? await client.call(method, { pressed: false })
            : { ok: false, error: 'Mod not connected' }
          return { method, ok: r.ok, error: r.error }
        } catch (err) {
          return { method, ok: false, error: err.message || String(err) }
        }
      })
    )
    for (const { method, ok, error } of results) {
      if (ok) {
        run.releasedKeys.push(method)
        run.activePressed.delete(method)
      } else {
        run.releaseErrors.push({ method, error: error || 'release failed' })
      }
    }
  }

  run.status = run.releaseErrors.length || status === 'failed'
    ? 'failed'
    : run.stopRequested ? 'stopped' : status
  run.finishedAt = Date.now()
  run._pruneTimer = setTimeout(() => runs.delete(run.id), RETENTION_MS)
  run._pruneTimer.unref?.()
  if (manager.eventBus) {
    manager.eventBus.emit('batch:done', {
      batchId: run.id, status: run.status,
      releasedKeys: run.releasedKeys, releaseErrors: run.releaseErrors
    })
  }
}

/** 把 [startIdx, end) 里尚未终态（pending/waiting）的步置为给定 state。 */
function markFrom(run, startIdx, state) {
  for (let i = startIdx; i < run.steps.length; i++) {
    const s = run.steps[i]
    if (s.state === 'pending' || s.state === 'waiting') s.state = state
  }
}

// ==================== 查询 / 停止 ====================

/** 取某 batch 的运行态；不存在返回 null。 */
function getBatch(batchId) {
  return runs.get(batchId) || null
}

/** 请求停止某 batch：等待中的步立即取消（runLoop 观察到 stopRequested 后收尾）。 */
function stopBatch(batchId) {
  const run = runs.get(batchId)
  if (!run) return { ok: false, error: 'batch not found' }
  if (run.finishedAt == null && run.status === 'running') {
    run.stopRequested = true
    for (const fn of run._stopWaitFns) fn()
    run._stopWaitFns = []
  }
  return { ok: true }
}

/** 可安全序列化给 HTTP/前端的 run 视图。 */
function serializeRun(run) {
  return {
    batchId: run.id,
    status: run.status,
    stopOnError: run.stopOnError,
    startedAt: run.startedAt,
    finishedAt: run.finishedAt,
    releasedKeys: run.releasedKeys,
    releaseErrors: run.releaseErrors,
    steps: run.steps.map((s) => ({
      index: s.index,
      waitMs: s.waitMs,
      method: s.method,
      params: s.params,
      state: s.state,
      result: s.result,
      waitStartMs: s.waitStartMs ?? null,
      callStartMs: s.callStartMs ?? null,
      finishMs: s.finishMs ?? null
    }))
  }
}

// ==================== 辅助 ====================

function isConnectivityError(res) {
  const e = String(res && res.error || '').toLowerCase()
  return e.includes('not connected') || e.includes('connection lost') || e.includes('send failed')
}

// ==================== 路由 ====================

/**
 * 注册 /api/mod/batch* 三条路由。必须在 registerModRoutes 内、POST /api/mod/*splat
 * 通配注册**之前**调用（Express 按注册序匹配，否则 batch 会被当地图方法名透传）。
 */
function registerBatchRoutes(app, manager) {
  // 启动一段序列
  app.post('/api/mod/batch', (req, res) => {
    const result = startBatch(manager, req.body)
    if (result.ok) {
      return res.json({ ok: true, batchId: result.batchId })
    }
    const status = result.http === 409 ? 409 : result.http === 502 ? 502 : 400
    const payload = { ok: false, error: result.error }
    if (result.runningId) payload.runningId = result.runningId
    res.status(status).json(payload)
  })

  // 查询进度
  app.get('/api/mod/batch/:batchId', (req, res) => {
    const run = runs.get(req.params.batchId)
    if (!run) return res.status(404).json({ ok: false, error: 'batch not found' })
    res.json({ ok: true, batch: serializeRun(run) })
  })

  // 中止（幂等：已终态再 stop 返回 200 原状态）
  app.post('/api/mod/batch/:batchId/stop', (req, res) => {
    const run = runs.get(req.params.batchId)
    if (!run) return res.status(404).json({ ok: false, error: 'batch not found' })
    const result = stopBatch(req.params.batchId)
    if (!result.ok) return res.status(404).json(result)
    res.json({ ok: true, batch: serializeRun(run) })
  })
}

module.exports = { registerBatchRoutes, startBatch, getBatch, stopBatch, serializeRun }
