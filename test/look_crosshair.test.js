const test=require('node:test'),assert=require('node:assert/strict'),{Vec3}=require('vec3')
const Block=require('prismarine-block')('1.21.11'),data=require('minecraft-data')('1.21.11')
const {RaycastIterator}=require('prismarine-world').iterators
const {handler}=require('../src/commands/actions/look')
function fixture(name,properties={}) {
  const block=Block.fromProperties(data.blocksByName[name].id,properties,0);block.position=new Vec3(0,1,0)
  const blocks=[block],bot={entity:{id:1,position:new Vec3(.5,0,-2),eyeHeight:1.62,yaw:0,pitch:0},entities:{}}
  bot.blockAt=()=>block
  bot.lookAt=async point=>{const d=point.minus(bot.entity.position.offset(0,1.62,0));bot.entity.yaw=Math.atan2(-d.x,-d.z);bot.entity.pitch=Math.atan2(d.y,Math.hypot(d.x,d.z))}
  bot.look=async(y,p)=>{bot.entity.yaw=y;bot.entity.pitch=p}
  bot.world={raycast:(eye,direction,limit,matcher)=>{
    let best=null,distance=limit
    for(const b of blocks){const ray=new RaycastIterator(eye,direction,limit);if(matcher(b,ray)&&eye.distanceTo(b.intersect)<distance){distance=eye.distanceTo(b.intersect);best=b}}
    return best
  }}
  return {bot,block,blocks}
}
for(const [name,properties] of [['chest',{}],['stone_slab',{type:'bottom'}],['stone_slab',{type:'top'}],['stone_button',{face:'wall',facing:'north',powered:false}],['stone_button',{face:'floor',facing:'east',powered:true}]]) {
  test('look block shape and ray hit: '+name+JSON.stringify(properties),async()=>{
    const {bot}=fixture(name,properties),r=await handler(bot,['block','0','1','0'])
    assert.equal(r.ok,true);assert.equal(r.targetMatched,true);assert.equal(r.crosshair.name,name)
    assert.equal(r.crosshair.type,'block');assert.ok(r.crosshair.distance<=4.5)
    if(name==='stone_slab')assert.equal(r.aimPoint.y,properties.type==='top'?1.75:1.25)
    if(name==='stone_button')assert.equal(r.shapeSource,'button-selection-shape')
  })
}
test('look modes report first obstruction, including entities; turning is not target success',async()=>{
  const {bot,blocks}=fixture('chest'),wall=Block.fromStateId(data.blocksByName.stone.defaultState,0);wall.position=new Vec3(0,1,-1);blocks.push(wall)
  let r=await handler(bot,['block','0','1','0']);assert.equal(r.targetMatched,false);assert.equal(r.crosshair.name,'stone')
  r=await handler(bot,['at','.5','1.5','.5']);assert.equal(r.crosshair.name,'stone')
  r=await handler(bot,['angle','0','0']);assert.equal(r.crosshair.name,'stone')
  bot.entities={2:{id:2,name:'cow',position:new Vec3(.5,0,-1.7),width:.6,height:1.8}}
  r=await handler(bot,['block','0','1','0']);assert.equal(r.crosshair.type,'entity');assert.equal(r.targetMatched,false)
})
test('look block rejects missing shapes/decimal grids; relative offsets floor feet; far targets are not crosshair hits',async()=>{
  const {bot}=fixture('chest')
  assert.equal((await handler(bot,['block','.5','1','0'])).errorCode,'INVALID_ARGUMENTS')
  bot.entity.position=new Vec3(.9,0,-2.1)
  assert.deepEqual({...((await handler(bot,['block','~','~1','~3'])).target)},{x:0,y:1,z:0})
  bot.blockAt=()=>null;assert.equal((await handler(bot,['block','0','1','0'])).errorCode,'BLOCK_UNAVAILABLE')
  bot.blockAt=()=>({name:'air',position:new Vec3(0,1,0),shapes:[]})
  assert.equal((await handler(bot,['block','0','1','0'])).errorCode,'TARGET_IS_AIR')
  bot.blockAt=()=>({name:'unknown_shape',position:new Vec3(0,1,0),shapes:[]})
  assert.equal((await handler(bot,['block','0','1','0'])).errorCode,'AIM_SHAPE_UNAVAILABLE')
  const f=fixture('chest');f.bot.entity.position=new Vec3(.5,0,-20)
  const r=await handler(f.bot,['block','0','1','0']);assert.equal(r.crosshair,null);assert.equal(r.targetMatched,false)
})
