package browserapi

import (
	"context"
	"io"
	"net/http"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
)

// The writer carries the named handler context through response helpers. It
// preserves Flusher capability exactly, including for committed streams.
type diagnosticWriter struct {
	http.ResponseWriter
	ctx    context.Context
	failed bool
}

func (w *diagnosticWriter) diagnosticContext() context.Context { return w.ctx }
func (w *diagnosticWriter) Unwrap() http.ResponseWriter        { return w.ResponseWriter }
func (w *diagnosticWriter) Write(p []byte) (int, error) {
	n, err := w.ResponseWriter.Write(p)
	if err == nil && n < len(p) {
		err = io.ErrShortWrite
	}
	if err != nil && !w.failed {
		w.failed = true
		reportResponse(w, err, "response_write")
	}
	return n, err
}

type diagnosticFlusher struct {
	*diagnosticWriter
	flusher http.Flusher
}

func (w diagnosticFlusher) Flush() { w.flusher.Flush() }
func wrapDiagnosticWriter(w http.ResponseWriter, ctx context.Context) http.ResponseWriter {
	d := &diagnosticWriter{ResponseWriter: w, ctx: ctx}
	if f, ok := w.(http.Flusher); ok {
		return diagnosticFlusher{d, f}
	}
	return d
}
func responseContext(w http.ResponseWriter) context.Context {
	if d, ok := w.(interface{ diagnosticContext() context.Context }); ok {
		return d.diagnosticContext()
	}
	return context.Background()
}
func reportResponse(w http.ResponseWriter, err error, cause string) {
	endpoint, _ := responseContext(w).Value(responseEndpointKey{}).(string)
	diagnostics.Report(responseContext(w), diagnostics.Annotate(err, diagnostics.Facts{Classification: "internal", Cause: cause, Stage: "response", Endpoint: endpoint}))
}
func browserOperation(path string) string {
	if strings.HasPrefix(path, "/api/console/v1/artifacts/") && strings.HasSuffix(path, "/raw") {
		return "browser.artifactRawDownload"
	}
	switch path {
	case "/api/console/v1/pairing/exchange":
		return "browser.exchange"
	case "/api/console/v1/pairing/challenge":
		return "browser.manualChallenge"
	case "/api/console/v1/bootstrap":
		return "browser.bootstrap"
	case "/api/console/v1/pairing/link":
		return "browser.pairingLink"
	case "/api/console/v1/tabs/release":
		return "browser.releaseTab"
	case "/api/console/v1/tabs/heartbeat":
		return "browser.heartbeat"
	case "/api/console/v1/target/status":
		return "browser.targetStatus"
	case "/api/console/v1/target/connect":
		return "browser.targetConnect"
	case "/api/console/v1/target/credential":
		return "browser.targetCredential"
	case "/api/console/v1/target/recheck":
		return "browser.targetRecheck"
	case "/api/console/v1/mcp/status":
		return "browser.mcpStatus"
	case "/api/console/v1/mcp/enable":
		return "browser.mcpEnable"
	case "/api/console/v1/mcp/reveal":
		return "browser.mcpReveal"
	case "/api/console/v1/mcp/regenerate":
		return "browser.mcpRegenerate"
	case "/api/console/v1/mcp/disable":
		return "browser.mcpDisable"
	case "/api/console/v1/mcp/remove-invalid":
		return "browser.mcpRemoveInvalid"
	case "/api/console/v1/observability/instance":
		return "browser.observabilityInstance"
	case "/api/console/v1/skills/list":
		return "browser.skillsList"
	case "/api/console/v1/skills/detail":
		return "browser.skillDetail"
	case "/api/console/v1/active-executions/list":
		return "browser.activeExecutionsList"
	case "/api/console/v1/active-executions/detail":
		return "browser.activeExecutionDetail"
	case "/api/console/v1/traces/list":
		return "browser.tracesList"
	case "/api/console/v1/traces/detail":
		return "browser.traceDetail"
	case "/api/console/v1/traces/analysis/summary":
		return "browser.traceAnalysisSummary"
	case "/api/console/v1/traces/analysis/plans":
		return "browser.traceAnalysisPlans"
	case "/api/console/v1/traces/analysis/frames":
		return "browser.traceAnalysisFrames"
	case "/api/console/v1/traces/analysis/records":
		return "browser.traceAnalysisRecords"
	case "/api/console/v1/traces/analysis/usage":
		return "browser.traceAnalysisUsage"
	case "/api/console/v1/traces/analysis/attempts":
		return "browser.traceAnalysisAttempts"
	case "/api/console/v1/traces/analysis/retries":
		return "browser.traceAnalysisRetries"
	case "/api/console/v1/traces/analysis/validation-links":
		return "browser.traceAnalysisValidationLinks"
	case "/api/console/v1/traces/analysis/failures":
		return "browser.traceAnalysisFailures"
	case "/api/console/v1/traces/analysis/failure-diagnostic":
		return "browser.traceAnalysisFailureDiagnostic"
	case "/api/console/v1/traces/analysis/gaps":
		return "browser.traceAnalysisGaps"
	case "/api/console/v1/traces/analysis/uncertainties":
		return "browser.traceAnalysisUncertainties"
	case "/api/console/v1/traces/analysis/search":
		return "browser.traceAnalysisSearch"
	case "/api/console/v1/traces/analysis/content-range":
		return "browser.traceAnalysisPayloadRange"
	case "/api/console/v1/traces/analysis/raw-record-range":
		return "browser.traceAnalysisRawRecordRange"
	case "/api/console/v1/artifacts/acquire":
		return "browser.artifactAcquire"
	case "/api/console/v1/artifacts/import":
		return "browser.artifactImport"
	case "/api/console/v1/artifacts/storage":
		return "browser.artifactStorage"
	case "/api/console/v1/artifacts/remove":
		return "browser.artifactRemove"
	case "/api/console/v1/artifacts/clear-expired":
		return "browser.artifactClearExpired"
	case "/api/console/v1/artifacts/clear-all-unused":
		return "browser.artifactClearAllUnused"
	case "/api/console/v1/activity/stream":
		return "browser.activityStream"
	case "/api/console/v1/activity/recent":
		return "browser.activityRecent"
	default:
		return "browser.unknown"
	}
}

func (w *diagnosticWriter) setDiagnosticScope(scope string) {
	w.ctx = diagnostics.WithScope(w.ctx, scope)
}
func responseScope(w http.ResponseWriter, scope string) {
	if d, ok := w.(interface{ setDiagnosticScope(string) }); ok {
		d.setDiagnosticScope(scope)
	}
}
func reportUnavailable(w http.ResponseWriter) {
	diagnostics.Report(responseContext(w), diagnostics.Annotate(staticError("unavailable dependency"), diagnostics.Facts{Classification: "internal", Cause: "unavailable"}))
}

func responseFailed(w http.ResponseWriter) bool {
	if d, ok := w.(interface{ diagnosticFailed() bool }); ok {
		return d.diagnosticFailed()
	}
	return false
}
func (w *diagnosticWriter) diagnosticFailed() bool { return w.failed }

type responseEndpointKey struct{}

func withResponseEndpoint(ctx context.Context, operation string) context.Context {
	endpoint := ""
	switch operation {
	case "browser.artifactRawDownload":
		endpoint = "artifact.download"
	case "browser.activityStream":
		endpoint = "activity.stream"
	}
	return context.WithValue(ctx, responseEndpointKey{}, endpoint)
}
