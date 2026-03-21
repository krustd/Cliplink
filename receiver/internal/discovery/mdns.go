package discovery

import "github.com/grandcat/zeroconf"

type Options struct {
	ServiceName string
	ServiceType string
	Domain      string
	Port        int
	Text        []string
}

func StartMDNS(opts Options) (*zeroconf.Server, error) {
	return zeroconf.Register(
		opts.ServiceName,
		opts.ServiceType,
		opts.Domain,
		opts.Port,
		opts.Text,
		nil,
	)
}
