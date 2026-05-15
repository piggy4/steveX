/**
 * placeentity — place an entity (painting, armor stand, etc.)
 * Requires the entity item in hand. Place against the block in sight.
 */
module.exports = {
  name: 'placeentity',
  description: 'Place an entity (painting, armor stand) against the block in sight',
  usage: 'placeentity',
  async handler(bot) {
    const target = bot.blockAtCursor(4.5)
    if (!target) return { ok: false, error: 'No block in sight to place against' }
    const face = { x: 0, y: 1, z: 0 }
    const entity = await bot.placeEntity(target, face)
    return { ok: true, output: `Placed entity: ${entity.name || entity.type}` }
  }
}
