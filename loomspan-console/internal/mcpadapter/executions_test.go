package mcpadapter

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func TestExecutionListIsCompactAndDetailPreservesEveryBranch(t *testing.T) {
	plan, task, step, group, concurrent := "plan-1", "task-1", 2, "group-a", true
	source := observability.ActiveExecution{SessionID: "session", TraceID: "trace", Status: "ACTIVE", Phase: "STEP", UpdatedAt: time.Unix(1, 0).UTC(), ActiveBranches: []observability.ActiveBranch{{PlanID: &plan, TaskID: &task, StepNumber: &step, ParallelGroup: &group, EffectiveConcurrency: &concurrent, Path: []observability.FramePathEntry{{FrameID: "root", FrameType: "ROOT_MISSION", Route: "entry"}, {FrameID: "leaf-a", FrameType: "STEP_EXECUTION", Route: "step-a"}}}, {Path: []observability.FramePathEntry{{FrameID: "root", FrameType: "ROOT_MISSION", Route: "entry"}, {FrameID: "leaf-b", FrameType: "TOOL_INVOCATION", Route: "tool-b"}}}}}
	compact := mapExecutionListItem(source)
	if compact.ActiveBranchCount != 2 || compact.SessionID != "session" {
		t.Fatalf("compact=%#v", compact)
	}
	detail := mapExecutionDetail(source)
	if len(detail.ActiveBranches) != 2 || len(detail.ActiveBranches[0].Path) != 2 || detail.ActiveBranches[0].PlanID == nil || detail.ActiveBranches[1].PlanID != nil || detail.ActiveBranches[1].EffectiveConcurrency != nil {
		t.Fatalf("detail=%#v", detail)
	}
	encoded, _ := json.Marshal(compact)
	for _, removed := range []string{"entrySkill", "usage", "configuredLimits", "activeBranches", "summary"} {
		if strings.Contains(string(encoded), removed) {
			t.Fatalf("compact list leaked %s: %s", removed, encoded)
		}
	}
}

