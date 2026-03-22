package com.cliplink.sender.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var selectedDeviceText: TextView
    private lateinit var selectedDeviceMetaText: TextView
    private lateinit var selectedDeviceStatusText: TextView
    private lateinit var scanStatusText: TextView
    private lateinit var emptyDevicesText: TextView
    private lateinit var refreshButton: Button
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
    private val scanSession = AtomicInteger(0)

    private lateinit var adapter: DeviceAdapter
    private var lastScanStartedAt = 0L

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ClipLinkNotificationService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectedDeviceText  = findViewById(R.id.selectedDeviceText)
        selectedDeviceMetaText = findViewById(R.id.selectedDeviceMetaText)
        selectedDeviceStatusText = findViewById(R.id.selectedDeviceStatusText)
        scanStatusText      = findViewById(R.id.scanStatusText)
        emptyDevicesText    = findViewById(R.id.emptyDevicesText)
        refreshButton       = findViewById(R.id.refreshButton)
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
        startScan(userInitiated = false)
        refreshSelectedDeviceHealth(showToastOnFailure = false)
    }

    override fun onResume() {
        super.onResume()
        val shouldRefresh = System.currentTimeMillis() - lastScanStartedAt > AUTO_SCAN_INTERVAL_MS
        if (shouldRefresh) {
            startScan(userInitiated = false)
        }
        refreshSelectedDeviceHealth(showToastOnFailure = false)
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
        refreshButton.setOnClickListener { startScan(userInitiated = true) }

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

        sendClipboardButton.isEnabled = false
        sendStatusText.text = "正在检测接收端状态…"
        thread(isDaemon = true) {
            val health = SenderClient.checkHealth(device)
            if (!health.ok) {
                runOnUiThread {
                    sendClipboardButton.isEnabled = true
                    applyHealthResult(device, health)
                    sendStatusText.text = "发送未开始：${health.message}"
                    showOfflineDialog(device, health.message)
                }
                return@thread
            }

            runOnUiThread {
                applyHealthResult(device, health)
                sendStatusText.text = "正在发送到 ${device.name}…"
            }

            val result = SenderClient.pushText(device, text)
            runOnUiThread {
                sendClipboardButton.isEnabled = true
                sendStatusText.text = if (result.isSuccess) {
                    "已发送到 ${device.name}\n${text.take(80)}"
                } else {
                    "发送失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            }
        }
    }

    private fun startScan(userInitiated: Boolean) {
        val sessionId = scanSession.incrementAndGet()
        lastScanStartedAt = System.currentTimeMillis()
        nsdScanner.stop()
        nsdSeen.clear()
        subnetSeen.clear()
        adapter.submitList(emptyList())
        renderDevices(emptyList())
        scanStatusText.text = if (userInitiated) "正在刷新设备列表…" else "正在搜索接收端…"

        nsdScanner.start(
            onUpdate = { devices ->
                if (sessionId != scanSession.get()) return@start
                nsdSeen.clear()
                devices.forEach { nsdSeen[it.stableKey] = it }
                refreshAdapter()
            },
            onError = { message ->
                if (sessionId != scanSession.get()) return@start
                runOnUiThread { scanStatusText.text = message }
            }
        )

        thread(isDaemon = true) {
            Thread.sleep(800)
            subnetScanner.scan(
                onStart = { subnet ->
                    if (sessionId != scanSession.get()) return@scan
                    runOnUiThread { scanStatusText.text = "正在扫描 $subnet.1-254…" }
                },
                onFound = { device ->
                    if (sessionId != scanSession.get()) return@scan
                    subnetSeen[device.stableKey] = device
                    refreshAdapter()
                },
                onProgress = { done, found ->
                    if (sessionId != scanSession.get()) return@scan
                    if (done % 30 == 0 || done == 254) {
                        runOnUiThread {
                            scanStatusText.text = "已扫描 $done/254，发现 $found 台接收端"
                        }
                    }
                },
                onDone = {
                    if (sessionId != scanSession.get()) return@scan
                    val total = (subnetSeen.values + nsdSeen.values)
                        .distinctBy { it.endpointKey }
                        .size
                    runOnUiThread {
                        scanStatusText.text = if (total == 0)
                            "暂未发现接收端，可手动输入 IP:端口"
                        else
                            "已发现 $total 台接收端"
                    }
                }
            )
        }
    }

    private fun refreshAdapter() {
        val merged = (nsdSeen.values + subnetSeen.values)
            .distinctBy { it.endpointKey }
            .sortedBy { it.name }
        runOnUiThread {
            val current = deviceStore.load()
            if (current != null) {
                val rediscovered = merged.firstOrNull { it.endpointKey == current.endpointKey }
                if (rediscovered == null) {
                    clearSelectedDevice("当前接收端已不在在线列表中，请重新选择")
                } else {
                    if (rediscovered != current) {
                        onDeviceSelected(rediscovered, showToast = false)
                    }
                }
            }
            renderDevices(merged)
        }
    }

    private fun renderDevices(devices: List<DeviceInfo>) {
        adapter.selectedEndpoint = deviceStore.load()?.endpointKey
        adapter.submitList(devices)
        emptyDevicesText.text = if (devices.isEmpty()) {
            "正在搜索接收端…\n确认电脑端已启动后，通常会在几秒内出现。"
        } else {
            ""
        }
        emptyDevicesText.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onDeviceSelected(device: DeviceInfo, showToast: Boolean = true) {
        deviceStore.save(device)
        renderSelected(device)
        manualNameInput.setText(device.name)
        manualTargetInput.setText("${device.host}:${device.port}")
        adapter.selectedEndpoint = device.endpointKey
        if (showToast) {
            toast("已选中 ${device.name}")
        }
        startNotificationService()
        refreshSelectedDeviceHealth(showToastOnFailure = false)
    }

    private fun renderSelected(device: DeviceInfo?) {
        selectedDeviceText.text = if (device == null) {
            "尚未选择接收端"
        } else {
            device.name
        }
        selectedDeviceMetaText.text = if (device == null) {
            "系统会自动搜索局域网内的接收端，也可手动输入地址"
        } else {
            "${device.host}:${device.port} · ${device.os ?: "unknown-os"}"
        }
        selectedDeviceStatusText.text = if (device == null) "尚未选择接收端" else "等待状态更新"
    }

    private fun pingCurrentDevice() {
        val device = deviceStore.load()
        if (device == null) {
            toast("请先选择或手动保存一个设备")
            return
        }
        selectedDeviceStatusText.text = "正在检测连接状态…"
        thread(isDaemon = true) {
            val result = SenderClient.checkHealth(device)

            runOnUiThread {
                applyHealthResult(device, result)
                AlertDialog.Builder(this)
                    .setTitle(if (result.ok) "连接正常" else "连接不可用")
                    .setMessage(buildHealthMessage(device, result))
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }

    private fun refreshSelectedDeviceHealth(showToastOnFailure: Boolean) {
        val device = deviceStore.load() ?: return
        selectedDeviceStatusText.text = "正在检测连接状态…"
        thread(isDaemon = true) {
            val result = SenderClient.checkHealth(device)
            runOnUiThread {
                applyHealthResult(device, result)
                if (showToastOnFailure && !result.ok) {
                    toast(result.message)
                }
            }
        }
    }

    private fun applyHealthResult(device: DeviceInfo, result: SenderClient.HealthCheckResult) {
        val stillSelected = deviceStore.load()?.endpointKey == device.endpointKey
        if (!result.ok && stillSelected) {
            clearSelectedDevice("当前设备已离线，请重新选择可用接收端")
            return
        }

        val suffix = result.latencyMs?.let { " · ${it}ms" }.orEmpty()
        selectedDeviceStatusText.text = if (result.ok) {
            "在线$suffix"
        } else {
            "不可用 · ${result.message}"
        }
        if (deviceStore.load()?.endpointKey == device.endpointKey) {
            selectedDeviceMetaText.text = "${device.host}:${device.port} · ${device.os ?: "unknown-os"}"
        }
    }

    private fun clearSelectedDevice(statusMessage: String) {
        deviceStore.clear()
        renderSelected(null)
        adapter.selectedEndpoint = null
        sendStatusText.text = statusMessage
    }

    private fun buildHealthMessage(device: DeviceInfo, result: SenderClient.HealthCheckResult): String {
        return buildString {
            appendLine("接收端：${device.name}")
            appendLine("地址：${device.host}:${device.port}")
            appendLine()
            appendLine(result.message)
            result.statusCode?.let { appendLine("HTTP 状态：$it") }
            result.latencyMs?.let { appendLine("延迟：${it}ms") }
            if (!result.ok) {
                appendLine()
                append("建议：确认电脑端接收端已启动，并检查手机与电脑是否在同一网络")
            }
        }.trimEnd()
    }

    private fun showOfflineDialog(device: DeviceInfo, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("暂时无法发送")
            .setMessage("接收端 ${device.name} 当前不可用。\n\n$reason")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val AUTO_SCAN_INTERVAL_MS = 15_000L
    }
}
