package applicationclient

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestGetReturnsBodyAndInstanceID(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{"items":[]}`))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()
	body, instanceID, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32)))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if string(body) != `{"items":[]}` {
		t.Fatalf("unexpected body: %s", body)
	}
	if instanceID != "11111111-1111-4111-8111-111111111111" {
		t.Fatalf("unexpected instanceID: %s", instanceID)
	}
}

func TestGetSendsRequiredHeaders(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Header.Get("Accept") != "application/json" ||
			request.Header.Get("Accept-Encoding") != "identity" ||
			request.Header.Get("Cache-Control") != "no-store" {
			t.Errorf("unexpected request headers: %#v", request.Header)
		}
		if values := request.Header.Values(APIKeyHeader); len(values) != 1 ||
			values[0] != strings.Repeat("k", 32) {
			t.Errorf("unexpected application key headers: %#v", values)
		}
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write([]byte(`{"items":[]}`))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()
	if _, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32))); err != nil {
		t.Fatal(err)
	}
}

func TestGetRejectsRedirectsAndEncodedResponses(t *testing.T) {
	tests := []struct {
		name   string
		status int
		header http.Header
	}{
		{
			name:   "redirect",
			status: http.StatusFound,
			header: http.Header{"Location": []string{"/another-resource"}},
		},
		{
			name:   "encoded",
			status: http.StatusOK,
			header: http.Header{"Content-Encoding": []string{"gzip"}},
		},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
				for name, values := range test.header {
					for _, value := range values {
						response.Header().Add(name, value)
					}
				}
				response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
				response.WriteHeader(test.status)
				_, _ = response.Write([]byte(`{}`))
			}))
			defer server.Close()
			address, _ := NormalizeAddress(server.URL)
			client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
			defer client.Close()
			_, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32)))
			failure, ok := err.(*Failure)
			if !ok || failure.Kind != FailureUnavailable && failure.Kind != FailureProtocol {
				t.Fatalf("expected typed rejection, got %v", err)
			}
		})
	}
}

func TestGetReturnsFailureLimitExceededWhenBodyExceedsMaxBytes(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		_, _ = response.Write([]byte(strings.Repeat("x", 100)))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()
	_, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 50, testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureLimitExceeded {
		t.Fatalf("expected FailureLimitExceeded, got %v", err)
	}
}

func TestGetMapsProblemCodesToFailureKinds(t *testing.T) {
	tests := []struct {
		code string
		kind FailureKind
	}{
		{"INVALID_REQUEST", FailureInvalidArgument},
		{"INVALID_CURSOR", FailureInvalidCursor},
		{"STALE_CURSOR", FailureStaleCursor},
		{"NOT_FOUND", FailureNotFound},
		{"LIMIT_EXCEEDED", FailureLimitExceeded},
		{"LIVE_MONITORING_UNAVAILABLE", FailureLiveMonitoringUnavailable},
	}
	for _, test := range tests {
		server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
			response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
			response.Header().Set("Content-Type", "application/json")
			response.WriteHeader(http.StatusBadRequest)
			_, _ = response.Write([]byte(`{"status":400,"code":"` + test.code + `","message":"test"}`))
		}))
		address, _ := NormalizeAddress(server.URL)
		client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
		_, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32)))
		client.Close()
		server.Close()
		failure, ok := err.(*Failure)
		if !ok {
			t.Fatalf("%s did not produce a typed failure: %v", test.code, err)
		}
		if failure.Kind != test.kind {
			t.Fatalf("expected %s, got %s", test.kind, failure.Kind)
		}
	}
}

func TestGetReturnsProtocolFailureForOversizedErrorBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set(InstanceIDHeader, "11111111-1111-4111-8111-111111111111")
		response.Header().Set("Content-Type", "application/json")
		response.WriteHeader(http.StatusBadRequest)
		_, _ = response.Write([]byte(strings.Repeat("x", int(problemMaxBytes)+1)))
	}))
	defer server.Close()
	address, _ := NormalizeAddress(server.URL)
	client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
	defer client.Close()
	_, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32)))
	failure, ok := err.(*Failure)
	if !ok || failure.Kind != FailureProtocol {
		t.Fatalf("expected FailureProtocol, got %v", err)
	}
}

func TestGetRejectsMissingOrDuplicateInstanceID(t *testing.T) {
	tests := []struct {
		name    string
		headers []string
	}{
		{name: "missing"},
		{name: "duplicate", headers: []string{
			"11111111-1111-4111-8111-111111111111",
			"22222222-2222-4222-8222-222222222222",
		}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
				for _, value := range test.headers {
					response.Header().Add(InstanceIDHeader, value)
				}
				_, _ = response.Write([]byte(`{}`))
			}))
			defer server.Close()
			address, _ := NormalizeAddress(server.URL)
			client, _ := New(address, testPolicy(), "0.1.0-SNAPSHOT")
			defer client.Close()
			_, _, err := client.Get(context.Background(), address.SkillsEndpoint(), 1024, testCredential(strings.Repeat("k", 32)))
			failure, ok := err.(*Failure)
			if !ok || failure.Kind != FailureProtocol {
				t.Fatalf("expected FailureProtocol, got %v", err)
			}
		})
	}
}

func TestAddressEndpointsEncodePathVariables(t *testing.T) {
	address, _ := NormalizeAddress("http://127.0.0.1:8080")
	if address.SkillsEndpoint() != "http://127.0.0.1:8080/_loomspan/observability/v1/skills" {
		t.Fatalf("unexpected skills endpoint: %s", address.SkillsEndpoint())
	}
	if got := address.SkillEndpoint("My Skill"); !strings.Contains(got, "My%20Skill") {
		t.Fatalf("skill endpoint did not encode space: %s", got)
	}
	if got := address.ActiveExecutionsEndpoint(); !strings.HasSuffix(got, "/active-executions") {
		t.Fatalf("unexpected active executions endpoint: %s", got)
	}
	if got := address.ActiveExecutionEndpoint("session-1"); !strings.HasSuffix(got, "/active-executions/session-1") {
		t.Fatalf("unexpected active execution endpoint: %s", got)
	}
	if got := address.ActivityEndpoint("11111111-1111-4111-8111-111111111111", "0"); !strings.Contains(got, "/activity?") ||
		!strings.Contains(got, "instanceId=11111111-1111-4111-8111-111111111111") ||
		!strings.Contains(got, "afterCursor=0") {
		t.Fatalf("unexpected activity endpoint: %s", got)
	}
	if got := address.TracesEndpoint(); !strings.HasSuffix(got, "/traces") {
		t.Fatalf("unexpected traces endpoint: %s", got)
	}
	if got := address.TraceEndpoint("trace-1"); !strings.HasSuffix(got, "/traces/trace-1") {
		t.Fatalf("unexpected trace endpoint: %s", got)
	}
	if got := address.ArtifactEndpoint("trace-1"); !strings.HasSuffix(got, "/traces/trace-1/artifact") {
		t.Fatalf("unexpected artifact endpoint: %s", got)
	}
	if got := address.ArtifactEndpoint("a b"); !strings.Contains(got, "a%20b") {
		t.Fatalf("artifact endpoint did not encode space: %s", got)
	}
}
