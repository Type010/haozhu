package com.jmzs.app.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 通知栏「复制验证码」按钮的广播接收器 */
class CopyCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val yzm = intent.getStringExtra(EXTRA_YZM).orEmpty()
        if (yzm.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("验证码", yzm))
        Toast.makeText(context, "验证码已复制", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_YZM = "extra_yzm"
    }
}
