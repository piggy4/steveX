/**
 * setinventory <slot> [itemName] [count] [metadata] — set inventory slot in creative mode
 * If itemName is omitted, clears the slot.
 */
module.exports = {
  name: 'setinventory',
  description: '[Creative] Set or clear an inventory slot',
  usage: 'setinventory <slot> [itemName] [count] [metadata]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: setinventory <slot> [itemName] [count] [metadata]' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot) || slot < 0) return { ok: false, error: 'Invalid slot number' }

    if (args.length === 1) {
      await bot.creative.clearSlot(slot)
      return { ok: true, output: `Cleared slot ${slot}` }
    }

    const mcData = require('minecraft-data')(bot.version)
    const itemName = args[1].toLowerCase()
    const count = args[2] ? parseInt(args[2], 10) : 1
    const metadata = args[3] ? parseInt(args[3], 10) : 0

    const itemData = Object.values(mcData.items).find(i => i.name === itemName || i.displayName?.toLowerCase() === itemName)
    if (!itemData) return { ok: false, error: `Unknown item: "${itemName}"` }

    const Item = require('prismarine-item')(bot.version)
    const item = new Item(itemData.id, count, metadata)

    await bot.creative.setInventorySlot(slot, item)
    return { ok: true, output: `Set slot ${slot} to ${itemName} x${count}` }
  }
}
