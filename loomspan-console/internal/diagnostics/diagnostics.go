// Package diagnostics contains the Console's content-free operational log boundary.
package diagnostics

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"log/slog"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

// Facts contains classifications, never error messages or application content.
// String fields except trusted scope IDs are filtered through the closed vocabulary.
type Facts struct {
	Classification, Cause, Endpoint, Stage, LimitName string
	LimitValue                                        int64
	Status                                            int
	Expected                                          bool
}
type factSource interface{ DiagnosticFacts() Facts }
type claimSource interface{ DiagnosticClaim() *atomic.Bool }
type annotation struct {
	cause error
	facts Facts
	claim *atomic.Bool
}

func (a *annotation) Error() string {
	if a.cause != nil {
		return a.cause.Error()
	}
	return "Console operation failed"
}
func (a *annotation) Unwrap() error                 { return a.cause }
func (a *annotation) DiagnosticFacts() Facts        { return a.facts }
func (a *annotation) DiagnosticClaim() *atomic.Bool { return a.claim }
func Claim(err error) *atomic.Bool {
	var c claimSource
	if errors.As(err, &c) {
		return c.DiagnosticClaim()
	}
	return new(atomic.Bool)
}

var claimMu sync.Mutex

func EnsureClaim(slot **atomic.Bool) *atomic.Bool {
	claimMu.Lock()
	defer claimMu.Unlock()
	if *slot == nil {
		*slot = new(atomic.Bool)
	}
	return *slot
}
func ClaimOr(err error, fallback *atomic.Bool) *atomic.Bool {
	var c claimSource
	if errors.As(err, &c) {
		return c.DiagnosticClaim()
	}
	return fallback
}
func Annotate(err error, f Facts) error { return &annotation{err, f, Claim(err)} }

type metadata struct {
	logger                                *slog.Logger
	request, operation, id, parent, scope string
}
type contextKey struct{}

var nextID atomic.Uint64

func id() string { return fmt.Sprintf("op-%x", nextID.Add(1)) }
func meta(ctx context.Context) metadata {
	if ctx != nil {
		if m, ok := ctx.Value(contextKey{}).(metadata); ok {
			return m
		}
	}
	return metadata{}
}
func WithLogger(ctx context.Context, logger *slog.Logger) context.Context {
	m := meta(ctx)
	m.logger = logger
	return context.WithValue(ctx, contextKey{}, m)
}
func WithRequest(ctx context.Context, operation string) context.Context {
	m := meta(ctx)
	m.request = id()
	m.parent = ""
	m.id = id()
	m.operation = operation
	return context.WithValue(ctx, contextKey{}, m)
}
func Operation(ctx context.Context, operation string) context.Context {
	m := meta(ctx)
	m.parent = m.id
	m.id = id()
	m.operation = operation
	return context.WithValue(ctx, contextKey{}, m)
}
func WithScope(ctx context.Context, scope string) context.Context {
	m := meta(ctx)
	m.scope = scope
	return context.WithValue(ctx, contextKey{}, m)
}
func Detach(lifetime, source context.Context, operation string) context.Context {
	return Operation(context.WithValue(lifetime, contextKey{}, meta(source)), operation)
}
func NewLogger(w io.Writer) *slog.Logger {
	return slog.New(slog.NewJSONHandler(w, &slog.HandlerOptions{Level: slog.LevelInfo}))
}

// ServerLog prevents net/http's emergency logger (including panic reports) from
// rendering arbitrary request or error content outside the diagnostic boundary.
func ServerLog(ctx context.Context) *log.Logger {
	return log.New(serverLogWriter{ctx: Operation(ctx, "http.server")}, "", 0)
}

type serverLogWriter struct{ ctx context.Context }

func (w serverLogWriter) Write(p []byte) (int, error) {
	Report(w.ctx, Annotate(nil, Facts{Classification: "internal", Cause: "unknown", Stage: "response"}))
	return len(p), nil
}
func base(ctx context.Context) (*slog.Logger, []any) {
	m := meta(ctx)
	if m.logger == nil {
		m.logger = slog.Default()
	}
	if m.id == "" {
		m.id = id()
	}
	if m.operation == "" {
		m.operation = "console.operation"
	}
	a := []any{"operation", m.operation, "operation_id", m.id}
	if m.request != "" {
		a = append(a, "request_id", m.request)
	}
	if m.parent != "" {
		a = append(a, "parent_operation_id", m.parent)
	}
	if m.scope != "" {
		a = append(a, "scope_id", m.scope)
	}
	return m.logger, a
}

