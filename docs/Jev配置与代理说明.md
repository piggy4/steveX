# Jev 配置与代理说明

## 结论

截至 2026-09-23，没有查到阿里云百炼、腾讯云或其他国内主流平台直接托管 Jev。可核验的入口有：

- TypeSafe 官方：`https://api.typesafe.ai/v1/systemone`
- Cloudflare Workers AI：模型 `typesafe/jev`
- Vercel AI Gateway：模型 `typesafe-ai/jev`
- OpenRouter Decisions API：模型 `typesafe/jev-1.13`

项目默认使用 TypeSafe 官方接口。Cloudflare 是备用网关，不是国内代理。搜索结果中的独立“Jev 代理站”缺少足够的主体、数据处理和上游证明，不作为默认配置。

OpenRouter 是另一条已核验的第三方网关，但同样不是国内服务。它为 Jev 提供专用的 `POST https://openrouter.ai/api/alpha/decisions` 接口，不能使用常规的 `/chat/completions`。

## 官方接口配置

PowerShell 当前会话：

```powershell
$env:JEV_API_KEY = "你的 TypeSafe API Key"
$env:JEV_PROVIDER = "typesafe"
npm run jev:check
```

持久化到当前 Windows 用户（新终端生效）：

```powershell
[Environment]::SetEnvironmentVariable("JEV_API_KEY", "你的 TypeSafe API Key", "User")
[Environment]::SetEnvironmentVariable("JEV_PROVIDER", "typesafe", "User")
```

不要把 Key 填入 `configs/defaults/app.json` 或提交到 Git。

## Cloudflare 备用入口

```powershell
$env:JEV_PROVIDER = "cloudflare"
$env:CLOUDFLARE_ACCOUNT_ID = "你的 Account ID"
$env:CLOUDFLARE_API_TOKEN = "你的 Workers AI Token"
npm run jev:check
```

Cloudflare 请求使用其官方包装格式，代码会自动解包 `result`。`JEV_BASE_URL` 仍可覆盖端点，供自建的可信转发服务使用：

```powershell
$env:JEV_PROVIDER = "typesafe"
$env:JEV_BASE_URL = "https://你的可信网关/v1/systemone"
```

兼容网关必须接受 TypeSafe 原生 `{ model, state, questions }` 请求并返回含 `answers` 的响应。不要使用只提供 OpenAI `/chat/completions` 的代理冒充 Jev，因为两者的输入和概率语义不同。

## OpenRouter 入口

```powershell
$env:JEV_PROVIDER = "openrouter"
$env:OPENROUTER_API_KEY = "在 OpenRouter 控制台生成的 Key"
$env:JEV_MODEL = "typesafe/jev-1.13"
npm run jev:check
```

Windows 用户级持久化配置（关闭并重新打开终端后生效）：

```powershell
[Environment]::SetEnvironmentVariable("JEV_PROVIDER", "openrouter", "User")
[Environment]::SetEnvironmentVariable("OPENROUTER_API_KEY", "在 OpenRouter 控制台生成的 Key", "User")
[Environment]::SetEnvironmentVariable("JEV_MODEL", "typesafe/jev-1.13", "User")
```

OpenRouter 账户的模型访问限制由其控制台决定；运行 `npm run jev:check` 是确认该 Key 对 Jev 可用的唯一依据。

## 配置项

| 环境变量 | 用途 | 默认值 |
| --- | --- | --- |
| `JEV_PROVIDER` | `typesafe`、`cloudflare` 或 `openrouter` | `typesafe` |
| `OPENROUTER_API_KEY` | OpenRouter Decisions API 密钥 | 无 |
| `JEV_API_KEY` | TypeSafe 官方密钥 | 无 |
| `JEV_BASE_URL` | 覆盖请求端点 | 官方端点 |
| `JEV_MODEL` | 模型版本 | 官方 `jev-1.13.0`；Cloudflare `typesafe/jev` |
| `JEV_TIMEOUT_MS` | 超时毫秒数 | `10000` |
| `CLOUDFLARE_ACCOUNT_ID` | Cloudflare 账户 ID | 无 |
| `CLOUDFLARE_API_TOKEN` | Cloudflare Workers AI Token | 无 |

`configs/defaults/app.json` 中 `decision.enabled` 默认为 `false`。当前提交只完成客户端与连通性验证，不会改变 agent 的动作；接入行为树时再由执行器显式启用。

## 验证

无需 Key 的离线测试：

```powershell
npm test
```

有 Key 后的真实调用：

```powershell
npm run jev:check
```

成功时输出 `model`、`answers` 和 `usage`。失败时只输出错误，不打印密钥。

## 参考

- TypeSafe Jev 文档：<https://docs.typesafe.ai/>
- Cloudflare Jev 模型文档：<https://developers.cloudflare.com/ai/models/typesafe/jev/>
- Vercel Jev 模型页：<https://vercel.com/ai-gateway/models/jev>
- OpenRouter Jev 模型页：<https://openrouter.ai/typesafe/jev-1.13/api>
