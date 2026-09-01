# AzureQL MCP Phase 2 工具规范

所有工具均针对当前登录且与 Agent 绑定的青龙账户。成功响应为 JSON 文本；失败使用 MCP `isError=true`，正文含稳定的 `code` 和安全消息。

| 工具 | Scope | 风险 | 输出限制 |
|---|---|---|---|
| `server_status` | `STATUS_READ` | LOW_READ | 任务汇总、平台、CPU/内存、运行/排队数 |
| `list_tasks` | `TASK_READ` | LOW_READ | 每页 1–100；不返回 command 或日志 |
| `list_scripts` | `SCRIPT_READ` | LOW_READ | 最多 100 个文件/目录元数据；不读正文 |
| `read_script` | `SCRIPT_READ` | SENSITIVE_READ | 相对路径；拒绝 `.`、`..`、绝对路径；最多 64 KiB；返回完整正文 SHA-256 与截断标志 |
| `list_dependencies` | `DEPENDENCY_READ` | LOW_READ | 最多 100 条；不返回安装日志 |
| `check_dependency` | `DEPENDENCY_READ` | LOW_READ | 精确匹配名称，可按类型过滤，返回是否存在及安装状态 |
| `list_envs` | `ENV_READ_METADATA` | SENSITIVE_READ | 最多 100 条；`values_included=false`，每项 `value_masked=true` |
| `list_logs` | `LOG_READ` | SENSITIVE_READ | 仅列举服务端日志树中的文件，最多 100 条，不返回正文 |
| `read_log_tail` | `LOG_READ` | SENSITIVE_READ | 路径必须存在于日志树；最多 1000 行、64 KiB UTF-8 尾部 |
| `get_task_log` | `LOG_READ` | SENSITIVE_READ | 正整数任务 ID；最多 1000 行、64 KiB UTF-8 尾部 |

## 受控工具

| 工具 | Scope | 风险 | 关键限制 |
|---|---|---|---|
| `get_operation` | `STATUS_READ` | LOW_READ | 只能读取当前 Agent 自己的 Operation |
| `create_script` | `SCRIPT_WRITE` | CONTROLLED_WRITE | 相对路径；UTF-8 正文最多 512 KiB；目标必须不存在 |
| `update_script` | `SCRIPT_WRITE` | CONTROLLED_WRITE | 正文最多 512 KiB；必须提供 `expected_sha256`；不允许 force |
| `run_task` | `TASK_EXECUTE` | EXECUTION | 单个正整数任务 ID；返回“已提交”而非伪造完成状态 |
| `stop_task` | `TASK_EXECUTE` | EXECUTION | 只能停止明确的青龙任务 ID |
| `install_dependency` | `DEPENDENCY_WRITE` | EXECUTION | 类型仅 `nodejs` / `python3` / `linux`；已存在时拒绝并提示重装 |
| `reinstall_dependency` | `DEPENDENCY_WRITE` | EXECUTION | 单个已存在依赖 ID |
| `create_env` | `ENV_WRITE` | CONTROLLED_WRITE | value 最多 64 KiB；响应、Operation、审计均不回显 value |
| `update_env` | `ENV_WRITE` | CONTROLLED_WRITE | 正整数 ID + 完整新 name/value；不读取或返回旧 value |
| `enable_env` | `ENV_WRITE` | CONTROLLED_WRITE | 单个已存在环境变量 ID |
| `disable_env` | `ENV_WRITE` | CONTROLLED_WRITE | 单个已存在环境变量 ID |
| `create_task` | `TASK_WRITE` | CONTROLLED_WRITE | 只接受青龙 2.21 已支持的显式字段 |
| `update_task` | `TASK_WRITE` | CONTROLLED_WRITE | 正整数 ID；按提供字段合并，至少提供一个可修改字段 |

除 `get_operation` 外，每个受控工具都要求：

```json
{
  "idempotency_key": "client-generated-stable-key",
  "operation_id": "op_... only after approval"
}
```

第一次调用不执行青龙写入，而是返回：

```json
{
  "operation_id": "op_...",
  "state": "waiting_confirmation",
  "confirmation_required": true,
  "next_action": "Ask the user to approve in AzureQL, then poll get_operation"
}
```

用户在手机端验证并批准后，`get_operation` 返回 `approved`。Agent 必须用原工具、完全相同的
业务参数与 `idempotency_key`，并加入返回的 `operation_id` 重试。已完成请求会回放原结果，
不会再次调用青龙。不同参数复用同一 key 返回 `IDEMPOTENCY_CONFLICT`。

`update_script` 的最小输入为：

```json
{
  "idempotency_key": "update-foo-20260831-1",
  "path": "scripts/foo.py",
  "content": "...",
  "expected_sha256": "<read_script sha256>"
}
```

`create_task` 支持 `name`、`command`、`schedule_type`、`schedule`、`extra_schedules`、`labels`、
`allow_multiple_instances`、`log_name`、`work_dir`、`task_before` 和 `task_after`；
`schedule_type` 仅为 `normal`、`once`、`boot`。`update_task` 额外要求 `id`，其余字段可选。

## 通用错误码

- `UNAUTHORIZED`：缺失或无效 Agent Token。
- `HOST_OR_ORIGIN_REJECTED`：Host/Origin 非 loopback。
- `ACCOUNT_NOT_ALLOWED`：Agent 未绑定当前账户。
- `AUTH_RATE_LIMITED` / `RATE_LIMITED`：鉴权或请求限流。
- `REQUEST_TOO_LARGE`：请求体超过 1 MiB。
- `SCOPE_DENIED`：Agent 缺少工具 Scope。
- `CONFIRMATION_DENIED` / `CONFIRMATION_EXPIRED`：手机端拒绝或十分钟内未批准。
- `OPERATION_NOT_FOUND` / `OPERATION_IN_PROGRESS`：Operation 不匹配或 Agent 已有写操作运行中。
- `IDEMPOTENCY_CONFLICT`：同一幂等键用于不同工具、账户或参数。
- `ALREADY_EXISTS`：脚本或依赖目标已存在。
- `SCRIPT_CONFLICT`：脚本 SHA-256 或服务端版本在写入前发生变化。
- `RESULT_TOO_LARGE`：脚本写入超过 512 KiB。
- `INVALID_ARGUMENT`：路径或参数不满足约束。
- `NOT_FOUND`：指定脚本或日志不在当前服务端的可访问列表中。
- `QINGLONG_UNAVAILABLE`：当前青龙服务端无法完成请求。
- `INTERNAL_ERROR`：已隐藏内部实现细节的未知错误。
