package com.cliplink.sender.service

import com.cliplink.sender.data.DeviceInfo
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL

object SenderClient {
    data class HealthCheckResult(
        val ok: Boolean,
        val message: String,
        val statusCode: Int? = null,
        val latencyMs: Long? = null,
    )

    fun checkHealth(device: DeviceInfo): HealthCheckResult {
        return try {
            val conn = (URL("${device.baseUrl}/healthz").openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500
                readTimeout = 2500
            }
            try {
                val startedAt = System.currentTimeMillis()
                val code = conn.responseCode
                val latencyMs = System.currentTimeMillis() - startedAt
                if (code in 200..299) {
                    HealthCheckResult(
                        ok = true,
                        message = "接收端在线，延迟 ${latencyMs}ms",
                        statusCode = code,
                        latencyMs = latencyMs,
                    )
                } else {
                    HealthCheckResult(
                        ok = false,
                        message = "接收端响应异常（HTTP $code）",
                        statusCode = code,
                        latencyMs = latencyMs,
                    )
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: ConnectException) {
            HealthCheckResult(ok = false, message = "连接被拒绝，请确认接收端服务已启动")
        } catch (_: SocketTimeoutException) {
            HealthCheckResult(ok = false, message = "连接超时，请确认手机和电脑在同一网络")
        } catch (_: UnknownHostException) {
            HealthCheckResult(ok = false, message = "地址无法解析，请检查设备地址")
        } catch (e: Exception) {
            HealthCheckResult(ok = false, message = e.message ?: "网络检查失败")
        }
    }

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
