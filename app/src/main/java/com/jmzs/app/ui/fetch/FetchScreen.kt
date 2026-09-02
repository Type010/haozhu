package com.jmzs.app.ui.fetch

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmzs.app.data.ASCRIPTIONS
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.OPERATORS
import com.jmzs.app.data.PROVINCES
import com.jmzs.app.data.local.Project
import com.jmzs.app.ui.components.DialogButtonTextStyle
import com.jmzs.app.ui.components.IconTint
import com.jmzs.app.ui.components.copyToClipboard
import com.jmzs.app.ui.components.findActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BrandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4D9FFF), Color(0xFF3D7BFD), Color(0xFF6C5CE7)),
)

@Composable
fun FetchScreen(container: AppContainer) {
    val viewModel: FetchViewModel = viewModel { FetchViewModel(container) }
    val ui by viewModel.ui.collectAsState()
    val monitor by viewModel.monitor.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // 应用重新回到前台（获得焦点）时自动刷新一次余额
    val lifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSummary()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Header(ui, onRefresh = viewModel::refreshSummary)
            Spacer(Modifier.height(10.dp))

            ProjectSection(
                projects = ui.projects,
                selectedSid = ui.selectedSid,
                onSelect = viewModel::selectProject,
                onAdd = viewModel::addProject,
                onRemove = viewModel::removeProject,
            )
            Spacer(Modifier.height(10.dp))

            NumberSection(viewModel = viewModel, monitor = monitor)
            Spacer(Modifier.height(10.dp))

            // 筛选区占剩余空间：大屏完整显示，小屏内部滚动，保证底部按钮始终可见
            FilterSection(
                ui = ui,
                onOperator = viewModel::setOperator,
                onProvince = viewModel::setProvince,
                onAscription = viewModel::setAscription,
                onParagraph = viewModel::setParagraph,
                onExclude = viewModel::setExclude,
                onUid = viewModel::setUid,
                modifier = Modifier.weight(1f),
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = viewModel::getPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !ui.fetchingPhone && !ui.working,
                colors = ButtonDefaults.buttonColorsPrimary(),
                cornerRadius = 15.dp,
            ) {
                Text(
                    text = when {
                        ui.fetchingPhone -> "正在取号…"
                        monitor.active -> "重新取号"
                        else -> "立即取号"
                    },
                    color = if (ui.fetchingPhone || ui.working) MiuixTheme.colorScheme.disabledOnPrimaryButton
                    else MiuixTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (ui.phoneError.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = ui.phoneError,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ---------- 顶部：标题 + 账户卡片 ----------

@Composable
private fun Header(ui: FetchUiState, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(BrandGradient)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "账户余额",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = ui.balance,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "最大并发 ${ui.maxNum} 个号码",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable(enabled = !ui.summaryLoading, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.summaryLoading) {
                        // 刷新中让刷新图标原地旋转，不额外叠加圆环
                        val rotation by rememberInfiniteTransition(label = "balanceRefresh").animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900, easing = LinearEasing),
                            ),
                            label = "balanceRefreshRotation",
                        )
                        IconTint(
                            MiuixIcons.Refresh,
                            Color.White,
                            Modifier
                                .size(20.dp)
                                .rotate(rotation),
                        )
                    } else {
                        IconTint(MiuixIcons.Refresh, Color.White, Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------- 项目选择 ----------

@Composable
private fun ProjectSection(
    projects: List<Project>,
    selectedSid: String,
    onSelect: (String) -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<Project?>(null) }

    Column {
        SectionTitle("接码项目", "项目 ID（sid）取自平台项目列表")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            projects.forEach { project ->
                Chip(
                    text = project.name,
                    selected = project.sid == selectedSid,
                    onClick = { onSelect(project.sid) },
                    onLongClick = { pendingRemove = project },
                )
            }
            Chip(
                text = "＋ 添加",
                selected = false,
                onClick = { showAddDialog = true },
            )
        }
    }

    if (showAddDialog) {
        AddProjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { sid, name ->
                onAdd(sid, name)
                showAddDialog = false
            },
        )
    }

    pendingRemove?.let { project ->
        OverlayDialog(
            show = true,
            title = "删除项目",
            summary = "确定删除项目「${project.name}」（sid=${project.sid}）吗？",
            onDismissRequest = { pendingRemove = null },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = { pendingRemove = null },
                    modifier = Modifier.weight(1f),
                    textStyle = DialogButtonTextStyle,
                )
                Button(
                    onClick = {
                        onRemove(project.sid)
                        pendingRemove = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "删除",
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var sid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    OverlayDialog(
        show = true,
        title = "添加项目",
        summary = "sid 为平台项目 ID，昵称仅本地显示",
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            TextField(
                value = sid,
                onValueChange = { sid = it },
                modifier = Modifier.fillMaxWidth(),
                label = "项目 ID（sid）",
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = "项目昵称（可选）",
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    textStyle = DialogButtonTextStyle,
                )
                Button(
                    onClick = { onConfirm(sid, name) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "添加",
                        color = MiuixTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// ---------- 筛选条件 ----------

@Composable
private fun FilterSection(
    ui: FetchUiState,
    onOperator: (Int?) -> Unit,
    onProvince: (String?) -> Unit,
    onAscription: (Int?) -> Unit,
    onParagraph: (String) -> Unit,
    onExclude: (String) -> Unit,
    onUid: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProvinceDialog by remember { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        SectionTitle("筛选条件", "留空均为不限制")

        // 运营商
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(
                text = "运营商不限",
                selected = ui.selectedOperator == null,
                onClick = { onOperator(null) },
            )
            OPERATORS.forEach { operator ->
                Chip(
                    text = operator.name,
                    selected = ui.selectedOperator == operator.code,
                    onClick = { onOperator(operator.code) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 省份
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(
                text = "省份不限",
                selected = ui.selectedProvince == null,
                onClick = { onProvince(null) },
            )
            Chip(
                text = ui.selectedProvince?.let { code ->
                    PROVINCES.firstOrNull { it.code == code }?.name ?: "已选省份"
                } ?: "选择省份 ▾",
                selected = ui.selectedProvince != null,
                onClick = { showProvinceDialog = true },
            )
        }

        Spacer(Modifier.height(8.dp))

        // 号码类型
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ASCRIPTIONS.forEach { ascription ->
                Chip(
                    text = ascription.name,
                    selected = ui.selectedAscription == ascription.code,
                    onClick = { onAscription(ascription.code) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 号段
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = ui.paragraph,
                onValueChange = onParagraph,
                modifier = Modifier.weight(1f),
                label = "包含号段",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
            TextField(
                value = ui.exclude,
                onValueChange = onExclude,
                modifier = Modifier.weight(1f),
                label = "排除号段",
                useLabelAsPlaceholder = true,
                singleLine = true,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "多个号段用 | 分隔，如 1380|1580|1880",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))
        TextField(
            value = ui.uid,
            onValueChange = onUid,
            modifier = Modifier.fillMaxWidth(),
            label = "对接码 uid（可选）",
            useLabelAsPlaceholder = true,
            singleLine = true,
        )
    }

    if (showProvinceDialog) {
        OverlayDialog(
            show = true,
            title = "选择省份",
            onDismissRequest = { showProvinceDialog = false },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
            ) {
                item {
                    DialogOptionRow(
                        text = "不限",
                        selected = ui.selectedProvince == null,
                        onClick = {
                            onProvince(null)
                            showProvinceDialog = false
                        },
                    )
                }
                items(PROVINCES) { province ->
                    DialogOptionRow(
                        text = "${province.name}（${province.code}）",
                        selected = ui.selectedProvince == province.code,
                        onClick = {
                            onProvince(province.code)
                            showProvinceDialog = false
                        },
                    )
                }
            }
        }
    }
}

// ---------- 号码与验证码 ----------

@Composable
private fun NumberSection(
    viewModel: FetchViewModel,
    monitor: com.jmzs.app.service.CodeMonitor.MonitorState,
) {
    var showBlacklist by remember { mutableStateOf(false) }
    var showRelease by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val active = monitor.active
    val codeReady = active && (monitor.sms.isNotBlank() || monitor.yzm.isNotBlank())

    // 号码卡片（常驻固定高度：进度条/监听文字/验证码区全部预留位置，
    // 取号后卡片高度不变，避免把下方按钮挤下去）
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "当前号码",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (active) formatPhone(monitor.phone) else "-",
                        color = if (active) MiuixTheme.colorScheme.onBackground
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (active) MiuixTheme.colorScheme.surfaceVariant
                            else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        )
                        .clickable(enabled = active) {
                            copyToClipboard(context, "手机号", monitor.phone)
                            viewModelEvents(viewModel, "号码已复制")
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    IconTint(
                        MiuixIcons.Copy,
                        if (active) MiuixTheme.colorScheme.onSurfaceSecondary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (active) {
                    buildString {
                        if (monitor.sp.isNotBlank()) append(monitor.sp)
                        if (monitor.phoneGsd.isNotBlank()) append(" · ${monitor.phoneGsd}")
                        if (monitor.projectName.isNotBlank()) append(" · ${monitor.projectName}")
                    }.ifBlank { "接码中" }
                } else {
                    "尚未取号，点击下方按钮开始"
                },
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )

            if (active && monitor.polling) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), progress = null, height = 4.dp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "正在监听验证码…",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 12.sp,
                    )
                    if (monitor.lastCheckedAt > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "上次检查 ${timeFormat(monitor.lastCheckedAt)}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    // 验证码卡片（收到验证码后显示，含完整短信）
    if (codeReady) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "验证码已到达",
                        color = MiuixTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "复制",
                        onClick = {
                            copyToClipboard(context, "验证码", monitor.yzm)
                            viewModelEvents(viewModel, "验证码已复制")
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = monitor.yzm.ifBlank { "（无法识别数字验证码）" },
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (monitor.sms.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = monitor.sms,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // 操作按钮：无号码时置灰禁用；重新取号也会自动释放上一个号码
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = viewModel::checkNow,
            modifier = Modifier.weight(1f),
            enabled = active,
            cornerRadius = 13.dp,
        ) {
            Text(
                text = "立即获取",
                color = if (active) MiuixTheme.colorScheme.onSecondaryVariant
                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                fontSize = 14.sp,
            )
        }
        Button(
            onClick = { showRelease = true },
            modifier = Modifier.weight(1f),
            enabled = active,
            cornerRadius = 13.dp,
        ) {
            Text(
                text = "释放",
                color = if (active) MiuixTheme.colorScheme.onSecondaryVariant
                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                fontSize = 14.sp,
            )
        }
        Button(
            onClick = { showBlacklist = true },
            modifier = Modifier.weight(1f),
            enabled = active,
            cornerRadius = 13.dp,
        ) {
            Text(
                text = "拉黑",
                color = if (active) MiuixTheme.colorScheme.onSecondaryVariant
                else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
                fontSize = 14.sp,
            )
        }
    }

    // 确认对话框
    if (active && showRelease) {
        ConfirmDialog(
            title = "释放号码",
            summary = "确定释放 ${monitor.phone} 吗？释放后该号码可能再次分配给你。",
            onDismiss = { showRelease = false },
            onConfirm = {
                showRelease = false
                viewModel.releasePhone()
            },
        )
    }
    if (active && showBlacklist) {
        ConfirmDialog(
            title = "拉黑号码",
            summary = "确定拉黑 ${monitor.phone} 吗？拉黑后该号码不会再分配给你。",
            onDismiss = { showBlacklist = false },
            onConfirm = {
                showBlacklist = false
                viewModel.blacklistPhone()
            },
        )
    }
}

// ---------- 通用小组件 ----------

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = subtitle,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    // 固定高度保证运营商/省份/号码类型各行完全对齐
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MiuixTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DialogOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = if (selected) MiuixTheme.colorScheme.primary
            else MiuixTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            IconTint(MiuixIcons.Basic.Check, MiuixTheme.colorScheme.primary, Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    summary: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                textStyle = DialogButtonTextStyle,
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(
                    text = "确定",
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private fun viewModelEvents(viewModel: FetchViewModel, text: String) {
    viewModel.showToast(text)
}

/** 138 1234 5678 式分段显示 */
internal fun formatPhone(phone: String): String {
    if (phone.length != 11) return phone
    return "${phone.substring(0, 3)} ${phone.substring(3, 7)} ${phone.substring(7, 11)}"
}

private fun timeFormat(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
