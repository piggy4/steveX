/**
 * dispenser — open the dispenser/dropper block in sight
 */
module.exports = {
  name: 'dispenser',
  description: 'Open the dispenser or dropper block in sight',
  usage: 'dispenser',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    const container = await bot.openDispenser(block)
    return { ok: true, output: `Opened ${block.name}` }
  }
}
