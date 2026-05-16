const { loadConfig } = require('./utils/config')
const { AgentManager } = require('./agent/agent_manager')
const { startWebServer } = require('./web/server')

const manager = new AgentManager(loadConfig)

console.log('[info][main] Starting SteveX with config:', manager.config)

startWebServer(manager)

process.on('SIGINT', () => {
  console.log('[info][main] Shutting down')
  manager.disconnectAll()
  process.exit(0)
})
