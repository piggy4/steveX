const http = require('http')
const { createApp } = require('./server/app')
const { setupWebSocket } = require('./server/ws')

/**
 * Start the Web management panel (Express + WebSocket).
 */

function startWebServer(manager) {
  const webConfig = manager.config.web
  const host = webConfig.host
  const port = webConfig.port

  const app = createApp(manager)
  const server = http.createServer(app)

  setupWebSocket(server, manager)

  server.listen(port, host, () => {
    console.log(`[info][web] Web panel listening on http://${host}:${port}`)
  })
}

module.exports = { startWebServer }
