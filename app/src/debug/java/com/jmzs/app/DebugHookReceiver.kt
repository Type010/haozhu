package com.jmzs.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.jmzs.app.service.CodeMonitor
import com.jmzs.app.service.CodePollingService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 仅 debug 变体编译：adb 广播注入取码状态/事件，黑盒测试通知链路。
 *
 *   adb shell am broadcast -a com.jmzs.app.DEBUG_POLLING       -n com.jmzs.app/.DebugHookReceiver  # 号码到手(监听中)
 *   adb shell am broadcast -a com.jmzs.app.DEBUG_INJECT_CODE   -n com.jmzs.app/.DebugHookReceiver  # 验证码到达
 *   adb shell am broadcast -a com.jmzs.app.DEBUG_START_SERVICE -n com.jmzs.app/.DebugHookReceiver  # 启动前台服务
 *   adb shell am broadcast -a com.jmzs.app.DEBUG_STOP_SERVICE  -n com.jmzs.app/.DebugHookReceiver  # 停止服务
 */
class DebugHookReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                ACTION_POLLING -> {
                    CodeMonitor.startPolling(
                        server = "http://127.0.0.1:9",
                        token = "debug",
                        sid = "debug",
                        phone = intent.getStringExtra(EXTRA_PHONE) ?: "138 0000 0000",
                        projectName = "调试项目",
                        intervalSec = 60,
                    )
                    Log.i(TAG, "state -> polling (active, no code)")
                }
                ACTION_INJECT_CODE -> {
                    DebugInjector.injectCode(
                        phone = intent.getStringExtra(EXTRA_PHONE) ?: "138 0000 0000",
                        yzm = intent.getStringExtra(EXTRA_YZM) ?: "654321",
                        sms = intent.getStringExtra(EXTRA_SMS) ?: "【调试】您的验证码为 654321，请勿泄露",
                        emitEvent = intent.getBooleanExtra(EXTRA_EMIT, true),
                    )
                }
                ACTION_START_SERVICE -> CodePollingService.start(context)
                ACTION_STOP_SERVICE -> CodePollingService.stop(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "debug hook failed", e)
        }
    }

    companion object {
        const val TAG = "DebugHook"
        const val ACTION_POLLING = "com.jmzs.app.DEBUG_POLLING"
        const val ACTION_INJECT_CODE = "com.jmzs.app.DEBUG_INJECT_CODE"
        const val ACTION_START_SERVICE = "com.jmzs.app.DEBUG_START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.jmzs.app.DEBUG_STOP_SERVICE"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_YZM = "yzm"
        const val EXTRA_SMS = "sms"
        const val EXTRA_EMIT = "emit"
    }
}

/** 反射操作 CodeMonitor 私有状态，避免为测试改动产品代码 */
private object DebugInjector {

    fun injectCode(phone: String, yzm: String, sms: String, emitEvent: Boolean) {
        val stateField = CodeMonitor::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = stateField.get(CodeMonitor) as MutableStateFlow<Any>

        val stateClass = Class.forName("com.jmzs.app.service.CodeMonitor\$MonitorState")
        val ctor = stateClass.declaredConstructors.first { it.parameterTypes.size == 14 }
        ctor.isAccessible = true
        val newState = ctor.newInstance(
            true,                          // active
            "http://127.0.0.1:9",          // server
            "debug",                       // sid
            phone,                         // phone
            "调试运营商",                    // sp
            "调试归属地",                    // phoneGsd
            "调试项目",                      // projectName
            false,                         // polling
            sms,                           // sms
            yzm,                           // yzm
            "",                            // lastError
            System.currentTimeMillis(),    // lastCheckedAt
            0,                             // default mask
            null,                          // DefaultConstructorMarker
        )
        stateFlow.value = newState
        Log.i(DebugHookReceiver.TAG, "state -> active with code yzm=$yzm")

        if (!emitEvent) return
        val flowField = CodeMonitor::class.java.getDeclaredField("_codeArrived")
        flowField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = flowField.get(CodeMonitor) as MutableSharedFlow<Any>
        val eventClass = Class.forName("com.jmzs.app.service.CodeMonitor\$CodeArrivedEvent")
        val eventCtor = eventClass.constructors.first()
        val emitted = flow.tryEmit(
            eventCtor.newInstance(
                phone, "debug", "调试项目", "调试运营商", "调试归属地",
                sms, yzm, System.currentTimeMillis(),
            ),
        )
        Log.i(DebugHookReceiver.TAG, "codeArrived.tryEmit=$emitted subscribers=${flow.subscriptionCount.value}")
    }
}
