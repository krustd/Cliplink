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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cliplink.sender.R
import com.cliplink.sender.data.DeviceInfo
import com.cliplink.sender.data.DeviceStore
import com.cliplink.sender.discovery.NsdScanner
import com.cliplink.sender.service.ClipboardRelayService

class MainActivity : ComponentActivity() {
    private lateinit var selectedDeviceText: TextView
    private lateinit var scanButton: Button
    private lateinit var stopScanButton: Button
    private lateinit var manualNameInput: EditText
    private lateinit var manualTargetInput: EditText
    private lateinit var saveManualButton: Button
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

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
