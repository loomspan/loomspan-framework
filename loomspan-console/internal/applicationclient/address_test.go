package applicationclient

import "testing"

func TestNormalizeAddressAcceptsCanonicalHTTPHTTPSTargets(t *testing.T) {
	tests := []struct {
		input, display, root string
		unencrypted          bool
	}{
		{"http://127.0.0.1:8080", "http://127.0.0.1:8080", "http://127.0.0.1:8080/_loomspan/observability/v1", true},
		{"https://EXAMPLE.test:443/context/", "https://example.test/context", "https://example.test/context/_loomspan/observability/v1", false},
		{"http://[::1]:8080/application", "http://[::1]:8080/application", "http://[::1]:8080/application/_loomspan/observability/v1", true},
		{"http://example.test:80/", "http://example.test", "http://example.test/_loomspan/observability/v1", true},
	}
	for _, test := range tests {
		address, err := NormalizeAddress(test.input)
		if err != nil {
			t.Fatalf("%q: %v", test.input, err)
		}
		if address.String() != test.display || address.ObservabilityRoot() != test.root ||
			address.Unencrypted() != test.unencrypted {
			t.Fatalf("%q normalized to %q root=%q unencrypted=%v", test.input, address, address.ObservabilityRoot(), address.Unencrypted())
		}
	}
}

func TestNormalizeAddressRejectsAmbiguousOrUnsafeAuthorityAndPath(t *testing.T) {
	inputs := []string{
		"", " https://example.test", "ftp://example.test", "https:example.test",
		"https://user:secret@example.test", "https://example.test?x=1", "https://example.test/#x",
		"https://", "https://éxample.test", "https://example%2etest", "http://[fe80::1%25eth0]",
		"https://example.test:0", "https://example.test:abc", "https://example.test/a//b",
		"https://example.test/a/../b", "https://example.test/a/%2f/b", "https://example.test/a\\b",
	}
	for _, input := range inputs {
		if _, err := NormalizeAddress(input); err == nil {
			t.Errorf("accepted unsafe address %q", input)
		}
	}
}
