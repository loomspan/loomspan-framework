package webhost

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"testing/fstest"
)

func TestStaticHandlerServesEntryAndDeepLinks(t *testing.T) {
	handler := StaticHandler(testFiles())
	for _, target := range []string{"/", "/foundation/deep-link"} {
		response := request(t, handler, http.MethodGet, target)
		if response.Code != http.StatusOK || response.Body.String() != "<main>loomspan</main>" {
			t.Fatalf("%s returned %d %q", target, response.Code, response.Body.String())
		}
		if response.Header().Get("Cache-Control") != "no-store" {
			t.Fatalf("%s cache = %q", target, response.Header().Get("Cache-Control"))
		}
	}
}

func TestStaticHandlerServesContentAddressedAssets(t *testing.T) {
	handler := StaticHandler(testFiles())
	for _, target := range []string{"/assets/app-12345678.js", "/assets/index-mcU-Khxd.js"} {
		response := request(t, handler, http.MethodGet, target)
		if response.Code != http.StatusOK || response.Body.String() != `console.log("loomspan")` {
			t.Fatalf("%s asset response = %d %q", target, response.Code, response.Body.String())
		}
		if response.Header().Get("Cache-Control") != immutable {
			t.Fatalf("%s cache = %q", target, response.Header().Get("Cache-Control"))
		}
		if !strings.HasPrefix(response.Header().Get("Content-Type"), "text/javascript") {
			t.Fatalf("%s content type = %q", target, response.Header().Get("Content-Type"))
		}
	}
}

func TestStaticHandlerDoesNotFallbackAssetOrReservedPaths(t *testing.T) {
	for _, target := range []string{"/assets/missing-12345678.js", "/api/console/missing", "/file.css", "/../secret", "/%2e%2e/secret"} {
		response := request(t, StaticHandler(testFiles()), http.MethodGet, target)
		if response.Code != http.StatusNotFound {
			t.Fatalf("%s returned %d, want 404", target, response.Code)
		}
		if strings.Contains(response.Body.String(), "loomspan") {
			t.Fatalf("%s returned the SPA entry", target)
		}
	}
}

func TestStaticHandlerMethodAndHeaderPolicy(t *testing.T) {
	handler := StaticHandler(testFiles())
	head := request(t, handler, http.MethodHead, "/assets/app-12345678.js")
	if head.Code != http.StatusOK || head.Body.Len() != 0 || head.Header().Get("Content-Length") == "" {
		t.Fatalf("HEAD response = %d length=%q body=%q", head.Code, head.Header().Get("Content-Length"), head.Body.String())
	}
	if head.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Fatal("nosniff header missing")
	}
	post := request(t, handler, http.MethodPost, "/")
	if post.Code != http.StatusMethodNotAllowed || post.Header().Get("Allow") != "GET, HEAD" {
		t.Fatalf("POST response = %d Allow=%q", post.Code, post.Header().Get("Allow"))
	}
}

func TestStaticHandlerAppliesCompleteBrowserSecurityHeaders(t *testing.T) {
	response := request(t, StaticHandler(testFiles()), http.MethodGet, "/")
	expected := map[string]string{
		"Content-Security-Policy":      "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'; manifest-src 'self'",
		"X-Frame-Options":              "DENY",
		"X-Content-Type-Options":       "nosniff",
		"Referrer-Policy":              "no-referrer",
		"Permissions-Policy":           "camera=(), microphone=(), geolocation=(), payment=(), usb=()",
		"Cross-Origin-Opener-Policy":   "same-origin",
		"Cross-Origin-Resource-Policy": "same-origin",
	}
	for name, want := range expected {
		if got := response.Header().Get(name); got != want {
			t.Errorf("%s = %q, want %q", name, got, want)
		}
	}
}

func request(t *testing.T, handler http.Handler, method, target string) *httptest.ResponseRecorder {
	t.Helper()
	request := httptest.NewRequest(method, target, nil)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}

func testFiles() fstest.MapFS {
	return fstest.MapFS{
		"index.html":               {Data: []byte("<main>loomspan</main>")},
		"assets/app-12345678.js":   {Data: []byte(`console.log("loomspan")`)},
		"assets/index-mcU-Khxd.js": {Data: []byte(`console.log("loomspan")`)},
		"assets/app-12345678.css":  {Data: []byte("body{}")},
		".vite/manifest.json":      {Data: []byte("{}")},
	}
}
