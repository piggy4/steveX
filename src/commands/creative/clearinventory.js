/**
 * clearinventory — clear all inventory slots in creative mode
 */
module.exports = {
  name: 'clearinventory',
  description: '[Creative] Clear all inventory slots',
  usage: 'clearinventory',
  async handler(bot) {
    await bot.creative.clearInventory()
    return { ok: true, output: 'Cleared entire inventory' }
  }
}
