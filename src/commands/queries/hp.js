/**
 * hp — show health
 */
module.exports = {
  name: 'hp',
  description: 'Show current health',
  usage: 'hp',
  async handler(bot) {
    return { ok: true, output: `HP: ${bot.health?.toFixed(1) ?? '?'}/20` }
  }
}
