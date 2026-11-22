const path = require('node:path')
const { createHash } = require('node:crypto')
const { details } = require('../../commands/item_details')
const { playerSlots,playerRefs,logicalSlot } = require('../../commands/slot_scope')

function inventorySnapshot(agent) {
  if (!agent.isOnline() || !agent.bot?.inventory) return { agentName: agent.name, online: false, sessionId: agent.sessionId ?? null }
  const bot = agent.bot
  const item = (value, ref) => {
    if (!value) return { ref, item: null }
    return { ref, item: { ...details(value, bot), displayName: value.displayName || value.name } }
  }
  const window = bot.currentWindow
  const player = playerSlots(bot)
  // An open window is authoritative for storage/hotbar slots; equipment remains player-local.
  const storage = Array.from({ length: 36 }, (_, index) => {
    const slot = index + 9
    const value = player[slot]
    return item(value, playerRefs[slot])
  })
  return {
    agentName: agent.name, sessionId: agent.sessionId ?? null, online: true,
    version: bot.version, readOnly: true, slotSpace: 'inventory',
    selectedRef: Number.isInteger(bot.quickBarSlot) ? playerRefs[36 + bot.quickBarSlot] : null,
    equipmentFreshness: window ? 'last-known' : 'player-inventory-cache',
    storage,
    equipment: ['head', 'torso', 'legs', 'feet', 'off-hand'].map(destination => {
      const slot = bot.getEquipmentDestSlot?.(destination)
      return { destination, ...item(Number.isInteger(slot) ? bot.inventory.slots?.[slot] : null, playerRefs[slot] ?? null) }
    }),
    cursor: item((window || bot.inventory).selectedItem, null).item,
    container: window ? {
      id: window.id, type: window.type, title: typeof window.title === 'string' ? window.title : window.type,
      slotSpace: 'window',
      slots: Array.from({ length: Math.max(0, Math.min(window.inventoryStart || 0, window.slots.length, 256)) },
        (_, slot) => item(window.slots[slot], logicalSlot(window,slot).ref)),
      ...(window.type==='minecraft:crafter_3x3'&&45 in window.slots?
        {preview:{...item(window.slots[45],'crafter:preview'),readOnly:true}}:{})
    } : null
  }
}

function registerInventoryRoutes(app, manager) {
  app.get('/api/agents/:name/inventory-preview', (req, res) => {
    res.set('Cache-Control', 'no-store')
    const agent = manager.agents.get(req.params.name)
    if (!agent) return res.status(404).json({ ok: false, error: 'Agent not started' })
    try {
      const body=JSON.stringify({ok:true,...inventorySnapshot(agent)})
      const etag='"'+createHash('sha256').update(body).digest('hex')+'"'
      res.set('ETag',etag)
      const matches=String(req.get('If-None-Match') || '').split(',').map(value=>value.trim().replace(/^W\//,''))
      if(matches.includes(etag)) return res.status(304).end()
      res.type('json').send(body)
    }
    catch (error) { res.status(503).json({ ok: false, error: 'Inventory temporarily unavailable' }) }
  })
  app.get('/api/item-textures/:version/:name', (req, res) => {
    const { version, name } = req.params
    if (!/^[0-9][0-9.a-z_-]{0,40}$/.test(version) || !/^[a-z0-9_]{1,100}$/.test(name)) return res.sendStatus(400)
    const root = path.resolve('runs/web-item-textures', version)
    res.sendFile(name + '.png', { root, dotfiles: 'deny' }, error => {
      if (error && !res.headersSent) res.sendStatus(404)
    })
  })
}

module.exports = { inventorySnapshot, registerInventoryRoutes }
