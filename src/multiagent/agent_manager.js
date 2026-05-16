const { EventEmitter } = require('events')
const { SteveXAgent } = require('../agent/agent')

class AgentManager {
  constructor(config) {
    this.config = config
    /**
     * Map of agent entries keyed by agent name.
     * @type {Map<string, { name:string, config:object, agentConfigOverride:object, agent:SteveXAgent|null }>}
     */
    this.agentMap = new Map()
    /**
     * Event bus bridging to WebSocket layer.
     * @type {EventEmitter}
     */
    this.eventBus = new EventEmitter()
    // Prevent MaxListeners warnings when multiple WebSocket clients connect
    this.eventBus.setMaxListeners(50)
  }

  // ── Lifecycle ──

  connectAll() {
    const entries = this.normalizeAgents(this.config)
    this.agentMap.clear()

    for (const entry of entries) {
      const agent = new SteveXAgent(entry.config, entry.name)
      agent.start()
      entry.agent = agent
      this.agentMap.set(entry.name, entry)
      this.eventBus.emit('agent:connect', { name: entry.name })
    }
  }

  disconnectAll() {
    for (const entry of this.agentMap.values()) {
      if (entry.agent) {
        this.eventBus.emit('agent:disconnect', { name: entry.name })
        entry.agent.shutdown()
      }
      entry.agent = null
    }
  }

  reload(config) {
    this.disconnectAll()
    this.config = config
    this.connectAll()
  }

  connectAgent(name) {
    const entry = this.agentMap.get(name)
    if (!entry) return false
    if (entry.agent && entry.agent.isOnline()) return true

    // fix ⑤ — rebuild config from current global config each time
    const override = entry.agentConfigOverride || {}
    const freshConfig = {
      ...this.config,
      minecraft: { ...(this.config.minecraft || {}), ...(override.minecraft || {}) },
      llm: { ...(this.config.llm || {}), ...(override.llm || {}) }
    }

    const agent = new SteveXAgent(freshConfig, entry.name)
    agent.start()
    entry.agent = agent
    entry.config = freshConfig
    this.eventBus.emit('agent:connect', { name: entry.name })
    return true
  }

  disconnectAgent(name) {
    const entry = this.agentMap.get(name)
    if (!entry) return false
    if (entry.agent && entry.agent.isOnline()) {
      this.eventBus.emit('agent:disconnect', { name: entry.name })
      entry.agent.shutdown()
      entry.agent = null
      return true
    }
    return false
  }

  // ── Operations ──

  async sendCommand(name, command) {
    const entry = this.agentMap.get(name)
    if (!entry || !entry.agent) {
      return { ok: false, error: 'Agent not found or not started' }
    }

    this.eventBus.emit('agent:command:start', {
      name,
      command,
      timestamp: Date.now()
    })

    const result = await entry.agent.executeCommand(command)

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
    for (const entry of this.agentMap.values()) {
      statuses.push({
        name: entry.name,
        username: entry.agent ? entry.agent.getUsername() : entry.config.minecraft.username,
        online: entry.agent ? entry.agent.isOnline() : false
      })
    }
    return statuses
  }

  // ── Config ──

  normalizeAgents(config) {
    const agents = Array.isArray(config.agents) ? config.agents : []

    if (agents.length === 0) {
      throw new Error('No agents configured. Add agents in configs/defaults/app.json')
    }

    return agents.map((agent) => {
      const mergedMinecraft = agent.minecraft || {}
      const mergedLlm = agent.llm || {}
      const name = agent.name || mergedMinecraft.username || 'steveX'
      return {
        name,
        config: { ...config, minecraft: mergedMinecraft, llm: mergedLlm },
        agentConfigOverride: { minecraft: mergedMinecraft, llm: mergedLlm },
        agent: null
      }
    })
  }
}

module.exports = { AgentManager }
