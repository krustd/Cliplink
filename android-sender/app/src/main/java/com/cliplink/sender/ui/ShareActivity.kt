package com.cliplink.sender.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cliplink.sender.data.DeviceStore
import com.cliplink.sender.service.SenderClient
import kotlin.concurrent.thread

/**
 * Transparent activity that receives text from the system share sheet,
 * sends it to the selected ClipLink receiver, then dismisses itself.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") {
            finish()
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (text.isNullOrBlank()) {
            toast("没有收到文本内容")
            finish()
            return
        }

        val device = DeviceStore(this).load()
        if (device == null) {
            toast("请先在 ClipLink 中选择目标设备")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        toast("正在发送到 ${device.name}…")
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
