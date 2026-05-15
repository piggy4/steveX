/**
 * findblock <blockName> [radius] — find the nearest block by name
 */
module.exports = {
  name: 'findblock',
  description: 'Find the nearest block by name',
  usage: 'findblock <blockName> [radius]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: findblock <blockName> [radius]' }
    const targetName = args[0].toLowerCase()
    const maxDistance = args[1] ? parseInt(args[1], 10) : 32
    if (isNaN(maxDistance) || maxDistance < 1) return { ok: false, error: 'Invalid radius' }

    const mcData = require('minecraft-data')(bot.version)
    const blockId = Object.values(mcData.blocks).find(b => b.name === targetName || b.displayName?.toLowerCase() === targetName)?.id
    if (!blockId) return { ok: false, error: `Unknown block: "${targetName}"` }

    const found = bot.findBlock({
      matching: blockId,
      maxDistance,
      count: 5
    })

    if (!found) return { ok: true, output: `No "${targetName}" found within ${maxDistance}m` }

    const pos = found.position
    return { ok: true, output: `Found ${targetName} at ${Math.floor(pos.x)} ${Math.floor(pos.y)} ${Math.floor(pos.z)} (distance: ${Math.floor(pos.distanceTo(bot.entity.position))}m)` }
  }
}
