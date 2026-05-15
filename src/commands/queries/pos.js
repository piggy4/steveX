/**
 * pos — show current position and dimension
 */
module.exports = {
  name: 'pos',
  description: 'Show current position and dimension',
  usage: 'pos',
  async handler(bot) {
    const pos = bot.entity.position
    const dim = bot.game.dimension || 'unknown'
    return { ok: true, output: `Position: ${Math.floor(pos.x)} ${Math.floor(pos.y)} ${Math.floor(pos.z)} | Dimension: ${dim}` }
  }
}
