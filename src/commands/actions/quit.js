/**
 * quit [reason] — gracefully disconnect from the server
 */
module.exports = {
  name: 'quit',
  description: 'Disconnect from the server',
  usage: 'quit [reason]',
  async handler(bot, args) {
    const reason = args.join(' ') || 'disconnect.quitting'
    bot.quit(reason)
    return { ok: true, output: 'Disconnected' }
  }
}
