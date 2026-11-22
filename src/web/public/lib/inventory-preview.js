import { escapeHtml } from './utils.js'
import { setFreshness } from './freshness.js'

export function itemTooltipText(entry) {
  const item = entry.item
  if (!item) return `Slot ${entry.ref ?? '-'}: Empty`
  const lines = [`${item.displayName || item.name} x${item.count}`, entry.ref ? `Slot ${entry.ref}` : 'Cursor']
  for (const [field, label] of [['enchantments', 'Enchantments'], ['storedEnchantments', 'Stored enchantments']]) {
    if (!item[field]?.length) continue
    lines.push(label + ':')
    for (const enchantment of item[field]) {
      const name = String(enchantment.name ?? 'Unknown').replace(/^minecraft:/, '').replaceAll('_', ' ')
      lines.push(`  ${name} ${enchantment.level}`)
    }
  }
  if (item.durability) lines.push(`Durability: ${item.durability.remaining ?? '?'} / ${item.durability.max ?? '?'} (used ${item.durability.used})`)
  if (item.potion) {
    lines.push(`Potion: ${item.potion.type ?? 'Unknown'}`)
    for(const effect of item.potion.effects??item.potion.customEffects??[])
      lines.push(`  ${effect.name ?? 'Unknown'} ${effect.level}: ${effect.durationTicks<0?'infinite':effect.durationTicks/20+' s'}`)
  }
  for(const layer of item.bannerPatterns??[])lines.push(`Banner: ${layer.color??'Unknown'} / ${layer.pattern??'Unknown'}`)
  if(item.map)lines.push(`Map #${item.map.id}; scale ${item.map.scale??'unknown'}; lock ${item.map.locked??'unknown'}`)
  if(item.map?.pendingOperation)lines.push(`Pending on extraction: ${item.map.pendingOperation}`)
  if(item.trim)lines.push(`Trim: ${item.trim.pattern??'Unknown'} / ${item.trim.material??'Unknown'}`)
  lines.push('', JSON.stringify(item, null, 2))
  return lines.join('\n')
}

function slotHtml(entry, version, selected = false) {
  const item = entry.item
  const text = itemTooltipText(entry)
  const durability = item?.durability
  const percent = durability?.max > 0 ? Math.max(0, Math.min(100, 100 * durability.remaining / durability.max)) : null
  return `<div class="inventory-slot${selected ? ' selected' : ''}" tabindex="0" data-tooltip="${escapeHtml(text)}" aria-label="${escapeHtml(text)}">
    ${item ? `<span class="item-fallback">${escapeHtml(item.name.replaceAll('_', ' '))}</span><img alt="" loading="lazy" src="/api/item-textures/${encodeURIComponent(version || 'unknown')}/${encodeURIComponent(item.name)}"><span class="item-count">${item.count > 1 ? item.count : ''}</span>` : ''}
    ${percent !== null ? `<span class="item-durability" style="--remaining:${percent}%"></span>` : ''}
    <small class="item-slot-number">${escapeHtml(entry.ref?.split(':').at(-1) ?? '')}</small>
  </div>`
}

