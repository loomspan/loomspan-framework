package browserapi

import (
	"encoding/base64"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
)

const maxTraceAnalysisJSONBody = 8 * 1024

type analysisRequest struct {
	TraceID  string `json:"traceId"`
	Source   string `json:"source"`
	PageSize int    `json:"pageSize,omitempty"`
	Cursor   string `json:"cursor,omitempty"`
}

// analysis resolves consistently while retaining precise domain errors.  The
// small wrapper also keeps every response scope-published.
func (router *Router) resolveAnalysis(response http.ResponseWriter, request *http.Request, body *analysisRequest) (evidence.Reference, artifact.Handle, bool) {
	if router.options.TraceAnalysis == nil || router.options.Artifacts == nil {
		writeError(response, 500, "CONSOLE_ERROR", "Trace analysis is unavailable.")
		return evidence.Reference{}, "", false
	}
	if body.TraceID == "" {
		writeError(response, 400, "INVALID_REQUEST", "A trace ID is required.")
		return evidence.Reference{}, "", false
	}
	ref, domain := router.resolveEvidenceReference(body.Source)
	if domain != nil {
		writeDomainError(response, domain)
		return evidence.Reference{}, "", false
	}
	entry, domain := router.options.Artifacts.Lookup(ref, body.TraceID)
	if domain != nil {
		writeEvidenceDomainError(response, ref, domain)
		return evidence.Reference{}, "", false
	}
	if !entry.LocalAvailable || entry.Handle == "" {
		writeEvidenceDomainError(response, ref, consolecore.NewError(consolecore.CodeNotFound, "The trace has not been acquired for analysis.", ref.ID(), consolecore.Details{}, nil))
		return evidence.Reference{}, "", false
	}
	return ref, entry.Handle, true
}

func (router *Router) traceAnalysisSummary(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.GetSummary(r.Context(), s, traceanalysis.SummaryRequest{Handle: h})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	router.writeEvidenceJSON(w, s, summaryDTO{Source: s.Source, TargetScopeID: string(s.TargetScope), TraceID: v.Context.TraceID, SessionID: v.Context.SessionID, Outcome: v.Outcome, TerminalFailureID: v.TerminalFailureID, ConfiguredLimits: configuredLimitsDTOValue(v.ConfiguredLimits), RecordCount: v.RecordCount, FrameCount: v.FrameCount, PlanCount: v.PlanCount, AttemptCount: v.AttemptCount, RetryCount: v.RetryCount, ValidationCount: v.ValidationCount, FailureCount: v.FailureCount, PayloadCount: v.PayloadCount, GapCount: v.GapCount, UncertaintyCount: v.UncertaintyCount, RootFrameIDs: append([]string{}, v.RootFrameIDs...), AttributedUsage: usageDTOValue(v.AttributedUsage), TerminalUsage: usageDTOValue(v.TerminalUsage), UnattributedUsage: usageDTOValue(v.UnattributedUsage), UnframedAttributedUsage: usageDTOValue(v.UnframedAttributed), UsageComplete: v.UsageComplete})
}

