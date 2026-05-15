/**
 * wait <ticks> — wait for a number of game ticks
 */
module.exports = {
  name: 'wait',
  description: 'Wait for a number of game ticks',
  usage: 'wait <ticks>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: wait <ticks>' }
    const ticks = parseInt(args[0], 10)
    if (isNaN(ticks) || ticks < 1) return { ok: false, error: 'Invalid tick count' }
    await bot.waitForTicks(ticks)
    return { ok: true, output: `Waited ${ticks} ticks` }
  }
}
