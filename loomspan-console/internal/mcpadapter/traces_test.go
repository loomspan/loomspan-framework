package mcpadapter

import (
	"context"
	"encoding/json"
	"reflect"
	"regexp"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceinventory"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceresolution"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func stringPointer(value string) *string { return &value }
func int64Pointer(value int64) *int64    { return &value }

func TestTraceRangeTextFallbackPreservesContent(t *testing.T) {
	content := strings.Repeat("unique-large-content", 4096)
	text := traceRangeText(rangeResult{ActualStart: 0, ActualEnd: int64(len(content)), TotalLength: int64(len(content) + 16), ContentType: "application/octet-stream", Encoding: "BASE64", Content: content, HasMore: true})
	if strings.Count(text, content) != 1 || !strings.Contains(text, `"actualStart":0`) || !strings.Contains(text, `"hasMore":true`) {
		t.Fatalf("range fallback omitted diagnostic facts: %q", text)
	}
	if overhead := len(text) - len(content); overhead > 4<<10 {
		t.Fatalf("range fallback overhead=%d bytes max=%d", overhead, 4<<10)
	}
}

func TestTraceRangeTextFallbackContainsExactlyTheReturnedFacts(t *testing.T) {
	value := rangeResult{Evidence: evidenceDTO{TraceID: "trace", SessionID: "session", ObservedAt: time.Unix(1, 2).UTC()}, ActualStart: 3, ActualEnd: 7, TotalLength: 9, ContentType: "text/plain", Encoding: "UTF8", Content: "<inert>\x00\n", HasMore: true, Continuation: "next"}
	var decoded rangeResult
	if err := json.Unmarshal([]byte(traceRangeText(value)), &decoded); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(decoded, value) {
		t.Fatalf("fallback facts=%#v want=%#v", decoded, value)
	}
}

func TestTraceSummaryFallbackRendersEveryReturnedFactMechanically(t *testing.T) {
	terminalFailureID := "failure-terminal"
	value := getTraceResult{Evidence: evidenceDTO{TraceID: "trace", SessionID: "session"}, Summary: traceSummaryDTO{
		Outcome: "FAILED", TerminalFailureID: &terminalFailureID, RecordCount: 4, RecordCountsByType: map[string]int64{
			string(traceanalysis.RecordTraceCompleted):   1,
			string(traceanalysis.RecordModelRequestSent): 2,
			string(traceanalysis.RecordTraceStarted):     1,
		},
	}}
	assertMechanicalTraceFallback(t, value, traceSummaryText(value))
}

func TestTraceNavigationTextFallbacksContainEveryAcceptedItem(t *testing.T) {
	large := strings.Repeat("x", maxTraceTokenLength)
	frames := queryFramesResult{Evidence: evidenceDTO{TraceID: large}, Projection: "COMPACT", HasMore: true, Continuation: large}
	records := queryRecordsResult{Evidence: evidenceDTO{TraceID: large}, HasMore: true, Continuation: large}
	list := listTracesResult{HasMore: true, Continuation: large}
	for range 64 {
		frames.Items = append(frames.Items, frameDTO{FrameID: large, Route: large})
		records.Items = append(records.Items, recordDTO{FrameID: large, Content: &contentDescriptorDTO{ContentRef: large}})
		traceID := large
		list.Items = append(list.Items, traceInventoryItemDTO{TraceID: large, SessionID: &traceID})
	}
	for name, test := range map[string]struct{ value, marker string }{
		"frames": {traceFramesText(frames), `"frameId":`}, "records": {traceRecordsText(records), `"sequence":`}, "inventory": {traceListText(list), `"traceId":`},
	} {
		if strings.Contains(test.value, "additional structured items omitted") || strings.Count(test.value, test.marker) < 64 {
			t.Fatalf("%s fallback did not contain every accepted item", name)
		}
	}
}

func TestSearchTextFallbackRetainsContentReferenceNavigation(t *testing.T) {
	evidence := evidenceDTO{TraceID: "trace-1", SessionID: "session-1", ObservedAt: time.Date(2026, 9, 1, 12, 0, 0, 0, time.UTC)}
	descriptors := []searchContentDescriptorDTO{{ContentID: "c1", ContentRef: "opaque-content-reference"}}
	searchText := traceRecordsText(queryRecordsResult{
		Evidence:           evidence,
		Matches:            []searchMatchDTO{{Sequence: 7, RecordType: "MODEL_RESPONSE_RECEIVED", MatchOffset: 2, MatchLength: 6, SearchedField: "content", ContentID: "c1"}},
		ContentDescriptors: &descriptors,
		Search:             &searchCoverageDTO{Query: "needle", CaseSensitive: true, Representation: "LOGICAL", SearchedFields: []string{"metadata", "content"}, SemanticContentCoverage: "AVAILABLE_COMPLETE_TEXT", WorkComplete: true, Limitations: []traceLimitationDTO{}},
	})
	for _, want := range []string{`"contentId":"c1"`, `"contentRef":"opaque-content-reference"`} {
		if !strings.Contains(searchText, want) {
			t.Fatalf("search fallback omitted %q: %s", want, searchText)
		}
	}
}

func TestRecordTextFallbackIncludesReturnedInlineContentAndOmission(t *testing.T) {
	inline := recordFallbackLine(recordDTO{Sequence: 1, Type: "MODEL_RESPONSE_RECEIVED", ValidationStatus: "retrying", Content: &contentDescriptorDTO{
		RetainedBytes: 24, InlineEligibility: true, ContentRef: "opaque", InlineContent: "first\nsecond\"",
	}})
	if !strings.Contains(inline, `"validationStatus":"retrying"`) || !strings.Contains(inline, `"inlineEligibility":true`) || !strings.Contains(inline, `"inlineContent":"first\nsecond\""`) {
		t.Fatalf("inline fallback omitted or unsafely rendered content: %q", inline)
	}
	if strings.Count(inline, "\n") != 1 {
		t.Fatalf("inline fallback is not one escaped record line: %q", inline)
	}

	omitted := recordFallbackLine(recordDTO{Sequence: 2, Type: "MODEL_RESPONSE_RECEIVED", Content: &contentDescriptorDTO{
		RetainedBytes: 8193, InlineEligibility: true, ContentRef: "opaque", InlineOmission: "PER_VALUE_LIMIT",
	}})
	if !strings.Contains(omitted, `"inlineEligibility":true`) || !strings.Contains(omitted, `"inlineOmission":"PER_VALUE_LIMIT"`) || strings.Contains(omitted, `"inlineContent":`) {
		t.Fatalf("omission fallback does not match the descriptor: %q", omitted)
	}
	if strings.Contains(omitted, `"validationStatus":`) {
		t.Fatalf("absent validation status was rendered: %q", omitted)
	}
}

func TestModelAttemptFailedFallbackProjectsOnlyRecordedNormalizedFacts(t *testing.T) {
	record := mapRecord(traceanalysis.RecordSummary{
		Sequence: 7, Type: string(traceanalysis.RecordModelAttemptFailed), FrameID: "model", Representation: "PHYSICAL",
		Facts: traceanalysis.RecordFacts{Attempts: []traceanalysis.AttemptSummary{{
			AttemptID: "attempt\n\"1", RetrySequenceID: "retry-1", AttemptNumber: 2, AttemptReason: "PROVIDER_RETRY",
			ProviderAttemptNumber: 3, FailureClassification: "TRANSIENT", FailureCategory: "TIMEOUT",
			RetryDecision: "RETRY", RetryDelayMillis: 419, RetryDelaySource: "BACKOFF",
			HTTPStatus: 504, ProviderErrorType: "type\n\"unsafe", ProviderErrorCode: "code-1",
		}}},
	})
	before, err := json.Marshal(record)
	if err != nil {
		t.Fatal(err)
	}
	line := recordFallbackLine(record)
	after, err := json.Marshal(record)
	if err != nil || string(before) != string(after) {
		t.Fatalf("fallback changed structured result: before=%s after=%s err=%v", before, after, err)
	}
	wants := []string{
		`"retrySequenceId":"retry-1"`, `"attemptId":"attempt\n\"1"`, `"attemptNumber":2`, `"attemptReason":"PROVIDER_RETRY"`,
		`"providerAttemptNumber":3`, `"failureClassification":"TRANSIENT"`, `"failureCategory":"TIMEOUT"`, `"retryDecision":"RETRY"`,
		`"retryDelayMillis":419`, `"retryDelaySource":"BACKOFF"`, `"httpStatus":504`, `"providerErrorType":"type\n\"unsafe"`, `"providerErrorCode":"code-1"`,
	}
	position := -1
	for _, want := range wants {
		next := strings.Index(line, want)
		if next <= position {
			t.Fatalf("attempt fallback missing or out of order %q: %q", want, line)
		}
		position = next
	}
	if strings.Count(line, "\n") != 1 {
		t.Fatalf("attempt fallback is not one escaped physical-record line: %q", line)
	}
	for _, forbidden := range []string{"cause=", "errorReason=", "exceptionMessage=", "diagnostic"} {
		if strings.Contains(line, forbidden) {
			t.Fatalf("attempt fallback invented %q: %q", forbidden, line)
		}
	}

	absent := record
	absent.Attempts[0].HTTPStatus = 0
	absent.Attempts[0].ProviderErrorType = ""
	absent.Attempts[0].ProviderErrorCode = ""
	absentLine := recordFallbackLine(absent)
	for _, omitted := range []string{`"httpStatus":`, `"providerErrorType":`, `"providerErrorCode":`, `unknown`} {
		if strings.Contains(absentLine, omitted) {
			t.Fatalf("absent optional field rendered as %q: %q", omitted, absentLine)
		}
	}
}

func TestMCPFallbacksNeverContainPointerAddresses(t *testing.T) {
	outcome, parent := "FAILED", "root"
	when := time.Date(2026, 8, 19, 9, 10, 11, 0, time.FixedZone("west", -7*60*60))
	closed, inclusive, self := int64(9), int64(8), int64(7)
	texts := []string{
		traceListText(listTracesResult{Items: []traceInventoryItemDTO{{TraceID: "trace", Outcome: &outcome, FinalizedAt: &when, AcquiredAt: &when, ImportedAt: &when}}}),
		traceFramesText(queryFramesResult{Items: []frameDTO{{FrameID: "child", ParentFrameID: &parent, ClosedTimestampMillis: &closed, InclusiveDurationMillis: &inclusive, SelfDurationMillis: &self}}}),
		traceRecordsText(queryRecordsResult{Items: []recordDTO{{Sequence: 1, Type: "STEP_FAILED"}}}),
		traceRangeText(rangeResult{Content: "ok"}),
	}
	address := regexp.MustCompile(`0x[0-9a-fA-F]+`)
	for _, text := range texts {
		if address.MatchString(text) {
			t.Fatalf("fallback contains pointer address: %q", text)
		}
	}
}

func TestRepresentativeStructuredNavigationResponsesMeetBudgets(t *testing.T) {
	frames := queryFramesResult{Evidence: evidenceDTO{TraceID: "trace-30"}, Projection: "COMPACT", Items: make([]frameDTO, 30)}
	for i := range frames.Items {
		frames.Items[i] = frameDTO{FrameID: strings.Repeat("f", 64), Route: strings.Repeat("r", 128), FrameType: "SKILL_EXECUTION", DirectAttemptCount: 2, DirectRetryCount: 1}
	}
	records := queryRecordsResult{Evidence: evidenceDTO{TraceID: "trace-descriptors"}, Items: make([]recordDTO, 64)}
	for i := range records.Items {
		records.Items[i] = recordDTO{Sequence: int64(i + 1), Type: "PLAN_UPDATED", Content: &contentDescriptorDTO{Role: "DATA", ContentType: "application/json", Encoding: "UTF8", RetainedBytes: 8192, Available: true, Complete: true, ContentRef: strings.Repeat("c", 512)}}
	}
	for name, value := range map[string]struct {
		value any
		max   int
	}{"frames": {frames, traceanalysis.MaxCompactResponseBytes}, "records": {records, traceanalysis.MaxDescriptorResponseBytes}} {
		body, err := json.Marshal(value.value)
		if err != nil || len(body) > value.max {
			t.Fatalf("%s bytes=%d max=%d err=%v", name, len(body), value.max, err)
		}
	}
}

func TestCompactFrameSerializationRetainsHierarchyWithoutEmptyDetailedFields(t *testing.T) {
	summary := traceanalysis.FrameSummary{
		FrameID: "root", ChildFrameIDs: []string{"child"}, FrameType: "ROOT_MISSION", Route: "root",
		Outcome: stringPointer("completed"), InclusiveDurationMillis: int64Pointer(42), DirectAttemptCount: 2, DirectFailureCount: 1,
	}
	compact := mapFrame(summary, traceanalysis.FrameProjectionCompact)
	body, err := json.Marshal(compact)
	if err != nil {
		t.Fatal(err)
	}
	text := string(body)
	for _, want := range []string{`"childFrameIds":["child"]`} {
		if !strings.Contains(text, want) {
			t.Fatalf("compact frame missing %s: %s", want, text)
		}
	}
	for _, forbidden := range []string{`"openedTimestampMillis"`, `"inclusiveDurationMillis"`, `"outcome"`, `"skillNames"`, `"attemptIds"`, `"retrySequenceIds"`, `"validationStatuses"`, `"failureIds"`, `"gapKinds"`, `"uncertaintyKinds"`, `"directUsage"`, `"directAttemptCount"`, `"directFailureCount"`, `"gapCount"`, `"selfDurationMillis"`} {
		if strings.Contains(text, forbidden) {
			t.Fatalf("compact frame retained %s: %s", forbidden, text)
		}
	}
	detailedBody, err := json.Marshal(mapFrame(summary, traceanalysis.FrameProjectionDetailed))
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{`"skillNames":[]`, `"attemptIds":[]`, `"failureIds":[]`, `"gapKinds":[]`} {
		if !strings.Contains(string(detailedBody), want) {
			t.Fatalf("detailed frame omitted %s: %s", want, detailedBody)
		}
	}
}

func TestFrameFallbackDistinguishesCompactOmissionFromDetailedDuration(t *testing.T) {
	duration := int64(42)
	summary := traceanalysis.FrameSummary{FrameID: "frame", ChildFrameIDs: []string{}, FrameType: "MODEL_CALL", OpenedTimestampMillis: 10, InclusiveDurationMillis: &duration, SelfDurationMillis: &duration, DirectAttemptCount: 2, DirectRetryCount: 1, GapCount: 1}
	compact := traceFramesText(queryFramesResult{Projection: string(traceanalysis.FrameProjectionCompact), Items: []frameDTO{mapFrame(summary, traceanalysis.FrameProjectionCompact)}})
	for _, omitted := range []string{"openedTimestampMillis", "inclusiveDurationMillis", "selfDurationMillis", "directAttemptCount", "directRetryCount", "gapCount"} {
		if strings.Contains(compact, omitted) {
			t.Fatalf("compact fallback exposed detailed field %q: %s", omitted, compact)
		}
	}
	if !strings.Contains(compact, `"projection":"COMPACT"`) {
		t.Fatalf("compact fallback=%q", compact)
	}
	detailed := traceFramesText(queryFramesResult{Projection: string(traceanalysis.FrameProjectionDetailed), Items: []frameDTO{mapFrame(summary, traceanalysis.FrameProjectionDetailed)}})
	if !strings.Contains(detailed, `"projection":"DETAILED"`) || !strings.Contains(detailed, `"openedTimestampMillis":10`) || !strings.Contains(detailed, `"inclusiveDurationMillis":42`) || !strings.Contains(detailed, `"selfDurationMillis":42`) || !strings.Contains(detailed, `"directAttemptCount":2`) || !strings.Contains(detailed, `"directRetryCount":1`) || !strings.Contains(detailed, `"gapCount":1`) {
		t.Fatalf("detailed fallback=%q", detailed)
	}
}

func assertMechanicalTraceFallback(t *testing.T, value any, fallback string) {
	t.Helper()
	encoded, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	if fallback != string(encoded)+"\n" {
		t.Fatalf("fallback differs from structured facts:\n got: %s\nwant: %s", fallback, encoded)
	}
}

func TestDetailedFrameMappingPreservesUsageWhenDurationIsUnavailable(t *testing.T) {
	summary := traceanalysis.FrameSummary{
		FrameID: "incomplete", FrameType: "MODEL_CALL",
		DirectUsage: traceanalysis.Usage{PromptUnits: 5, CompletionUnits: 2, TotalUnits: 7}, DirectUsageComplete: true,
		DescendantUsage: traceanalysis.Usage{PromptUnits: 3, CompletionUnits: 1, TotalUnits: 4}, DescendantUsageComplete: true,
		InclusiveUsage: traceanalysis.Usage{PromptUnits: 8, CompletionUnits: 3, TotalUnits: 11}, InclusiveUsageComplete: true,
	}
	mapped := mapFrame(summary, traceanalysis.FrameProjectionDetailed)
	if mapped.InclusiveDurationMillis != nil || mapped.DirectUsage == nil || mapped.DescendantUsage == nil || mapped.InclusiveUsage == nil {
		t.Fatalf("detailed mapping dropped duration-independent usage: %+v", mapped)
	}
	if mapped.DirectUsage.TotalUnits != 7 || mapped.DescendantUsage.TotalUnits != 4 || mapped.InclusiveUsage.TotalUnits != 11 {
		t.Fatalf("detailed usage changed: %+v", mapped)
	}
}

func TestBinaryInlineContentUsesBase64Transport(t *testing.T) {
	mapped := mapRecord(traceanalysis.RecordSummary{Content: &traceanalysis.ContentDescriptor{Encoding: traceanalysis.ContentEncodingBinary, InlineContent: []byte{0xff, 0x00, 0x80}}})
	if mapped.Content == nil || mapped.Content.InlineContent != "/wCA" {
		t.Fatalf("content=%+v", mapped.Content)
	}
}

func TestQueryTracePlansMapsWholeAuthoritativePlansAndNullableFacts(t *testing.T) {
	traceContext := traceanalysis.TraceContext{Evidence: evidence.ForImported(), Handle: artifact.Handle("handle"), TraceID: "trace-1", SessionID: "session-1"}
	group, assigned := "group-a", "frame-task"
	concurrent, noOverlap := true, false
	analysis := &fakeTraceAnalysis{plans: traceanalysis.Page[traceanalysis.PlanSummary]{Context: traceContext, Items: []traceanalysis.PlanSummary{{Context: traceContext, PlanID: "plan-1", CapabilityName: "Investigate", CreatedAt: "2026-09-01T00:00:00Z", Status: "COMPLETED", CreationSequence: 5, TraceRootFrameID: "root", MissionFrameID: "mission", PlanningFrameID: "planning", Tasks: []traceanalysis.PlanTaskSummary{{StepNumber: 1, TaskID: "task-1", Title: "Collect", Status: "COMPLETED", DependsOn: []string{}, ExpectedOutputs: []string{"evidence"}, ParallelGroup: &group, AssignedFrameID: &assigned, EffectiveConcurrency: &concurrent, FailureIDs: []string{}}}, ExecutionUnits: []traceanalysis.PlanExecutionUnitSummary{{Position: 1, ParallelGroup: &group, TaskIDs: []string{"task-1"}, EffectiveConcurrency: &concurrent, ObservedOverlap: &noOverlap}}, Transitions: []traceanalysis.PlanTransitionSummary{{Sequence: 6, Kind: "TASK_STARTED", TaskIDs: []string{"task-1"}, ParallelGroup: &group, EffectiveConcurrency: &concurrent}}}}}}
	options := newMCPTestOptions(t, nil)
	options.TraceAnalysis = analysis
	options.TraceResolver = &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: traceContext.Handle, Metadata: artifact.TraceMetadata{TraceID: "trace-1", SessionID: "session-1"}}, ref: traceContext.Evidence}
	call, envelope, err := handleQueryTracePlans(context.Background(), options, queryTracePlansInput{TraceID: "trace-1", PlanID: "plan-1", PageSize: 1})
	if err != nil || call.IsError || envelope.Result == nil || len(envelope.Result.Items) != 1 {
		t.Fatalf("result=%#v err=%v", envelope, err)
	}
	plan := envelope.Result.Items[0]
	if plan.PlanID != "plan-1" || len(plan.Tasks) != 1 || plan.Tasks[0].AssignedFrameID == nil || len(plan.ExecutionUnits) != 1 || plan.ExecutionUnits[0].ObservedOverlap == nil || *plan.ExecutionUnits[0].ObservedOverlap || analysis.planQuery.PlanID != "plan-1" {
		t.Fatalf("plan=%#v query=%#v", plan, analysis.planQuery)
	}
	if !strings.Contains(call.Content[0].(*mcp.TextContent).Text, `"observedOverlap":false`) {
		t.Fatalf("fallback omitted false overlap: %s", call.Content[0].(*mcp.TextContent).Text)
	}
}

