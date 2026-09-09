// ── steveX Agents page ──
// Rendering + event handlers for the agent management view.
//
// 底层已改为透传采集端 mod 的 48 方法（steveX_改进方案.md）：原"Send Command"
// 自由文本框 → "Call Mod Method" → 现改为 **Mod API 查询器**（只读参考/助写，
// 不执行）：搜索下拉选中方法后，下方显示该方法的 step JSON 序列写法 + 可编辑填参模板，
// 复制或「追加到 Mod Batch」后由 Mod Batch 面板统一执行（POST /api/mod/batch）。
// 卡片同时展示该 agent 的 mod 连接状态（host:port / connected / busy）。

import { getState, subscribe } from '../lib/state.js'
import { connectAgent, disconnectAgent, fetchModMethods, startBatch, getBatch, stopBatch } from '../lib/api.js'
import { escapeHtml } from '../lib/utils.js'
import { hydrateIcons } from '../lib/icons.js'

const PLACEHOLDER = {
  health: 20,
  maxHealth: 20,
  mode: 'Survival',
  model: 'deepseek-v4-flash',
  position: { x: '~', y: '~', z: '~' },
  action: 'Idle',
  mod: { host: null, port: null, connected: false, busy: false, error: '' }
}

// Backend getStatus() already returns canonical field names.
// Fall back to PLACEHOLDER only when a field is truly missing.
function getAgentView(agent) {
  return {
    health: agent.health ?? PLACEHOLDER.health,
    maxHealth: agent.maxHealth ?? PLACEHOLDER.maxHealth,
    mode: agent.gameMode ?? PLACEHOLDER.mode,
    model: agent.model ?? PLACEHOLDER.model,
    position: agent.position ?? PLACEHOLDER.position,
    action: agent.currentAction ?? PLACEHOLDER.action,
    mod: agent.mod ?? PLACEHOLDER.mod
  }
}

function formatPosition(position) {
  const pos = position || PLACEHOLDER.position

  const x = pos.x ?? '~'
  const y = pos.y ?? '~'
  const z = pos.z ?? '~'

  return `x: ${x}, y: ${y}, z: ${z}`
}

function getHealthPercent(health, maxHealth) {
  if (!maxHealth || maxHealth <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((health / maxHealth) * 100)))
}

// ── Filter / sort ──

function filteredAgents() {
  const { agents, filters } = getState()
  const query = filters.query.toLowerCase()

  let list = agents.filter(agent => {
    const matchText = agent.name.toLowerCase().includes(query)
    const matchStatus =
      filters.status === 'all' ||
      (filters.status === 'online' ? agent.online : !agent.online)
    return matchText && matchStatus
  })

  list.sort((a, b) => {
    if (filters.sortBy === 'status') {
      return Number(b.online) - Number(a.online) || a.name.localeCompare(b.name)
    }

    if (filters.sortBy === 'health') {
      const ah = getAgentView(a).health
      const bh = getAgentView(b).health
      return bh - ah || a.name.localeCompare(b.name)
    }

    return a.name.localeCompare(b.name, undefined, { numeric: true })
  })

  return list
}

// ── Mod API 查询器：方法清单（GET /api/mod/methods，含 method/category/description/zh/paramDefs）──

let METHOD_LIST = []

/** category 键 → 中文标签（下拉分组 + tag 显示用）。 */
const CATEGORY_ZH = {
  continuous: '持续按键',
  'one-shot': '一次性按键 / 杂项',
  camera: '视角',
  query: '感知 / 查询',
  system: '系统',
  container: '容器',
  chat: '聊天',
  sign: '告示牌',
  book: '书与笔',
  vision: '视觉'
}

function categoryZh(cat) {
  return CATEGORY_ZH[cat] || cat
}

/** 搜索词匹配：方法名 / 中文说明 / 英文描述 / 分类中文名。q 已 lower 化。 */
function methodMatches(m, q) {
  if (!q) return true
  return (
    m.method.toLowerCase().includes(q) ||
    (m.zh || '').toLowerCase().includes(q) ||
    (m.description || '').toLowerCase().includes(q) ||
    categoryZh(m.category).toLowerCase().includes(q)
  )
}

