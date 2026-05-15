/**
 * equip <item name> — equip an item from inventory
 */
module.exports = {
  name: 'equip',
  description: 'Equip an item from inventory',
  usage: 'equip <item name>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: equip <item name>' }
    const itemName = args.join(' ')
    const item = bot.inventory.items().find(i => i.name.includes(itemName))
    if (!item) return { ok: false, error: `No item matching "${itemName}" in inventory` }
    await bot.equip(item, 'hand')
    return { ok: true, output: `Equipped ${item.name}` }
  }
}
