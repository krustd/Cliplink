package discovery

import (
	"encoding/json"
	"fmt"
	"log"
	"net"
)

// UDPDiscoveryPort is the UDP port used for broadcast device discovery.
// Android sends "CLIPLINK_DISCOVER" to 255.255.255.255:UDPDiscoveryPort;
// the receiver replies with a JSON payload containing its connection info.
const UDPDiscoveryPort = 43838

const udpProbe = "CLIPLINK_DISCOVER"

// UDPInfo is the JSON payload sent back to the Android client.
type UDPInfo struct {
	Name string `json:"name"`
	Port int    `json:"port"`
	OS   string `json:"os"`
}

// StartUDPDiscovery binds a UDP socket on UDPDiscoveryPort and replies to
// every valid probe. It returns a stop function; call it to close the socket.
func StartUDPDiscovery(info UDPInfo) (stop func(), err error) {
	conn, err := net.ListenPacket("udp4", fmt.Sprintf(":%d", UDPDiscoveryPort))
	if err != nil {
		return nil, err
	}

	resp, _ := json.Marshal(info)

	go func() {
		buf := make([]byte, 64)
		for {
			n, src, err := conn.ReadFrom(buf)
			if err != nil {
				return // socket closed
			}
			if string(buf[:n]) == udpProbe {
				_, _ = conn.WriteTo(resp, src)
				log.Printf("udp-discovery: responded to %s", src)
			}
		}
	}()

	return func() { conn.Close() }, nil
}
