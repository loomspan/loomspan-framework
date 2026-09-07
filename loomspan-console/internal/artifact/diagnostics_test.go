package artifact

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"io"
	"runtime"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"
)

func TestRejectedUpstreamArtifactDiagnosticRetainsEndpoint(t *testing.T) {
	var output diagnosticBuffer
	processor := newFakeProcessor()
	processor.err = consolecore.NewError(consolecore.CodeInvalidArtifact, "The trace artifact could not be validated.", "trace-canary", consolecore.Details{}, diagnostics.Annotate(errors.New("content-canary"), diagnostics.Facts{Cause: "line_limit", Stage: "parse", LimitName: "maxPhysicalLineBytes", LimitValue: 4 << 20}))
	service := newTestServiceWithProcessor(t, Config{MaxBytes: 1024, IdleTTL: time.Hour}, newFakeLoader(testTraceMetadata("trace-canary", 4)), newFakeOpener([]byte("data"), 4), &manualTimerFactory{}, newManualClock(time.Now()), nil, processor)
	scope, cancel := testScope("scope-test")
	defer cancel()
	service.ActivateActivity(scope)
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.artifactAcquire")
	_, domain := service.Acquire(ctx, scope, "trace-canary")
	if domain == nil || domain.Code != consolecore.CodeInvalidArtifact || domain.Message != "The trace artifact could not be validated." {
		t.Fatalf("changed outward error: %v", domain)
	}
	diagnostics.Report(ctx, domain)
	var primary map[string]any
	for _, line := range strings.Split(strings.TrimSpace(output.String()), "\n") {
		var record map[string]any
		if err := json.Unmarshal([]byte(line), &record); err != nil {
			t.Fatal(err)
		}
		if record["msg"] == "operation failed" {
			if primary != nil {
				t.Fatal("duplicate primary")
			}
			primary = record
		}
	}
	if primary["endpoint"] != "artifact.download" || primary["scope_id"] != "scope-test" || primary["cause"] != "line_limit" || primary["limit_name"] != "maxPhysicalLineBytes" || primary["limit_value"] != float64(4<<20) || primary["request_id"] == nil || strings.Contains(output.String(), "canary") {
		t.Fatal(output.String())
	}
}

type diagnosticImportProcessor struct{ *fakeProcessor }

func TestIncompatibleUpstreamArtifactDiagnosticIsWarning(t *testing.T) {
	var output diagnosticBuffer
	processor := newFakeProcessor()
	processor.err = consolecore.NewError(consolecore.CodeIncompatibleArtifact, "The trace artifact uses a different Loomspan release.", "trace-canary", consolecore.Details{}, nil)
	service := newTestServiceWithProcessor(t, Config{MaxBytes: 1024, IdleTTL: time.Hour}, newFakeLoader(testTraceMetadata("trace-canary", 4)), newFakeOpener([]byte("data"), 4), &manualTimerFactory{}, newManualClock(time.Now()), nil, processor)
	scope, cancel := testScope("scope-test")
	defer cancel()
	service.ActivateActivity(scope)
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.artifactAcquire")
	_, domain := service.Acquire(ctx, scope, "trace-canary")
	if domain == nil || domain.Code != consolecore.CodeIncompatibleArtifact {
		t.Fatalf("changed outward response: %v", domain)
	}
	diagnostics.Report(ctx, domain)
	if strings.Count(output.String(), `"msg":"operation failed"`) != 1 || !strings.Contains(output.String(), `"level":"WARN"`) || strings.Contains(output.String(), `"level":"ERROR"`) || !strings.Contains(output.String(), `"endpoint":"artifact.download"`) {
		t.Fatal(output.String())
	}
}

func (p diagnosticImportProcessor) PreflightImport(_ context.Context, raw io.Reader) (ImportPreflight, *consolecore.Error) {
	return ImportPreflight{Header: ImportHeader{TraceID: "trace-import", SessionID: "session-import"}, Raw: raw}, nil
}

type diagnosticImportReader struct{}

func (diagnosticImportReader) Read([]byte) (int, error) { return 0, errors.New("reader-canary") }

func TestImportReadFailureDoesNotClaimUpstreamEndpoint(t *testing.T) {
	var output diagnosticBuffer
	service := newTestServiceWithProcessor(t, Config{MaxBytes: 1024, IdleTTL: time.Hour}, newFakeLoader(TraceMetadata{}), newFakeOpener(nil, 0), &manualTimerFactory{}, newManualClock(time.Now()), nil, diagnosticImportProcessor{newFakeProcessor()})
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.artifactImport")
	_, domain := service.Import(ctx, diagnosticImportReader{}, -1)
	if domain == nil || domain.Message != "The artifact stream was interrupted." {
		t.Fatalf("changed outward error: %v", domain)
	}
	diagnostics.Report(ctx, domain)
	var record map[string]any
	if err := json.Unmarshal([]byte(output.String()), &record); err != nil {
		t.Fatal(err)
	}
	if record["endpoint"] != nil || record["scope_id"] != nil || record["cause"] != "body_read" || record["request_id"] == nil || strings.Contains(output.String(), "canary") {
		t.Fatal(output.String())
	}
}

