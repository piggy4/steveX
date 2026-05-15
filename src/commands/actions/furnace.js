/**
 * furnace — open the furnace block in sight
 */
module.exports = {
  name: 'furnace',
  description: 'Open the furnace block in sight',
  usage: 'furnace',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    const furnace = await bot.openFurnace(block)
    // Store reference for sub-commands like takeInput, putFuel etc.
    bot._furnace = furnace
    return { ok: true, output: `Opened furnace: ${block.name}` }
  }
}
