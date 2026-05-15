/**
 * clearslot <slot> — clear a specific inventory slot in creative mode
 */
module.exports = {
  name: 'clearslot',
  description: '[Creative] Clear a specific inventory slot',
  usage: 'clearslot <slot>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: clearslot <slot>' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot) || slot < 0) return { ok: false, error: 'Invalid slot number' }
    await bot.creative.clearSlot(slot)
    return { ok: true, output: `Cleared slot ${slot}` }
  }
}
