package com.jmzs.app.ui.login

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmzs.app.data.AppContainer
import com.jmzs.app.data.DEFAULT_SERVERS
import com.jmzs.app.ui.components.IconTint
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 品牌渐变 */
private val BrandGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF4D9FFF), Color(0xFF3D7BFD), Color(0xFF6C5CE7)),
)

@Composable
fun LoginScreen(container: AppContainer) {
    val viewModel: LoginViewModel = viewModel { LoginViewModel(container) }
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))

        // Logo：渐变圆角 + 消息图标
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            IconTint(MiuixIcons.Messages, Color.White, Modifier.size(44.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "接码助手",
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "豪猪平台 · API 客户端",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(36.dp))

        TextField(
            value = state.server,
            onValueChange = viewModel::onServerChange,
            modifier = Modifier.fillMaxWidth(),
            label = "服务器地址",
            singleLine = true,
        )

        // 线路快捷选择
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DEFAULT_SERVERS.forEach { server ->
                val selected = state.server == server
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MiuixTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { viewModel.onServerChange(server) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (server == DEFAULT_SERVERS[0]) "线路 1" else "线路 2",
                        color = if (selected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        TextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = "账号",
            singleLine = true,
        )

        Spacer(Modifier.height(14.dp))

        TextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = "密码",
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )

        if (state.error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.error,
                color = MiuixTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = viewModel::login,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !state.loading,
            colors = ButtonDefaults.buttonColorsPrimary(),
            cornerRadius = 14.dp,
        ) {
            Text(
                text = if (state.loading) "登录中…" else "登 录",
                color = if (state.loading) MiuixTheme.colorScheme.disabledOnPrimaryButton
                else MiuixTheme.colorScheme.onPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "登录一次即可获得 token，后续自动登录\n服务器地址可自定义，例如 https://api.haozhuma.com",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(40.dp))
    }
}
