/**
 * gamemode — show current game mode
 */
module.exports = {
  name: 'gamemode',
  description: 'Show current game mode',
  usage: 'gamemode',
  async handler(bot) {
    const modes = { 0: 'survival', 1: 'creative', 2: 'adventure', 3: 'spectator' }
    const mode = modes[bot.game.gameMode] ?? '?'
    return { ok: true, output: `Game mode: ${mode}` }
  }
}
