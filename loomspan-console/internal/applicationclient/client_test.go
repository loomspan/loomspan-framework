package applicationclient

import (
	"context"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

type testCredential []byte

func (credential testCredential) Apply(request *http.Request) error {
	if err := ValidateCredential(credential); err != nil {
		return err
	}
	request.Header.Set(APIKeyHeader, string(credential))
	return nil
}

func testPolicy() NetworkPolicy {
	return NetworkPolicy{
		ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: time.Second,
	}
}

func TestInstanceRequestSendsExactlyOneBoundedCredentialHeader(t *testing.T) {
	key := testCredential(strings.Repeat("k", 32))
	var received http.Header
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		received = request.Header.Clone()
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write(validInstanceBody("0.1.0-SNAPSHOT"))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, err := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer client.Close()
	if _, err := client.Probe(context.Background(), key); err != nil {
		t.Fatal(err)
	}
	if values := received.Values(APIKeyHeader); len(values) != 1 || values[0] != string(key) {
		t.Fatal("selected request did not receive exactly one application key")
	}
	if received.Get("Accept-Encoding") != "identity" {
		t.Fatalf("unexpected encoding request: %q", received.Get("Accept-Encoding"))
	}
	for _, invalid := range [][]byte{
		[]byte(strings.Repeat("x", 31)), []byte(strings.Repeat("x", 513)), []byte(strings.Repeat("x", 31) + " "),
	} {
		if _, err := client.Probe(context.Background(), testCredential(invalid)); err == nil {
			t.Fatalf("accepted invalid key length=%d", len(invalid))
		}
	}
}

func TestClientConsumesCommittedInstanceFixtureOnlyAfterExactCompatibility(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "instance-status.json"))
	if err != nil {
		t.Fatal(err)
	}
	instance := serveBody(t, fixture, "0.1.0-SNAPSHOT")
	if instance.InstanceID != "11111111-1111-4111-8111-111111111111" || !instance.LiveMonitoringAvailable {
		t.Fatalf("unexpected instance: %#v", instance)
	}
	var value map[string]any
	if err := json.Unmarshal(fixture, &value); err != nil {
		t.Fatal(err)
	}
	value["consoleCompatibilityVersion"] = "0.1.0"
	value["instanceId"] = 4
	mismatch, _ := json.Marshal(value)
	failure := serveFailure(t, mismatch, "0.1.0-SNAPSHOT")
	if failure.Kind != FailureIncompatible || failure.Observed != "0.1.0" {
		t.Fatalf("unexpected mismatch: %#v", failure)
	}
}

func TestTransportUsesDirectSelectedAuthorityAndNeverFollowsRedirect(t *testing.T) {
	var redirected bool
	receiver := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { redirected = true }))
	defer receiver.Close()
	selected := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		http.Redirect(response, &http.Request{}, receiver.URL, http.StatusTemporaryRedirect)
	}))
	defer selected.Close()
	t.Setenv("HTTP_PROXY", receiver.URL)
	address, _ := NormalizeAddress(selected.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()
	_, err := client.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Category != CategoryRedirect || redirected {
		t.Fatalf("redirect behavior failure=%v redirected=%v", err, redirected)
	}
}

func TestProbeReturnsCallerCancellationWithoutTransportRetryClassification(t *testing.T) {
	started := make(chan struct{})
	server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, request *http.Request) {
		close(started)
		<-request.Context().Done()
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()

	parent, cancel := context.WithCancel(context.Background())
	result := make(chan error, 1)
	go func() {
		_, err := client.Probe(parent, testCredential(strings.Repeat("k", 32)))
		result <- err
	}()
	<-started
	cancel()
	if err := <-result; !errors.Is(err, context.Canceled) {
		t.Fatalf("caller cancellation was classified as a transport failure: %v", err)
	}
}

