package com.jmzs.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.local.NumberRecord
import com.jmzs.app.ui.components.DialogButtonTextStyle
import com.jmzs.app.ui.components.copyToClipboard
import com.jmzs.app.ui.fetch.formatPhone
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(container: AppContainer) {
    val viewModel: HistoryViewModel = viewModel { HistoryViewModel(container) }
    val history by viewModel.history.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selected by remember { mutableStateOf<NumberRecord?>(null) }
    var showBlacklistConfirm by remember { mutableStateOf<NumberRecord?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "取号历史",
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "点击记录可重新占用号码继续接码",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                }
                if (history.isNotEmpty()) {
                    TextButton(
                        text = "清空",
                        onClick = { showClearConfirm = true },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (history.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "暂无历史记录",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(history, key = { "${it.time}-${it.phone}" }) { record ->
                        HistoryCard(record = record, onClick = { selected = record })
                    }
                    item { Spacer(Modifier.height(70.dp)) }
                }
            }
        }

        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // 记录详情对话框
    selected?.let { record ->
        OverlayDialog(
            show = true,
            title = formatPhone(record.phone),
            summary = buildString {
                if (record.projectName.isNotBlank()) append("项目：${record.projectName}")
                if (record.sp.isNotBlank()) append(" · ${record.sp}")
                if (record.phoneGsd.isNotBlank()) append(" · ${record.phoneGsd}")
            }.ifBlank { "号码详情" },
            onDismissRequest = { selected = null },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (record.isReleased) {
                    Text(
                        text = "已释放 · 未收到验证码",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (record.yzm.isNotBlank()) {
                    Text(
                        text = "验证码：${record.yzm}",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (record.sms.isNotBlank()) {
                    Text(
                        text = record.sms,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = "时间：${historyTimeFormat(record.time)}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        text = "关闭",
                        onClick = { selected = null },
                        modifier = Modifier.weight(1f),
                        textStyle = DialogButtonTextStyle,
                    )
                    if (record.yzm.isNotBlank()) {
                        TextButton(
                            text = "复制",
                            onClick = {
                                copyToClipboard(context, "验证码", record.yzm)
                                viewModel.showToast("验证码已复制")
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = DialogButtonTextStyle,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.reuse(record)
                            selected = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = "占用",
                            color = MiuixTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "拉黑该号码",
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBlacklistConfirm = record }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    // 拉黑确认对话框
    showBlacklistConfirm?.let { record ->
        OverlayDialog(
            show = true,
            title = "拉黑号码",
            summary = "确定拉黑 ${formatPhone(record.phone)} 吗？拉黑后该号码不会再分配给你。",
            onDismissRequest = { showBlacklistConfirm = null },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showBlacklistConfirm = null },
                    modifier = Modifier.weight(1f),
                    textStyle = DialogButtonTextStyle,
                )
                Button(
                    onClick = {
                        viewModel.blacklist(record)
                        showBlacklistConfirm = null
                        selected = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "拉黑",
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        OverlayDialog(
            show = true,
            title = "清空历史",
            summary = "确定清空全部取号历史记录吗？",
            onDismissRequest = { showClearConfirm = false },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.weight(1f),
                    textStyle = DialogButtonTextStyle,
                )
                Button(
                    onClick = {
                        viewModel.clear()
                        showClearConfirm = false
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "清空",
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(record: NumberRecord, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPhone(record.phone),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                if (record.yzm.isNotBlank()) {
                    Text(
                        text = record.yzm,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                } else if (record.isReleased) {
                    Text(
                        text = "已释放",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = "未收到码",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        if (record.projectName.isNotBlank()) append(record.projectName)
                        if (record.sp.isNotBlank()) append(" · ${record.sp}")
                        if (record.phoneGsd.isNotBlank()) append(" · ${record.phoneGsd}")
                    }.ifBlank { "sid=${record.sid}" },
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = historyTimeFormat(record.time),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun historyTimeFormat(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
