/**
 * digtime — show how long it would take to dig the block in sight
 */
module.exports = {
  name: 'digtime',
  description: 'Show dig time for the block in sight',
  usage: 'digtime',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    const ms = bot.digTime(block)
    return { ok: true, output: `Digging ${block.name} would take ${ms}ms (${(ms / 1000).toFixed(1)}s)` }
  }
}
