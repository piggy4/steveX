const trackers = new WeakMap()

function playerTrackingPlugin(bot) {
  if (trackers.has(bot) || !bot.on || !bot._client?.on) return
  // Mineflayer keeps entity objects across respawn; membership alone is not
  // evidence that a player has been observed in the new world.
  let observed = new WeakSet(Object.values(bot.entities || {}).filter(entity => entity && typeof entity === 'object'))
  const tracker = { has: entity => observed.has(entity) }
  const spawn = entity => { if (entity && typeof entity === 'object') observed.add(entity) }
  const reset = () => { observed = new WeakSet() }
  const self = () => spawn(bot.entity)
  const end = () => {
    bot._client.removeListener('respawn', reset)
    bot._client.removeListener('login', reset)
    bot.removeListener('entitySpawn', spawn)
    bot.removeListener('spawn', self)
    bot.removeListener('forcedMove', self)
    trackers.delete(bot)
  }
  trackers.set(bot, tracker)
  bot._client.on('respawn', reset)
  bot._client.on('login', reset)
  bot.on('entitySpawn', spawn)
  bot.on('spawn', self)
  bot.on('forcedMove', self)
  bot.once('end', end)
}

function isTrackedPlayerEntity(bot, entity) {
  playerTrackingPlugin(bot)
  return !!entity && entity.isValid !== false && (!bot.entities || bot.entities[entity.id] === entity)
    && (!trackers.has(bot) || trackers.get(bot).has(entity))
}

module.exports = { playerTrackingPlugin, isTrackedPlayerEntity }
