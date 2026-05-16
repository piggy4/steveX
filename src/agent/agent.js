const { createBot } = require('../connector/mineflayer_connector')
const { logInfo, logError } = require('../telemetry/logger')
const { pathfinder, Movements } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')
const { loadCommands } = require('../commands')

class SteveXAgent {
  constructor(config, name) {
    this.config = config
    this.name = name || 'steveX'
    this.bot = null
    this.movements = null
    this.commands = {}
    this.commandList = []
    this.connecting = false
    this.connected = false

    // Load commands from filesystem
    const { commands, list } = loadCommands()
    this.commands = commands
    this.commandList = list
  }

  getAvailableCommands() {
    return Object.keys(this.commands).sort()
  }

  getCommandList() {
    return this.commandList
  }

  start() {
    this.connecting = true
    this.connected = false
    this.bot = createBot(this.config.minecraft)
    this.bot.loadPlugin(pathfinder)
    this.registerEvents()
  }

  registerEvents() {
    this.bot.on('login', () => {
      this.connected = true
    })

    this.bot.once('spawn', () => {
      this.connecting = false
      this.connected = true
      logInfo(`Bot spawned (${this.name})`)
      const mcData = mcDataLoader(this.bot.version)
      this.movements = new Movements(this.bot, mcData)
      this.bot.pathfinder.setMovements(this.movements)
    })

    this.bot.on('end', () => {
      this.connecting = false
      this.connected = false
    })

    this.bot.on('kicked', (reason) => {
      this.connecting = false
      this.connected = false
      logError(`Bot kicked (${this.name})`, reason)
    })

    this.bot.on('error', (error) => {
      // Suppress noisy pathfinder timeouts — they are handled internally
      if (error.message && error.message.includes('Took to long to decide path to goal')) {
        logInfo(`Pathfinder timeout (${this.name})`)
        return
      }
      this.connecting = false
      this.connected = false
      logError(`Bot error (${this.name})`, error)
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
      const available = this.getAvailableCommands().join(', ')
      return { ok: false, error: `Unknown command: ${command}. Available: ${available}` }
    }

    try {
      return await handler.call(this, this.bot, args)
    } catch (err) {
      logError(`Command error (${this.name})`, err)
      return { ok: false, error: err.message || String(err) }
    }
  }

  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }

  /** Whether the bot is connected and spawned, or is actively connecting. */
  isOnline() {
    if (!this.bot) return false
    if (this.connected) return true
    if (this.connecting) return true
    return false
  }

  /** The in-game username, falling back to config. */
  getUsername() {
    return this.bot ? this.bot.username : this.config.minecraft.username
  }

  /** Gracefully disconnect the bot from the server. */
  shutdown() {
    if (this.bot) {
      this.bot.quit('Shutdown')
    }
  }
}

module.exports = {
  SteveXAgent
}
