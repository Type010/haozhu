package com.jmzs.app.ui.fetch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.api.ApiException
import com.jmzs.app.data.api.PhoneFilters
import com.jmzs.app.data.local.Project
import com.jmzs.app.service.CodeMonitor
import com.jmzs.app.service.CodePollingService
import com.jmzs.app.service.PhoneActions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FetchUiState(
    /** 账户余额 */
    val balance: String = "--",
    /** 最大并发数 */
    val maxNum: Int = 0,
    val summaryLoading: Boolean = false,
    // 项目
    val projects: List<Project> = emptyList(),
    val selectedSid: String = "",
    // 筛选条件
    val selectedOperator: Int? = null,
    val selectedProvince: String? = null,
    val selectedAscription: Int? = null,
    val paragraph: String = "",
    val exclude: String = "",
    val uid: String = "",
    // 取号状态
    val fetchingPhone: Boolean = false,
    val phoneError: String = "",
    val phone: String = "",
    val phoneSp: String = "",
    val phoneGsd: String = "",
    // 操作中标记
    val working: Boolean = false,
)

class FetchViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.settingsRepository
    private val actions = PhoneActions(container)

    /** 取码轮询引擎状态（与页面无关，后台仍继续） */
    val monitor: StateFlow<CodeMonitor.MonitorState> = CodeMonitor.state

    private val _ui = MutableStateFlow(FetchUiState())
    val ui: StateFlow<FetchUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.settings.collect { settings ->
                _ui.update {
                    it.copy(
                        projects = settings.projects,
                        selectedSid = settings.lastSid,
                    )
                }
            }
        }
        refreshSummary()
    }

    private fun toast(text: String) = _events.tryEmit(text)

    /** 供界面直接提示（复制成功等） */
    fun showToast(text: String) = toast(text)

    /** 刷新余额 */
    fun refreshSummary() {
        viewModelScope.launch {
            _ui.update { it.copy(summaryLoading = true) }
            val settings = repo.settings.first()
            try {
                val response = container.apiService.getSummary(settings.server, settings.token)
                _ui.update {
                    it.copy(
                        balance = response.money.ifBlank { "0.00" },
                        maxNum = response.num ?: 0,
                    )
                }
            } catch (e: ApiException) {
                toast(e.message ?: "余额查询失败")
            } catch (e: Exception) {
                toast("余额查询失败：${e.message ?: ""}")
            } finally {
                _ui.update { it.copy(summaryLoading = false) }
            }
        }
    }

    /** 获取手机号并开始轮询验证码 */
    fun getPhone() {
        val state = _ui.value
        if (state.fetchingPhone || state.working) return
        if (state.selectedSid.isBlank()) {
            toast("请先添加或选择一个项目（sid）")
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(fetchingPhone = true, phoneError = "") }
            val settings = repo.settings.first()
            try {
                // 界面同一时间只保留一个号码：先释放上一个占用号码，避免并发槽位泄漏
                actions.releaseOldQuietly()
                val response = container.apiService.getPhone(
                    server = settings.server,
                    token = settings.token,
                    sid = state.selectedSid,
                    filters = PhoneFilters(
                        isp = state.selectedOperator,
                        province = state.selectedProvince,
                        ascription = state.selectedAscription,
                        paragraph = state.paragraph,
                        exclude = state.exclude,
                        uid = state.uid,
                        author = settings.author,
                    ),
                )
                if (response.phone.isBlank()) {
                    throw ApiException("取号失败：平台未返回号码")
                }
                repo.setLastSid(state.selectedSid)
                _ui.update {
                    it.copy(phone = response.phone, phoneSp = response.sp, phoneGsd = response.phoneGsd)
                }
                val projectName = state.projects.firstOrNull { it.sid == state.selectedSid }?.name.orEmpty()
                CodeMonitor.startPolling(
                    server = settings.server,
                    token = settings.token,
                    sid = state.selectedSid,
                    phone = response.phone,
                    sp = response.sp,
                    phoneGsd = response.phoneGsd,
                    projectName = projectName,
                    intervalSec = settings.pollIntervalSec,
                )
                if (settings.backgroundEnabled) {
                    CodePollingService.start(container.context)
                }
            } catch (e: ApiException) {
                _ui.update { it.copy(phoneError = e.message ?: "取号失败") }
            } catch (e: Exception) {
                _ui.update { it.copy(phoneError = "取号失败：${e.message ?: ""}") }
            } finally {
                _ui.update { it.copy(fetchingPhone = false) }
            }
        }
    }

    /** 指定号码（重新占用，用于历史号码再次接码） */
    fun specifyPhone(phone: String, sid: String, projectName: String = "") {
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            try {
                actions.specify(phone, sid, projectName)
                toast("已重新占用 $phone，开始取码")
            } catch (e: ApiException) {
                toast(e.message ?: "指定号码失败")
            } catch (e: Exception) {
                toast("指定号码失败：${e.message ?: ""}")
            } finally {
                _ui.update { it.copy(working = false) }
            }
        }
    }

    /** 立即检查一次验证码（重启轮询，立即请求） */
    fun checkNow() {
        viewModelScope.launch { actions.checkNow() }
    }

    /** 释放当前号码 */
    fun releasePhone() {
        if (!CodeMonitor.state.value.active || _ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            try {
                actions.releaseCurrent()
                toast("号码已释放")
            } catch (e: ApiException) {
                toast(e.message ?: "释放失败")
            } catch (e: Exception) {
                toast("释放失败：${e.message ?: ""}")
            } finally {
                _ui.update { it.copy(working = false) }
            }
        }
    }

    /** 拉黑当前号码 */
    fun blacklistPhone() {
        if (!CodeMonitor.state.value.active || _ui.value.working) return
        viewModelScope.launch {
            _ui.update { it.copy(working = true) }
            try {
                actions.blacklistCurrent()
                toast("已拉黑该号码，不再分配")
            } catch (e: ApiException) {
                toast(e.message ?: "拉黑失败")
            } catch (e: Exception) {
                toast("拉黑失败：${e.message ?: ""}")
            } finally {
                _ui.update { it.copy(working = false) }
            }
        }
    }

    // ---- 项目与筛选条件 ----

    fun selectProject(sid: String) {
        _ui.update { it.copy(selectedSid = sid) }
        viewModelScope.launch { repo.setLastSid(sid) }
    }

    fun addProject(sid: String, name: String) {
        val cleanSid = sid.trim()
        if (cleanSid.isEmpty()) {
            toast("项目 ID（sid）不能为空")
            return
        }
        viewModelScope.launch {
            repo.addProject(Project(sid = cleanSid, name = name.trim().ifBlank { "项目 $cleanSid" }))
            repo.setLastSid(cleanSid)
            toast("项目已添加")
        }
    }

    fun removeProject(sid: String) {
        viewModelScope.launch { repo.removeProject(sid) }
    }

    fun setOperator(isp: Int?) = _ui.update { it.copy(selectedOperator = isp) }
    fun setProvince(code: String?) = _ui.update { it.copy(selectedProvince = code) }
    fun setAscription(code: Int?) = _ui.update { it.copy(selectedAscription = code) }
    fun setParagraph(value: String) = _ui.update { it.copy(paragraph = value) }
    fun setExclude(value: String) = _ui.update { it.copy(exclude = value) }
    fun setUid(value: String) = _ui.update { it.copy(uid = value) }
}