func TestRecordResultSerializationKeepsOrdinaryAndSearchModesExclusive(t *testing.T) {
	ordinary, err := json.Marshal(queryRecordsResult{Evidence: evidenceDTO{TraceID: "trace", SessionID: "session"}, Items: []recordDTO{}})
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(ordinary), `"items":[]`) || strings.Contains(string(ordinary), `"matches"`) || strings.Contains(string(ordinary), `"contentDescriptors"`) || strings.Contains(string(ordinary), `"search"`) {
		t.Fatalf("ordinary=%s", ordinary)
	}
	descriptors := []searchContentDescriptorDTO{}
	search, err := json.Marshal(queryRecordsResult{Evidence: evidenceDTO{TraceID: "trace", SessionID: "session"}, Matches: []searchMatchDTO{}, ContentDescriptors: &descriptors, Search: &searchCoverageDTO{Limitations: []traceLimitationDTO{}}})
	if err != nil {
		t.Fatal(err)
	}
	for _, required := range []string{`"matches":[]`, `"contentDescriptors":[]`, `"search":`} {
		if !strings.Contains(string(search), required) {
			t.Fatalf("search omitted %s: %s", required, search)
		}
	}
	if strings.Contains(string(search), `"items"`) {
		t.Fatalf("search retained ordinary items: %s", search)
	}
}

