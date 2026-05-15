const http = require('http')
const { logInfo } = require('../telemetry/logger')
const { createApp } = require('./server/app')
const { setupWebSocket } = require('./server/ws')

/**
 * Start the Web management panel (Express + WebSocket).
 *
 * @param {Object} options
 * @param {import('../multiagent/agent_manager').AgentManager} options.manager
 * @param {Function} options.loadConfig
 * @param {{ host?: string, port?: number, enabled?: boolean }} options.webConfig
 */
function startWebServer({ manager, loadConfig, webConfig }) {
  const host = webConfig.host || '0.0.0.0'
  const port = webConfig.port || 8090

  const app = createApp(manager, loadConfig)
  const server = http.createServer(app)

  setupWebSocket(server, manager)

  server.listen(port, host, () => {
    logInfo(`Web panel listening on http://${host}:${port}`)
  })
}

module.exports = { startWebServer }
