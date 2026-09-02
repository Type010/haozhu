package com.jmzs.app

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.jmzs.app.data.local.ThemeMode
import com.jmzs.app.ui.components.findActivity
import com.jmzs.app.ui.login.LoginScreen
import com.jmzs.app.ui.main.MainScreen
import com.jmzs.app.ui.theme.JmzsTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** 应用根组件：主题 + 登录/主页切换 */
@Composable
fun AppRoot() {
    val container = (LocalContext.current.applicationContext as JmzsApp).container
    val settings by container.settingsRepository.settings.collectAsState(initial = null)

    // Miuix 的弹窗/下拉组件内部使用 androidx.navigationevent 的 NavigationBackHandler，
    // 必须在根组合处提供一个 NavigationEventDispatcherOwner，否则打开任何弹窗都会崩溃
    val navigationOwner = rememberNavigationEventDispatcherOwner(parent = null)
    val context = LocalContext.current
    LaunchedEffect(navigationOwner) {
        // Android 13+：接入系统返回手势（输入会随处理器数量自动注册/注销）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.findActivity()?.let { activity ->
                navigationOwner.navigationEventDispatcher.addInput(
                    OnBackInvokedDefaultInput(activity.onBackInvokedDispatcher),
                )
            }
        }
    }

    // DataStore 首次发射前显示空白占位，避免闪现登录页
    val current = settings ?: run {
        Box(Modifier.fillMaxSize())
        return
    }

    val mode = when (current.themeMode) {
        ThemeMode.LIGHT -> ColorSchemeMode.Light
        ThemeMode.DARK -> ColorSchemeMode.Dark
        ThemeMode.SYSTEM -> ColorSchemeMode.System
    }

    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides navigationOwner) {
        JmzsTheme(mode) {
            if (current.isLoggedIn) {
                MainScreen(container = container)
            } else {
                LoginScreen(container = container)
            }
        }
    }
}
