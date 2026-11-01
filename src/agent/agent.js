const mineflayer = require('mineflayer')
const { pathfinder, Movements } = require('mineflayer-pathfinder')
const mcDataLoader = require('minecraft-data')
const { velocityCompatibility } = require('./velocity_compat')
const { spectatorCompatibility } = require('./spectator_compat')
const { raycastCompatibility } = require('./raycast_compat')
const { ActionExecutor, cancelled } = require('./action_executor')
const { craftTimeoutMs } = require('../commands/craft_timing')
const { clearUse, hasUse } = require('../commands/held_use')
const { clearSneakHold } = require('./sneak_state')
const itemUseCompatibleActions = new Set(['right', 'sneak', 'wait', 'move', 'jump', 'look', 'goto', 'follow'])
const { effectCommands, validateArguments, normalizeFailure, exceptionFailure } = require('../commands/command_contract')

class SteveXAgent {
  /**
   * @param {object} config - agent 配置对象
   * @param {string} name - agent 名称
   * @param {object} [commands] - 共享命令表 { name -> handler }，由 AgentManager 传入
   */
  constructor(config, name = 'steveX', commands = {}, options = {}) {
    this.config = config
    this.name = name
    this.eventBus = options.eventBus || null
    this.bot = null
    this.movements = null
    this.commands = commands
    this.connected = false
    this.connecting = false
    this.queryNames = options.queryNames || new Set()
    this.executor = new ActionExecutor(() => this.stopPhysicalActions())
    const { CommandRuntime } = require('./command_runtime')
    this.commandRuntime = new CommandRuntime((input, options) => this._performCommand(input, options))
    this.renderService = options.renderService || null
    this.observationRevision = 0
    this.sessionId = require('node:crypto').randomUUID()
    const { InteractionMemory } = require('./interaction_memory')
    this.interactionMemory = new InteractionMemory({ agentName: name,
      agentId: config.renderer?.agentId || name, sessionId: this.sessionId,
      memoryNamespace: options.memoryNamespace || `session-${this.sessionId}`,
      server: { host: config.minecraft?.host, port: config.minecraft?.port || 25565 } })

    // Current action shown on the web console.
    // It will be updated when a command is running.
    this.currentAction = 'Idle'
  }

  start() {
    const mc = this.config.minecraft

    this.connecting = true
    this.currentAction = 'Connecting'

    this.bot = mineflayer.createBot({
      host: mc.host,
      port: mc.port,
      username: mc.username,
      auth: mc.auth,
      version: mc.version
    })

    require('./block_tags').attachBlockTags(this.bot)
    require('./chat_context').attachChatContext(this.bot)
    this.bot.loadPlugin(pathfinder)
    // Load after Mineflayer's built-ins so this corrects their legacy scaling.
    this.bot.loadPlugin(velocityCompatibility)
    this.bot.loadPlugin(spectatorCompatibility)
    this.bot.loadPlugin(raycastCompatibility)
    this.bot.loadPlugin(require('./hud_effects').hudEffectsPlugin)
    this.bot.loadPlugin(require('./player_tracking').playerTrackingPlugin)
    this.registerEvents()
  }

  registerEvents() {
    this.bot.once('spawn', () => {
      require('../commands/inventory_sync').installInventorySync(this.bot)
      require('../commands/workstations').attach(this.bot)
      this.interactionMemory.attach(this.bot)
      this.connected = true
      this.connecting = false
      this.currentAction = 'Idle'

      console.log(`[info](${this.name}) Bot spawned `)

      const mcData = mcDataLoader(this.bot.version)
      this.movements = new Movements(this.bot, mcData)
      this.bot.pathfinder.setMovements(this.movements)
      this.eventBus?.emit('agent:update',{name:this.name,sessionId:this.sessionId,timestamp:Date.now()})
    })

    // 统一断连处理：end/kicked 都标记为离线
    const onDisconnect = (reason) => {
      this.interactionMemory.close()
      this.brainLoop?.stop()
      this.commandRuntime.close()
      this.executor.close()
      this.stopPhysicalActions()
      this.connecting = false
      this.connected = false
      this.currentAction = 'Offline'
      this.eventBus?.emit('agent:update',{name:this.name,sessionId:this.sessionId,timestamp:Date.now()})

      if (reason) {
        console.error(`[error](${this.name}) Bot disconnected`, reason)
      }
    }

    this.bot.on('end', () => onDisconnect())

    this.bot.on('kicked', (reason) => {
      onDisconnect(reason)
    })

    this.bot.on('error', (error) => {
      this.currentAction = 'Error'
      console.error(`[error](${this.name}) Bot error`, error)
    })
  }

