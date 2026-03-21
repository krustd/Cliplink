package com.cliplink.sender.service

import com.cliplink.sender.data.DeviceInfo
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SenderClient {
    fun pushText(device: DeviceInfo, text: String): Result<Unit> {
        return runCatching {
            val conn = (URL("${device.baseUrl}/api/v1/clipboard/text").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 4000
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            try {
                val payload = JSONObject().apply {
                    put("text", text)
                    put("source", "android")
                }.toString()

                conn.outputStream.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                        writer.write(payload)
                    }
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("receiver status=$code")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
