const { loadConfig } = require('./utils/config')
const { AgentManager } = require('./multiagent/agent_manager')
const { startWebServer } = require('./web/server')
const { logInfo } = require('./telemetry/logger')

function main() {
  const config = loadConfig()
  const manager = new AgentManager(config)

  logInfo('steveX started')

  manager.connectAll()

  const webConfig = config.web || { enabled: false }
  if (webConfig.enabled) {
    startWebServer({
      manager,
      loadConfig,
      webConfig
    })
  }

  process.on('SIGINT', () => {
    logInfo('Shutting down')
    manager.disconnectAll()
    process.exit(0)
  })
}

main()
