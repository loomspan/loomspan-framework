package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/console"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"io"
	"strings"
	"testing"
)

func TestStartupFailureExcludesArgumentsAndNestedErrors(t *testing.T) {
	for _, args := range [][]string{{"secret-extra-argument"}, nil} {
		var b bytes.Buffer
		ctx := diagnostics.Operation(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&b)), "console.startup")
		err := run(ctx, args, io.Discard, runtimeDependencies{version: "development", verify: func() error { return errors.New("secret-asset-path") }, serve: func(context.Context, console.Options) error { return nil }})
		if err == nil {
			t.Fatal("expected startup failure")
		}
		diagnostics.Report(ctx, err)
		if strings.Contains(b.String(), "secret") {
			t.Fatal("startup disclosure")
		}
		var r map[string]any
		if json.Unmarshal(b.Bytes(), &r) != nil || r["level"] != "ERROR" || r["operation"] != "console.startup" {
			t.Fatal("invalid startup diagnostic")
		}
	}
}
