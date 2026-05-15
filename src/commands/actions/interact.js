/**
 * interact — interact with the block/entity in sight
 */
module.exports = {
  name: 'interact',
  description: 'Interact with the block or entity in sight',
  usage: 'interact',
  async handler(bot) {
    // Try entity first
    const entity = bot.entityAtCursor(4.5)
    if (entity) {
      await bot.activateEntity(entity)
      return { ok: true, output: `Interacted with ${entity.name || entity.type}` }
    }

    // Then block
    const block = bot.blockAtCursor(4.5)
    if (block) {
      await bot.activateBlock(block)
      return { ok: true, output: `Interacted with ${block.name}` }
    }

    return { ok: false, error: 'Nothing interactive in sight' }
  }
}
