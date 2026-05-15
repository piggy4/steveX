/**
 * dig — dig the block in front
 */
module.exports = {
  name: 'dig',
  description: 'Dig the block in sight',
  usage: 'dig',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    await bot.dig(block)
    return { ok: true, output: `Dug ${block.name}` }
  }
}
