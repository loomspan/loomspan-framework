package diagnostics

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"os"
	"strings"
	"sync"
	"testing"
	"time"
)

func capture() (context.Context, *bytes.Buffer) {
	var b bytes.Buffer
	return WithRequest(WithLogger(context.Background(), slog.New(slog.NewJSONHandler(&b, &slog.HandlerOptions{Level: slog.LevelDebug}))), "test.operation"), &b
}
func records(t *testing.T, b *bytes.Buffer) []map[string]any {
	t.Helper()
	var out []map[string]any
	for _, line := range strings.Split(strings.TrimSpace(b.String()), "\n") {
		if line == "" {
			continue
		}
		var r map[string]any
		if err := json.Unmarshal([]byte(line), &r); err != nil {
			t.Fatal(err)
		}
		out = append(out, r)
	}
	return out
}
func TestDiagnosticFactsExcludeNestedContent(t *testing.T) {
	ctx, b := capture()
	canary := "secret-url-path-key-canary"
	nested := errors.Join(&url.Error{Op: canary, URL: canary, Err: errors.New(canary)}, &os.PathError{Op: canary, Path: canary, Err: errors.New(canary)})
	err := Annotate(nested, Facts{Classification: canary, Cause: canary, Endpoint: canary, Stage: canary, LimitName: canary, LimitValue: 33})
	if !Report(ctx, err) {
		t.Fatal("no primary")
	}
	if strings.Contains(b.String(), canary) {
		t.Fatal("nested content leaked")
	}
	r := records(t, b)[0]
	for _, field := range []string{"classification", "cause", "endpoint", "stage", "limit_name"} {
		if r[field] != "unknown" {
			t.Fatalf("unsafe field %s", field)
		}
	}
	if r["operation"] != "test.operation" || r["request_id"] == nil || r["operation_id"] == nil {
		t.Fatal("missing correlation")
	}
}
func TestPrimaryClaimSharedAcrossWrappers(t *testing.T) {
	ctx, b := capture()
	err := Annotate(errors.New("private"), Facts{Cause: "body_read"})
	wrapped := fmt.Errorf("private wrapper: %w", err)
	var wg sync.WaitGroup
	for range 100 {
		wg.Go(func() { Report(ctx, wrapped) })
	}
	wg.Wait()
	if len(records(t, b)) != 1 {
		t.Fatal("duplicate primary")
	}
	Report(ctx, Annotate(errors.New("second"), Facts{Cause: "response_write"}))
	if len(records(t, b)) != 2 {
		t.Fatal("distinct failure suppressed")
	}
}
func TestRepeatFirstChangeRecovery(t *testing.T) {
	ctx, b := capture()
	var repeat Repeat
	for range 101 {
		repeat.Failure(ctx, Annotate(errors.New("private"), Facts{Cause: "connection"}))
	}
	repeat.Failure(ctx, Annotate(errors.New("private"), Facts{Cause: "dns"}))
	repeat.Failure(ctx, Annotate(errors.New("private"), Facts{Cause: "dns"}))
	repeat.Recover(ctx)
	repeat.Recover(ctx)
	rr := records(t, b)
	if len(rr) != 3 || rr[1]["suppressed_count"] != float64(100) || rr[2]["suppressed_count"] != float64(1) {
		t.Fatalf("incorrect series %v", rr)
	}
}
func TestDefaultJSONAndExpectedOutcomes(t *testing.T) {
	var b bytes.Buffer
	ctx := Operation(WithLogger(context.Background(), NewLogger(&b)), "console.startup")
	Event(ctx, "ready", "")
	Report(ctx, Annotate(errors.New("private"), Facts{Cause: "assets"}))
	Report(ctx, Annotate(context.Canceled, Facts{}))
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	Report(canceled, Annotate(errors.New("private"), Facts{}))
	Report(ctx, Annotate(errors.New("private"), Facts{Expected: true}))
	NewLogger(&b).Debug("hidden")
	rr := records(t, &b)
	if len(rr) != 2 {
		t.Fatal(rr)
	}
	for _, r := range rr {
		if _, err := time.Parse(time.RFC3339Nano, r["time"].(string)); err != nil {
			t.Fatal(err)
		}
	}
}
func TestDetachedContextRetainsOnlyDiagnosticMetadata(t *testing.T) {
	type secretKey struct{}
	ctx, b := capture()
	ctx = context.WithValue(ctx, secretKey{}, "secret")
	ctx = WithScope(ctx, "trusted-scope")
	canceled, cancel := context.WithCancel(ctx)
	cancel()
	child := Detach(context.Background(), canceled, "artifact.acquire")
	if child.Err() != nil || child.Value(secretKey{}) != nil {
		t.Fatal("copied requester cancellation or credentials")
	}
	Report(child, Annotate(errors.New("private"), Facts{Cause: "body_read"}))
	r := records(t, b)[0]
	if r["scope_id"] != "trusted-scope" || r["parent_operation_id"] == nil {
		t.Fatal(r)
	}
}
func TestSecurityRejectionLogsAreBounded(t *testing.T) {
	ctx, b := capture()
	now := time.Date(2100, 1, 1, 0, 0, 0, 0, time.UTC)
	for range 1001 {
		RejectAt(ctx, "browser", "origin", now)
	}
	RejectAt(ctx, "browser", "origin", now.Add(time.Minute))
	rr := records(t, b)
	if len(rr) != 2 || rr[1]["suppressed_count"] != float64(1000) {
		t.Fatal(rr)
	}
}

func TestServerLogExcludesEmergencyMessageContent(t *testing.T) {
	ctx, b := capture()
	ServerLog(ctx).Print("panic: secret-path-and-request-canary")
	rr := records(t, b)
	if len(rr) != 1 || rr[0]["operation"] != "http.server" || strings.Contains(b.String(), "canary") {
		t.Fatal("unsafe HTTP emergency log")
	}
}
