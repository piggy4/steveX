// ── steveX HTTP API client ──

import { setState } from './state.js'

export async function fetchStatus() {
  const res = await fetch('/api/status')
  const data = await res.json()
  setState({ agents: data.agents, uptimeSec: data.uptimeSec })
  return data
}

export async function reloadConfig() {
  await fetch('/api/reload', { method: 'POST' })
  // Wait a moment for agents to establish connections before checking status
  await new Promise(resolve => setTimeout(resolve, 1500))
  await fetchStatus()
}

export async function connectAgent(name) {
  const res = await fetch(`/api/agents/${encodeURIComponent(name)}/connect`, {
    method: 'POST'
  })
  return res.ok
}

export async function disconnectAgent(name) {
  const res = await fetch(`/api/agents/${encodeURIComponent(name)}/disconnect`, {
    method: 'POST'
  })
  return res.ok
}

// 透传调用采集端 mod 方法（POST /api/mod/:method，body 即 params）
export async function callModMethod(method, params = {}, options = {}) {
  try {
    const res = await fetch(`/api/mod/${encodeURIComponent(method)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
      signal: options.signal
    })

    const data = await res.json().catch(() => null)
    if (!data || typeof data !== 'object') {
      return { ok: false, error: `Invalid server response (HTTP ${res.status})` }
    }
    return data
  } catch (err) {
    const cancelled = err && err.name === 'AbortError'
    return {
      ok: false,
      cancelled,
      error: cancelled ? 'Request cancelled' : `Request failed: ${err.message || String(err)}`
    }
  }
}

// 获取 mod 48 方法清单（含参数签名与说明）
export async function fetchModMethods() {
  const res = await fetch('/api/mod/methods')
  const data = await res.json()
  return (data && data.methods) || []
}

// ── 批量时序 API（设计 docs/批量时序API设计方案.md v1）──
// 启动一段有序步骤序列：{steps:[{method,params?,waitMs?}|{waitMs}], stopOnError?}，
// 8090 服务端后台串行执行（步前 waitMs 延时），立即返回 batchId。
export async function startBatch(steps, options = {}) {
  try {
    const res = await fetch('/api/mod/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ steps }),
      signal: options.signal
    })
    const data = await res.json().catch(() => null)
    if (!data || typeof data !== 'object') {
      return { ok: false, error: `Invalid server response (HTTP ${res.status})` }
    }
    if (!data.ok) {
      return { ok: false, error: data.error || `HTTP ${res.status}`, runningId: data.runningId }
    }
    return { ok: true, batchId: data.batchId }
  } catch (err) {
    return {
      ok: false,
      cancelled: err && err.name === 'AbortError',
      error: err && err.name === 'AbortError' ? 'Request cancelled' : `Request failed: ${err.message || String(err)}`
    }
  }
}

// 查询 batch 进度。未知/已过期 id → 返回 null（404）。
export async function getBatch(batchId) {
  try {
    const res = await fetch(`/api/mod/batch/${encodeURIComponent(batchId)}`)
    if (!res.ok) return null
    const data = await res.json()
    return (data && data.ok && data.batch) || null
  } catch (_) {
    return null
  }
}

// 中止某 batch（等待中的步立即取消；在途 mod 调用返回后停）。
export async function stopBatch(batchId) {
  try {
    const res = await fetch(`/api/mod/batch/${encodeURIComponent(batchId)}/stop`, {
      method: 'POST'
    })
    const data = await res.json().catch(() => null)
    return (data && data.ok) ? { ok: true } : { ok: false, error: (data && data.error) || `HTTP ${res.status}` }
  } catch (err) {
    return { ok: false, error: `Request failed: ${err.message || String(err)}` }
  }
}

// 获取 mod 连接状态（GET /api/mod/status）
export async function fetchModStatus() {
  const res = await fetch('/api/mod/status')
  return res.json()
}
export async function fetchEnvConfig() {
  const res = await fetch('/api/config/environment')

  if (!res.ok) {
    const error = await res.json().catch(() => ({}))
    throw new Error(error.error || 'Failed to load config')
  }

  return res.json()
}

export async function saveEnvConfig(config) {
  const res = await fetch('/api/config/environment', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ config })
  })

  if (!res.ok) {
    const error = await res.json().catch(() => ({}))
    throw new Error(error.error || 'Failed to save config')
  }

  return res.json()
}
