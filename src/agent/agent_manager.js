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
     * Map of agent entries keyed by agent name.
     * @type {Map<string, { name:string, config:object, agent:SteveXAgent|null }>}
     */
    this.agentMap = new Map()
    /**
     * Event bus bridging to WebSocket layer.
     * @type {EventEmitter}
     */
    this.eventBus = new EventEmitter()
    // Prevent MaxListeners warnings when multiple WebSocket clients connect
    this.eventBus.setMaxListeners(50)

    // Pre-populate agentMap entries from config (agents not yet connected)
    this.normalizeAgents(this.config).forEach(entry => {
      entry.agent = null
      this.agentMap.set(entry.name, entry)
    })
  }

  // ── Lifecycle ──

  connectAll() {
    const entries = this.normalizeAgents(this.config)
    this.agentMap.clear()

    for (const entry of entries) {
      const agent = new SteveXAgent(entry.config, entry.name, this.sharedCommands)
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

  reload() {
    this.disconnectAll()
    this.config = this.loadConfig()
    this.agentMap.clear()
    this.normalizeAgents(this.config).forEach(entry => {
      entry.agent = null
      this.agentMap.set(entry.name, entry)
    })
  }

  connectAgent(name) {
    const entry = this.agentMap.get(name)
    if (!entry) return false
    if (entry.agent && entry.agent.isOnline()) return true

    const agent = new SteveXAgent(entry.config, entry.name, this.sharedCommands)
    agent.start()
    entry.agent = agent
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
      const name = agent.name || agent.minecraft?.username || 'steveX'
      // NOTE: config 直接引用 agent 自身，不做深拷贝/merge。
      // 每个 agent 的配置在 configs/defaults/app.json 中已是完整独立对象，
      // 不需要从全局 config 剥离后再覆盖。如无充分理由请勿改为 deepMerge。
      return {
        name,
        config: agent,
        agent: null
      }
    })
  }
}

module.exports = { AgentManager }
