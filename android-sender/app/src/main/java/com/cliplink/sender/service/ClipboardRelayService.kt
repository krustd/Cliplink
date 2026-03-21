package com.cliplink.sender.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceStore
import kotlin.concurrent.thread

class ClipboardRelayService : Service() {
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var deviceStore: DeviceStore
    @Volatile private var lastText: String = ""
    private var started = false

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = readClipboardText() ?: return@OnPrimaryClipChangedListener
        if (text.isBlank() || text == lastText) return@OnPrimaryClipChangedListener
        if (text.length > MAX_TEXT_LENGTH) return@OnPrimaryClipChangedListener
        lastText = text

        val target = deviceStore.load() ?: return@OnPrimaryClipChangedListener
        thread(name = "cliplink-sender", isDaemon = true) {
            SenderClient.pushText(target, text)
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        deviceStore = DeviceStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            createChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            clipboardManager.addPrimaryClipChangedListener(clipListener)
            started = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { clipboardManager.removePrimaryClipChangedListener(clipListener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun readClipboardText(): String? {
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount <= 0) return null
        val item: ClipData.Item = clipData.getItemAt(0)
        return item.coerceToText(this)?.toString()
    }

    private fun buildNotification(): Notification {
        val device = deviceStore.load()
        val subtitle = if (device == null) {
            "未选择目标设备，打开 App 选择后生效"
        } else {
            "已连接 ${device.name} (${device.host}:${device.port})"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cliplink)
            .setContentTitle("ClipLink 自动同步已开启")
            .setContentText(subtitle)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ClipLink Sender",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "cliplink_sender_channel"
        private const val NOTIFICATION_ID = 11037
        private const val MAX_TEXT_LENGTH = 512 * 1024  // 512 KB，超出则不发送
    }
}
