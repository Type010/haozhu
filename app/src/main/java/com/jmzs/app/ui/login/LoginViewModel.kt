package com.jmzs.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.api.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val server: String = "api.haozhuma.com",
    val username: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String = "",
    val loggedIn: Boolean = false,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onServerChange(value: String) = _uiState.update { it.copy(server = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }

    fun login() {
        val state = _uiState.value
        if (state.loading) return
        if (state.server.isBlank()) {
            _uiState.update { it.copy(error = "请输入服务器地址") }
            return
        }
        if (state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入账号和密码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = "") }
            try {
                val response = container.apiService.login(
                    server = state.server,
                    user = state.username,
                    pass = state.password,
                )
                if (response.token.isBlank()) {
                    throw ApiException("登录失败：服务器未返回令牌")
                }
                container.settingsRepository.saveLogin(
                    server = state.server,
                    username = state.username,
                    password = state.password,
                    token = response.token,
                )
                _uiState.update { it.copy(loading = false, loggedIn = true) }
            } catch (e: ApiException) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "登录失败") }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.message ?: "登录失败") }
            }
        }
    }
}
