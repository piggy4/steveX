/**
 * weather — show weather state
 */
module.exports = {
  name: 'weather',
  description: 'Show current weather',
  usage: 'weather',
  async handler(bot) {
    const rain = bot.isRaining ? 'raining' : 'clear'
    return { ok: true, output: `Weather: ${rain}` }
  }
}
