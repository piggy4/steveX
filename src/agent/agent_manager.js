const { EventEmitter } = require('events')
const { SteveXAgent } = require('./agent')
const { loadCommands } = require('../commands')

class AgentManager {
  constructor(loadConfig) {
    this.loadConfig = loadConfig
    this.config = this.loadConfig()
    // Load commands once, share across all agents
    this.sharedCommands = loadCommands().commands
    /**
     * Map of connected agent instances keyed by agent name.
     * @type {Map<string, SteveXAgent>}
     */
    this.agents = new Map()
    /**
     * Event bus bridging to WebSocket layer.
     * @type {EventEmitter}
     */
    this.eventBus = new EventEmitter()
    // Prevent MaxListeners warnings when multiple WebSocket clients connect
    this.eventBus.setMaxListeners(50)
  }

  // ── Lifecycle ──

  disconnectAll() {
    for (const [name, agent] of this.agents) {
      this.eventBus.emit('agent:disconnect', { name })
      agent.shutdown()
    }
    this.agents.clear()
  }

  reload() {
    this.disconnectAll()
    this.config = this.loadConfig()
  }

  connectAgent(name) {
    const cfg = (this.config.agents || []).find(c => c.name === name)
    if (!cfg) return false
    const existing = this.agents.get(name)
    if (existing && existing.isOnline()) return true

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
    const statuses = []
    for (const cfg of (this.config.agents || [])) {
      const agent = this.agents.get(cfg.name)
      statuses.push({
        name: cfg.name,
        username: agent?.getUsername() ?? cfg.minecraft.username,
        online: agent?.isOnline() ?? false
      })
    }
    return statuses
  }
}

module.exports = { AgentManager }
