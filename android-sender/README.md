# ClipLink Android Sender

ClipLink 的 Android 发送端，将手机剪贴板内容发送到局域网内的桌面接收端。

## 发送方式

ClipLink 采用**显式发送**模型——由用户主动触发，不在后台监听剪贴板。这是因为 Android 10 起系统明确禁止后台应用读取剪贴板（隐私保护）。

提供三种发送入口：

### 1. 通知栏快捷按钮（推荐）

App 启动后会在通知栏常驻一条通知，下拉即可点击「📋 发送剪贴板」。
点击后会短暂弹出一个小对话框，完成后自动关闭。

### 2. App 内按钮

打开 App，点底部「📋 发送剪贴板」按钮，直接读取并发送当前剪贴板内容。

### 3. 系统分享菜单

在任意 App（浏览器、微信、备忘录等）中选中文字 → 点「分享」→ 选择 ClipLink，
内容将立即发送到已选中的设备。

---

## 设备发现

支持两种发现方式，自动并行运行：

| 方式 | 说明 |
|------|------|
| **mDNS** | 监听 `_cliplink._tcp` 服务，路由器支持组播时秒级发现 |
| **子网扫描** | 扫描本机所在 /24 子网的全部 IP，适配禁止组播的路由器 |

扫描不到时可手动输入 `IP:端口`（例如 `192.168.1.8:43837`）直连。

---

## 多厂商 ROM 兼容

> **中国本土 ROM 的通知栏按钮默认会被系统拦截**，需要手动授权。
> App 首次启动时会自动检测设备厂商并弹出引导对话框。

| 厂商 / 系统 | 需要开启的权限 |
|-------------|---------------|
| 小米 MIUI / HyperOS | 自启动 + 后台弹出界面 |
| OPPO ColorOS | 自启动 + 允许后台运行 |
| vivo OriginOS / FuntouchOS | 自启动 + 后台弹出界面 |
| 华为 EMUI / HarmonyOS | 自启动 + 关联启动 |
| 三星 One UI | 后台使用限制设为「不限制」|

App 会尝试直接跳转到对应厂商的设置页面，无法跳转时降级到系统「应用详情」页。

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 向桌面端发送文本 |
| `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` | 获取本机 IP 用于子网扫描 |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS 组播发现 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC` | 通知栏常驻服务 |
| `POST_NOTIFICATIONS` | 显示通知栏（Android 13+ 运行时申请）|

**不需要**：无障碍权限、悬浮窗权限、读取剪贴板的后台权限。

---

## 编译运行

```bash
# 用 Android Studio 打开
open android-sender/

# 或命令行构建
cd android-sender
./gradlew assembleDebug
```

- minSdk: 26（Android 8.0）
- targetSdk: 35
- 编译工具链：AGP 8.5.2 / Kotlin 1.9.24 / Gradle 8.7

## 发布

- 推送标准语义化版本 tag，例如 `v0.1.2` 后，GitHub Actions 会自动构建并上传 signed release APK
- 仓库已配置 release keystore secrets，后续正式版本会沿用同一把签名密钥，保证用户可直接覆盖升级
- Android 与 receiver 统一使用同一个版本号，并发布到同一个 GitHub Release
- 本地如需手动打正式包，可在 `android-sender/` 目录执行 `./gradlew assembleRelease`

---

## 使用流程

1. 在电脑上启动 `cliplinkd`（见 `receiver/` 目录）
2. 打开 App，等待自动搜索附近设备
3. 从列表中点选目标设备，或手动输入 IP:端口 后保存
4. 看到当前接收端信息并显示在线状态后即可发送
5. 此后可用通知栏按钮、App 内按钮或分享菜单发送内容

发送前 App 会先检查接收端是否在线；连接有问题时，也可以点「检查在线」查看状态。

---

## 注意事项

- 通信走 HTTP 明文，仅适用于受信任的局域网
- 手机与电脑须在同一 WiFi 下
- 建议将 App 的电池优化设为「不限制」，防止系统杀掉通知栏服务