/**
 * 重建一个方法下拉：按本面板搜索词过滤 + 按 category 分组。
 * 保留当前选中（若仍被过滤保留）；当前方法被过滤掉则收起 detail。
 */
function renderMethodOptions(sel) {
  if (!sel) return
  const panel = sel.closest('.command-panel[data-action="modapi"]')
  const q = (panel && panel.querySelector('.modapi-search-input').value.trim() || '').toLowerCase()
  const current = sel.value

  const groups = new Map() // 保持 METHODS 原有顺序
  for (const m of METHOD_LIST) {
    if (!methodMatches(m, q)) continue
    if (!groups.has(m.category)) groups.set(m.category, [])
    groups.get(m.category).push(m)
  }

  let html = '<option value="">— select method —</option>'
  for (const [cat, list] of groups) {
    html += `<optgroup label="${escapeHtml(categoryZh(cat))}">`
    for (const m of list) html += `<option value="${escapeHtml(m.method)}">${escapeHtml(m.method)}</option>`
    html += '</optgroup>'
  }
  sel.innerHTML = html

  const keep = current && [...sel.options].some(o => o.value === current)
  if (keep) {
    sel.value = current
  } else if (current) {
    // 当前选中被搜索词过滤掉了 → 收起 detail，避免显示与下拉不一致的方法
    const detail = panel && panel.querySelector('.modapi-detail')
    if (detail) detail.hidden = true
  }
}

function syncMethodSelects() {
  document.querySelectorAll('.method-select').forEach(renderMethodOptions)
}

async function loadMethodList() {
  try {
    METHOD_LIST = (await fetchModMethods()) || []
  } catch (err) {
    console.error('[agents] Failed to load mod methods:', err)
    METHOD_LIST = []
  }
  syncMethodSelects()
}

/** 根据 paramDefs 的 sample（缺省回落 def）生成预填 params 对象。 */
function sampleParams(m) {
  const params = {}
  for (const p of m.paramDefs || []) {
    params[p.name] = p.sample !== undefined ? p.sample : p.def
  }
  return params
}

/** 该方法的完整 step JSON（单行，供 Mod Batch）：{"method":"…","params":{…}} */
function stepJsonLine(m) {
  return JSON.stringify({ method: m.method, params: sampleParams(m) })
}

function fmtDef(def) {
  if (typeof def === 'string' && def === '') return '""'
  return JSON.stringify(def)
}

/** 选中某方法 → 展示下方 detail（语法框含 zh 说明 + step 写法，下接 step 模板 + 参数提示）。 */
function renderModApiDetail(panel, entry) {
  const detail = panel.querySelector('.modapi-detail')
  if (!entry) {
    detail.hidden = true
    return
  }
  detail.hidden = false

  const meta = detail.querySelector('.modapi-meta')
  meta.innerHTML =
    `<span class="pill">${escapeHtml(categoryZh(entry.category))}</span>` +
    `<code class="modapi-method">${escapeHtml(entry.method)}</code>` +
    `<span class="modapi-zh">${escapeHtml(entry.zh || '')}</span>`

  const ta = detail.querySelector('textarea[name="stepjson"]')
  ta.value = stepJsonLine(entry)

  const wrap = detail.querySelector('.modapi-params')
  const defs = entry.paramDefs || []
  if (!defs.length) {
    wrap.innerHTML = '<div class="modapi-noparam">无参数 — 上面的 step JSON 可直接复制 / 追加。</div>'
    return
  }
  const rows = defs.map(p => {
    const req = p.required
      ? '<span class="pmeta req">必填</span>'
      : (p.def !== undefined ? `<span class="pmeta">默认 ${escapeHtml(fmtDef(p.def))}</span>` : '')
    return (
      `<div class="modapi-prow">` +
      `<span class="pname">${escapeHtml(p.name)}</span>` +
      `<span class="ptype">${escapeHtml(p.type)}</span>` +
      req +
      `<span class="phint">${escapeHtml(p.hint || '')}</span>` +
      `</div>`
    )
  })
  wrap.innerHTML = `<div class="modapi-params-title">参数（模板已按示例值预填，按需修改）：</div>${rows.join('')}`
}

