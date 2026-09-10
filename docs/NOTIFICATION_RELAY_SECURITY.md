# 青龙通知中继与 FCM 安全设计

## 目标与非目标

目标链路为：青龙 Webhook → 用户部署的 HTTPS 中继 → FCM HTTP v1 → AzureQL Android 系统通知。
它用于实时通知，不把 AzureQL 变成服务端通知历史数据库，也不允许手机直接暴露 HTTP 监听端口。

现有“通知设置”编辑的是青龙 `/api/user/notification` 中的单一通知提供商；它与本链路不是同一个功能。
中继功能在完成配对和部署前必须保持关闭，不得静默把用户已有的 Telegram、Bark 等渠道替换为 Webhook。

## 信任边界

- APK 只包含 Firebase Android 客户端参数。Firebase 服务账户 JSON、OAuth 访问令牌和私钥只允许存在于中继的
  服务器端凭据存储中。
- 中继不得接收或保存青龙登录 Token、账户密码、mTLS 私钥、脚本环境变量或 AzureQL 备份。
- 每个青龙账户生成独立的 256-bit Webhook 接收密钥；密钥放在 `Authorization` Header，不放在 URL、Body、
  FCM payload 或日志中。
- FCM registration token 视为敏感投递地址：客户端和中继均不得写入普通日志；中继应加密静态存储。
- 配对使用一次性、短时效 enrollment code。生产实现应由 Android Keystore 中的设备私钥签名注册、更新 Token
  和撤销请求，中继只保存设备公钥。

## 中继接口（v1）

### 设备注册

`POST /v1/devices/register`

- `Authorization: Bearer <one-time-enrollment-code>`
- 请求：`devicePublicKey`、`fcmToken`、`appInstanceId`。
- 响应：设备 ID；不得返回 Firebase 服务账户材料。
- enrollment code 使用一次即失效，最长 10 分钟，并限制来源与尝试次数。

### 青龙账户绑定

`POST /v1/devices/{deviceId}/servers`

- 请求由已配对设备签名。
- 请求只包含不透明 `serverId` 和用户可识别的别名。
- 响应返回一次性的 Webhook 接收密钥及可复制的 URL/Header/Body 配置；中继只保存密钥哈希。

### Webhook 接收

`POST /v1/qinglong/{serverId}/notify`

```http
Authorization: Bearer <receiver-secret>
Content-Type: application/json
```

```json
{
  "title": "$title",
  "content": "$content"
}
```

中继验证密钥后，把内容转换为仅含以下字符串字段的 FCM data message：

```json
{
  "schema": "1",
  "eventId": "<deduplication-id>",
  "serverId": "<opaque-id>",
  "serverName": "<display-alias>",
  "title": "<max-160-characters>",
  "body": "<max-4096-characters>"
}
```

FCM TTL 建议不超过 24 小时。中继按 `serverId` 限流、按事件摘要短时去重；正文只在请求和 FCM 投递所需的
内存中存在，不写数据库、访问日志或错误追踪。服务端使用 Firebase Admin SDK 或 Application Default
Credentials 调用 HTTP v1；禁止使用已废弃的 legacy server key 接口。

## Android 端规则

- 仅接受 `schema=1`、安全标识符及限定长度的 data payload；不接受中继指定的 Intent、URI、Channel ID、
  图标或任意富媒体地址。
- 每个服务器使用独立通知 Channel；锁屏默认只显示通用来源与标题，正文标为 private。
- 点击通知只打开 AzureQL，并携带不透明 server/event ID；账户切换必须在本地已保存账户中匹配，不能根据
  FCM 文本创建账户或导航到外部 URL。
- Android 13+ 仅在用户主动开启同步时请求通知权限；拒绝权限不影响青龙管理、备份或 MCP。
- 关闭同步或删除账户时，同时撤销中继绑定；Token 刷新后只向已配对中继提交新 Token。

## 青龙配置保护

系统通知与脚本通知必须分别明确启用：

1. `/api/user/notification` 只允许一个 provider。已有 provider 时默认禁止覆盖；将来若支持中继同时转发，
   必须由中继显式配置而非客户端猜测。
2. 脚本通知通过 `config.sh` 的 `WEBHOOK_*`。客户端只管理带开始/结束标记的 AzureQL 区块，保存前重新读取并
   比较内容哈希；发现并发修改必须停止。关闭时只删除自己管理的区块。

## 构建与部署门禁

Android 构建从环境变量读取以下公开客户端参数；缺少任一项时 FCM 初始化保持关闭：

- `AZUREQL_FIREBASE_APPLICATION_ID`
- `AZUREQL_FIREBASE_API_KEY`
- `AZUREQL_FIREBASE_PROJECT_ID`
- `AZUREQL_FIREBASE_SENDER_ID`

这些参数可识别 Firebase 项目但不能授权发送消息。授权发送的服务账户必须通过中继部署环境的
`GOOGLE_APPLICATION_CREDENTIALS` 或云平台 Workload Identity 提供，永不写入 Gradle、APK、GitHub Release
附件或仓库 Secrets 以外的构建日志。

发布前必须完成：中继渗透/滥用测试、密钥撤销、Token 刷新、重复消息、离线过期、通知权限拒绝、账户删除、
锁屏隐私、已有通知渠道不覆盖，以及 Firebase/中继不可用时不影响应用核心功能的验收。

## 依据

- Firebase Android 接收消息：<https://firebase.google.com/docs/cloud-messaging/android/receive-messages>
- FCM HTTP v1 与服务端授权：<https://firebase.google.com/docs/cloud-messaging/send/v1-api>
- Firebase Android BoM：<https://firebase.google.com/docs/android/learn-more#bom>
- 青龙内置 API / `notificationInfo`：<https://qinglong.online/en/guide/user-guide/built-in-api>
