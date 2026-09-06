# AzureQL v2.3.0 — What’s Changed / 更新内容

## 中文

### 内置代码编辑器与语法高亮

- 使用 Sora Editor 为普通脚本提供原生查看与编辑体验，支持行号、块引导线和增量语法高亮。
- 按扩展名自动识别 Python、JavaScript、TypeScript、Shell、JSON 与 YAML，也可在标题栏手动切换语言或纯文本。
- 未知类型安全降级为纯文本；大文件继续使用 8 KiB 分段预览，避免完整脚本进入编辑器布局。
- 编辑保存继续沿用既有的缓存复用、服务端版本/哈希冲突检查、上传复核和待上传草稿恢复链路。

### WebDAV 与 S3 网络备份

- “数据备份与恢复”更名为“备份与恢复”，本机导出入口更名为“导出到本机存储”。
- 新增独立“网络存储设置”子页，支持 WebDAV 和 S3 兼容存储；两者均已配置时，导出前明确选择目标。
- WebDAV 支持连接测试、逐级创建目录和流式上传；S3 支持 AWS SigV4、自定义端点、路径样式、自动区域重签和条件写入。
- WebDAV 密码及 S3 Access Key / Secret Key 使用 Android Keystore 加密；上传不携带青龙 Token，并保留后台进度、取消、重试、通知与脱敏错误分类。
- “选择导出内容”默认折叠，仅显示基础设置、配置文件和脚本文件，可展开查看全部模块。

### MCP 局域网与授权控制

- MCP 服务默认仍仅本机可访问，可在服务停止时显式开启“局域网可访问”；设置页默认展示首选局域网 IPv4，其他 VPN/IPv6 地址按需展开。
- 局域网模式继续执行 Host/Origin 校验、Bearer Token 鉴权、请求体/并发/速率限制和本地脱敏审计。当前为明文 HTTP，只适合可信局域网，不应暴露到公网。
- 已授予“受控写入与执行”的 Agent 可单独开启“静默允许写入与执行”，仅跳过逐次确认；账户绑定、Scope、参数/路径上限、串行化、幂等与脚本冲突保护保持不变。
- 开启高风险选项前需要设备身份验证；撤销受控权限会同步撤销静默授权。

### mTLS 连接维护

- 启用客户端证书的网络客户端最长复用 12 小时，之后在下一次 API 或 WebSocket 建连前重建 SSLContext，并自动复用当前账户的 PKCS#12、证书密码和私有 CA 完成完整握手。
- 普通非 mTLS 连接继续长期复用；该机制不静默重放账号密码，也不会把真正的 Token 过期、DNS 故障或证书失效隐藏为续连成功。
- 此路径需要等待会话自然过期，跨日实机验证仍在进行，不计作已通过验收。

### 验证与兼容性

- 2.3.0 发布前已通过 Debug APK 构建、全部 Debug 单元测试、Android lint 与备份模块 AndroidTest 源码编译，失败任务为 0。
- WebDAV 真实服务上传已通过；S3 模块已覆盖签名、上传、区域重试和输入校验，真实 AWS S3 / R2 / MinIO 仍待专项验证。
- Sora 编辑器已在 Motorola XT2551-3（Android 16）验证语言切换、输入法、退出和只读显示；脚本实际保存/冲突/断网仍沿用既有链路并继续观察。
- MCP 局域网监听与未授权拒绝已在 Android 16 实机验证；服务默认设置仍为仅本机访问。
- 支持 Android 12（API 31）及以上，青龙 v2.17 及以上。

## English

### Built-in code editor and syntax highlighting

- Added a native Sora Editor experience for regular scripts, including line numbers, block guides, and incremental syntax highlighting.
- Language mode is selected from the file extension for Python, JavaScript, TypeScript, Shell, JSON, and YAML, with manual language and plain-text overrides.
- Unknown files safely fall back to plain text. Large files keep the existing 8 KiB paged preview instead of entering the full editor layout.
- Saving continues to use the existing cache reuse, server version/hash conflict checks, upload verification, and pending-draft recovery flow.

### WebDAV and S3 network backups

- Renamed “Data backup & restore” to “Backup & restore” and clarified the local destination as “Export to local storage”.
- Added a dedicated Network Storage Settings page for WebDAV and S3-compatible storage. When both are configured, the destination is selected explicitly before export.
- WebDAV supports connection tests, recursive directory creation, and streaming uploads. S3 supports AWS SigV4, custom endpoints, path-style access, automatic region re-signing, and conditional writes.
- WebDAV passwords and S3 access/secret keys are encrypted with Android Keystore. Upload requests never carry the QingLong token and retain background progress, cancellation, retry, notifications, and sanitized error reporting.
- The export-content list is collapsed by default and initially shows Base settings, Configuration files, and Scripts, with an option to reveal every module.

### MCP local-network access and authorization controls

- MCP remains loopback-only by default and can be explicitly exposed to a trusted local network while the service is stopped. The settings page shows the preferred LAN IPv4 first and keeps VPN/IPv6 endpoints collapsed.
- Local-network mode retains Host/Origin validation, Bearer-token authentication, body/concurrency/rate limits, and sanitized local auditing. Transport is currently cleartext HTTP and must not be exposed to untrusted or public networks.
- Agents with Controlled Writes & Execution can optionally enable silent approval. This skips only per-operation confirmation; account binding, scopes, parameter/path limits, serialization, idempotency, and script conflict protection remain enforced.
- Device authentication is required before enabling either high-risk option, and revoking controlled access also revokes silent approval.

### mTLS connection maintenance

- mTLS clients are rotated after at most 12 hours. Before the next API or WebSocket connection, AzureQL rebuilds the SSLContext and automatically reuses the current account’s PKCS#12 certificate, certificate password, and private CA for a full handshake.
- Non-mTLS clients remain reusable without periodic rotation. The mechanism does not replay account passwords or disguise genuine token expiry, DNS failures, or invalid certificates.
- Cross-day device validation remains in progress because the session must expire naturally; it is not reported as a completed acceptance test in this release.

### Validation and compatibility

- The 2.3.0 pre-release run passed the Debug APK build, all Debug unit tests, Android lint, and compilation of the backup module’s AndroidTest sources with zero failed tasks.
- Upload to a real WebDAV service passed. S3 signing, upload, region retry, and validation are covered by module tests; real AWS S3, R2, and MinIO remain pending.
- Sora language switching, IME behavior, dismissal, and read-only rendering were checked on a Motorola XT2551-3 running Android 16. The existing save/conflict/offline flow remains under observation.
- MCP LAN listening and unauthorized rejection were checked on Android 16; the default remains loopback-only.
- Requires Android 12 (API 31) or later and QingLong v2.17 or later.

## Third-party software / 第三方组件

Sora Editor and the selected Monarch language definitions remain under their respective upstream licenses. Both licenses permit commercial use when their conditions are followed; neither is a non-commercial-only license. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Sora Editor 与所选 Monarch 语法定义继续适用各自的上游许可证。遵守对应条件时，两者均可用于商业用途，
并非“仅限非商业使用”。具体归属、源码、许可证入口和分发检查见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
