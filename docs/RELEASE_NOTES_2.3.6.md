# AzureQL v2.3.6 — What’s Changed / 更新内容

## 中文

客户端版本：**AzureQL 2.3.6**

服务端基线：**青龙 2.23.0**

### 脚本历史版本

- 脚本页可查看最近 20 个历史版本，对比内容并恢复指定版本。
- 恢复前会校验当前内容哈希；服务端内容已变更时阻止覆盖，本地存在未保存修改时也不会直接恢复。
- 普通 UTF-8 脚本保存会纳入服务端历史；超过 1 MiB 的脚本使用带哈希校验的原子保存，并明确提示本次不生成历史版本。

### 青龙 2.23.0 适配

- 系统日志使用 2.23.0 的有界 `limit` 请求，任务和订阅日志使用官方 `offset/nextOffset/total/truncated` 游标；旧版路由和客户端虚拟游标已移除。
- 任务和订阅编辑页补充 node-cron 宏、日期与星期的 OR 语义，以及按服务端时区处理夏令时的说明。
- 客户端现仅面向青龙 2.23.0，不再保留旧服务端兼容分支。

### 构建工具链

- 升级至 Android Gradle Plugin 9.4.1、Kotlin 2.4.21、KSP 2.3.12 和 Gradle 9.7.1。
- GitHub Actions 已验证 Debug 构建、单元测试、Android Lint 与 Compose UI 测试编译；正式 APK 在发布流水线中签名并校验。

> 本版本的青龙 2.23.0 真实服务端与实机验收将在下载正式 APK 后进行。

## English

Client version: **AzureQL 2.3.6**

Server baseline: **QingLong 2.23.0**

### Script version history

- Browse the latest 20 script versions, compare their contents, and restore a selected version.
- Restore requests verify the current content hash to prevent overwriting concurrent changes. Restoring is also blocked while unsaved local edits exist.
- Regular UTF-8 script saves participate in server-side history. Scripts larger than 1 MiB use an atomic hash-checked save and clearly report that no history entry was created.

### QingLong 2.23.0 support

- System logs use the bounded 2.23.0 `limit` request, while task and subscription logs use the official `offset/nextOffset/total/truncated` cursor contract. Legacy routes and client-generated cursors have been removed.
- Task and subscription editors document node-cron macros, OR semantics for day-of-month and day-of-week, and daylight-saving behavior based on the server time zone.
- The client now targets QingLong 2.23.0 only and no longer carries compatibility branches for older server releases.

### Build toolchain

- Updated to Android Gradle Plugin 9.4.1, Kotlin 2.4.21, KSP 2.3.12, and Gradle 9.7.1.
- GitHub Actions validates the Debug build, unit tests, Android Lint, and Compose UI test compilation. The release pipeline signs and verifies the production APK.

> Live QingLong 2.23.0 server and device acceptance testing will follow using the downloadable production APK.

## Third-party software / 第三方组件

Third-party components remain under their respective upstream licenses. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

第三方组件继续适用各自的上游许可证，详见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

Full Changelog: https://github.com/Infinifar/AzureQL/compare/v2.3.5...v2.3.6
