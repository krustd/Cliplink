// diagnose is a standalone tool that checks whether cliplinkd is
// advertising itself correctly on the local network and whether its
// HTTP endpoint is reachable.
//
// Run it on the same machine as cliplinkd (or any machine on the LAN):
//
//	go run ./cmd/diagnose
package main

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/grandcat/zeroconf"
)

func main() {
	fmt.Println("=== ClipLink 网络诊断 ===")
	fmt.Println()

	fmt.Println("【1】在局域网中搜索 _cliplink._tcp 服务（最多等 6 秒）…")
	found := browseMDNS()

	if len(found) == 0 {
		fmt.Println()
		fmt.Println("❌ 未找到任何 ClipLink 服务。可能原因：")
		fmt.Println("   · cliplinkd 没有运行")
		fmt.Println("   · macOS 防火墙阻断了 mDNS 多播（UDP 5353）")
		fmt.Println("   · 路由器隔离了多播流量（AP 隔离）")
		return
	}

	fmt.Println()
	fmt.Println("【2】测试 HTTP 可达性…")
	for _, e := range found {
		for _, ip := range e.AddrIPv4 {
			pingHTTP(ip.String(), e.Port)
		}
		if len(e.AddrIPv4) == 0 {
			fmt.Println("   ⚠️  只有 IPv6 地址，Android 可能无法连接")
			for _, ip := range e.AddrIPv6 {
				fmt.Printf("      IPv6: %s\n", ip)
			}
		}
	}
}

type entry struct {
	name    string
	AddrIPv4 []interface{ String() string }
	AddrIPv6 []interface{ String() string }
	Port    int
	Text    []string
}

func browseMDNS() []*zeroconf.ServiceEntry {
	resolver, err := zeroconf.NewResolver(nil)
	if err != nil {
		fmt.Printf("   ❌ 无法创建 mDNS 解析器: %v\n", err)
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 6*time.Second)
	defer cancel()

	ch := make(chan *zeroconf.ServiceEntry)
	go func() {
		if err := resolver.Browse(ctx, "_cliplink._tcp", "local.", ch); err != nil {
			fmt.Printf("   ❌ mDNS Browse 失败: %v\n", err)
		}
	}()

	var results []*zeroconf.ServiceEntry
	for {
		select {
		case e, ok := <-ch:
			if !ok {
				return results
			}
			results = append(results, e)
			fmt.Printf("   ✅ 发现: %s\n", e.ServiceInstanceName())
			for _, ip := range e.AddrIPv4 {
				fmt.Printf("      IPv4 %s  端口 %d\n", ip, e.Port)
			}
			for _, ip := range e.AddrIPv6 {
				fmt.Printf("      IPv6 %s  端口 %d\n", ip, e.Port)
			}
			fmt.Printf("      TXT  %v\n", e.Text)
		case <-ctx.Done():
			return results
		}
	}
}

func pingHTTP(host string, port int) {
	url := fmt.Sprintf("http://%s:%d/healthz", host, port)
	fmt.Printf("   → GET %s  ", url)

	client := &http.Client{Timeout: 3 * time.Second}
	start := time.Now()
	resp, err := client.Get(url)
	elapsed := time.Since(start)

	if err != nil {
		fmt.Printf("❌ 失败: %v\n", err)
		fmt.Println("      可能原因: 防火墙未放行该端口，或 cliplinkd 未监听")
		return
	}
	defer resp.Body.Close()
	fmt.Printf("✅ %s  (%dms)\n", resp.Status, elapsed.Milliseconds())
}