type fakeTraceInventory struct {
	result traceinventory.Result
	query  traceinventory.Query
	calls  int
}

func (fake *fakeTraceInventory) List(_ context.Context, query traceinventory.Query) (traceinventory.Result, *consolecore.Error) {
	fake.calls++
	fake.query = query
	result := fake.result
	if query.Admit != nil {
		accepted := len(result.Items)
		for index, item := range result.Items {
			if query.Admit(item) {
				continue
			}
			if index == 0 {
				return traceinventory.Result{}, consolecore.NewError(consolecore.CodeLimitExceeded, "oversized inventory item", "", consolecore.Details{}, nil)
			}
			accepted = index
			result.HasMore = true
			result.Continuation = "opaque"
			break
		}
		result.Items = result.Items[:accepted]
	}
	return result, nil
}

type fakeTraceAnalysis struct {
	summary      traceanalysis.TraceSummary
	plans        traceanalysis.Page[traceanalysis.PlanSummary]
	frames       traceanalysis.Page[traceanalysis.FrameSummary]
	records      traceanalysis.Page[traceanalysis.RecordSummary]
	search       traceanalysis.SearchPage
	payload      traceanalysis.ByteRangeResult
	raw          traceanalysis.ByteRangeResult
	refs         []evidence.Reference
	frameQuery   traceanalysis.FrameQuery
	recordQuery  traceanalysis.RecordQuery
	rangeRequest traceanalysis.RangeRequest
	searchQuery  traceanalysis.SearchQuery
	planQuery    traceanalysis.PlanQuery
	summaryCalls int
	planCalls    int
	frameCalls   int
	recordCalls  int
	payloadCalls int
	rawCalls     int
	searchCalls  int
	onSummary    func()
	onSearch     func()
}

