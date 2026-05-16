const { WebSocketServer } = require('ws')
const { logInfo } = require('../../telemetry/logger')

/**
 * Set up WebSocket server on top of the existing HTTP server.
 * Bridges AgentManager eventBus → real-time browser push.
 *
 * @param {import('http').Server} server
 * @param {import('../../multiagent/agent_manager').AgentManager} manager
 */
function setupWebSocket(server, manager) {
  const wss = new WebSocketServer({ server })

  wss.on('connection', (ws) => {
    logInfo('Web client connected')

    ws.on('close', () => {
      logInfo('Web client disconnected')
    })

    // Send current status snapshot immediately after connect
    ws.send(JSON.stringify({
      type: 'snapshot',
      agents: manager.getStatus(),
      uptimeSec: Math.floor(process.uptime())
    }))
  })

  // ── Periodic status updates every 3 seconds ──
  const statusInterval = setInterval(() => {
    broadcast(wss, {
      type: 'snapshot',
      agents: manager.getStatus(),
      uptimeSec: Math.floor(process.uptime())
    })
  }, 3000)

  // Clean up interval when server closes
  wss.on('close', () => clearInterval(statusInterval))

  // ── AgentManager eventBus → WebSocket broadcast ──

  const eventBus = manager.eventBus
  if (!eventBus) return

  eventBus.on('agent:connect', (data) => {
    broadcast(wss, { type: 'agent:connect', name: data.name })
  })

  eventBus.on('agent:disconnect', (data) => {
    broadcast(wss, { type: 'agent:disconnect', name: data.name })
  })

  eventBus.on('agent:command:start', (data) => {
    broadcast(wss, {
      type: 'agent:command:start',
      name: data.name,
      command: data.command,
      timestamp: data.timestamp
    })
  })

  eventBus.on('agent:command:done', (data) => {
    broadcast(wss, {
      type: 'agent:command:done',
      name: data.name,
      command: data.command,
      ok: data.ok,
      output: data.output || null,
      error: data.error || null,
      timestamp: data.timestamp
    })
  })

  eventBus.on('agent:llm:input', (data) => {
    broadcast(wss, {
      type: 'agent:llm:input',
      name: data.name,
      model: data.model,
      prompt: data.prompt,
      timestamp: data.timestamp
    })
  })

  eventBus.on('agent:llm:output', (data) => {
    broadcast(wss, {
      type: 'agent:llm:output',
      name: data.name,
      model: data.model,
      response: data.response,
      timestamp: data.timestamp
    })
  })
}

function broadcast(wss, data) {
  const message = JSON.stringify(data)
  for (const client of wss.clients) {
    if (client.readyState === 1) { // WebSocket.OPEN
      client.send(message)
    }
  }
}

module.exports = { setupWebSocket }
