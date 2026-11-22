const api = require('../workstations')
module.exports = {
  name: 'window', description: 'Read current UI, scoped slots, empty slots and trades. Loom and stonecutter options include the button index; null means unknown and [] means no usable choices. Copy each typed ref directly into slot commands; player references stay fixed. Book UI is not a slot window', usage: 'window',
  handler: bot => {
    const book=require('../book_ui').describeBook(bot)
    if(book)return {ok:true,window:book,output:JSON.stringify(book)}
    return !bot.currentWindow ? { ok:false, errorCode:'NO_WINDOW', error:'No inventory window is open' } : api.guarded(async () => {
      const data = {...api.describe(bot, {compact:true}),openedFrom:require('../window_open').windowSource(bot.currentWindow)}
      return {ok:true,output:JSON.stringify(data),window:data}
    })
  }
}

module.exports.handler = require('../command_contract').withContract(module.exports.name, module.exports.handler)
