package com.jmzs.app.service

import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.local.AppSettings
import com.jmzs.app.data.local.NumberRecord
import com.jmzs.app.service.CodeMonitor
import com.jmzs.app.service.CodePollingService
import kotlinx.coroutines.flow.first

/**
 * 号码操作集合：供取号工作台与历史页复用。
 * 所有方法内部读取最新账号配置并处理「服务启动/停止」的联动。
 */
class PhoneActions(private val container: AppContainer) {

    private val repo get() = container.settingsRepository

    /**
     * 若有活动号码，先尽力释放并停掉轮询/服务。
     * 界面同一时间只展示一个号码，重取/重新占用前必须先释放旧号码，
     * 否则旧号码会一直占用平台并发槽位直到平台侧超时。
     */
    suspend fun releaseOldQuietly() {
        val monitor = CodeMonitor.state.value
        if (!monitor.active) return
        val released = try {
            val settings = repo.settings.first()
            container.apiService.cancelRecv(settings.server, settings.token, monitor.sid, monitor.phone)
            true
        } catch (_: Exception) {
            // 释放失败不阻断新取号流程
            false
        } finally {
            CodeMonitor.stopPolling()
            CodePollingService.stop(container.context)
        }
        if (released) recordReleased(monitor)
    }

    /** 指定号码（重新占用）并开始取码轮询 */
    suspend fun specify(phone: String, sid: String, projectName: String = "") {
        releaseOldQuietly()
        val settings = repo.settings.first()
        val response = container.apiService.specifyPhone(
            server = settings.server,
            token = settings.token,
            sid = sid,
            phone = phone,
            author = AppSettings.DEFAULT_AUTHOR,
        )
        repo.setLastSid(sid)
        CodeMonitor.startPolling(
            server = settings.server,
            token = settings.token,
            sid = sid,
            phone = phone,
            sp = response.sp,
            phoneGsd = response.phoneGsd,
            projectName = projectName,
            intervalSec = settings.pollIntervalSec,
        )
        if (settings.backgroundEnabled) {
            CodePollingService.start(container.context)
        }
    }

    /** 释放当前活动号码，返回平台是否释放成功（失败时本地状态已清理） */
    suspend fun releaseCurrent(): Boolean {
        val monitor = CodeMonitor.state.value
        if (!monitor.active) return false
        val settings = repo.settings.first()
        // 平台侧号码可能已失效（收码后被回收等），释放失败也必须清理本地状态，
        // 否则界面永远卡在"可释放"，再点永远失败
        val released = try {
            container.apiService.cancelRecv(settings.server, settings.token, monitor.sid, monitor.phone)
            true
        } catch (_: Exception) {
            false
        } finally {
            CodeMonitor.stopPolling()
            CodePollingService.stop(container.context)
        }
        if (released) recordReleased(monitor)
        return released
    }

    /** 拉黑当前活动号码，返回平台是否拉黑成功（失败时本地状态已清理） */
    suspend fun blacklistCurrent(): Boolean {
        val monitor = CodeMonitor.state.value
        if (!monitor.active) return false
        val settings = repo.settings.first()
        val blacklisted = try {
            container.apiService.addBlacklist(settings.server, settings.token, monitor.sid, monitor.phone)
            true
        } catch (_: Exception) {
            false
        } finally {
            CodeMonitor.stopPolling()
            CodePollingService.stop(container.context)
        }
        if (blacklisted) recordReleased(monitor)
        return blacklisted
    }

    /** 释放全部号码 */
    suspend fun releaseAll() {
        val monitor = CodeMonitor.state.value
        val settings = repo.settings.first()
        container.apiService.cancelAllRecv(settings.server, settings.token)
        CodeMonitor.stopPolling()
        CodePollingService.stop(container.context)
        if (monitor.active) recordReleased(monitor)
    }

    /** 把未收到验证码即被释放的号码写入历史，供用户回顾与拉黑 */
    private suspend fun recordReleased(monitor: CodeMonitor.MonitorState) {
        if (monitor.sms.isNotBlank() || monitor.yzm.isNotBlank()) return
        repo.addRecord(
            NumberRecord(
                time = System.currentTimeMillis(),
                phone = monitor.phone,
                sid = monitor.sid,
                projectName = monitor.projectName,
                sp = monitor.sp,
                phoneGsd = monitor.phoneGsd,
                status = NumberRecord.STATUS_RELEASED,
            ),
        )
    }

    /** 立即检查一次验证码（重启轮询触发即时请求） */
    suspend fun checkNow() {
        val monitor = CodeMonitor.state.value
        if (!monitor.active) return
        val settings = repo.settings.first()
        CodeMonitor.startPolling(
            server = settings.server,
            token = settings.token,
            sid = monitor.sid,
            phone = monitor.phone,
            sp = monitor.sp,
            phoneGsd = monitor.phoneGsd,
            projectName = monitor.projectName,
            intervalSec = settings.pollIntervalSec,
        )
        // 验证码到达后前台服务会自行停止；手动继续取码时若开启后台接码需重新拉起
        if (settings.backgroundEnabled) {
            CodePollingService.start(container.context)
        }
    }
}
