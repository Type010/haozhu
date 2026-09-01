package com.jmzs.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.local.NumberRecord
import com.jmzs.app.service.PhoneActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository
    private val actions = PhoneActions(container)

    /** 历史记录（最新在前） */
    val history: kotlinx.coroutines.flow.StateFlow<List<NumberRecord>> =
        repo.settings
            .map { it.history }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** 重新占用该历史号码并开始取码 */
    fun reuse(record: NumberRecord) {
        viewModelScope.launch {
            try {
                actions.specify(record.phone, record.sid, record.projectName)
                _events.tryEmit("已重新占用 ${record.phone}，开始取码")
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "重新占用失败")
            }
        }
    }

    /** 拉黑该历史号码（平台侧不再分配） */
    fun blacklist(record: NumberRecord) {
        viewModelScope.launch {
            try {
                val settings = container.settingsRepository.settings.first()
                container.apiService.addBlacklist(
                    server = settings.server,
                    token = settings.token,
                    sid = record.sid,
                    phone = record.phone,
                )
                _events.tryEmit("已拉黑 ${record.phone}，不再分配")
            } catch (e: Exception) {
                _events.tryEmit(e.message ?: "拉黑失败")
            }
        }
    }

    fun clear() {
        viewModelScope.launch { repo.clearHistory() }
    }

    fun showToast(text: String) = _events.tryEmit(text)
}
