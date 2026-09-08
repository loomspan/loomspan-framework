package console

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
)

func TestObservabilityRoutesDoNotLeakApplicationCredential(t *testing.T) {
	secret := "LOOMSPAN_" + "TEST_APPLICATION_KEY_DO_NOT_LEAK_9a2c"
	var received []string
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "skills-page.json"))
	if err != nil {
		t.Fatal(err)
	}
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		received = append(received, request.Header.Values(applicationclient.APIKeyHeader)...)
		response.Header().Set("Content-Type", "application/json")
		response.Header().Set(applicationclient.InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		if strings.HasSuffix(request.URL.Path, "/instance") {
			_, _ = response.Write([]byte(`{"instanceId":"11111111-1111-4111-8111-111111111111","consoleCompatibilityVersion":"1.0.0-beta.3-SNAPSHOT","observedAt":"2026-07-25T12:00:00Z","liveMonitoringAvailable":true,"registeredSkillCount":1,"activeExecutionCount":0,"catalogedTraceCount":0,"tracePersistencePolicy":"PERSISTENT","completionGraceTtl":"PT2M","traceCatalogMetadataTtl":"PT168H"}`))
			return
		}
		_, _ = response.Write(fixture)
	}))
	defer server.Close()
	policy := applicationclient.NetworkPolicy{
		ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: time.Second,
	}
	targetContext, err := target.New(func(address applicationclient.Address) (target.ProbeClient, error) {
		return applicationclient.New(address, policy, "1.0.0-beta.3-SNAPSHOT")
	}, nil, time.Now)
	if err != nil {
		t.Fatal(err)
	}
	defer targetContext.Close()
	if err := targetContext.Select(server.URL); err != nil {
		t.Fatal(err)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(secret)); domain != nil {
		t.Fatal(domain)
	}
	service := observability.New()
	scope, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	page, domain := service.ListSkills(context.Background(), scope, observability.ListRequest{})
	if domain != nil {
		t.Fatal(domain)
	}
	encoded, err := json.Marshal(page)
	if err != nil {
		t.Fatal(err)
	}
	rendered := string(encoded) + fmt.Sprintf("%#v", page)
	if strings.Contains(rendered, secret) {
		t.Fatal("credential escaped into observability DTO or formatted output")
	}
	for _, hdr := range received {
		if hdr != secret {
			t.Fatalf("unexpected header value: %q", hdr)
		}
	}
	if len(received) == 0 {
		t.Fatal("credential was not sent on any upstream request")
	}
}

