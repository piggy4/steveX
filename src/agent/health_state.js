const maxHealthKeys = ['minecraft:max_health', 'max_health', 'minecraft:generic.max_health', 'generic.max_health', 'generic.maxHealth', 'minecraft:generic.maxHealth']

function healthState(bot) {
  const finite = value => typeof value === 'number' && Number.isFinite(value)
  const attributes = bot?.entity?.attributes
  const definition = bot?.registry?.attributesArray?.find(entry => maxHealthKeys.includes(entry.resource) || entry.name === 'maxHealth')
  const attribute = maxHealthKeys.map(key => attributes?.[key]).find(value => value !== undefined)
  let maxHealth = null
  if (attribute && finite(attribute.value)) {
    const modifiers = attribute.modifiers ?? []
    if (Array.isArray(modifiers) && modifiers.every(m => finite(m.amount) && [0, 1, 2].includes(m.operation))) {
      // Attribute operations: add values, multiply the adjusted base, then multiply total.
      const base = attribute.value + modifiers.filter(m => m.operation === 0).reduce((sum, m) => sum + m.amount, 0)
      const scaled = base * (1 + modifiers.filter(m => m.operation === 1).reduce((sum, m) => sum + m.amount, 0))
      const total = modifiers.filter(m => m.operation === 2).reduce((value, m) => value * (1 + m.amount), scaled)
      if (finite(total)) {
        const bounded = Math.min(finite(definition?.max) ? definition.max : Infinity,
          Math.max(finite(definition?.min) ? definition.min : -Infinity, total))
        if (bounded > 0) maxHealth = bounded
      }
    }
  } else if (!attribute && finite(bot?.maxHealth) && bot.maxHealth > 0) {
    maxHealth = bot.maxHealth
  } else if (!attribute && bot?.entity && finite(definition?.default) && definition.default > 0) {
    // Vanilla does not send unchanged attributes on join. Its client uses the
    // versioned registry default until an explicit attribute override arrives.
    maxHealth = definition.default
  }
  const absorptionKey = bot?.registry?.entitiesByName?.player?.metadataKeys?.indexOf('player_absorption') ?? -1
  const absorption = absorptionKey >= 0 ? bot?.entity?.metadata?.[absorptionKey] : undefined
  return { health: finite(bot?.health) ? bot.health : null, maxHealth,
    ...(finite(absorption) && absorption > 0 ? { absorption } : {}) }
}

module.exports = { healthState }
