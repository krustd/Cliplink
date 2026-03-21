# ClipLink Receiver (`cliplinkd`)

ClipLink 的桌面端接收守护进程，运行在 macOS / Windows / Linux 上。

接收来自手机的文本并写入本机系统剪贴板，同时通过 mDNS 和 UDP 广播让手机自动发现。

## 快速启动

```bash
cd receiver
go run ./cmd/cliplinkd
```

默认监听 `:43837`，启动后控制台会打印监听地址和设备名。

### 编译为可执行文件

```bash
cd receiver
go build -o cliplinkd ./cmd/cliplinkd
./cliplinkd
```

### 查看版本信息

```bash
cd receiver
go run ./cmd/cliplinkd --version
```

---

## 发布包

- 推送标准语义化版本 tag，例如 `v0.1.0` 后，GitHub Actions 会自动发布跨平台构建产物
- 产物包含 Windows `.zip`、macOS `.tar.gz`，以及 Linux `deb`、`rpm`、`apk`、`archlinux` 包
- 如只想本地生成单个可执行文件，继续使用 `go build ./cmd/cliplinkd` 即可

---

## 配置（环境变量）

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `CLIPLINK_PORT` | `43837` | HTTP 监听端口 |
| `CLIPLINK_DEVICE_NAME` | 主机名 | 手机端设备列表中显示的名称 |
| `CLIPLINK_MDNS_SERVICE_TYPE` | `_cliplink._tcp` | mDNS 服务类型 |
| `CLIPLINK_MDNS_SERVICE_NAME` | 同 `CLIPLINK_DEVICE_NAME` | mDNS 实例名 |

示例：

```bash
CLIPLINK_PORT=8080 CLIPLINK_DEVICE_NAME="我的 MacBook" go run ./cmd/cliplinkd
```

---

## 设备发现机制

`cliplinkd` 同时启用两种广播方式，手机端自动选用可用的：

| 机制 | 端口/协议 | 适用场景 |
|------|-----------|---------|
| **mDNS** | UDP 5353 | 路由器支持组播（大多数家用路由器）|
| **UDP 广播** | UDP 43838 | 路由器禁止组播时的兜底方案 |

---

## HTTP API

### `GET /healthz`

健康检查，返回 `200 OK`。

```bash
curl http://192.168.1.8:43837/healthz
```

---

### `GET /api/v1/info`

返回设备信息（JSON）。

```bash
curl http://192.168.1.8:43837/api/v1/info
```

```json
{
  "name": "My-MacBook",
  "os": "darwin"
}
```

---

### `POST /api/v1/clipboard/text`

直接推送纯文本到剪贴板。

```bash
curl -X POST http://192.168.1.8:43837/api/v1/clipboard/text \
  -H 'Content-Type: application/json' \
  -d '{"text": "hello from android", "source": "android"}'
```

---

### `POST /api/v1/clipboard/push`

通用推送入口（预留扩展，当前仅支持 `type=text`）。

```bash
curl -X POST http://192.168.1.8:43837/api/v1/clipboard/push \
  -H 'Content-Type: application/json' \
  -d '{"type": "text", "text": "hello from android", "source": "android"}'
```

---

## 诊断工具

如遇连接问题，可运行诊断程序检查网络和服务状态：

```bash
cd receiver
go run ./cmd/diagnose
```

---

## 系统要求

- Go 1.21+
- macOS、Linux 或 Windows
- 与 Android 手机在同一局域网下

### 依赖

| 包 | 用途 |
|----|------|
| `github.com/atotto/clipboard` | 系统剪贴板读写 |
| `github.com/grandcat/zeroconf` | mDNS 服务广播与发现 |

---

## 安全说明

- 通信使用 HTTP 明文，**仅适用于受信任的局域网**
- 不对外暴露，不做身份验证
- 监听所有网卡（`0.0.0.0`），如需限制可通过防火墙规则控制
