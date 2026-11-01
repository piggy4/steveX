/**
 * findplayer <name> — find a player by name and show their info
 */
module.exports = {
  name: 'findplayer',
  description: 'Find players by name; coordinates and current dimension are available only for valid entities still tracked locally. Untracked tab-list names may be in another dimension or out of range; never navigate using an old position',
  usage: 'findplayer <name>',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: findplayer <name>' }
    const search = args.join(' ').toLowerCase()

    const matches = Object.values(bot.players || {}).filter(p =>
      typeof p.username === 'string' && p.username.toLowerCase().includes(search)
    ).sort((a, b) => Number(b.username.toLowerCase() === search) - Number(a.username.toLowerCase() === search) || a.username.localeCompare(b.username))

    if (matches.length === 0) return { ok: true, output: `No players matching "${search}"`, players: [] }

    const players = matches.map(p => {
      const entity = p.entity
      const pos = entity?.position
      // Tab-list entries can outlive entity tracking, including across respawns/dimensions.
      const loaded = entity?.isValid !== false && (!bot.entities || bot.entities[entity?.id] === entity)
        && pos && [pos.x, pos.y, pos.z].every(Number.isFinite)
      return { name: p.username, loaded: !!loaded,
        ...(loaded ? { position: { x: pos.x, y: pos.y, z: pos.z },
          ...(typeof bot.game?.dimension === 'string' ? { dimension: bot.game.dimension } : {}) } : {}),
        ...(Number.isFinite(p.ping) ? { ping: p.ping } : {}) }
    })

    const list = players.map(p => {
      const format = value => (Math.sign(value) * Math.round(Math.abs(value) * 10) / 10 || 0).toFixed(1)
      const pos = p.loaded ? `at ${[p.position.x, p.position.y, p.position.z].map(format).join(' ')}` : '(position unavailable: not tracked)'
      return `${p.name}${p.ping === undefined ? '' : ` — ping: ${p.ping}ms`}, ${pos}`
    }).join('\n')

    return { ok: true, output: `Players matching "${search}":\n${list}`, players }
  }
}

module.exports.handler = require('../command_contract').withContract(module.exports.name, module.exports.handler)
