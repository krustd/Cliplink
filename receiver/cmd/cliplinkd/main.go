package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	"cliplink/receiver/internal/clipboard"
	"cliplink/receiver/internal/config"
	"cliplink/receiver/internal/discovery"
	"cliplink/receiver/internal/httpapi"
)

func main() {
	cfg := config.FromEnv()

	clipboardWriter := clipboard.NewSystemWriter()
	handler := httpapi.NewHandler(cfg.DeviceName, cfg.DeviceOS, clipboardWriter)

	server := &http.Server{
		Addr:              cfg.ListenAddr,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
	}

	stopUDP, err := discovery.StartUDPDiscovery(discovery.UDPInfo{
		Name: cfg.DeviceName,
		Port: cfg.Port,
		OS:   cfg.DeviceOS,
	})
	if err != nil {
		log.Printf("warning: UDP broadcast discovery unavailable: %v", err)
	} else {
		defer stopUDP()
	}

	service, err := discovery.StartMDNS(discovery.Options{
		ServiceName: cfg.MDNSServiceName,
		ServiceType: cfg.MDNSServiceType,
		Domain:      "local.",
		Port:        cfg.Port,
		Text: []string{
			"api=/api/v1/clipboard/text",
			"info=/api/v1/info",
			"name=" + cfg.DeviceName,
			"os=" + cfg.DeviceOS,
			"content=text/plain+json",
			"version=1",
		},
	})
	if err != nil {
		log.Fatalf("failed to start mDNS service: %v", err)
	}
	defer service.Shutdown()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	go func() {
		log.Printf("cliplinkd listening on %s", cfg.ListenAddr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("http server failed: %v", err)
		}
	}()

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(shutdownCtx); err != nil {
		log.Printf("graceful shutdown error: %v", err)
	}

	log.Println("cliplinkd stopped")
}
