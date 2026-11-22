const {compactItemView}=require('../agent/interaction_memory')
const plain=value=>JSON.parse(JSON.stringify(value,(_,v)=>typeof v==='bigint'?v.toString():v))
function legacy(item){try{return item.legacyData||(item.nbt?.type==='compound'?require('prismarine-nbt').simplify(item.nbt):{})}catch{return {}}}
function damage(item){try{return item.durabilityUsed??item.components?.find(c=>c.type==='damage')?.data??legacy(item).Damage??0}catch{return null}}
function brief(item) {
  if(!item)return null
  const c=item.components||[],n=legacy(item),flags=[]
  if(c.some(c=>['enchantments','stored_enchantments'].includes(c.type))||n.Enchantments?.length||n.StoredEnchantments?.length)flags.push('enchanted')
  if(damage(item)>0)flags.push('damaged')
  if(c.some(c=>!['damage','max_damage','enchantments','stored_enchantments'].includes(c.type))||Object.keys(n).some(k=>!['Damage','Enchantments','StoredEnchantments'].includes(k)))flags.push('special-data')
  return {name:item.name,count:item.count,...(flags.length?{flags}:{}),...(item.removedComponents?.length?{flags:[...flags,'modified-defaults']}: {})}
}
function details(item,bot,depth=0,budget={remaining:128}) {
  if(!item)return null
  if(depth>=4||budget.remaining--<=0)return {...brief(item),detailsTruncated:true}
  const compact=compactItemView(item),{slot,...result}=compact,c=item.components||[],n=legacy(item)
  if(item.legacyData)result.legacyData={...n}
  Object.assign(result,require('./item_metadata').describe(item,bot))
  // Decode readable fields without repeating nested container payloads or full-durability defaults.
  result.components=c.filter(c=>!['container','bundle_contents','enchantments','stored_enchantments','damage','max_damage'].includes(c.type))
  if(!result.components.length)delete result.components
  for(const kind of ['enchantments','stored_enchantments']) {
    const data=c.find(c=>c.type===kind)?.data
    const list=Array.isArray(data)?data:data?.enchantments
    const old=kind==='stored_enchantments'?n.StoredEnchantments:n.Enchantments
    const entries=list||old
    if(entries?.length)result[kind==='enchantments'?'enchantments':'storedEnchantments']=entries.map(e=>({name:typeof e.id==='number'?bot.registry?.enchantments?.[e.id]?.name??`unknown:${e.id}`:e.name||e.id,level:e.level??e.lvl}))
  }
  const used=damage(item),max=c.find(c=>c.type==='max_damage')?.data??item.maxDurability??bot.registry?.itemsByName?.[item.name]?.maxDurability
  if(used>0)result.durability={used,...(Number.isFinite(max)?{remaining:Math.max(0,max-used),max}: {})}
  if(n.display?.Name)result.customName=n.display.Name
  if(n.display?.Lore)result.lore=n.display.Lore
  const component=c.find(c=>['container','bundle_contents'].includes(c.type)),old=n.BlockEntityTag?.Items||n.Items
  const contents=component?(Array.isArray(component.data)?component.data:component.data?.contents):old
  if(component||old) {
    if(result.nbt?.value?.BlockEntityTag?.value?.Items)delete result.nbt.value.BlockEntityTag.value.Items
    if(result.nbt?.value?.Items)delete result.nbt.value.Items
    if(result.legacyData){delete result.legacyData.Items;if(result.legacyData.BlockEntityTag)result.legacyData.BlockEntityTag={...result.legacyData.BlockEntityTag,Items:undefined}}
    result.contentsKnown=Array.isArray(contents)
    if(Array.isArray(contents)) {
      result.contents=[]
      for(const [index,entry] of contents.entries()) {
        if(budget.remaining<=0){result.contentsTruncated=true;break}
        const raw=entry?.item||entry
        if(!raw)continue
        const count=raw.count??raw.itemCount??raw.Count??0
        if(count<=0)continue
        const type=raw.itemId??raw.type,reg=bot.registry?.items?.[type]
        const nested=raw.name?{...raw}:{name:raw.id?.replace(/^minecraft:/,'')||reg?.name||`unknown:${type}`,type,count,metadata:raw.metadata??0,
          components:raw.components,removedComponents:raw.removedComponents,nbt:raw.nbt,maxDurability:reg?.maxDurability}
        if(raw.tag&&!nested.nbt)nested.legacyData=raw.tag
        result.contents.push({...(component?.type==='bundle_contents'?{}:{slot:entry.Slot??entry.slot??index}),...details(nested,bot,depth+1,budget)})
      }
    }
  }else if(item.name?.includes('shulker_box')||item.name?.endsWith('bundle'))result.contentsKnown=false
  return plain(result)
}
module.exports={brief,details}
