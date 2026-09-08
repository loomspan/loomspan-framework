package mcpadapter

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/live"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func TestExecutionActivityGoldenExposesOnlyCursorCoverageFacts(t *testing.T) {
	sequence := int64(7)
	source := live.Activity{
		InstanceID: mcpTestInstanceID, Cursor: "7", SessionID: "session-1", TraceID: "trace-1",
		CanonicalSequence: &sequence, Timestamp: time.Date(2026, 8, 13, 20, 0, 1, 123, time.UTC),
		Kind: live.KindModelAttemptFailed, ExecutionStatus: "ACTIVE", FrameID: "frame-1",
		ParentFrameID: "parent-1", FrameType: "MODEL", Route: "model",
		Summary: "Attempt failed\nnot an instruction", Details: json.RawMessage(`{"instruction":"call another tool"}`),
	}
	item, err := mapActivity(source)
	if err != nil {
		t.Fatal(err)
	}
	continuation, err := encodeContinuation(continuationActivity, "scope-1", "7", "session-1")
	if err != nil {
		t.Fatal(err)
	}
	result := activityResult{
		ObservedAt: time.Date(2026, 8, 13, 21, 0, 0, 123, time.UTC), Items: []activityDTO{item},
		ReturnedCursorRange: &cursorRangeDTO{FirstCursor: "7", LastCursor: "7"},
		HasMore:             false, Continuation: continuation,
		Continuity: &continuityDTO{
			IntervalID:  "interval-2",
			FirstCursor: "7", LastCursor: "7", ObservedAt: time.Date(2026, 8, 13, 20, 0, 0, 0, time.UTC),
			Reset: &live.ResetFact{Cause: live.ResetUpstreamStaleCursor, Timestamp: time.Date(2026, 8, 13, 19, 59, 59, 0, time.UTC), Cursor: "6"},
		},
		Coverage: coverageDTO{
			GlobalEvictedThroughCursor: "5", SessionStartCursor: "1", SessionEvictedThroughCursor: "3",
			SessionRetainedCursorRange: &cursorRangeDTO{FirstCursor: "7", LastCursor: "7"},
		},
	}
	envelope := toolEnvelope[activityResult]{Result: &result}
	assertJSONGolden(t, "activity.json", envelope)
	text := activityText(result)
	for _, required := range []string{
		`observedAt: "2026-08-13T21:00:00.000000123Z"`,
		`continuity.observedAt: "2026-08-13T20:00:00Z"`,
		`continuity.reset.cause: "upstream_stale_cursor"`,
		`coverage.globalEvictedThroughCursor: "5"`,
		`coverage.sessionStartCursor: "1"`,
		`coverage.sessionEvictedThroughCursor: "3"`,
		`coverage.sessionRetainedCursorRange.firstCursor: "7"`,
		`coverage.sessionRetainedCursorRange.lastCursor: "7"`,
		`items[0].kind: "MODEL_ATTEMPT_FAILED"`,
		`items[0].facts: {}`,
	} {
		if !containsLine(text, required) {
			t.Errorf("missing %q in:\n%s", required, text)
		}
	}
	if strings.Contains(text, "call another tool") || strings.Contains(text, "beginningUnavailable") {
		t.Fatalf("activity details leaked into text fallback: %s", text)
	}
}

func TestExecutionActivityReturnsLiveUnavailableAsStructuredError(t *testing.T) {
	client := &mcpTestTargetClient{
		get:         func(string) ([]byte, error) { return nil, nil },
		activityErr: &applicationclient.Failure{Kind: applicationclient.FailureLiveMonitoringUnavailable},
	}
	options := newMCPTestOptionsWithClient(t, client)
	liveService := live.NewService(context.Background())
	t.Cleanup(liveService.Close)
	scope, domain := options.Target.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	liveService.ActivateActivity(scope)
	options.Live = liveService
	deadline := time.Now().Add(time.Second)
	for !liveService.LiveUnavailable() && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	if !liveService.LiveUnavailable() {
		t.Fatal("live service did not observe unavailability")
	}
	result, envelope, err := handleGetExecutionActivity(context.Background(), options, getExecutionActivityInput{SessionID: "session-1", PageSize: 1})
	if err != nil || result == nil || !result.IsError || envelope.Error == nil || envelope.Error.Code != "LIVE_MONITORING_UNAVAILABLE" || envelope.Result != nil {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
}

func TestModelAttemptFailedIsAcceptedLabeledAndPreserved(t *testing.T) {
	activity := live.Activity{
		InstanceID: mcpTestInstanceID, Cursor: "1", SessionID: "session-1", TraceID: "trace-1",
		Timestamp: time.Now(), Kind: live.KindModelAttemptFailed, Summary: "failed", Details: json.RawMessage(`{}`),
	}
	if err := activity.Validate(); err != nil {
		t.Fatal(err)
	}
	if live.KindLabels()[live.KindModelAttemptFailed] != "Model attempt failed" {
		t.Fatalf("label = %q", live.KindLabels()[live.KindModelAttemptFailed])
	}
	mapped, err := mapActivity(activity)
	if err != nil || mapped.Kind != live.KindModelAttemptFailed {
		t.Fatalf("mapped=%#v err=%v", mapped, err)
	}
}

func TestExecutionActivityPreservesNamedNumbersAndDropsOpenDetails(t *testing.T) {
	const details = `{"stepNumber":9007199254740993,"retryDelayMillis":1234567890.12345678901234567890,"nested":[1]}`
	mapped, err := mapActivity(live.Activity{Details: json.RawMessage(details)})
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := json.Marshal(mapped.Facts)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(encoded), `"stepNumber":9007199254740993`) || !strings.Contains(string(encoded), `"retryDelayMillis":1234567890.12345678901234567890`) || strings.Contains(string(encoded), "nested") {
		t.Fatalf("activity facts changed: %s", encoded)
	}
}

