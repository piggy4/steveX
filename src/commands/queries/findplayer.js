/**
 * findplayer <name> — find a player by name and show their info
 */
module.exports = {
  name: 'findplayer',
  description: 'Find players by name and show their info',
  usage: 'findplayer <name>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: findplayer <name>' }
    const search = args.join(' ').toLowerCase()

    const matches = Object.values(bot.players).filter(p =>
      p.username.toLowerCase().includes(search)
    )

    if (matches.length === 0) return { ok: true, output: `No players matching "${search}"` }

    const list = matches.map(p => {
      const pos = p.entity ? `at ${Math.floor(p.entity.position.x)} ${Math.floor(p.entity.position.y)} ${Math.floor(p.entity.position.z)}` : '(too far)'
      return `${p.username} — ping: ${p.ping}ms, ${pos}`
    }).join('\n')

    return { ok: true, output: `Players matching "${search}":\n${list}` }
  }
}
