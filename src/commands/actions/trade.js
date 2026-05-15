/**
 * trade <index> [times] — trade with an opened villager
 */
module.exports = {
  name: 'trade',
  description: 'Trade with an opened villager',
  usage: 'trade <index> [times]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: trade <index> [times]' }
    const index = parseInt(args[0], 10)
    const times = args[1] ? parseInt(args[1], 10) : 1
    if (isNaN(index) || index < 0) return { ok: false, error: 'Invalid trade index' }
    // First, try to find an open villager window
    const villager = bot.currentWindow
    if (!villager || !villager.trades) return { ok: false, error: 'No villager window open. Use "open" first on a villager.' }
    await bot.trade(villager, index, times)
    return { ok: true, output: `Traded ${times} time(s) with trade index ${index}` }
  }
}
