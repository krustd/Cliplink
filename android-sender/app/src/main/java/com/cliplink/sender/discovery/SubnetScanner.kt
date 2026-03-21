package com.cliplink.sender.discovery

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
 * Scans every host in the local /24 subnet by sending a GET /api/v1/info
 * request. Works even when the router blocks multicast/broadcast traffic
 * because it uses plain unicast TCP — the same transport confirmed to work
 * by the connection test.
 *
 * Modelled after LocalSend's "legacy HTTP mode": try multicast first, then
 * fall back to subnet scan if no devices appear within 1 second.
 */
class SubnetScanner {

    /**
     * @param port      HTTP port to probe (default 43837)
     * @param onFound   called on a background thread each time a device responds
     * @param onDone    called when the scan finishes (success or failure)
     */
    fun scan(port: Int = 43837, onFound: (DeviceInfo) -> Unit, onDone: () -> Unit) {
        thread(name = "cliplink-subnet-scan", isDaemon = true) {
            val base = getSubnetBase()
            if (base == null) {
                Log.w(TAG, "Cannot determine local IPv4 subnet, subnet scan skipped")
                onDone()
                return@thread
            }

            Log.i(TAG, "Subnet scan start: $base.1–254 :$port  ($PARALLEL parallel)")
            val executor = Executors.newFixedThreadPool(PARALLEL)
            val latch = CountDownLatch(254)

            for (last in 1..254) {
                val ip = "$base.$last"
                executor.submit {
                    try {
                        probe(ip, port)?.let { onFound(it) }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()
            Log.i(TAG, "Subnet scan done")
            onDone()
        }
    }

    // Attempts a GET /api/v1/info on the given host. Returns a DeviceInfo on
    // success, or null for any error (connection refused, timeout, wrong body…).
    private fun probe(ip: String, port: Int): DeviceInfo? {
        return try {
            val conn = URL("http://$ip:$port/api/v1/info").openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = READ_MS
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                if (!json.optBoolean("ok")) return null
                val name = json.optString("device_name").takeIf { it.isNotBlank() } ?: ip
                val os   = json.optString("device_os").takeIf  { it.isNotBlank() }
                Log.i(TAG, "Found: $name @ $ip:$port")
                DeviceInfo(name = name, host = ip, port = port, os = os)
            } finally {
                conn.disconnect()
            }
        } catch (_: JSONException)  { null }
          catch (_: Exception)      { null }   // timeout / refused — expected for most IPs
    }

    // Iterates NetworkInterface to find the first non-loopback IPv4 address and
    // returns its /24 prefix (e.g. "192.168.1" for 192.168.1.42).
    private fun getSubnetBase(): String? {
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull()
            ?: return null
        for (iface in ifaces.asSequence()) {
            if (!iface.isUp || iface.isLoopback) continue
            for (addr in iface.inetAddresses.asSequence()) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val ip = addr.hostAddress ?: continue
                val parts = ip.split(".")
                if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
            }
        }
        return null
    }

    companion object {
        private const val TAG         = "SubnetScanner"
        private const val PARALLEL    = 50      // concurrent HTTP probes
        private const val CONNECT_MS  = 1000    // connect timeout per host
        private const val READ_MS     = 1000    // read timeout per host
    }
}
