const installedVersion = require('mineflayer/package.json').version

function diggingCompatibility(bot) {
  if (installedVersion !== '4.39.0') return false
  const original = bot.digTime
  const read = item => {
    const value = item?.enchants ?? []
    if (Array.isArray(value)) return { value, wrapped: false }
    if (!Array.isArray(value?.enchantments)) throw new TypeError('Unsupported item enchantment data')
    return { wrapped: true, value: value.enchantments.map(e => ({
      name: typeof e.id === 'number' ? bot.registry.enchantments[e.id]?.name : e.name || e.id,
      lvl: e.level ?? e.lvl
    })) }
  }
  const wrapped = block => {
    const held = read(bot.heldItem), head = read(bot.inventory.slots[bot.getEquipmentDestSlot('head')])
    if (!held.wrapped && !head.wrapped) return original(block)
    // 4.39.0 passes the modern component wrapper to prismarine-block as an array.
    // Keep upstream tool, water, ground and effect rules; normalize only that shape.
    return block.digTime(bot.heldItem?.type ?? null, bot.game.gameMode === 'creative',
      ['water', 'flowing_water'].includes(bot._getBlockAtEyeLevel()?.name),
      !bot.entity.onGround, held.value.concat(head.value), bot.entity.effects)
  }
  bot.digTime = wrapped
  bot.once('end', () => { if (bot.digTime === wrapped) bot.digTime = original })
  return true
}

module.exports = { diggingCompatibility }
