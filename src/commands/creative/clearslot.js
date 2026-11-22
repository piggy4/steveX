/**
 * clearslot <slot> — clear a specific inventory slot in creative mode
 */
module.exports = {
  name: 'clearslot',
  description: '[Creative] Clear a specific inventory slot',
  usage: 'clearslot <playerRef>',
  async handler(bot, args) {
    if (args.length !== 1) return { ok: false, error: 'Usage: clearslot <playerRef>' }
    const slot = require('../slot_scope').playerIndex(args[0])
    if (slot <= 0) return { ok: false, errorCode:'INVALID_SLOT_REFERENCE', error: 'Expected a writable player slot reference' }
    await bot.creative.clearSlot(slot)
    return { ok: true, ref:args[0], output: `Cleared ${args[0]}` }
  }
}
