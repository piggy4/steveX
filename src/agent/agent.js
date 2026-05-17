const mineflayer = require('mineflayer')
const { pathfinder, Movements } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')

class SteveXAgent {
  /**
   * @param {object} config - agent 配置对象
   * @param {string} name - agent 名称
   * @param {object} [commands] - 共享命令表 { name -> handler }，由 AgentManager 传入
   * @param {object} [stateStore] - 运行时状态存储，由 AgentManager 传入
   */
  constructor(config, name = 'steveX', commands = {}, stateStore = null) {
    this.config = config
    this.name = name
    this.bot = null
    this.movements = null
    this.commands = commands
    this.stateStore = stateStore
    this.connected = false
    this.lastPositionAt = 0
  }

  start() {
    const mc = this.config.minecraft
    this.bot = mineflayer.createBot({
      host: mc.host,
      port: mc.port,
      username: mc.username,
      auth: mc.auth,
      version: mc.version
    })
    this.bot.loadPlugin(pathfinder)
    this.registerEvents()
  }

  registerEvents() {
    this.bot.once('spawn', () => {
      this.connected = true
      console.log(`[info](${this.name}) Bot spawned `)
      const mcData = mcDataLoader(this.bot.version)
      this.movements = new Movements(this.bot, mcData)
      this.bot.pathfinder.setMovements(this.movements)
      this.updateState(this.buildSnapshot())
    })

    // 统一断连处理：end/kicked 都标记为离线
    const onDisconnect = (reason) => {
      this.connected = false
      this.updateState({
        online: false,
        action_status: 'offline',
        current_action: null,
        last_seen: Date.now()
      })
      if (reason) console.error(`[error](${this.name}) Bot disconnected`, reason)
    }

    this.bot.on('end', () => onDisconnect())
    this.bot.on('kicked', (reason) => onDisconnect(reason))
    this.bot.on('error', (error) => {
      console.error(`[error](${this.name}) Bot error`, error)
    })

    this.bot.on('health', () => {
      this.updateState({
        health: this.bot.health,
        hunger: this.bot.food,
        last_seen: Date.now()
      })
    })

    this.bot.on('game', () => {
      this.updateState({
        gamemode: this.bot.game?.gameMode ?? null,
        last_seen: Date.now()
      })
    })

    this.bot.on('move', () => {
      const now = Date.now()
      if (now - this.lastPositionAt < 300) return
      this.lastPositionAt = now
      const pos = this.bot.entity?.position
      if (!pos) return
      this.updateState({
        position: { x: pos.x, y: pos.y, z: pos.z },
        last_seen: now
      })
    })

    this.bot.inventory?.on('updateSlot', () => {
      this.updateState({
        inventory: this.bot.inventory.items(),
        last_seen: Date.now()
      })
    })
  }

  buildSnapshot() {
    const pos = this.bot.entity?.position
    return {
      online: true,
      health: this.bot.health,
      hunger: this.bot.food,
      gamemode: this.bot.game?.gameMode ?? null,
      position: pos ? { x: pos.x, y: pos.y, z: pos.z } : null,
      inventory: this.bot.inventory?.items() || [],
      last_seen: Date.now()
    }
  }

  updateState(patch) {
    if (!this.stateStore) return
    this.stateStore.updateState(this.name, patch)
  }

  /**
   * Execute a mineflayer command for this agent.
   * Commands are registered via registerCommand().
   * @param {string} input - raw command string
   * @returns {Promise<{ok:boolean,output?:string,error?:string}>}
   */
  async executeCommand(input) {
    if (!this.bot) {
      return { ok: false, error: 'Bot not started' }
    }

    const trimmed = input.trim()
    if (!trimmed) {
      return { ok: false, error: 'Empty command' }
    }

    const parts = trimmed.split(/\s+/)
    const command = parts[0].toLowerCase()
    const args = parts.slice(1)

    const handler = this.commands[command]
    if (!handler) {
      const available = Object.keys(this.commands).sort().join(', ')
      return { ok: false, error: `Unknown command: ${command}. Available: ${available}` }
    }

    try {
      return await handler.call(this, this.bot, args)
    } catch (err) {
      console.error(`[error](${this.name}) Command error`, err)
      return { ok: false, error: err.message || String(err) }
    }
  }

  /** Whether the bot is connected and spawned. */
  isOnline() {
    return this.bot && this.connected
  }

  /** The in-game username, falling back to config. */
  getUsername() {
    return this.bot ? this.bot.username : this.config.minecraft.username
  }

  /** Gracefully disconnect the bot from the server. */
  shutdown() {
    if (this.bot) {
      this.bot.end()
    }
    this.connected = false
  }
}

module.exports = {
  SteveXAgent
}