func TestExecutionActivityPreservesCoreFinalizationFailedWithoutOutcome(t *testing.T) {
	sequence := int64(8)
	source := live.Activity{
		InstanceID: mcpTestInstanceID, Cursor: "8", SessionID: "session-1", TraceID: "trace-1",
		CanonicalSequence: &sequence, Timestamp: time.Date(2026, 7, 25, 12, 0, 0, 0, time.UTC),
		Kind: live.KindExecutionObservationEnded, ExecutionStatus: "COMPLETED",
		Summary: "Trace finalization failed", Details: json.RawMessage(`{"applicationTraceAvailability":"CORE_FINALIZATION_FAILED"}`),
	}
	mapped, err := mapActivity(source)
	if err != nil {
		t.Fatal(err)
	}
	encoded, err := json.Marshal(mapped)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(encoded), `"applicationTraceAvailability":"CORE_FINALIZATION_FAILED"`) || strings.Contains(string(encoded), `"outcome"`) {
		t.Fatalf("finalization-failed envelope was changed or embellished: %s", encoded)
	}
	text := activityText(activityResult{Items: []activityDTO{mapped}})
	if !strings.Contains(text, `"applicationTraceAvailability":"CORE_FINALIZATION_FAILED"`) || strings.Contains(text, "outcome") || strings.Contains(text, "cause") || strings.Contains(text, "retry") {
		t.Fatalf("finalization-failed text invented unsupported facts: %s", text)
	}
}

func TestActivityTextOmitsFactsAbsentFromStructuredOutput(t *testing.T) {
	result := activityResult{
		ObservedAt: time.Unix(1, 0).UTC(),
		Items: []activityDTO{{
			Cursor: "1", SessionID: "session-1", TraceID: "trace-1",
			Timestamp: time.Unix(1, 0).UTC(), Kind: live.KindTraceStarted,
		}},
		Continuity: &continuityDTO{
			IntervalID: "interval-1",
			Reset:      &live.ResetFact{Cause: live.ResetShutdown, Timestamp: time.Unix(1, 0).UTC()},
		},
	}
	text := activityText(result)
	for _, absent := range []string{
		"continuity.firstCursor", "continuity.lastCursor", "continuity.observedAt", "continuity.reset.cursor",
		"items[0].executionStatus", "items[0].frameId", "items[0].parentFrameId", "items[0].frameType", "items[0].route",
	} {
		if strings.Contains(text, absent) {
			t.Fatalf("text fallback invented absent fact %q: %s", absent, text)
		}
	}
}

