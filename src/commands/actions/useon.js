/**
 * useon <entityName> — use held item on an entity (saddle, shear, etc.)
 */
module.exports = {
  name: 'useon',
  description: 'Use the held item on a matching entity',
  usage: 'useon <entityName>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: useon <entityName>' }
    const name = args.join(' ').toLowerCase()
    const entity = Object.values(bot.entities).find(e =>
      e.name && e.name.toLowerCase().includes(name)
    )
    if (!entity) return { ok: false, error: `No entity matching "${name}" nearby` }
    await bot.useOn(entity)
    return { ok: true, output: `Used item on ${entity.name || entity.type}` }
  }
}
