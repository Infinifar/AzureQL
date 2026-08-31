# AzureQL 本地 MCP 架构（Phase 1）

## 目标与边界

AzureQL 在 Android 前台服务中提供 Streamable HTTP MCP 端点：

```text
AI Agent
  -> adb forward / Android loopback
  -> Ktor HTTP 安全管线
  -> MCP Kotlin SDK 0.15 stateless session
  -> Tool Registry + Policy
  -> core:domain Repository
  -> 当前登录的青龙服务端
```

MCP 层只是协议适配器。工具不能直接访问 Retrofit、青龙 Token、SessionManager 或数据库实现；所有青龙数据必须经 `core:domain` Repository 获取。

## 生命周期

- 用户在设置页显式创建 Agent 并启动服务。
- Android 前台服务拥有协程作用域，Ktor 引擎由 `McpServerEngine` 管理。
- 只监听 `127.0.0.1:18765/mcp`，不支持局域网或公网绑定。
- 使用 SDK stateless Streamable HTTP：每个请求创建独立协议 Server 和该 Agent 可见的工具集合，请求结束即释放并发许可。
- 停止前台服务时有界关闭 Ktor 引擎并释放端口。

## 模块职责

- `core:mcp/McpSecurity.kt`：Agent、Token 哈希、账户绑定、限流、审计与 HTTP 授权。
- `core:mcp/McpTools.kt`：与 SDK 无关的工具定义、策略元数据及首批只读工具。
- `core:mcp/KotlinSdkMcpServerEngine.kt`：Ktor/MCP SDK 适配、动态工具注册、结构化错误。
- `core:domain/ActiveAccountIdentityProvider.kt`：仅暴露非敏感的当前账户稳定标识。
- `feature:mcp`：本地身份验证、一次性 Token 展示、Agent 撤销及服务状态 UI。

## 后续扩展顺序

1. 为写操作建立待确认队列、幂等键、预览与结果回执。
2. 增加审计查看与导出 UI。
3. 实现带 SHA-256 冲突保护的脚本创建与更新。
4. 最后评估任务执行；任意 Shell、配置文件写入和代理工具仍不开放。
