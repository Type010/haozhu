package com.jmzs.app.data.local

import kotlinx.serialization.Serializable

/** 用户收藏的接码项目（sid 对应平台的项目 ID） */
@Serializable
data class Project(
    val sid: String,
    val name: String,
)

/** 取号历史记录 */
@Serializable
data class NumberRecord(
    /** 记录时间戳（毫秒） */
    val time: Long,
    val phone: String,
    val sid: String,
    val projectName: String = "",
    val sp: String = "",
    val phoneGsd: String = "",
    val sms: String = "",
    val yzm: String = "",
    /**
     * 记录状态：
     * - received 收到验证码
     * - released 未收到验证码即被释放（号码仍在占用期间被取消）
     */
    val status: String = STATUS_RECEIVED,
) {
    val isReleased: Boolean get() = status == STATUS_RELEASED

    companion object {
        const val STATUS_RECEIVED = "received"
        const val STATUS_RELEASED = "released"
    }
}

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 应用全部本地设置 */
data class AppSettings(
    val server: String = DEFAULT_SERVER,
    val username: String = "",
    val password: String = "",
    val token: String = "",
    /** 验证码轮询间隔（秒） */
    val pollIntervalSec: Int = 15,
    /** 后台接码（前台服务轮询 + 通知） */
    val backgroundEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val projects: List<Project> = emptyList(),
    val lastSid: String = "",
    val history: List<NumberRecord> = emptyList(),
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()

    companion object {
        const val DEFAULT_SERVER = "api.haozhuma.com"
        /** 开发者分成账号（author 参数），固定使用且不提供界面修改 */
        const val DEFAULT_AUTHOR = "A2066584044"
        const val MAX_HISTORY = 200
    }
}
