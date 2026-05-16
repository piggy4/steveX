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

export async function sendCommand(name, command) {
  const res = await fetch(`/api/agents/${encodeURIComponent(name)}/command`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ command })
  })
  const result = await res.json()
  return result
}
