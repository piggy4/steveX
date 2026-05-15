/**
 * putaway <slot> — put item from a slot into empty inventory space
 */
module.exports = {
  name: 'putaway',
  description: 'Put an item from a slot into empty inventory space',
  usage: 'putaway <slot>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: putaway <slot>' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot) || slot < 0) return { ok: false, error: 'Invalid slot number' }
    await bot.putAway(slot)
    return { ok: true, output: `Put away item from slot ${slot}` }
  }
}
