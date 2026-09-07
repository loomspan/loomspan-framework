package browserapi

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/browserauth"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/live"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
)

type failedDiagnosticResponse struct{ *httptest.ResponseRecorder }

func (w failedDiagnosticResponse) Write(p []byte) (int, error) {
	return 0, errors.New("secret-writer-canary")
}
func diagnosticRecords(t *testing.T, b *bytes.Buffer) []map[string]any {
	t.Helper()
	var records []map[string]any
	for _, line := range bytes.Split(bytes.TrimSpace(b.Bytes()), []byte("\n")) {
		if len(line) == 0 {
			continue
		}
		var r map[string]any
		if err := json.Unmarshal(line, &r); err != nil {
			t.Fatal(err)
		}
		records = append(records, r)
	}
	return records
}
func TestResponseEncodingAndWriteDiagnostic(t *testing.T) {
	var logs bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)), "browser.skillsList")
	recorder := httptest.NewRecorder()
	writer := wrapDiagnosticWriter(failedDiagnosticResponse{recorder}, ctx)
	domain := consolecore.NewError(consolecore.CodeConsoleError, "protected public message", "", consolecore.Details{}, errors.New("secret-domain-canary"))
	writeDomainError(writer, domain)
	records := diagnosticRecords(t, &logs)
	if len(records) != 2 {
		t.Fatalf("want distinct domain and response failures: %s", logs.String())
	}
	if recorder.Code != 500 || records[1]["cause"] != "response_write" {
		t.Fatalf("status=%d records=%v", recorder.Code, records)
	}
	for _, r := range records {
		if r["request_id"] == nil || r["operation_id"] == nil || r["operation"] != "browser.skillsList" {
			t.Fatalf("missing correlation: %v", r)
		}
	}
	if strings.Contains(logs.String(), "canary") || strings.Contains(logs.String(), "protected public") {
		t.Fatal("content leaked")
	}
	logs.Reset()
	recorder = httptest.NewRecorder()
	writeJSON(wrapDiagnosticWriter(recorder, ctx), 200, make(chan int))
	records = diagnosticRecords(t, &logs)
	if len(records) != 1 || records[0]["cause"] != "response_encode" || recorder.Code != 500 {
		t.Fatalf("encode failure: %s %d", logs.String(), recorder.Code)
	}
}
func TestCanceledResponseWriteDiagnosticIsQuiet(t *testing.T) {
	var logs bytes.Buffer
	ctx, cancel := context.WithCancel(diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)), "browser.activityStream"))
	cancel()
	writer := wrapDiagnosticWriter(failedDiagnosticResponse{httptest.NewRecorder()}, ctx)
	writeJSON(writer, 200, map[string]bool{"ok": true})
	if logs.Len() != 0 {
		t.Fatalf("canceled write: %s", logs.String())
	}
}
func TestBrowserDiagnosticOperationsAndFlusherPreservation(t *testing.T) {
	if browserOperation("/api/console/v1/artifacts/secret-trace-canary/raw") != "browser.artifactRawDownload" || browserOperation("/secret-canary") != "browser.unknown" {
		t.Fatal("operation uses request content")
	}
	if _, ok := wrapDiagnosticWriter(httptest.NewRecorder(), context.Background()).(http.Flusher); !ok {
		t.Fatal("lost flusher")
	}
}

type diagnosticArtifactClient struct{ *countingArtifactProbeClient }
type diagnosticArtifactReader struct{ read bool }

func (r *diagnosticArtifactReader) Read(p []byte) (int, error) {
	if r.read {
		return 0, errors.New("secret-reader-canary")
	}
	r.read = true
	return copy(p, []byte("prefix")), nil
}
func (c diagnosticArtifactClient) OpenArtifact(context.Context, string, string, applicationclient.Credential) (*applicationclient.ArtifactStream, error) {
	return applicationclient.NewTestArtifactStream(io.NopCloser(&diagnosticArtifactReader{}), "11111111-1111-4111-8111-111111111111", -1), nil
}
func TestDownloadLateFailureDiagnostic(t *testing.T) {
	router, cookie, target := downloadTestRouterWithTarget(t, diagnosticArtifactClient{&countingArtifactProbeClient{}})
	defer target.Close()
	for _, writeFailure := range []bool{false, true} {
		var logs bytes.Buffer
		request := downloadRequest(cookie, "secret-trace-canary")
		request.Header.Set("X-Request-ID", "secret-header-canary")
		request = request.WithContext(diagnostics.WithLogger(request.Context(), diagnostics.NewLogger(&logs)))
		recorder := httptest.NewRecorder()
		var writer http.ResponseWriter = recorder
		if writeFailure {
			writer = failedDiagnosticResponse{recorder}
		}
		router.ServeHTTP(writer, request)
		records := diagnosticRecords(t, &logs)
		if recorder.Code != 200 || len(records) != 1 {
			t.Fatalf("late failure contract: %d %s", recorder.Code, logs.String())
		}
		cause := "body_read"
		if writeFailure {
			cause = "response_write"
		} else if recorder.Body.String() != "prefix" {
			t.Fatalf("body changed: %q", recorder.Body.String())
		}
		r := records[0]
		if r["cause"] != cause || r["endpoint"] != "artifact.download" || r["scope_id"] != "scope-1" || r["request_id"] == nil || r["operation"] != "browser.artifactRawDownload" {
			t.Fatalf("record: %v", r)
		}
		if strings.Contains(logs.String(), "canary") {
			t.Fatal("content leaked")
		}
	}
}

