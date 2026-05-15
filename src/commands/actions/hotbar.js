/**
 * hotbar <slot> — select a quick bar slot (0-8)
 */
module.exports = {
  name: 'hotbar',
  description: 'Select a quick bar slot (0-8)',
  usage: 'hotbar <slot>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: hotbar <0-8>' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot) || slot < 0 || slot > 8) return { ok: false, error: 'Slot must be between 0 and 8' }
    bot.setQuickBarSlot(slot)
    return { ok: true, output: `Selected quick bar slot ${slot}` }
  }
}