type diagnosticBuffer struct {
	mu     sync.Mutex
	buffer bytes.Buffer
}

func (b *diagnosticBuffer) Write(p []byte) (int, error) {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buffer.Write(p)
}
func (b *diagnosticBuffer) String() string {
	b.mu.Lock()
	defer b.mu.Unlock()
	return b.buffer.String()
}

func TestJoinedAcquisitionOnePrimaryWithWaiterLinks(t *testing.T) {
	var output diagnosticBuffer
	logger := diagnostics.NewLogger(&output)
	loader := newFakeLoader(testTraceMetadata("trace-canary", 1))
	loader.barrier = make(chan struct{})
	loader.release = make(chan struct{})
	loader.err = consolecore.NewError(consolecore.CodeTargetUnavailable, "credential-canary", "scope-test", consolecore.Details{}, diagnostics.Annotate(errors.New("credential-canary"), diagnostics.Facts{Cause: "body_read", Endpoint: "traces.get"}))
	service := newTestService(t, Config{MaxBytes: 1024, IdleTTL: time.Hour}, loader, newFakeOpener(nil, 0))
	scope, cancel := testScope("scope-test")
	defer cancel()
	service.ActivateActivity(scope)
	results := make(chan *consolecore.Error, 2)
	for i := 0; i < 2; i++ {
		go func() {
			ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), logger), "browser.artifact")
			_, err := service.Acquire(ctx, scope, "trace-canary")
			diagnostics.Report(ctx, err)
			results <- err
		}()
	}
	<-loader.barrier
	deadline := time.Now().Add(5 * time.Second)
	for {
		service.mu.Lock()
		count := 0
		for _, e := range service.entries {
			count = e.waiters
		}
		service.mu.Unlock()
		if count == 2 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("waiters did not join")
		}
		runtime.Gosched()
	}
	close(loader.release)
	first, second := <-results, <-results
	if first == nil || first != second {
		t.Fatal("waiters did not share terminal failure")
	}
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 1 || strings.Count(text, `"msg":"operation.link"`) != 2 || !strings.Contains(text, `"scope_id":"scope-test"`) {
		t.Fatal(text)
	}
	for _, secret := range []string{"trace-canary", "credential-canary"} {
		if strings.Contains(text, secret) {
			t.Fatal(text)
		}
	}
}

func TestArtifactStorageDiagnosticStages(t *testing.T) {
	var output diagnosticBuffer
	fs := newFaultyFS()
	fs.syncFail = syscall.ENOSPC
	service := newTestServiceWithDeps(t, Config{Unlimited: true, IdleTTL: time.Hour}, newFakeLoader(testTraceMetadata("trace-canary", 4)), newFakeOpener([]byte("data"), 4), &manualTimerFactory{}, newManualClock(time.Now()), fs)
	scope, cancel := testScope("scope-test")
	defer cancel()
	service.ActivateActivity(scope)
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.artifact")
	_, domain := service.Acquire(ctx, scope, "trace-canary")
	if domain == nil {
		t.Fatal("expected failure")
	}
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 1 || !strings.Contains(text, `"cause":"no_space"`) || !strings.Contains(text, `"stage":"sync"`) || strings.Contains(text, "trace-canary") {
		t.Fatal(text)
	}
}

func TestImportDiagnosticOmitsScopeAndExpectedInvalidity(t *testing.T) {
	var output diagnosticBuffer
	processor := newFakeProcessor()
	processor.err = consolecore.NewError(consolecore.CodeLocalStorageUnavailable, "path-canary", "trace-canary", consolecore.Details{}, errors.New("path-canary"))
	service := newImportTestService(t, Config{MaxBytes: 1024, IdleTTL: time.Hour}, processor)
	ctx := diagnostics.WithScope(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "irrelevant-target")
	_, domain := service.Import(ctx, strings.NewReader("data"), 4)
	if domain == nil {
		t.Fatal("expected failure")
	}
	diagnostics.Report(ctx, domain)
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 1 || strings.Contains(text, "scope_id") || strings.Contains(text, "canary") {
		t.Fatal(text)
	}
	processor.err = consolecore.NewError(consolecore.CodeInvalidArtifact, "invalid", "trace-canary", consolecore.Details{}, errors.New("content-canary"))
	_, domain = service.Import(ctx, strings.NewReader("data"), 4)
	diagnostics.Report(ctx, domain)
	if output.String() != text {
		t.Fatal(output.String())
	}
}
