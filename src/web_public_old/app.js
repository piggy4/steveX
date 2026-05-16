// ── steveX Debug Console ──
// WebSocket-driven real-time agent monitoring

const grid = document.getElementById('grid')
const reloadButton = document.getElementById('reload')
const uptimeEl = document.getElementById('uptime')
const wsIndicator = document.getElementById('ws-indicator')

// ── WebSocket ──

const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
const ws = new WebSocket(`${protocol}//${location.host}`)

ws.onopen = () => {
  wsIndicator.className = 'status ws-status online'
  wsIndicator.textContent = 'WS: online'
}

ws.onclose = () => {
  wsIndicator.className = 'status ws-status offline'
  wsIndicator.textContent = 'WS: offline'
  // Auto-reconnect after 2s
  setTimeout(() => {
    if (ws.readyState === WebSocket.CLOSED) {
      window.location.reload()
    }
  }, 2000)
}

ws.onmessage = (event) => {
  const data = JSON.parse(event.data)

  switch (data.type) {
    case 'snapshot':
      applySnapshot(data)
      break

    case 'agent:connect':
    case 'agent:disconnect':
      handleAgentStatus(data.name, data.type === 'agent:connect')
      break

    case 'agent:command:start':
      addLog(data.name, 'cmd-start', {
        command: data.command,
        timestamp: data.timestamp
      })
      break

    case 'agent:command:done':
      addLog(data.name, data.ok ? 'cmd-done' : 'cmd-error', {
        command: data.command,
        output: data.output || data.error || '',
        timestamp: data.timestamp
      })
      break

    case 'agent:llm:input':
      addLog(data.name, 'llm-input', {
        model: data.model,
        prompt: data.prompt,
        timestamp: data.timestamp
      })
      break

    case 'agent:llm:output':
      addLog(data.name, 'llm-output', {
        model: data.model,
        response: data.response,
        timestamp: data.timestamp
      })
      break
  }
}

// ── Initial load via HTTP ──

async function refresh() {
  const response = await fetch('/api/status')
  const data = await response.json()
  uptimeEl.textContent = `Uptime: ${data.uptimeSec}s`
  buildGrid(data.agents)
}

reloadButton.addEventListener('click', async () => {
  await fetch('/api/reload', { method: 'POST' })
  // Wait a moment for agents to establish connections before checking status
  await new Promise(resolve => setTimeout(resolve, 1500))
  await refresh()
})

// ── Card management ──

function buildGrid(agents) {
  const existingNames = new Set()
  agents.forEach((agent) => {
    existingNames.add(agent.name)
    ensureCard(agent)
  })
  // Remove cards for agents that no longer exist
  const cards = grid.querySelectorAll('.card')
  cards.forEach((card) => {
    if (!existingNames.has(card.dataset.agentName)) {
      card.remove()
    }
  })
}

function ensureCard(agent) {
  let card = grid.querySelector(`.card[data-agent-name="${agent.name}"]`)
  if (!card) {
    card = createCard(agent)
    grid.appendChild(card)
  }
  updateStatusPill(card, agent.online)
  updateUsernamePill(card, agent.username)
}

function createCard(agent) {
  const card = document.createElement('section')
  card.className = 'card'
  card.dataset.agentName = agent.name

  card.innerHTML = `
    <h2>${escapeHtml(agent.name)}</h2>
    <div>
      <span class="pill status-pill"></span>
      <span class="pill username-pill"></span>
    </div>
    <div class="actions">
      <button class="green" data-action="connect">Connect</button>
      <button class="orange" data-action="disconnect">Disconnect</button>
    </div>
    <div class="command">
      <input type="text" class="cmd-input"
        placeholder="Command: goto 0 64 0, dig, lookat..." />
      <button class="secondary" data-action="command">Run</button>
    </div>
    <div class="log-panel"></div>
  `

  updateStatusPill(card, agent.online)
  updateUsernamePill(card, agent.username)

  // Attach button handlers
  const buttons = card.querySelectorAll('button')
  buttons.forEach((btn) => {
    btn.addEventListener('click', () => onAction(agent.name, btn, card))
  })

  return card
}

function updateStatusPill(card, online) {
  const pill = card.querySelector('.status-pill')
  if (!pill) return
  pill.className = `pill status-pill ${online ? 'online' : 'offline'}`
  pill.textContent = online ? 'online' : 'offline'
}

function updateUsernamePill(card, username) {
  const pill = card.querySelector('.username-pill')
  if (!pill) return
  pill.textContent = username || '—'
}

function handleAgentStatus(name, online) {
  const card = grid.querySelector(`.card[data-agent-name="${name}"]`)
  if (!card) return
  updateStatusPill(card, online)
}

// ── Log entries ──

function addLog(name, entryType, data) {
  const card = grid.querySelector(`.card[data-agent-name="${name}"]`)
  if (!card) return

  const panel = card.querySelector('.log-panel')
  if (!panel) return

  const entry = document.createElement('div')
  entry.className = `log-entry ${entryType}`

  const time = data.timestamp
    ? new Date(data.timestamp).toLocaleTimeString()
    : new Date().toLocaleTimeString()

  switch (entryType) {
    case 'cmd-start':
      entry.innerHTML = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> → ${escapeHtml(data.command)}`
      break

    case 'cmd-done':
      entry.innerHTML = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> ← ${escapeHtml(data.command)}<br>${escapeHtml(data.output)}`
      break

    case 'cmd-error':
      entry.innerHTML = `<span class="log-time">${time}</span> <span class="log-tag">[CMD]</span> ✕ ${escapeHtml(data.command)}<br>${escapeHtml(data.output)}`
      break

    case 'llm-input':
      entry.innerHTML = `<span class="log-time">${time}</span> <span class="log-tag">[LLM]</span> → <em>${escapeHtml(data.model)}</em><br>${escapeHtml(truncate(data.prompt, 2000))}`
      break

    case 'llm-output':
      entry.innerHTML = `<span class="log-time">${time}</span> <span class="log-tag">[LLM]</span> ← <em>${escapeHtml(data.model)}</em><br>${escapeHtml(truncate(data.response, 2000))}`
      break
  }

  panel.appendChild(entry)
  panel.scrollTop = panel.scrollHeight

  // Keep max 200 entries to avoid memory bloat
  while (panel.children.length > 200) {
    panel.firstChild.remove()
  }
}

// ── Actions ──

async function onAction(name, button, card) {
  const action = button.dataset.action

  if (action === 'connect' || action === 'disconnect') {
    await fetch(`/api/agents/${encodeURIComponent(name)}/${action}`, {
      method: 'POST'
    })
    return
  }

  if (action === 'command') {
    const input = card.querySelector('.cmd-input')
    const command = input.value.trim()
    if (!command) return

    const response = await fetch(`/api/agents/${encodeURIComponent(name)}/command`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command })
    })
    const result = await response.json()

    // Show result in log panel via WebSocket would be real-time,
    // but for HTTP commands we render inline too
    addLog(name, result.ok ? 'cmd-done' : 'cmd-error', {
      command,
      output: result.output || result.error || 'Done',
      timestamp: Date.now()
    })

    input.value = ''
  }
}

// ── Snapshot handler (WebSocket on connect) ──

function applySnapshot(data) {
  uptimeEl.textContent = `Uptime: ${data.uptimeSec}s`
  if (data.agents) {
    buildGrid(data.agents)
  }
}

// ── Helpers ──

function escapeHtml(str) {
  if (!str) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function truncate(str, maxLen) {
  if (!str || str.length <= maxLen) return str
  return str.slice(0, maxLen) + '…'
}

// ── Bootstrap ──

refresh()
