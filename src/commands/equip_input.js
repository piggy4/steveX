const destinations = new Set(['hand', 'head', 'torso', 'legs', 'feet', 'off-hand'])
function parseEquip(args) {
  if (!Array.isArray(args) || args.some(value => typeof value !== 'string' || !value || /\s/.test(value))) return null
  const mode = args[0]?.toLowerCase()
  const explicit = mode === 'slot' || mode === 'item'
  if (args.length < (explicit ? 2 : 1) || args.length > (explicit ? 3 : 2)) return null
  const value = args[explicit ? 1 : 0]
  const destination = (args[explicit ? 2 : 1] || 'hand').toLowerCase()
  if (!destinations.has(destination)) return null
  if (mode === 'slot') {
    const slot=require('./slot_scope').storageIndex(value)
    if(slot<0)return null
    return { mode, slot, destination }
  }
  if (!/^(?:minecraft:)?[a-z0-9_]+$/i.test(value)) return null
  return { mode: 'item', name: value.toLowerCase().replace(/^minecraft:/, ''), destination }
}
module.exports = { parseEquip }