/** detail 里的瞬时反馈（追加成功 / 校验失败）。 */
function flashFeedback(panel, msg, isErr) {
  const el = panel.querySelector('.modapi-feedback')
  if (!el) return
  el.textContent = msg
  el.className = 'mod-result modapi-feedback ' + (isErr ? 'err' : 'ok')
  el.hidden = false
  if (panel._fbTimer) clearTimeout(panel._fbTimer)
  panel._fbTimer = setTimeout(() => { el.hidden = true }, 2600)
}

// ── Agent card template ──

function agentCardHtml(agent) {
  const statusClass = agent.online ? 'online' : 'offline'
  const statusText = agent.online ? 'online' : 'offline'
  const username = agent.username || '—'

  const view = getAgentView(agent)
  const percent = getHealthPercent(view.health, view.maxHealth)
  const position = formatPosition(view.position)

  const mod = view.mod
  const modDot = mod.connected ? (mod.busy ? 'busy' : 'online') : 'offline'
  const modEndpoint = mod.host ? `${escapeHtml(mod.host)}:${escapeHtml(mod.port)}` : '—'
  const modBusyTag = mod.connected && mod.busy ? '<span class="pill busy-pill">busy</span>' : ''

  return `
    <article class="agent-card" data-agent-name="${escapeHtml(agent.name)}">
      <div class="agent-header">
        <div class="agent-id">
          <h2>${escapeHtml(agent.name)}</h2>
          <span class="dot ${statusClass}" aria-hidden="true"></span>
          <span class="state-label" data-status="${agent.online ? 'online' : 'offline'}">${statusText}</span>
          <span class="pill username-pill" style="margin-left:8px;color:var(--muted);font-size:14px">${escapeHtml(username)}</span>
        </div>

        <div class="agent-actions">
          <button class="btn soft-success" type="button" data-action="connect" data-agent="${escapeHtml(agent.name)}">
            <span data-icon="link"></span>
            Connect
          </button>
          <button class="btn soft-danger" type="button" data-action="disconnect" data-agent="${escapeHtml(agent.name)}">
            <span data-icon="unlink"></span>
            Disconnect
          </button>
          <button class="btn ghost" type="button" data-action="log" data-agent="${escapeHtml(agent.name)}">
            <span data-icon="logs"></span>
            Agent Log
          </button>
        </div>
      </div>

      <div class="agent-body">
        <div class="stats-panel">
          <div class="stats-column">
            <div class="stat-row">
              <div class="stat-label">Health</div>
              <div class="stat-value">
                <span data-icon="heart"></span>
                <span class="health-wrap">
                  <span class="health-number">${view.health} / ${view.maxHealth}</span>
                  <span class="health-bar" aria-label="Health ${percent}%">
                    <span class="health-fill" style="--health:${percent}%"></span>
                  </span>
                </span>
              </div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Game Mode</div>
              <div class="stat-value"><span data-icon="gamepad"></span>${escapeHtml(view.mode)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Model</div>
              <div class="stat-value"><span data-icon="brain"></span>${escapeHtml(view.model)}</div>
            </div>
          </div>

          <div class="stats-column">
            <div class="stat-row">
              <div class="stat-label">Position</div>
              <div class="stat-value"><span data-icon="pin"></span>${escapeHtml(position)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Current Action</div>
              <div class="stat-value"><span data-icon="walk"></span>${escapeHtml(view.action)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Mod</div>
              <div class="stat-value">
                <span class="dot ${modDot}" aria-hidden="true"></span>
                <span class="mod-endpoint">${modEndpoint}</span>
                ${modBusyTag}
              </div>
            </div>
          </div>
        </div>

        <div class="command-stack">
          <div class="command-panel" data-action="modapi">
            <div class="command-title">Mod API 查询器 <span>(参考/助写：选方法看 step JSON 写法，执行走下方 Mod Batch)</span></div>
            <div class="command-row">
              <label class="modapi-search" title="按方法名 / 功能过滤">
                <span data-icon="search"></span>
                <input type="search" class="modapi-search-input" placeholder="搜索方法 / 功能，如 key/、camera、chat、容器…" autocomplete="off" spellcheck="false" />
              </label>
            </div>
            <div class="command-row">
              <select name="method" class="method-select" aria-label="mod method">
                <option value="">— select method —</option>
              </select>
            </div>
            <div class="modapi-detail" hidden>
              <div class="modapi-grammar">
                <div class="modapi-meta"></div>
                <pre class="modapi-grammar-code">每行一个 step JSON，粘贴到下方 Mod Batch 后 Run：
  调用步   {"waitMs":毫秒(可选), "method":"…", "params":{…}}   ← waitMs = 执行本步前先等
  纯等待步 {"waitMs":3000}</pre>
              </div>
              <div class="command-row">
                <textarea name="stepjson" class="method-params modapi-step" rows="2" spellcheck="false"></textarea>
              </div>
              <div class="modapi-params"></div>
              <div class="command-row">
                <button class="btn send" type="button" data-action="append-step">追加到 Mod Batch</button>
                <button class="btn ghost" type="button" data-action="copy-step">复制</button>
              </div>
              <pre class="mod-result modapi-feedback" hidden></pre>
            </div>
          </div>

          <form class="command-panel" data-action="modbatch">
            <div class="command-title">Mod Batch <span>(step JSON 序列 → 服务端 /api/mod/batch 按序执行)</span></div>
            <div class="command-row">
              <textarea name="batch" class="method-params batch-input" rows="5" spellcheck="false"
                placeholder='每行一个 step JSON：调用步 {method, params?, waitMs?}（waitMs=执行前先等）；纯等待步 {waitMs}
{"waitMs":3000}
{"method":"key/up","params":{"pressed":true}}
{"waitMs":2000}
{"method":"key/up","params":{"pressed":false}}'></textarea>
            </div>
            <div class="command-row">
              <button class="btn send" type="submit">Run Batch</button>
              <button class="btn ghost" type="button" data-action="stop-batch" hidden>Stop</button>
            </div>
            <pre class="mod-result batch-result" hidden></pre>
          </form>
        </div>
      </div>
    </article>
  `
}

