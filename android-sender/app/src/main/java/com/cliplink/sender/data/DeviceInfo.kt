package com.cliplink.sender.data

data class DeviceInfo(
    val name: String,
    val host: String,
    val port: Int,
    val os: String? = null
) {
    val baseUrl: String
        get() = "http://$host:$port"

    val stableKey: String
        get() = "$name@$host:$port"
}
