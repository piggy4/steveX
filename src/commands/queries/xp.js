/**
 * xp — show experience
 */
module.exports = {
  name: 'xp',
  description: 'Show experience level, points and progress',
  usage: 'xp',
  async handler(bot) {
    return { ok: true, output: `Level: ${bot.experience.level ?? '?'} | Points: ${bot.experience.points ?? '?'} | Progress: ${(bot.experience.progress * 100).toFixed(0) ?? '?'}%` }
  }
}
