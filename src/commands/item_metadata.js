const caches=new WeakMap()
// Vanilla 1.21.11 Potions bootstrap order; this built-in registry is not sent.
const vanillaPotions=require('./potions_1_21_11.json')
const bare=id=>id?.replace(/^minecraft:/,'')
const colors='white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black'.split(' ')
const component=(item,type)=>item?.components?.find(c=>c.type===type)?.data
const registries=new Set(['banner_pattern','trim_material','trim_pattern','potion','mob_effect'])

function attach(bot){
  if(caches.has(bot))return
  const state={registries:new Map(),tags:new Map(),maps:new Map()}
  caches.set(bot,state)
  const registry=p=>{const id=bare(p.id);if(registries.has(id))state.registries.set(id,p.entries)}
  const tags=p=>{
    const group=p.tags?.find(g=>bare(g.tagType)==='banner_pattern')
    if(group)state.tags.set('banner_pattern',new Map(group.tags.map(t=>[bare(t.tagName),t.entries])))
  }
  const map=p=>{
    state.maps.delete(p.itemDamage)
    state.maps.set(p.itemDamage,{scale:p.scale,locked:p.locked})
    if(state.maps.size>256)state.maps.delete(state.maps.keys().next().value)
  }
  bot._client.on('registry_data',registry);bot._client.on('tags',tags);bot._client.on('map',map)
  bot.once('end',()=>{
    bot._client.removeListener('registry_data',registry);bot._client.removeListener('tags',tags)
    bot._client.removeListener('map',map);caches.delete(bot)
  })
}
const entry=(bot,registry,id)=>caches.get(bot)?.registries.get(registry)?.[id]
function holder(bot,registry,value,key){
  return entry(bot,registry,value?.[key])?.key??value?.data?.assetId??value?.data?.assetBase??null
}
function describe(item,bot){
  const out={}
  const potion=component(item,'potion_contents')
  if(potion){
    const defaultScale=item.name==='lingering_potion'?0.25:item.name==='tipped_arrow'?0.125:1
    const scale=component(item,'potion_duration_scale')??(item.removedComponents?.includes('potion_duration_scale')?1:defaultScale)
    const scaled=t=>t<=0?t:Math.max(1,Math.floor(Math.fround(t*scale)))
    const effect=e=>({name:entry(bot,'mob_effect',e.id)?.key??bot.registry?.effects?.[e.id]?.name??null,
      level:e.details.amplifier+1,durationTicks:scaled(e.details.duration)})
    const synced=entry(bot,'potion',potion.potionId)
    const base=bot.version==='1.21.11'&&!caches.get(bot)?.registries.has('potion')?vanillaPotions[potion.potionId]:null
    const customEffects=(potion.customEffects??[]).map(effect)
    out.potion={type:synced?.key??(base?'minecraft:'+base[0]:null),
      ...(potion.potionId!==undefined?{typeId:potion.potionId}:{}),durationScale:scale,
      baseEffectsKnown:!!base||potion.potionId===undefined,
      effects:[...(base?.[1]??[]).map(([name,ticks,level])=>({name:'minecraft:'+name,level,durationTicks:scaled(ticks)})),...customEffects]}
  }
  const layers=component(item,'banner_patterns')?.layers
  if(Array.isArray(layers))out.bannerPatterns=layers.map(l=>({pattern:holder(bot,'banner_pattern',l.pattern,'patternId'),color:colors[l.colorId]??null}))
  const id=component(item,'map_id')
  if(Number.isInteger(id)){
    const cached=caches.get(bot)?.maps.get(id),operation=component(item,'map_post_processing')
    out.map={id,...(cached??{}),...(operation===0||operation===1?{pendingOperation:operation===0?'lock':'scale'}:{})}
  }
  const trim=component(item,'trim')
  if(trim)out.trim={material:holder(bot,'trim_material',trim.material,'materialId'),pattern:holder(bot,'trim_pattern',trim.pattern,'patternId')}
  return out
}
function loomOptions(bot,w){
  const [banner,dye,pattern]=w.slots
  if(!banner||!dye)return []
  if((component(banner,'banner_patterns')?.layers?.length??0)>=6)return []
  const tag=pattern?component(pattern,'provides_banner_patterns')??
    (bot.version==='1.21.11'&&pattern.name?.endsWith('_banner_pattern')&&!pattern.removedComponents?.includes('provides_banner_patterns')
      ?'pattern_item/'+pattern.name.slice(0,-15):null):'no_item_required'
  const ids=tag&&caches.get(bot)?.tags.get('banner_pattern')?.get(bare(tag))
  if(!ids)return null
  const color=dye.name?.replace(/_dye$/,'')
  if(!colors.includes(color))return null
  return ids.map((id,index)=>({index,pattern:entry(bot,'banner_pattern',id)?.key??null,color}))
}
module.exports={attach,describe,loomOptions}
