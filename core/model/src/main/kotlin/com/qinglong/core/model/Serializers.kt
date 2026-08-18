package com.qinglong.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * MongoDB ObjectId 反序列化器。
 *
 * 青龙后端使用 MongoDB，`_id` 字段在 JSON 序列化时可能有两种形态：
 * 1. 纯字符串： `"_id": "507f1f77bcf86cd799439011"`
 * 2. 扩展 JSON： `"_id": { "$oid": "507f1f77bcf86cd799439011" }`
 *
 * 本序列化器统一将两者解析为 24 位十六进制字符串。
 */
object ObjectIdSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ObjectId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeString("") else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        return try {
            val jsonDecoder = decoder as JsonDecoder
            when (val el = jsonDecoder.decodeJsonElement()) {
                is JsonNull -> null
                is JsonPrimitive -> el.jsonPrimitive.content
                is JsonObject -> el.jsonObject["\$oid"]?.jsonPrimitive?.content
                else -> el.toString()
            }
        } catch (_: Exception) {
            try {
                decoder.decodeString()
            } catch (_: Exception) {
                null
            }
        }
    }
}