// Safe accepts only the diagnostic vocabulary. It never formats arbitrary values.
func Safe(value string) string {
	switch value {
	case "internal", "unknown", "authentication", "access", "incompatible", "unavailable", "protocol", "invalid_argument", "invalid_cursor", "stale_cursor", "not_found", "limit_exceeded", "live_monitoring_unavailable",
		"INVALID_ARGUMENT", "TARGET_AUTHENTICATION_REQUIRED", "TARGET_ACCESS_BLOCKED", "TARGET_UNAVAILABLE", "INCOMPATIBLE_TARGET", "INCOMPATIBLE_ARTIFACT", "TARGET_CHANGED", "INVALID_CURSOR", "STALE_CURSOR", "NOT_FOUND", "ARTIFACT_EXPIRED", "AMBIGUOUS_TRACE", "TRACE_UNAVAILABLE", "ARTIFACT_IN_USE", "ARTIFACT_ALREADY_EXISTS", "INVALID_ARTIFACT", "LIVE_MONITORING_UNAVAILABLE", "LIMIT_EXCEEDED", "LOCAL_STORAGE_UNAVAILABLE", "CONSOLE_ERROR",
		"dns", "connection", "timeout", "tls_untrusted_issuer", "tls_hostname_mismatch", "tls_expired", "tls_not_yet_valid", "tls_handshake", "redirect", "namespace_not_found", "upstream_server", "upstream_protocol",
		"body_read", "body_limit", "response_decode", "instance_header", "content_encoding", "response_encode", "response_write", "stream_read", "stream_protocol", "stream_limit", "storage_read", "storage_write", "storage_sync", "storage_close", "storage_remove", "storage_capacity", "storage_open", "permission", "no_space", "invalid_content", "line_limit", "depth_limit", "canceled", "configuration", "assets", "listener", "profile", "workspace", "shutdown", "entropy",
		"instance", "skills.list", "skills.get", "executions.list", "executions.get", "traces.list", "traces.get", "artifact.download", "activity.stream",
		"open", "request", "response", "probe", "decode", "copy", "sync", "close", "remove", "capacity", "expiry", "manifest", "index", "payload", "parse", "install", "monitor", "startup", "read", "write", "handshake", "cursor", "baseline",
		"maxResponseBytes", "problemMaxBytes", "maxBytes", "maxEventBytes", "maxLineBytes", "maxDepth", "responseBudget", "maxJSONBody", "maxTargetJSONBody", "maxArtifactBytes", "maxWorkspaceBytes", "maxArtifacts",
		"connected", "disconnected", "selected", "rotated", "required", "accepted", "blocked", "created", "expired", "released", "closed", "paired", "enabled", "disabled", "ready", "stopped", "recovered", "authentication_required", "authentication_rejected", "access_blocked", "incompatible_target", "none", "connecting", "browser", "mcp", "origin", "host", "method", "validation", "session", "authentication_generation", "stream", "admission",
		"MALFORMED_JSON", "INCONSISTENT_IDENTITY", "NON_MONOTONIC_SEQUENCE", "INCOMPLETE_CHUNKS", "INVALID_CHUNKS", "MISSING_COMPLETION", "NON_FINAL_COMPLETION", "UNSUPPORTED_VALUE", "CONTRADICTORY_USAGE", "INVALID_FRAME_RELATIONSHIP", "INVALID_TERMINAL_FAILURE", "INVALID_ATTEMPT", "INVALID_USAGE", "INVALID_PLAN_LINEAGE", "LINE_TOO_LARGE", "EXCESSIVE_JSON_DEPTH", "TRUNCATED_INPUT", "maxPhysicalLineBytes", "maxJSONDepth", "invalid",
		"cursor_protocol", "max_activity_utf8_bytes", "maxSSELineBytes", "maxSSEFrameBytes", "maxSSEDataBytes", "traceImportBytes", "trace-workspace.max-bytes", "responseBytes", "rangeBytes", "pageSize", "literalTextBytes", "literalTextRunes":
		return value
	default:
		return "unknown"
	}
}

