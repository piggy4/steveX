/**
 * click <slot> [button] — click a slot in the current window
 * button: 0 = left click, 1 = right click
 */
module.exports = {
  name: 'click',
  description: 'Click a slot in the current window',
  usage: 'click <slot> [button]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: click <slot> [button]' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot) || slot < 0) return { ok: false, error: 'Invalid slot number' }
    const button = args[1] ? parseInt(args[1], 10) : 0
    if (button !== 0 && button !== 1) return { ok: false, error: 'Button must be 0 (left) or 1 (right)' }
    await bot.clickWindow(slot, button, 0)
    return { ok: true, output: `Clicked slot ${slot} with ${button === 0 ? 'left' : 'right'} button` }
  }
}
