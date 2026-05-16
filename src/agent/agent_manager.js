const { EventEmitter } = require('events')
const { SteveXAgent } = require('./agent')
const { loadCommands } = require('../commands')

class AgentManager {
  constructor(loadConfig) {
    this.loadConfig = loadConfig
    this.config = this.loadConfig()
    this.sharedCommands = loadCommands().commands
    this.agents = new Map()
    this.eventBus = new EventEmitter()
    this.eventBus.setMaxListeners(50)
    // Pre-index agent configs by name for O(1) lookup
    this.agentConfigs = new Map(
      (this.config.agents || []).map(cfg => [cfg.name, cfg])
    )
  }

  // ── Lifecycle ──

  disconnectAll() {
    for (const name of this.agents.keys()) {
      this.disconnectAgent(name)
    }
  }

  reload() {
    this.disconnectAll()
    this.config = this.loadConfig()
    this.agentConfigs = new Map(
      (this.config.agents || []).map(cfg => [cfg.name, cfg])
    )
  }

  connectAgent(name) {
    const cfg = this.agentConfigs.get(name)
    if (!cfg || this.agents.get(name)?.isOnline()) return !!cfg

    const agent = new SteveXAgent(cfg, name, this.sharedCommands)
    agent.start()
    this.agents.set(name, agent)
    this.eventBus.emit('agent:connect', { name })
    return true
  }

  disconnectAgent(name) {
    const agent = this.agents.get(name)
    if (!agent || !agent.isOnline()) return false

    this.eventBus.emit('agent:disconnect', { name })
    agent.shutdown()
    this.agents.delete(name)
    return true
  }

  // ── Operations ──

  async sendCommand(name, command) {
    const agent = this.agents.get(name)
    if (!agent) {
      return { ok: false, error: 'Agent not found or not started' }
    }

    this.eventBus.emit('agent:command:start', {
      name,
      command,
      timestamp: Date.now()
    })

    const result = await agent.executeCommand(command)

    this.eventBus.emit('agent:command:done', {
      name,
      command,
      ok: result.ok,
      output: result.output || null,
      error: result.error || null,
      timestamp: Date.now()
    })

    return result
  }

  // ── Queries ──

  getStatus() {
    return [...this.agentConfigs.values()].map(cfg => {
      const agent = this.agents.get(cfg.name)
      return {
        name: cfg.name,
        username: agent?.getUsername() ?? cfg.minecraft.username,
        online: agent?.isOnline() ?? false
      }
    })
  }
}

module.exports = { AgentManager }
