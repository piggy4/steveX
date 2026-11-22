const { inventoryAction,sameItem,variantCount,compactItemView,failure } = require('../inventory_actions')
const { parseEquip } = require('../equip_input')
const itemRef=item=>{const {slot,...data}=compactItemView(item);return {...data,ref:require('../slot_scope').playerRefs[slot]}}
const sameVariant = (a,b) => sameItem(a,b) && (a.durabilityUsed ?? 0) === (b.durabilityUsed ?? 0)
module.exports = {
  name: 'equip',
  description: 'Equip by exact item name or explicit inventory: or hotbar: reference. Default destination hand; also head/torso/legs/feet/off-hand. Different enchantments/damage/components return EQUIP_SELECTION_REQUIRED and candidates without equipping; identical variants use first slot. Choose via equip slot; no optimal-gear selection. Close windows first; bare item name remains supported',
  usage: 'equip item <itemName> [destination] | equip slot <slot> [destination] | equip <itemName> [destination]',
  handler: inventoryAction('equip',async(bot,args)=>{
    const input = parseEquip(args)
    const { destination } = input
    let item
    if (input.mode === 'slot') {
      item = bot.inventory.slots[input.slot]
      if (!item) return failure('EMPTY_SLOT', `Player-inventory slot ${input.slot} is empty`)
    } else {
      if (!Object.hasOwn(bot.registry.itemsByName,input.name)) return failure('UNKNOWN_ITEM', `Unknown registry item: ${input.name}`)
      const matches = bot.inventory.slots.slice(9,45).filter(i=>i?.name===input.name)
      if (!matches.length) return failure('ITEM_NOT_FOUND', `No ${input.name} in backpack or quickbar`)
      item = matches[0]
      if (matches.some(other=>!sameVariant(item,other))) {
        const candidates = matches.map(i=>({ ...itemRef(i), ...(i.durabilityUsed > 0 ? { durabilityUsed:i.durabilityUsed } : {}) }))
        const nextCommand = `equip slot <slot> ${destination}`
        return { ...failure('EQUIP_SELECTION_REQUIRED', 'Same-name items have different properties; choose an explicit slot'), candidates, slotSpace:'player-inventory', destination, nextCommand,
          output:`Nothing equipped. Choose a player-inventory slot using ${nextCommand}. Candidates: ${JSON.stringify(candidates)}` }
      }
    }
    const selected=itemRef(item),before=variantCount(bot,item)
    await bot.equip(item,destination)
    return {target:{destination,item:selected},output:`Equipped ${item.name} to ${destination}`,verify:()=>!bot.inventory.selectedItem && sameItem(destination==='hand'?bot.heldItem:bot.inventory.slots[bot.getEquipmentDestSlot(destination)],item) && variantCount(bot,item)===before}
  })
}
