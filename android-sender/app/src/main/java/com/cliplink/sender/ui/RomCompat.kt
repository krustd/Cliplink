package com.cliplink.sender.ui

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity

/**
 * Detects Chinese OEM ROMs and shows a one-time guide to enable the permissions
 * needed for notification-triggered activity launches (auto-start + background popup).
 *
 * Without these permissions, tapping the notification button does nothing
 * on Xiaomi (MIUI), OPPO (ColorOS), vivo (OriginOS/FuntouchOS), and Huawei (EMUI).
 */
object RomCompat {

    private const val PREF_KEY = "rom_guide_shown"

    fun showPermissionGuideIfNeeded(activity: ComponentActivity) {
        val prefs = activity.getSharedPreferences("cliplink", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_KEY, false)) return

        val guide = buildGuide() ?: return

        AlertDialog.Builder(activity)
            .setTitle(guide.title)
            .setMessage(guide.message)
            .setPositiveButton("前往设置") { _, _ ->
                openSettings(activity, guide.settingsIntent)
            }
            .setNegativeButton("稍后再说", null)
            .setOnDismissListener {
                prefs.edit().putBoolean(PREF_KEY, true).apply()
            }
            .show()
    }

    private data class Guide(val title: String, val message: String, val settingsIntent: Intent)

    private fun buildGuide(): Guide? {
        return when (Build.MANUFACTURER.lowercase().trim()) {
            "xiaomi", "redmi", "poco" -> Guide(
                title = "小米设备需要额外授权",
                message = "在 MIUI / HyperOS 上，通知栏的【发送剪贴板】按钮需要以下两项权限：\n\n" +
                        "1. 自启动\n2. 后台弹出界面\n\n" +
                        "路径：安全中心 -> 授权管理 -> 自启动管理 -> 找到 ClipLink 开启\n\n" +
                        "同时：设置 -> 应用 -> ClipLink -> 权限 -> 后台弹出界面 -> 允许",
                settingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
            )

            "oppo", "oneplus", "realme" -> Guide(
                title = "OPPO / 一加设备需要额外授权",
                message = "在 ColorOS 上，通知栏按钮需要以下权限：\n\n" +
                        "1. 自启动\n2. 允许后台运行\n\n" +
                        "路径：手机管家 -> 权限隐私 -> 自启动管理 -> 找到 ClipLink 开启",
                settingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.privacypermissionsentry.PermissionTopActivity"
                    )
                }
            )

            "vivo" -> Guide(
                title = "vivo 设备需要额外授权",
                message = "在 OriginOS / FuntouchOS 上，通知栏按钮需要以下权限：\n\n" +
                        "1. 自启动\n2. 后台弹出界面\n\n" +
                        "路径：i管家 -> 应用管理 -> 自启动管理 -> 找到 ClipLink 开启",
                settingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
            )

            "huawei", "honor" -> Guide(
                title = "华为 / 荣耀设备需要额外授权",
                message = "在 EMUI / HarmonyOS 上，通知栏按钮需要开启：\n\n" +
                        "1. 自启动\n2. 关联启动\n\n" +
                        "路径：手机管家 -> 应用启动管理 -> ClipLink -> 关闭自动管理 -> 手动全部开启",
                settingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
            )

            "samsung" -> Guide(
                title = "三星设备建议关闭省电限制",
                message = "在 One UI 上，如果通知栏按钮无响应，请：\n\n" +
                        "设置 -> 电池 -> 后台使用限制 -> 找到 ClipLink -> 选择「不限制」",
                settingsIntent = Intent().apply {
                    component = ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity"
                    )
                }
            )

            else -> null
        }
    }

    private fun openSettings(activity: ComponentActivity, intent: Intent) {
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            // Fallback: open App Info page
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
            )
        }
    }
}