func TestActivityRelayLateFailureDiagnostic(t *testing.T) {
	service := live.NewService(context.Background())
	defer service.Close()
	router, session := activityTestRouter(t, service)
	defer router.options.Target.Close()
	tab, err := router.options.Sessions.Bootstrap(context.Background(), session, "")
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.AddCookie(browserauth.SessionCookie(session))
	request.Header.Set("X-loomspan-Console-Tab", tab.TabID)
	request.Header.Set(csrfHeader, tab.CSRF)
	var logs bytes.Buffer
	request = request.WithContext(diagnostics.WithLogger(request.Context(), diagnostics.NewLogger(&logs)))
	response := httptest.NewRecorder()
	router.ServeHTTP(failedDiagnosticResponse{response}, request)
	records := diagnosticRecords(t, &logs)
	if response.Code != 200 || response.Header().Get("Content-Type") != "text/event-stream" || len(records) != 1 {
		t.Fatalf("relay contract: %d %s", response.Code, logs.String())
	}
	if records[0]["cause"] != "response_write" || records[0]["endpoint"] != "activity.stream" || records[0]["request_id"] == nil || records[0]["scope_id"] == nil {
		t.Fatalf("relay: %v", records[0])
	}
	if strings.Contains(logs.String(), "canary") {
		t.Fatal("secret leaked")
	}
}

type diagnosticMCPManager struct{ fakeMCPManager }

func (*diagnosticMCPManager) Enable(context.Context) (string, error) {
	return "", errors.New("secret-management-canary")
}
func TestBrowserFailureDiagnosticsPreserveContract(t *testing.T) {
	for _, test := range []struct {
		name, path, body, contentType, operation, code string
		status                                         int
		scope                                          bool
	}{
		{"acquisition", "/artifacts/acquire", `{"traceId":"secret-trace-canary"}`, "application/json", "browser.artifactAcquire", "CONSOLE_ERROR", 500, true},
		{"import", "/artifacts/import", "secret-content-canary", "application/x-ndjson", "browser.artifactImport", "CONSOLE_ERROR", 500, false},
		{"analysis", "/traces/analysis/summary", `{"traceId":"secret-trace-canary","source":"TARGET"}`, "application/json", "browser.traceAnalysisSummary", "CONSOLE_ERROR", 500, true},
		{"imported analysis", "/traces/analysis/summary", `{"traceId":"secret-trace-canary","source":"IMPORTED"}`, "application/json", "browser.traceAnalysisSummary", "CONSOLE_ERROR", 500, false},
		{"management", "/mcp/enable", `{}`, "application/json", "browser.mcpEnable", "MCP_STATE_CHANGED", 409, false},
		{"target dependency", "/target/recheck", `{}`, "application/json", "browser.targetRecheck", "CONSOLE_ERROR", 500, false},
	} {
		t.Run(test.name, func(t *testing.T) {
			domain := consolecore.NewError(consolecore.CodeConsoleError, "Protected operation failure.", "", consolecore.Details{}, errors.New("secret-nested-canary"))
			fake := &fakeArtifactService{acquireErr: domain, importErr: domain, lookupResult: artifact.LookupResult{Handle: "handle", LocalAvailable: true}}
			router, tab, csrf, cookie := artifactTestRouterWithCSRF(t, fake)
			originalTarget := router.options.Target
			defer originalTarget.Close()
			router.options.TraceAnalysis = &fakeTraceAnalysisService{summaryErr: domain}
			router.options.MCP = &diagnosticMCPManager{}
			if test.name == "target dependency" {
				router.options.Target = nil
			}
			var logs bytes.Buffer
			request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1"+test.path, strings.NewReader(test.body))
			request.Host = "127.0.0.1:7943"
			request.Header.Set("Origin", "http://127.0.0.1:7943")
			request.Header.Set("Content-Type", test.contentType)
			request.AddCookie(cookie)
			request.Header.Set("X-loomspan-Console-Tab", tab)
			request.Header.Set(csrfHeader, csrf)
			request = request.WithContext(diagnostics.WithLogger(request.Context(), diagnostics.NewLogger(&logs)))
			response := httptest.NewRecorder()
			router.ServeHTTP(response, request)
			if response.Code != test.status || !strings.Contains(response.Body.String(), `"code":"`+test.code+`"`) {
				t.Fatalf("response contract: %d %s", response.Code, response.Body.String())
			}
			records := diagnosticRecords(t, &logs)
			if len(records) != 1 {
				t.Fatalf("want one primary: %s", logs.String())
			}
			r := records[0]
			if r["operation"] != test.operation || r["request_id"] == nil || r["operation_id"] == nil {
				t.Fatalf("correlation: %v", r)
			}
			if (r["scope_id"] != nil) != test.scope {
				t.Fatalf("scope: %v", r)
			}
			if strings.Contains(logs.String(), "canary") || strings.Contains(logs.String(), "Protected operation") {
				t.Fatal("sensitive content leaked")
			}
		})
	}
}

