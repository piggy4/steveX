#!/usr/bin/env node
/**
 * steveX 验证脚本 —— 采集端 mod WebSocket JSON-RPC API（50 方法）。
 * 替代旧 test_api.py（HTTP 版，mod 的 HTTP API 已不存在）。
 *
 * 用法：
 *   node test_mod_api.js             直连 mod WS(25550) 做 JSON-RPC 冒烟测试
 *   node test_mod_api.js --stevex    另测 steveX 透传层（POST /api/mod/:method，
 *                                    需 steveX 已在 8090 运行）
 *   node test_mod_api.js --all       额外执行有副作用/较慢的方法
 *                                    （vision/snapshot、inventory、chat 等）
 *
 * 前置：采集端 mod 已启动并进入世界。主菜单已有 25550 监听，但玩家方法不可用。
 */

const WebSocket = require('ws')

const MOD_WS = 'ws://127.0.0.1:25550'
const STEVE_X_BASE = process.env.STEVE_X_BASE || 'http://localhost:8090'
const REQUEST_TIMEOUT_MS = 20000

const args = process.argv.slice(2)
const viaStevex = args.includes('--stevex')
const all = args.includes('--all')

let failures = 0

function check(label, ok, detail = '') {
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}${detail ? '  ·  ' + detail : ''}`)
  if (!ok) failures++
}

// ── JSON-RPC over WebSocket（自实现，避免依赖 steveX 模块）──
class JsonRpcClient {
  constructor(url) {
    this.url = url
    this.ws = new WebSocket(url)
    this.nextId = 1
    this.pending = new Map()
  }

  open() {
    return new Promise((resolve, reject) => {
      this.ws.on('open', resolve)
      this.ws.on('error', (err) => reject(err))
      this.ws.on('message', (data) => {
        let msg
        try { msg = JSON.parse(data.toString()) } catch { return }
        if (typeof msg.id !== 'number') return
        const p = this.pending.get(msg.id)
        if (!p) return
        this.pending.delete(msg.id)
        clearTimeout(p.timer)
        if (msg.ok === true) p.resolve({ ok: true, data: msg.data })
        else p.resolve({ ok: false, error: msg.error || 'unknown mod error' })
      })
    })
  }

  call(method, params = {}) {
    const id = this.nextId++
    return new Promise((resolve) => {
      const timer = setTimeout(() => {
        this.pending.delete(id)
        resolve({ ok: false, error: `timeout: ${method}` })
      }, REQUEST_TIMEOUT_MS)
      this.pending.set(id, { resolve, timer })
      try { this.ws.send(JSON.stringify({ id, method, params })) }
      catch (err) {
        clearTimeout(timer)
        this.pending.delete(id)
        resolve({ ok: false, error: `send failed: ${err.message}` })
      }
    })
  }

  close() {
    for (const [, p] of this.pending) {
      clearTimeout(p.timer)
      p.resolve({ ok: false, error: 'connection closed' })
    }
    this.pending.clear()
    try { this.ws.close() } catch (_) {}
  }
}

// ── 直连 mod WS：JSON-RPC 冒烟测试 ──
async function testDirectMod() {
  console.log('\n=== 直连 mod WS（' + MOD_WS + '）===')

  const c = new JsonRpcClient(MOD_WS)
  await c.open()
  console.log('  [connected]')

  // 系统
  const status = await c.call('status', {})
  check('status 返回 ok', status.ok === true, JSON.stringify(status.data))
  check('status 含 busy/error 字段', status.ok && typeof status.data.busy === 'boolean' && typeof status.data.error === 'string')

  // 感知/查询
  const player = await c.call('player', {})
  check('player 返回 ok', player.ok === true)
  check('player.vitals.health 可转换为有限数字',
    player.ok && Number.isFinite(Number(player.data?.vitals?.health)),
    JSON.stringify(player.data?.vitals))

  const f3 = await c.call('f3', {})
  check('f3 返回 ok', f3.ok === true)
  check('f3.position.xyz 为 3 元数组', f3.ok && Array.isArray(f3.data?.position?.xyz) && f3.data.position.xyz.length === 3,
    JSON.stringify(f3.data?.position?.xyz))

  const tablist = await c.call('tablist', {})
  check('tablist 返回 ok', tablist.ok === true)

  const sound = await c.call('sound', {})
  check('sound 返回 ok', sound.ok === true)

  const profiler = await c.call('profiler', {})
  check('profiler 返回 ok', profiler.ok === true)

  const settings = await c.call('settings/get', {})
  check('settings/get 返回 ok', settings.ok === true)

  // 视角（增量 0 旋转，无副作用）
  const turn = await c.call('camera/turn', { dx: 0, dy: 0 })
  check('camera/turn 返回 ok', turn.ok === true)

  // 一次性按键：热栏选槽（槽 1，无破坏性）
  const hotbar = await c.call('key/hotbar', { slot: 1 })
  check('key/hotbar {slot:1} 返回 ok', hotbar.ok === true)

  // 未知方法 → ok:false + error
  const unknown = await c.call('not/a/real/method', {})
  check('未知方法返回 ok:false + error', unknown.ok === false && typeof unknown.error === 'string',
    JSON.stringify(unknown.error))

  const recoveredPlayer = await c.call('player', {})
  const recoveredStatus = await c.call('status', {})
  check('成功调用后 status.error 清空',
    recoveredPlayer.ok === true && recoveredStatus.ok === true && recoveredStatus.data.error === '',
    JSON.stringify(recoveredStatus.data))

  // busy 语义：一个慢方法（vision/snapshot）执行前后 busy 翻转（lenient 判定）
  if (all) {
    const before = await c.call('status', {})
    const snap = await c.call('vision/snapshot', {})
    const after = await c.call('status', {})
    check('vision/snapshot 返回 ok', snap.ok === true,
      snap.ok ? `visibleBlocks=${snap.data?.visibleBlockCount} entities=${snap.data?.entityCount}` : snap.error)
    check('vision/snapshot 结束后 busy 复位为 false', after.ok && after.data.busy === false,
      JSON.stringify({ before: before.data, after: after.data }))

    const inventory = await c.call('inventory', {})
    check('inventory 返回 ok', inventory.ok === true)

    const chatText = await c.call('chat/text', {})
    check('chat/text 返回 ok', chatText.ok === true)
  }

  c.close()
  console.log('  [closed]')
}

// ── 经 steveX 透传层：HTTP POST /api/mod/:method ──
async function testViaSteveX() {
  console.log('\n=== steveX 透传层（' + STEVE_X_BASE + '）===')

  const post = async (path, body) => {
    const res = await fetch(STEVE_X_BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {})
    })
    return { status: res.status, json: await res.json() }
  }
  const get = async (path) => {
    const res = await fetch(STEVE_X_BASE + path)
    return { status: res.status, json: await res.json() }
  }

  // 方法清单（50）
  const methods = await get('/api/mod/methods')
  check('GET /api/mod/methods 返回 ok', methods.json.ok === true)
  check('方法清单共 50 个', Array.isArray(methods.json.methods) && methods.json.methods.length === 50,
    `count=${methods.json.methods?.length}`)

  // 状态
  const st = await get('/api/mod/status')
  check('GET /api/mod/status 返回 connected + 50 methods',
    typeof st.json.connected === 'boolean' && st.json.methodCount === 50)

  // 透传主路径
  const p = await post('/api/mod/player', {})
  check('POST /api/mod/player 返回 ok', p.json.ok === true, JSON.stringify(p.json.data?.vitals))

  const f = await post('/api/mod/f3', {})
  check('POST /api/mod/f3 返回 ok', f.json.ok === true)

  // 通用入口
  const g = await post('/api/mod', { method: 'status', params: {} })
  check('POST /api/mod {method:status} 返回 ok', g.json.ok === true)

  // 错误透传
  const bad = await post('/api/mod/not/a/real/method', {})
  check('未知方法 → 502 + ok:false', bad.status === 502 && bad.json.ok === false,
    `http=${bad.status} error=${bad.json.error}`)

  // /api/skills 语义已改为 mod 方法清单
  const skills = await get('/api/skills')
  check('GET /api/skills 返回 50 个方法', skills.json.ok === true && skills.json.skills.length === 50,
    `count=${skills.json.skills?.length}`)

  // agent 状态含 mod 连接信息
  const agents = await get('/api/status')
  const a = agents.json.agents && agents.json.agents[0]
  check('GET /api/status agent.mod 含 host/port/connected',
    !!a && a.mod && typeof a.mod.connected === 'boolean', JSON.stringify(a?.mod))
}

async function main() {
  try {
    await testDirectMod()
    if (viaStevex) await testViaSteveX()
  } catch (err) {
    console.error('\n[error]', err.message)
    failures++
  }

  console.log(failures === 0 ? '\nALL PASS' : `\n${failures} FAILURE(S)`)
  process.exit(failures === 0 ? 0 : 1)
}

main()
