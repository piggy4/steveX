/**
 * respawn — manually respawn after death
 */
module.exports = {
  name: 'respawn',
  description: 'Manually respawn after death',
  usage: 'respawn',
  async handler(bot) {
    await bot.respawn()
    return { ok: true, output: 'Respawned' }
  }
}
