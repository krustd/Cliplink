package com.cliplink.sender.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cliplink.sender.data.DeviceInfo
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class NsdScanner(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val seen = ConcurrentHashMap<String, DeviceInfo>()

    fun start(onUpdate: (List<DeviceInfo>) -> Unit, onError: (String) -> Unit) {
        if (discoveryListener != null) return

        // UDP broadcast runs in parallel with mDNS — works even when the
        // router blocks multicast traffic between WiFi clients.
        startUdpScan(onUpdate)

        fun resolveService(serviceInfo: NsdServiceInfo) {
            nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Resolve failed for ${si.serviceName}, errorCode=$errorCode")
                    // FAILURE_ALREADY_ACTIVE (3): Android resolves one service at a time;
                    // retry after a short delay when multiple are found simultaneously.
                    if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
                        mainHandler.postDelayed({ resolveService(si) }, 1000)
                    }
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val rawHost = resolved.host?.hostAddress
                    if (rawHost == null) {
                        Log.w(TAG, "hostAddress is null for ${resolved.serviceName}, skipping")
                        return
                    }
                    // Strip IPv6 zone ID (e.g. "fe80::1%wlan0" → "fe80::1")
                    val host = rawHost.substringBefore('%')
                    val port = resolved.port
                    if (port <= 0) {
                        Log.w(TAG, "Invalid port $port for ${resolved.serviceName}, skipping")
                        return
                    }

                    val txt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        resolved.attributes.mapValues { String(it.value ?: ByteArray(0)) }
                    } else {
                        emptyMap()
                    }

                    val name = txt["name"] ?: resolved.serviceName
                    val os = txt["os"]
                    val device = DeviceInfo(name = name, host = host, port = port, os = os)
                    Log.i(TAG, "mDNS resolved: $name @ $host:$port")
                    seen[device.stableKey] = device
                    onUpdate(seen.values.sortedBy { it.name })
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.i(TAG, "mDNS discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val type = serviceInfo.serviceType.lowercase()
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}, type=$type")
                if (!isClipLinkServiceType(type)) return
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val maybeName = serviceInfo.serviceName
                Log.i(TAG, "Service lost: $maybeName")
                val removedKeys = seen.keys.filter { it.startsWith("$maybeName@") }
                removedKeys.forEach { seen.remove(it) }
                onUpdate(seen.values.sortedBy { it.name })
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "mDNS discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed, errorCode=$errorCode")
                onError("启动搜索失败: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed, errorCode=$errorCode")
                onError("停止搜索失败: $errorCode")
            }
        }

        discoveryListener = listener
        multicastLock = wifiManager.createMulticastLock("cliplink-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        val listener = discoveryListener ?: return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        runCatching { multicastLock?.release() }
        multicastLock = null
        discoveryListener = null
        seen.clear()
    }

    // Sends a UDP broadcast probe and collects replies for 4 seconds.
    // Results are merged into the same `seen` map so the UI list stays unified.
    private fun startUdpScan(onUpdate: (List<DeviceInfo>) -> Unit) {
        thread(name = "cliplink-udp-scan", isDaemon = true) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 500  // short timeout so we can loop until deadline

                val probe = UDP_PROBE.toByteArray(Charsets.UTF_8)
                val broadcast = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(probe, probe.size, broadcast, UDP_DISCOVERY_PORT))
                Log.i(TAG, "UDP broadcast sent to 255.255.255.255:$UDP_DISCOVERY_PORT")

                val buf = ByteArray(512)
                val deadline = System.currentTimeMillis() + 4000L
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        socket.receive(pkt)
                        val text = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        val json = JSONObject(text)
                        val host = pkt.address?.hostAddress?.substringBefore('%') ?: continue
                        val name = json.optString("name").takeIf { it.isNotBlank() } ?: host
                        val port = json.optInt("port", 43837)
                        val os = json.optString("os").takeIf { it.isNotBlank() }
                        val device = DeviceInfo(name = name, host = host, port = port, os = os)
                        Log.i(TAG, "UDP found: $name @ $host:$port")
                        seen[device.stableKey] = device
                        onUpdate(seen.values.sortedBy { it.name })
                    } catch (_: SocketTimeoutException) {
                        // normal — keep waiting until deadline
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "UDP scan error: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "NsdScanner"
        private const val SERVICE_TYPE = "_cliplink._tcp."
        private const val SERVICE_TYPE_NO_DOT = "_cliplink._tcp"
        private const val UDP_PROBE = "CLIPLINK_DISCOVER"
        const val UDP_DISCOVERY_PORT = 43838

        private fun isClipLinkServiceType(raw: String): Boolean {
            return raw == SERVICE_TYPE ||
                raw == SERVICE_TYPE_NO_DOT ||
                raw.contains("_cliplink._tcp.local.")
        }
    }
}