func TestListExecutionsGoldenPreservesBoundedActiveSummaries(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-executions-page.json"))
	if err != nil {
		t.Fatal(err)
	}
	options := newMCPTestOptions(t, func(endpoint string) ([]byte, error) {
		if !strings.Contains(endpoint, "/active-executions?") {
			return nil, errors.New("unexpected endpoint: " + endpoint)
		}
		return fixture, nil
	})
	result, envelope, err := handleListExecutions(context.Background(), options, listExecutionsInput{PageSize: 64})
	if err != nil || result.IsError || envelope.Result == nil {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	assertJSONGolden(t, "executions-list.json", envelope)
	text := result.Content[0].(*mcp.TextContent).Text
	for _, required := range []string{`items[0].sessionId: "session-1"`, `items[0].status: "ACTIVE"`, `items[0].phase: "RUNNING"`} {
		if !containsLine(text, required) {
			t.Errorf("missing %q in:\n%s", required, text)
		}
	}
	if strings.Contains(text, "outcome") || strings.Contains(text, "selfDuration") {
		t.Fatalf("execution list invented finalized facts: %s", text)
	}
}

func TestListExecutionsOmittedPageSizeUsesMCPDefault(t *testing.T) {
	options := newMCPTestOptions(t, func(endpoint string) ([]byte, error) {
		if !strings.Contains(endpoint, "pageSize=16") {
			return nil, errors.New("unexpected endpoint: " + endpoint)
		}
		return []byte(`{"items":[],"hasMore":false,"nextCursor":null,"resumeCursor":null,"observedAt":"2026-08-20T20:00:00Z"}`), nil
	})
	result, envelope, err := handleListExecutions(context.Background(), options, listExecutionsInput{})
	if err != nil || result.IsError || envelope.Result == nil || len(envelope.Result.Items) != 0 {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
}

func TestGetExecutionGoldenPreservesProvisionalFactsWithoutDiagnosis(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-execution-detail.json"))
	if err != nil {
		t.Fatal(err)
	}
	options := newMCPTestOptions(t, func(endpoint string) ([]byte, error) {
		if !strings.Contains(endpoint, "/active-executions/session-1") {
			return nil, errors.New("unexpected endpoint: " + endpoint)
		}
		return fixture, nil
	})
	result, envelope, err := handleGetExecution(context.Background(), options, getExecutionInput{SessionID: "session-1"})
	if err != nil || result.IsError || envelope.Result == nil {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	assertJSONGolden(t, "execution-detail.json", envelope)
	text := result.Content[0].(*mcp.TextContent).Text
	for _, required := range []string{
		`execution.sessionId: "session-1"`, `execution.status: "ACTIVE"`,
		`execution.activeBranches.count: 0`, `execution.usage.usageUnits: 15`,
		`execution.configuredLimits.maxUsageUnits: 200000`,
	} {
		if !containsLine(text, required) {
			t.Errorf("missing %q in:\n%s", required, text)
		}
	}
}

func TestGetExecutionRejectsAnIndivisibleDetailAboveTheResponseBudget(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-execution-detail.json"))
	if err != nil {
		t.Fatal(err)
	}
	var execution map[string]any
	if err := json.Unmarshal(fixture, &execution); err != nil {
		t.Fatal(err)
	}
	branches := make([]any, 64)
	for branchIndex := range branches {
		path := make([]any, 20)
		path[0] = map[string]any{"frameId": "root", "frameType": "ROOT_MISSION", "route": "entry"}
		for pathIndex := 1; pathIndex < len(path); pathIndex++ {
			path[pathIndex] = map[string]any{
				"frameId":   fmt.Sprintf("branch-%02d-frame-%02d", branchIndex, pathIndex),
				"frameType": "SKILL_EXECUTION", "route": strings.Repeat("r", 64),
			}
		}
		branches[branchIndex] = map[string]any{"planId": nil, "taskId": nil, "stepNumber": nil, "parallelGroup": nil, "effectiveConcurrency": nil, "path": path}
	}
	execution["activeBranches"] = branches
	body, err := json.Marshal(execution)
	if err != nil {
		t.Fatal(err)
	}
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return body, nil })
	result, envelope, err := handleGetExecution(context.Background(), options, getExecutionInput{SessionID: "session-1"})
	if err != nil || result == nil || !result.IsError || envelope.Error == nil || envelope.Error.Code != "LIMIT_EXCEEDED" || envelope.Result != nil {
		t.Fatalf("result=%#v envelopeError=%+v envelopeResult=%#v err=%v", result, envelope.Error, envelope.Result, err)
	}
}

func TestListExecutionsMaximumPageHas64WholeItems(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-executions-page.json"))
	if err != nil {
		t.Fatal(err)
	}
	var page struct {
		Items      []map[string]any `json:"items"`
		HasMore    bool             `json:"hasMore"`
		NextCursor *string          `json:"nextCursor"`
		ObservedAt string           `json:"observedAt"`
	}
	if err := json.Unmarshal(fixture, &page); err != nil || len(page.Items) == 0 {
		t.Fatalf("decode execution fixture: items=%d err=%v", len(page.Items), err)
	}
	prototype := page.Items[0]
	page.Items = make([]map[string]any, maxMCPPageSize)
	for index := range page.Items {
		encoded, marshalErr := json.Marshal(prototype)
		if marshalErr != nil {
			t.Fatal(marshalErr)
		}
		var item map[string]any
		if err := json.Unmarshal(encoded, &item); err != nil {
			t.Fatal(err)
		}
		item["sessionId"] = fmt.Sprintf("session-%02d", index)
		item["traceId"] = fmt.Sprintf("trace-%02d", index)
		page.Items[index] = item
	}
	next := "next-64"
	page.HasMore, page.NextCursor = true, &next
	body, err := json.Marshal(page)
	if err != nil {
		t.Fatal(err)
	}
	options := newMCPTestOptions(t, func(string) ([]byte, error) { return body, nil })
	result, envelope, err := handleListExecutions(context.Background(), options, listExecutionsInput{PageSize: maxMCPPageSize})
	if err != nil || result.IsError || envelope.Result == nil {
		t.Fatalf("result=%#v envelope=%#v err=%v", result, envelope, err)
	}
	if len(envelope.Result.Items) != maxMCPPageSize || envelope.Result.Items[63].SessionID != "session-63" || envelope.Result.Continuation == "" {
		t.Fatalf("maximum execution page was truncated or incomplete")
	}
	text := result.Content[0].(*mcp.TextContent).Text
	if !containsLine(text, `count: 64`) || !containsLine(text, `items[63].sessionId: "session-63"`) {
		t.Fatalf("maximum execution page text was truncated")
	}
}
