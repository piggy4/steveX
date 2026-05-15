/**
 * look <yaw> <pitch> — look at angle
 */
module.exports = {
  name: 'look',
  description: 'Set view angle',
  usage: 'look <yaw> <pitch>',
  async handler(bot, args) {
    if (args.length < 2) return { ok: false, error: 'Usage: look <yaw> <pitch>' }
    const [yaw, pitch] = args.map(Number)
    if (isNaN(yaw) || isNaN(pitch)) return { ok: false, error: 'Invalid yaw/pitch' }
    await bot.look(yaw, pitch, true)
    return { ok: true, output: `Looked at yaw=${yaw} pitch=${pitch}` }
  }
}
