package agenteval

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestEvaluationServerUsesProductionAdapterAndThirteenTools(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	server, err := StartServer(t.TempDir(), cases["finalized-tools-only"], "0.1.0-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Close(ctx)
	}()
	post := func(payload string) map[string]any {
		req, _ := http.NewRequest(http.MethodPost, server.Endpoint, bytes.NewBufferString(payload))
		req.Header.Set("Authorization", "Bearer "+server.Key)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json, text/event-stream")
		req.Header.Set("MCP-Protocol-Version", "2025-11-25")
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		raw, _ := io.ReadAll(resp.Body)
		var out map[string]any
		if err := json.Unmarshal(raw, &out); err != nil {
			t.Fatalf("%s: %v", raw, err)
		}
		return out
	}
	post(`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"eval-test","version":"1"}}}`)
	listed := post(`{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}`)
	tools := listed["result"].(map[string]any)["tools"].([]any)
	if len(tools) != 13 {
		t.Fatalf("tools=%d", len(tools))
	}
	found := false
	for _, raw := range tools {
		if raw.(map[string]any)["name"] == "LOOMSPAN_query_trace_plans" {
			found = true
		}
	}
	if !found {
		t.Fatal("plan query missing")
	}
}

func TestLiveEvaluationCaseServesCompleteBranchesThroughProductionAdapter(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	server, err := StartServer(t.TempDir(), cases["live-tools-only"], "0.1.0-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Close(ctx)
	}()
	activityContext, cancelActivity := context.WithTimeout(context.Background(), 5*time.Second)
	activityRequest, _ := http.NewRequestWithContext(activityContext, http.MethodGet, server.upstream.URL+"/api/console/v1/active-executions/session-live-concurrent-siblings/activity", nil)
	activityResponse, err := server.upstream.Client().Do(activityRequest)
	if err != nil {
		cancelActivity()
		t.Fatal(err)
	}
	if activityResponse.StatusCode != http.StatusOK || activityResponse.Header.Get("Content-Type") != "text/event-stream" {
		t.Fatalf("activity fixture status=%d content-type=%q", activityResponse.StatusCode, activityResponse.Header.Get("Content-Type"))
	}
	cancelActivity()
	_ = activityResponse.Body.Close()
	post := func(payload string) map[string]any {
		req, _ := http.NewRequest(http.MethodPost, server.Endpoint, bytes.NewBufferString(payload))
		req.Header.Set("Authorization", "Bearer "+server.Key)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json, text/event-stream")
		req.Header.Set("MCP-Protocol-Version", "2025-11-25")
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		raw, _ := io.ReadAll(resp.Body)
		var out map[string]any
		if err := json.Unmarshal(raw, &out); err != nil {
			t.Fatalf("%s: %v", raw, err)
		}
		return out
	}
	post(`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"eval-test","version":"1"}}}`)
	listed := post(`{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"LOOMSPAN_list_executions","arguments":{"pageSize":64}}}`)
	listedResult := listed["result"].(map[string]any)["structuredContent"].(map[string]any)["result"].(map[string]any)
	items := listedResult["items"].([]any)
	if len(items) != len(cases["live-tools-only"].Fixtures) {
		t.Fatalf("executions=%d fixtures=%d", len(items), len(cases["live-tools-only"].Fixtures))
	}
	wantBranches := map[string]int{
		"session-live-concurrent-siblings":         2,
		"session-live-grouped-disabled":            1,
		"session-live-nested-inheritance":          1,
		"session-live-nested-shadowing":            1,
		"session-live-serial-assigned":             1,
		"session-live-unassigned":                  1,
		"session-live-equal-group-different-plans": 2,
		"session-live-repeated-task-leaves":        2,
	}
	for _, raw := range items {
		sessionID := raw.(map[string]any)["sessionId"].(string)
		result := post(fmt.Sprintf(`{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"LOOMSPAN_get_execution","arguments":{"sessionId":%q}}}`, sessionID))
		call := result["result"].(map[string]any)
		if call["isError"] == true {
			t.Fatalf("call failed for %s: %#v", sessionID, result)
		}
		envelope := call["structuredContent"].(map[string]any)["result"].(map[string]any)
		execution := envelope["execution"].(map[string]any)
		branches := execution["activeBranches"].([]any)
		if len(branches) != wantBranches[sessionID] {
			t.Fatalf("%s branches=%d want=%d", sessionID, len(branches), wantBranches[sessionID])
		}
	}
}

func TestImportedEvaluationCasePresentsAmbiguousSameTimeCandidates(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	server, err := StartServer(t.TempDir(), cases["imported-tools-only"], "0.1.0-SNAPSHOT")
	if err != nil {
		t.Fatal(err)
	}
	defer func() {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Close(ctx)
	}()
	post := func(payload string) map[string]any {
		req, _ := http.NewRequest(http.MethodPost, server.Endpoint, bytes.NewBufferString(payload))
		req.Header.Set("Authorization", "Bearer "+server.Key)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json, text/event-stream")
		req.Header.Set("MCP-Protocol-Version", "2025-11-25")
		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		defer resp.Body.Close()
		raw, _ := io.ReadAll(resp.Body)
		var out map[string]any
		if err := json.Unmarshal(raw, &out); err != nil {
			t.Fatalf("%s: %v", raw, err)
		}
		return out
	}
	post(`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"eval-test","version":"1"}}}`)
	listed := post(`{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"LOOMSPAN_list_traces","arguments":{"sources":["IMPORTED"],"order":"IMPORTED_DESC","pageSize":64}}}`)
	result := listed["result"].(map[string]any)["structuredContent"].(map[string]any)["result"].(map[string]any)
	items := result["items"].([]any)
	if len(items) != 2 || result["complete"] != true || result["hasMore"] != false {
		t.Fatalf("unexpected imported discovery: %#v", result)
	}
	first := items[0].(map[string]any)["importedAt"]
	second := items[1].(map[string]any)["importedAt"]
	if first != second {
		t.Fatalf("import candidates are not equally recent: %v != %v", first, second)
	}
}

func TestBoundedContentEvaluationCaseHasOneExplicitTrace(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	value := cases["bounded-tools-only"]
	if len(value.Fixtures) != 1 || !strings.Contains(value.DeveloperPrompt, "trace-repeated-search-content") {
		t.Fatalf("bounded case must identify one explicit trace: %#v", value)
	}
}
