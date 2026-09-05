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
 * 号码到手开始监听验证码时启动并常驻"正在监听验证码"通知，
 * 收到验证码后把该通知原地更新为"验证码已到达"并保留在通知栏，随后服务自停。
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
            // startForegroundService 启动的服务必须先兑现前台契约再退出，
            // 否则系统视为违约（Android 12+ 直接抛异常），通知也可能残留
            startForegroundCompat(buildEmptyNotification())
            stopSelf()
            return START_NOT_STICKY
        }
        // 验证码在服务停止窗口期已到达（事件无订阅者被丢弃，如收码后点"立即获取"、
        // 占用已有验证码的号码）：直接把通知更新为验证码内容，不再挂"正在监听"
        if (state.yzm.isNotBlank() || state.sms.isNotBlank()) {
            postCodeNotification(CodeMonitor.toArrivedEvent(state))
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(buildListeningNotification(state))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID_LISTENING, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID_LISTENING, notification)
        }
    }

    /** 空状态的占位通知：仅供服务兑现前台契约后立即退出使用 */
    private fun buildEmptyNotification(): Notification =
        Notification.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_code)
            .setContentTitle("")
            .build()

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

    /** 已发过通知的验证码标识，事件流与状态补发两条路径可能带来同一验证码，去重避免重复覆盖 */
    private var lastNotifiedKey: String? = null

    private fun postCodeNotification(event: CodeArrivedEvent) {
        val key = "${event.phone}|${event.yzm}|${event.sms}"
        if (key == lastNotifiedKey) return
        lastNotifiedKey = key
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
        // 把通知栏里已有的常驻通知(1001"正在监听验证码")原地更新为验证码内容，
        // 再脱离前台保留在通知栏——不新发通知，也不因服务停止而消失
        val notification = Notification.Builder(this, CHANNEL_LISTENING)
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
        startForeground(NOTIFICATION_ID_LISTENING, notification)
        // 通知脱离前台身份保留在通知栏，随后服务停止不会把它撤掉
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val listening = NotificationChannel(
            CHANNEL_LISTENING,
            "接码监听",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "后台轮询验证码时常驻显示；收到验证码后原地更新为验证码内容" }
        manager.createNotificationChannels(listOf(listening))
    }

    companion object {
        const val CHANNEL_LISTENING = "jmzs_listening"
        const val NOTIFICATION_ID_LISTENING = 1001

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