// ── Log modal ──

function showLogModal(name) {
  // Remove any existing modal
  const existing = document.getElementById('log-modal')
  if (existing) existing.remove()

  const entries = getState().logs[name] || []

  const modal = document.createElement('div')
  modal.id = 'log-modal'
  modal.className = 'log-modal-overlay'
  modal.innerHTML = `
    <div class="log-modal">
      <div class="log-modal-header">
        <h2>${escapeHtml(name)} <span>Agent Log</span></h2>
        <button class="log-modal-close" type="button">&times;</button>
      </div>
      <div class="log-modal-body">
        ${entries.length === 0 ? '<div class="log-empty">No log entries yet.</div>' : ''}
      </div>
    </div>
  `

  document.body.appendChild(modal)

  // Populate existing entries
  const body = modal.querySelector('.log-modal-body')
  for (const entry of entries) {
    body.appendChild(buildLogEntryEl(entry))
  }
  body.scrollTop = body.scrollHeight

  // Close handlers
  modal.addEventListener('click', (e) => {
    if (e.target === modal || e.target.closest('.log-modal-close')) {
      modal.remove()
    }
  })

  // Subscribe for live updates while modal is open
  const unsub = subscribe(() => {
    if (!document.getElementById('log-modal')) {
      unsub()
      return
    }

    const currentEntries = getState().logs[name] || []
    const currentCount = body.children.length - (body.querySelector('.log-empty') ? 1 : 0)

    const empty = body.querySelector('.log-empty')
    if (empty && currentEntries.length > 0) empty.remove()

    for (let i = currentCount; i < currentEntries.length; i++) {
      body.appendChild(buildLogEntryEl(currentEntries[i]))
    }

    body.scrollTop = body.scrollHeight
  })
}

