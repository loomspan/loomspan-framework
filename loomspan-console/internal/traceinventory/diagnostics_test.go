package traceinventory

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"strings"
	"testing"
	"time"
)

func TestIncompleteInventoryFailureHasOwner(t *testing.T) {
	var b bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&b)), "mcp.list_traces")
	failure := consolecore.NewError(consolecore.CodeTargetUnavailable, "private response", "scope", consolecore.Details{}, diagnostics.Annotate(errors.New("secret-upstream-body"), diagnostics.Facts{Cause: "response_decode", Endpoint: "traces.list"}))
	service := New(&fakeArtifacts{}, &fakeCatalog{listError: failure}, &fakeTarget{scope: target.Scope{ID: "scope"}}, time.Now)
	result, domain := service.List(ctx, Query{})
	if domain != nil || result.Complete || len(result.Limitations) != 1 {
		t.Fatal("changed incomplete response")
	}
	var r map[string]any
	if json.Unmarshal(b.Bytes(), &r) != nil || r["operation"] != "traceinventory.list" || r["scope_id"] != "scope" || r["cause"] != "response_decode" || r["request_id"] == nil {
		t.Fatal("missing incomplete inventory diagnostic")
	}
	if strings.Contains(b.String(), "secret") {
		t.Fatal("content disclosure")
	}
}
