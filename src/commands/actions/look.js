const { resolve, wrap } = require('../look_input')
const { Vec3 } = require('vec3')
const { crosshairFeedback, blockAim } = require('../look_feedback')
module.exports = {
  name: 'look',
  description: 'Turn once; no movement, click or tracking. angle: Minecraft degrees (0 south, 90 west, -90 east, positive pitch down). at: exact world-space point, not a block-grid position. block: recommended before block interaction; integer grid coordinates, aims at the first supported shape box center. For placement aim at existing support, not the empty destination. Buttons use selection boxes; others use registry collision boxes, not complete vanilla outlines; missing shapes fail. ~ offsets current angles/feet; block floors feet first. All modes return first crosshair block/entity within 4.5 blocks; block also returns targetMatched. Check these before clicking; ok means turned, not interaction success. No visible-face search, ^ or radians',
  usage: 'look angle <yaw> <pitch> | look at <x> <y> <z> | look block <x> <y> <z>',
  async handler(bot, args) {
    if (args[0] === 'angle') {
      const yaw = wrap(resolve(args[1], 180 - bot.entity?.yaw * 180 / Math.PI))
      const pitch = Math.max(-90, Math.min(90, resolve(args[2], -bot.entity?.pitch * 180 / Math.PI)))
      if (![yaw,pitch].every(Number.isFinite)) return { ok:false, errorCode:'STATE_UNAVAILABLE', error:'Current rotation unavailable' }
      await bot.look(Math.PI - yaw * Math.PI / 180, -pitch * Math.PI / 180, true)
      return { ok:true, yaw,pitch, angleUnit:'degrees', ...crosshairFeedback(bot), output:`Look angle yaw=${yaw} pitch=${pitch} degrees (Minecraft)` }
    }
    const values = args.slice(1).map((t,i)=>resolve(t,args[0]==='block'?Math.floor(bot.entity?.position?.[['x','y','z'][i]]):bot.entity?.position?.[['x','y','z'][i]]))
    if (!values.every(v=>Number.isFinite(v)&&Math.abs(v)<=Number.MAX_SAFE_INTEGER)) return {ok:false,errorCode:'INVALID_ARGUMENTS',error:'Resolved target exceeds safe coordinate range'}
    const target = new Vec3(...values)
    if(args[0]==='block') {
      const block=bot.blockAt(target)
      if(!block)return {ok:false,errorCode:'BLOCK_UNAVAILABLE',error:'Target block is not loaded',target}
      if(['air','cave_air','void_air'].includes(block.name))return {ok:false,errorCode:'TARGET_IS_AIR',error:'Air has no selectable shape. For placement, aim at an existing support block, not the empty destination',target,block:block.name}
      const aim=blockAim(block)
      if(!aim)return {ok:false,errorCode:'AIM_SHAPE_UNAVAILABLE',error:'No supported selection/shape data for target; use look at with an explicit point',target}
      await bot.lookAt(aim.point)
      const feedback=crosshairFeedback(bot),hit=feedback.crosshair
      return {ok:true,target,aimPoint:aim.point,shapeSource:aim.shapeSource,shapeIndex:aim.shapeIndex,...feedback,
        targetMatched:feedback.raycastAvailable?!!(hit?.type==='block' && ['x','y','z'].every(k=>hit.position[k]===target[k])):null,
        output:`Turned toward ${block.name} at ${values.join(' ')}; inspect crosshair before interacting`}
    }
    await bot.lookAt(target)
    return {ok:true,target:{x:target.x,y:target.y,z:target.z},...crosshairFeedback(bot),output:`Look at ${values.join(' ')} (world coordinates)`}
  }
}
module.exports.handler = require('../command_contract').withContract(module.exports.name, module.exports.handler)