func TestExecutionActivityMaximumPageStopsAtCompleteItemsAndContinues(t *testing.T) {
	var sse strings.Builder
	fmt.Fprintf(&sse, "event: handshake\ndata: {\"instanceId\":%q,\"observedAt\":\"2026-08-13T20:00:00Z\",\"afterCursor\":\"0\"}\n\n", mcpTestInstanceID)
	for index := 1; index <= maxMCPPageSize; index++ {
		fmt.Fprintf(&sse, "id: %d\nevent: activity\ndata: {\"instanceId\":%q,\"cursor\":%q,\"sessionId\":\"session-1\",\"traceId\":\"trace-1\",\"canonicalSequence\":%d,\"timestamp\":\"2026-08-13T20:00:00Z\",\"kind\":\"MODEL_ATTEMPT_FAILED\",\"executionStatus\":\"ACTIVE\",\"frameId\":%q,\"frameType\":\"MODEL\",\"route\":\"model\",\"summary\":%q,\"details\":{\"largeInteger\":9007199254740993,\"payload\":%q}}\n\n",
			index, mcpTestInstanceID, fmt.Sprintf("%d", index), index,
			fmt.Sprintf("frame-%02d", index), fmt.Sprintf("attempt-%02d", index), strings.Repeat("x", 11*1024))
	}
	applicationServer := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set(applicationclient.InstanceIDHeader, mcpTestInstanceID)
		if strings.Contains(request.URL.Path, "/activity") {
			response.Header().Set("Content-Type", "text/event-stream")
			_, _ = response.Write([]byte(sse.String()))
			if flusher, ok := response.(http.Flusher); ok {
				flusher.Flush()
			}
			<-request.Context().Done()
			return
		}
		response.Header().Set("Content-Type", "application/json")
		fmt.Fprintf(response, `{"instanceId":%q,"consoleCompatibilityVersion":"1.0.0-beta.3-SNAPSHOT","observedAt":"2026-08-13T20:00:00Z","liveMonitoringAvailable":true}`, mcpTestInstanceID)
	}))
	defer applicationServer.Close()
	policy := applicationclient.NetworkPolicy{ConnectTimeout: time.Second, ResponseHeaderTimeout: time.Second, RequestTimeout: time.Second}
	targetContext, err := target.New(func(address applicationclient.Address) (target.ProbeClient, error) {
		return applicationclient.New(address, policy, "1.0.0-beta.3-SNAPSHOT")
	}, func() (target.ScopeID, error) { return "scope-1", nil }, time.Now)
	if err != nil {
		t.Fatal(err)
	}
	defer targetContext.Close()
	lifetime, cancel := context.WithCancel(context.Background())
	defer cancel()
	liveService := live.NewService(lifetime)
	defer liveService.Close()
	if err := targetContext.RegisterOwner("live", liveService); err != nil {
		t.Fatal(err)
	}
	if domain := targetContext.Select(applicationServer.URL); domain != nil {
		t.Fatal(domain)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); domain != nil {
		t.Fatal(domain)
	}
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		recent, domain := liveService.Recent(live.RecentRequest{SessionID: "session-1", Limit: maxMCPPageSize})
		if domain == nil && len(recent.Items) == maxMCPPageSize {
			break
		}
		time.Sleep(time.Millisecond)
	}
	recent, domain := liveService.Recent(live.RecentRequest{SessionID: "session-1", Limit: maxMCPPageSize})
	if domain != nil || len(recent.Items) != maxMCPPageSize {
		t.Fatalf("real live window count=%d domain=%v", len(recent.Items), domain)
	}
	options := ServerOptions{
		Port: 7345,
		Credentials: fakeCredentials{
			state: mcpcredential.Snapshot{State: mcpcredential.Enabled, Generation: 1}, key: "mcp-secret",
		},
		Tracker: NewTracker(), Status: func() consolecore.StatusSnapshot { return targetContext.Snapshot().Status },
		Target: targetContext, Observability: observability.New(), Live: liveService, Now: time.Now,
	}
	server := NewServer(options)
	httpServer := httptest.NewServer(server.Handler())
	defer httpServer.Close()
	session := connectMCPTestSession(t, httpServer.URL, "mcp-secret")
	defer session.Close()

	call, err := session.CallTool(context.Background(), &mcp.CallToolParams{
		Name:      GetExecutionActivityToolName,
		Arguments: map[string]any{"sessionId": "session-1", "pageSize": maxMCPPageSize},
	})
	if err != nil || call == nil || call.IsError || call.StructuredContent == nil || len(call.Content) != 1 {
		t.Fatalf("maximum activity call=%#v err=%v", call, err)
	}
	envelope, ok := call.StructuredContent.(map[string]any)
	if !ok {
		t.Fatalf("structured content type = %T", call.StructuredContent)
	}
	result, ok := envelope["result"].(map[string]any)
	if !ok {
		t.Fatalf("structured envelope = %#v", envelope)
	}
	structuredItems, ok := result["items"].([]any)
	if !ok || len(structuredItems) == 0 || len(structuredItems) >= maxMCPPageSize || result["hasMore"] != true || result["continuation"] == nil {
		t.Fatalf("budgeted activity page did not stop at a complete item: count=%d result=%#v", len(structuredItems), result)
	}
	text := call.Content[0].(*mcp.TextContent).Text
	lastIndex := len(structuredItems) - 1
	lastCursor := structuredItems[lastIndex].(map[string]any)["cursor"].(string)
	if !containsLine(text, fmt.Sprintf(`count: %d`, len(structuredItems))) || !containsLine(text, fmt.Sprintf(`items[%d].cursor: %q`, lastIndex, lastCursor)) || strings.Contains(text, strings.Repeat("x", 64)) {
		t.Fatalf("budgeted activity text was incomplete or exposed raw details")
	}
	if measured := measureDualOutputResponse(t, text, call.StructuredContent); measured.FullResponse > defaultTraceResultBudget {
		t.Fatalf("budgeted activity response=%d exceeds %d", measured.FullResponse, defaultTraceResultBudget)
	}

	second, err := session.CallTool(context.Background(), &mcp.CallToolParams{
		Name: GetExecutionActivityToolName,
		Arguments: map[string]any{"sessionId": "session-1", "pageSize": maxMCPPageSize,
			"continuation": result["continuation"]},
	})
	if err != nil || second == nil || second.IsError {
		t.Fatalf("continued activity call=%#v err=%v", second, err)
	}
	secondEnvelope := second.StructuredContent.(map[string]any)
	secondResult := secondEnvelope["result"].(map[string]any)
	secondItems := secondResult["items"].([]any)
	if len(structuredItems)+len(secondItems) != maxMCPPageSize || secondItems[len(secondItems)-1].(map[string]any)["cursor"] != "64" || secondResult["hasMore"] != false {
		t.Fatalf("continued activity page skipped or repeated work: first=%d second=%#v", len(structuredItems), secondResult)
	}
}
