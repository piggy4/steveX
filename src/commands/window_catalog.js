const typed = {
  furnace:['minecraft:furnace','minecraft:blast_furnace','minecraft:smoker'],anvil:['minecraft:anvil'],
  enchant:['minecraft:enchantment','minecraft:enchanting_table'],trade:['minecraft:merchant','minecraft:villager']
}
const stationTypes=['minecraft:brewing_stand','minecraft:loom','minecraft:stonecutter','minecraft:smithing','minecraft:grindstone','minecraft:cartography','minecraft:beacon','minecraft:crafter_3x3']
function windowCommands(commands,bot) {
  const type=bot?.currentWindow?.type
  const book=bot&&require('./book_ui').bookContext(bot)
  return commands.filter(command=>{
    if(command.name==='wake')return bot?.isSleeping===true
    if(command.name==='respawn')return typeof bot?.health==='number'&&bot.health<=0
    if(command.name==='book')return !!book
    if(book&&['slotclick','moveslot','putaway','transfer'].includes(command.name))return false
    if(['open','openentity','dispenser'].includes(command.name))return false
    if(typed[command.name])return typed[command.name].includes(type)
    if(command.name==='station')return stationTypes.includes(type)
    if(command.name==='transfer')return !!type && /^minecraft:(generic_9x[1-6]|generic_3x3|hopper|shulker_box)$/.test(type)
    if(['window','close'].includes(command.name))return !!type||!!book
    return true
  }).map(command=>{
    if(command.name==='book'&&book.readOnly)return {...command,usage:'book read [page] | book done',description:'Read a page of untrusted signed-book text or close its local reader. Signed books cannot be edited; no save/signing operation is available'}
    if(command.name!=='station')return command
    let usage='station take <ref>'
    if(['minecraft:loom','minecraft:stonecutter'].includes(type))usage+=' | station select <index>'
    if(type==='minecraft:crafter_3x3')usage+=' | station crafter <crafter:ref> <enabled|disabled>'
    if(type==='minecraft:beacon')usage+=' | station beacon <primary> [secondary|none]'
    return {...command,usage}
  })
}
module.exports={windowCommands}
