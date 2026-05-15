/**
 * inventory — list inventory
 */
module.exports = {
  name: 'inventory',
  description: 'List inventory contents',
  usage: 'inventory',
  async handler(bot) {
    const items = bot.inventory.items()
    if (items.length === 0) return { ok: true, output: 'Inventory is empty' }
    const list = items.map(i => `${i.name} x${i.count}`).join(', ')
    return { ok: true, output: `Inventory: ${list}` }
  }
}
