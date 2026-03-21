package com.cliplink.sender.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceInfo
import com.cliplink.sender.data.DeviceStore
import com.cliplink.sender.discovery.NsdScanner
import com.cliplink.sender.discovery.SubnetScanner
import com.cliplink.sender.service.ClipLinkNotificationService
import com.cliplink.sender.service.SenderClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var selectedDeviceText: TextView
    private lateinit var scanStatusText: TextView
    private lateinit var scanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var manualNameInput: EditText
    private lateinit var manualTargetInput: EditText
    private lateinit var saveManualButton: Button
    private lateinit var pingButton: Button
    private lateinit var sendClipboardButton: Button
    private lateinit var sendStatusText: TextView

    private lateinit var deviceStore: DeviceStore
    private lateinit var nsdScanner: NsdScanner
    private val subnetScanner by lazy { SubnetScanner(this) }

    private val nsdSeen    = ConcurrentHashMap<String, DeviceInfo>()
    private val subnetSeen = ConcurrentHashMap<String, DeviceInfo>()

    private lateinit var adapter: DeviceAdapter

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ClipLinkNotificationService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedDeviceText  = findViewById(R.id.selectedDeviceText)
        scanStatusText      = findViewById(R.id.scanStatusText)
        scanButton          = findViewById(R.id.scanButton)
        stopScanButton      = findViewById(R.id.refreshButton)
        manualNameInput     = findViewById(R.id.manualNameInput)
        manualTargetInput   = findViewById(R.id.manualTargetInput)
        saveManualButton    = findViewById(R.id.saveManualButton)
        pingButton          = findViewById(R.id.pingButton)
        sendClipboardButton = findViewById(R.id.sendClipboardButton)
        sendStatusText      = findViewById(R.id.sendStatusText)

        deviceStore = DeviceStore(this)
        nsdScanner  = NsdScanner(this)

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
        startNotificationService()
        RomCompat.showPermissionGuideIfNeeded(this)
    }

    private fun startNotificationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                ClipLinkNotificationService.start(this)
            } else {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            ClipLinkNotificationService.start(this)
        }
    }

    override fun onDestroy() {
        nsdScanner.stop()
        super.onDestroy()
    }

    private fun bindActions() {
        scanButton.setOnClickListener { startScan() }

        stopScanButton.setOnClickListener {
            nsdScanner.stop()
            nsdSeen.clear()
            subnetSeen.clear()
            adapter.submitList(emptyList())
            scanStatusText.text = ""
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

        sendClipboardButton.setOnClickListener { sendCurrentClipboard() }
    }

    private fun sendCurrentClipboard() {
        val device = deviceStore.load()
        if (device == null) {
            toast("请先选择或手动保存一个设备")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            toast("剪贴板为空")
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString()?.trim()
        if (text.isNullOrBlank()) {
            toast("剪贴板内容为空")
            return
        }
        sendStatusText.text = "📤 正在发送…\n「${text.take(80)}」"
        thread(isDaemon = true) {
            val result = SenderClient.pushText(device, text)
            runOnUiThread {
                sendStatusText.text = if (result.isSuccess)
                    "✅ 发送成功 → ${device.host}:${device.port}\n「${text.take(80)}」"
                else
                    "❌ 发送失败：${result.exceptionOrNull()?.message}\n「${text.take(80)}」"
            }
        }
    }

    private fun startScan() {
        nsdSeen.clear()
        subnetSeen.clear()
        adapter.submitList(emptyList())
        scanStatusText.text = "正在初始化搜索…"

        nsdScanner.start(
            onUpdate = { devices ->
                nsdSeen.clear()
                devices.forEach { nsdSeen[it.stableKey] = it }
                refreshAdapter()
            },
            onError = {}
        )

        thread(isDaemon = true) {
            Thread.sleep(1000)
            subnetScanner.scan(
                onStart = { subnet ->
                    runOnUiThread { scanStatusText.text = "子网扫描中: $subnet.1–254…" }
                },
                onFound = { device ->
                    subnetSeen[device.stableKey] = device
                    refreshAdapter()
                },
                onProgress = { done, found ->
                    if (done % 30 == 0 || done == 254) {
                        runOnUiThread {
                            scanStatusText.text = "子网扫描中: $done/254，已找到 $found 台设备"
                        }
                    }
                },
                onDone = {
                    val total = subnetSeen.size + nsdSeen.size
                    runOnUiThread {
                        scanStatusText.text = if (total == 0)
                            "扫描完成，未找到设备。请确认 cliplinkd 已运行并尝试手动输入 IP"
                        else
                            "扫描完成，共找到 $total 台设备"
                    }
                }
            )
        }
    }

    private fun refreshAdapter() {
        val merged = (nsdSeen.values + subnetSeen.values)
            .distinctBy { it.stableKey }
            .sortedBy { it.name }
        runOnUiThread { adapter.submitList(merged) }
    }

    private fun onDeviceSelected(device: DeviceInfo) {
        deviceStore.save(device)
        renderSelected(device)
        manualNameInput.setText(device.name)
        manualTargetInput.setText("${device.host}:${device.port}")
        toast("已选择设备: ${device.name}")
        startNotificationService()
    }

    private fun renderSelected(device: DeviceInfo?) {
        selectedDeviceText.text = if (device == null) {
            "当前未选择接收设备"
        } else {
            "当前设备: ${device.name} (${device.host}:${device.port})"
        }
    }

    private fun pingCurrentDevice() {
        val device = deviceStore.load()
        if (device == null) {
            toast("请先选择或手动保存一个设备")
            return
        }
        toast("正在测试连接 ${device.host}:${device.port}…")
        thread(isDaemon = true) {
            val result = StringBuilder()
            result.appendLine("目标: ${device.name}")
            result.appendLine("地址: ${device.host}:${device.port}")
            result.appendLine()
            try {
                val conn = URL("http://${device.host}:${device.port}/healthz").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val t0 = System.currentTimeMillis()
                try {
                    val code = conn.responseCode
                    val ms = System.currentTimeMillis() - t0
                    if (code in 200..299) {
                        result.appendLine("✅ HTTP 连接成功")
                        result.appendLine("状态: $code  延迟: ${ms}ms")
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
