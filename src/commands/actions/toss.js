/**
 * toss <itemName> [count] — alias for drop
 */
module.exports = {
  name: 'toss',
  description: 'Toss/drop items from inventory',
  usage: 'toss <itemName> [count]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: toss <itemName> [count]' }
    const itemName = args[0].toLowerCase()
    const count = args[1] ? parseInt(args[1], 10) : null
    const item = bot.inventory.items().find(i => i.name.includes(itemName))
    if (!item) return { ok: false, error: `No item matching "${itemName}" in inventory` }
    if (count !== null) {
      await bot.toss(item.type, item.metadata, count)
      return { ok: true, output: `Tossed ${count} x ${item.name}` }
    }
    await bot.tossStack(item)
    return { ok: true, output: `Tossed ${item.name} x${item.count}` }
  }
}