export function inventoryHtml(data) {
  if (!data.online) return '<p class="inventory-status">Agent offline</p>'
  const slots = entries => entries.map(entry => slotHtml(entry, data.version, entry.ref === data.selectedRef)).join('')
  return `<div class="inventory-sections">
    <section><h4>Equipment / Offhand${data.equipmentFreshness === 'last-known' ? ' <span>Last known</span>' : ''}</h4>${data.equipmentFreshness === 'last-known' ? '<p class="inventory-status equipment-freshness">Container open: equipment updates may be delayed.</p>' : ''}<div class="inventory-equipment">${data.equipment.map(entry => `<div><span class="inventory-equipment-label">${escapeHtml(entry.destination)}</span>${slotHtml(entry, data.version)}</div>`).join('')}</div></section>
    <section><h4>Inventory <span>Player slots</span></h4><div class="inventory-grid">${slots(data.storage.slice(0, 27))}</div><div class="inventory-grid inventory-hotbar">${slots(data.storage.slice(27))}</div></section>
    <section><h4>Current Container</h4>${data.container ? `<p class="inventory-window-title">${escapeHtml(data.container.type)} #${data.container.id}</p><div class="inventory-grid">${slots(data.container.slots)}</div>` : '<p class="inventory-status">No container open</p>'}${data.container?.preview ? `<h4>Preview</h4>${slotHtml(data.container.preview,data.version)}` : ''}${data.cursor ? `<h4>Cursor</h4>${slotHtml({ item: data.cursor }, data.version)}` : ''}</section>
  </div>`
}

let tooltipSequence = 0
function createInventoryTooltip(container) {
  const tooltip = document.createElement('div')
  tooltip.id = `inventory-item-tooltip-${++tooltipSequence}`
  tooltip.className = 'inventory-item-tooltip'
  tooltip.setAttribute('role', 'tooltip')
  tooltip.hidden = true
  document.body.appendChild(tooltip)
  let anchor = null, pointer = null, keyboard = false, dismissed = false
  const hide = () => {
    anchor?.removeAttribute('aria-describedby')
    anchor = null
    tooltip.hidden = true
  }
  const show = slot => {
    if (!slot || !container.contains(slot) || !slot.getClientRects().length) return hide()
    if (anchor !== slot) anchor?.removeAttribute('aria-describedby')
    anchor = slot
    slot.setAttribute('aria-describedby', tooltip.id)
    tooltip.textContent = slot.dataset.tooltip
    tooltip.hidden = false
    const rect = slot.getBoundingClientRect()
    const x = keyboard ? rect.right : pointer.x
    const y = keyboard ? rect.top : pointer.y
    tooltip.style.left = `${Math.max(8, Math.min(x + 14, innerWidth - tooltip.offsetWidth - 8))}px`
    tooltip.style.top = `${Math.max(8, Math.min(y + 14, innerHeight - tooltip.offsetHeight - 8))}px`
  }
  const refresh = () => {
    if (dismissed) return hide()
    if (keyboard) show(document.activeElement?.closest('.inventory-slot'))
    else if (pointer) show(document.elementFromPoint(pointer.x, pointer.y)?.closest('.inventory-slot'))
    else hide()
  }
  const move = event => {
    dismissed = false
    keyboard = false
    pointer = { x: event.clientX, y: event.clientY }
    show(event.target.closest('.inventory-slot'))
  }
  const leave = event => { if (!container.contains(event.relatedTarget)) { pointer = null; hide() } }
  const focus = event => { dismissed = false; keyboard = true; show(event.target.closest('.inventory-slot')) }
  const blur = () => { if (keyboard) hide() }
  const escape = event => { if (event.key === 'Escape') { dismissed = true; pointer = null; hide() } }
  container.addEventListener('pointerover', move)
  container.addEventListener('pointermove', move)
  container.addEventListener('pointerout', leave)
  container.addEventListener('focusin', focus)
  container.addEventListener('focusout', blur)
  container.addEventListener('keydown', escape)
  window.addEventListener('scroll', refresh, true)
  window.addEventListener('resize', refresh)
  return { refresh, destroy() {
    hide()
    tooltip.remove()
    container.removeEventListener('pointerover', move)
    container.removeEventListener('pointermove', move)
    container.removeEventListener('pointerout', leave)
    container.removeEventListener('focusin', focus)
    container.removeEventListener('focusout', blur)
    container.removeEventListener('keydown', escape)
    window.removeEventListener('scroll', refresh, true)
    window.removeEventListener('resize', refresh)
  } }
}

export function initInventoryPreview(container) {
  const panels = new Map()
  const tooltip = createInventoryTooltip(container)
  let disposed = false
  const page = typeof document === 'undefined' ? null : document
  const mark = (panel,state,mode) => setFreshness(panel.querySelector('[data-inventory-freshness]'),{checkedAt:state.checkedAt,mode})
  const update = async (panel, state) => {
    if (disposed || page?.hidden || !panel.isConnected || !panel.open || panels.get(panel) !== state) return
    const revision = state.revision = (state.revision || 0) + 1
    const current = () => !disposed && !page?.hidden && panel.open && panel.isConnected && panels.get(panel) === state && state.revision === revision
    const controller = new AbortController()
    state.controller = controller
    const timeout = setTimeout(() => controller.abort(), 5000)
    try {
      const response = await fetch(`/api/agents/${encodeURIComponent(state.name)}/inventory-preview`, { signal: controller.signal, cache: 'no-store', headers: state.etag ? {'If-None-Match':state.etag} : {} })
      if (response.status === 304) {
        if(current()) {
          if(state.online)state.checkedAt=Date.now()
          mark(panel,state,state.online?'checked':'offline')
        }
        return
      }
      const data = await response.json()
      if (!current()) return
      if (!response.ok || !data.ok) throw new Error(data.error || 'Inventory unavailable')
      if (data.agentName !== state.name) throw new Error('Agent identity mismatch')
      state.etag = response.headers?.get('etag') || null
      if(!data.online && state.sessionId!==data.sessionId)state.checkedAt=undefined
      state.online=data.online
      if(data.online)state.checkedAt=Date.now()
      mark(panel,state,data.online?'checked':'offline')
      // Keep same-session cached slots on disconnect, explicitly marked last known.
      if(!data.online && state.hasData && state.sessionId===data.sessionId)return
      state.sessionId=data.sessionId
      state.hasData=!!data.online
      const signature = JSON.stringify(data)
      if (signature !== state.signature) {
        state.signature = signature
        const content = panel.querySelector('.inventory-preview-content')
        content.innerHTML = inventoryHtml(data)
        content.querySelectorAll('img').forEach(img => {
          img.addEventListener('load', () => img.parentElement.classList.add('has-texture'))
          img.addEventListener('error', () => { img.hidden = true })
        })
        tooltip.refresh()
      }
    } catch (error) {
      if (current()) {
        mark(panel,state,'error')
        if(!state.hasData)panel.querySelector('.inventory-preview-content').textContent = error.name === 'AbortError' ? 'Inventory request timed out' : error.message
        state.etag = null
        tooltip.refresh()
      }
    } finally {
      clearTimeout(timeout)
      if (current()) state.timer = setTimeout(() => update(panel, state), 1000)
    }
  }
  const release = panel => {
    const state = panels.get(panel)
    if (!state) return
    panels.delete(panel)
    clearTimeout(state.timer)
    state.controller?.abort()
    tooltip.refresh()
  }
  const toggle = event => {
    const panel = event.target
    if (!panel.matches('.inventory-preview')) return
    release(panel)
    if (panel.open) {
      const state = { name: panel.closest('[data-agent-name]').dataset.agentName }
      panels.set(panel, state)
      panel.querySelector('.inventory-preview-content').textContent = 'Loading...'
      mark(panel,state,'checked')
      update(panel, state)
    }
  }
  container.addEventListener('toggle', toggle, true)
  function visibility() {
    for (const [panel,state] of panels) {
      state.revision = (state.revision || 0) + 1
      clearTimeout(state.timer); state.controller?.abort()
      mark(panel,state,'paused')
      if (!page.hidden) update(panel,state)
    }
  }
  page?.addEventListener?.('visibilitychange',visibility)
  const observer = new MutationObserver(() => {
    for (const panel of panels.keys()) if (!panel.isConnected) release(panel)
    tooltip.refresh()
  })
  observer.observe(container, { childList: true, subtree: true })
  return () => {
    disposed = true
    tooltip.destroy()
    observer.disconnect()
    container.removeEventListener('toggle', toggle, true)
    page?.removeEventListener?.('visibilitychange',visibility)
    for (const panel of panels.keys()) release(panel)
  }
}
