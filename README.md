# steveX
一个基于 Mineflayer 的 Minecraft LLM Agent 框架，支持多 Agent 管理、77 个命令、Web 面板实时监控。

> **项目定位：** 构建一套 **以"版本控制"与"群体进化"为核心的多智能体系统架构**，在 Minecraft 虚拟环境中探索 AI Agent 的前沿技术。

---

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置

编辑 `configs/defaults/app.json`

### 3. 启动

```bash
npm start
```

---

## 功能特性

### 命令系统（77 个命令）

- **Actions（57 个）** — 动作指令：移动、挖掘、建造、交互、合成等
- **Queries（14 个）** — 环境查询：位置、血量、背包、附近方块/实体等
- **Creative（6 个）** — 创造模式：飞行、物品栏编辑等

所有命令自动加载，详见 [`src/commands/`](src/commands/)。

### 多 Agent 管理

支持同时管理多个 Minecraft 机器人，每个 Agent 独立配置 Minecraft 连接和 LLM 参数。

### Web 面板

打开 http://localhost:8090 查看状态、启停 Agent、发送聊天命令和查看实时日志。

---

## 多 Agent 配置示例

```json
{
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
}
```


## 技术栈

| 技术 | 用途 |
|------|------|
| Node.js ≥18 | 运行时 |
| CommonJS | 模块系统 |
| mineflayer ^4.37.1 | Minecraft 机器人库 |
| mineflayer-pathfinder ^2.4.5 | 寻路插件 |
| Express ^5.2.1 | Web 框架 |
| ws ^8.20.1 | WebSocket 实时推送 |
| DeepSeek API | LLM 调用 |
