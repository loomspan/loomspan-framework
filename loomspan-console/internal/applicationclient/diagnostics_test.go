package applicationclient

import (
	"context"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"io"
	"net/http"
	"strings"
	"testing"
)

type failingDiagnosticReader struct{ err error }

func (r failingDiagnosticReader) Read(p []byte) (int, error) { copy(p, "ok"); return 2, r.err }

func TestReadBoundedPreservesReaderFailure(t *testing.T) {
	sentinel := errors.New("secret-reader-canary")
	_, err := readBounded(failingDiagnosticReader{sentinel}, 100)
	if !errors.Is(err, sentinel) {
		t.Fatal("bounded reader discarded the original reader failure")
	}
}

type diagnosticTransport func(*http.Request) (*http.Response, error)

func (f diagnosticTransport) RoundTrip(r *http.Request) (*http.Response, error) { return f(r) }

type diagnosticCredential struct{}

func (diagnosticCredential) Apply(r *http.Request) error {
	r.Header.Set(APIKeyHeader, strings.Repeat("a", 32))
	return nil
}

func TestBodyReadFailurePreservesPublicMapping(t *testing.T) {
	address, _ := NormalizeAddress("http://localhost:9876")
	sentinel := errors.New("secret-reader-canary")
	cases := []struct {
		name   string
		reader io.Reader
		cause  string
	}{
		{"reader", failingDiagnosticReader{sentinel}, "body_read"},
		{"overflow", strings.NewReader(strings.Repeat("x", 101)), "body_limit"},
		{"canceled reader", failingDiagnosticReader{context.Canceled}, "canceled"},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			client := &Client{address: address, requestTimeout: 1e9, http: &http.Client{Transport: diagnosticTransport(func(*http.Request) (*http.Response, error) {
				return &http.Response{StatusCode: 200, Header: http.Header{http.CanonicalHeaderKey(InstanceIDHeader): []string{"12345678-1234-1234-1234-123456789abc"}}, Body: io.NopCloser(test.reader)}, nil
			})}}
			_, _, err := client.Get(context.Background(), address.SkillEndpoint("secret-skill"), 100, diagnosticCredential{})
			var failure *Failure
			if !errors.As(err, &failure) || failure.Kind != FailureLimitExceeded {
				t.Fatal("changed public classification")
			}
			public := failure.ConsoleError("scope")
			if public.Message != "The observability response exceeds the configured limit." || public.Details.LimitName != "" {
				t.Fatal("changed outward mapping")
			}
			facts := diagnostics.Extract(err)
			if facts.Cause != test.cause || facts.LimitValue != 100 || facts.LimitName != "maxBytes" || facts.Endpoint != "skills.get" {
				t.Fatalf("incorrect diagnostic facts: %+v", facts)
			}
			if test.name == "reader" && !errors.Is(err, sentinel) {
				t.Fatal("lost reader cause")
			}
			if test.name == "canceled reader" && (!errors.Is(err, context.Canceled) || !facts.Expected) {
				t.Fatal("lost body cancellation cause")
			}
		})
	}
	content, err := readBounded(strings.NewReader(strings.Repeat("x", 100)), 100)
	if err != nil || len(content) != 100 {
		t.Fatal("exact limit changed")
	}
}

func TestEndpointFamiliesNeverIncludeResourceIdentifiers(t *testing.T) {
	a, _ := NormalizeAddress("http://localhost:9876/context")
	for endpoint, want := range map[string]string{a.InstanceEndpoint(): "instance", a.SkillsEndpoint() + "?cursor=secret": "skills.list", a.SkillEndpoint("secret"): "skills.get", a.ActiveExecutionEndpoint("secret"): "executions.get", a.TraceEndpoint("secret"): "traces.get", a.ArtifactEndpoint("secret"): "artifact.download"} {
		if got := a.endpointFamily(endpoint); got != want {
			t.Fatalf("family %s wanted %s", got, want)
		}
	}
}
