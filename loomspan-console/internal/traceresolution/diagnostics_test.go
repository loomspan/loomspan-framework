package traceresolution

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"strings"
	"testing"
)

func TestResolutionProbeRetainsTargetScopeBeforeReferenceExists(t *testing.T) {
	var logs bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)), "mcp.get_trace")
	failure := consolecore.NewError(consolecore.CodeTargetUnavailable, "secret-canary", "scope-test", consolecore.Details{}, diagnostics.Annotate(errors.New("secret-canary"), diagnostics.Facts{Cause: "connection", Endpoint: "traces.get"}))
	service := New(&fakeArtifacts{lookups: map[evidence.Source]artifact.LookupResult{evidence.SourceImported: {LocalAvailable: true}}}, &fakeCatalog{err: failure}, &fakeTarget{scope: target.Scope{ID: "scope-test"}})
	_, domain := service.Resolve(ctx, "secret-trace-canary")
	diagnostics.Report(ctx, domain)
	var record map[string]any
	if domain != failure || json.Unmarshal(logs.Bytes(), &record) != nil || record["scope_id"] != "scope-test" || record["endpoint"] != "traces.get" || record["request_id"] == nil || strings.Contains(logs.String(), "canary") {
		t.Fatal(logs.String())
	}
}
