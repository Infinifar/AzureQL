# AzureQL v2.3.1 — What’s Changed / 更新内容

## 中文

### 设置内多账户与服务器管理

- 新增独立的“账户与服务器”页面，可在应用内预览、排序、编辑、删除和直接切换已保存账户；只有新增账户才进入完整登录页。
- 账户卡片展示脱敏服务器地址、别名、认证方式、TLS/mTLS 状态、最近使用时间和当前账户标记，不显示 Token、密码或 Client Secret。
- 支持密码、Client ID、2FA 与 mTLS 账户直接切换。候选账户认证成功后才替换当前会话；网络、TLS、证书或验证码失败时继续保留原账户。
- 当前账户删除前会先安全切换至其他账户；备份、恢复或网络导出仍在运行时会阻止可能造成跨账户数据混用的切换或删除。

### 账户内证书维护

- 可在账户编辑界面直接添加、替换或移除 PKCS#12 客户端证书和私有 CA，无需返回登录页重新创建账户。
- 新证书先原子复制到应用私有目录，证书密码继续使用 Android Keystore 加密保存。
- 当前账户保存 TLS 变更后自动使用候选配置重新认证；失败或取消 2FA 时恢复旧账户记录、旧证书密码和原会话。
- 替换或删除账户后，只清理不再被其他已保存账户或当前会话引用的证书文件。

### 账户隔离与界面改进

- 删除账户会同步清理其响应缓存、带账户元数据的脚本草稿、WebDAV/S3 配置和本地 MCP Agent 授权，不影响其他账户。
- WebDAV 与 S3 网络存储设置改为按账户隔离；旧版全局配置仅迁移给升级时的当前账户。
- 账户卡片改用明确的“切换”和“编辑”文字操作；置顶、上移、下移和删除集中到“更多”菜单，减少误触并改善窄屏信息层级。
- 长服务器地址和用户名增加省略保护，当前账户使用状态标签、色彩与描边明确区分。

### 验证与兼容性

- 2.3.1 发布前本地 CI 等价验证通过：44 个测试套件、250 项单元测试全部通过，失败、错误和跳过均为 0；Debug APK、Android Lint 与备份模块 AndroidTest 源码编译通过。
- 三账户切换矩阵以及账户编辑、删除和证书直接替换已在 Motorola XT2551-3（Android 16）实机验收通过。
- 新增单元测试覆盖候选连接成功/失败回滚、2FA、账户删除、隔离清理、证书保存及错误证书回滚。
- 支持 Android 12（API 31）及以上，青龙 v2.17 及以上。

## English

### In-app account and server management

- Added a dedicated Accounts and Servers screen for previewing, sorting, editing, deleting, and directly switching saved accounts. Only adding a new account opens the full sign-in flow.
- Account cards show a masked server address, alias, authentication method, TLS/mTLS status, last-used time, and current-account marker without exposing tokens, passwords, or client secrets.
- Password, Client ID, 2FA, and mTLS accounts can be switched directly. The active session is replaced only after the candidate account authenticates successfully; network, TLS, certificate, and verification-code failures leave the previous account available.
- Deleting the active account first performs a safe switch when another account exists. Active backup, restore, or network-export work blocks account changes that could mix account-scoped data.

### Certificate maintenance from account settings

- PKCS#12 client certificates and private CAs can now be added, replaced, or removed from the account editor without recreating the account through the sign-in screen.
- New certificate files are copied atomically into app-private storage, while certificate passwords remain encrypted with Android Keystore.
- Saving TLS changes for the active account re-authenticates with an isolated candidate configuration. A failure or cancelled 2FA challenge restores the previous account record, certificate password, and active session.
- Replaced certificate files are removed only after no saved account or active session references them.

### Account isolation and UI improvements

- Removing an account also clears its response cache, account-tagged script drafts, WebDAV/S3 settings, and local MCP Agent access without affecting other accounts.
- WebDAV and S3 settings are now isolated per QingLong account. Legacy global settings migrate only to the account active during upgrade.
- Account cards now use explicit Switch and Edit actions. Pin, move, and delete operations are grouped in a More menu to reduce accidental actions and visual clutter.
- Long server addresses and usernames are safely ellipsized, while the active account uses a distinct status chip, color, and outline.

### Validation and compatibility

- The local CI-equivalent pre-release run passed 44 suites and 250 unit tests with zero failures, errors, or skips, together with the Debug APK build, Android Lint, and backup-module AndroidTest source compilation.
- The three-account switching matrix, account editing/deletion, and direct certificate replacement passed device acceptance on a Motorola XT2551-3 running Android 16.
- Added unit coverage for candidate-session success and rollback, 2FA, account deletion, scoped cleanup, certificate persistence, and invalid-certificate rollback.
- Requires Android 12 (API 31) or later and QingLong v2.17 or later.

## Third-party software / 第三方组件

Sora Editor and the selected Monarch language definitions remain under their respective upstream licenses. Both permit commercial use when their license conditions are followed. See [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

Sora Editor 与所选 Monarch 语法定义继续适用各自的上游许可证；遵守许可证条件时均允许商业使用。详见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。
