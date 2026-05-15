/**
 * oxygen — show oxygen level
 */
module.exports = {
  name: 'oxygen',
  description: 'Show oxygen level',
  usage: 'oxygen',
  async handler(bot) {
    return { ok: true, output: `Oxygen: ${bot.oxygenLevel ?? '?'}/20` }
  }
}
