// steveX Debug Console — static front-end prototype
// This file intentionally does not require backend APIs. It renders the UI from
// local state so the page can be opened as a pure frontend mock.

const icons = {
  home: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="m3 11 9-8 9 8"/><path d="M5 10v10h14V10"/><path d="M9 20v-6h6v6"/></svg>',
  agents: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
  world: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z"/></svg>',
  tasks: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>',
  capsules: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M4 8a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4Z"/><path d="M8 11h8"/><path d="M9 15h6"/><path d="M9 7l2 2 4-4"/></svg>',
  evolution: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-5 6"/><path d="M19 9h-5"/><path d="M19 9v5"/></svg>',
  configs: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5Z"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.6 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.6 1.65 1.65 0 0 0 10 3.09V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9c.14.31.49.99 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"/></svg>',
  logs: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/><path d="M9 13h6"/><path d="M9 17h3"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M21 12a9 9 0 0 0-15-6.7L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 15 6.7L21 16"/><path d="M21 21v-5h-5"/></svg>',
  plus: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M12 5v14"/><path d="M5 12h14"/></svg>',
  search: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>',
  link: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>',
  unlink: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M17 7h1a5 5 0 0 1 0 10h-2"/><path d="M8 17H6A5 5 0 0 1 6 7h1"/><path d="M12 7h2"/><path d="M10 17h2"/><path d="m2 2 20 20"/></svg>',
  heart: '<svg class="heart" viewBox="0 0 24 24" fill="currentColor" stroke="currentColor"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8Z"/></svg>',
  gamepad: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M6 12h4"/><path d="M8 10v4"/><path d="M15 13h.01"/><path d="M18 11h.01"/><path d="M17.3 6H6.7a4 4 0 0 0-3.9 3.1l-1 4.6a4 4 0 0 0 6.7 3.7L10.5 15h3l2 2.4a4 4 0 0 0 6.7-3.7l-1-4.6A4 4 0 0 0 17.3 6Z"/></svg>',
  brain: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M9.5 2A3.5 3.5 0 0 0 6 5.5v.4A4.5 4.5 0 0 0 4.5 14v.5a3.5 3.5 0 0 0 5.7 2.7"/><path d="M14.5 2A3.5 3.5 0 0 1 18 5.5v.4A4.5 4.5 0 0 1 19.5 14v.5a3.5 3.5 0 0 1-5.7 2.7"/><path d="M12 2v20"/><path d="M8 8h4"/><path d="M12 8h4"/><path d="M8 13h4"/><path d="M12 13h4"/></svg>',
  pin: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 1 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>',
  walk: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><circle cx="13" cy="4" r="2"/><path d="M8 22l3-7"/><path d="M16 22l-2-6-3-2 1-5"/><path d="m7 12 4-3 4 1"/></svg>'
}

const agentsList = document.getElementById('agents-list')
const searchInput = document.getElementById('agent-search')
const statusFilter = document.getElementById('status-filter')
const sortBy = document.getElementById('sort-by')
const reloadButton = document.getElementById('reload')
const newAgentButton = document.getElementById('new-agent')
const uptimeEl = document.getElementById('uptime')
const wsIndicator = document.getElementById('ws-indicator')
const sidebarWs = document.getElementById('sidebar-ws')

let uptimeSeconds = 33
let nextAgentId = 3

let agents = [
  {
    name: 'steveX-1',
    online: true,
    health: 20,
    maxHealth: 20,
    mode: 'Survival',
    model: 'deepseek-v4-flash',
    position: { x: 123, y: 64, z: -45 },
    action: 'Mining iron ore'
  },
  {
    name: 'steveX-2',
    online: true,
    health: 20,
    maxHealth: 20,
    mode: 'Creative',
    model: 'deepseek-v4-flash',
    position: { x: 98, y: 70, z: 210 },
    action: 'Building wall'
  }
]

function hydrateIcons(root = document) {
  root.querySelectorAll('[data-icon]').forEach((slot) => {
    const name = slot.dataset.icon
    if (icons[name]) slot.innerHTML = icons[name]
  })
}

function render() {
  const query = searchInput.value.trim().toLowerCase()
  const status = statusFilter.value
  const order = sortBy.value

  let visibleAgents = agents.filter((agent) => {
    const matchesText = agent.name.toLowerCase().includes(query)
    const matchesStatus = status === 'all' || (status === 'online' ? agent.online : !agent.online)
    return matchesText && matchesStatus
  })

  visibleAgents = visibleAgents.sort((a, b) => {
    if (order === 'status') return Number(b.online) - Number(a.online) || a.name.localeCompare(b.name)
    if (order === 'health') return b.health - a.health || a.name.localeCompare(b.name)
    return a.name.localeCompare(b.name, undefined, { numeric: true })
  })

  if (!visibleAgents.length) {
    agentsList.innerHTML = '<div class="empty-state">No agents match the current filters.</div>'
    return
  }

  agentsList.innerHTML = visibleAgents.map(agentTemplate).join('')
  hydrateIcons(agentsList)
}

