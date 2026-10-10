# AzureQL v2.3.5 — What’s Changed / 更新内容

## 中文

客户端版本：**AzureQL 2.3.5**

服务端兼容目标：**青龙 2.22**

### 功能：支持青龙服务端版本 2.22

- 配置文件详情接口迁移到 v2.22.0 的 `/api/configs/detail?path=...`，避免继续调用已经废弃的旧路径。
- 认证请求收到 HTTP 401 时只清除失效 Token，继续保留用户授权保存的密码、Client Secret 与 mTLS 证书材料，便于重新认证。
- 修改密码或 2FA 状态成功后主动使旧会话失效，避免继续使用服务端已经撤销的 Token。
- 备份恢复健康检查现在可识别 HTTP 503，并显示调度服务暂不可用的明确提示。

### 日志可靠性

- 通用日志读取适配 v2.22.0 的有界 `offset`、`limit` 与 `tail` 响应。
- 任务从运行态进入终态后，会继续按游标排空全部剩余日志分页，避免遗漏最后几页或重复追加完成信息。
- 长系统日志滚动和正文顶部下拉刷新已在 Android 16 真机上通过验证，刷新期间不会误唤起输入法。

### 首页与通知渠道

- 首页“今日成功”和“今日失败”现可查看对应任务、命令、执行次数及已删除状态。
- 通知设置加入青龙 v2.22.0 的 WPUSH 渠道字段，支持 API Key、Channel 与 Topic 广播编码；敏感字段继续默认遮罩且不落盘。

### 验证与兼容性

- 全项目 53 个测试套件、300 项单元测试全部通过，Android Lint 与 Debug APK 构建成功。
- 已在 Motorola XT2551-3（Android 16 / API 36）连接青龙 v2.22.0 完成覆盖安装、会话保留、首页明细、已完成任务日志和系统日志冒烟；未发现新增崩溃或 ANR。
- WPUSH 真实发送、会改变认证状态的 401/密码/2FA 场景，以及恢复期间 HTTP 503 仍需在具备专用凭据或测试实例时验证。
- 支持 Android 12（API 31）及以上；本版本重点适配青龙 v2.22.0，并继续保留既有兼容路径。

## English

Client version: **AzureQL 2.3.5**

Server compatibility target: **QingLong 2.22**

### Feature: QingLong server 2.22 support

- Migrated config-file detail requests to the v2.22.0 `/api/configs/detail?path=...` endpoint instead of the retired legacy path.
- An HTTP 401 on an authenticated request now clears only the expired token while retaining user-authorized saved passwords, client secrets, and mTLS certificate material for re-authentication.
- Successful password or 2FA changes explicitly invalidate the previous session so a server-revoked token is never reused.
- Backup-restore health checks now recognize HTTP 503 and present an actionable scheduler-unavailable message.

### Reliable logs

- General log reads now support the bounded v2.22.0 `offset`, `limit`, and `tail` response contract.
- When a task reaches a terminal state, AzureQL continues draining every remaining cursor page, preventing truncated tails and duplicated completion output.
- Long system-log scrolling and pull-to-refresh were validated on an Android 16 device without unexpectedly opening the keyboard.

### Dashboard and notification providers

- The dashboard’s Today Success and Today Failure counters now open task details with names, commands, run counts, and deleted-task state.
- Notification settings add QingLong v2.22.0 WPUSH fields for API Key, Channel, and Topic broadcast encoding while preserving masked, non-persistent secret handling.

### Validation and compatibility

- All 53 test suites and 300 unit tests passed, together with Android Lint and the Debug APK build.
- In-place update, preserved session state, dashboard details, completed-task logs, and system logs were smoke-tested on a Motorola XT2551-3 running Android 16 / API 36 against QingLong v2.22.0, with no new crash or ANR.
- Live WPUSH delivery, destructive authentication scenarios involving 401/password/2FA changes, and HTTP 503 during restore still require dedicated credentials or a disposable test instance.
- Requires Android 12 (API 31) or later. This release targets QingLong v2.22.0 while retaining the existing compatibility paths.

## Third-party software / 第三方组件

Third-party components remain under their respective upstream licenses. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

第三方组件继续适用各自的上游许可证，详见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

Full Changelog: https://github.com/Infinifar/AzureQL/compare/v2.3.4...v2.3.5
