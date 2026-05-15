/**
 * anvil — open the anvil block in sight
 */
module.exports = {
  name: 'anvil',
  description: 'Open the anvil block in sight',
  usage: 'anvil',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    const anvil = await bot.openAnvil(block)
    bot._anvil = anvil
    return { ok: true, output: 'Opened anvil' }
  }
}
