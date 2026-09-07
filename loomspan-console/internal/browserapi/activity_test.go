package browserapi

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/browserauth"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/live"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
)

func activityTestRouter(t *testing.T, liveService *live.Service) (*Router, string) {
	t.Helper()
	entropy := bytes.Repeat([]byte{14}, 32*16)
	pairing := browserauth.NewPairing(nil, bytes.NewReader(entropy))
	registry := browserauth.NewRegistry(nil, bytes.NewReader(entropy))
	sessionID, _ := registry.CreateSession(context.Background())
	policy, _ := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	targetContext, _ := target.New(func(applicationclient.Address) (target.ProbeClient, error) {
		return &fakeProbeClient{}, nil
	}, nil, nil)
	_ = targetContext.Select("http://127.0.0.1:8080")
	router, _ := New(Options{
		Policy:     policy,
		Pairing:    pairing,
		Sessions:   registry,
		PairingURL: func(value string) string { return value },
		Target:     targetContext,
		Live:       liveService,
	})
	return router, sessionID
}

func TestActivityRecentReturnsItemsFromLiveService(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)
	response := apiRequest(router, "/api/console/v1/activity/recent", `{"cursor":"","limit":10}`, browserauth.SessionCookie(sessionID))
	if response.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", response.Code, response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"items":[]`) {
		t.Fatalf("expected empty items array, got %s", response.Body.String())
	}
	if !strings.Contains(response.Body.String(), `"hasMore":false`) {
		t.Fatalf("expected hasMore=false, got %s", response.Body.String())
	}
}

func TestActivityRecentRejectsInvalidJSON(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)
	response := apiRequest(router, "/api/console/v1/activity/recent", `{invalid}`, browserauth.SessionCookie(sessionID))
	if response.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", response.Code)
	}
}

func TestActivityRecentRequiresSession(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, _ := activityTestRouter(t, liveService)
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/recent", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", response.Code)
	}
}

func TestActivityStreamRequiresSession(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, _ := activityTestRouter(t, liveService)
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", response.Code)
	}
}

func TestActivityStreamReturnsSSEContentType(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)

	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	if bootstrap.Code != http.StatusOK {
		t.Fatalf("bootstrap failed: %d %s", bootstrap.Code, bootstrap.Body.String())
	}
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	if err := json.Unmarshal(bootstrap.Body.Bytes(), &bs); err != nil {
		t.Fatal(err)
	}

	streamCtx, streamCancel := context.WithCancel(context.Background())
	defer streamCancel()
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request = request.WithContext(streamCtx)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request.Header.Set(csrfHeader, bs.CSRFToken)
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	done := make(chan struct{})
	go func() {
		router.ServeHTTP(response, request)
		close(done)
	}()
	time.Sleep(100 * time.Millisecond)
	streamCancel()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("stream handler did not exit after context cancellation")
	}
	// Read headers after the handler goroutine has exited to avoid a data
	// race on the ResponseRecorder's header map.
	if contentType := response.Header().Get("Content-Type"); !strings.HasPrefix(contentType, "text/event-stream") {
		t.Fatalf("expected text/event-stream, got %q", contentType)
	}
}

func TestActivityRecentReturnsErrorWhenLiveIsNil(t *testing.T) {
	entropy := bytes.Repeat([]byte{14}, 32*16)
	pairing := browserauth.NewPairing(nil, bytes.NewReader(entropy))
	registry := browserauth.NewRegistry(nil, bytes.NewReader(entropy))
	sessionID, _ := registry.CreateSession(context.Background())
	policy, _ := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	targetContext, _ := target.New(func(applicationclient.Address) (target.ProbeClient, error) {
		return &fakeProbeClient{}, nil
	}, nil, nil)
	_ = targetContext.Select("http://127.0.0.1:8080")
	router, _ := New(Options{
		Policy:     policy,
		Pairing:    pairing,
		Sessions:   registry,
		PairingURL: func(value string) string { return value },
		Target:     targetContext,
	})
	response := apiRequest(router, "/api/console/v1/activity/recent", `{}`, browserauth.SessionCookie(sessionID))
	if response.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", response.Code)
	}
}

func TestActivityStreamReturnsErrorWhenLiveIsNil(t *testing.T) {
	entropy := bytes.Repeat([]byte{14}, 32*16)
	pairing := browserauth.NewPairing(nil, bytes.NewReader(entropy))
	registry := browserauth.NewRegistry(nil, bytes.NewReader(entropy))
	sessionID, _ := registry.CreateSession(context.Background())
	policy, _ := NewPolicy("127.0.0.1:7943", "http://127.0.0.1:7943", "")
	targetContext, _ := target.New(func(applicationclient.Address) (target.ProbeClient, error) {
		return &fakeProbeClient{}, nil
	}, nil, nil)
	_ = targetContext.Select("http://127.0.0.1:8080")
	router, _ := New(Options{
		Policy:     policy,
		Pairing:    pairing,
		Sessions:   registry,
		PairingURL: func(value string) string { return value },
		Target:     targetContext,
	})
	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	if bootstrap.Code != http.StatusOK {
		t.Fatalf("bootstrap failed: %d %s", bootstrap.Code, bootstrap.Body.String())
	}
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	if err := json.Unmarshal(bootstrap.Body.Bytes(), &bs); err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request.Header.Set(csrfHeader, bs.CSRFToken)
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", response.Code)
	}
}

