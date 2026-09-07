package browserapi

import (
	"context"
	"encoding/json"
	"net/http"
	"sort"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
)

const maxObservabilityJSONBody = 4 * 1024

func (router *Router) observabilityInstance(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	if err := decodeJSONLimit(request, &struct{}{}, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	status, domain := router.options.Observability.GetInstance(request.Context(), scope)
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	router.writeScopedJSON(response, scope.ID, status)
}

func (router *Router) skillsList(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		Cursor   string `json:"cursor,omitempty"`
		PageSize int    `json:"pageSize,omitempty"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	page, domain := router.options.Observability.ListSkills(request.Context(), scope, observability.ListRequest{
		Cursor:   body.Cursor,
		PageSize: body.PageSize,
	})
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	router.writeScopedJSON(response, scope.ID, page)
}

func (router *Router) skillDetail(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		RegisteredName string `json:"registeredName"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	detail, domain := router.options.Observability.GetSkill(request.Context(), scope, body.RegisteredName)
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	router.writeScopedJSON(response, scope.ID, detail)
}

func (router *Router) activeExecutionsList(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		Cursor   string `json:"cursor,omitempty"`
		PageSize int    `json:"pageSize,omitempty"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	page, domain := router.options.Observability.ListActiveExecutions(request.Context(), scope, observability.ListRequest{
		Cursor:   body.Cursor,
		PageSize: body.PageSize,
	})
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	router.writeScopedJSON(response, scope.ID, page)
}

func (router *Router) activeExecutionDetail(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		SessionID string `json:"sessionId"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	execution, domain := router.options.Observability.GetActiveExecution(request.Context(), scope, body.SessionID)
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	router.writeScopedJSON(response, scope.ID, execution)
}

func (router *Router) tracesList(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		Cursor   string `json:"cursor,omitempty"`
		PageSize int    `json:"pageSize,omitempty"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	page, domain := router.options.Observability.ListTraces(request.Context(), scope, observability.ListRequest{
		Cursor:   body.Cursor,
		PageSize: body.PageSize,
	})
	if domain != nil {
		diagnostics.Report(request.Context(), domain)
		if allowsCachedTraceFallback(domain) {
			if cached, ok := router.cachedTracePage(request.Context(), scope.ID); ok {
				router.writeScopedJSON(response, scope.ID, cached)
				return
			}
		}
		writeDomainError(response, domain)
		return
	}
	page = router.enrichTracePage(request.Context(), scope.ID, page)
	router.writeScopedJSON(response, scope.ID, page)
}

func (router *Router) traceDetail(response http.ResponseWriter, request *http.Request, _ string) {
	if router.options.Observability == nil || router.options.Target == nil {
		reportUnavailable(response)
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "Observability service is unavailable.")
		return
	}
	var body struct {
		TraceID string `json:"traceId"`
	}
	if err := decodeJSONLimit(request, &body, maxObservabilityJSONBody); err != nil {
		writeError(response, http.StatusBadRequest, "INVALID_REQUEST", "Invalid request.")
		return
	}
	scope, domain := router.options.Target.Capture()
	if domain != nil {
		writeDomainError(response, domain)
		return
	}
	responseScope(response, string(scope.ID))
	request = request.WithContext(diagnostics.WithScope(request.Context(), string(scope.ID)))
	trace, domain := router.options.Observability.GetTrace(request.Context(), scope, body.TraceID)
	if domain != nil {
		diagnostics.Report(request.Context(), domain)
		if allowsCachedTraceFallback(domain) {
			if cached, ok := router.cachedTrace(request.Context(), scope.ID, body.TraceID); ok {
				router.writeScopedJSON(response, scope.ID, cached)
				return
			}
		}
		writeDomainError(response, domain)
		return
	}
	trace = router.enrichTrace(request.Context(), scope.ID, trace)
	router.writeScopedJSON(response, scope.ID, trace)
}

func allowsCachedTraceFallback(domain *consolecore.Error) bool {
	if domain == nil {
		return false
	}
	switch domain.Code {
	case consolecore.CodeTargetAuthentication,
		consolecore.CodeTargetAccessBlocked,
		consolecore.CodeTargetUnavailable,
		consolecore.CodeNotFound,
		consolecore.CodeConsoleError:
		return true
	default:
		return false
	}
}

// cachedTrace returns acquisition-time trace facts for a valid installed
// artifact without claiming that the application is currently reachable or
// authorized.
func (router *Router) cachedTrace(ctx context.Context, scope target.ScopeID, traceID string) (observability.Trace, bool) {
	if router.options.Artifacts == nil {
		return observability.Trace{}, false
	}
	lookup, domain := router.options.Artifacts.Lookup(evidence.ForTarget(scope), traceID)
	if domain != nil {
		diagnostics.Report(diagnostics.Operation(ctx, "browser.cachedTrace"), domain)
	}
	if domain != nil || !lookup.LocalAvailable {
		return observability.Trace{}, false
	}
	return observability.Trace{
		TargetScopeID:             string(scope),
		TraceID:                   lookup.Metadata.TraceID,
		SessionID:                 lookup.Metadata.SessionID,
		EntrySkill:                lookup.Metadata.EntrySkill,
		Outcome:                   lookup.Metadata.Outcome,
		FinalizedAt:               lookup.Metadata.FinalizedAt,
		SizeBytes:                 lookup.Metadata.SizeBytes,
		PersistencePolicy:         lookup.Metadata.PersistencePolicy,
		ApplicationTraceExpiresAt: lookup.Metadata.ApplicationTraceExpiresAt,
		LocalAvailable:            true,
		ArtifactHandle:            string(lookup.Handle),
		ApplicationAvailability:   string(lookup.ApplicationAvailability),
	}, true
}

func (router *Router) cachedTracePage(ctx context.Context, scope target.ScopeID) (observability.Page[observability.Trace], bool) {
	if router.options.Artifacts == nil {
		return observability.Page[observability.Trace]{}, false
	}
	snapshot, domain := router.options.Artifacts.StorageSnapshot()
	if domain != nil {
		diagnostics.Report(diagnostics.Operation(ctx, "browser.cachedTracePage"), domain)
	}
	if domain != nil || len(snapshot.Entries) == 0 {
		return observability.Page[observability.Trace]{}, false
	}
	items := make([]observability.Trace, 0, len(snapshot.Entries))
	observedAt := snapshot.Entries[0].AcquiredAt
	for _, entry := range snapshot.Entries {
		if entry.AcquiredAt.After(observedAt) {
			observedAt = entry.AcquiredAt
		}
		if trace, ok := router.cachedTrace(ctx, scope, entry.TraceID); ok {
			items = append(items, trace)
		}
	}
	sort.Slice(items, func(i, j int) bool { return items[i].TraceID < items[j].TraceID })
	if len(items) == 0 {
		return observability.Page[observability.Trace]{}, false
	}
	return observability.Page[observability.Trace]{
		TargetScopeID: string(scope),
		Items:         items,
		ObservedAt:    observedAt,
	}, true
}

func (router *Router) writeScopedJSON(response http.ResponseWriter, scope target.ScopeID, value any) {
	content, err := json.Marshal(value)
	if err != nil {
		reportResponse(response, err, "response_encode")
		writeError(response, http.StatusInternalServerError, "CONSOLE_ERROR", "The Console response could not be created.")
		return
	}
	content = append(content, '\n')
	if domain := router.options.Target.PublishCurrent(scope, func() {
		writeJSONBytes(response, http.StatusOK, content)
	}); domain != nil {
		writeDomainError(response, domain)
	}
}
