const test=require('node:test'),assert=require('node:assert/strict')
const {loadCommands}=require('../src/commands'),{commands}=loadCommands()
function fixture(){return {quickBarSlot:0,registry:require('minecraft-data')('1.21.11'),inventory:{slots:Array(46).fill(null),inventoryStart:9,inventoryEnd:45}}}
function put(bot,slot,name,count=1,extra={}){bot.inventory.slots[slot]={name,count,slot,...extra}}
test('inventory summary partitions equipment, hotbar and backpack without slot numbers or component payloads',async()=>{
 const bot=fixture();put(bot,5,'diamond_helmet');put(bot,45,'shield');put(bot,36,'stone',64);put(bot,9,'diamond_pickaxe',1,{components:[{type:'damage',data:8}],maxDurability:1561})
 const r=await commands.inventory(bot,[])
 assert.equal(r.slotSpace,'inventory');assert.equal(r.emptySlotCount,34);assert.equal(r.hotbar[0].selected,true);assert.equal(r.backpack[0].flags.includes('damaged'),true)
 assert.deepEqual(r.equipment.map(i=>i.destination),['head','off-hand']);assert.equal(/"slot":|components|1561/.test(r.output),false)
 assert.deepEqual(JSON.parse(r.output).hotbar,r.hotbar)
})
test('inventory slots includes every nonempty fixed player slot and never expands details',async()=>{
 const bot=fixture();for(const slot of [0,1,5,9,36,45])put(bot,slot,'stone',slot+1,{components:[{type:'custom_name',data:'private item text'}]})
 const r=await commands.inventory(bot,['slots']);assert.deepEqual(r.slots.map(i=>i.ref),['crafting:result','crafting:1','equipment:head','inventory:1','hotbar:1','equipment:offhand']);assert.doesNotMatch(r.output,/private item text/)
})
test('empty inventory returns empty groups and 36 empty backpack/hotbar slots',async()=>{
 const r=await commands.inventory(fixture(),[]);assert.deepEqual(r.backpack,[]);assert.deepEqual(r.hotbar,[]);assert.deepEqual(r.equipment,[]);assert.equal(r.emptySlotCount,36)
})
test('open chest player region overrides stale cache and maps to unchanged player indices',async()=>{
 const bot=fixture();put(bot,9,'apple');put(bot,5,'helmet')
 bot.currentWindow={id:7,type:'minecraft:generic_9x3',inventoryStart:27,inventoryEnd:63,slots:Array(63).fill(null)}
 bot.currentWindow.slots[27]={name:'iron_ingot',count:3,slot:27};bot.currentWindow.slots[54]={name:'stone',count:8,slot:54}
 const r=await commands.inventory(bot,['slots']);assert.equal(r.slotSpace,'inventory');assert.equal(r.emptySlotCount,34)
 assert.deepEqual(r.slots.map(i=>[i.ref,i.name]),[['equipment:head','helmet'],['inventory:1','iron_ingot'],['hotbar:1','stone']])
 assert.equal((await commands.slotinfo(bot,['inventory:1'])).item.name,'iron_ingot')
 assert.equal((await commands.slotinfo(bot,['storage_9x3:28'])).ok,false)
 bot.currentWindow=null;assert.equal((await commands.slotinfo(bot,['inventory:1'])).ref,'inventory:1')
})
test('slotinfo reports enchantments and damaged durability with readable names',async()=>{
 const bot=fixture(),id=bot.registry.enchantmentsByName.unbreaking.id
 put(bot,9,'diamond_pickaxe',1,{maxDurability:1561,components:[{type:'enchantments',data:{enchantments:[{id,level:3}]}},{type:'damage',data:8}]})
 const r=await commands.slotinfo(bot,['inventory:1']);assert.equal(r.empty,false);assert.deepEqual(r.item.enchantments,[{name:'unbreaking',level:3}]);assert.deepEqual(r.item.durability,{used:8,remaining:1553,max:1561})
 assert.equal(r.textTrust,'untrusted-world-data');assert.match(r.output,/unbreaking/)
})
test('slotinfo omits full durability and ordinary empty properties; empty slot is explicit',async()=>{
 const bot=fixture();put(bot,9,'diamond_pickaxe',1,{maxDurability:1561,components:[{type:'damage',data:0},{type:'max_damage',data:1561}]})
 const r=await commands.slotinfo(bot,['inventory:1']);assert.deepEqual(r.item,{name:'diamond_pickaxe',count:1})
 assert.deepEqual((await commands.slotinfo(bot,['inventory:2'])).item,null);assert.equal((await commands.slotinfo(bot,['inventory:2'])).empty,true)
})
test('slotinfo decodes modern bundle and shulker contents from supplied components only',async()=>{
 const bot=fixture(),type=bot.registry.itemsByName.diamond.id
 put(bot,9,'bundle',1,{components:[{type:'bundle_contents',data:{contents:[{itemId:type,itemCount:3,components:[]}]}}]})
 put(bot,10,'shulker_box',1,{components:[{type:'container',data:{contents:[{itemCount:0},{itemId:type,itemCount:2,components:[]}]}}]})
 const a=await commands.slotinfo(bot,['inventory:1']),b=await commands.slotinfo(bot,['inventory:2'])
 assert.equal(a.item.contentsKnown,true);assert.equal(a.item.contents[0].name,'diamond');assert.equal(a.item.contents[0].count,3)
 assert.equal(b.item.contents[0].slot,1);assert.equal(b.item.contents[0].count,2)
 assert.equal(Object.hasOwn(b.item,'components'),false)
})
test('unknown contents remain unknown, explicit empty container remains known empty',async()=>{
 const bot=fixture();put(bot,9,'shulker_box');put(bot,10,'shulker_box',1,{components:[{type:'container',data:{contents:[]}}]})
 assert.equal((await commands.slotinfo(bot,['inventory:1'])).item.contentsKnown,false)
 const b=await commands.slotinfo(bot,['inventory:2']);assert.equal(b.item.contentsKnown,true);assert.deepEqual(b.item.contents,[])
})
test('nested details are bounded with truncation and BigInt safely serialized',async()=>{
 const bot=fixture();let item={name:'stone',count:1}
 for(let i=0;i<7;i++)item={name:'bundle',count:1,components:[{type:'bundle_contents',data:{contents:[item]}}]}
 put(bot,9,'bundle',1,item);put(bot,10,'stone',1,{nbt:{type:'compound',value:{seed:{type:'long',value:123n}}}})
 const r=await commands.slotinfo(bot,['inventory:1']);assert.match(r.output,/detailsTruncated/)
 assert.doesNotThrow(()=>JSON.stringify(r));assert.match((await commands.slotinfo(bot,['inventory:2'])).output,/123/)
})
test('slot scope and index validation never opens windows or falls back',async()=>{
 const bot=fixture()
 for(const args of [[],['inventory'],['inventory:-1'],['inventory:46'],['inventory:1x'],['typo','0']])assert.equal((await commands.slotinfo(bot,args)).ok,false)
 assert.equal((await commands.slotinfo(bot,['storage_9x3:10'])).ok,false)
 assert.equal((await commands.inventory(bot,['slot','inventory:1'])).errorCode,'INVALID_ARGUMENTS')
})
