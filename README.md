<p align="center">
  <img src="docs/images/azureql-icon.png" width="128" alt="AzureQL app icon" />
</p>

<h1 align="center">AzureQL</h1>

<p align="center"><strong>Azure Dragon Panel</strong></p>

<p align="center">面向青龙服务端的原生 Android 管理客户端</p>

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Material%203-blue?logo=jetpackcompose)](https://developer.android.com/compose)
[![Hilt](https://img.shields.io/badge/DI-Hilt-orange?logo=dagger)](https://dagger.dev/hilt/)
[![Retrofit](https://img.shields.io/badge/HTTP-Retrofit-green?logo=square)](https://square.github.io/retrofit/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

AzureQL 是基于 [青龙面板 API](https://github.com/whyour/qinglong) 的原生 Android 客户端，对外名称为 **Azure Dragon Panel**，使用 **Kotlin + Jetpack Compose + Material 3** 构建。

> **兼容性**：青龙 v2.17+ 后端已从 MongoDB 迁移至 SQLite，本应用已对齐数字自增主键 `id`（非旧的 MongoDB `_id` 字符串）。
>
> **系统要求**：Android 12（API 31）及以上。


## 📱 应用展示

<table>
  <tr>
    <td align="center"><strong>首页仪表盘</strong><br><img src="docs/images/azureql-home.jpg" width="260" alt="首页仪表盘" /></td>
    <td align="center"><strong>定时任务</strong><br><img src="docs/images/azureql-tasks.jpg" width="260" alt="定时任务" /></td>
    <td align="center"><strong>脚本管理</strong><br><img src="docs/images/azureql-scripts.jpg" width="260" alt="脚本管理" /></td>
  </tr>
  <tr>
    <td align="center"><strong>环境变量</strong><br><img src="docs/images/azureql-environments.jpg" width="260" alt="环境变量" /></td>
    <td align="center"><strong>订阅管理</strong><br><img src="docs/images/azureql-subscribe.jpg" width="260" alt="订阅管理" /></td>
    <td align="center"><strong>设置</strong><br><img src="docs/images/azureql-settings.jpg" width="260" alt="设置" /></td>
  </tr>
</table>

## ✨ 特性

- 🎨 **Material You** 动态配色（Light / Dark 主题）
- 🔐 **两步验证 (2FA)** — 同时提供二维码、手动密钥和验证码确认
- 🔑 **mTLS 客户端证书** 支持（`.p12` / `.pfx` + 私有 CA）；mTLS 网络客户端每 12 小时自动轮换并复用证书完成新握手
- 🔏 **Bitwarden 自动填充**（用户名 / 密码 / 两步验证码语义标记）
- 🏗️ **Clean Architecture** + MVVM 架构
- 💉 **Hilt** 依赖注入
- 🌐 **Retrofit** 网络层（系统证书校验 + 客户端证书）
- 🔐 **每账户加密凭据** — 记住密码后按服务器、账户和登录模式分别使用 Android Keystore 加密，切换历史账户可安全回填
- ⚡ **加密本地缓存** — 首页、任务和脚本树先显示缓存再刷新，按账户隔离并自动清理 8 天前数据
- 📝 **大脚本可靠工作流** — 文件流写入账户隔离的私有缓存，服务端 `size`/可用 `mtime` 比对后复用；按段预览、上传二次确认、冲突确认与待上传草稿恢复
- ✨ **内置代码编辑器** — Sora Editor 行号与增量语法高亮，按扩展名识别 Python、JavaScript、TypeScript、Shell、JSON、YAML，也可手动切换为纯文本
- 🧭 **类型安全导航** (`@Serializable` routes)
- 📊 **首页仪表盘** — 任务总览卡 + 系统状态卡（内存 / CPU / 运行时长）
- 🗂️ **功能模块** — 定时任务、环境变量、脚本、订阅、依赖与日志管理
- 👆 **连续滑动导航** — 首页、任务、脚本、订阅、环境与设置支持左右滑动切换，并与底部导航保持同步
- ⏱️ **青龙 2.21 任务管理** — 常规/手动/开机运行、附加定时、标签筛选、实例模式、日志目录与执行前后命令
- 🏷️ **标签与脚本联动** — 标签管理显示引用数，支持安全重命名与未引用标签删除；任务命令可定位并打开实际脚本
- 📥 **脚本文件操作** — 从 Android 系统文件选择器批量导入脚本，支持创建根目录/嵌套文件夹及复制文件或文件夹路径
- 🔄 **订阅管理** — 支持公开/私有仓库与单文件，以及白黑名单、依赖、后缀、代理和自动任务策略
- 📡 **可靠实时日志** — 任务与订阅运行期间按游标增量显示日志；兼容终态完整快照，避免任务结束后重复追加
- 🔔 **通知设置** — 在应用内配置并测试青龙通知渠道，动态支持 Gotify、Ntfy、Telegram、Webhook、Bark、邮件等官方渠道；敏感字段默认遮罩且不落盘
- 📜 **按日系统日志** — 按服务器时区浏览最近 7 天的青龙系统日志，支持按需加载、下拉刷新、长日志窗口化滚动及旧版青龙兼容
- 💾 **备份与恢复** — 通过青龙官方 API 导出与恢复；支持本机存储、WebDAV 与 S3 兼容存储，网络凭据使用 Android Keystore 加密
- 👥 **账户与服务器** — 在设置内预览、编辑、排序、删除和直接切换已保存账户；支持密码、Client ID、2FA 与 mTLS 账户，切换失败保留原会话
- 🧩 **MCP（Phase 2）** — 默认仅本机访问，可显式开放到可信局域网；10 个限长只读工具、13 个受控工具、可选静默授权、Agent 独立权限、幂等与本地脱敏审计

## 🏗️ 架构

```
app/                        ← 入口 + DI + 首页 / 配置
├── core/
│   ├── model/              ← 纯 Kotlin 领域模型
│   ├── data/               ← Repository + Retrofit + Room 加密缓存 + mTLS
│   ├── domain/             ← UseCase + Repository 接口
│   ├── mcp/                ← MCP 协议适配 + 回环 Streamable HTTP 引擎
│   └── ui/                 ← 共享 Compose 组件 + Theme
└── feature/
    ├── login/              ← 登录 + 两步验证 + mTLS 证书选择
    ├── task/               ← 定时任务管理
    ├── env/                ← 环境变量管理
    ├── script/             ← 脚本导入 / 分段预览 / Sora 编辑与高亮 / 订阅管理
    ├── dependency/         ← 依赖管理
    ├── backup/             ← 服务端备份与恢复 + WebDAV / S3 网络存储
    ├── log/                ← 日志查看
    ├── mcp/                ← MCP 前台服务 + 技术预览设置页
    └── settings/           ← 设置（通知渠道 / 系统配置 / 多账户与服务器 / 登录日志）
```

## ⚡ 性能与大脚本策略

- 首页、任务和脚本树采用“缓存先显示、服务端随后刷新”；缓存 JSON 解码和脚本树排序在
  后台调度器执行，底部主导航关闭无必要的页面切换动画，减少应用冷启动后的首次切页负担。
- 冷启动把会话和主题偏好合并成一个本地首帧快照；Android 12 系统 Splash 使用静态图标，
  不播放图标动画或退出动画，快照就绪后直接显示登录页或首页。
- 任务与订阅编辑在窄屏上使用等宽分段选择，三种主类型无需横向滚动；设置页长按服务端
  版本可用系统默认浏览器打开当前登录地址。
- 脚本下载使用青龙官方文件流接口，避免把大文件包装成一个巨大 JSON 字符串。小于等于
  512 KiB 的 UTF-8 文件可在应用内编辑；512 KiB～10 MiB 文件使用 8192 字符分段预览，
  并可交给系统文本编辑器修改；超过 10 MiB 的文件仅预览和下载。
  分段渲染会在字形安全边界拆开超长行，保持原始文本和复制内容不被视觉换行改写。
- 编辑后的文件通过 multipart 文件流上传；HTTP 成功后仍须以服务端版本大小复核。复核不
  确认、离线、超时或服务端错误时草稿保留为“待上传”，不会误报成功。回传前会比较服务端
  `mtime`、大小或原始文件哈希；发现脚本已被其他客户端修改时必须由用户确认是否覆盖。
  非法 UTF-8 文件不会被替换字符后误写回服务端。
- 大脚本草稿是为外部编辑器准备的应用私有临时明文文件，不写入 Room 响应缓存、不参与
  备份，也不包含 Token。干净关闭保留缓存供版本比对复用，显式放弃修改或确认上传后删除；
  维护任务会清理 8 天前和 LRU 超额的草稿。外部编辑器须支持写回 Android `content://` URI。
- 小型脚本的查看与编辑由 Sora Editor 提供行号、块引导线与增量语法高亮；语言模式根据文件扩展名
  自动选择，也可在标题栏手动切换。未知扩展名安全降级为纯文本，高亮仅改变显示，不执行脚本。
  大文件仍使用既有分页预览，避免把完整正文送入编辑器布局。
- 2026-09-04 实测缓存复用：50 MiB 脚本首开下载约 `28.7 s`，关闭后重开约 `2.6 s`，
  约为 `11x` 提升；内容文件 mtime 保持不变，确认未重新下载。
- 当前构建 Macrobenchmark：10 MiB 长单行分页预览 CPU 帧耗时 P50/P90/P95/P99 为
  `2.6/11.6/17.8/24.3 ms`，P99 frame overrun 为 `16.3 ms`。相对修复前约 `175.0 ms`
  的长行布局尖峰，尾部 overrun 降低约 `90.7%`；冷启动 Baseline Profile OFF/ON 中位数
  `385.7/296.6 ms`，缩短约 `23.1%`。
- 独立 `:benchmark` 模块覆盖冷启动、主导航、500/1000 项任务、大脚本目录、1/5/20 MiB 日志、
  10/50 MiB 脚本和订阅日志轮询。实机一键预检、运行及 Trace 拉取方式见
  [benchmark/README.md](benchmark/README.md)，性能结论与待验证项见
  [IMPROVEMENT_PLAN.md](docs/IMPROVEMENT_PLAN.md)。

## 🧩 本地 MCP（Phase 2）

设置中的 **MCP 服务** 可由用户手动启动本地前台服务。先通过设备锁屏验证创建只读 Agent，
复制仅显示一次的 Token，再启动服务。Token 使用 256-bit 随机数生成，应用只保存哈希，并把
Agent 绑定到创建时的当前青龙账户。服务默认只监听本机；用户可在停止服务后显式开启“局域网可访问”，
并从设置页查看首选局域网 IPv4 或展开其他 VPN/IPv6 地址。两种模式都会校验 Host/Origin，并实施请求体、
并发和速率限制及本地脱敏审计。局域网模式仍使用明文 HTTP Bearer Token，只适合可信网络，不应暴露到公网。

基础只读工具为 `server_status`、`list_tasks`、`list_scripts`、`read_script`、`list_dependencies`、
`check_dependency`、`list_envs`、`list_logs`、`read_log_tail` 和 `get_task_log`。日志仅返回受限尾部；
环境变量值、青龙 Token、密码、证书和私钥不会暴露。

用户可在设备身份验证后，为单个 Agent 开启 Phase 2 的受控写入与执行权限。新增
`get_operation`、`create_script`、`update_script`、`run_task`、`stop_task`、
`install_dependency`、`reinstall_dependency`、`create_env`、`update_env`、`enable_env`、
`disable_env`、`create_task` 和 `update_task`。每次写入都先生成待确认 Operation；用户必须在
手机端再次验证并批准，Agent 再携带相同 `idempotency_key`、`operation_id` 和参数重试才会执行。
Operation 会持久化保存幂等结果，避免网络重试造成重复写入；脚本更新还必须携带
`read_script` 返回的 `expected_sha256`，冲突时不会强制覆盖。

已授予受控权限的 Agent 还可单独开启“静默允许写入与执行”，跳过每次操作的交互确认。开启前必须再次
完成设备身份验证；账户绑定、Scope、参数和路径上限、单 Agent 串行化、幂等、脚本哈希冲突检查与脱敏审计
仍然生效。关闭受控权限会同步撤销静默授权。

MCP 设置页默认展示最近 3 条脱敏审计，可展开至最近 20 条或收起，并支持清除审计、修改 Agent
名称与权限和处理待确认操作。环境变量值和脚本
正文不会写入 Operation 或审计。未建模的删除操作、配置文件修改、任意 HTTP、任意 Shell 和青龙凭据读取
仍未开放。

电脑调试时先执行：

```bash
adb forward tcp:18765 tcp:18765
```

再让 MCP 客户端连接 `http://127.0.0.1:18765/mcp`，并发送
`Authorization: Bearer <Agent Token>`。架构、安全模型、工具契约、兼容矩阵和开源选型见
[AZUREQL_MCP_ARCHITECTURE.md](docs/AZUREQL_MCP_ARCHITECTURE.md)、
[AZUREQL_MCP_SECURITY.md](docs/AZUREQL_MCP_SECURITY.md)、
[AZUREQL_MCP_TOOL_SPEC.md](docs/AZUREQL_MCP_TOOL_SPEC.md)、
[MCP_COMPATIBILITY.md](docs/MCP_COMPATIBILITY.md) 与
[MCP_OPEN_SOURCE_REFERENCES.md](docs/MCP_OPEN_SOURCE_REFERENCES.md)。

## ☁️ 网络备份

“备份与恢复”支持将青龙官方归档导出到本机存储，或上传到已经保存并测试连接的 WebDAV / S3 目标。
网络存储设置按青龙账户隔离，切换账户不会复用另一账户的 WebDAV/S3 凭据；升级前的全局配置会迁移给
升级时的当前账户。
网络存储连接信息集中在独立设置子页；两种目标均可用时，导出前由用户明确选择。WebDAV 密码、S3 Access Key
和 Secret Key 使用各自独立的 Android Keystore 密钥加密，不进入备份归档、URL 或应用日志。

上传任务通过 WorkManager 在后台执行：先把青龙导出流写入应用私有临时文件，再流式上传，保留进度、取消、
重试、前台通知与脱敏错误分类。WebDAV 支持逐级创建远程目录；S3 支持 AWS SigV4、自定义端点、路径样式、
自动区域重签和条件写入。归档默认使用唯一时间戳文件名，并拒绝静默覆盖已有对象。

## 🔔 通知与系统日志

“设置 → 通知设置”直接读取当前青龙服务器的通知配置，并在保存前由青龙后端发送测试通知。页面按渠道动态
展示必填项与可选项，密钥和令牌默认遮罩，只保留在当前页面内存中；进程重建或切换账户后会从当前服务器
重新读取，避免跨账户串用。通知服务若位于受 mTLS 保护的反向代理后，应确保青龙容器本身具备访问条件，
或使用容器可直连的内网地址。

“设置 → 系统日志”按服务器时区列出最近 7 天，点选后再加载对应日期正文，并支持下拉刷新、空日志和
1 MiB 截断提示。客户端兼容不接受 `limit` 查询参数的青龙 2.20.x：收到 HTTP 400 时自动以旧格式重试，
同时仍在本地限制读取上限。该入口不会替代任务详情实时日志、订阅日志或 MCP 日志工具。

## 🚀 快速开始

1. **克隆项目**
```bash
git clone https://github.com/Infinifar/AzureQL.git
```

2. **用 Android Studio 打开**（Hedgehog+ 推荐）

3. **构建 & 运行**
```bash
./gradlew :app:assembleDebug
```

## 🔑 登录流程

```
用户输入 Host + 用户名 + 密码（可选 mTLS 证书）
       │
       ▼
POST /api/user/login ───── code=200 ──→ 登录成功，获取 Token
       │
       │ code=420
       ▼
┌─────────────────────────┐
│   两步验证界面（内嵌）    │
│   扫描二维码或输入密钥     │
│   输入 6 位验证码         │
└─────────────────────────┘
       │
       ▼
PUT /api/user/two-factor/login ──→ 验证成功，获取 Token
```

### mTLS 客户端证书

若青龙面板启用了双向 TLS 认证，登录时：

1. 在登录界面点击 **「mTLS 证书」**
2. 选择 `.p12` / `.pfx` 证书文件（通过系统文件选择器）
3. 输入证书密码
4. 正常登录

证书路径使用 DataStore 持久化，证书密码使用 Android Keystore 加密，切换服务器后仍可复用；也可以选择
私有 CA 验证服务端。启用 mTLS 的网络客户端最长复用 12 小时，之后会在下一次 API 或 WebSocket 建连前
重建 SSLContext，并使用当前账户证书重新执行完整握手，无需切换账户。已保存账户还可在
**设置 → 账户与服务器 → 编辑** 中直接添加、替换或移除客户端证书和私有 CA；当前账户会先以新证书
重新认证，失败时自动恢复原证书与原会话。该跨日续连路径仍在持续实机观察中。

## 📋 开发计划

- [x] **阶段一：项目基础设施** — 架构、DI、网络层、主题
- [x] **阶段二：数据层重构** — 数字主键 `id` 对齐 SQLite、批量操作 API
- [x] **阶段三：登录模块** — 密码 / ClientID 双模式 + 2FA + mTLS + Autofill
- [x] **阶段四：导航 & 主框架** — 底部导航 + 类型安全路由
- [x] **阶段五：功能模块** — 任务 / 环境变量 / 脚本 / 依赖 / 日志 / 设置
- [x] **阶段六：首页仪表盘** — 任务总览 + 系统状态卡
- [x] **阶段七：测试与发布验收** — Unit / Integration / UI / Macrobenchmark / 真机回归

## 📄 License

AzureQL 使用 MIT License；Sora Editor、Monarch 语法定义等第三方组件适用各自许可证，详见
[第三方组件声明](THIRD_PARTY_NOTICES.md)。
