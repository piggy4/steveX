/**
 * swing [hand] — swing arm animation
 * hand: right (default) or left
 */
module.exports = {
  name: 'swing',
  description: 'Play arm swing animation',
  usage: 'swing [hand]',
  async handler(bot, args) {
    const hand = args[0] === 'left' ? 'left' : 'right'
    bot.swingArm(hand)
    return { ok: true, output: `Swung ${hand} arm` }
  }
}
