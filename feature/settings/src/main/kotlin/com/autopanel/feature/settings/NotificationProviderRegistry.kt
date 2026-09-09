package com.autopanel.feature.settings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal enum class NotificationFieldKind { Text, Secret, Number, Multiline, Choice }

internal data class NotificationChoice(val value: String, val zh: String, val en: String)

internal data class NotificationFieldSpec(
    val key: String,
    val zh: String,
    val en: String,
    val required: Boolean = false,
    val kind: NotificationFieldKind = NotificationFieldKind.Text,
    val choices: List<NotificationChoice> = emptyList()
)

internal data class NotificationProviderSpec(
    val type: String,
    val zh: String,
    val en: String,
    val fields: List<NotificationFieldSpec>
)

private fun text(key: String, zh: String, en: String, required: Boolean = false) =
    NotificationFieldSpec(key, zh, en, required)
private fun secret(key: String, zh: String, en: String, required: Boolean = false) =
    NotificationFieldSpec(key, zh, en, required, NotificationFieldKind.Secret)
private fun number(key: String, zh: String, en: String) =
    NotificationFieldSpec(key, zh, en, kind = NotificationFieldKind.Number)
private fun multiline(key: String, zh: String, en: String) =
    NotificationFieldSpec(key, zh, en, kind = NotificationFieldKind.Multiline)
private fun choice(
    key: String,
    zh: String,
    en: String,
    required: Boolean,
    vararg values: NotificationChoice
) = NotificationFieldSpec(key, zh, en, required, NotificationFieldKind.Choice, values.toList())