  /**
   * Execute a mineflayer command for this agent.
   * Commands are registered via registerCommand().
   * @param {string} input - raw command string
   * @returns {Promise<{ok:boolean,output?:string,error?:string}>}
   */
  async executeCommand(input, options = {}) {
    let handle
    try { handle = this.submitCommand(input, options) }
    catch (error) {
      const name = typeof input === 'string' ? input.trim().split(/\s+/)[0].toLowerCase() : ''
      return exceptionFailure(name, error, options.signal)
    }
    return handle.result
  }

  submitCommand(input, options = {}) {
    return this.commandRuntime.submit(input, options)
  }

  getCommandStatus(callId) { return this.commandRuntime.get(callId) }
  cancelCommand(callId, reason) { return this.commandRuntime.cancel(callId, reason) }

  async _performCommand(input, options = {}) {
    const startedAt = performance.now()
    const parts=typeof input==='string'?input.trim().split(/\s+/):[]
    const trace={name:this.name,sessionId:this.sessionId,callId:options.callId,
      apiName:parts[0]?.toLowerCase() || '',arguments:{raw:typeof input==='string'?input:'[non-string input]',positional:parts.slice(1)},
      source:options.source || 'manual',llmRequestId:options.llmRequestId || null,contextId:options.contextId || null,
      taskId:options.taskId || null,nodeId:options.nodeId || null,parentCallId:options.parentCallId || null}
    const emit=(phase,fields={})=>{try{this.eventBus?.emit('agent:api:'+phase,{...trace,...fields,timestamp:Date.now()})}catch{}}
    let measured,result,error,running=false,operationStarted
    emit('start')
    try {
      result=await this._executeCommand(input, { ...options, onTiming: timing => { measured = timing },
        onCallStarted:()=>{if(!running){running=true;operationStarted=performance.now();options.onRuntimeStarted?.();emit('running')}} })
      return result
    } catch(cause) {
      error=cause
      throw cause
    } finally {
      const commandMs = performance.now() - startedAt
      emit('done',{result,error:error?.message,cancelled:!!options.signal?.aborted || error?.name==='AbortError' || !!result?.cancelled,
        timings:{queueMs:measured?.queueMs ?? 0,executionMs:measured?.executionMs ?? (running?performance.now()-operationStarted:0),commandMs}})
      try { options.onTiming?.({ queueMs: measured?.queueMs ?? 0, executionMs: measured?.executionMs ?? commandMs, commandMs }) } catch {}
    }
  }

  async _executeCommand(input, options = {}) {
    const requested = typeof input === 'string' ? input.trim().split(/\s+/)[0].toLowerCase() : ''
    if (!this.bot || !this.connected) {
      return normalizeFailure(requested, {
        ok: false,
        errorCode: 'NOT_CONNECTED',
        error: 'Bot not started'
      })
    }

    const trimmed = typeof input === 'string' ? input.trim() : ''

    if (!trimmed) {
      return {
        ok: false,
        error: 'Empty command'
      }
    }

    const parts = trimmed.split(/\s+/)
    const command = parts[0].toLowerCase()
    const args = parts.slice(1)

    const handler = Object.hasOwn(this.commands, command) ? this.commands[command] : null

    if (!handler) {
      const available = Object.keys(this.commands).sort().join(', ')

      return {
        ok: false,
        error: `Unknown command: ${command}. Available: ${available}`
      }
    }

    const invalid = validateArguments(command, args)
    if (invalid) return invalid
    if(command==='right' && args[0]?.toLowerCase()==='click' && hasUse(this.bot))return normalizeFailure(command,{ok:false,errorCode:'ITEM_ALREADY_IN_USE',error:'right up the active held-item request before right click',changes:[]})

    let operationOutcome
    try {
      options.signal?.throwIfAborted()
      if (this.queryNames.has(command)) {
        options.onCallStarted?.()
        return normalizeFailure(command, await handler.call(this, this.bot, args, this, options))
      }
      if (options.source !== 'brain') this.brainLoop?.stop()
      if (command === 'dig' && args[0]?.toLowerCase() === 'stop') {
        options.onCallStarted?.()
        return normalizeFailure(command, await handler.call(this, this.bot, args, this, options))
      }
      if (command === 'stop') {
        options.onCallStarted?.()
        this.brainLoop?.stop()
        this.executor.cancelAll('Stop requested')
        this.stopPhysicalActions()
        return { ok: true, output: 'Actions and autonomous brain stopped' }
      }
      return await this.executor.run(command, async signal => {
        options.onCallStarted?.()
        if (!this.connected) throw new Error('Agent is offline')
        if (command !== 'swing') this.stopPhysicalActions({ preserveItemUse: itemUseCompatibleActions.has(command), preserveSneak:true })
        this.currentAction = command
        try {
          const result = await handler.call(this, this.bot, args, this, { ...options, signal })
          if (effectCommands.has(command)) operationOutcome = result
          if (result?.status === 'RUNNING') this.backgroundSource = options.source || 'manual'
          return normalizeFailure(command, result)
        }
        finally {
          if (signal.aborted) this.stopPhysicalActions()
          this.currentAction = this.connected ? (this.bot?._followCleanup ? 'follow' : 'Idle') : 'Offline'
        }
      }, { ...options, timeoutMs: options.timeoutMs ?? this.config.runtime?.actionTimeoutMs ?? (command === 'craft' ? craftTimeoutMs(args) : 30000) })
    } catch (err) {
      if (err.name !== 'AbortError') console.error(`[error](${this.name}) Command error`, err)

      const operations = require('../commands/operation_result')
      if (effectCommands.has(command)) {
        const failure = exceptionFailure(command,err,options.signal)
        let output = operationOutcome?.output || failure.output
        const oldPrefix = `[${operationOutcome?.errorCode}] `
        if (output?.startsWith(oldPrefix)) output = output.slice(oldPrefix.length)
        return normalizeFailure(command, operations.result(command, { ...operationOutcome, ...failure, output }))
      }

      return exceptionFailure(command,err,options.signal)
    }
  }