func TestClientMapsJavaProblemsAndGenericUpstreamFailuresPrecisely(t *testing.T) {
	root := filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest")
	entries, err := filepath.Glob(filepath.Join(root, "problem-*.json"))
	if err != nil || len(entries) == 0 {
		t.Fatalf("problem fixture inventory unavailable: %v", err)
	}
	for _, path := range entries {
		body, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		var problem struct {
			Status int    `json:"status"`
			Code   string `json:"code"`
		}
		if err := json.Unmarshal(body, &problem); err != nil {
			t.Fatal(err)
		}
		server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
			response.Header().Set("Content-Type", "application/json")
			response.WriteHeader(problem.Status)
			_, _ = response.Write(body)
		}))
		address, _ := NormalizeAddress(server.URL)
		client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
		_, probeErr := client.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
		client.Close()
		server.Close()
		failure, ok := probeErr.(*Failure)
		if !ok {
			t.Fatalf("%s did not produce a typed failure: %v", filepath.Base(path), probeErr)
		}
		if problem.Code == "LOOMSPAN_API_KEY_REJECTED" && failure.Kind != FailureAuthentication {
			t.Fatalf("recognized key rejection mapped to %s", failure.Kind)
		}
	}

	for _, status := range []int{http.StatusUnauthorized, http.StatusForbidden} {
		server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
			response.WriteHeader(status)
			_, _ = response.Write([]byte(`{"status":` + fmt.Sprint(status) + `,"code":"UPSTREAM","message":"blocked"}`))
		}))
		address, _ := NormalizeAddress(server.URL)
		client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
		_, probeErr := client.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
		client.Close()
		server.Close()
		if failure := probeErr.(*Failure); failure.Kind != FailureAccess {
			t.Fatalf("generic %d mapped to %s", status, failure.Kind)
		}
	}
}

func TestTransportUsesOptionalCustomCAWithoutWeakeningVerification(t *testing.T) {
	server := httptest.NewTLSServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write(validInstanceBody("0.1.0-SNAPSHOT"))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	untrusted, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	_, err := untrusted.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
	untrusted.Close()
	failure, ok := err.(*Failure)
	if !ok || failure.Category != CategoryTLSUntrustedIssuer {
		t.Fatalf("untrusted certificate classification=%v", err)
	}
	policy := testPolicy()
	policy.CABundlePEM = pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: server.Certificate().Raw})
	trusted, err := New(address, policy, "0.1.0-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer trusted.Close()
	if _, err := trusted.Probe(context.Background(), testCredential(strings.Repeat("k", 32))); err != nil {
		t.Fatal(err)
	}
	wrongAddress, _ := NormalizeAddress(strings.Replace(server.URL, "127.0.0.1", "localhost", 1))
	wrongHost, _ := New(wrongAddress, policy, "0.1.0-SNAPSHOT")
	defer wrongHost.Close()
	_, err = wrongHost.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
	if failure, ok := err.(*Failure); !ok || failure.Category != CategoryTLSHostname {
		t.Fatalf("hostname verification was weakened: %v", err)
	}
}

func TestTLSValidityFailuresRetainExpiredAndNotYetValidCategories(t *testing.T) {
	now := time.Now()
	notYetValid := classifyTransport(x509.CertificateInvalidError{
		Cert: &x509.Certificate{
			NotBefore: now.Add(time.Hour),
			NotAfter:  now.Add(2 * time.Hour),
		},
		Reason: x509.Expired,
	})
	if notYetValid.Category != CategoryTLSNotYetValid || notYetValid.Retryable {
		t.Fatalf("not-yet-valid certificate classification=%#v", notYetValid)
	}
	expired := classifyTransport(x509.CertificateInvalidError{
		Cert:   &x509.Certificate{NotBefore: now.Add(-2 * time.Hour), NotAfter: now.Add(-time.Hour)},
		Reason: x509.Expired,
	})
	if expired.Category != CategoryTLSExpired || expired.Retryable {
		t.Fatalf("expired certificate classification=%#v", expired)
	}
}

func serveBody(t *testing.T, body []byte, expected string) Instance {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write(body)
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), expected)
	defer client.Close()
	instance, err := client.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatal(err)
	}
	return instance
}

func serveFailure(t *testing.T, body []byte, expected string) *Failure {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write(body)
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), expected)
	defer client.Close()
	_, err := client.Probe(context.Background(), testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok {
		t.Fatalf("expected failure, got %v", err)
	}
	return failure
}

func validInstanceBody(version string) []byte {
	return []byte(`{"instanceId":"11111111-1111-4111-8111-111111111111","consoleCompatibilityVersion":"` + version + `","observedAt":"2026-07-25T12:00:00Z","liveMonitoringAvailable":true}`)
}
