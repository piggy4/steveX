// ── steveX Debug Console ──
// ES Module entry point. WebSocket-driven real-time agent monitoring.

import { getState, setState, subscribe } from './lib/state.js'
import { initWebSocket } from './lib/ws-client.js'
import { fetchStatus, reloadConfig } from './lib/api.js'
import { hydrateIcons } from './lib/icons.js'
import { renderAgents, initAgents } from './pages/agents.js'

// ── DOM refs ──

const agentsList = document.getElementById('agents-list')
const searchInput = document.getElementById('agent-search')
const statusFilter = document.getElementById('status-filter')
const sortBy = document.getElementById('sort-by')
const reloadButton = document.getElementById('reload')
const newAgentButton = document.getElementById('new-agent')
const uptimeEl = document.getElementById('uptime')
const wsIndicator = document.getElementById('ws-indicator')
const sidebarWs = document.getElementById('sidebar-ws')
const sidebarDot = document.querySelector('.sidebar-status .dot')

// ── Sidebar navigation (placeholder) ──

document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', (e) => {
    const label = item.textContent.trim()
    if (label !== 'Agents') {
      e.preventDefault()
      alert('Coming soon')
    }
  })
})

// ── Controls ──

searchInput.addEventListener('input', () => {
  const state = getState()
  state.filters.query = searchInput.value.trim()
  renderAgents(agentsList)
})

statusFilter.addEventListener('change', () => {
  const state = getState()
  state.filters.status = statusFilter.value
  renderAgents(agentsList)
})

sortBy.addEventListener('change', () => {
  const state = getState()
  state.filters.sortBy = sortBy.value
  renderAgents(agentsList)
})

newAgentButton.addEventListener('click', () => {
  alert('Coming soon')
})

reloadButton.addEventListener('click', async () => {
  reloadButton.animate([
    { transform: 'translateY(0)' },
    { transform: 'translateY(-1px) scale(0.98)' },
    { transform: 'translateY(0)' }
  ], { duration: 220 })

  await reloadConfig()
})

// ── WS & uptime indicators ──

function updateWsUI() {
  const { wsConnected } = getState()
  wsIndicator.textContent = wsConnected ? 'WS: online' : 'WS: offline'
  wsIndicator.className = wsConnected ? 'ws-online' : 'ws-offline'
  if (sidebarWs) sidebarWs.textContent = wsIndicator.textContent
  if (sidebarDot) sidebarDot.className = `dot ${wsConnected ? 'online' : 'offline'}`
}

function updateUptimeUI() {
  uptimeEl.textContent = `Uptime: ${getState().uptimeSec}s`
}

// ── Bootstrap ──

hydrateIcons()
initAgents(agentsList)
initWebSocket()

// Initial data load
fetchStatus().then(() => {
  renderAgents(agentsList)
  updateUptimeUI()
  updateWsUI()
})

// React to state changes
subscribe(() => {
  updateWsUI()
  updateUptimeUI()
})

// Uptime ticker (client-side)
setInterval(() => {
  const state = getState()
  state.uptimeSec += 1
  updateUptimeUI()
}, 1000)
