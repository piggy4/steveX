/**
 * openentity — open an entity with an inventory (villager, minecart, llama, etc.)
 */
module.exports = {
  name: 'openentity',
  description: 'Open an entity with an inventory in sight',
  usage: 'openentity',
  async handler(bot) {
    const entity = bot.entityAtCursor(4.5)
    if (!entity) return { ok: false, error: 'No entity in sight to open' }
    const window = await bot.openEntity(entity)
    return { ok: true, output: `Opened entity: ${entity.name || entity.type}` }
  }
}
