const test=require('node:test')
const assert=require('node:assert/strict')
const {EventEmitter}=require('node:events')
const metadata=require('../src/commands/item_metadata')
const {brief,details}=require('../src/commands/item_details')
const item=(name,type,data)=>({name,count:1,components:[{type,data}]})
function fixture(){
  const bot=Object.assign(new EventEmitter(),{version:'1.21.11',_client:new EventEmitter()})
  metadata.attach(bot)
  return bot
}
test('potion details use versioned defaults and scale duration without extending summary',()=>{
  const bot=fixture(),p=item('lingering_potion','potion_contents',{potionId:15,customEffects:[]})
  assert.equal(details(p,bot).potion.type,'minecraft:strong_swiftness')
  assert.deepEqual(details(p,bot).potion.effects,[{name:'minecraft:speed',level:2,durationTicks:450}])
  assert.equal(Object.hasOwn(brief(p),'potion'),false)
  bot.version='other';assert.equal(details(p,bot).potion.baseEffectsKnown,false)
})
test('map metadata is unknown until synced; pending zoom is not a completed zoom',()=>{
  const bot=fixture(),map=item('filled_map','map_id',42)
  map.components.push({type:'map_post_processing',data:1})
  assert.deepEqual(details(map,bot).map,{id:42,pendingOperation:'scale'})
  bot._client.emit('map',{itemDamage:42,scale:2,locked:false})
  assert.deepEqual(details(map,bot).map,{id:42,scale:2,locked:false,pendingOperation:'scale'})
})
test('loom preserves synced order, differentiates unknown from empty and shares banner decoder',()=>{
  const bot=fixture(),w={slots:[{name:'white_banner'},{name:'red_dye'},null]}
  assert.equal(metadata.loomOptions(bot,w),null)
  bot._client.emit('registry_data',{id:'minecraft:banner_pattern',entries:[{key:'minecraft:circle'},{key:'minecraft:cross'}]})
  bot._client.emit('tags',{tags:[{tagType:'minecraft:banner_pattern',tags:[{tagName:'minecraft:no_item_required',entries:[1,0]}]}]})
  assert.deepEqual(metadata.loomOptions(bot,w),[{index:0,pattern:'minecraft:cross',color:'red'},{index:1,pattern:'minecraft:circle',color:'red'}])
  const result=item('white_banner','banner_patterns',{layers:[{colorId:14,pattern:{patternId:1}}]})
  assert.deepEqual(details(result,bot).bannerPatterns,[{pattern:'minecraft:cross',color:'red'}])
  w.slots[1]=null;assert.deepEqual(metadata.loomOptions(bot,w),[])
  bot.emit('end');assert.equal(bot._client.listenerCount('map'),0)
})
test('trim identifiers come from synchronized registries, not registry index guesses',()=>{
  const bot=fixture(),armor=item('diamond_chestplate','trim',{pattern:{patternId:0},material:{materialId:0}})
  assert.deepEqual(details(armor,bot).trim,{pattern:null,material:null})
  bot._client.emit('registry_data',{id:'minecraft:trim_pattern',entries:[{key:'minecraft:sentry'}]})
  bot._client.emit('registry_data',{id:'minecraft:trim_material',entries:[{key:'minecraft:gold'}]})
  assert.deepEqual(details(armor,bot).trim,{pattern:'minecraft:sentry',material:'minecraft:gold'})
})
