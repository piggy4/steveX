/**
 * attack — attack the entity in front
 */
module.exports = {
  name: 'attack',
  description: 'Attack the nearest entity',
  usage: 'attack',
  async handler(bot) {
    const entity = bot.nearestEntity()
    if (!entity) return { ok: false, error: 'No entity nearby' }
    await bot.attack(entity)
    return { ok: true, output: `Attacked ${entity.name || entity.type}` }
  }
}