type failingEntropy struct{}

func (failingEntropy) Read([]byte) (int, error) { return 0, errors.New("secret-entropy-canary") }

func TestPairingEntropyFailurePreservesResponseAndCorrelation(t *testing.T) {
	for _, boundary := range []string{"pairing", "session", "tab"} {
		t.Run(boundary, func(t *testing.T) {
			pairing := browserauth.NewPairing(nil, nil)
			sessions := browserauth.NewRegistry(nil, nil)
			path, body := "/pairing/challenge", `{}`
			var cookie *http.Cookie
			if boundary == "pairing" {
				pairing = browserauth.NewPairing(nil, failingEntropy{})
			}
			if boundary == "session" {
				secret, _ := pairing.Create(context.Background(), false)
				sessions = browserauth.NewRegistry(nil, failingEntropy{})
				path, body = "/pairing/exchange", `{"secret":"`+secret+`"}`
			}
			if boundary == "tab" {
				sessions = browserauth.NewRegistry(nil, io.MultiReader(bytes.NewReader(make([]byte, 32)), failingEntropy{}))
				session, err := sessions.CreateSession(context.Background())
				if err != nil {
					t.Fatal(err)
				}
				cookie = browserauth.SessionCookie(session)
				path = "/bootstrap"
			}
			policy, _ := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
			router, _ := New(Options{Policy: policy, Pairing: pairing, Sessions: sessions, PairingURL: func(s string) string { return s }})
			var logs bytes.Buffer
			req := httptest.NewRequest("POST", "http://127.0.0.1:7943/api/console/v1"+path, strings.NewReader(body))
			req.Header.Set("Origin", "http://127.0.0.1:7943")
			if cookie != nil {
				req.AddCookie(cookie)
			}
			req = req.WithContext(diagnostics.WithLogger(req.Context(), diagnostics.NewLogger(&logs)))
			response := httptest.NewRecorder()
			router.ServeHTTP(response, req)
			if response.Code != 429 {
				t.Fatalf("response changed: %d %s", response.Code, response.Body.String())
			}
			records := diagnosticRecords(t, &logs)
			failures := 0
			for _, r := range records {
				if r["request_id"] == nil {
					t.Fatalf("missing request correlation: %v", r)
				}
				if r["msg"] == "operation failed" {
					failures++
					if r["cause"] != "entropy" {
						t.Fatal(r)
					}
				}
			}
			if failures != 1 || strings.Contains(logs.String(), "canary") {
				t.Fatal(logs.String())
			}
		})
	}
}

type cachedDiagnosticClient struct{ *countingArtifactProbeClient }

func (c cachedDiagnosticClient) Get(context.Context, string, int64, applicationclient.Credential) ([]byte, string, error) {
	return nil, "", diagnostics.Annotate(errors.New("secret-upstream-canary"), diagnostics.Facts{Cause: "connection", Endpoint: "traces.get"})
}
func TestCachedTraceFallbackStillDiagnosesUpstreamFailure(t *testing.T) {
	router, cookie, owner := downloadTestRouterWithTarget(t, cachedDiagnosticClient{&countingArtifactProbeClient{}})
	defer owner.Close()
	router.options.Observability = observability.New()
	router.options.Artifacts = &fakeArtifactService{lookupResult: artifact.LookupResult{LocalAvailable: true, Metadata: artifact.TraceMetadata{TraceID: "trace-canary"}}}
	var logs bytes.Buffer
	req := httptest.NewRequest("POST", "http://127.0.0.1:7943/api/console/v1/traces/detail", strings.NewReader(`{"traceId":"trace-canary"}`))
	req.Header.Set("Origin", "http://127.0.0.1:7943")
	req.AddCookie(cookie)
	req = req.WithContext(diagnostics.WithLogger(req.Context(), diagnostics.NewLogger(&logs)))
	response := httptest.NewRecorder()
	router.ServeHTTP(response, req)
	records := diagnosticRecords(t, &logs)
	if response.Code != 200 || !strings.Contains(response.Body.String(), `"localAvailable":true`) || len(records) != 1 {
		t.Fatalf("%d %s %s", response.Code, response.Body.String(), logs.String())
	}
	if records[0]["scope_id"] != "scope-1" || records[0]["endpoint"] != "traces.get" || records[0]["request_id"] == nil || strings.Contains(logs.String(), "canary") {
		t.Fatal(logs.String())
	}
}
