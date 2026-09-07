package target

import (
	"bytes"
	"context"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"strings"
	"testing"
	"time"
)

func TestRetainedBodyCancellationPreservesFailureResponse(t *testing.T) {
	for _, kind := range []applicationclient.FailureKind{applicationclient.FailureLimitExceeded, applicationclient.FailureProtocol} {
		t.Run(string(kind), func(t *testing.T) {
			failure := &applicationclient.Failure{Kind: kind, Category: applicationclient.CategoryUpstreamProtocol}
			// A bounded reader now retains cancellation below the pre-existing
			// public Failure. Cancellation must remain inspectable without
			// replacing that already selected response classification.
			err := errors.Join(failure, diagnostics.Annotate(context.Canceled, diagnostics.Facts{Cause: "body_read"}))
			client := &fakeUpstreamClient{err: err, artifactErr: err}
			scope := Scope{ID: "scope-test", Context: context.Background(), client: client, credential: testCredentialValue()}
			want := failure.ConsoleError(string(scope.ID))
			for _, operation := range []string{"get", "activity", "artifact"} {
				t.Run(operation, func(t *testing.T) {
					var domain *consolecore.Error
					switch operation {
					case "get":
						_, domain = scope.Upstream(context.Background(), "", 1024)
					case "activity":
						_, domain = scope.OpenActivity(context.Background(), "0")
					case "artifact":
						_, domain = scope.OpenArtifact(context.Background(), "trace")
					}
					if domain == nil || domain.Code != want.Code || domain.Message != want.Message || domain.Details != want.Details {
						t.Fatalf("body cancellation changed response: got %v, want %v", domain, want)
					}
				})
			}
		})
	}
}

func TestProbeRetainedBodyCancellationPreservesFailureResponse(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	client := &fakeClient{err: errors.Join(&applicationclient.Failure{Kind: applicationclient.FailureProtocol, Category: applicationclient.CategoryUpstreamProtocol}, context.Canceled)}
	owner, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil }, func() (ScopeID, error) { return "scope-test", nil }, time.Now)
	defer owner.Close()
	_, domain := owner.SelectAndConnect(ctx, "http://localhost:8080", []byte(strings.Repeat("k", 32)))
	if domain == nil || domain.Message != "The selected target is unavailable." || domain.Details.TransportCategory != string(applicationclient.CategoryUpstreamProtocol) {
		t.Fatalf("probe body cancellation changed response: %v", domain)
	}
}

func TestProbeDiagnosticFirstChangeRecovery(t *testing.T) {
	var output bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.target")
	client := &fakeClient{err: diagnostics.Annotate(errors.New("credential-canary"), diagnostics.Facts{Cause: "connection"}), instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	owner, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil }, func() (ScopeID, error) { return "scope-test", nil }, time.Now)
	defer owner.Close()
	_, _ = owner.SelectAndConnect(ctx, "http://localhost:8080", []byte(strings.Repeat("k", 32)))
	for i := 0; i < 100; i++ {
		_, _ = owner.Recheck(ctx)
	}
	client.err = diagnostics.Annotate(errors.New("credential-canary"), diagnostics.Facts{Cause: "dns"})
	_, _ = owner.Recheck(ctx)
	client.err = nil
	_, _ = owner.Recheck(ctx)
	_, _ = owner.Recheck(ctx)
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 2 || strings.Count(text, `"msg":"operation recovered"`) != 1 {
		t.Fatal(text)
	}
	if !strings.Contains(text, `"suppressed_count":100`) || !strings.Contains(text, `"request_id":`) || !strings.Contains(text, `"scope_id":"scope-test"`) || strings.Contains(text, "credential-canary") {
		t.Fatal(text)
	}
	if strings.Count(text, `"state":"connected"`) != 1 {
		t.Fatal(text)
	}
}

func TestProbeCallerDeadlinePreservesOriginalFailureMapping(t *testing.T) {
	var logs bytes.Buffer
	ctx := diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs))
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	owner, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil }, func() (ScopeID, error) { return "scope-test", nil }, time.Now)
	defer owner.Close()
	_, _ = owner.SelectAndConnect(ctx, "http://localhost:8080", []byte(strings.Repeat("k", 32)))
	logs.Reset()
	client.err = context.DeadlineExceeded
	expired, cancel := context.WithDeadline(ctx, time.Now().Add(-time.Second))
	defer cancel()
	_, domain := owner.Recheck(expired)
	if domain == nil || domain.Message != "The selected target is unavailable." {
		t.Fatalf("changed outward error: %v", domain)
	}
	if strings.Contains(logs.String(), `"level":"ERROR"`) || strings.Contains(logs.String(), `"level":"WARN"`) {
		t.Fatal(logs.String())
	}
}

func TestManualProbeFailureLinksNewRequestToRetrySeries(t *testing.T) {
	var logs bytes.Buffer
	base := diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs))
	client := &fakeClient{err: errors.New("secret-canary")}
	owner, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil }, func() (ScopeID, error) { return "scope-test", nil }, time.Now)
	defer owner.Close()
	_, _ = owner.SelectAndConnect(diagnostics.WithRequest(base, "browser.targetConnect"), "http://localhost:8080", []byte(strings.Repeat("k", 32)))
	logs.Reset()
	_, _ = owner.Recheck(diagnostics.WithRequest(base, "browser.targetRecheck"))
	if strings.Count(logs.String(), `"msg":"operation.link"`) != 1 || strings.Contains(logs.String(), `"msg":"operation failed"`) || !strings.Contains(logs.String(), `"shared_operation_id":`) {
		t.Fatal(logs.String())
	}
}

func TestBackgroundProbeRotationFailureHasPrimaryOwner(t *testing.T) {
	var logs bytes.Buffer
	ctx := diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs))
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	failFactory := false
	owner, _ := New(func(applicationclient.Address) (ProbeClient, error) {
		if failFactory {
			return nil, errors.New("secret-factory-canary")
		}
		return client, nil
	}, func() (ScopeID, error) { return "scope-test", nil }, time.Now)
	defer owner.Close()
	_, _ = owner.SelectAndConnect(ctx, "http://localhost:8080", []byte(strings.Repeat("k", 32)))
	logs.Reset()
	failFactory = true
	client.instance = fixedInstance("22222222-2222-4222-8222-222222222222")
	_, domain := owner.probe(ctx, false, "scope-test")
	diagnostics.Report(ctx, domain)
	if domain == nil || strings.Count(logs.String(), `"msg":"operation failed"`) != 1 || !strings.Contains(logs.String(), `"operation":"target.probe"`) || strings.Contains(logs.String(), "canary") {
		t.Fatal(logs.String())
	}
}
