const mineflayer = require('mineflayer')
const { pathfinder, Movements } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')

class SteveXAgent {
  /**
   * @param {object} config - agent 配置对象
   * @param {string} name - agent 名称
   * @param {object} [commands] - 共享命令表 { name -> handler }，由 AgentManager 传入
   */
  constructor(config, name, commands) {
    this.config = config
    this.name = name || 'steveX'
    this.bot = null
    this.movements = null
    this.commands = commands || {}
    this.connected = false
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
      console.log(`[info] Bot spawned (${this.name})`)
      const mcData = mcDataLoader(this.bot.version)
      this.movements = new Movements(this.bot, mcData)
      this.bot.pathfinder.setMovements(this.movements)
    })

    this.bot.on('end', () => {
      this.connected = false
    })

    this.bot.on('kicked', (reason) => {
      this.connected = false
      console.error(`[error] Bot kicked (${this.name})`, reason)
    })

    this.bot.on('error', (error) => {
      // Suppress noisy pathfinder timeouts — they are handled internally
      if (error.message && error.message.includes('Took to long to decide path to goal')) {
        console.log(`[info] Pathfinder timeout (${this.name})`)
        return
      }
      this.connected = false
      console.error(`[error] Bot error (${this.name})`, error)
    })
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
      console.error(`[error] Command error (${this.name})`, err)
      return { ok: false, error: err.message || String(err) }
    }
  }

  /** Whether the bot is connected and spawned. */
  isOnline() {
    return !!(this.bot && this.connected)
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
