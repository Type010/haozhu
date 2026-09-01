package com.jmzs.app.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 平台接口的 code 字段在部分接口返回数字（0/-1），部分接口返回字符串（"0"），
 * 用该序列化器统一解析为 Int。null / 无法解析时返回 -1（视为失败）。
 */
object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val json = decoder as? JsonDecoder ?: return -1
        val element = json.decodeJsonElement()
        if (element is JsonPrimitive) {
            return element.content.trim().toDoubleOrNull()?.toInt() ?: -1
        }
        return -1
    }

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }
}

/**
 * 平台返回的字符串字段可能为 null（例如查询余额时 money 为 null），
 * 用该序列化器把 null 统一解析为空字符串，避免解析异常。
 */
object NullableStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NullableString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val json = decoder as? JsonDecoder ?: return ""
        val element = json.decodeJsonElement()
        return when {
            element is JsonPrimitive && element.isString -> element.content
            element is JsonPrimitive -> element.content
            else -> ""
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

/** 登录：获得 token 令牌 */
@Serializable
data class LoginResponse(
    @Serializable(with = FlexibleIntSerializer::class) val code: Int = -1,
    @Serializable(with = NullableStringSerializer::class) val msg: String = "",
    @Serializable(with = NullableStringSerializer::class) val token: String = "",
)

/** 查询余额：getSummary */
@Serializable
data class SummaryResponse(
    @Serializable(with = FlexibleIntSerializer::class) val code: Int = -1,
    @Serializable(with = NullableStringSerializer::class) val msg: String = "",
    /** 账户余额（字符串，可能为 null） */
    @Serializable(with = NullableStringSerializer::class) val money: String = "",
    val num: Int? = null,
)

/** 获取号码 / 指定号码：getPhone */
@Serializable
data class PhoneResponse(
    @Serializable(with = FlexibleIntSerializer::class) val code: Int = -1,
    @Serializable(with = NullableStringSerializer::class) val msg: String = "",
    @Serializable(with = NullableStringSerializer::class) val sid: String = "",
    @SerialName("shop_name")
    @Serializable(with = NullableStringSerializer::class) val shopName: String = "",
    @SerialName("country_name")
    @Serializable(with = NullableStringSerializer::class) val countryName: String = "",
    @SerialName("country_code")
    @Serializable(with = NullableStringSerializer::class) val countryCode: String = "",
    @SerialName("country_qu")
    @Serializable(with = NullableStringSerializer::class) val countryQu: String = "",
    @Serializable(with = NullableStringSerializer::class) val uid: String = "",
    @Serializable(with = NullableStringSerializer::class) val phone: String = "",
    @Serializable(with = NullableStringSerializer::class) val sp: String = "",
    @SerialName("phone_gsd")
    @Serializable(with = NullableStringSerializer::class) val phoneGsd: String = "",
)

/** 获取验证码：getMessage */
@Serializable
data class MessageResponse(
    @Serializable(with = FlexibleIntSerializer::class) val code: Int = -1,
    @Serializable(with = NullableStringSerializer::class) val msg: String = "",
    @Serializable(with = NullableStringSerializer::class) val sms: String = "",
    @Serializable(with = NullableStringSerializer::class) val yzm: String = "",
)

/** 释放 / 拉黑等简单操作：cancelRecv / cancelAllRecv / addBlacklist */
@Serializable
data class SimpleResponse(
    @Serializable(with = FlexibleIntSerializer::class) val code: Int = -1,
    @Serializable(with = NullableStringSerializer::class) val data: String = "",
    @Serializable(with = NullableStringSerializer::class) val msg: String = "",
)
