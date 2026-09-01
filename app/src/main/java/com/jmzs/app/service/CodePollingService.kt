package com.jmzs.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.jmzs.app.MainActivity
import com.jmzs.app.R
import com.jmzs.app.service.CodeMonitor.CodeArrivedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 后台接码前台服务：
 * 有活动号码时保活进程持续轮询，收到验证码后推送高优先级通知并自动停止。
 */
class CodePollingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannels()
        scope.launch {
            CodeMonitor.codeArrived.collect { event ->
                postCodeNotification(event)
                stopSelf()
            }
        }
        scope.launch {
            CodeMonitor.state.collect { state ->
                if (!state.active) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val state = CodeMonitor.state.value
        if (!state.active) {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildListeningNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_LISTENING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID_LISTENING, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildListeningNotification(state: CodeMonitor.MonitorState): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_code)
            .setContentTitle("正在监听验证码")
            .setContentText("${state.phone}（${state.projectName.ifBlank { "sid=${state.sid}" }}）")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun postCodeNotification(event: CodeArrivedEvent) {
        val copyIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, CopyCodeReceiver::class.java).putExtra(CopyCodeReceiver.EXTRA_YZM, event.yzm),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (event.yzm.isNotBlank()) {
            "验证码 ${event.yzm} · ${event.phone}"
        } else {
            "收到短信 · ${event.phone}"
        }
        val notification = Notification.Builder(this, CHANNEL_CODE)
            .setSmallIcon(R.drawable.ic_stat_code)
            .setContentTitle("验证码已到达")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("${event.sms}\n\n号码：${event.phone}"))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_code),
                    "复制验证码",
                    copyIntent,
                ).build(),
            )
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_CODE, notification)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val listening = NotificationChannel(
            CHANNEL_LISTENING,
            "接码监听",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "后台轮询验证码时常驻显示" }
        val code = NotificationChannel(
            CHANNEL_CODE,
            "验证码提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "收到验证码时提醒" }
        manager.createNotificationChannels(listOf(listening, code))
    }

    companion object {
        const val CHANNEL_LISTENING = "jmzs_listening"
        const val CHANNEL_CODE = "jmzs_code"
        const val NOTIFICATION_ID_LISTENING = 1001
        const val NOTIFICATION_ID_CODE = 1002

        /** 有活动号码且开启后台接码时启动服务 */
        fun start(context: Context) {
            val intent = Intent(context, CodePollingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CodePollingService::class.java))
        }
    }
}
