const http = require('http')
const { createApp } = require('./server/app')
const { setupWebSocket } = require('./server/ws')

/**
 * Start the Web management panel (Express + WebSocket).
 * @param {import('../agent/agent_manager').AgentManager} manager
 */
function startWebServer(manager) {
  const webConfig = manager.config.web || {}
  const host = webConfig.host || 'localhost'
  const port = webConfig.port || 8090

  const app = createApp(manager)
  const server = http.createServer(app)

  setupWebSocket(server, manager)

  server.listen(port, host, () => {
    console.log(`[info][web] Web panel listening on http://${host}:${port}`)
  })
}

module.exports = { startWebServer }
