# AzureQL 本地 MCP 架构

## 目标与边界

AzureQL 在用户主动启动的 Android 前台服务中提供 Streamable HTTP MCP 端点：

```text
AI Agent
  -> 本机回环或可信局域网
  -> Ktor CIO HTTP 安全管线
  -> MCP Kotlin SDK 0.15 无状态会话
  -> Tool Registry + Policy
  -> Operation Manager + 幂等 + 确认策略
  -> core:domain Repository
  -> 当前登录的青龙服务端
```

MCP 只负责协议适配。工具不能直接访问 Retrofit、青龙 Token、`SessionManager` 或数据库实现；所有青龙操作必须经过 `core:domain` Repository。

## 服务生命周期

- 用户在设置中创建 Agent 后手动启动服务；进程结束后不会自行重启。
- 默认监听 `127.0.0.1:18765/mcp`。
- 用户停止服务后可显式开启“局域网可访问”，此时监听全部接口，但只接受应用枚举出的设备地址和匹配的 Host/Origin。
- 服务停止时有界关闭 CIO 引擎、传输协程和端口。
- SDK 使用 stateless Streamable HTTP：每次请求创建并关闭独立协议会话，不保留服务端 MCP Session。

局域网模式仍是明文 HTTP Bearer Token，只适合可信网络。TLS 或安全隧道尚未实现。

## 模块职责

- `core:mcp/McpSecurity.kt`：Agent、Token 哈希、账户绑定、Scope、网络校验、限流和审计。
- `core:mcp/McpTools.kt` / `McpWriteTools.kt`：工具定义、只读工具与受控工具。
- `core:mcp/McpOperations.kt`：确认状态机、写入串行化、幂等回放和进程中断恢复。
- `core:mcp/KotlinSdkMcpServerEngine.kt`：CIO、MCP SDK、动态工具注册和结构化错误适配。
- `core:domain/ActiveAccountIdentityProvider.kt`：只暴露当前账户的非敏感稳定标识。
- `feature:mcp`：服务设置、设备身份验证、一次性 Token、Agent 权限、待确认操作和脱敏审计 UI。

## Agent 与权限

- Agent Token 使用 256-bit 随机数，只在创建时显示一次，磁盘仅保存 SHA-256 哈希。
- Agent 绑定创建时的青龙账户；切换账户后原 Agent 不能访问新账户。
- 默认 Agent 只有只读 Scope。受控写入与执行必须单独通过设备身份验证开启。
- 关闭受控权限时同步关闭该 Agent 的静默授权。

## 受控操作

默认流程：

```text
首次调用 + idempotency_key
  -> WAITING_CONFIRMATION
  -> 应用通知 / MCP 设置页
  -> 用户验证：APPROVED 或 DENIED
  -> Agent 以相同参数、idempotency_key 和 operation_id 重试
  -> RUNNING
  -> SUCCEEDED / FAILED
```

已显式开启静默授权的 Agent，对当前注册的 `CONTROLLED_WRITE` / `EXECUTION` 工具可在首次请求时直接进入 `RUNNING`。静默授权不会绕过账户绑定、Scope、参数与路径限制、幂等、单 Agent 写入串行化、脚本冲突检查或审计，也不会自动批准 `HIGH_RISK` 工具。

- 待确认或已批准 Operation 十分钟后过期。
- 结果保留 24 小时，最多保存 200 条；相同请求只回放结果，不重复调用青龙。
- 只持久化请求哈希、脱敏目标和脱敏结果，不保存 Token、环境变量值或脚本正文。
- App 在 `RUNNING` 时被终止，重启后将该 Operation 标记为 `PROCESS_INTERRUPTED`，不会自动重放。
- 同一 Agent 同时最多执行一个写 Operation；普通 MCP 请求最多四并发。

## 后续工作

1. 完成账户切换和十分钟自然过期的补充实机验收。
2. 增加审计导出和更细粒度的单 Scope 权限编辑。
3. 等待官方 Kotlin SDK 稳定支持新版 MCP Tasks 扩展，再评估与后台 Operation 的进度桥接。
4. 为局域网模式提供 TLS 或等效安全隧道。

任意 Shell、任意 HTTP、未建模删除、配置文件写入、备份恢复和凭据访问不在扩展计划内。
