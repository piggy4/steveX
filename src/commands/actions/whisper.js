/**
 * whisper <player> <message> — whisper to a player
 */
module.exports = {
  name: 'whisper',
  description: 'Send a whisper to a player',
  usage: 'whisper <player> <message>',
  async handler(bot, args) {
    if (args.length < 2) return { ok: false, error: 'Usage: whisper <player> <message>' }
    const player = args[0]
    const message = args.slice(1).join(' ')
    bot.whisper(player, message)
    return { ok: true, output: `Whispered to ${player}: ${message}` }
  }
}
