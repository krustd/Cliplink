package com.cliplink.sender.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceInfo
import com.cliplink.sender.data.DeviceStore
import com.cliplink.sender.discovery.NsdScanner
import com.cliplink.sender.service.ClipboardRelayService
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var selectedDeviceText: TextView
    private lateinit var scanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var manualNameInput: EditText
    private lateinit var manualTargetInput: EditText
    private lateinit var saveManualButton: Button
    private lateinit var pingButton: Button
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button

    private lateinit var deviceStore: DeviceStore
    private lateinit var scanner: NsdScanner
    private lateinit var adapter: DeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedDeviceText = findViewById(R.id.selectedDeviceText)
        scanButton = findViewById(R.id.scanButton)
        stopScanButton = findViewById(R.id.refreshButton)
        manualNameInput = findViewById(R.id.manualNameInput)
        manualTargetInput = findViewById(R.id.manualTargetInput)
        saveManualButton = findViewById(R.id.saveManualButton)
        pingButton = findViewById(R.id.pingButton)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)

        deviceStore = DeviceStore(this)
        scanner = NsdScanner(this)

        adapter = DeviceAdapter(::onDeviceSelected)
        findViewById<RecyclerView>(R.id.devicesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        val selected = deviceStore.load()
        renderSelected(selected)
        if (selected != null) {
            manualNameInput.setText(selected.name)
            manualTargetInput.setText("${selected.host}:${selected.port}")
        }
        bindActions()
        requestNotificationPermissionIfNeeded()
    }

    override fun onDestroy() {
        scanner.stop()
        super.onDestroy()
    }

    private fun bindActions() {
        scanButton.setOnClickListener {
            scanner.start(
                onUpdate = { devices -> runOnUiThread { adapter.submitList(devices) } },
                onError = { msg -> runOnUiThread { toast(msg) } }
            )
            toast("正在搜索局域网设备…")
        }

        stopScanButton.setOnClickListener {
            scanner.stop()
            adapter.submitList(emptyList())
            toast("已停止搜索")
        }

        saveManualButton.setOnClickListener {
            val rawTarget = manualTargetInput.text?.toString()?.trim().orEmpty()
            if (rawTarget.isEmpty() || !rawTarget.contains(":")) {
                toast("请输入正确的 IP:端口")
                return@setOnClickListener
            }
            val split = rawTarget.split(":", limit = 2)
            val host = split[0].trim()
            val port = split[1].trim().toIntOrNull()
            if (host.isEmpty() || port == null || port !in 1..65535) {
                toast("请输入正确的 IP:端口")
                return@setOnClickListener
            }

            val customName = manualNameInput.text?.toString()?.trim().orEmpty()
            val device = DeviceInfo(
                name = if (customName.isBlank()) "Manual $host" else customName,
                host = host,
                port = port,
                os = "manual"
            )
            onDeviceSelected(device)
        }

        pingButton.setOnClickListener { pingCurrentDevice() }

        startServiceButton.setOnClickListener {
            if (deviceStore.load() == null) {
                toast("请先选择一个接收设备")
                return@setOnClickListener
            }
            val intent = Intent(this, ClipboardRelayService::class.java)
            ContextCompat.startForegroundService(this, intent)
            toast("后台自动同步已启动")
        }

        stopServiceButton.setOnClickListener {
            stopService(Intent(this, ClipboardRelayService::class.java))
            toast("后台自动同步已停止")
        }
    }

    private fun onDeviceSelected(device: DeviceInfo) {
        deviceStore.save(device)
        renderSelected(device)
        manualNameInput.setText(device.name)
        manualTargetInput.setText("${device.host}:${device.port}")
        val intent = Intent(this, ClipboardRelayService::class.java)
        ContextCompat.startForegroundService(this, intent)
        toast("已选择并启动后台同步: ${device.name}")
    }

    private fun renderSelected(device: DeviceInfo?) {
        selectedDeviceText.text = if (device == null) {
            "当前未选择接收设备"
        } else {
            "当前设备: ${device.name} (${device.host}:${device.port})"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }

    private fun pingCurrentDevice() {
        val device = deviceStore.load()
        if (device == null) {
            toast("请先选择或手动保存一个设备")
            return
        }
        toast("正在测试连接 ${device.host}:${device.port}…")
        thread(isDaemon = true) {
            val url = "http://${device.host}:${device.port}/healthz"
            val result = StringBuilder()
            result.appendLine("目标: ${device.name}")
            result.appendLine("地址: ${device.host}:${device.port}")
            result.appendLine()
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val t0 = System.currentTimeMillis()
                try {
                    val code = conn.responseCode
                    val ms = System.currentTimeMillis() - t0
                    if (code in 200..299) {
                        result.appendLine("✅ HTTP 连接成功")
                        result.appendLine("状态: $code  延迟: ${ms}ms")
                        result.appendLine()
                        result.appendLine("手机 → 电脑 网络正常。")
                        result.appendLine("如果剪贴板仍不同步，请检查：")
                        result.appendLine("· 后台同步服务是否已启动")
                        result.appendLine("· 系统是否限制了后台 App")
                    } else {
                        result.appendLine("⚠️ HTTP 返回异常状态: $code")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: java.net.ConnectException) {
                result.appendLine("❌ 连接被拒绝")
                result.appendLine("原因: ${e.message}")
                result.appendLine()
                result.appendLine("可能是：")
                result.appendLine("· cliplinkd 没有运行")
                result.appendLine("· 端口号错误（默认 43837）")
                result.appendLine("· 电脑防火墙拦截了该端口")
            } catch (e: java.net.SocketTimeoutException) {
                result.appendLine("❌ 连接超时（3 秒）")
                result.appendLine()
                result.appendLine("可能是：")
                result.appendLine("· IP 地址不对，目标主机不可达")
                result.appendLine("· 手机和电脑不在同一 WiFi")
                result.appendLine("· 路由器开启了 AP 隔离")
            } catch (e: java.net.UnknownHostException) {
                result.appendLine("❌ 无法解析主机名")
                result.appendLine("原因: ${e.message}")
            } catch (e: Exception) {
                result.appendLine("❌ 未知错误")
                result.appendLine("${e.javaClass.simpleName}: ${e.message}")
            }

            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("连接测试结果")
                    .setMessage(result.toString().trimEnd())
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
