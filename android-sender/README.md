# ClipLink Android Sender

Android 发送端：
- 搜索局域网里的 ClipLink Receiver（mDNS `_cliplink._tcp`）
- 选择目标设备
- 启动前台后台服务，监听剪切板变化
- 每次复制文本后自动推送到目标设备

## 功能状态

- 已实现：设备扫描、设备选择、前台服务常驻、复制文本自动推送
- 当前仅支持：文本

## 打开方式

1. 用 Android Studio 打开 `android-sender/`
2. 等待 Gradle Sync 完成
3. 运行到手机

## 使用流程

1. 先启动 Mac/PC 上的 `receiver`
2. 打开 Android App，点“搜索设备”
3. 点选目标设备（会自动启动后台同步）
4. 之后你在手机复制文本，就会自动 POST 到接收端

如果搜索不到设备，可用手动模式：

1. 在 App 里输入 `IP:端口`（例如 `192.168.1.8:43837`）
2. 可选输入一个设备名
3. 点“手动保存并使用”（会自动启动后台同步）

## 注意

- App 走的是 HTTP 明文（局域网内），Manifest 已打开 `usesCleartextTraffic=true`
- Android 新版本对剪切板后台访问有限制，不同系统 ROM 可能行为不同
- 建议把 App 电池优化设为“不限制”，减少后台被杀概率
