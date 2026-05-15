/**
 * writebook <slot> <page1>|<page2>|... — write a book
 * slot: inventory slot number (36 = first quickbar)
 * pages: separated by |
 */
module.exports = {
  name: 'writebook',
  description: 'Write text into a book',
  usage: 'writebook <slot> <page1>|<page2>|...',
  async handler(bot, args) {
    if (args.length < 2) return { ok: false, error: 'Usage: writebook <slot> <page1>|<page2>|...' }
    const slot = parseInt(args[0], 10)
    if (isNaN(slot)) return { ok: false, error: 'Invalid slot number' }
    const pages = args.slice(1).join(' ').split('|')
    await bot.writeBook(slot, pages)
    return { ok: true, output: `Wrote ${pages.length} page(s) to book in slot ${slot}` }
  }
}
