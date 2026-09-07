package mcpadapter

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/profile"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
)

func TestMCPFailureDiagnosticsPreserveContract(t *testing.T) {
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return nil, errors.New("secret-upstream-canary") })
	options.Status = func() consolecore.StatusSnapshot { return consolecore.StatusSnapshot{} }
	server := NewServer(options)
	var logs bytes.Buffer
	for _, name := range []string{RuntimeToolName, ListSkillsToolName} {
		request := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"`+name+`","arguments":{}}}`))
		request.Host = "127.0.0.1:7345"
		request.Header.Set("Authorization", "Bearer mcp-secret")
		request.Header.Set("Content-Type", "application/json")
		request.Header.Set("Accept", "application/json, text/event-stream")
		request.Header.Set("MCP-Protocol-Version", "2025-11-25")
		request = request.WithContext(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)))
		response := httptest.NewRecorder()
		server.Handler().ServeHTTP(response, request)
		if response.Code != 200 {
			t.Fatalf("HTTP contract: %d %s", response.Code, response.Body.String())
		}
		if !strings.Contains(response.Body.String(), `"isError":true`) {
			t.Fatalf("expected tool error: %s", response.Body.String())
		}
	}
	lines := bytes.Split(bytes.TrimSpace(logs.Bytes()), []byte("\n"))
	if len(lines) != 2 {
		t.Fatalf("want one failure per tool: %s", logs.String())
	}
	var lastRequest, lastOperation string
	for i, line := range lines {
		var record map[string]any
		if err := json.Unmarshal(line, &record); err != nil {
			t.Fatal(err)
		}
		req, _ := record["request_id"].(string)
		op, _ := record["operation_id"].(string)
		if req == "" || op == "" || req == lastRequest || op == lastOperation || record["parent_operation_id"] == nil {
			t.Fatalf("bad correlation: %v", record)
		}
		expected := []string{"mcp." + RuntimeToolName, "mcp." + ListSkillsToolName}[i]
		if record["operation"] != expected {
			t.Fatalf("operation=%v", record)
		}
		lastRequest, lastOperation = req, op
	}
	if strings.Contains(logs.String(), "canary") || strings.Contains(logs.String(), "mcp-secret") {
		t.Fatal("secret leaked")
	}
}

func TestMCPAuthenticationDiagnosticTransitions(t *testing.T) {
	owned, err := profile.Open(filepath.Join(t.TempDir(), "profile", "config.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	store, err := mcpcredential.Open(owned.Directory, bytes.NewReader(make([]byte, 96)))
	if err != nil {
		t.Fatal(err)
	}
	lifecycle := NewLifecycle(store, NewTracker(), nil)
	var logs bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)), "browser.mcpEnable")
	key, err := lifecycle.Enable(ctx)
	if err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 100; i++ {
		lifecycle.Status()
	}
	if err := lifecycle.Disable(ctx); err != nil {
		t.Fatal(err)
	}
	lines := bytes.Split(bytes.TrimSpace(logs.Bytes()), []byte("\n"))
	if len(lines) != 2 {
		t.Fatalf("want two committed transitions: %s", logs.String())
	}
	for i, line := range lines {
		var record map[string]any
		if err := json.Unmarshal(line, &record); err != nil {
			t.Fatal(err)
		}
		want := []string{"enabled", "disabled"}[i]
		if record["state"] != want || record["operation"] != "mcp.authentication" || record["level"] != "INFO" {
			t.Fatalf("transition: %v", record)
		}
	}
	if strings.Contains(logs.String(), key) || strings.Contains(logs.String(), owned.Directory) {
		t.Fatal("sensitive lifecycle values leaked")
	}
}

func TestMCPSecurityRejectionDiagnosticsAreBounded(t *testing.T) {
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return nil, nil })
	server := NewServer(options)
	var logs bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logs, &slog.HandlerOptions{Level: slog.LevelDebug}))
	for i := 0; i < 1000; i++ {
		request := httptest.NewRequest(http.MethodPost, "/", nil)
		request.Host = fmt.Sprintf("secret-attacker-%d.invalid", i)
		request = request.WithContext(diagnostics.WithLogger(request.Context(), logger))
		response := httptest.NewRecorder()
		server.Handler().ServeHTTP(response, request)
		if response.Code != 400 {
			t.Fatalf("rejection contract: %d", response.Code)
		}
	}
	if bytes.Count(logs.Bytes(), []byte("\n")) > 1 {
		t.Fatalf("unbounded rejection logs: %s", logs.String())
	}
	if strings.Contains(logs.String(), "secret-attacker") || strings.Contains(logs.String(), `"level":"ERROR"`) || strings.Contains(logs.String(), `"level":"WARN"`) {
		t.Fatalf("unsafe rejection: %s", logs.String())
	}
}

func TestMCPImportedTraceDiagnosticOmitsFencingScope(t *testing.T) {
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return nil, nil })
	options.TraceResolver = &fakeTraceArtifacts{ref: evidence.ForImported(), scope: target.Scope{ID: "secret-fencing-scope-canary"}}
	options.TraceAnalysis = nil
	var logs bytes.Buffer
	ctx := diagnostics.WithRequest(diagnostics.WithLogger(context.Background(), diagnostics.NewLogger(&logs)), "mcp."+GetTraceToolName)
	result, envelope, err := handleGetTrace(ctx, options, getTraceInput{TraceID: "secret-trace-canary"})
	if err != nil || result == nil || !result.IsError || envelope.Error == nil {
		t.Fatalf("result=%v err=%v", result, err)
	}
	var record map[string]any
	if err := json.Unmarshal(logs.Bytes(), &record); err != nil {
		t.Fatalf("one primary: %s %v", logs.String(), err)
	}
	if record["scope_id"] != nil || strings.Contains(logs.String(), "canary") {
		t.Fatalf("fencing scope attributed to import: %s", logs.String())
	}
}
