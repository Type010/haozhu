package com.jmzs.app.ui.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 全局主题控制器。
 *
 * @param mode 主题模式（跟随系统 / 浅色 / 深色），由设置页持久化后传入
 */
@Composable
fun JmzsTheme(
    mode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit,
) {
    val controller = androidx.compose.runtime.remember(mode) {
        ThemeController(
            colorSchemeMode = mode,
            keyColor = androidx.compose.ui.graphics.Color(0xFF3D7BFD),
        )
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
