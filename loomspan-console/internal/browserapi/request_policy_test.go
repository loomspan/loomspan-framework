package browserapi

import (
	"net/http/httptest"
	"testing"
)

func TestBrowserPolicyAcceptsOnlyActualProductionAuthorityAndOrigin(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("POST", "http://127.0.0.1:7943/api/console/v1/bootstrap", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	if !policy.ValidateHost(request) || !policy.ValidateOrigin(request) {
		t.Fatal("exact production request rejected")
	}
	for _, host := range []string{"localhost:7943", "127.0.0.1:7944", "127.0.0.1:7943,evil"} {
		request.Host = host
		if policy.ValidateHost(request) {
			t.Fatalf("host %q accepted", host)
		}
	}
}

func TestDevelopmentPolicyAddsOnlyConfiguredViteAuthorityOriginPair(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "http://127.0.0.1:5173")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("POST", "http://127.0.0.1:5173/api/console/v1/bootstrap", nil)
	request.Host = "127.0.0.1:5173"
	request.Header.Set("Origin", "http://127.0.0.1:5173")
	if !policy.ValidateHost(request) || !policy.ValidateOrigin(request) {
		t.Fatal("exact development pair rejected")
	}
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	if policy.ValidateOrigin(request) {
		t.Fatal("cross-paired origin accepted")
	}
}

func TestParseLoopbackOriginRejectsBroadOrAmbiguousValues(t *testing.T) {
	for _, value := range []string{"https://127.0.0.1:1", "http://localhost:1", "http://0.0.0.0:1", "http://127.0.0.1", "http://127.0.0.1:1/", " http://127.0.0.1:1", "http://127.0.0.1:1/path", "http://user@127.0.0.1:1"} {
		if _, _, err := ParseLoopbackOrigin(value); err == nil {
			t.Fatalf("%q accepted", value)
		}
	}
}

func TestValidateDownloadRequestAcceptsSameOriginNavigationWithoutOrigin(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	// Real same-origin browser navigation: no Origin header, no Sec-Fetch-Site.
	if !policy.ValidateDownloadRequest(request) {
		t.Fatal("same-origin navigation without Origin header rejected")
	}
}

func TestValidateDownloadRequestAcceptsSameOriginNavigationMetadata(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Sec-Fetch-Site", "same-origin")
	request.Header.Set("Sec-Fetch-Mode", "navigate")
	if !policy.ValidateDownloadRequest(request) {
		t.Fatal("same-origin navigation metadata rejected")
	}
}

func TestValidateDownloadRequestAcceptsUserNavigationMetadata(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Sec-Fetch-Site", "none")
	request.Header.Set("Sec-Fetch-Mode", "navigate")
	request.Header.Set("Sec-Fetch-Dest", "empty")
	request.Header.Set("Sec-Fetch-User", "?1")
	if !policy.ValidateDownloadRequest(request) {
		t.Fatal("user navigation metadata rejected")
	}
}

func TestValidateDownloadRequestRejectsSameOriginFetch(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	for _, mode := range []string{"cors", "no-cors", "same-origin", ""} {
		request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
		request.Host = "127.0.0.1:7943"
		request.Header.Set("Sec-Fetch-Site", "same-origin")
		if mode != "" {
			request.Header.Set("Sec-Fetch-Mode", mode)
		}
		if policy.ValidateDownloadRequest(request) {
			t.Fatalf("same-origin fetch mode %q accepted", mode)
		}
	}
}

func TestValidateDownloadRequestRejectsCrossSiteFetchMetadata(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Sec-Fetch-Site", "cross-site")
	if policy.ValidateDownloadRequest(request) {
		t.Fatal("Sec-Fetch-Site: cross-site accepted")
	}
}

func TestValidateDownloadRequestRejectsSameSiteFetchMetadata(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Sec-Fetch-Site", "same-site")
	if policy.ValidateDownloadRequest(request) {
		t.Fatal("Sec-Fetch-Site: same-site accepted")
	}
}

func TestValidateDownloadRequestRejectsMatchingOriginWhenFetchMetadataAbsent(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	if policy.ValidateDownloadRequest(request) {
		t.Fatal("script-shaped request with matching Origin accepted")
	}
}

func TestValidateDownloadRequestRejectsMismatchedOriginWhenFetchMetadataAbsent(t *testing.T) {
	policy, err := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest("GET", "http://127.0.0.1:7943/api/console/v1/artifacts/trace-1/raw", nil)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://evil.test")
	if policy.ValidateDownloadRequest(request) {
		t.Fatal("mismatched Origin with absent Sec-Fetch-Site accepted")
	}
}
