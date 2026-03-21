# ClipLink

局域网剪贴板同步工具。手机上复制的文字，一键发到电脑剪贴板。

```
手机（Android）  ──HTTP──▶  电脑（macOS / Windows / Linux）
  ClipLink App              cliplinkd 守护进程
```

## 组成

| 目录 | 语言 | 职责 |
|------|------|------|
| `android-sender/` | Kotlin | Android 发送端 App |
| `receiver/` | Go | 桌面端接收守护进程 |

## 快速开始

**1. 启动桌面接收端**

```bash
cd receiver
go run ./cmd/cliplinkd
# 默认监听 :43837，同时广播 mDNS 服务供手机发现
```

**2. 安装 Android App**

可直接从 GitHub Releases 下载 Android APK，或用 Android Studio 打开 `android-sender/` 自行编译安装。

**3. 连接**

打开 App 后会自动搜索附近接收端，点选电脑即可完成连接。

## 发送方式

| 方式 | 操作 |
|------|------|
| 通知栏按钮 | 下拉通知栏 → 点「📋 发送剪贴板」|
| App 内按钮 | 打开 App → 点「📋 发送剪贴板」|
| 系统分享菜单 | 任意 App 中选中文字 → 分享 → 选 ClipLink |

## 特性

- **显式发送**：用户主动触发，不在后台偷读剪贴板，无隐私顾虑
- **双模式发现**：mDNS 自动发现 + 子网扫描，适配路由器禁止组播的环境
- **手动 IP**：扫描不到时手动输入 `IP:端口` 直连
- **多厂商兼容**：内置小米 / OPPO / vivo / 华为 / 三星 ROM 权限引导
- **纯局域网**：HTTP 明文，数据不出本地网络

## 系统要求

- Android 8.0+（minSdk 26）
- 桌面端：Go 1.21+，macOS / Windows / Linux
- 手机与电脑需在同一 WiFi 下

## 发布

- Android 发送端：推送 tag `android-sender-v*` 后，GitHub Actions 会自动构建已签名的 release APK 并附加到 GitHub Release
- Receiver 桌面端：推送 tag `receiver-v*` 后，GitHub Actions 会自动发布 Windows 压缩包、macOS 压缩包以及 Linux `deb`、`rpm`、`apk`、`archlinux` 包
