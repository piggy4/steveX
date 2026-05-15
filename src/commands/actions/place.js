/**
 * place — place the held block
 */
module.exports = {
  name: 'place',
  description: 'Place the held block against the block in sight',
  usage: 'place',
  async handler(bot) {
    const target = bot.blockAtCursor(4.5)
    if (!target) return { ok: false, error: 'No block in sight to place against' }
    const face = { x: 0, y: 1, z: 0 }
    await bot.placeBlock(target, face)
    return { ok: true, output: `Placed block against ${target.name}` }
  }
}
