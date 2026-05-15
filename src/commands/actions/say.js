/**
 * say <message> — send chat message
 */
module.exports = {
  name: 'say',
  description: 'Send a chat message',
  usage: 'say <message>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: say <message>' }
    const message = args.join(' ')
    await bot.chat(message)
    return { ok: true, output: `Sent: ${message}` }
  }
}
