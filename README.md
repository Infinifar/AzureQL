# 🐉 青龙面板 Android 客户端

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-Material%203-blue?logo=jetpackcompose)](https://developer.android.com/compose)
[![Hilt](https://img.shields.io/badge/DI-Hilt-orange?logo=dagger)](https://dagger.dev/hilt/)
[![Retrofit](https://img.shields.io/badge/HTTP-Retrofit-green?logo=square)](https://square.github.io/retrofit/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

基于 [青龙面板 API](https://github.com/whyour/qinglong) 的原生 Android 客户端，使用 **Kotlin + Jetpack Compose + Material 3** 构建。

## ✨ 特性

- 🎨 **Material You** 动态配色（Light / Dark 主题）
- 🔐 **两步验证 (2FA)** 内嵌登录界面
- 🏗️ **Clean Architecture** + MVVM 架构
- 💉 **Hilt** 依赖注入
- 🌐 **Retrofit** 网络层（自签名证书信任）
- 📦 **DataStore** 本地凭证持久化
- 🧭 **类型安全导航** (`@Serializable` routes)
- ⏰ 定时任务管理（运行/停止/启用/禁用/置顶/批量操作）
- 📝 环境变量管理（编辑/批量/去重/快捷导入/备份）
- 📜 脚本管理（目录树/编辑/下载/删除）
- 📦 依赖管理（安装/卸载/日志）
- 📊 首页实时统计与系统日志查看

## 🔑 青龙后端 API 约定（v2.17+ SQLite）

> 青龙 v2.17 起后端从 MongoDB 迁移到 SQLite，主键由 `_id`（ObjectId 字符串）变为 `id`（自增整数）。

| 约定 | 说明 |
|------|------|
| 主键 | `id: Int`（数字，不是 MongoDB `_id`） |
| 响应格式 | `{code:200, data:T, message:String}` |
| 批量操作 | body 用 `List<Int>`（run/stop/enable/disable/pin/unpin/delete） |
| 路径参数 | `{id}` 是 Int |
| 认证 | Bearer token（`/api/user/login` 除外） |
| 环境变量字段 | `id`、`name`、`value`、`remarks`、`status`、`isPinned` |
| 任务字段 | `isDisabled`、`isPinned`、`last_running_time`、`last_execution_time`、`sub_id`、`log_path` |

### 关键端点

- `GET /api/crons?searchValue=&page=&size=` — 任务列表（分页）
- `GET /api/crons/{id}/log` — 任务实时日志
- `GET /api/envs` / `PUT /api/envs` / `POST /api/envs` — 环境变量
- `GET /api/scripts` — 脚本列表（`type` 字段区分 file/directory）
- `GET /api/logs/` / `GET /api/logs/detail?file=&path=` — 日志列表/详情
- `GET /api/dependencies` — 依赖列表

## 🏗️ 架构

```
app/                        ← 入口 + DI + Home
├── core/
│   ├── model/              ← 纯 Kotlin 领域模型（kotlinx.serialization）
│   ├── data/               ← Repository + Retrofit + Session
│   ├── domain/             ← Repository 接口
│   └── ui/                 ← 共享 Compose 组件 + Theme
└── feature/
    ├── login/              ← 登录 + 两步验证
    ├── task/               ← 定时任务
    ├── env/                ← 环境变量
    ├── script/             ← 脚本管理
    ├── dependency/         ← 依赖管理
    ├── log/                ← 日志
    └── settings/           ← 设置
```

## 🚀 快速开始

1. **克隆项目**
```bash
git clone https://github.com/yisilan83/qinglong-app-android.git
```

2. **用 Android Studio 打开**（Hedgehog+ 推荐）

3. **构建 & 运行**
```bash
./gradlew :app:assembleDebug
```

## 🔑 登录流程

```
用户输入 Host + 用户名 + 密码
       │
       ▼
POST /api/user/login ───── code=200 ──→ 登录成功，获取 Token
       │
       │ code=420
       ▼
┌─────────────────────────┐
│   两步验证界面（内嵌）    │
│   输入 6 位验证码         │
└─────────────────────────┘
       │
       ▼
PUT /api/user/two-factor/login ──→ 验证成功，获取 Token
```

## 📄 License

MIT License
