# ClipLink

ClipLink 是一个局域网文本投递工具：在 Android 手机上主动发送文本，一键写入电脑剪贴板。

```text
Android 手机  ── 局域网 HTTP ──▶  电脑
ClipLink App                    cliplinkd
```

它不是后台同步器，而是一个显式发送工具。什么时候发送由你决定，数据只在本地网络中传输，不经过云端。

## 能做什么

- 手机上复制了一段文字，想立刻发到电脑继续使用
- 不想登录账号，不想依赖云同步，只想在同一 Wi-Fi 下快速传文本
- 希望发送入口足够顺手，可以用通知栏按钮、App 内按钮或系统分享菜单发送

## 项目组成

| 组件 | 目录 | 说明 |
|------|------|------|
| Android 发送端 | `android-sender/` | 选择目标电脑并发送当前文本 |
| 桌面接收端 | `receiver/` | 接收文本并写入本机系统剪贴板 |

## 快速开始

### 1. 启动电脑端接收服务

```bash
cd receiver
go run ./cmd/cliplinkd
```

默认监听 `:43837`，并通过 mDNS 和广播向局域网内的手机提供发现能力。

### 2. 安装 Android App

- 从 GitHub Releases 下载 APK
- 或者用 Android Studio 打开 `android-sender/` 自行编译安装

### 3. 连接设备并发送

- 打开 Android App
- 等待自动发现附近接收端
- 点选目标电脑后即可发送当前文本
- 如果自动发现失败，也可以手动输入 `IP:端口`

## 发送入口

| 方式 | 操作 |
|------|------|
| 通知栏快捷按钮 | 下拉通知栏，点击「📋 发送剪贴板」 |
| App 内按钮 | 打开 App，点击「📋 发送剪贴板」 |
| 系统分享菜单 | 在任意 App 中选择文本后点分享，选择 ClipLink |

## 项目特点

- **显式发送**：不在后台偷偷读取剪贴板，只在你触发时发送
- **纯局域网传输**：数据不经过第三方服务
- **自动发现 + 手动直连**：支持 mDNS、子网扫描和手动输入地址
- **兼容国内常见 ROM**：内置小米、OPPO、vivo、华为、三星等系统的权限引导
- **跨桌面平台**：接收端支持 macOS、Windows、Linux

## 系统要求

- Android 8.0+（minSdk 26）
- 桌面端支持 macOS / Windows / Linux
- 本地开发 receiver 需要 Go 1.21+
- 手机与电脑需处于同一局域网

## 仓库结构

```text
.
├── android-sender/   Android 发送端
├── receiver/         桌面接收端
└── .github/workflows/ CI 与发布流程
```

模块说明见：

- `android-sender/README.md`
- `receiver/README.md`

## 发布

- 推送标准语义化版本 tag，例如 `v0.1.2`
- GitHub Actions 会在同一个 GitHub Release 下发布 Android APK 和 receiver 桌面端产物
- Receiver 产物包含 Windows 压缩包、macOS 压缩包，以及 Linux `deb`、`rpm`、`apk`、`archlinux` 包

## 安全说明

- 通信使用局域网 HTTP 明文
- 不做账号系统，不经过云端
- 仅建议在受信任的本地网络中使用
