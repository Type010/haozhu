package com.jmzs.app.data

import android.content.Context
import com.jmzs.app.data.api.ApiService
import com.jmzs.app.data.local.SettingsRepository
import com.jmzs.app.service.CodeMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 进程级依赖容器。监听取码事件，统一写入历史记录（保证后台前台只写一份）。
 */
class AppContainer(val context: Context) {

    val settingsRepository = SettingsRepository(context)
    val apiService = ApiService()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            CodeMonitor.codeArrived.collect { event ->
                settingsRepository.addRecord(CodeMonitor.toRecord(event))
            }
        }
    }
}
