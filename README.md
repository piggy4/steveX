# steveX

A minimal Mineflayer-based LLM agent skeleton for Minecraft.

## Quick start

1. Install dependencies:

   npm install

2. Configure the agents (defaults are in configs/defaults/app.json). You can set apiKey there or override by env vars (applies to all agents):

   MC_HOST, MC_PORT, MC_USERNAME, MC_AUTH, MC_VERSION
   DEEPSEEK_API_KEY, DEEPSEEK_BASE_URL, DEEPSEEK_MODEL

3. Run:

   npm start

## Reload config

While the process is running, type `reload` in the terminal and press Enter to apply the latest config without restarting.

## Web panel

Enable in configs/defaults/app.json:

```json
"web": { "host": "localhost", "port": 8090 }
```

Open http://localhost:8090 to view status and start/stop or send chat commands.

## Multi-agent

Add multiple agents in configs/defaults/app.json:

```json
"agents": [
   {
      "name": "steveX-1",
         "minecraft": {
            "host": "localhost",
            "port": 25565,
            "username": "steveX-1",
            "auth": "offline",
            "version": false
         },
         "llm": {
            "provider": "deepseek",
            "apiKey": "YOUR_KEY_1",
            "model": "deepseek-v4-flash",
            "baseUrl": "https://api.deepseek.com/v1/chat/completions",
            "timeoutMs": 20000
         }
   },
   {
      "name": "steveX-2",
         "minecraft": {
            "host": "localhost",
            "port": 25565,
            "username": "steveX-2",
            "auth": "offline",
            "version": false
         },
         "llm": {
            "provider": "deepseek",
            "apiKey": "YOUR_KEY_2",
            "model": "deepseek-v4-flash",
            "baseUrl": "https://api.deepseek.com/v1/chat/completions",
            "timeoutMs": 20000
         }
   }
]
```

## Notes

- The initial agent only logs in and responds to chat using the LLM.
- DeepSeek API is called with an OpenAI-compatible Chat Completions request.
- Pathfinder commands: `goto <x> <y> <z>` and `stop`.
