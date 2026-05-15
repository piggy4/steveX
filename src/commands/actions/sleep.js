/**
 * sleep — find a nearby bed and sleep
 */
module.exports = {
  name: 'sleep',
  description: 'Find and sleep in a nearby bed',
  usage: 'sleep',
  async handler(bot) {
    const bed = bot.findBlock({
      matching: block => bot.isABed(block),
      maxDistance: 4
    })
    if (!bed) return { ok: false, error: 'No bed nearby' }
    await bot.sleep(bed)
    return { ok: true, output: `Sleeping in bed at ${bed.position}` }
  }
}
