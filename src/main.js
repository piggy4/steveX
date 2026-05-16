const { loadConfig } = require('./utils/config')
const { AgentManager } = require('./agent/agent_manager')
const { startWebServer } = require('./web/server')
const { logInfo } = require('./telemetry/logger')

function main() {
  const manager = new AgentManager(loadConfig)

  logInfo('steveX started')

  startWebServer(manager)

  process.on('SIGINT', () => {
    logInfo('Shutting down')
    manager.disconnectAll()
    process.exit(0)
  })
}

main()