function buildLogEntryEl(entry) {
  const el = document.createElement('div')
  el.className = `log-entry ${entry.type}`

  const time = entry.timestamp
    ? new Date(entry.timestamp).toLocaleTimeString()
    : new Date().toLocaleTimeString()

  let html = ''
  switch (entry.type) {
    case 'cmd-start':
      html = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> → ${escapeHtml(entry.command)}`
      break
    case 'cmd-done':
      html = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> ← ${escapeHtml(entry.command)}<br>${escapeHtml(entry.output)}`
      break
    case 'cmd-error':
      html = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> ✕ ${escapeHtml(entry.command)}<br>${escapeHtml(entry.output)}`
      break
    case 'llm-input':
      html = `<span class="log-time">${time}</span> <span class="log-tag">[LLM]</span> → <em>${escapeHtml(entry.model)}</em><br>${escapeHtml(entry.prompt ? entry.prompt.slice(0, 2000) + (entry.prompt.length > 2000 ? '…' : '') : '')}`
      break
    case 'llm-output':
      html = `<span class="log-time">${time}</span> <span class="log-tag">[LLM]</span> ← <em>${escapeHtml(entry.model)}</em><br>${escapeHtml(entry.response ? entry.response.slice(0, 2000) + (entry.response.length > 2000 ? '…' : '') : '')}`
      break
  }

  el.innerHTML = html
  return el
}

// ── Render the full agents list ──

export function renderAgents(container) {
  const list = filteredAgents()

  if (!list.length) {
    container.innerHTML = '<div class="empty-state">No agents match the current filters.</div>'
    return
  }

  // Smart diff: update existing cards, add new ones, remove gone ones
  const existingNames = new Set()

  list.forEach(agent => {
    existingNames.add(agent.name)

    let card = container.querySelector(`.agent-card[data-agent-name="${escapeHtml(agent.name)}"]`)

    if (!card) {
      const temp = document.createElement('div')
      temp.innerHTML = agentCardHtml(agent)
      card = temp.firstElementChild
      container.appendChild(card)
      hydrateIcons(card)
      // 填充新卡的方法下拉（METHOD_LIST 可能已异步就绪）
      const sel = card.querySelector('select.method-select')
      if (sel) renderMethodOptions(sel)
    }

    // Update dynamic parts without full re-render
    updateDynamicFields(card, agent)
  })

  // Remove cards for agents that no longer exist
  container.querySelectorAll('.agent-card').forEach(card => {
    if (!existingNames.has(card.dataset.agentName)) {
      card.remove()
    }
  })
}

function updateDynamicFields(card, agent) {
  const statusClass = agent.online ? 'online' : 'offline'
  const statusText = agent.online ? 'online' : 'offline'

  const view = getAgentView(agent)
  const percent = getHealthPercent(view.health, view.maxHealth)
  const position = formatPosition(view.position)

  const mod = view.mod
  const modDot = mod.connected ? (mod.busy ? 'busy' : 'online') : 'offline'
  const modEndpoint = mod.host ? `${mod.host}:${mod.port}` : '—'

  // Header dot + state label
  const dot = card.querySelector('.agent-id .dot')
  if (dot) dot.className = `dot ${statusClass}`

  const label = card.querySelector('.agent-id .state-label')
  if (label) {
    label.textContent = statusText
    label.dataset.status = statusClass
  }

  // Username pill
  const usernamePill = card.querySelector('.username-pill')
  if (usernamePill) {
    usernamePill.textContent = agent.username || '—'
  }

  // Update each stat row by label
  const statRows = card.querySelectorAll('.stat-row')

  statRows.forEach(row => {
    const lbl = row.querySelector('.stat-label')
    const val = row.querySelector('.stat-value')
    if (!lbl || !val) return

    const name = lbl.textContent.trim()

    if (name === 'Health') {
      const healthNumber = row.querySelector('.health-number')
      if (healthNumber) {
        healthNumber.textContent = `${view.health} / ${view.maxHealth}`
      }

      const healthBar = row.querySelector('.health-bar')
      if (healthBar) {
        healthBar.setAttribute('aria-label', `Health ${percent}%`)
      }

      const healthFill = row.querySelector('.health-fill')
      if (healthFill) {
        healthFill.style.setProperty('--health', `${percent}%`)
      }
    }

    if (name === 'Game Mode') {
      val.innerHTML = `<span data-icon="gamepad"></span>${escapeHtml(view.mode)}`
      hydrateIcons(val)
    }

    if (name === 'Model') {
      val.innerHTML = `<span data-icon="brain"></span>${escapeHtml(view.model)}`
      hydrateIcons(val)
    }

    if (name === 'Position') {
      val.innerHTML = `<span data-icon="pin"></span>${escapeHtml(position)}`
      hydrateIcons(val)
    }

    if (name === 'Current Action') {
      val.innerHTML = `<span data-icon="walk"></span>${escapeHtml(view.action)}`
      hydrateIcons(val)
    }

    if (name === 'Mod') {
      const busyTag = mod.connected && mod.busy ? '<span class="pill busy-pill">busy</span>' : ''
      val.innerHTML = `
        <span class="dot ${modDot}" aria-hidden="true"></span>
        <span class="mod-endpoint">${escapeHtml(modEndpoint)}</span>
        ${busyTag}
      `
    }
  })
}

// ── Event delegation ──

/** 追加按钮：把 detail 里当前 step JSON 校验后追加到同卡 Mod Batch 文本框。 */
function appendStepToBatch(btn) {
  const panel = btn.closest('.command-panel[data-action="modapi"]')
  const card = btn.closest('.agent-card')
  if (!panel || !card) return

  const ta = panel.querySelector('textarea[name="stepjson"]')
  const batchTa = card.querySelector('textarea[name="batch"]')
  if (!ta || !batchTa) return

  const line = ta.value.trim()
  if (!line) return

  let obj
  try {
    obj = JSON.parse(line)
  } catch (err) {
    flashFeedback(panel, `step JSON 无效：${err.message}`, true)
    return
  }
  if (!obj || typeof obj !== 'object' || Array.isArray(obj) || typeof obj.method !== 'string' || !obj.method) {
    flashFeedback(panel, 'step JSON 需是含字符串 method 字段的对象', true)
    return
  }

  batchTa.value = batchTa.value.trim() === ''
    ? line
    : (batchTa.value.endsWith('\n') ? batchTa.value : batchTa.value + '\n') + line
  flashFeedback(panel, `已追加到 Mod Batch：${obj.method}`, false)
  batchTa.focus()
}

/** 复制按钮：把当前 step JSON 写剪贴板（不可用则降级选中文本）。 */
async function copyStep(btn) {
  const panel = btn.closest('.command-panel[data-action="modapi"]')
  const ta = panel && panel.querySelector('textarea[name="stepjson"]')
  const line = ta && ta.value.trim()
  if (!line) return

  try {
    await navigator.clipboard.writeText(line)
  } catch (_) {
    ta.focus()
    ta.select()
    try { document.execCommand('copy') } catch (__) { /* 忽略 */ }
  }
  const orig = btn.textContent
  btn.textContent = '已复制!'
  setTimeout(() => { btn.textContent = orig }, 1000)
}

function handleClick(e) {
  const btn = e.target.closest('button[data-action]')
  if (!btn) return

  const name = btn.dataset.agent
  const action = btn.dataset.action

  if (action === 'connect') {
    connectAgent(name)
  } else if (action === 'disconnect') {
    disconnectAgent(name)
  } else if (action === 'log') {
    showLogModal(name)
  } else if (action === 'append-step') {
    appendStepToBatch(btn)
  } else if (action === 'copy-step') {
    copyStep(btn)
  } else if (action === 'stop-batch') {
    const form = btn.closest('form.command-panel')
    if (!form) return
    const batchId = form.dataset.batchId
    if (batchId) {
      // 服务端 stop：等待中的步立即取消，轮询会读到 stopped 终态并自动收尾。
      stopBatch(batchId)
    }
  }
}

/** 搜索输入：即时按词过滤本面板方法下拉。 */
function handleModApiInput(e) {
  const input = e.target.closest('.modapi-search-input')
  if (!input) return
  const panel = input.closest('.command-panel[data-action="modapi"]')
  const sel = panel && panel.querySelector('select.method-select')
  if (sel) renderMethodOptions(sel)
}

/** 选中方法：渲染下方 detail（meta + step 模板 + 参数提示）。 */
function handleModApiChange(e) {
  const sel = e.target.closest('select.method-select')
  if (!sel) return
  const panel = sel.closest('.command-panel[data-action="modapi"]')
  const entry = METHOD_LIST.find(m => m.method === sel.value) || null
  renderModApiDetail(panel, entry)
}

async function handleSubmit(e) {
  const form = e.target.closest('form.command-panel')
  if (!form) return

  e.preventDefault()

  if (form.dataset.action === 'modbatch') {
    await runModBatch(form)
  }
}

// ── Mod Batch 批量时序执行（服务端 /api/mod/batch）──
// 语法（设计 docs/批量时序API设计方案.md v1）：每行一个 step JSON，无特殊前缀。
//   调用步    { method, params?, waitMs? }   waitMs = 执行本步前先等（毫秒）
//   纯等待步  { waitMs }                     无 method，仅暂停
// 执行权在 8090 服务端：提交后后台串行跑，本面板轮询 GET /api/mod/batch/:id 看进度，
// Stop 调 stop 接口。"按住键结束后兜底松开"由服务端统一负责，浏览器不再维护按键状态。

function parseBatchStep(line) {
  const rest = line.trim()
  if (!rest || rest.startsWith('#') || rest.startsWith('//')) return null

  // delay: 前缀已移除——给可读迁移提示，而不是泛泛的 "invalid JSON"。
  if (/^delay:\d+\s+/i.test(rest)) {
    return { error: `'delay:' 前缀已移除，请在 step JSON 内用 "waitMs":…（示例行）` }
  }

  let obj
  try {
    obj = JSON.parse(rest)
  } catch {
    return { error: `invalid JSON: ${rest}` }
  }

  if (!obj || typeof obj !== 'object' || Array.isArray(obj)) {
    return { error: `step must be an object: ${rest}` }
  }

  if (typeof obj.method === 'string' && obj.method) {
    const params = obj.params && typeof obj.params === 'object' && !Array.isArray(obj.params) ? obj.params : {}
    const waitMs = Number.isInteger(obj.waitMs) && obj.waitMs >= 0 ? obj.waitMs : 0
    return { waitMs, method: obj.method, params }
  }
  if (obj.method == null) {
    if (Number.isInteger(obj.waitMs) && obj.waitMs > 0) {
      return { waitMs: obj.waitMs, method: null, params: null }
    }
    return { error: `pure-wait step needs a positive integer waitMs: ${rest}` }
  }
  return { error: `missing method: ${rest}` }
}

const BATCH_TERMINAL = new Set(['done', 'stopped', 'failed'])

async function runModBatch(form) {
  if (form.dataset.batchRunning === '1') return

  const textarea = form.querySelector('textarea[name="batch"]')
  const runBtn = form.querySelector('button[type="submit"]')
  const stopBtn = form.querySelector('[data-action="stop-batch"]')
  const resultEl = form.querySelector('.batch-result')

  const steps = []
  const errors = []
  textarea.value.split('\n').forEach((line, i) => {
    const s = parseBatchStep(line)
    if (!s) return
    if (s.error) errors.push(`line ${i + 1}: ${s.error}`)
    else steps.push(s)
  })

  if (!steps.length) {
    alert(errors.length ? `No valid steps:\n${errors.join('\n')}` : 'No steps to run')
    return
  }

  form.dataset.batchRunning = '1'
  runBtn.disabled = true
  stopBtn.hidden = false
  resultEl.hidden = false
  resultEl.className = 'mod-result batch-result'
  resultEl.textContent = errors.length ? errors.join('\n') + '\n\n' : ''

  try {
    resultEl.textContent += `Submitting ${steps.length} steps …\n`
    const start = await startBatch(steps)
    if (!start.ok) {
      resultEl.className = 'mod-result batch-result err'
      resultEl.textContent += `batch failed to start: ${start.error || 'unknown error'}\n`
      return
    }
    form.dataset.batchId = start.batchId
    await pollBatch(form, start.batchId)
  } catch (err) {
    resultEl.className = 'mod-result batch-result err'
    resultEl.textContent += `batch failed: ${err.message || String(err)}\n`
  } finally {
    form.dataset.batchId = ''
    form.dataset.batchRunning = '0'
    runBtn.disabled = false
    stopBtn.hidden = true
    resultEl.scrollTop = resultEl.scrollHeight
  }
}

/** 提交后轮询进度，每 ~350ms 全量重绘一次，读到终态退出。 */
async function pollBatch(form, batchId) {
  const resultEl = form.querySelector('.batch-result')
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

  for (;;) {
    if (!form.isConnected) return   // 卡片已卸载/重渲染 → 收手（服务端 batch 继续跑）

    const batch = await getBatch(batchId)
    if (!batch) {
      resultEl.className = 'mod-result batch-result err'
      resultEl.textContent += '— batch state no longer available —\n'
      return
    }

    renderBatchProgress(resultEl, batch)
    if (BATCH_TERMINAL.has(batch.status)) return
    await sleep(350)
  }
}

/** 全量重绘进度（步骤数有限，整段重建最简单可靠）。 */
function renderBatchProgress(resultEl, batch) {
  const lines = batch.steps.map((st, i) => {
    const tag = `[${i + 1}/${batch.steps.length}]`
    const callLabel = st.method || (st.waitMs > 0 ? `wait ${st.waitMs}ms` : '')
    const prefix = st.waitMs > 0 && st.method ? `(wait ${st.waitMs}ms) ` : ''

    switch (st.state) {
      case 'done': {
        if (!st.method) return `${tag} wait ${st.waitMs}ms`
        const r = st.result || {}
        const body = r.ok
          ? JSON.stringify(r.data === undefined ? null : r.data)
          : `✕ ${r.error || 'unknown error'}`
        return `${tag} ${prefix}${st.method} → ${body}`
      }
      case 'running':
        return `${tag} ${st.method} … (calling)`
      case 'waiting':
        return `${tag} ${prefix}${callLabel} … (waiting)`
      case 'skipped':
        return `${tag} skipped`
      default:
        return `${tag} ${callLabel} pending`
    }
  })

  if (batch.status !== 'running') {
    lines.push(`— batch ${batch.status} —`)
    if (Array.isArray(batch.releasedKeys) && batch.releasedKeys.length) {
      lines.push(`released ${batch.releasedKeys.join(', ')}`)
    }
    for (const failure of batch.releaseErrors || []) {
      lines.push(`failed to release ${failure.method}: ${failure.error}`)
    }
  }

  resultEl.className = batch.status === 'done'
    ? 'mod-result batch-result ok'
    : batch.status === 'running'
      ? 'mod-result batch-result'
      : 'mod-result batch-result err'

  resultEl.textContent = lines.join('\n')
  resultEl.scrollTop = resultEl.scrollHeight
}

// ── Init ──

export function initAgents(container) {
  container.addEventListener('click', handleClick)
  container.addEventListener('submit', handleSubmit)
  container.addEventListener('input', handleModApiInput)
  container.addEventListener('change', handleModApiChange)
  loadMethodList()
}
