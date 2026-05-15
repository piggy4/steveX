/**
 * craft <itemName> [count] — craft an item from inventory
 */
module.exports = {
  name: 'craft',
  description: 'Craft an item from inventory (hand-craftable only)',
  usage: 'craft <itemName> [count]',
  async handler(bot, args) {
    if (args.length < 1) return { ok: false, error: 'Usage: craft <itemName> [count]' }
    const itemName = args[0].toLowerCase()
    const count = args[1] ? parseInt(args[1], 10) : 1
    if (isNaN(count) || count < 1) return { ok: false, error: 'Invalid count' }

    const mcData = require('minecraft-data')(bot.version)
    const itemId = Object.values(mcData.items).find(i => i.name === itemName || i.displayName?.toLowerCase() === itemName)?.id
    if (!itemId) return { ok: false, error: `Unknown item: "${itemName}"` }

    const recipes = bot.recipesFor(itemId, null, count, null)
    if (recipes.length === 0) return { ok: false, error: `No hand-craftable recipe for "${itemName}"` }

    await bot.craft(recipes[0], count, null)
    return { ok: true, output: `Crafted ${itemName} x${count}` }
  }
}