  stopPhysicalActions({ preserveItemUse = false, preserveSneak = false } = {}) {
    const bot = this.bot
    if (!bot) return
    if (!preserveSneak) clearSneakHold(bot)
    if (bot._followInterval) { clearInterval(bot._followInterval); bot._followInterval = null }
    bot._followCleanup?.()
    this.backgroundSource = null
    if (this.connected && !this.executor.active) this.currentAction = 'Idle'
    for (const stop of [() => bot.pathfinder?.setGoal(null), () => { const sneak=preserveSneak&&bot.getControlState?.('sneak');bot.clearControlStates?.();if(sneak)bot.setControlState('sneak',true) },
      () => { if (bot.vehicle) require('./vehicle_input').steerVehicle(bot) },
      () => bot.stopDigging?.(), ...(!preserveItemUse ? [() => { try { bot.deactivateItem?.() } finally { clearUse(bot) } }] : [])]) {
      try { stop()?.catch?.(() => {}) } catch (_) {}
    }
  }

  stopBrainActions() {
    if (this.backgroundSource === 'brain') this.stopPhysicalActions()
  }

  async captureObservation(options = {}) {
    if (!this.renderService) return { ok: false, error: 'Shared renderer is disabled' }
    try {
      const frame = await this.executor.run('capture', async signal => {
        if (!this.isOnline()) throw new Error('Agent is offline')
        this.stopPhysicalActions({ preserveItemUse: true, preserveSneak:true })
        this.currentAction = 'capture'
        try { return await this.renderService.capture(this, signal, options.perspective) }
        finally { this.currentAction = this.connected ? 'Idle' : 'Offline' }
      }, { ...options, timeoutMs: this.renderService.timeoutMs || 60000 })
      return { ok: true, observation: { agentId: frame.agentId, agentName: this.name, sessionId: this.sessionId, revision: ++this.observationRevision,
        observedAt: new Date(frame.timestamp).toISOString(), sources: frame.previewOnly ? ['rgb', 'depth'] : ['rgb', 'depth', 'client-nbt'], frame } }
    } catch (error) { return { ok: false, cancelled: error.name === 'AbortError', error: error.message } }
  }

  /** Whether the bot is connected and spawned. */
  startBrain(opts = {}, bus = null) {
    if (this.brainLoop) return this.brainLoop.start()
    const { Brain } = require('./brain')
    const llmCfg = this.config?.llm || {}
    this.brainLoop = new Brain(this, llmCfg, bus || { emit: () => {} }, opts)
    return this.brainLoop.start()
  }

  isOnline() {
    return this.bot && this.connected
  }

  /** Whether the bot is in the connection handshake. */
  isConnecting() {
    return this.bot && this.connecting
  }

  /** The in-game username, falling back to config. */
  getUsername() {
    return this.bot ? this.bot.username : this.config.minecraft.username
  }

  /** The current action shown in web console. */
  getCurrentAction() {
    return this.currentAction
  }

  /** Gracefully disconnect the bot from the server. */
  shutdown() {
    this.interactionMemory.close()
    if (this.brainLoop) {
      this.brainLoop.stop()
      this.brainLoop = null
    }
    this.executor.close()
    this.commandRuntime.close()
    this.stopPhysicalActions()

    if (this.bot) {
      if (this.bot._followInterval) {
        clearInterval(this.bot._followInterval)
        this.bot._followInterval = null
      }
      this.currentAction = 'Disconnecting'
      this.bot.end()
    }

    this.connected = false
    this.connecting = false
    this.currentAction = 'Offline'
  }
}

module.exports = {
  SteveXAgent
}