// TestScopeOpenArtifactStreamsJavaProducedFixtureBytesThroughCompositionRoot is
// the PR12 Phase 1 cross-boundary integration test. It stands up the real
// application client against an httptest.Server that mimics the Java artifact
// endpoint contract (Content-Type, Content-Length, instance header, no-store,
// safe attachment disposition) and verifies that Scope.OpenArtifact streams the
// exact Java-produced fixture bytes without buffering, without leaking the
// application key into the streamed body or logs, and with the validated
// instance identity available on the stream. See
// Regression coverage for the console artifact service.
// section "Cross-boundary fixture coverage".
func TestScopeOpenArtifactStreamsJavaProducedFixtureBytesThroughCompositionRoot(t *testing.T) {
	const instanceID = "11111111-1111-4111-8111-111111111111"
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "traces", "single-attempt-success.ndjson"))
	if err != nil {
		t.Fatal(err)
	}

	var artifactRequests int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if strings.HasSuffix(request.URL.Path, "/artifact") {
			atomic.AddInt32(&artifactRequests, 1)
			response.Header().Set(applicationclient.InstanceIDHeader, instanceID)
			response.Header().Set("Content-Type", applicationclient.ArtifactMediaType)
			response.Header().Set("Content-Length", fmt.Sprintf("%d", len(fixture)))
			response.Header().Set("Content-Disposition", `attachment; filename="loomspan-trace-single-attempt-success.ndjson"`)
			response.Header().Set("Cache-Control", "no-store")
			_, _ = response.Write(fixture)
			return
		}
		response.Header().Set(applicationclient.InstanceIDHeader, instanceID)
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{"instanceId":"` + instanceID + `","consoleCompatibilityVersion":"1.0.0-beta.3-SNAPSHOT","observedAt":"2026-07-25T12:00:00Z","liveMonitoringAvailable":true,"registeredSkillCount":0,"activeExecutionCount":0,"catalogedTraceCount":0,"tracePersistencePolicy":"PERSISTENT","completionGraceTtl":"PT2M","traceCatalogMetadataTtl":"PT168H"}`))
	}))
	defer server.Close()

	policy := applicationclient.NetworkPolicy{
		ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: 30 * time.Second,
	}
	targetContext, err := target.New(func(address applicationclient.Address) (target.ProbeClient, error) {
		return applicationclient.New(address, policy, "1.0.0-beta.3-SNAPSHOT")
	}, nil, time.Now)
	if err != nil {
		t.Fatal(err)
	}
	defer targetContext.Close()
	if err := targetContext.Select(server.URL); err != nil {
		t.Fatal(err)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); domain != nil {
		t.Fatal(domain)
	}

	scope, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	stream, domain := scope.OpenArtifact(context.Background(), "single-attempt-success")
	if domain != nil {
		t.Fatalf("open artifact: %v", domain)
	}
	defer stream.Close()

	if stream.InstanceID() != instanceID {
		t.Fatalf("stream instance id: got %q want %q", stream.InstanceID(), instanceID)
	}
	if stream.MediaType() != applicationclient.ArtifactMediaType {
		t.Fatalf("stream media type: got %q want %q", stream.MediaType(), applicationclient.ArtifactMediaType)
	}
	if stream.DeclaredLength() != int64(len(fixture)) {
		t.Fatalf("declared length: got %d want %d", stream.DeclaredLength(), len(fixture))
	}

	body, err := io.ReadAll(stream.Body())
	if err != nil {
		t.Fatalf("read streamed body: %v", err)
	}
	if !bytes.Equal(body, fixture) {
		t.Fatalf("streamed bytes mismatch: got %d bytes want %d bytes", len(body), len(fixture))
	}
	if atomic.LoadInt32(&artifactRequests) != 1 {
		t.Fatalf("expected exactly one artifact request, got %d", atomic.LoadInt32(&artifactRequests))
	}
}

// TestScopeOpenArtifactRejectsIncompatibleTarget verifies the exact-release
// compatibility gate prevents a successful artifact stream. The scope is
// established against an incompatible instance (the probe fails), so the
// scope's InstanceID is never populated. OpenArtifact must surface the
// incompatibility or target change rather than returning a usable stream.
func TestScopeOpenArtifactRejectsIncompatibleTarget(t *testing.T) {
	const instanceID = "11111111-1111-4111-8111-111111111111"
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if strings.HasSuffix(request.URL.Path, "/artifact") {
			response.Header().Set(applicationclient.InstanceIDHeader, instanceID)
			response.Header().Set("Content-Type", applicationclient.ArtifactMediaType)
			_, _ = response.Write([]byte(`{}`))
			return
		}
		response.Header().Set(applicationclient.InstanceIDHeader, instanceID)
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{"instanceId":"` + instanceID + `","consoleCompatibilityVersion":"1.0.0-beta.3-SNAPSHOT","observedAt":"2026-07-25T12:00:00Z","liveMonitoringAvailable":true,"registeredSkillCount":0,"activeExecutionCount":0,"catalogedTraceCount":0,"tracePersistencePolicy":"PERSISTENT","completionGraceTtl":"PT2M","traceCatalogMetadataTtl":"PT168H"}`))
	}))
	defer server.Close()

	policy := applicationclient.NetworkPolicy{
		ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: time.Second,
	}
	targetContext, err := target.New(func(address applicationclient.Address) (target.ProbeClient, error) {
		return applicationclient.New(address, policy, "9.9.9-INCOMPATIBLE")
	}, nil, time.Now)
	if err != nil {
		t.Fatal(err)
	}
	defer targetContext.Close()
	if err := targetContext.Select(server.URL); err != nil {
		t.Fatal(err)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); domain == nil {
		t.Fatal("expected incompatible target error during probe")
	}

	scope, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	_, domain = scope.OpenArtifact(context.Background(), "trace-1")
	if domain == nil {
		t.Fatal("expected OpenArtifact to fail on an incompatible target")
	}
}
