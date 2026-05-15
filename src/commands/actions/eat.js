/**
 * eat — eat the held food
 */
module.exports = {
  name: 'eat',
  description: 'Eat the currently held food item',
  usage: 'eat',
  async handler(bot) {
    if (!bot.heldItem) return { ok: false, error: 'No item in hand' }
    await bot.consume()
    return { ok: true, output: `Ate ${bot.heldItem.name}` }
  }
}
