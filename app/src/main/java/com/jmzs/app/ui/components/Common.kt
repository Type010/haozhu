package com.jmzs.app.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 给 ImageVector 图标着色（MiuixIcons 返回未着色矢量） */
@Composable
fun IconTint(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/** 复制文本到系统剪贴板 */
fun copyToClipboard(context: Context, label: String, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java)
    manager?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * 弹窗动作按钮的统一字号。次要按钮（TextButton）若不指定会使用 miuix 默认 17sp，
 * 与主按钮内容 14sp 不一致，导致两按钮高度与字体不同，这里统一为 14sp。
 */
val DialogButtonTextStyle: TextStyle
    @Composable
    get() = MiuixTheme.textStyles.main.copy(fontSize = 14.sp)

/** 从任意 Context 向上查找宿主 Activity */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
