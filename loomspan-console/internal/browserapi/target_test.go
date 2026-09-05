package browserapi

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/browserauth"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

func TestTargetRoutesMapSharedErrorsToStableBrowserEnvelopeAndHTTPStatus(t *testing.T) {
	tests := []struct {
		code   consolecore.Code
		status int
	}{
		{consolecore.CodeInvalidArgument, http.StatusBadRequest},
		{consolecore.CodeTargetAuthentication, http.StatusUnauthorized},
		{consolecore.CodeTargetAccessBlocked, http.StatusForbidden},
		{consolecore.CodeNotFound, http.StatusNotFound},
		{consolecore.CodeIncompatibleTarget, http.StatusConflict},
		{consolecore.CodeInvalidArtifact, http.StatusUnprocessableEntity},
		{consolecore.CodeLimitExceeded, http.StatusTooManyRequests},
		{consolecore.CodeTargetUnavailable, http.StatusServiceUnavailable},
		{consolecore.CodeConsoleError, http.StatusInternalServerError},
	}
	for _, test := range tests {
		response := httptest.NewRecorder()
		writeDomainError(response, consolecore.NewError(test.code, "Safe message.", "scope-1", consolecore.Details{}, nil))
		if response.Code != test.status || !strings.Contains(response.Body.String(), `"code":"`+string(test.code)+`"`) {
			t.Errorf("%s status=%d body=%s", test.code, response.Code, response.Body.String())
		}
	}
}

func TestTargetRoutesApplySecurityBeforeBodyRead(t *testing.T) {
	entropy := bytes.Repeat([]byte{4}, 32*16)
	pairing := browserauth.NewPairing(nil, bytes.NewReader(entropy))
	registry := browserauth.NewRegistry(nil, bytes.NewReader(entropy))
	policy, _ := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	router, _ := New(Options{
		Policy: policy, Pairing: pairing, Sessions: registry,
		PairingURL: func(value string) string { return value },
	})
	for _, path := range []string{
		"/api/console/v1/target/status",
		"/api/console/v1/target/connect",
		"/api/console/v1/target/credential",
		"/api/console/v1/target/recheck",
	} {
		body := &readSpy{Reader: strings.NewReader(strings.Repeat("x", maxTargetJSONBody+1))}
		request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943"+path, body)
		request.Host = "127.0.0.1:7943"
		request.Header.Set("Origin", "http://127.0.0.1:7943")
		response := httptest.NewRecorder()
		router.ServeHTTP(response, request)
		if response.Code != http.StatusUnauthorized || body.read {
			t.Errorf("%s status=%d bodyRead=%v", path, response.Code, body.read)
		}
	}
}

func TestTargetBodyLimitDoesNotExpandExistingLimit(t *testing.T) {
	content := `{}` + strings.Repeat(" ", maxJSONBody)
	targetRequest := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(content))
	if err := decodeJSONLimit(targetRequest, &struct{}{}, maxTargetJSONBody); err != nil {
		t.Fatalf("valid target body within 4 KiB was rejected: %v", err)
	}
	ordinary := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(content))
	if err := decodeJSON(ordinary, &struct{}{}); err == nil || !strings.Contains(err.Error(), "exceeds") {
		t.Fatalf("ordinary body limit expanded: %v", err)
	}
}