func (fake *fakeTraceAnalysis) QueryPlans(_ context.Context, ref evidence.Reference, query traceanalysis.PlanQuery) (traceanalysis.Page[traceanalysis.PlanSummary], *consolecore.Error) {
	fake.planCalls++
	fake.refs = append(fake.refs, ref)
	fake.planQuery = query
	page := fake.plans
	if page.Items == nil {
		page.Items = []traceanalysis.PlanSummary{}
		page.Context = traceanalysis.TraceContext{Evidence: ref, Handle: query.Handle, TraceID: "trace-1", SessionID: "session-1"}
	}
	if query.Admit != nil {
		accepted := len(page.Items)
		for index, item := range page.Items {
			if query.Admit(item) {
				continue
			}
			if index == 0 {
				return traceanalysis.Page[traceanalysis.PlanSummary]{}, consolecore.NewError(consolecore.CodeLimitExceeded, "oversized plan", "", consolecore.Details{}, nil)
			}
			accepted = index
			page.HasMore = true
			page.NextCursor = "opaque"
			break
		}
		page.Items = page.Items[:accepted]
	}
	return page, nil
}

func (fake *fakeTraceAnalysis) GetSummary(_ context.Context, ref evidence.Reference, _ traceanalysis.SummaryRequest) (traceanalysis.TraceSummary, *consolecore.Error) {
	fake.summaryCalls++
	fake.refs = append(fake.refs, ref)
	if fake.onSummary != nil {
		fake.onSummary()
	}
	return fake.summary, nil
}
func (fake *fakeTraceAnalysis) QueryFrames(_ context.Context, ref evidence.Reference, query traceanalysis.FrameQuery) (traceanalysis.Page[traceanalysis.FrameSummary], *consolecore.Error) {
	fake.frameCalls++
	fake.refs = append(fake.refs, ref)
	fake.frameQuery = query
	page := fake.frames
	if query.Admit != nil {
		accepted := len(page.Items)
		for index, item := range page.Items {
			if query.Admit(item) {
				continue
			}
			if index == 0 {
				return traceanalysis.Page[traceanalysis.FrameSummary]{}, consolecore.NewError(consolecore.CodeLimitExceeded, "oversized frame", "", consolecore.Details{}, nil)
			}
			accepted = index
			page.HasMore = true
			page.NextCursor = "opaque"
			break
		}
		page.Items = page.Items[:accepted]
	}
	return page, nil
}
func (fake *fakeTraceAnalysis) QueryRecords(_ context.Context, ref evidence.Reference, query traceanalysis.RecordQuery) (traceanalysis.Page[traceanalysis.RecordSummary], *consolecore.Error) {
	fake.recordCalls++
	fake.refs = append(fake.refs, ref)
	fake.recordQuery = query
	page := fake.records
	if query.Admit != nil {
		accepted := len(page.Items)
		for index, item := range page.Items {
			if query.Admit(item) {
				continue
			}
			if index == 0 {
				return traceanalysis.Page[traceanalysis.RecordSummary]{}, consolecore.NewError(consolecore.CodeLimitExceeded, "oversized record", "", consolecore.Details{}, nil)
			}
			accepted = index
			page.HasMore = true
			page.NextCursor = "opaque"
			break
		}
		page.Items = page.Items[:accepted]
	}
	return page, nil
}
func (fake *fakeTraceAnalysis) ReadContentRange(_ context.Context, ref evidence.Reference, request traceanalysis.RangeRequest) (traceanalysis.ByteRangeResult, *consolecore.Error) {
	fake.payloadCalls++
	fake.refs = append(fake.refs, ref)
	fake.rangeRequest = request
	return fake.payload, nil
}
func (fake *fakeTraceAnalysis) ReadRawArtifactRange(_ context.Context, ref evidence.Reference, request traceanalysis.RangeRequest) (traceanalysis.ByteRangeResult, *consolecore.Error) {
	fake.rawCalls++
	fake.refs = append(fake.refs, ref)
	fake.rangeRequest = request
	return fake.raw, nil
}
func (fake *fakeTraceAnalysis) Search(_ context.Context, ref evidence.Reference, query traceanalysis.SearchQuery) (traceanalysis.SearchPage, *consolecore.Error) {
	fake.searchCalls++
	fake.refs = append(fake.refs, ref)
	fake.searchQuery = query
	page := fake.search
	if page.Context.TraceID == "" {
		page.Context = fake.records.Context
	}
	if page.Items == nil {
		page.Items = []traceanalysis.SearchResult{}
	}
	if query.Admit != nil {
		accepted := len(page.Items)
		for index, item := range page.Items {
			contentRef := ""
			for _, descriptor := range page.ContentDescriptors {
				if descriptor.ContentID == item.ContentID {
					contentRef = descriptor.ContentRef
					break
				}
			}
			if query.Admit(item, contentRef) {
				continue
			}
			if index == 0 {
				return traceanalysis.SearchPage{}, consolecore.NewError(consolecore.CodeLimitExceeded, "oversized search match", "", consolecore.Details{}, nil)
			}
			accepted = index
			page.HasMore = true
			page.NextCursor = "opaque"
			break
		}
		page.Items = page.Items[:accepted]
		acceptedIDs := map[string]bool{}
		for _, item := range page.Items {
			acceptedIDs[item.ContentID] = true
		}
		descriptors := page.ContentDescriptors[:0]
		for _, descriptor := range page.ContentDescriptors {
			if acceptedIDs[descriptor.ContentID] {
				descriptors = append(descriptors, descriptor)
			}
		}
		page.ContentDescriptors = descriptors
	}
	if fake.onSearch != nil {
		fake.onSearch()
	}
	return page, nil
}

