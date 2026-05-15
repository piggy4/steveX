/**
 * time — show game time information
 */
module.exports = {
  name: 'time',
  description: 'Show game time, day and moon phase',
  usage: 'time',
  async handler(bot) {
    const tod = bot.time.timeOfDay ?? '?'
    const day = bot.time.day ?? '?'
    const isDay = bot.time.isDay ? 'day' : 'night'
    const moon = ['full', 'waning gibbous', 'third quarter', 'waning crescent', 'new', 'waxing crescent', 'first quarter', 'waxing gibbous'][bot.time.moonPhase] ?? '?'
    return { ok: true, output: `Time: ${tod} ticks | Day: ${day} | ${isDay} | Moon: ${moon}` }
  }
}
