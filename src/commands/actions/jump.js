/**
 * jump — make the bot jump
 */
module.exports = {
  name: 'jump',
  description: 'Make the bot jump',
  usage: 'jump',
  async handler(bot) {
    bot.setControlState('jump', true)
    await new Promise(resolve => setTimeout(resolve, 200))
    bot.setControlState('jump', false)
    return { ok: true, output: 'Jumped' }
  }
}