type fakeTraceArtifacts struct {
	result artifact.AcquiredArtifact
	err    *consolecore.Error
	calls  atomic.Int32
	ref    evidence.Reference
	scope  target.Scope
}

func (fake *fakeTraceArtifacts) Resolve(_ context.Context, _ string) (traceresolution.Resolved, *consolecore.Error) {
	fake.calls.Add(1)
	if fake.err != nil {
		return traceresolution.Resolved{}, fake.err
	}
	ref := fake.ref
	if !ref.Valid() {
		ref = evidence.ForImported()
		if fake.result.Owner.Source() == evidence.SourceTarget {
			ref = evidence.ForTarget(fake.result.Owner.TargetScope())
		}
	}
	scope := fake.scope
	if ref.Source == evidence.SourceTarget && scope.ID == "" {
		scope.ID = ref.TargetScope
	}
	return traceresolution.Resolved{Reference: ref, Handle: fake.result.Handle, Scope: scope}, nil
}

func TestOversizedSemanticItemsProduceOnlyTypedErrorResults(t *testing.T) {
	handle := artifact.Handle(strings.Repeat("a", 64))
	traceContext := traceanalysis.TraceContext{Evidence: evidence.ForImported(), Handle: handle, TraceID: "trace-oversized"}
	analysis := &fakeTraceAnalysis{
		frames:  traceanalysis.Page[traceanalysis.FrameSummary]{Context: traceContext, Items: []traceanalysis.FrameSummary{{Context: traceContext, FrameID: strings.Repeat("f", defaultTraceResultBudget), FrameType: "MODEL_CALL"}}},
		records: traceanalysis.Page[traceanalysis.RecordSummary]{Context: traceContext, Items: []traceanalysis.RecordSummary{{Context: traceContext, Sequence: 1, Type: "MODEL_RESPONSE_RECEIVED", FrameID: strings.Repeat("f", defaultTraceResultBudget), Facts: traceanalysis.RecordFacts{}}}},
	}
	options := ServerOptions{Credentials: fakeCredentials{state: mcpcredential.Snapshot{State: mcpcredential.Enabled}}, TraceResolver: &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: handle}, ref: evidence.ForImported()}, TraceAnalysis: analysis, Now: time.Now}

	frameCall, frameEnvelope, err := handleQueryTraceFrames(context.Background(), options, queryTraceFramesInput{TraceID: "trace-oversized", Projection: traceanalysis.FrameProjectionDetailed})
	if err != nil || frameCall == nil || !frameCall.IsError || frameEnvelope.Result != nil || frameEnvelope.Error == nil || frameEnvelope.Error.Code != consolecore.CodeLimitExceeded {
		t.Fatalf("oversized frame call=%#v envelope=%#v err=%v", frameCall, frameEnvelope, err)
	}
	if text := frameCall.Content[0].(*mcp.TextContent).Text; strings.Contains(text, strings.Repeat("f", 128)) {
		t.Fatalf("oversized frame leaked a partial item: %q", text)
	}

	recordCall, recordEnvelope, err := handleQueryTraceRecords(context.Background(), options, queryTraceRecordsInput{TraceID: "trace-oversized"})
	if err != nil || recordCall == nil || !recordCall.IsError || recordEnvelope.Result != nil || recordEnvelope.Error == nil || recordEnvelope.Error.Code != consolecore.CodeLimitExceeded {
		t.Fatalf("oversized record call=%#v envelope=%#v err=%v", recordCall, recordEnvelope, err)
	}
	if text := recordCall.Content[0].(*mcp.TextContent).Text; strings.Contains(text, strings.Repeat("f", 128)) {
		t.Fatalf("oversized record leaked a partial item: %q", text)
	}
}