func (router *Router) traceAnalysisPlans(w http.ResponseWriter, r *http.Request, _ string) {
	var b struct {
		analysisRequest
		PlanID string `json:"planId,omitempty"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryPlans(r.Context(), s, traceanalysis.PlanQuery{
		Handle: h, PlanID: b.PlanID, PageSize: b.PageSize, Cursor: b.Cursor, Admit: newPlanPageAdmission(s),
	})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]planDTO, 0, len(v.Items))
	for _, item := range v.Items {
		items = append(items, planDTOValue(item))
	}
	router.writeEvidenceJSON(w, s, pageDTO[planDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}

func newPlanPageAdmission(ref evidence.Reference) func(traceanalysis.PlanSummary) bool {
	// Continuations are opaque base64url strings, so one byte costs one JSON
	// string byte. Reserve the full request-limit-sized cursor.
	worstCursor := strings.Repeat("x", maxTraceAnalysisJSONBody)
	envelope, err := json.Marshal(pageDTO[planDTO]{Source: ref.Source, TargetScopeID: string(ref.TargetScope), Items: []planDTO{}, HasMore: true, NextCursor: &worstCursor})
	// Replace the empty items array with admitted item bytes and reserve the
	// newline appended by writeJSON. A marshal failure safely rejects the item.
	used := len(envelope) - len("[]") + 1
	count := 0
	return func(item traceanalysis.PlanSummary) bool {
		if err != nil {
			return false
		}
		encoded, marshalErr := json.Marshal(planDTOValue(item))
		if marshalErr != nil {
			return false
		}
		separator := 0
		if count > 0 {
			separator = 1
		}
		if used+separator+len(encoded) > traceanalysis.MaxDescriptorResponseBytes {
			return false
		}
		used += separator + len(encoded)
		count++
		return true
	}
}

func (router *Router) traceAnalysisFrames(w http.ResponseWriter, r *http.Request, _ string) {
	var b struct {
		analysisRequest
		Filter     traceanalysis.FrameFilter     `json:"filter"`
		Order      traceanalysis.FrameOrder      `json:"order"`
		Projection traceanalysis.FrameProjection `json:"projection"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryFrames(r.Context(), s, traceanalysis.FrameQuery{Handle: h, Filter: b.Filter, Order: b.Order, Projection: b.Projection, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]frameDTO, 0, len(v.Items))
	compact := b.Projection == "" || b.Projection == traceanalysis.FrameProjectionCompact
	for _, x := range v.Items {
		items = append(items, frameDTO{FrameID: x.FrameID, ParentFrameID: x.ParentFrameID, ChildFrameIDs: append([]string{}, x.ChildFrameIDs...), FrameType: x.FrameType, Route: x.Route, PlanID: x.PlanID, TaskID: x.TaskID, StepNumber: x.StepNumber, ParallelGroup: x.ParallelGroup, EffectiveConcurrency: x.EffectiveConcurrency, OpenedTimestampMillis: x.OpenedTimestampMillis, ClosedTimestampMillis: x.ClosedTimestampMillis, InclusiveDurationMillis: x.InclusiveDurationMillis, SelfDurationMillis: x.SelfDurationMillis, DirectUsage: usageDTOValue(x.DirectUsage), DirectUsageComplete: x.DirectUsageComplete, DescendantUsage: usageDTOValue(x.DescendantUsage), DescendantUsageComplete: x.DescendantUsageComplete, InclusiveUsage: usageDTOValue(x.InclusiveUsage), InclusiveUsageComplete: x.InclusiveUsageComplete, SkillNames: append([]string{}, x.SkillNames...), Outcome: x.Outcome, AttemptIDs: append([]string{}, x.AttemptIDs...), RetrySequenceIDs: append([]string{}, x.RetrySequenceIDs...), ValidationStatuses: append([]string{}, x.ValidationStatuses...), FailureIDs: append([]string{}, x.FailureIDs...), GapKinds: append([]string{}, x.GapKinds...), UncertaintyKinds: append([]string{}, x.UncertaintyKinds...), DirectAttemptCount: x.DirectAttemptCount, DirectRetryCount: x.DirectRetryCount, DirectValidationCount: x.DirectValidationCount, DirectFailureCount: x.DirectFailureCount, GapCount: x.GapCount, UncertaintyCount: x.UncertaintyCount, compact: compact})
	}
	router.writeEvidenceJSON(w, s, pageDTO[frameDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisRecords(w http.ResponseWriter, r *http.Request, _ string) {
	var b struct {
		analysisRequest
		Filter         traceanalysis.RecordFilter         `json:"filter"`
		Representation traceanalysis.RecordRepresentation `json:"representation"`
		InlineContent  bool                               `json:"inlineContent"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryRecords(r.Context(), s, traceanalysis.RecordQuery{Handle: h, Filter: b.Filter, Representation: b.Representation, InlineContent: b.InlineContent, PageSize: b.PageSize, Cursor: b.Cursor, Admit: newRecordPageAdmission(s)})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]recordDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, boundedRecordDTOValue(x, s))
	}
	router.writeEvidenceJSON(w, s, pageDTO[recordDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisUsage(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.GetUsageBreakdown(r.Context(), s, h)
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	router.writeEvidenceJSON(w, s, usageDTO{Source: s.Source, TargetScopeID: string(s.TargetScope), Attributed: usageDTOValue(v.Attributed), Unattributed: usageDTOValue(v.Unattributed), UnframedAttributed: usageDTOValue(v.UnframedAttributed), Terminal: usageDTOValue(v.Terminal)})
}
func (router *Router) traceAnalysisAttempts(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryAttempts(r.Context(), s, traceanalysis.AttemptQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]attemptDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, attemptDTO{RetrySequenceID: x.RetrySequenceID, AttemptID: x.AttemptID, AttemptNumber: x.AttemptNumber,
			AttemptReason: x.AttemptReason, ProviderAttemptNumber: x.ProviderAttemptNumber, Outcome: x.Outcome,
			FailureClassification: x.FailureClassification, FailureCategory: x.FailureCategory,
			RetryDecision: x.RetryDecision, RetryDelayMillis: x.RetryDelayMillis, RetryDelaySource: x.RetryDelaySource,
			HTTPStatus: x.HTTPStatus, ProviderErrorType: x.ProviderErrorType, ProviderErrorCode: x.ProviderErrorCode,
			ContentRef: x.ContentRef, Usage: usageDTOValue(x.Usage), UsageComplete: x.UsageComplete})
	}
	router.writeEvidenceJSON(w, s, pageDTO[attemptDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisRetries(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryRetries(r.Context(), s, traceanalysis.RetryQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]retryDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, retryDTO{RetrySequenceID: x.RetrySequenceID, Usage: usageDTOValue(x.Usage), UsageComplete: x.UsageComplete})
	}
	router.writeEvidenceJSON(w, s, pageDTO[retryDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisValidationLinks(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryValidationLinks(r.Context(), s, traceanalysis.ValidationQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]validationDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, validationDTO{Status: x.Status, RetrySequenceID: x.RetrySequenceID, AttemptID: x.AttemptID, AttemptNumber: x.AttemptNumber})
	}
	router.writeEvidenceJSON(w, s, pageDTO[validationDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisFailures(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryFailures(r.Context(), s, traceanalysis.FailureQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]failureDTO, 0, len(v.Items))
	for _, x := range v.Items {
		diagnostics := make([]diagnosticDescriptorDTO, 0, len(x.Diagnostics))
		for _, d := range x.Diagnostics {
			diagnostics = append(diagnostics, diagnosticDescriptorDTO{Ordinal: d.Ordinal, Kind: d.Kind, ContentType: d.ContentType, Truncated: d.Truncated, CaptureLimitBytes: d.CaptureLimitBytes, DecodedBytes: d.DecodedBytes})
		}
		items = append(items, failureDTO{FailureID: x.FailureID, Terminal: x.Terminal, Sequence: x.Sequence, TimestampMillis: x.TimestampMillis, RecordType: x.RecordType, FrameID: x.FrameID, Route: x.Route, AttemptID: x.AttemptID, RetrySequenceID: x.RetrySequenceID, ValidationStatus: x.ValidationStatus, ExceptionType: x.ExceptionType, ContextSummary: x.ContextSummary, Diagnostics: diagnostics})
	}
	router.writeEvidenceJSON(w, s, pageDTO[failureDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisFailureDiagnostic(w http.ResponseWriter, r *http.Request, _ string) {
	var b struct {
		analysisRequest
		FailureID string `json:"failureId"`
		Ordinal   *int   `json:"ordinal"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	if b.Ordinal == nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.GetFailureDiagnostic(r.Context(), s, traceanalysis.FailureDiagnosticRequest{Handle: h, FailureID: b.FailureID, Ordinal: *b.Ordinal})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	desc := diagnosticDescriptorDTO{Ordinal: v.Descriptor.Ordinal, Kind: v.Descriptor.Kind, ContentType: v.Descriptor.ContentType, Truncated: v.Descriptor.Truncated, CaptureLimitBytes: v.Descriptor.CaptureLimitBytes, DecodedBytes: v.Descriptor.DecodedBytes}
	router.writeEvidenceJSON(w, s, failureDiagnosticDTO{Source: s.Source, TargetScopeID: string(s.TargetScope), FailureID: v.FailureID, Descriptor: desc, Text: v.Text})
}
func (router *Router) traceAnalysisGaps(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryGaps(r.Context(), s, traceanalysis.GapQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]gapDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, gapDTO{Kind: x.Kind, FrameID: x.FrameID, AttemptID: x.AttemptID})
	}
	router.writeEvidenceJSON(w, s, pageDTO[gapDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) traceAnalysisUncertainties(w http.ResponseWriter, r *http.Request, _ string) {
	var b analysisRequest
	if !router.decodeAnalysis(w, r, &b) {
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.QueryUncertainties(r.Context(), s, traceanalysis.UncertaintyQuery{Handle: h, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]uncertaintyDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, uncertaintyDTO{Kind: x.Kind, FrameID: x.FrameID})
	}
	router.writeEvidenceJSON(w, s, pageDTO[uncertaintyDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)})
}
func (router *Router) decodeAnalysis(w http.ResponseWriter, r *http.Request, b *analysisRequest) bool {
	if decodeJSONLimit(r, b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return false
	}
	return true
}
func (router *Router) traceAnalysisSearch(w http.ResponseWriter, r *http.Request, _ string) {
	var b struct {
		analysisRequest
		Text string `json:"text"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	v, d := router.options.TraceAnalysis.Search(r.Context(), s, traceanalysis.SearchQuery{Handle: h, Text: b.Text, PageSize: b.PageSize, Cursor: b.Cursor})
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	items := make([]searchDTO, 0, len(v.Items))
	for _, x := range v.Items {
		items = append(items, searchDTO{Sequence: x.Sequence, RecordType: x.RecordType, FrameID: x.FrameID, MatchOffset: x.MatchOffset, MatchLength: x.MatchLength, SearchedField: x.SearchedField, ContentID: x.ContentID})
	}
	descriptors := make([]searchContentDescriptorDTO, 0, len(v.ContentDescriptors))
	for _, descriptor := range v.ContentDescriptors {
		descriptors = append(descriptors, searchContentDescriptorDTO{ContentID: descriptor.ContentID, ContentRef: descriptor.ContentRef})
	}
	limitations := make([]searchLimitationDTO, 0, len(v.SearchLimitations))
	for _, limitation := range v.SearchLimitations {
		limitations = append(limitations, searchLimitationDTO{Code: limitation.Code, Message: limitation.Message})
	}
	router.writeEvidenceJSON(w, s, searchPageDTO{pageDTO: pageDTO[searchDTO]{Source: s.Source, TargetScopeID: string(s.TargetScope), Items: items, HasMore: v.HasMore, NextCursor: nullCursor(v.NextCursor)}, ContentDescriptors: descriptors, Search: searchCoverageDTO{Query: b.Text, CaseSensitive: true, Representation: "LOGICAL", SearchedFields: []string{"metadata", "content"}, SemanticContentCoverage: "AVAILABLE_COMPLETE_TEXT", WorkComplete: !v.HasMore, Limitations: limitations}})
}
func (router *Router) traceAnalysisPayloadRange(w http.ResponseWriter, r *http.Request, _ string) {
	router.traceAnalysisRange(w, r, true)
}
func (router *Router) traceAnalysisRawRecordRange(w http.ResponseWriter, r *http.Request, _ string) {
	router.traceAnalysisRange(w, r, false)
}
func (router *Router) traceAnalysisRange(w http.ResponseWriter, r *http.Request, payload bool) {
	var b struct {
		analysisRequest
		ContentRef     string `json:"contentRef"`
		RecordSequence int64  `json:"recordSequence"`
		Start          int64  `json:"start"`
		MaxBytes       int    `json:"maxBytes"`
		ContinueCursor string `json:"continueCursor"`
	}
	if decodeJSONLimit(r, &b, maxTraceAnalysisJSONBody) != nil {
		writeError(w, 400, "INVALID_REQUEST", "Invalid request.")
		return
	}
	s, h, ok := router.resolveAnalysis(w, r, &b.analysisRequest)
	if !ok {
		return
	}
	q := traceanalysis.RangeRequest{Handle: h, ContentRef: b.ContentRef, RecordSequence: b.RecordSequence, Start: b.Start, MaxBytes: b.MaxBytes, ContinueCursor: b.ContinueCursor}
	var v traceanalysis.ByteRangeResult
	var d *consolecore.Error
	if payload {
		if b.ContentRef == "" || b.RecordSequence != 0 {
			writeError(w, 400, "INVALID_REQUEST", "A content reference is required.")
			return
		}
		v, d = router.options.TraceAnalysis.ReadContentRange(r.Context(), s, q)
	} else {
		if b.RecordSequence < 1 || b.ContentRef != "" {
			writeError(w, 400, "INVALID_REQUEST", "A record sequence is required.")
			return
		}
		v, d = router.options.TraceAnalysis.ReadRawRecordRange(r.Context(), s, q)
	}
	if d != nil {
		writeEvidenceDomainError(w, s, d)
		return
	}
	content := string(v.Content)
	result := map[string]any{"source": s.Source, "actualStart": v.ActualStart, "actualEnd": v.ActualEnd, "totalLength": v.TotalLength, "contentType": v.ContentType, "encoding": v.Encoding, "content": content, "hasMore": v.HasMore, "nextCursor": nullCursor(v.NextCursor)}
	if s.Source == evidence.SourceTarget {
		result["targetScopeId"] = string(s.TargetScope)
	}
	router.writeEvidenceJSON(w, s, result)
}

type summaryDTO struct {
	Source                  evidence.Source      `json:"source"`
	TargetScopeID           string               `json:"targetScopeId,omitempty"`
	TraceID                 string               `json:"traceId"`
	SessionID               string               `json:"sessionId"`
	Outcome                 string               `json:"outcome"`
	TerminalFailureID       *string              `json:"terminalFailureId"`
	ConfiguredLimits        *configuredLimitsDTO `json:"configuredLimits"`
	RecordCount             int64                `json:"recordCount"`
	FrameCount              int                  `json:"frameCount"`
	PlanCount               int                  `json:"planCount"`
	AttemptCount            int                  `json:"attemptCount"`
	RetryCount              int                  `json:"retryCount"`
	ValidationCount         int                  `json:"validationCount"`
	FailureCount            int                  `json:"failureCount"`
	PayloadCount            int                  `json:"payloadCount"`
	GapCount                int                  `json:"gapCount"`
	UncertaintyCount        int                  `json:"uncertaintyCount"`
	RootFrameIDs            []string             `json:"rootFrameIds"`
	AttributedUsage         usageValueDTO        `json:"attributedUsage"`
	TerminalUsage           usageValueDTO        `json:"terminalUsage"`
	UnattributedUsage       usageValueDTO        `json:"unattributedUsage"`
	UnframedAttributedUsage usageValueDTO        `json:"unframedAttributedUsage"`
	UsageComplete           bool                 `json:"usageComplete"`
}
type frameDTO struct {
	FrameID                 string        `json:"frameId"`
	ParentFrameID           *string       `json:"parentFrameId"`
	ChildFrameIDs           []string      `json:"childFrameIds"`
	FrameType               string        `json:"frameType"`
	Route                   string        `json:"route"`
	PlanID                  *string       `json:"planId"`
	TaskID                  *string       `json:"taskId"`
	StepNumber              *int64        `json:"stepNumber"`
	ParallelGroup           *string       `json:"parallelGroup"`
	EffectiveConcurrency    *bool         `json:"effectiveConcurrency"`
	OpenedTimestampMillis   int64         `json:"openedTimestampMillis"`
	ClosedTimestampMillis   *int64        `json:"closedTimestampMillis"`
	InclusiveDurationMillis *int64        `json:"inclusiveDurationMillis"`
	SelfDurationMillis      *int64        `json:"selfDurationMillis"`
	DirectUsage             usageValueDTO `json:"directUsage"`
	DirectUsageComplete     bool          `json:"directUsageComplete"`
	DescendantUsage         usageValueDTO `json:"descendantUsage"`
	DescendantUsageComplete bool          `json:"descendantUsageComplete"`
	InclusiveUsage          usageValueDTO `json:"inclusiveUsage"`
	InclusiveUsageComplete  bool          `json:"inclusiveUsageComplete"`
	SkillNames              []string      `json:"skillNames"`
	Outcome                 *string       `json:"outcome"`
	AttemptIDs              []string      `json:"attemptIds"`
	RetrySequenceIDs        []string      `json:"retrySequenceIds"`
	ValidationStatuses      []string      `json:"validationStatuses"`
	FailureIDs              []string      `json:"failureIds"`
	GapKinds                []string      `json:"gapKinds"`
	UncertaintyKinds        []string      `json:"uncertaintyKinds"`
	DirectAttemptCount      int           `json:"directAttemptCount"`
	DirectRetryCount        int           `json:"directRetryCount"`
	DirectValidationCount   int           `json:"directValidationCount"`
	DirectFailureCount      int           `json:"directFailureCount"`
	GapCount                int           `json:"gapCount"`
	UncertaintyCount        int           `json:"uncertaintyCount"`
	compact                 bool
}

func (value frameDTO) MarshalJSON() ([]byte, error) {
	type alias frameDTO
	body, err := json.Marshal(alias(value))
	if err != nil || !value.compact {
		return body, err
	}
	var fields map[string]any
	if err := json.Unmarshal(body, &fields); err != nil {
		return nil, err
	}
	for _, name := range []string{"planId", "taskId", "stepNumber", "parallelGroup", "effectiveConcurrency", "selfDurationMillis", "directUsage", "directUsageComplete", "descendantUsage", "descendantUsageComplete", "inclusiveUsage", "inclusiveUsageComplete", "skillNames", "attemptIds", "retrySequenceIds", "validationStatuses", "failureIds", "gapKinds", "uncertaintyKinds"} {
		delete(fields, name)
	}
	return json.Marshal(fields)
}

type recordDTO struct {
	PlanUpdate       *planUpdateDTO        `json:"planUpdate,omitempty"`
	Sequence         int64                 `json:"sequence"`
	Type             string                `json:"type"`
	FailureID        string                `json:"failureId,omitempty"`
	ValidationStatus string                `json:"validationStatus,omitempty"`
	FrameID          string                `json:"frameId"`
	ParentFrameID    string                `json:"parentFrameId"`
	FrameType        string                `json:"frameType"`
	Route            string                `json:"route"`
	ThreadName       string                `json:"threadName"`
	TimestampMillis  int64                 `json:"timestampMillis"`
	Representation   string                `json:"representation"`
	IsChunk          bool                  `json:"isChunk"`
	IsEnvelope       bool                  `json:"isEnvelope"`
	Content          *contentDescriptorDTO `json:"content,omitempty"`
	Plan             *planReferenceDTO     `json:"plan,omitempty"`
}
type contentDescriptorDTO struct {
	Role              string `json:"role"`
	ContentType       string `json:"contentType"`
	Encoding          string `json:"encoding"`
	RetainedBytes     int64  `json:"retainedBytes"`
	Available         bool   `json:"available"`
	Complete          bool   `json:"complete"`
	InlineEligibility bool   `json:"inlineEligibility"`
	InlineOmission    string `json:"inlineOmission,omitempty"`
	ContentRef        string `json:"contentRef,omitempty"`
	InlineContent     string `json:"inlineContent,omitempty"`
}
type planReferenceDTO struct {
	PlanID         string `json:"planId"`
	CapabilityName string `json:"capabilityName"`
}

type planTaskDTO struct {
	StepNumber           int64    `json:"stepNumber"`
	TaskID               string   `json:"taskId"`
	Title                string   `json:"title"`
	Status               string   `json:"status"`
	CapabilityName       *string  `json:"capabilityName"`
	Intent               *string  `json:"intent"`
	DependsOn            []string `json:"dependsOn"`
	ExpectedOutputs      []string `json:"expectedOutputs"`
	ParallelGroup        *string  `json:"parallelGroup"`
	Note                 *string  `json:"note"`
	AssignedFrameID      *string  `json:"assignedFrameId"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	FailureIDs           []string `json:"failureIds"`
}

type planExecutionUnitDTO struct {
	Position             int64    `json:"position"`
	ParallelGroup        *string  `json:"parallelGroup"`
	TaskIDs              []string `json:"taskIds"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	ObservedOverlap      *bool    `json:"observedOverlap"`
}

type planTransitionDTO struct {
	Sequence             int64    `json:"sequence"`
	Kind                 string   `json:"kind"`
	TaskIDs              []string `json:"taskIds"`
	ParallelGroup        *string  `json:"parallelGroup"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	Outcome              string   `json:"outcome"`
}

type planDTO struct {
	PlanID           string                 `json:"planId"`
	CapabilityName   string                 `json:"capabilityName"`
	CreatedAt        string                 `json:"createdAt"`
	Status           string                 `json:"status"`
	CreationSequence int64                  `json:"creationSequence"`
	TraceRootFrameID string                 `json:"traceRootFrameId"`
	MissionFrameID   string                 `json:"missionFrameId"`
	PlanningFrameID  string                 `json:"planningFrameId"`
	AttemptID        string                 `json:"attemptId"`
	RetrySequenceID  string                 `json:"retrySequenceId"`
	Tasks            []planTaskDTO          `json:"tasks"`
	ExecutionUnits   []planExecutionUnitDTO `json:"executionUnits"`
	Transitions      []planTransitionDTO    `json:"transitions"`
}

func planDTOValue(value traceanalysis.PlanSummary) planDTO {
	tasks := make([]planTaskDTO, 0, len(value.Tasks))
	for _, task := range value.Tasks {
		tasks = append(tasks, planTaskDTO{StepNumber: task.StepNumber, TaskID: task.TaskID, Title: task.Title, Status: task.Status, CapabilityName: task.CapabilityName, Intent: task.Intent, DependsOn: append([]string{}, task.DependsOn...), ExpectedOutputs: append([]string{}, task.ExpectedOutputs...), ParallelGroup: task.ParallelGroup, Note: task.Note, AssignedFrameID: task.AssignedFrameID, EffectiveConcurrency: task.EffectiveConcurrency, FailureIDs: append([]string{}, task.FailureIDs...)})
	}
	units := make([]planExecutionUnitDTO, 0, len(value.ExecutionUnits))
	for _, unit := range value.ExecutionUnits {
		units = append(units, planExecutionUnitDTO{Position: unit.Position, ParallelGroup: unit.ParallelGroup, TaskIDs: append([]string{}, unit.TaskIDs...), EffectiveConcurrency: unit.EffectiveConcurrency, ObservedOverlap: unit.ObservedOverlap})
	}
	transitions := make([]planTransitionDTO, 0, len(value.Transitions))
	for _, transition := range value.Transitions {
		transitions = append(transitions, planTransitionDTO{Sequence: transition.Sequence, Kind: transition.Kind, TaskIDs: append([]string{}, transition.TaskIDs...), ParallelGroup: transition.ParallelGroup, EffectiveConcurrency: transition.EffectiveConcurrency, Outcome: transition.Outcome})
	}
	return planDTO{PlanID: value.PlanID, CapabilityName: value.CapabilityName, CreatedAt: value.CreatedAt, Status: value.Status, CreationSequence: value.CreationSequence, TraceRootFrameID: value.TraceRootFrameID, MissionFrameID: value.MissionFrameID, PlanningFrameID: value.PlanningFrameID, AttemptID: value.AttemptID, RetrySequenceID: value.RetrySequenceID, Tasks: tasks, ExecutionUnits: units, Transitions: transitions}
}

type pageDTO[T any] struct {
	Source        evidence.Source `json:"source"`
	TargetScopeID string          `json:"targetScopeId,omitempty"`
	Items         []T             `json:"items"`
	HasMore       bool            `json:"hasMore"`
	NextCursor    *string         `json:"nextCursor"`
}
type usageDTO struct {
	Source             evidence.Source `json:"source"`
	TargetScopeID      string          `json:"targetScopeId,omitempty"`
	Attributed         usageValueDTO   `json:"attributed"`
	Unattributed       usageValueDTO   `json:"unattributed"`
	UnframedAttributed usageValueDTO   `json:"unframedAttributed"`
	Terminal           usageValueDTO   `json:"terminal"`
}
type usageValueDTO struct {
	PromptUnits     int64 `json:"promptUnits"`
	CompletionUnits int64 `json:"completionUnits"`
	TotalUnits      int64 `json:"totalUnits"`
}

func usageDTOValue(v traceanalysis.Usage) usageValueDTO {
	return usageValueDTO{PromptUnits: v.PromptUnits, CompletionUnits: v.CompletionUnits, TotalUnits: v.TotalUnits}
}

type attemptDTO struct {
	RetrySequenceID       string        `json:"retrySequenceId"`
	AttemptID             string        `json:"attemptId"`
	AttemptNumber         int64         `json:"attemptNumber"`
	AttemptReason         string        `json:"attemptReason"`
	ProviderAttemptNumber int64         `json:"providerAttemptNumber"`
	Outcome               string        `json:"outcome"`
	FailureClassification string        `json:"failureClassification,omitempty"`
	FailureCategory       string        `json:"failureCategory,omitempty"`
	RetryDecision         string        `json:"retryDecision,omitempty"`
	RetryDelayMillis      int64         `json:"retryDelayMillis"`
	RetryDelaySource      string        `json:"retryDelaySource,omitempty"`
	HTTPStatus            int64         `json:"httpStatus,omitempty"`
	ProviderErrorType     string        `json:"providerErrorType,omitempty"`
	ProviderErrorCode     string        `json:"providerErrorCode,omitempty"`
	ContentRef            string        `json:"contentRef,omitempty"`
	Usage                 usageValueDTO `json:"usage"`
	UsageComplete         bool          `json:"usageComplete"`
}
type retryDTO struct {
	RetrySequenceID string        `json:"retrySequenceId"`
	Usage           usageValueDTO `json:"usage"`
	UsageComplete   bool          `json:"usageComplete"`
}
type validationDTO struct {
	Status          string `json:"status"`
	RetrySequenceID string `json:"retrySequenceId"`
	AttemptID       string `json:"attemptId"`
	AttemptNumber   int64  `json:"attemptNumber"`
}
type failureDTO struct {
	FailureID        string                    `json:"failureId"`
	Terminal         bool                      `json:"terminal"`
	Sequence         int64                     `json:"sequence"`
	TimestampMillis  int64                     `json:"timestampMillis"`
	RecordType       string                    `json:"recordType"`
	FrameID          string                    `json:"frameId"`
	Route            string                    `json:"route"`
	AttemptID        string                    `json:"attemptId"`
	RetrySequenceID  string                    `json:"retrySequenceId"`
	ValidationStatus string                    `json:"validationStatus"`
	ExceptionType    string                    `json:"exceptionType,omitempty"`
	ContextSummary   string                    `json:"contextSummary,omitempty"`
	Diagnostics      []diagnosticDescriptorDTO `json:"diagnostics,omitempty"`
}
type diagnosticDescriptorDTO struct {
	Ordinal           int    `json:"ordinal"`
	Kind              string `json:"kind"`
	ContentType       string `json:"contentType"`
	Truncated         bool   `json:"truncated"`
	CaptureLimitBytes int    `json:"captureLimitBytes"`
	DecodedBytes      int    `json:"decodedBytes"`
}
type failureDiagnosticDTO struct {
	Source        evidence.Source         `json:"source"`
	TargetScopeID string                  `json:"targetScopeId,omitempty"`
	FailureID     string                  `json:"failureId"`
	Descriptor    diagnosticDescriptorDTO `json:"descriptor"`
	Text          string                  `json:"text"`
}

type configuredLimitsDTO struct {
	MaxSkillInvocations int64 `json:"maxSkillInvocations"`
	MaxToolInvocations  int64 `json:"maxToolInvocations"`
	MaxLinterRetries    int64 `json:"maxLinterRetries"`
	MaxModelCalls       int64 `json:"maxModelCalls"`
	MaxProviderAttempts int64 `json:"maxProviderAttempts"`
	MaxUsageUnits       int64 `json:"maxUsageUnits"`
}

func configuredLimitsDTOValue(v *traceanalysis.ConfiguredLimits) *configuredLimitsDTO {
	if v == nil {
		return nil
	}
	return &configuredLimitsDTO{MaxSkillInvocations: v.MaxSkillInvocations, MaxToolInvocations: v.MaxToolInvocations,
		MaxLinterRetries: v.MaxLinterRetries, MaxModelCalls: v.MaxModelCalls,
		MaxProviderAttempts: v.MaxProviderAttempts, MaxUsageUnits: v.MaxUsageUnits}
}

type gapDTO struct {
	Kind      string `json:"kind"`
	FrameID   string `json:"frameId"`
	AttemptID string `json:"attemptId"`
}
type uncertaintyDTO struct {
	Kind    string `json:"kind"`
	FrameID string `json:"frameId"`
}
type searchDTO struct {
	Sequence      int64  `json:"sequence"`
	RecordType    string `json:"recordType"`
	FrameID       string `json:"frameId"`
	MatchOffset   int64  `json:"matchOffset"`
	MatchLength   int    `json:"matchLength"`
	SearchedField string `json:"searchedField"`
	ContentID     string `json:"contentId,omitempty"`
}
type searchContentDescriptorDTO struct {
	ContentID  string `json:"contentId"`
	ContentRef string `json:"contentRef"`
}
type searchLimitationDTO struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}
type searchCoverageDTO struct {
	Query                   string                `json:"query"`
	CaseSensitive           bool                  `json:"caseSensitive"`
	Representation          string                `json:"representation"`
	SearchedFields          []string              `json:"searchedFields"`
	SemanticContentCoverage string                `json:"semanticContentCoverage"`
	WorkComplete            bool                  `json:"workComplete"`
	Limitations             []searchLimitationDTO `json:"limitations"`
}
type searchPageDTO struct {
	pageDTO[searchDTO]
	ContentDescriptors []searchContentDescriptorDTO `json:"contentDescriptors"`
	Search             searchCoverageDTO            `json:"search"`
}

func nullCursor(v string) *string {
	if v == "" {
		return nil
	}
	return &v
}

func recordDTOValue(x traceanalysis.RecordSummary) recordDTO {
	item := recordDTO{Sequence: x.Sequence, Type: x.Type, FailureID: x.FailureID, ValidationStatus: x.ValidationStatus, FrameID: x.FrameID, ParentFrameID: x.ParentFrameID, FrameType: x.FrameType, Route: x.Route, ThreadName: x.ThreadName, TimestampMillis: x.TimestampMillis, Representation: x.Representation, IsChunk: x.IsChunk, IsEnvelope: x.IsEnvelope}
	if x.Content != nil {
		inline := string(x.Content.InlineContent)
		if x.Content.Encoding == traceanalysis.ContentEncodingBinary && len(x.Content.InlineContent) > 0 {
			inline = base64.StdEncoding.EncodeToString(x.Content.InlineContent)
		}
		item.Content = &contentDescriptorDTO{Role: string(x.Content.Role), ContentType: x.Content.ContentType, Encoding: string(x.Content.Encoding), RetainedBytes: x.Content.RetainedBytes, Available: x.Content.Available, Complete: x.Content.Complete, InlineEligibility: x.Content.InlineEligibility, InlineOmission: string(x.Content.InlineOmission), ContentRef: x.Content.ContentRef, InlineContent: inline}
	}
	if x.Facts.Plan != nil {
		p := x.Facts.Plan
		item.Plan = &planReferenceDTO{PlanID: p.PlanID, CapabilityName: p.CapabilityName}
	}
	if x.Facts.PlanUpdate != nil {
		update := x.Facts.PlanUpdate
		item.PlanUpdate = &planUpdateDTO{PreviousSequence: update.PreviousSequence, Availability: update.Availability}
		if update.Changes != nil {
			changes := make([]planFieldChangeDTO, 0, len(*update.Changes))
			for _, change := range *update.Changes {
				changes = append(changes, planFieldChangeDTO{TaskID: change.TaskID, Field: change.Field, Before: change.Before, After: change.After})
			}
			item.PlanUpdate.Changes = &changes
		}
	}
	return item
}

// Reserve all brackets, the writeJSON newline, and a request-limit-sized cursor.
func recordPageOverhead(ref evidence.Reference) int {
	cursor := strings.Repeat("x", maxTraceAnalysisJSONBody)
	encoded, _ := json.Marshal(pageDTO[recordDTO]{Source: ref.Source, TargetScopeID: string(ref.TargetScope), Items: []recordDTO{}, HasMore: true, NextCursor: &cursor})
	return len(encoded) + 1
}

func boundedRecordDTOValue(x traceanalysis.RecordSummary, ref evidence.Reference) recordDTO {
	item := recordDTOValue(x)
	encoded, _ := json.Marshal(item)
	if recordPageOverhead(ref)+len(encoded) > traceanalysis.MaxDescriptorResponseBytes && item.PlanUpdate != nil {
		item.PlanUpdate = &planUpdateDTO{PreviousSequence: item.PlanUpdate.PreviousSequence, Availability: "LIMIT_EXCEEDED"}
	}
	return item
}

func newRecordPageAdmission(ref evidence.Reference) func(traceanalysis.RecordSummary) bool {
	used, count := recordPageOverhead(ref), 0
	return func(item traceanalysis.RecordSummary) bool {
		encoded, err := json.Marshal(boundedRecordDTOValue(item, ref))
		separator := 0
		if count > 0 {
			separator = 1
		}
		if err != nil || used+separator+len(encoded) > traceanalysis.MaxDescriptorResponseBytes {
			return false
		}
		used += separator + len(encoded)
		count++
		return true
	}
}

type planUpdateDTO struct {
	PreviousSequence int64                 `json:"previousSequence"`
	Availability     string                `json:"availability"`
	Changes          *[]planFieldChangeDTO `json:"changes,omitempty"`
}
type planFieldChangeDTO struct {
	TaskID string  `json:"taskId,omitempty"`
	Field  string  `json:"field"`
	Before *string `json:"before"`
	After  *string `json:"after"`
}
