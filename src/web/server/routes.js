/**
 * Register all API routes on the Express app.
 */

function registerRoutes(app, manager, loadConfig) {

  // ---- Status ----
  app.get('/api/status', (req, res) => {
    res.json({
      agents: manager.getStatus(),
      uptimeSec: Math.floor(process.uptime())
    })
  })

  // ---- Reload config ----
  app.post('/api/reload', (req, res) => {
    const nextConfig = loadConfig()
    manager.reload(nextConfig)
    res.json({ ok: true })
  })

  // ---- Connect agent ----
  app.post('/api/agents/:name/connect', (req, res) => {
    const name = decodeURIComponent(req.params.name)
    const ok = manager.connectAgent(name)
    res.status(ok ? 200 : 404).json({ ok })
  })

  // ---- Disconnect agent ----
  app.post('/api/agents/:name/disconnect', (req, res) => {
    const name = decodeURIComponent(req.params.name)
    const ok = manager.disconnectAgent(name)
    res.status(ok ? 200 : 404).json({ ok })
  })

  // ---- Execute command ----
  app.post('/api/agents/:name/command', async (req, res) => {
    const name = decodeURIComponent(req.params.name)
    const command = (req.body.command || '').trim()

    if (!command) {
      return res.status(400).json({ ok: false, error: 'Missing command' })
    }

    const result = await manager.sendCommand(name, command)
    res.status(result.ok ? 200 : 400).json(result)
  })

  // ---- 404 catch-all ----
  app.use((req, res) => {
    res.status(404).json({ ok: false, error: 'Not found' })
  })
}

module.exports = { registerRoutes }