function agentTemplate(agent) {
  const statusClass = agent.online ? 'online' : 'offline'
  const statusText = agent.online ? 'online' : 'offline'
  const percent = Math.max(0, Math.min(100, Math.round((agent.health / agent.maxHealth) * 100)))
  const position = `x: ${agent.position.x}, y: ${agent.position.y}, z: ${agent.position.z}`

  return `
    <article class="agent-card" data-agent-name="${escapeHtml(agent.name)}">
      <div class="agent-header">
        <div class="agent-id">
          <h2>${escapeHtml(agent.name)}</h2>
          <span class="dot ${statusClass}" aria-hidden="true"></span>
          <span class="state-label">${statusText}</span>
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
                  <span class="health-number">${agent.health} / ${agent.maxHealth}</span>
                  <span class="health-bar" aria-label="Health ${percent}%"><span class="health-fill" style="--health:${percent}%"></span></span>
                </span>
              </div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Game Mode</div>
              <div class="stat-value"><span data-icon="gamepad"></span>${escapeHtml(agent.mode)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Model</div>
              <div class="stat-value"><span data-icon="brain"></span>${escapeHtml(agent.model)}</div>
            </div>
          </div>

          <div class="stats-column">
            <div class="stat-row">
              <div class="stat-label">Position</div>
              <div class="stat-value"><span data-icon="pin"></span>${escapeHtml(position)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Current Action</div>
              <div class="stat-value"><span data-icon="walk"></span>${escapeHtml(agent.action)}</div>
            </div>

            <div class="stat-row">
              <div class="stat-label">Status</div>
              <div class="stat-value"><span class="dot ${statusClass}"></span>${agent.online ? 'Online' : 'Offline'}</div>
            </div>
          </div>
        </div>

        <div class="command-stack">
          <form class="command-panel" data-action="command" data-agent="${escapeHtml(agent.name)}">
            <div class="command-title">Send Command <span>(to Agent)</span></div>
            <div class="command-row">
              <input type="text" name="message" placeholder="Enter command..." autocomplete="off" />
              <button class="btn send" type="submit">Send</button>
            </div>
          </form>

          <form class="command-panel" data-action="message" data-agent="${escapeHtml(agent.name)}">
            <div class="command-title">Send Message <span>(to LLM Planner)</span></div>
            <div class="command-row">
              <input type="text" name="message" placeholder="Enter message for LLM Planner..." autocomplete="off" />
              <button class="btn send" type="submit">Send</button>
            </div>
          </form>
        </div>
      </div>
    </article>
  `
}

function setWsStatus(isOnline) {
  wsIndicator.textContent = isOnline ? 'WS: online' : 'WS: offline'
  wsIndicator.className = isOnline ? 'ws-online' : 'ws-offline'
  sidebarWs.textContent = wsIndicator.textContent
  const sidebarDot = document.querySelector('.sidebar-status .dot')
  sidebarDot.className = `dot ${isOnline ? 'online' : 'offline'}`
}

function updateUptime() {
  uptimeEl.textContent = `Uptime: ${uptimeSeconds}s`
}

function mutateAgent(name, updater) {
  agents = agents.map((agent) => agent.name === name ? { ...agent, ...updater(agent) } : agent)
  render()
}

function handleAgentAction(event) {
  const button = event.target.closest('button[data-action]')
  if (!button) return

  const name = button.dataset.agent
  const action = button.dataset.action

  if (action === 'connect') {
    mutateAgent(name, () => ({ online: true, action: 'Connected and awaiting task' }))
  }

  if (action === 'disconnect') {
    mutateAgent(name, () => ({ online: false, action: 'Disconnected' }))
  }
}

function handleFormSubmit(event) {
  const form = event.target.closest('form.command-panel')
  if (!form) return

  event.preventDefault()

  const input = form.querySelector('input[name="message"]')
  const value = input.value.trim()
  if (!value) return

  const type = form.dataset.action === 'command' ? 'Command sent' : 'Message queued'
  mutateAgent(form.dataset.agent, () => ({ action: `${type}: ${value}` }))
  input.value = ''
}

function addAgent() {
  const id = nextAgentId++
  agents = [
    ...agents,
    {
      name: `steveX-${id}`,
      online: true,
      health: 20,
      maxHealth: 20,
      mode: id % 2 ? 'Survival' : 'Creative',
      model: 'deepseek-v4-flash',
      position: { x: 80 + id * 12, y: 64 + id, z: id % 2 ? -32 : 180 + id * 8 },
      action: 'Awaiting instruction'
    }
  ]
  render()
}

function reloadConfig() {
  reloadButton.animate([
    { transform: 'translateY(0)' },
    { transform: 'translateY(-1px) scale(0.98)' },
    { transform: 'translateY(0)' }
  ], { duration: 220 })

  uptimeSeconds = 33
  updateUptime()
  setWsStatus(true)
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

searchInput.addEventListener('input', render)
statusFilter.addEventListener('change', render)
sortBy.addEventListener('change', render)
newAgentButton.addEventListener('click', addAgent)
reloadButton.addEventListener('click', reloadConfig)
agentsList.addEventListener('click', handleAgentAction)
agentsList.addEventListener('submit', handleFormSubmit)

setInterval(() => {
  uptimeSeconds += 1
  updateUptime()
}, 1000)

hydrateIcons()
updateUptime()
setWsStatus(true)
render()
