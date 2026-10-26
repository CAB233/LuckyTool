package com.luckyzyx.luckytool.utils

import kotlinx.serialization.json.*
import java.util.UUID

const val ChannelPrefs = "ChannelEvents"

/** 远程偏好中的瞬时事件，限定为项目实际使用的数据类型。 */
object ChannelEventCodec {
    data class Event(val target: String, val value: Any?)

    fun key(target: String, key: String): String = "${if (target == "system") "android" else target}:$key"

    fun encode(target: String, value: Any?): String = buildJsonObject {
        put("id", UUID.randomUUID().toString())
        put("target", target)
        val type = when (value) {
            null -> "signal"
            is String -> "string"
            is Boolean -> "boolean"
            is Int -> "int"
            is Long -> "long"
            is Float -> "float"
            is Pair<*, *> -> {
                require(value.first is String && value.second is Boolean)
                "appSwitch"
            }
            is List<*> -> {
                require(value.all { it is Int })
                "userIds"
            }
            else -> error("Unsupported channel value: ${value.javaClass.name}")
        }
        put("type", type)
        put("value", when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Pair<*, *> -> JsonArray(listOf(JsonPrimitive(value.first as String), JsonPrimitive(value.second as Boolean)))
            is List<*> -> JsonArray(value.map { JsonPrimitive(it as Int) })
            else -> error("Unsupported channel value")
        })
    }.toString()

    fun decode(raw: String): Event {
        val json = Json.parseToJsonElement(raw).jsonObject
        val value = json.getValue("value")
        return Event(json.getValue("target").jsonPrimitive.content, when (json.getValue("type").jsonPrimitive.content) {
            "signal" -> null
            "string" -> value.jsonPrimitive.content
            "boolean" -> value.jsonPrimitive.boolean
            "int" -> value.jsonPrimitive.int
            "long" -> value.jsonPrimitive.long
            "float" -> value.jsonPrimitive.float
            "appSwitch" -> value.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonPrimitive.boolean }
            "userIds" -> ArrayList(value.jsonArray.map { it.jsonPrimitive.int })
            else -> error("Unsupported channel event type")
        })
    }
}