func TestTraceHandlersResolveTraceIDAndPreserveQuestionSpecificRequests(t *testing.T) {
	now := time.Date(2026, 8, 18, 12, 0, 0, 0, time.UTC)
	handle := artifact.Handle(strings.Repeat("a", 64))
	traceContext := traceanalysis.TraceContext{Evidence: evidence.ForImported(), Handle: handle, TraceID: "trace-i", SessionID: "session-i"}
	analysis := &fakeTraceAnalysis{
		summary: traceanalysis.TraceSummary{Context: traceContext, RootFrameIDs: []string{}},
		frames: traceanalysis.Page[traceanalysis.FrameSummary]{Context: traceContext, Items: []traceanalysis.FrameSummary{{
			Context: traceContext, FrameID: "retrying", FrameType: "MODEL_CALL", ChildFrameIDs: []string{}, DirectRetryCount: 2,
		}}},
		records: traceanalysis.Page[traceanalysis.RecordSummary]{Context: traceContext, Items: []traceanalysis.RecordSummary{}, NextCursor: "next"},
		payload: traceanalysis.ByteRangeResult{Context: traceContext, ContentType: "text/plain", Encoding: traceanalysis.RangeEncodingText, Content: []byte("ok")},
		raw:     traceanalysis.ByteRangeResult{Context: traceContext, ContentType: "application/x-ndjson", Encoding: traceanalysis.RangeEncodingText, Content: []byte("{}\n")},
	}
	resolver := &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: handle}, ref: evidence.ForImported()}
	sessionID := "session-i"
	inventory := &fakeTraceInventory{result: traceinventory.Result{ObservedAt: now, Complete: true, Items: []traceinventory.Entry{{TraceID: "trace-i", SessionID: &sessionID}}}}
	options := ServerOptions{Credentials: fakeCredentials{state: mcpcredential.Snapshot{State: mcpcredential.Enabled, Generation: 1}}, Now: func() time.Time { return now }, TraceInventory: inventory, TraceAnalysis: analysis, TraceResolver: resolver}

	if result, envelope, err := handleListTraces(context.Background(), options, listTracesInput{}); err != nil || result.IsError || envelope.Result == nil || !envelope.Result.Complete {
		t.Fatalf("list result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	if result, envelope, err := handleGetTrace(context.Background(), options, getTraceInput{TraceID: "trace-i"}); err != nil || result.IsError || envelope.Result == nil || envelope.Result.Evidence.TraceID != "trace-i" {
		t.Fatalf("get result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	frameFilter := traceanalysis.FrameFilter{MinDirectRetries: 2}
	if result, envelope, err := handleQueryTraceFrames(context.Background(), options, queryTraceFramesInput{TraceID: "trace-i", Order: traceanalysis.FrameOrderCanonical, Filter: frameFilter}); err != nil || result.IsError || envelope.Result == nil || len(envelope.Result.Items) != 1 || analysis.frameQuery.Filter.MinDirectRetries != 2 {
		t.Fatalf("frames result=%#v envelope=%#v request=%#v err=%v", result, envelope, analysis.frameQuery, err)
	}
	if result, _, err := handleQueryTraceRecords(context.Background(), options, queryTraceRecordsInput{TraceID: "trace-i", Continuation: "prior", InlineContent: true}); err != nil || result.IsError || analysis.recordQuery.Cursor != "prior" || !analysis.recordQuery.InlineContent {
		t.Fatalf("records result=%#v request=%#v err=%v", result, analysis.recordQuery, err)
	}
	literalFilter := traceanalysis.RecordFilter{LiteralText: "needle", Types: []string{string(traceanalysis.RecordPlanCreated)}, FrameID: "planning"}
	if result, _, err := handleQueryTraceRecords(context.Background(), options, queryTraceRecordsInput{TraceID: "trace-i", Filter: literalFilter}); err != nil || result.IsError || analysis.searchQuery.Filter.FrameID != "planning" || len(analysis.searchQuery.Filter.Types) != 1 {
		t.Fatalf("search result=%#v request=%#v err=%v", result, analysis.searchQuery, err)
	}
	start := int64(0)
	if result, _, err := handleTraceRange(context.Background(), options, traceRangeInput{TraceID: "trace-i", ContentRef: "opaque", Start: &start}, false); err != nil || result.IsError || analysis.rangeRequest.ContentRef != "opaque" {
		t.Fatalf("payload result=%#v request=%#v err=%v", result, analysis.rangeRequest, err)
	}
	if result, _, err := handleTraceRange(context.Background(), options, traceRangeInput{TraceID: "trace-i", Start: &start}, true); err != nil || result.IsError {
		t.Fatalf("raw result=%#v err=%v", result, err)
	}
	if result, _, err := handleTraceRange(context.Background(), options, traceRangeInput{TraceID: "trace-i", ContentRef: "opaque"}, false); err != nil || result.IsError || analysis.rangeRequest.Start != 0 {
		t.Fatalf("omitted payload start result=%#v request=%#v err=%v", result, analysis.rangeRequest, err)
	}
	if result, _, err := handleTraceRange(context.Background(), options, traceRangeInput{TraceID: "trace-i"}, true); err != nil || result.IsError || analysis.rangeRequest.Start != 0 {
		t.Fatalf("omitted raw start result=%#v request=%#v err=%v", result, analysis.rangeRequest, err)
	}
	if resolver.calls.Load() != 8 || analysis.summaryCalls != 1 || analysis.frameCalls != 1 || analysis.recordCalls != 1 || analysis.searchCalls != 1 || analysis.payloadCalls != 2 || analysis.rawCalls != 2 {
		t.Fatalf("resolver=%d summary=%d frames=%d records=%d search=%d payload=%d raw=%d", resolver.calls.Load(), analysis.summaryCalls, analysis.frameCalls, analysis.recordCalls, analysis.searchCalls, analysis.payloadCalls, analysis.rawCalls)
	}
}

func TestTraceOpaqueTokenErrorsGiveTraceIDRecoveryWithoutHandleTerms(t *testing.T) {
	for _, test := range []struct {
		domain       *consolecore.Error
		continuation bool
		payload      bool
		want         string
	}{
		{consolecore.NewError(consolecore.CodeInvalidCursor, "old handle mismatch", "scope", consolecore.Details{}, nil), true, false, "Restart this query by traceId."},
		{consolecore.NewError(consolecore.CodeInvalidArgument, "The content reference is invalid.", "scope", consolecore.Details{}, nil), false, true, "Re-query the relevant record descriptor by traceId."},
		{consolecore.NewError(consolecore.CodeArtifactExpired, "old handle expired", "scope", consolecore.Details{}, nil), false, false, "Retry inspection by traceId"},
	} {
		mapped := mapTraceAnalysisError(test.domain, "trace-1", test.continuation, test.payload)
		if !strings.Contains(mapped.Message, test.want) || strings.Contains(strings.ToLower(mapped.Message), "handle") || mapped.TargetScopeID != "" {
			t.Fatalf("mapped=%#v", mapped)
		}
	}
}

type mutableTraceCredentials struct{ generation atomic.Uint64 }

func (credentials *mutableTraceCredentials) Snapshot() mcpcredential.Snapshot {
	return mcpcredential.Snapshot{State: mcpcredential.Enabled, Generation: credentials.generation.Load()}
}
func (credentials *mutableTraceCredentials) Authenticate(string) (uint64, bool) {
	return credentials.generation.Load(), true
}

func TestTraceHandlerSuppressesAmbiguityAndChangedPublicationAuthority(t *testing.T) {
	handle := artifact.Handle(strings.Repeat("f", 64))

	t.Run("ambiguity stops before analysis", func(t *testing.T) {
		analysis := &fakeTraceAnalysis{}
		options := ServerOptions{
			Credentials:   fakeCredentials{state: mcpcredential.Snapshot{State: mcpcredential.Enabled, Generation: 1}},
			TraceResolver: &fakeTraceArtifacts{err: consolecore.NewError(consolecore.CodeAmbiguousTrace, "ambiguous", "", consolecore.Details{}, nil)},
			TraceAnalysis: analysis,
			Now:           time.Now,
		}
		result, envelope, err := handleGetTrace(context.Background(), options, getTraceInput{TraceID: "trace-1"})
		if err != nil || result == nil || !result.IsError || envelope.Error == nil || envelope.Error.Code != consolecore.CodeAmbiguousTrace || analysis.summaryCalls != 0 {
			t.Fatalf("result=%#v envelope=%#v err=%v summaryCalls=%d", result, envelope, err, analysis.summaryCalls)
		}
	})

	t.Run("selected-target imported fallback rotates after analysis", func(t *testing.T) {
		ids := []target.ScopeID{"scope-1", "scope-2"}
		client := &mcpTestTargetClient{}
		targetContext, err := target.New(
			func(applicationclient.Address) (target.ProbeClient, error) { return client, nil },
			func() (target.ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil },
			time.Now,
		)
		if err != nil {
			t.Fatal(err)
		}
		defer targetContext.Close()
		if domain := targetContext.Select("http://127.0.0.1:8080"); domain != nil {
			t.Fatal(domain)
		}
		if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); domain != nil {
			t.Fatal(domain)
		}
		scope, domain := targetContext.Capture()
		if domain != nil {
			t.Fatal(domain)
		}
		analysis := &fakeTraceAnalysis{summary: traceanalysis.TraceSummary{Context: traceanalysis.TraceContext{TraceID: "trace-1"}, RootFrameIDs: []string{}}}
		analysis.onSummary = func() {
			if domain := targetContext.Select("http://127.0.0.1:8081"); domain != nil {
				t.Fatal(domain)
			}
		}
		options := ServerOptions{
			Credentials:   fakeCredentials{state: mcpcredential.Snapshot{State: mcpcredential.Enabled, Generation: 1}},
			Target:        targetContext,
			TraceResolver: &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: handle}, ref: evidence.ForImported(), scope: scope},
			TraceAnalysis: analysis,
			Now:           time.Now,
		}
		result, envelope, callErr := handleGetTrace(context.Background(), options, getTraceInput{TraceID: "trace-1"})
		if callErr != nil || result == nil || !result.IsError || envelope.Error == nil || envelope.Error.Code != consolecore.CodeTargetChanged {
			t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, callErr)
		}
	})

	t.Run("authentication generation changes after analysis", func(t *testing.T) {
		credentials := &mutableTraceCredentials{}
		credentials.generation.Store(1)
		tracker := NewTracker()
		ctx, done, err := tracker.Admit(context.Background(), 1)
		if err != nil {
			t.Fatal(err)
		}
		defer done()
		analysis := &fakeTraceAnalysis{summary: traceanalysis.TraceSummary{Context: traceanalysis.TraceContext{TraceID: "trace-1"}, RootFrameIDs: []string{}}}
		analysis.onSummary = func() { credentials.generation.Store(2) }
		options := ServerOptions{
			Credentials:   credentials,
			TraceResolver: &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: handle}, ref: evidence.ForImported()},
			TraceAnalysis: analysis,
			Now:           time.Now,
		}
		result, envelope, callErr := handleGetTrace(ctx, options, getTraceInput{TraceID: "trace-1"})
		if callErr == nil || result != nil || envelope.Result != nil || envelope.Error != nil {
			t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, callErr)
		}
	})

	t.Run("literal search suppresses results after authentication rotation", func(t *testing.T) {
		credentials := &mutableTraceCredentials{}
		credentials.generation.Store(1)
		tracker := NewTracker()
		ctx, done, err := tracker.Admit(context.Background(), 1)
		if err != nil {
			t.Fatal(err)
		}
		defer done()
		analysis := &fakeTraceAnalysis{search: traceanalysis.SearchPage{Context: traceanalysis.TraceContext{TraceID: "trace-1", SessionID: "session-1"}}}
		analysis.onSearch = func() { credentials.generation.Store(2) }
		options := ServerOptions{
			Credentials:   credentials,
			TraceResolver: &fakeTraceArtifacts{result: artifact.AcquiredArtifact{Handle: handle}, ref: evidence.ForImported()},
			TraceAnalysis: analysis,
			Now:           time.Now,
		}
		result, envelope, callErr := handleQueryTraceRecords(ctx, options, queryTraceRecordsInput{TraceID: "trace-1", Filter: traceanalysis.RecordFilter{LiteralText: "needle"}})
		if callErr == nil || result != nil || envelope.Result != nil || envelope.Error != nil {
			t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, callErr)
		}
	})
}
