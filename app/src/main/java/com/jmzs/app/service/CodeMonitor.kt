package com.jmzs.app.service

import com.jmzs.app.data.api.ApiService
import com.jmzs.app.data.local.NumberRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 取码轮询引擎（进程级单例）。
 * 轮询任务挂在进程级协程作用域上，与页面生命周期无关，
 * 因此 App 退到后台后由前台服务保活进程即可持续取码。
 */
object CodeMonitor {

    data class MonitorState(
        /** 是否有活动号码 */
        val active: Boolean = false,
        val server: String = "",
        val sid: String = "",
        val phone: String = "",
        val sp: String = "",
        val phoneGsd: String = "",
        val projectName: String = "",
        /** 是否正在轮询 */
        val polling: Boolean = false,
        /** 已收到的短信全文 */
        val sms: String = "",
        /** 系统识别的数字验证码 */
        val yzm: String = "",
        /** 最近一次错误提示 */
        val lastError: String = "",
        /** 最近一次轮询时间戳 */
        val lastCheckedAt: Long = 0,
    )

    /** 收到验证码事件（后台通知、历史记录都监听它） */
    data class CodeArrivedEvent(
        val phone: String,
        val sid: String,
        val projectName: String,
        val sp: String,
        val phoneGsd: String,
        val sms: String,
        val yzm: String,
        val time: Long,
    )

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val _codeArrived = MutableSharedFlow<CodeArrivedEvent>(extraBufferCapacity = 8)
    val codeArrived: SharedFlow<CodeArrivedEvent> = _codeArrived.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private val api = ApiService()

    /** 开始为一个号码轮询验证码（会终止之前的轮询） */
    fun startPolling(
        server: String,
        token: String,
        sid: String,
        phone: String,
        sp: String = "",
        phoneGsd: String = "",
        projectName: String = "",
        intervalSec: Int,
    ) {
        stopPolling()
        _state.value = MonitorState(
            active = true,
            server = server,
            sid = sid,
            phone = phone,
            sp = sp,
            phoneGsd = phoneGsd,
            projectName = projectName,
            polling = true,
        )
        pollJob = scope.launch {
            while (isActive) {
                val result = try {
                    api.getMessage(server, token, sid, phone)
                } catch (e: CancellationException) {
                    // 任务被新会话替换：直接结束，不得污染新会话状态
                    throw e
                } catch (e: Exception) {
                    _state.update {
                        it.copy(
                            lastError = e.message ?: "网络错误",
                            lastCheckedAt = System.currentTimeMillis(),
                        )
                    }
                    delay(intervalSec * 1000L)
                    continue
                }
                if (result.code == 0) {
                    if (!isActive) return@launch
                    _state.update {
                        it.copy(
                            polling = false,
                            sms = result.sms,
                            yzm = result.yzm,
                            lastError = "",
                            lastCheckedAt = System.currentTimeMillis(),
                        )
                    }
                    _codeArrived.tryEmit(
                        CodeArrivedEvent(
                            phone = phone,
                            sid = sid,
                            projectName = projectName,
                            sp = sp,
                            phoneGsd = phoneGsd,
                            sms = result.sms,
                            yzm = result.yzm,
                            time = System.currentTimeMillis(),
                        ),
                    )
                    return@launch
                }
                _state.update {
                    it.copy(
                        lastError = result.msg.ifBlank { "验证码尚未到达" },
                        lastCheckedAt = System.currentTimeMillis(),
                    )
                }
                delay(intervalSec * 1000L)
            }
        }
    }

    /** 停止轮询并清空活动号码 */
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _state.value = MonitorState()
    }

    fun toRecord(event: CodeArrivedEvent): NumberRecord = NumberRecord(
        time = event.time,
        phone = event.phone,
        sid = event.sid,
        projectName = event.projectName,
        sp = event.sp,
        phoneGsd = event.phoneGsd,
        sms = event.sms,
        yzm = event.yzm,
    )

    /** 从当前轮询状态构造到达事件（服务停止窗口期内事件被丢弃时的补发路径） */
    fun toArrivedEvent(state: MonitorState): CodeArrivedEvent = CodeArrivedEvent(
        phone = state.phone,
        sid = state.sid,
        projectName = state.projectName,
        sp = state.sp,
        phoneGsd = state.phoneGsd,
        sms = state.sms,
        yzm = state.yzm,
        time = if (state.lastCheckedAt > 0) state.lastCheckedAt else System.currentTimeMillis(),
    )
}
