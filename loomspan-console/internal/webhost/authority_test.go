package webhost

import (
	"net"
	"testing"
)

type testAddress string

func (address testAddress) Network() string { return "tcp" }
func (address testAddress) String() string  { return string(address) }

func TestAuthorityFromActualIPv4AndIPv6BoundAddress(t *testing.T) {
	for _, test := range []struct {
		address string
		host    string
		origin  string
	}{
		{"127.0.0.1:43129", "127.0.0.1:43129", "http://127.0.0.1:43129"},
		{"[::1]:43129", "[::1]:43129", "http://[::1]:43129"},
	} {
		authority, err := AuthorityFromAddress(testAddress(test.address))
		if err != nil {
			t.Fatal(err)
		}
		if authority.Host != test.host || authority.Origin != test.origin {
			t.Fatalf("%s -> %#v", test.address, authority)
		}
	}
}

func TestHostUsesEphemeralBoundPortRatherThanConfiguredZero(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	authority, err := AuthorityFromAddress(listener.Addr())
	if err != nil || authority.Host == "127.0.0.1:0" {
		t.Fatalf("authority=%#v err=%v", authority, err)
	}
}
