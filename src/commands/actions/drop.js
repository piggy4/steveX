/**
 * drop <itemName> [count] — drop items from inventory
 */
module.exports = {
  name: 'drop',
  description: 'Drop items from inventory',
  usage: 'drop <itemName> [count]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: drop <itemName> [count]' }
    const itemName = args[0].toLowerCase()
    const count = args[1] ? parseInt(args[1], 10) : null
    const item = bot.inventory.items().find(i => i.name.includes(itemName))
    if (!item) return { ok: false, error: `No item matching "${itemName}" in inventory` }
    if (count !== null) {
      await bot.toss(item.type, item.metadata, count)
      return { ok: true, output: `Dropped ${count} x ${item.name}` }
    }
    await bot.tossStack(item)
    return { ok: true, output: `Dropped ${item.name} x${item.count}` }
  }
}
