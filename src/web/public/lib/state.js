// ── steveX global reactive state ──

const state = {
  agents: [],
  uptimeSec: 0,
  wsConnected: false,
  filters: { query: '', status: 'all', sortBy: 'name' },
  logs: {},          // { [agentName]: LogEntry[] }
  _listeners: []
}

const LOG_MAX = 200

export function getState() {
  return state
}

export function setState(partial) {
  Object.assign(state, partial)
  notify()
}

export function subscribe(fn) {
  state._listeners.push(fn)
  return () => {
    state._listeners = state._listeners.filter(f => f !== fn)
  }
}

export function patchAgent(name, updater) {
  const idx = state.agents.findIndex(a => a.name === name)
  if (idx !== -1) {
    state.agents[idx] = { ...state.agents[idx], ...updater(state.agents[idx]) }
    notify()
  }
}

export function addLog(name, entryType, data) {
  if (!state.logs[name]) state.logs[name] = []

  const entry = {
    type: entryType,
    command: data.command || null,
    output: data.output || null,
    model: data.model || null,
    prompt: data.prompt || null,
    response: data.response || null,
    timestamp: data.timestamp || Date.now()
  }

  state.logs[name].push(entry)

  while (state.logs[name].length > LOG_MAX) {
    state.logs[name].shift()
  }

  notify()
}

function notify() {
  state._listeners.forEach(fn => fn())
}
