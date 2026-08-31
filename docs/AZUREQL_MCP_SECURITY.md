# AzureQL MCP 安全模型（Phase 2）

## 资产与威胁

受保护资产包括青龙访问令牌、环境变量值、脚本内容、任务日志和控制能力。主要威胁是本机恶意应用、浏览器 DNS rebinding、Token 泄露、越权工具调用、超大请求/响应耗尽内存，以及切换账户后的跨账户读取。

## 默认策略

- 默认拒绝：无 Bearer Agent Token 返回 `401`；Scope 或账户不符返回 `403`。
- Token 使用 256-bit CSPRNG 生成，带版本前缀，只在创建时显示一次。
- 磁盘只保存 SHA-256 Token 哈希；使用常量时间比较。
- Agent 创建前要求 Android 生物识别或设备锁屏凭据。
- Agent 显示名称可修改；改名不轮换 Token、不扩大 Scope，也不改变账户绑定。
- 每个 Agent 绑定创建时的当前青龙账户；切换账户后不会继承访问权。
- Agent 默认仍只有 `LOW_READ` 和 `SENSITIVE_READ`；受控写入/执行权限必须按 Agent 经设备身份验证开启。
- 环境变量仅返回元数据，永不返回 value；青龙 Token 永不进入 MCP 响应或审计。

## 网络与资源限制

- 固定绑定 `127.0.0.1`。
- 校验 `Host`，仅接受 localhost/IPv4/IPv6 loopback。
- `Origin` 缺失时允许原生客户端；存在时仅接受 loopback Origin，阻止 DNS rebinding。
- 请求体上限 1 MiB。
- 每 Agent 最多 4 个并发请求、每分钟最多 60 个请求；失败鉴权按来源限速。
- 列表最多 100 条；脚本正文最多返回 64 KiB UTF-8，且不会截断半个码点。

## 审计

所有鉴权失败和工具调用写入应用私有的本地环形审计记录，最多 500 条、保留 30 天。记录 Agent、工具、风险、结果、耗时和脱敏目标摘要，不记录 Token、环境变量值、脚本正文、参数全文或响应全文。

用户批准和拒绝也分别记录为 `USER_APPROVED` / `USER_DENIED`。MCP 设置页仅显示最近 20 条，
可由用户确认后清除全部本地审计；审计导出仍未开放。

## 写入与执行

- 所有 Phase 2 工具要求相应 Agent Scope，并逐次进行手机端设备身份验证确认。
- 每个请求必须提供 8–128 字符的 `idempotency_key`；持久化时只保存 Agent 绑定后的 SHA-256。
- 重试必须携带匹配的 `operation_id` 且业务参数哈希完全一致，否则返回 `IDEMPOTENCY_CONFLICT` 或 `OPERATION_NOT_FOUND`。
- 相同已完成请求只回放脱敏结果，不再次调用 Repository；同一 Agent 同时只运行一个写操作。
- 脚本写入最多 512 KiB，路径拒绝绝对路径、反斜杠、NUL、`.` 和 `..`；更新必须通过 `expected_sha256` 冲突检查，MCP 不提供 `force`。
- 环境变量值只传给 `EnvRepository`，不写入 Operation、响应或审计；响应只标记 `value_stored=true`。
- 依赖安装和任务运行的 `SUCCEEDED` 表示青龙已接受提交，不伪装成长任务已经运行完成。

## 暂不开放

删除、配置修改、环境变量值读取、任意 HTTP 代理、任意 Shell、脚本直接执行、备份恢复和青龙凭据访问仍不注册为工具。Phase 2 只允许已列明字段的 Repository 操作，不能通过额外参数绕过边界。
