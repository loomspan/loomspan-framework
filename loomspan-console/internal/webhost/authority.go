package webhost

import (
	"fmt"
	"net"
)

type Authority struct {
	Host   string
	Origin string
}

func AuthorityFromAddress(address net.Addr) (Authority, error) {
	host, port, err := net.SplitHostPort(address.String())
	if err != nil {
		return Authority{}, fmt.Errorf("derive listener authority: %w", err)
	}
	ip := net.ParseIP(host)
	if ip == nil || !ip.IsLoopback() {
		return Authority{}, fmt.Errorf("bound listener is not an explicit loopback IP")
	}
	canonical := net.JoinHostPort(ip.String(), port)
	return Authority{Host: canonical, Origin: "http://" + canonical}, nil
}
