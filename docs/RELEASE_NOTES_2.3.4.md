# AzureQL v2.3.4 — What’s Changed / 更新内容

## 中文

### 网络备份与恢复

- WebDAV 与 S3 网络存储现在支持浏览远端备份、选择归档并流式下载。
- 下载过程限制文件类型、远端目录边界和最大字节数；完成后复用现有归档校验、二次覆盖确认、服务恢复和重新登录流程。
- WebDAV 列表解析限制 XML 大小并拒绝 DTD/外部实体；S3 支持签名的 `ListObjectsV2` 分页和对象下载。
- 取消、失败或完成后会清理本地临时文件，传输进度可在页面切换后继续观察。

### 网络存储配置保留

- 修复覆盖安装后 WebDAV/S3 设置可能看似清空的问题。
- 网络存储设置改用规范化、版本化的账户作用域，并只迁移同一账户的旧配置，避免跨账户串用。
- 配置异步加载完成前显示明确加载状态，不再短暂展示空白表单。

### English 本地化

- 补齐客户端业务页面、校验错误、空状态、Toast、Snackbar 和其他瞬时提示的 English 文案。
- 日期和时间按当前应用 Locale 使用系统格式；首页数字使用本地数字分组，动态任务、变量、标签和地址支持正确英文单复数。
- 服务端日志、脚本源码和通知正文保持原样，不对用户内容进行错误翻译。

### FCM 通知接收技术预览

- 加入默认关闭的 Firebase 初始化和受约束的 FCM data payload 接收器。
- 通知按服务器建立 Channel，锁屏默认隐藏正文；接收器不接受远端 Intent、URI、图标或任意富媒体地址。
- 新增独立的中继威胁模型与安全协议，服务账户私钥不会进入 APK 或仓库。
- **本版本尚未完成 FCM 实机验收、中继部署、设备配对、Token 注册或青龙 Webhook 自动配置；该能力当前不可视为端到端可用。**

### 验证与兼容性

- 用户已确认本轮除 FCM 外的功能通过实机测试。
- 网络存储与备份、本地化、设置、任务、环境变量、脚本和 MCP 相关单元测试通过，Debug APK 构建成功。
- GitHub Actions 将继续执行全量 JVM 测试、Android Lint、Compose 测试源码编译、正式签名 APK 构建和签名校验。
- 支持 Android 12（API 31）及以上、青龙 v2.17 及以上。

## English

### Network backup and restore

- WebDAV and S3 storage can now list remote backups, select an archive, and stream it to the device.
- Downloads enforce archive types, configured-directory boundaries, and byte limits, then reuse the existing validation, destructive-restore confirmation, service recovery, and re-authentication flow.
- WebDAV parsing limits XML size and rejects DTD/external entities. S3 uses signed paginated `ListObjectsV2` requests and signed object downloads.
- Local temporary files are removed after cancellation, failure, or completion, while transfer progress remains observable across navigation.

### Persistent network-storage settings

- Fixed WebDAV/S3 settings sometimes appearing empty after an in-place app update.
- Settings now use a normalized, versioned account scope and migrate only the matching account’s previous values, preventing cross-account reuse.
- The settings screen shows an explicit loading state until encrypted account settings are available instead of briefly rendering an empty form.

### Complete English code localization

- Expanded English coverage across business screens, validation errors, empty states, Toasts, Snackbars, and other transient messages owned by the app.
- Dates and times follow the active app locale, dashboard numbers use locale-aware grouping, and dynamic task, variable, label, and address counts use correct English singular/plural forms.
- Server logs, script source, and notification content remain unchanged so user-provided content is never mistranslated.

### FCM receiving technology preview

- Added default-off Firebase initialization and a constrained FCM data-payload receiver.
- Notifications use per-server channels and hide their body on the lock screen. The receiver does not accept remote intents, URIs, icons, or arbitrary media.
- Added a separate relay threat model and security protocol; Firebase service-account credentials never enter the APK or repository.
- **FCM device validation, relay deployment, device enrollment, token registration, and automatic QingLong Webhook configuration are not complete in this release. This is not yet an end-to-end notification service.**

### Validation and compatibility

- The user confirmed all changes in this release except FCM on a physical device.
- Unit tests passed for network storage/backup, localization, Settings, Tasks, Variables, Scripts, and MCP; the Debug APK also built successfully.
- GitHub Actions continues to run the complete JVM suite, Android Lint, Compose test-source compilation, signed Release APK construction, and signature verification.
- Requires Android 12 (API 31) or later and QingLong v2.17 or later.

## Third-party software / 第三方组件

Third-party components remain under their respective upstream licenses. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

第三方组件继续适用各自的上游许可证，详见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

Full Changelog: https://github.com/Infinifar/AzureQL/compare/v2.3.3...v2.3.4
