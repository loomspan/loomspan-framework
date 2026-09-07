package console

import (
	"bytes"
	"context"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"strings"
	"testing"
)

func TestConsoleBackgroundFatalDiagnostic(t *testing.T) {
	var b bytes.Buffer
	ctx := diagnostics.Operation(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&b)), "console.run")
	var returned error
	monitorFailure(ctx, func(err error) { returned = err }, "workspace")(errors.New("secret-workspace-path"))
	diagnostics.Report(ctx, returned)
	if strings.Count(b.String(), "operation failed") != 1 || !strings.Contains(b.String(), `"stage":"monitor"`) || !strings.Contains(b.String(), `"cause":"workspace"`) || strings.Contains(b.String(), "secret") {
		t.Fatalf("incorrect monitor diagnostic %s", b.String())
	}
}
