/**
 * move <direction> [durationMs] — set movement control state
 * direction: forward, back, left, right, sprint, sneak
 * durationMs: optional, auto-stop after ms (default: 1000)
 */
module.exports = {
  name: 'move',
  description: 'Set movement control state',
  usage: 'move <forward|back|left|right|sprint|sneak> [durationMs]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: move <forward|back|left|right|sprint|sneak> [durationMs]' }
    const control = args[0].toLowerCase()
    const valid = ['forward', 'back', 'left', 'right', 'sprint', 'sneak']
    if (!valid.includes(control)) return { ok: false, error: `Invalid direction. Valid: ${valid.join(', ')}` }
    const duration = args[1] ? parseInt(args[1], 10) : 1000

    bot.setControlState(control, true)

    if (duration > 0) {
      setTimeout(() => bot.setControlState(control, false), duration)
    }

    const sec = (duration / 1000).toFixed(1)
    return { ok: true, output: `Moving ${control} for ${sec}s` }
  }
}
