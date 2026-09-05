package com.jmzs.app.data.api

/** 获取号码 / 指定号码时的可选筛选参数 */
data class PhoneFilters(
    /** 运营商代码，1移动 5联通 9电信 14广电 16虚商，null 不限 */
    val isp: Int? = null,
    /** 省份代码，如 44=广东，null 不限 */
    val province: String? = null,
    /** 号码类型，1 只取虚拟 2 只取实卡，null 不限制 */
    val ascription: Int? = null,
    /** 只取号段，多个用 | 分隔，如 1380|1580|1880，空不限 */
    val paragraph: String = "",
    /** 排除号段，多个用 | 分隔，空不限 */
    val exclude: String = "",
    /** 只取该对接码的手机号，空不限 */
    val uid: String = "",
    /** 开发者账号（置入该参数获取 50% 消费分成），空不传 */
    val author: String = "",
) {
    fun toParamMap(): Map<String, String> = buildMap {
        isp?.let { put("isp", it.toString()) }
        province?.takeIf { it.isNotBlank() }?.let { put("Province", it) }
        ascription?.let { put("ascription", it.toString()) }
        if (paragraph.isNotBlank()) put("paragraph", paragraph.trim())
        if (exclude.isNotBlank()) put("exclude", exclude.trim())
        if (uid.isNotBlank()) put("uid", uid.trim())
        if (author.isNotBlank()) put("author", author.trim())
    }
}

/**
 * 豪猪平台接口封装。所有接口统一 GET/POST 方式：
 * https://{服务器}/sms/?api=xxx&token=令牌&...
 */
class ApiService {

    private inline fun <reified T> parse(body: String, codeGetter: (T) -> Int, msgGetter: (T) -> String): T {
        val result = ApiClient.json.decodeFromString<T>(body)
        val code = codeGetter(result)
        if (code != 0 && code != 200) {
            throw ApiException(msgGetter(result).ifBlank { "接口返回错误（code=$code）" })
        }
        return result
    }

    /** 登录，获得 token 令牌（令牌固定，登录一次即可） */
    suspend fun login(server: String, user: String, pass: String): LoginResponse {
        val body = ApiClient.get(
            server,
            mapOf("api" to "login", "user" to user, "pass" to pass),
        )
        return parse(body, { it.code }, { it.msg })
    }

    /** 查询账户信息：余额、最大并发数 */
    suspend fun getSummary(server: String, token: String): SummaryResponse {
        val body = ApiClient.get(server, mapOf("api" to "getSummary", "token" to token))
        return parse(body, { it.code }, { it.msg })
    }

    /** 获取手机号 */
    suspend fun getPhone(
        server: String,
        token: String,
        sid: String,
        filters: PhoneFilters = PhoneFilters(),
    ): PhoneResponse {
        val body = ApiClient.get(
            server,
            buildMap {
                put("api", "getPhone")
                put("token", token)
                put("sid", sid)
                putAll(filters.toParamMap())
            },
        )
        return parse(body, { it.code }, { it.msg })
    }

    /** 指定手机号（再次占用该号码，之后才能读取验证码） */
    suspend fun specifyPhone(
        server: String,
        token: String,
        sid: String,
        phone: String,
        author: String = "",
    ): PhoneResponse {
        val body = ApiClient.get(
            server,
            buildMap {
                put("api", "getPhone")
                put("token", token)
                put("sid", sid)
                put("phone", phone)
                if (author.isNotBlank()) put("author", author)
            },
        )
        return parse(body, { it.code }, { it.msg })
    }

    /** 获取验证码（tm 为毫秒时间戳，防缓存；与官方 PC 客户端一致） */
    suspend fun getMessage(
        server: String,
        token: String,
        sid: String,
        phone: String,
    ): MessageResponse {
        val body = ApiClient.get(
            server,
            mapOf(
                "api" to "getMessage",
                "token" to token,
                "sid" to sid,
                "phone" to phone,
                "tm" to System.currentTimeMillis().toString(),
            ),
        )
        return parse(body, { it.code }, { it.msg })
    }

    /** 释放指定手机号 */
    suspend fun cancelRecv(server: String, token: String, sid: String, phone: String): SimpleResponse {
        val body = ApiClient.get(
            server,
            mapOf(
                "api" to "cancelRecv",
                "token" to token,
                "sid" to sid,
                "phone" to phone,
            ),
        )
        return parse(body, { it.code }, { it.msg })
    }

    /** 释放全部手机号 */
    suspend fun cancelAllRecv(server: String, token: String): SimpleResponse {
        val body = ApiClient.get(server, mapOf("api" to "cancelAllRecv", "token" to token))
        return parse(body, { it.code }, { it.msg })
    }

    /** 拉黑指定手机号 */
    suspend fun addBlacklist(server: String, token: String, sid: String, phone: String): SimpleResponse {
        val body = ApiClient.get(
            server,
            mapOf(
                "api" to "addBlacklist",
                "token" to token,
                "sid" to sid,
                "phone" to phone,
            ),
        )
        return parse(body, { it.code }, { it.msg })
    }
}
