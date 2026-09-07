package live

import (
	"bytes"
	"context"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"strings"
	"testing"
)

func TestBaselineRefreshDiagnosticContext(t *testing.T) {
	var output bytes.Buffer
	ctx := diagnostics.WithScope(diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "browser.target"), "scope-test")
	service := NewService(ctx)
	scope := target.Scope{ID: "scope-test", Context: ctx}
	service.scope = &scope
	service.diagnostic = &workerDiagnostics{stream: ctx, baseline: diagnostics.Detach(ctx, ctx, "live.baseline")}
	failed := true
	service.SetBaselineLoader(func(context.Context, target.Scope) (Baseline, *consolecore.Error) {
		if failed {
			return Baseline{}, consolecore.NewError(consolecore.CodeTargetUnavailable, "secret-canary", "scope-test", consolecore.Details{}, diagnostics.Annotate(errors.New("secret-canary"), diagnostics.Facts{Cause: "connection", Endpoint: "executions.list"}))
		}
		return Baseline{ResumeCursor: "0"}, nil
	})
	for i := 0; i < 101; i++ {
		_, _ = service.refreshBaseline(ctx, scope)
	}
	failed = false
	_, _ = service.refreshBaseline(ctx, scope)
	_, _ = service.refreshBaseline(ctx, scope)
	service.publishConnectionFor(scope.ID, true, "")
	service.publishConnectionFor(scope.ID, true, "")
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 1 || strings.Count(text, `"msg":"operation recovered"`) != 1 || strings.Count(text, `"state":"connected"`) != 1 {
		t.Fatal(text)
	}
	if strings.Contains(text, "secret-canary") || !strings.Contains(text, `"operation":"live.baseline"`) || !strings.Contains(text, `"suppressed_count":100`) {
		t.Fatal(text)
	}
}

func TestLiveReadFailureDiagnosticAcrossReconnect(t *testing.T) {
	var output bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&output)), "live.stream")
	owner, server := setupTargetWithOwner(t, handshakeFrame()+"event: activity\nid: 7\ndata: {secret-canary}\n\n", nil)
	defer server.Close()
	defer owner.Close()
	scope, domain := owner.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	service := NewService(ctx)
	service.scope = &scope
	var failures diagnostics.Repeat
	for i := 0; i < 3; i++ {
		stream, domain := scope.OpenActivity(ctx, "0")
		if domain != nil {
			t.Fatal(domain)
		}
		service.consumeWithSeries(ctx, stream, scope, &failures)
		_ = stream.Close()
	}
	text := output.String()
	if strings.Count(text, `"msg":"operation failed"`) != 1 || strings.Contains(text, "secret-canary") || !strings.Contains(text, `"endpoint":"activity.stream"`) || !strings.Contains(text, `"scope_id":`) {
		t.Fatal(text)
	}
}
