package com.jmzs.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.POLL_INTERVALS
import com.jmzs.app.data.local.AppSettings
import com.jmzs.app.data.local.ThemeMode
import com.jmzs.app.service.CodeMonitor
import com.jmzs.app.service.CodePollingService
import com.jmzs.app.ui.components.DialogButtonTextStyle
import com.jmzs.app.ui.components.copyToClipboard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(container: AppContainer) {
    val repo = container.settingsRepository
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val settings by repo.settings.collectAsState(initial = AppSettings())
    val snackbarHostState = remember { SnackbarHostState() }

    var showServerDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch { snackbarHostState.showSnackbar("通知权限已开启") }
        } else {
            scope.launch { snackbarHostState.showSnackbar("未授予通知权限，后台收码将无法弹出提醒") }
        }
    }

    val intervalItems = remember {
        POLL_INTERVALS.map { DropdownItem(text = "$it 秒") }
    }
    val intervalIndex = POLL_INTERVALS.indexOf(settings.pollIntervalSec).coerceAtLeast(0)

    val themeItems = remember {
        listOf(
            DropdownItem(text = "跟随系统"),
            DropdownItem(text = "浅色"),
            DropdownItem(text = "深色"),
        )
    }
    val themeIndex = when (settings.themeMode) {
        ThemeMode.SYSTEM -> 0
        ThemeMode.LIGHT -> 1
        ThemeMode.DARK -> 2
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "设置",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(14.dp))

            // 账号
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column {
                    ArrowPreference(
                        title = "账号",
                        summary = settings.username.ifBlank { "未记录账号名" },
                    )
                    ArrowPreference(
                        title = "Token 令牌",
                        summary = "点击查看并复制",
                        onClick = { showTokenDialog = true },
                    )
                    ArrowPreference(
                        title = "服务器地址",
                        summary = settings.server,
                        onClick = { showServerDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 接码设置
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column {
                    SwitchPreference(
                        title = "后台接码",
                        summary = "退到后台继续轮询，收到验证码推送通知",
                        checked = settings.backgroundEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            scope.launch {
                                repo.setBackgroundEnabled(enable)
                                // 关闭开关时同步停掉正在运行的后台服务，避免状态脱钩
                                if (!enable) {
                                    CodePollingService.stop(context)
                                }
                            }
                        },
                    )
                    OverlaySpinnerPreference(
                        items = intervalItems,
                        selectedIndex = intervalIndex,
                        title = "轮询间隔",
                        summary = "验证码查询频率",
                        onSelectedIndexChange = { index ->
                            scope.launch { repo.setPollInterval(POLL_INTERVALS[index]) }
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // 通用
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column {
                    OverlaySpinnerPreference(
                        items = themeItems,
                        selectedIndex = themeIndex,
                        title = "主题",
                        summary = "界面明暗外观",
                        onSelectedIndexChange = { index ->
                            scope.launch {
                                repo.setThemeMode(
                                    when (index) {
                                        1 -> ThemeMode.LIGHT
                                        2 -> ThemeMode.DARK
                                        else -> ThemeMode.SYSTEM
                                    },
                                )
                            }
                        },
                    )
                    ArrowPreference(
                        title = "关于",
                        summary = "版本 1.1.2",
                        onClick = { showAboutDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Column {
                    ArrowPreference(
                        title = "退出登录",
                        summary = "清除本地令牌与账号",
                        onClick = { showLogoutDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(80.dp))
        }

        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // ---------- 编辑对话框 ----------

    EditTextDialog(
        show = showServerDialog,
        title = "服务器地址",
        initial = settings.server,
        placeholder = "api.haozhuma.com",
        onDismiss = { showServerDialog = false },
        onConfirm = { value ->
            scope.launch { repo.setServer(value) }
            showServerDialog = false
        },
    )

    OverlayDialog(
        show = showTokenDialog,
        title = "Token 令牌",
        summary = "令牌为固定值，修改密码后才会变化",
        onDismissRequest = { showTokenDialog = false },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = settings.token.ifBlank { "（无令牌）" },
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(14.dp))
            DialogActionButtons(
                cancelText = "关闭",
                confirmText = "复制",
                onCancel = { showTokenDialog = false },
                onConfirm = {
                    copyToClipboard(context, "token", settings.token)
                    showTokenDialog = false
                    scope.launch { snackbarHostState.showSnackbar("令牌已复制") }
                },
            )
        }
    }

    OverlayDialog(
        show = showLogoutDialog,
        title = "退出登录",
        summary = "退出后将清除本地保存的令牌与账号，需重新登录。",
        onDismissRequest = { showLogoutDialog = false },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            DialogActionButtons(
                cancelText = "取消",
                confirmText = "退出",
                onCancel = { showLogoutDialog = false },
                onConfirm = {
                    CodeMonitor.stopPolling()
                    CodePollingService.stop(context)
                    scope.launch { repo.clearLogin() }
                    showLogoutDialog = false
                },
            )
        }
    }

    OverlayDialog(
        show = showAboutDialog,
        title = "关于接码助手",
        onDismissRequest = { showAboutDialog = false },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = "版本 1.1.2",
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "豪猪平台 API 安卓客户端\n基于 Miuix（compose-miuix-ui）构建\n任何以升级等理由要求转账的均为骗子，谨防受骗",
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "关闭",
                    onClick = { showAboutDialog = false },
                    modifier = Modifier.weight(1f),
                    textStyle = DialogButtonTextStyle,
                )
            }
        }
    }
}

/**
 * 弹窗底部双按钮：次要按钮（取消/关闭）与主按钮（保存/复制/退出）统一尺寸与字号。
 */
@Composable
private fun DialogActionButtons(
    cancelText: String,
    confirmText: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(
            text = cancelText,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            textStyle = DialogButtonTextStyle,
        )
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            Text(
                text = confirmText,
                color = MiuixTheme.colorScheme.onPrimary,
                style = DialogButtonTextStyle,
            )
        }
    }
}


@Composable
private fun EditTextDialog(
    show: Boolean,
    title: String,
    initial: String,
    placeholder: String,
    summary: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // show 变为 true（重新打开）时回填最新的初始值
    var value by remember(show) { mutableStateOf(initial) }

    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            TextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = placeholder,
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            DialogActionButtons(
                cancelText = "取消",
                confirmText = "保存",
                onCancel = onDismiss,
                onConfirm = { onConfirm(value) },
            )
        }
    }
}
