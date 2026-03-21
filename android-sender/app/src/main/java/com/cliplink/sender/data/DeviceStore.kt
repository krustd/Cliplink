package com.cliplink.sender.data

import android.content.Context

class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("cliplink_sender", Context.MODE_PRIVATE)

    fun save(device: DeviceInfo) {
        prefs.edit()
            .putString(KEY_NAME, device.name)
            .putString(KEY_HOST, device.host)
            .putInt(KEY_PORT, device.port)
            .putString(KEY_OS, device.os)
            .apply()
    }

    fun load(): DeviceInfo? {
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val port = prefs.getInt(KEY_PORT, -1)
        if (port <= 0) return null
        return DeviceInfo(
            name = name,
            host = host,
            port = port,
            os = prefs.getString(KEY_OS, null)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_NAME = "target_name"
        private const val KEY_HOST = "target_host"
        private const val KEY_PORT = "target_port"
        private const val KEY_OS = "target_os"
    }
}
