# AzureQL 本地 MCP 架构（Phase 2）

## 目标与边界

AzureQL 在 Android 前台服务中提供 Streamable HTTP MCP 端点：

```text
AI Agent
  -> adb forward / Android loopback
  -> Ktor HTTP 安全管线
  -> MCP Kotlin SDK 0.15 stateless session
  -> Tool Registry + Policy
  -> Operation Manager + phone confirmation + idempotency
  -> core:domain Repository
  -> 当前登录的青龙服务端
```

MCP 层只是协议适配器。工具不能直接访问 Retrofit、青龙 Token、SessionManager 或数据库实现；所有青龙数据必须经 `core:domain` Repository 获取。

## 生命周期

- 用户在设置页显式创建 Agent 并启动服务。
- Android 前台服务拥有协程作用域，Ktor 引擎由 `McpServerEngine` 管理。
- 只监听 `127.0.0.1:18765/mcp`，不支持局域网或公网绑定。
- 使用 SDK stateless Streamable HTTP：每个请求创建独立协议 Server 和该 Agent 可见的工具集合，请求结束即释放并发许可。
- 写操作的 HTTP 请求不等待用户交互：第一次调用只创建十分钟有效的待确认 Operation；手机端批准后，Agent 以相同参数、`idempotency_key` 和 `operation_id` 重试才执行。
- 停止前台服务时有界关闭 Ktor 引擎并释放端口。

## 模块职责

- `core:mcp/McpSecurity.kt`：Agent、Token 哈希、账户绑定、限流、审计与 HTTP 授权。
- `core:mcp/McpTools.kt` / `McpWriteTools.kt`：与 SDK 无关的工具定义、只读工具与 Phase 2 受控工具。
- `core:mcp/McpOperations.kt`：持久化确认状态机、每 Agent 单写并发、24 小时幂等回放与进程中断恢复。
- `core:mcp/KotlinSdkMcpServerEngine.kt`：Ktor/MCP SDK 适配、动态工具注册、结构化错误。
- `core:domain/ActiveAccountIdentityProvider.kt`：仅暴露非敏感的当前账户稳定标识。
- `feature:mcp`：本地身份验证、一次性 Token、Agent 名称/权限、待确认操作、脱敏审计及服务状态 UI。

## 受控操作状态机

```text
首次调用 + idempotency_key
  -> WAITING_CONFIRMATION
  -> 手机通知 / MCP 设置页
  -> 用户验证：APPROVED 或 DENIED
  -> Agent 用 operation_id 重试
  -> RUNNING
  -> SUCCEEDED / FAILED
  -> 相同请求重试直接回放结果，不再次调用青龙
```

- 待确认与已批准状态十分钟过期；结果保留 24 小时，最多 200 条。
- 只持久化请求 SHA-256、目标摘要和脱敏结果，不保存脚本正文、环境变量值或 Bearer Token。
- App 在 `RUNNING` 状态被终止时，重启后把 Operation 标记为 `PROCESS_INTERRUPTED`，不会盲目重放。
- 同一个 Agent 同时最多执行一个写 Operation；普通 MCP 请求仍沿用每 Agent 四并发限制。

## 后续扩展顺序

1. Phase 2 实机验证全部受控工具、通知、批准/拒绝和重试回放。
2. 增加审计导出与更细粒度的单 Scope 权限编辑。
3. 评估 MCP Tasks Extension 与后台 Operation 进度桥接。
4. 任意 Shell、删除、配置文件写入和 HTTP 代理仍不开放。
