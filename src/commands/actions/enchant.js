/**
 * enchant — open the enchantment table in sight
 */
module.exports = {
  name: 'enchant',
  description: 'Open the enchantment table in sight',
  usage: 'enchant',
  async handler(bot) {
    const block = bot.blockAtCursor(4.5)
    if (!block) return { ok: false, error: 'No block in sight' }
    const table = await bot.openEnchantmentTable(block)
    bot._enchantTable = table
    return { ok: true, output: 'Opened enchantment table' }
  }
}
