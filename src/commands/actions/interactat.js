/**
 * interactat <entityName> — interact with a matching entity at its position
 * (useful for armor stands and similar entities)
 */
module.exports = {
  name: 'interactat',
  description: 'Interact with a matching entity at its position',
  usage: 'interactat <entityName>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: interactat <entityName>' }
    const name = args.join(' ').toLowerCase()
    const entity = Object.values(bot.entities).find(e =>
      e.name && e.name.toLowerCase().includes(name)
    )
    if (!entity) return { ok: false, error: `No entity matching "${name}" nearby` }
    await bot.activateEntityAt(entity, entity.position)
    return { ok: true, output: `Interacted with ${entity.name || entity.type} at position` }
  }
}