func TestActivityStreamRejectsMissingTabHeader(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for missing tab header, got %d", response.Code)
	}
}

func TestActivityStreamRejectsMissingCSRFToken(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)

	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	if bootstrap.Code != http.StatusOK {
		t.Fatalf("bootstrap failed: %d %s", bootstrap.Code, bootstrap.Body.String())
	}
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	if err := json.Unmarshal(bootstrap.Body.Bytes(), &bs); err != nil {
		t.Fatal(err)
	}

	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	router.ServeHTTP(response, request)
	if response.Code != http.StatusForbidden {
		t.Fatalf("expected 403 for missing CSRF token, got %d", response.Code)
	}
}

func TestActivityStreamRejectsDuplicateRelayPerTab(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)

	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	if bootstrap.Code != http.StatusOK {
		t.Fatalf("bootstrap failed: %d %s", bootstrap.Code, bootstrap.Body.String())
	}
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	if err := json.Unmarshal(bootstrap.Body.Bytes(), &bs); err != nil {
		t.Fatal(err)
	}

	streamCtx, streamCancel := context.WithCancel(context.Background())
	defer streamCancel()
	request1 := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request1 = request1.WithContext(streamCtx)
	request1.Host = "127.0.0.1:7943"
	request1.Header.Set("Origin", "http://127.0.0.1:7943")
	request1.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request1.Header.Set(csrfHeader, bs.CSRFToken)
	request1.AddCookie(browserauth.SessionCookie(sessionID))
	response1 := httptest.NewRecorder()
	done1 := make(chan struct{})
	go func() {
		router.ServeHTTP(response1, request1)
		close(done1)
	}()
	time.Sleep(100 * time.Millisecond)

	request2 := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request2 = request2.WithContext(context.Background())
	request2.Host = "127.0.0.1:7943"
	request2.Header.Set("Origin", "http://127.0.0.1:7943")
	request2.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request2.Header.Set(csrfHeader, bs.CSRFToken)
	request2.AddCookie(browserauth.SessionCookie(sessionID))
	response2 := httptest.NewRecorder()
	router.ServeHTTP(response2, request2)
	if response2.Code != http.StatusConflict {
		t.Fatalf("expected 409 for duplicate relay, got %d", response2.Code)
	}

	streamCancel()
	select {
	case <-done1:
	case <-time.After(time.Second):
		t.Fatal("first stream handler did not exit")
	}
}

func TestActivityStreamSetsNoStoreAndNosniffHeaders(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()
	router, sessionID := activityTestRouter(t, liveService)

	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	_ = json.Unmarshal(bootstrap.Body.Bytes(), &bs)

	streamCtx, streamCancel := context.WithCancel(context.Background())
	defer streamCancel()
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request = request.WithContext(streamCtx)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request.Header.Set(csrfHeader, bs.CSRFToken)
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	done := make(chan struct{})
	go func() {
		router.ServeHTTP(response, request)
		close(done)
	}()
	time.Sleep(100 * time.Millisecond)
	streamCancel()
	<-done
	// Read headers after the handler goroutine has exited to avoid a data
	// race on the ResponseRecorder's header map.
	if cc := response.Header().Get("Cache-Control"); cc != "no-store" {
		t.Fatalf("expected Cache-Control no-store, got %q", cc)
	}
	if xcto := response.Header().Get("X-Content-Type-Options"); xcto != "nosniff" {
		t.Fatalf("expected X-Content-Type-Options nosniff, got %q", xcto)
	}
}

func TestActivityStreamEmitsConsoleConnectionEvent(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(ctx)
	defer liveService.Close()

	router, sessionID := activityTestRouter(t, liveService)

	bootstrap := apiRequest(router, "/api/console/v1/bootstrap", `{}`, browserauth.SessionCookie(sessionID))
	var bs struct {
		TabID     string `json:"tabId"`
		CSRFToken string `json:"csrfToken"`
	}
	_ = json.Unmarshal(bootstrap.Body.Bytes(), &bs)

	streamCtx, streamCancel := context.WithCancel(context.Background())
	defer streamCancel()
	request := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:7943/api/console/v1/activity/stream", strings.NewReader(`{}`))
	request = request.WithContext(streamCtx)
	request.Host = "127.0.0.1:7943"
	request.Header.Set("Origin", "http://127.0.0.1:7943")
	request.Header.Set("X-loomspan-Console-Tab", bs.TabID)
	request.Header.Set(csrfHeader, bs.CSRFToken)
	request.AddCookie(browserauth.SessionCookie(sessionID))
	response := httptest.NewRecorder()
	done := make(chan struct{})
	go func() {
		router.ServeHTTP(response, request)
		close(done)
	}()
	time.Sleep(100 * time.Millisecond)
	streamCancel()
	<-done
	// Read the body after the handler goroutine has exited to avoid a data
	// race on the ResponseRecorder's buffer.
	body := response.Body.String()
	if !strings.Contains(body, "event: console.connection") {
		t.Fatalf("expected console.connection event, got: %s", body)
	}
	if !strings.Contains(body, `"connected":false`) {
		t.Fatalf("expected the inactive coordinator's factual connection state, got: %s", body)
	}
}