internal object NotificationProviderRegistry {
    val providers = listOf(
        NotificationProviderSpec("closed", "关闭通知", "Disabled", emptyList()),
        NotificationProviderSpec("gotify", "Gotify", "Gotify", listOf(
            text("gotifyUrl", "服务地址", "Server URL", true), secret("gotifyToken", "应用令牌", "App token", true),
            number("gotifyPriority", "优先级", "Priority")
        )),
        NotificationProviderSpec("ntfy", "Ntfy", "Ntfy", listOf(
            text("ntfyUrl", "服务地址", "Server URL", true), text("ntfyTopic", "主题", "Topic", true),
            text("ntfyPriority", "优先级", "Priority"), secret("ntfyToken", "访问令牌", "Access token"),
            text("ntfyUsername", "用户名", "Username"), secret("ntfyPassword", "密码", "Password"),
            multiline("ntfyActions", "操作按钮", "Actions")
        )),
        NotificationProviderSpec("serverChan", "Server 酱", "ServerChan", listOf(secret("serverChanKey", "SendKey", "SendKey", true))),
        NotificationProviderSpec("pushDeer", "PushDeer", "PushDeer", listOf(
            secret("pushDeerKey", "PushKey", "PushKey", true), text("pushDeerUrl", "服务地址", "Server URL")
        )),
        NotificationProviderSpec("bark", "Bark", "Bark", listOf(
            secret("barkPush", "推送密钥", "Push key", true), text("barkUrl", "服务地址", "Server URL"),
            text("barkIcon", "图标", "Icon"), text("barkSound", "声音", "Sound"), text("barkGroup", "分组", "Group"),
            text("barkLevel", "时效级别", "Interruption level"), text("barkArchive", "归档参数", "Archive")
        )),
        NotificationProviderSpec("telegramBot", "Telegram Bot", "Telegram Bot", listOf(
            secret("telegramBotToken", "Bot Token", "Bot token", true), text("telegramBotUserId", "用户 ID", "User ID", true),
            text("telegramBotProxyHost", "代理主机", "Proxy host"), number("telegramBotProxyPort", "代理端口", "Proxy port"),
            secret("telegramBotProxyAuth", "代理认证", "Proxy auth"), text("telegramBotApiHost", "API 主机", "API host")
        )),
        NotificationProviderSpec("dingtalkBot", "钉钉机器人", "DingTalk Bot", listOf(
            secret("dingtalkBotToken", "Access Token", "Access token", true), secret("dingtalkBotSecret", "签名密钥", "Signing secret")
        )),
        NotificationProviderSpec("weWorkBot", "企业微信机器人", "WeCom Bot", listOf(
            secret("weWorkBotKey", "机器人 Key", "Bot key", true), text("weWorkOrigin", "服务地址", "Server origin")
        )),
        NotificationProviderSpec("weWorkApp", "企业微信应用", "WeCom App", listOf(
            secret("weWorkAppKey", "应用配置", "App configuration", true), text("weWorkOrigin", "服务地址", "Server origin")
        )),
        NotificationProviderSpec("lark", "飞书机器人", "Lark Bot", listOf(
            secret("larkKey", "Webhook Key", "Webhook key", true), secret("larkSecret", "签名密钥", "Signing secret")
        )),
        NotificationProviderSpec("aibotk", "智能微秘书", "Aibotk", listOf(
            secret("aibotkKey", "API Key", "API key", true),
            choice("aibotkType", "目标类型", "Target type", true,
                NotificationChoice("room", "群聊", "Room"), NotificationChoice("contact", "联系人", "Contact")),
            text("aibotkName", "目标名称", "Target name", true)
        )),
        NotificationProviderSpec("iGot", "iGot", "iGot", listOf(secret("iGotPushKey", "Push Key", "Push key", true))),
        NotificationProviderSpec("pushPlus", "PushPlus", "PushPlus", listOf(
            secret("pushPlusToken", "Token", "Token", true), text("pushPlusUser", "群组编码", "Group code"),
            text("pushplusTemplate", "模板", "Template"), text("pushplusChannel", "渠道", "Channel"),
            text("pushplusWebhook", "Webhook", "Webhook"), text("pushplusCallbackUrl", "回调地址", "Callback URL"),
            text("pushplusTo", "好友令牌", "Recipient token")
        )),
        NotificationProviderSpec("wePlusBot", "微加机器人", "WePlus Bot", listOf(
            secret("wePlusBotToken", "Token", "Token", true), text("wePlusBotReceiver", "接收者", "Receiver"),
            text("wePlusBotVersion", "接口版本", "API version")
        )),
        NotificationProviderSpec("wxPusherBot", "WxPusher", "WxPusher", listOf(
            secret("wxPusherBotAppToken", "App Token", "App token", true), text("wxPusherBotTopicIds", "主题 ID", "Topic IDs"),
            text("wxPusherBotUids", "用户 UID", "User UIDs")
        )),
        NotificationProviderSpec("wxPusherSpt", "WxPusher 一对一", "WxPusher SPT", listOf(
            multiline("wxPusherSptList", "发送配置", "Send configuration").copy(required = true)
        )),
        NotificationProviderSpec("openiLink", "OpeniLink", "OpeniLink", listOf(
            secret("openiLinkAppToken", "App Token", "App token", true), text("openiLinkHubUrl", "Hub 地址", "Hub URL"),
            secret("openiLinkContextToken", "Context Token", "Context token")
        )),
        NotificationProviderSpec("chat", "群晖 Chat", "Synology Chat", listOf(
            text("synologyChatUrl", "Webhook 地址", "Webhook URL", true)
        )),
        NotificationProviderSpec("email", "电子邮件", "Email", listOf(
            text("emailService", "邮件服务", "Mail service", true), text("emailUser", "发件账号", "Sender account", true),
            secret("emailPass", "密码或授权码", "Password or app code", true), text("emailTo", "收件地址", "Recipient")
        )),
        NotificationProviderSpec("pushMe", "PushMe", "PushMe", listOf(
            secret("pushMeKey", "Push Key", "Push key", true), text("pushMeUrl", "服务地址", "Server URL")
        )),
        NotificationProviderSpec("chronocat", "Chronocat", "Chronocat", listOf(
            text("chronocatURL", "服务地址", "Server URL", true), text("chronocatQQ", "QQ 号", "QQ number", true),
            secret("chronocatToken", "Access Token", "Access token", true)
        )),
        NotificationProviderSpec("goCqHttpBot", "go-cqhttp", "go-cqhttp", listOf(
            text("goCqHttpBotUrl", "服务地址", "Server URL", true), secret("goCqHttpBotToken", "Access Token", "Access token", true),
            text("goCqHttpBotQq", "QQ 号", "QQ number", true)
        )),
        NotificationProviderSpec("webhook", "自定义 Webhook", "Custom webhook", listOf(
            choice("webhookMethod", "请求方法", "Method", true,
                NotificationChoice("GET", "GET", "GET"), NotificationChoice("POST", "POST", "POST"), NotificationChoice("PUT", "PUT", "PUT")),
            choice("webhookContentType", "内容类型", "Content type", true,
                NotificationChoice("text/plain", "纯文本", "Plain text"), NotificationChoice("application/json", "JSON", "JSON"),
                NotificationChoice("multipart/form-data", "表单数据", "Multipart form"),
                NotificationChoice("application/x-www-form-urlencoded", "URL 编码表单", "URL-encoded form")),
            text("webhookUrl", "请求地址", "Request URL", true), multiline("webhookHeaders", "请求头", "Headers"),
            multiline("webhookBody", "请求体", "Body")
        ))
    )

    fun find(type: String): NotificationProviderSpec? = when (type) {
        "", "closed" -> providers.first()
        "feishu" -> providers.first { it.type == "lark" }
        else -> providers.firstOrNull { it.type == type }
    }
}

internal fun notificationFields(config: JsonObject): Map<String, String> = config.mapValues { (_, value) ->
    (value as? JsonPrimitive)?.contentOrNull ?: value.toString()
}

internal fun buildNotificationPayload(
    original: JsonObject,
    originalType: String,
    selectedType: String,
    values: Map<String, String>
): JsonObject {
    val provider = NotificationProviderRegistry.find(selectedType)
        ?: return original
    val outputType = if (provider.type == "closed") "" else provider.type
    val knownKeys = provider.fields.mapTo(mutableSetOf()) { it.key }
    val retained = if (NotificationProviderRegistry.find(originalType)?.type == provider.type) {
        original.filterKeys { it != "type" && it !in knownKeys }
    } else emptyMap()
    val updated = provider.fields.mapNotNull { field ->
        val raw = values[field.key].orEmpty().trim()
        if (raw.isEmpty() && !field.required) null
        else field.key to if (field.kind == NotificationFieldKind.Number) {
            raw.toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(raw)
        } else JsonPrimitive(raw)
    }.toMap()
    return JsonObject(retained + mapOf("type" to JsonPrimitive(outputType)) + updated)
}

internal fun JsonObject.notificationType(): String =
    (this["type"] as? JsonPrimitive)?.contentOrNull.orEmpty().ifBlank { "closed" }
