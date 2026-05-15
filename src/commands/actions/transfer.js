/**
 * transfer <itemName> <count> — transfer items between inventory and open container
 */
module.exports = {
  name: 'transfer',
  description: 'Transfer items between inventory and open container',
  usage: 'transfer <itemName> [count]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: transfer <itemName> [count]' }
    const itemName = args[0].toLowerCase()
    const count = args[1] ? parseInt(args[1], 10) : 1
    if (isNaN(count) || count < 1) return { ok: false, error: 'Invalid count' }

    const item = bot.inventory.items().find(i => i.name.includes(itemName))
    if (!item) return { ok: false, error: `No "${itemName}" in inventory` }

    const window = bot.currentWindow
    if (!window) return { ok: false, error: 'No container window open' }

    // Copy item from inventory window (slots 9-35 = main inventory, 36-44 = hotbar) into container window
    // Container slots are typically 0-53 depending on type
    await bot.transfer({
      window,
      itemType: item.type,
      metadata: item.metadata,
      count,
      sourceStart: 9,
      sourceEnd: 44,
      destStart: 0,
      destEnd: window.inventoryStart
    })

    return { ok: true, output: `Transferred ${count} x ${item.name} to container` }
  }
}
