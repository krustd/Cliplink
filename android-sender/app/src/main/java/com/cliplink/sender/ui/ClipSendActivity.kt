package com.cliplink.sender.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceStore
import com.cliplink.sender.service.SenderClient
import kotlin.concurrent.thread

/**
 * Dialog-style activity launched from the notification "发送剪贴板" button.
 *
 * A dialog with actual visible content reliably receives window focus on all
 * devices including Huawei EMUI. We read the clipboard only after focus is
 * confirmed via onWindowFocusChanged, satisfying the Android 10+ restriction.
 */
class ClipSendActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private var sent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clip_send)
        statusText = findViewById(R.id.clipSendStatus)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || sent) return
        sent = true
        readAndSend()
    }

    private fun readAndSend() {
        val device = DeviceStore(this).load()
        if (device == null) {
            toast("请先在 ClipLink 中选择目标设备")
            finish()
            return
        }

        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            toast("剪贴板为空")
            finish()
            return
        }

        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        if (text.isNullOrBlank()) {
            toast("剪贴板内容为空")
            finish()
            return
        }

        statusText.text = "正在发送到 ${device.name}…"
        thread(isDaemon = true) {
            val result = SenderClient.pushText(device, text)
            runOnUiThread {
                if (result.isSuccess) {
                    toast("✅ 已发送到 ${device.name}")
                } else {
                    toast("❌ 发送失败: ${result.exceptionOrNull()?.message}")
                }
                finish()
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
