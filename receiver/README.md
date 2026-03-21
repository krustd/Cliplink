# ClipLink Receiver

这是局域网剪切板同步里的**接收端服务**（通常运行在 Mac/Windows/Linux）。

职责：
- 通过 mDNS 广播服务，方便手机发现接收端
- 提供 HTTP API 接收文本
- 收到文本后写入本机系统剪切板

当前只支持文本，后续可扩展图片/文件。

## Run

```bash
cd receiver
go run ./cmd/cliplinkd
```

默认监听 `:43837`。

### 环境变量

- `CLIPLINK_PORT`：监听端口（默认 43837）
- `CLIPLINK_DEVICE_NAME`：设备显示名（默认使用主机名）
- `CLIPLINK_MDNS_SERVICE_TYPE`：默认 `_cliplink._tcp`
- `CLIPLINK_MDNS_SERVICE_NAME`：mDNS 实例名（默认跟 `CLIPLINK_DEVICE_NAME` 一致）

## API

- `GET /healthz`
- `GET /api/v1/info`（返回设备名和系统类型）
- `POST /api/v1/clipboard/text`（纯文本直推）
- `POST /api/v1/clipboard/push`（预留扩展入口，当前仅支持 `type=text`）

文本直推示例：

```bash
curl -X POST 'http://<receiver-ip>:43837/api/v1/clipboard/text' \
  -H 'Content-Type: application/json' \
  -d '{"text":"hello from android","source":"android"}'
```

通用入口示例：

```bash
curl -X POST 'http://<receiver-ip>:43837/api/v1/clipboard/push' \
  -H 'Content-Type: application/json' \
  -d '{"type":"text","text":"hello from android","source":"android"}'
```

设备信息示例：

```bash
curl 'http://<receiver-ip>:43837/api/v1/info'
```
