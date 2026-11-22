const api = require('./workstations')
const {isDeepStrictEqual}=require('node:util')
const {sameItem}=require('./inventory_actions')

async function enchant(bot, args = [], signal) {
  signal?.throwIfAborted()
  const op = args[0]
  if (!op || op === 'open') throw Object.assign(new Error('Use right click to open the enchanting table first'),{code:'INVALID_ARGUMENTS'})
  const w = api.current(bot, ['minecraft:enchantment', 'minecraft:enchanting_table'])
  let verify
  if (op === 'put' || op === 'lapis') {
    const item = api.slot(w, args[1], true)
    if (op === 'lapis' && item.name !== 'lapis_lazuli') throw new Error('Choose a lapis_lazuli inventory slot')
    if (w.slots[op === 'put' ? 0 : 1]) throw new Error('Target slot occupied; take it out first')
    await w[op === 'put' ? 'putTargetItem' : 'putLapis'](item)
    verify=()=>sameItem(w.slots[op==='put'?0:1],item)
  } else if (op === 'choose') {
    const choice = api.integer(args[1], 'enchantment choice', 0, 2)
    if (!w.enchantments?.[choice] || w.enchantments[choice].level <= 0) throw new Error('Enchantment unavailable; inspect window after inserting item/lapis')
    const before=structuredClone(w.slots[0])
    await w.enchant(choice)
    verify=()=>!!w.slots[0]&&!sameItem(w.slots[0],before)
  } else if (op === 'take') { api.slot(w, '0'); await w.takeTargetItem();verify=()=>!w.slots[0] }
  else throw new Error('Usage: enchant put <slot> | enchant lapis <slot> | enchant choose <0..2> | enchant take')
  return {...api.success(bot, `Enchantment operation ${op} completed`),verify}
}

async function anvil(bot, args = [], signal) {
  signal?.throwIfAborted()
  if (!args.length || args[0] === 'open') throw Object.assign(new Error('Use right click to open the anvil first'),{code:'INVALID_ARGUMENTS'})
  const w = api.current(bot, ['minecraft:anvil'])
  const first = api.slot(w, args[1], true)
  const before=structuredClone(w.slots)
  if (args[0] === 'rename') {
    const name = args.slice(2).join(' ')
    if (!name || name.length > 35) throw new Error('Name must contain 1..35 characters')
    await w.rename(first, name)
  } else if (args[0] === 'combine') {
    const second = api.slot(w, args[2], true)
    if (first.slot === second.slot) throw new Error('Choose two different inventory slots')
    await w.combine(first, second, args.slice(3).join(' ') || undefined)
  } else throw new Error('Usage: anvil rename <slot> <name> | anvil combine <slot1> <slot2> [name]')
  return {...api.success(bot, `Anvil operation ${args[0]} completed`),verify:()=>!w.selectedItem&&!isDeepStrictEqual(before.slice(w.inventoryStart),w.slots.slice(w.inventoryStart))}
}

async function furnace(bot, args = [], signal) {
  signal?.throwIfAborted()
  if (!args.length || args[0] === 'open') throw Object.assign(new Error('Use right click to open the furnace first'),{code:'INVALID_ARGUMENTS'})
  const w = api.current(bot, ['minecraft:furnace', 'minecraft:blast_furnace', 'minecraft:smoker'])
  let verify
  if (args[0] === 'input' || args[0] === 'fuel') {
    const item = api.slot(w, args[1], true), count = api.integer(args[2] ?? 1, 'count', 1, item.count)
    const source=item.slot,before=item.count,identity=structuredClone(item)
    // Move exactly the selected stack, rather than letting a type-only native
    // search substitute another stack with different components.
    await require('./window_actions').move(bot,w,source,args[0]==='input'?0:1,count,signal)
    verify=()=>before-(sameItem(w.slots[source],identity)?w.slots[source].count:0)===count
  } else if (args[0] === 'take') {
    const methods = { input: ['takeInput', 0], fuel: ['takeFuel', 1], output: ['takeOutput', 2] }
    const op = args[1].startsWith('furnace:') && Object.hasOwn(methods,args[1].slice(8)) ? methods[args[1].slice(8)] : null
    if (!op) throw new Error('Expected input, fuel or output')
    api.slot(w, String(op[1])); await w[op[0]]()
    verify=()=>!w.slots[op[1]]
  } else throw new Error('Usage: furnace input <slot> [count] | furnace fuel <slot> [count] | furnace take <input|fuel|output>')
  return {...api.success(bot, `Furnace operation ${args.join(' ')} completed`),verify}
}
module.exports = { enchant, anvil, furnace }
