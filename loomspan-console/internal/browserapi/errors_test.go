package browserapi

import (
	"net/http/httptest"
	"strings"
	"testing"
)

func TestJSONDecoderRejectsOversizeUnknownTrailingAndMultipleDocuments(t *testing.T) {
	for _, body := range []string{
		`{"value":"` + strings.Repeat("x", maxJSONBody) + `"}`,
		`{"unknown":true}`,
		`{} {}`,
	} {
		request := httptest.NewRequest("POST", "/", strings.NewReader(body))
		var target struct {
			Value string `json:"value,omitempty"`
		}
		if err := decodeJSON(request, &target); err == nil {
			t.Fatalf("accepted body length=%d", len(body))
		}
	}
}

func TestBrowserErrorEnvelopeIsBoundedSanitizedAndStable(t *testing.T) {
	response := httptest.NewRecorder()
	writeError(response, 403, "BROWSER_SECURITY_REJECTED", "Browser request rejected.")
	body := response.Body.String()
	if len(body) > 256 || strings.Contains(body, `C:\secret`) ||
		!strings.Contains(body, `"code":"BROWSER_SECURITY_REJECTED"`) {
		t.Fatalf("body=%q", body)
	}
	if response.Header().Get("Cache-Control") != "no-store" ||
		response.Header().Get("Content-Security-Policy") == "" {
		t.Fatal("security response policy missing")
	}
}
