package config

import (
	"fmt"
	"os"
	"runtime"
	"strconv"
)

const (
	defaultPort            = 43837
	defaultMDNSServiceType = "_cliplink._tcp"
	defaultDeviceName      = "ClipLink Receiver"
)

type Config struct {
	Port            int
	ListenAddr      string
	DeviceName      string
	DeviceOS        string
	MDNSServiceType string
	MDNSServiceName string
}

func FromEnv() Config {
	port := defaultPort
	if raw := os.Getenv("CLIPLINK_PORT"); raw != "" {
		if v, err := strconv.Atoi(raw); err == nil && v > 0 && v <= 65535 {
			port = v
		}
	}

	serviceType := os.Getenv("CLIPLINK_MDNS_SERVICE_TYPE")
	if serviceType == "" {
		serviceType = defaultMDNSServiceType
	}

	deviceName := os.Getenv("CLIPLINK_DEVICE_NAME")
	if deviceName == "" {
		if host, err := os.Hostname(); err == nil && host != "" {
			deviceName = host
		} else {
			deviceName = defaultDeviceName
		}
	}

	serviceName := os.Getenv("CLIPLINK_MDNS_SERVICE_NAME")
	if serviceName == "" {
		serviceName = deviceName
	}

	return Config{
		Port:            port,
		ListenAddr:      fmt.Sprintf(":%d", port),
		DeviceName:      deviceName,
		DeviceOS:        runtime.GOOS,
		MDNSServiceType: serviceType,
		MDNSServiceName: serviceName,
	}
}
