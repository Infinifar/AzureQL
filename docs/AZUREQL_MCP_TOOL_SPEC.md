# AzureQL MCP 首批工具规范

所有工具均针对当前登录且与 Agent 绑定的青龙账户。成功响应为 JSON 文本；失败使用 MCP `isError=true`，正文含稳定的 `code` 和安全消息。

| 工具 | Scope | 风险 | 输出限制 |
|---|---|---|---|
| `server_status` | `STATUS_READ` | LOW_READ | 任务汇总、平台、CPU/内存、运行/排队数 |
| `list_tasks` | `TASK_READ` | LOW_READ | 每页 1–100；不返回 command 或日志 |
| `list_scripts` | `SCRIPT_READ` | LOW_READ | 最多 100 个文件/目录元数据；不读正文 |
| `read_script` | `SCRIPT_READ` | SENSITIVE_READ | 相对路径；拒绝 `.`、`..`、绝对路径；最多 64 KiB；返回完整正文 SHA-256 与截断标志 |
| `list_dependencies` | `DEPENDENCY_READ` | LOW_READ | 最多 100 条；不返回安装日志 |
| `list_envs` | `ENV_READ_METADATA` | SENSITIVE_READ | 最多 100 条；`values_included=false`，每项 `value_masked=true` |

## 通用错误码

- `UNAUTHORIZED`：缺失或无效 Agent Token。
- `HOST_OR_ORIGIN_REJECTED`：Host/Origin 非 loopback。
- `ACCOUNT_NOT_ALLOWED`：Agent 未绑定当前账户。
- `AUTH_RATE_LIMITED` / `RATE_LIMITED`：鉴权或请求限流。
- `REQUEST_TOO_LARGE`：请求体超过 1 MiB。
- `SCOPE_DENIED`：Agent 缺少工具 Scope。
- `INVALID_ARGUMENT`：路径或参数不满足约束。
- `QINGLONG_UNAVAILABLE`：当前青龙服务端无法完成请求。
- `INTERNAL_ERROR`：已隐藏内部实现细节的未知错误。