func Extract(err error) Facts {
	var result Facts
	// Outer wrappers own classifications; inner annotations refine causal facts.
	var visit func(error)
	visit = func(e error) {
		if e == nil {
			return
		}
		if s, ok := e.(factSource); ok {
			f := s.DiagnosticFacts()
			if result.Classification == "" {
				result.Classification = f.Classification
			}
			if f.Cause != "" {
				result.Cause = f.Cause
			}
			if f.Endpoint != "" {
				result.Endpoint = f.Endpoint
			}
			if f.Stage != "" {
				result.Stage = f.Stage
			}
			if f.LimitName != "" {
				result.LimitName = f.LimitName
				result.LimitValue = f.LimitValue
			}
			if f.Status != 0 {
				result.Status = f.Status
			}
			result.Expected = result.Expected || f.Expected
		}
		if joined, ok := e.(interface{ Unwrap() []error }); ok {
			for _, child := range joined.Unwrap() {
				visit(child)
			}
		} else if single, ok := e.(interface{ Unwrap() error }); ok {
			visit(single.Unwrap())
		}
	}
	visit(err)
	if errors.Is(err, context.Canceled) {
		result.Expected = true
		result.Cause = "canceled"
	}
	if result.Classification == "" {
		result.Classification = "internal"
	}
	if result.Cause == "" {
		result.Cause = "unknown"
	}
	return result
}
func fields(f Facts) []any {
	a := []any{"classification", Safe(f.Classification), "cause", Safe(f.Cause)}
	for _, v := range []struct{ k, v string }{{"endpoint", f.Endpoint}, {"stage", f.Stage}, {"limit_name", f.LimitName}} {
		if v.v != "" {
			a = append(a, v.k, Safe(v.v))
		}
	}
	if f.LimitName != "" {
		a = append(a, "limit_value", f.LimitValue)
	}
	if f.Status != 0 {
		a = append(a, "status", f.Status)
	}
	return a
}
func expected(ctx context.Context, f Facts) bool {
	return f.Expected || (ctx != nil && ctx.Err() != nil)
}
func emit(ctx context.Context, f Facts, suppressed uint64) {
	logger, a := base(ctx)
	a = append(a, fields(f)...)
	if suppressed > 0 {
		a = append(a, "suppressed_count", suppressed)
	}
	level := slog.LevelError
	if f.Classification == "INVALID_ARTIFACT" || f.Classification == "INCOMPATIBLE_ARTIFACT" || f.Classification == "incompatible" || f.Classification == "INCOMPATIBLE_TARGET" {
		level = slog.LevelWarn
	}
	logger.Log(ctx, level, "operation failed", a...)
}
func Report(ctx context.Context, err error) bool {
	if err == nil {
		return false
	}
	f := Extract(err)
	if !Claim(err).CompareAndSwap(false, true) {
		return false
	}
	if expected(ctx, f) {
		return false
	}
	emit(ctx, f, 0)
	return true
}
func Event(ctx context.Context, state, previous string) {
	if state == previous {
		return
	}
	logger, a := base(ctx)
	a = append(a, "state", Safe(state))
	if previous != "" {
		a = append(a, "previous_state", Safe(previous))
	}
	logger.InfoContext(ctx, "lifecycle changed", a...)
}
func Link(ctx, shared context.Context) {
	logger, a := base(ctx)
	a = append(a, "shared_operation_id", meta(shared).id)
	logger.InfoContext(ctx, "operation.link", a...)
}

type Repeat struct {
	mu         sync.Mutex
	key        string
	suppressed uint64
}

func (r *Repeat) Failure(ctx context.Context, err error) {
	if err == nil {
		return
	}
	f := Extract(err)
	Claim(err).CompareAndSwap(false, true)
	if expected(ctx, f) {
		return
	}
	key := strings.Join([]string{Safe(f.Classification), Safe(f.Cause), Safe(f.Endpoint), Safe(f.Stage), Safe(f.LimitName), fmt.Sprint(f.LimitValue), fmt.Sprint(f.Status)}, "|")
	r.mu.Lock()
	defer r.mu.Unlock()
	if key == r.key {
		r.suppressed++
		return
	}
	emit(ctx, f, r.suppressed)
	r.key = key
	r.suppressed = 0
}
func (r *Repeat) Recover(ctx context.Context) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.key == "" {
		return
	}
	logger, a := base(ctx)
	a = append(a, "state", "recovered", "suppressed_count", r.suppressed)
	logger.InfoContext(ctx, "operation recovered", a...)
	r.key = ""
	r.suppressed = 0
}
func (r *Repeat) Reset() { r.mu.Lock(); defer r.mu.Unlock(); r.key = ""; r.suppressed = 0 }

type rejectionBucket struct {
	last       time.Time
	suppressed uint64
}

var rejections struct {
	sync.Mutex
	buckets [2][10]rejectionBucket
}

func Reject(ctx context.Context, adapter, reason string) { RejectAt(ctx, adapter, reason, time.Now()) }
func RejectAt(ctx context.Context, adapter, reason string, now time.Time) {
	ai := 0
	if adapter == "mcp" {
		ai = 1
	}
	ri := 0
	switch reason {
	case "authentication":
		ri = 1
	case "origin":
		ri = 2
	case "host":
		ri = 3
	case "method":
		ri = 4
	case "validation":
		ri = 5
	case "session":
		ri = 6
	case "disabled":
		ri = 7
	case "admission":
		ri = 8
	case "body_limit":
		ri = 9
	}
	rejections.Lock()
	defer rejections.Unlock()
	b := &rejections.buckets[ai][ri]
	if !b.last.IsZero() && now.Sub(b.last) < time.Minute {
		b.suppressed++
		return
	}
	logger, a := base(ctx)
	a = append(a, "classification", "authentication", "cause", Safe(reason), "suppressed_count", b.suppressed)
	logger.DebugContext(ctx, "request rejected", a...)
	b.last = now
	b.suppressed = 0
}
