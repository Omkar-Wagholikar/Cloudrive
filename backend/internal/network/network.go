package network

import (
	"fmt"
	"net"
)

// LocalAddresses returns all non-loopback IPv4 addresses on the machine,
// formatted as "ip:port" using the provided port string (e.g. ":8081").
func LocalAddresses(port string) ([]string, error) {
	ifaces, err := net.Interfaces()
	if err != nil {
		return nil, fmt.Errorf("interfaces: %w", err)
	}

	var addrs []string
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		ifAddrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range ifAddrs {
			var ip net.IP
			switch v := a.(type) {
			case *net.IPNet:
				ip = v.IP
			case *net.IPAddr:
				ip = v.IP
			}
			if ip == nil || ip.IsLoopback() {
				continue
			}
			if v4 := ip.To4(); v4 != nil {
				addrs = append(addrs, v4.String()+port)
			}
		}
	}
	return addrs, nil
}
