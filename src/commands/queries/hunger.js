/**
 * hunger — show food level and saturation
 */
module.exports = {
  name: 'hunger',
  description: 'Show food level and saturation',
  usage: 'hunger',
  async handler(bot) {
    return { ok: true, output: `Hunger: ${bot.food ?? '?'}/20 | Saturation: ${bot.foodSaturation?.toFixed(1) ?? '?'}` }
  }
}
