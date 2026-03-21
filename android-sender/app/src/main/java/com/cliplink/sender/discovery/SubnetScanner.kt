package com.cliplink.sender.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.cliplink.sender.data.DeviceInfo
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Scans every host in the local /24 subnet via GET /api/v1/info (plain
 * unicast TCP). Works even when the router blocks multicast/broadcast.
 */
class SubnetScanner(private val context: Context) {

    /**
     * @param port       HTTP port to probe (default 43837)
     * @param onStart    called with the subnet prefix being scanned, e.g. "192.168.1"
     * @param onFound    called on a background thread each time a device responds
     * @param onProgress called after each probe completes (done out of 254)
     * @param onDone     called when the full scan finishes
     */
    fun scan(
        port: Int = 43837,
        onStart: (subnet: String) -> Unit = {},
        onFound: (DeviceInfo) -> Unit,
        onProgress: (done: Int, found: Int) -> Unit = { _, _ -> },
        onDone: () -> Unit,
    ) {
        thread(name = "cliplink-subnet-scan", isDaemon = true) {
            val base = getSubnetBase()
            if (base == null) {
                Log.w(TAG, "Cannot determine local IPv4 subnet — subnet scan skipped")
                onDone()
                return@thread
            }

            Log.i(TAG, "Subnet scan: $base.1–254 :$port  parallel=$PARALLEL")
            onStart(base)

            val executor = Executors.newFixedThreadPool(PARALLEL)
            val latch = CountDownLatch(254)
            var done = 0
            var found = 0

            for (last in 1..254) {
                val ip = "$base.$last"
                executor.submit {
                    try {
                        val device = probe(ip, port)
                        if (device != null) {
                            synchronized(this) { found++ }
                            onFound(device)
                        }
                    } finally {
                        val d = synchronized(this) { ++done }
                        onProgress(d, found)
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()
            Log.i(TAG, "Subnet scan done, found=$found")
            onDone()
        }
    }

    private fun probe(ip: String, port: Int): DeviceInfo? {
        return try {
            val conn = URL("http://$ip:$port/api/v1/info").openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout    = READ_MS
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                if (!json.optBoolean("ok")) return null
                val name = json.optString("device_name").takeIf { it.isNotBlank() } ?: ip
                val os   = json.optString("device_os").takeIf  { it.isNotBlank() }
                Log.i(TAG, "Found ClipLink: $name @ $ip:$port")
                DeviceInfo(name = name, host = ip, port = port, os = os)
            } finally {
                conn.disconnect()
            }
        } catch (_: JSONException) { null }
          catch (_: Exception)     { null }
    }

    // Uses WifiManager (most reliable on Android) with NetworkInterface as fallback.
    @Suppress("DEPRECATION")
    private fun getSubnetBase(): String? {
        // Primary: WifiManager DHCP info — always reflects the active WiFi connection
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.dhcpInfo?.ipAddress ?: 0
        if (ip != 0) {
            val a = ip and 0xff
            val b = (ip shr 8) and 0xff
            val c = (ip shr 16) and 0xff
            val base = "$a.$b.$c"
            Log.i(TAG, "Subnet base (WifiManager): $base")
            return base
        }

        // Fallback: iterate NetworkInterface
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null
        for (iface in ifaces.asSequence()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses.asSequence()) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val parts = addr.hostAddress?.split(".") ?: continue
                if (parts.size == 4) {
                    val base = "${parts[0]}.${parts[1]}.${parts[2]}"
                    Log.i(TAG, "Subnet base (NetworkInterface): $base")
                    return base
                }
            }
        }
        return null
    }

    companion object {
        private const val TAG        = "SubnetScanner"
        private const val PARALLEL   = 50
        private const val CONNECT_MS = 1000
        private const val READ_MS    = 1000
    }
}
