// ── steveX WebSocket client ──

import { setState, patchAgent, addLog } from './state.js'

const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
let ws = null

export function initWebSocket() {
  ws = new WebSocket(`${protocol}//${location.host}`)

  ws.onopen = () => {
    setState({ wsConnected: true })
  }

  ws.onclose = () => {
    setState({ wsConnected: false })
    // Auto-reconnect after 2s
    setTimeout(() => {
      if (!ws || ws.readyState === WebSocket.CLOSED) {
        window.location.reload()
      }
    }, 2000)
  }

  ws.onmessage = (event) => {
    const data = JSON.parse(event.data)

    switch (data.type) {
      case 'snapshot':
        setState({
          agents: data.agents,
          uptimeSec: data.uptimeSec
        })
        break

      case 'agent:connect':
        patchAgent(data.name, () => ({ online: true }))
        break

      case 'agent:disconnect':
        patchAgent(data.name, () => ({ online: false }))
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
}
