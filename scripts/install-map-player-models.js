const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto')
const {execFile}=require('node:child_process'),{promisify}=require('node:util')
const run=promisify(execFile),root=path.resolve(__dirname,'..')
const hashes={
  'player-models.js':'cf6d269f493b27ff5dbd49c23e3fc586eaaf877ce876b3b180bc00e09dcf8d4b',
  'items-registry.json':'182544995faa1c1619c7d17fcdca48d9fdb3d5c4ac48cf119b85d98314cbb316',
  LICENSE:'3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986'
}
function syncPlayerLabel(app, instance) {
  const marker = app.playerMarkerManager?.getPlayerMarker(instance.data.uuid)
  if (!marker || marker.data?.foreign || !marker.visible) return
  // CSS2D projects matrixWorld, not position. Follow the interpolated model even
  // after movement stops, instead of BlueMap's separate one-second animation.
  const p = instance.currentPos
  marker.position.set(p.x, p.y + 1.8, p.z)
  marker.updateMatrixWorld(true)
}
function adapt(source) {
  let js=source.replaceAll('\r\n','\n')
  const replace=(from,to)=>{if(js.split(from).length!==2)throw Error('Upstream adaptation mismatch: '+from.slice(0,80));js=js.replace(from,to)}
  replace('        const app = window.bluemap;',`        const app = window.bluemap;
        let stopped = false, inFlight = false;
        const requests = new Set();
        const nativeFetch = window.fetch.bind(window);
        async function fetch(url, options = {}) {
            if (stopped || document.hidden) throw new DOMException('Inactive map', 'AbortError');
            const controller = new AbortController();
            requests.add(controller);
            const timeout = setTimeout(() => controller.abort(), 5000);
            try { return await nativeFetch(url, {...options, signal: controller.signal, cache:'no-store'}); }
            finally { clearTimeout(timeout); requests.delete(controller); }
        }`)
  replace('        function registerAnimatedTexture(img, canvas, ctx, tex, mcmetaUrl) {',
    '        function registerAnimatedTexture(img, canvas, ctx, tex, mcmetaUrl) { return; // Lightweight monitor: first texture frame only.\n')
  replace('            glintUniforms.uGlintTime.value += delta;','            // Enchantment glint is not enabled in the appearance feed.')
  replace('                parts.root.position.copy(currentPos);',
    '                parts.root.position.copy(currentPos);\n                ('+syncPlayerLabel.toString()+')(app, instance);')
  replace('        function updateScene() {',`        function updateScene() {
            if (stopped || document.hidden) return;
            app.mapViewer.redraw(); // Keep BlueMap itself on foreground vsync, not its idle 20 FPS.`)
  replace('        async function fetchPlayers() {\n            try {',`        async function fetchPlayers() {
            if (stopped || document.hidden || inFlight) return;
            inFlight = true;
            for (const [id, instance] of loadedPlayers) {
                if (Date.now() - instance.data.observedAt > 5000) {
                    destroyPlayerInstance(instance); loadedPlayers.delete(id);
                }
            }
            try {`)
  replace('                if (!Array.isArray(playersList)) return;',
    '                if (stopped || document.hidden || !Array.isArray(playersList)) return;')
  replace('                for (const pData of playersList) {',
    '                for (const pData of playersList) {\n                    if (!Number.isFinite(pData.observedAt) || Date.now() - pData.observedAt > 5000) continue;')
  replace('                // Ignore transient network errors\n            }\n        }',
    '                // A failed feed never keeps stale avatars indefinitely.\n            } finally { inFlight = false; }\n        }')
  replace('            if (!instance) return;\n            instance.dynamicHeldItems = [];',
    '            if (!instance) return;\n            instance.destroyed = true;\n            instance.dynamicHeldItems = [];')
  js=js.replaceAll('if (!result) return;','if (!result || stopped || instance.destroyed) return;')
  replace('        console.log("[BlueMap-Player-Models] 3D Player Models loaded successfully!");',`        function suspend() {
            clearInterval(pollId); cancelAnimationFrame(animId);
            for (const controller of requests) controller.abort();
            window.__bpmState.destroyAll();
        }
        function visibility() {
            if (document.hidden) suspend();
            else if (!stopped) {
                lastFrameTime = performance.now();
                window.__bpmState.interval = setInterval(fetchPlayers, POLL_INTERVAL);
                fetchPlayers(); animId = requestAnimationFrame(updateScene);
            }
        }
        document.addEventListener('visibilitychange', visibility);
        window.addEventListener('pagehide', () => {
            stopped = true; clearInterval(window.__bpmState.interval); suspend();
            document.removeEventListener('visibilitychange', visibility);
            playerContainer.removeFromParent();
        }, {once:true});
        const cameraHash = () => {
            if (window.parent !== window) window.parent.postMessage({type:'stevex-map-camera',hash:location.hash}, '*');
        };
        window.addEventListener('hashchange', cameraHash); cameraHash();
        console.log('[SteveX] syrupderg player models / lightweight 1.21.11 adapter');`)
  // The resumed timer is distinct from the initial constant timer.
  js=js.replace('clearInterval(pollId); cancelAnimationFrame(animId);','clearInterval(pollId); clearInterval(window.__bpmState.interval); cancelAnimationFrame(animId);')
  return '// GPL-3.0 upstream syrupderg/bluemap-player-models @856213085250d77f855bd43b80c5a93e9acca4be\n// SteveX modifications: polling, expiry, visibility, foreground vsync; see vendor README.\n'+js
}
async function install(webroot,clientJar) {
  webroot=path.resolve(webroot);clientJar=path.resolve(clientJar)
  const target=path.join(webroot,'assets/bluemap-player-models'),upstream={}
  await fs.mkdir(target,{recursive:true})
  for(const [name,hash] of Object.entries(hashes)) {
    upstream[name]=await fs.readFile(path.join(root,'vendor/bluemap-player-models',name))
    if(crypto.createHash('sha256').update(upstream[name]).digest('hex')!==hash)throw Error('Upstream checksum mismatch: '+name)
  }
  await fs.writeFile(path.join(target,'player-models.js'),adapt(upstream['player-models.js'].toString()))
  await fs.writeFile(path.join(target,'LICENSE'),upstream.LICENSE)
  await fs.copyFile(path.join(root,'src/web/public/lib/map-player-labels.js'),path.join(target,'player-labels.js'))
  const items=require('minecraft-data')('1.21.11').itemsByName
  const registry=Object.fromEntries(Object.entries(JSON.parse(upstream['items-registry.json'])).filter(([id])=>items[id]))
  await fs.writeFile(path.join(target,'items-registry.json'),JSON.stringify(registry))
  await run('powershell.exe',['-NoProfile','-ExecutionPolicy','Bypass','-File',path.join(__dirname,'install-map-player-textures.ps1'),'-ClientJar',clientJar,'-Destination',target],{windowsHide:true})
  const settingsFile=path.join(webroot,'settings.json'),settings=JSON.parse(await fs.readFile(settingsFile))
  settings.resolutionDefault=0.5
  settings.scripts=[...(settings.scripts||[]).filter(s=>!s.includes('bluemap-player-models/')),
    'assets/bluemap-player-models/player-models.js','assets/bluemap-player-models/player-labels.js']
  await fs.writeFile(settingsFile,JSON.stringify(settings))
  return {target,items:Object.keys(registry).length}
}
if(require.main===module) {
  const [webroot,clientJar]=process.argv.slice(2)
  if(!webroot||!clientJar)throw Error('Usage: node scripts/install-map-player-models.js <BlueMap webroot> <1.21.11 client jar>')
  install(webroot,clientJar).then(console.log).catch(error=>{console.error(error);process.exitCode=1})
}
module.exports={adapt,install,hashes,syncPlayerLabel}
