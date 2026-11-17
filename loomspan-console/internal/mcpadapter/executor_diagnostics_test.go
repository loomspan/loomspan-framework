package mcpadapter

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

func TestExecutorDiagnosticDiscoveryAndActualMCPBrowserTraversal(t *testing.T) {
	for _, name := range []string{"java-root-success", "nested-frame-usage", "executor-terminal-failure", "executor-recoverable-failure"} {
		t.Run(name, func(t *testing.T) {
			raw, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "traces", name+".ndjson"))
			if err != nil {
				t.Fatal(err)
			}
			raw = bytes.ReplaceAll(raw, []byte(`"consoleCompatibilityVersion":"1.0.0-beta.10-SNAPSHOT"`), []byte(`"consoleCompatibilityVersion":"development"`))
			h := newRealSemanticHarnessFromRaw(t, raw)
			options := newMCPTestOptions(t, nil)
			options.TraceAnalysis = h.analysis
			options.TraceResolver = &fakeTraceArtifacts{result: h.acquired, ref: evidence.ForImported()}
			session := semanticFixtureSessionWithOptions(t, options)
			tools, err := session.ListTools(context.Background(), nil)
			if err != nil {
				t.Fatal(err)
			}
			if len(tools.Tools) != 13 {
				t.Fatalf("unexpected new tool: %d", len(tools.Tools))
			}
			for _, tool := range tools.Tools {
				if tool.Name == QueryTraceRecordsToolName {
					input, _ := json.Marshal(tool.InputSchema)

					for _, kind := range []string{"EXECUTOR_DIAGNOSTIC_RECORDED", "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED"} {
						if !bytes.Contains(input, []byte(kind)) {
							t.Fatalf("discovery omitted %s", kind)
						}
					}
				}
				if tool.Name == GetTraceToolName || tool.Name == QueryTraceFramesToolName || tool.Name == GetExecutionToolName {
					if !strings.Contains(tool.Description, "Framework-accounted usage") {
						t.Fatalf("%s omitted accounting scope", tool.Name)
					}
				}
			}
			browser := newSemanticBrowserPost(t, h)
			traceID := h.acquired.Metadata.TraceID
			type physical struct {
				Sequence int64                      `json:"sequence"`
				Type     string                     `json:"recordType"`
				FrameID  string                     `json:"frameId"`
				Data     json.RawMessage            `json:"data"`
				Metadata map[string]json.RawMessage `json:"metadata"`
			}
			var inventory []physical
			frames := map[string]bool{"": true, "unrelated-frame": true}
			chunks := map[string][]byte{}
			for _, line := range bytes.Split(bytes.TrimSpace(raw), []byte("\n")) {
				var rec physical
				if err := json.Unmarshal(line, &rec); err != nil {
					t.Fatal(err)
				}
				inventory = append(inventory, rec)
				if strings.HasPrefix(rec.Type, "EXECUTOR_DIAGNOSTIC_") {
					frames[rec.FrameID] = true
				}
				if rec.Type == "PAYLOAD_CHUNK_APPENDED" {
					var id, part string
					_ = json.Unmarshal(rec.Metadata["payloadId"], &id)
					_ = json.Unmarshal(rec.Data, &part)
					chunks[id] = append(chunks[id], []byte(part)...)
				}
			}
			selections := [][]string{nil, {"EXECUTOR_DIAGNOSTIC_RECORDED"}, {"EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED"}, {"EXECUTOR_DIAGNOSTIC_RECORDED", "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED"}, {"FRAME_OPENED", "EXECUTOR_DIAGNOSTIC_RECORDED"}}
			for _, representation := range []string{"LOGICAL", "PHYSICAL"} {
				for _, types := range selections {
					for frame := range frames {
						var expected []int64
						for _, rec := range inventory {
							if representation == "LOGICAL" && rec.Type == "PAYLOAD_CHUNK_APPENDED" {
								continue
							}
							if len(types) > 0 && !slicesContain(types, rec.Type) || frame != "" && rec.FrameID != frame {
								continue
							}
							expected = append(expected, rec.Sequence)
						}
						var actual []int64
						continuation := ""
						for {
							args := map[string]any{"traceId": traceID, "pageSize": 2, "representation": representation, "inlineContent": false, "filter": map[string]any{"types": types, "frameId": frame}}
							if types == nil {
								args["filter"] = map[string]any{"frameId": frame}
							}
							if continuation != "" {
								args["continuation"] = continuation
							}
							call, err := session.CallTool(context.Background(), &mcp.CallToolParams{Name: QueryTraceRecordsToolName, Arguments: args})
							if err != nil || call.IsError {
								t.Fatalf("actual MCP records=%+v err=%v args=%v", call, err, args)
							}
							encoded, _ := json.Marshal(call.StructuredContent)
							var envelope struct {
								Result queryRecordsResult `json:"result"`
							}
							if err := json.Unmarshal(encoded, &envelope); err != nil {
								t.Fatal(err)
							}
							page := envelope.Result
							browserArgs := cloneSemanticRequest(args)
							browserArgs["source"] = "IMPORTED"
							delete(browserArgs, "continuation")
							if continuation != "" {
								browserArgs["cursor"] = continuation
							}
							body, _ := json.Marshal(browserArgs)
							// Browser and MCP continuations are transport-neutral and refer to the same bound artifact.
							browserPage := browser("/api/console/v1/traces/analysis/records", string(body))
							assertAdapterItemsEqual(t, browserPage["items"], page.Items, "raw", "facts", "threadName", "isChunk", "isEnvelope", "attempts", "retries", "validations", "failures")
							for _, rec := range page.Items {
								actual = append(actual, rec.Sequence)
								if rec.FrameID != inventory[rec.Sequence-1].FrameID || rec.Type != inventory[rec.Sequence-1].Type {
									t.Fatalf("record lost canonical identity: %+v", rec)
								}
								if rec.Content != nil && len(rec.Content.InlineContent) > 0 {
									t.Fatal("eager inline content")
								}
							}
							if !page.HasMore {
								break
							}
							if page.Continuation == "" || page.Continuation == continuation {
								t.Fatal("non-progressing continuation")
							}
							continuation = page.Continuation
						}
						if !reflect.DeepEqual(actual, expected) {
							t.Fatalf("%s types=%v frame=%s sequence=%v want=%v", representation, types, frame, actual, expected)
						}
					}
				}
			}
			// Read retained diagnostic DATA with actual MCP calls and independent browser ranges.
			call, page, err := handleQueryTraceRecords(context.Background(), options, queryTraceRecordsInput{TraceID: traceID, PageSize: 64, Filter: traceanalysis.RecordFilter{Types: []string{"EXECUTOR_DIAGNOSTIC_RECORDED", "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED"}}})
			if err != nil || call.IsError || page.Result == nil || len(page.Result.Items) == 0 {
				t.Fatalf("diagnostics=%+v err=%v", page, err)
			}
			for _, rec := range page.Result.Items {
				if rec.Content == nil {
					t.Fatal("diagnostic lost descriptor")
				}
				original := inventory[rec.Sequence-1]
				want := []byte(original.Data)
				if _, chunked := original.Metadata["payloadId"]; chunked {
					var id string
					_ = json.Unmarshal(original.Metadata["payloadId"], &id)
					want = chunks[id]
				}
				var got []byte
				continuation := ""
				for {
					args := map[string]any{"traceId": traceID, "contentRef": rec.Content.ContentRef, "maxBytes": 257}
					if continuation != "" {
						args["continuation"] = continuation
					}
					result, err := session.CallTool(context.Background(), &mcp.CallToolParams{Name: ReadTraceContentToolName, Arguments: args})
					if err != nil || result.IsError {
						t.Fatalf("content=%+v err=%v", result, err)
					}
					encoded, _ := json.Marshal(result.StructuredContent)
					var envelope struct {
						Result rangeResult `json:"result"`
					}
					_ = json.Unmarshal(encoded, &envelope)
					part := envelope.Result
					browserArgs := cloneSemanticRequest(args)
					browserArgs["source"] = "IMPORTED"
					delete(browserArgs, "continuation")
					if continuation != "" {
						browserArgs["continueCursor"] = continuation
					}
					body, _ := json.Marshal(browserArgs)
					browserPart := browser("/api/console/v1/traces/analysis/content-range", string(body))
					if browserPart["content"] != part.Content || browserPart["encoding"] != part.Encoding {
						t.Fatal("browser/MCP retained content mismatch")
					}
					decoded := []byte(part.Content)
					if part.Encoding == "BASE64" {
						decoded, err = base64.StdEncoding.DecodeString(part.Content)
						if err != nil {
							t.Fatal(err)
						}
					}
					got = append(got, decoded...)
					if !part.HasMore {
						break
					}
					if part.Continuation == "" || part.Continuation == continuation {
						t.Fatal("non-progressing range")
					}
					continuation = part.Continuation
				}
				if !bytes.Equal(got, want) {
					t.Fatalf("sequence %d retained bytes differ", rec.Sequence)
				}
				if len(rec.Failures) > 0 || len(rec.Attempts) > 0 || len(rec.Retries) > 0 {
					t.Fatal("authored JSON generated Framework facts")
				}
			}
			_, summary, err := handleGetTrace(context.Background(), options, getTraceInput{TraceID: traceID})
			if err != nil || summary.Result == nil {
				t.Fatal(err)
			}
			if name == "nested-frame-usage" {
				facts := summary.Result.Summary
				if facts.AttributedUsage != (usageDTO{PromptUnits: 5, CompletionUnits: 3, TotalUnits: 8}) || facts.TerminalUsage != facts.AttributedUsage || facts.AttemptCount != 2 || facts.FailureCount != 0 || facts.Outcome != "SUCCEEDED" {
					t.Fatalf("diagnostic usage/outcome changed native facts: %+v", facts)
				}
				_, frames, err := handleQueryTraceFrames(context.Background(), options, queryTraceFramesInput{TraceID: traceID, Projection: traceanalysis.FrameProjectionDetailed, Filter: traceanalysis.FrameFilter{FrameIDs: []string{"executor-child"}}})
				if err != nil || frames.Result == nil || len(frames.Result.Items) != 1 {
					t.Fatalf("executor frame=%+v err=%v", frames, err)
				}
				child := frames.Result.Items[0]
				if child.FrameType != "ROOT_MISSION" || child.ParentFrameID == nil || *child.ParentFrameID != "skill" || child.InclusiveUsage == nil || child.InclusiveUsage.TotalUnits != 0 || len(child.AttemptIDs) > 0 || len(child.FailureIDs) > 0 {
					t.Fatalf("executor child facts=%+v", child)
				}
			}
			if strings.HasPrefix(name, "executor-") {
				classification := "TERMINAL"
				if name == "executor-recoverable-failure" {
					classification = "RECOVERABLE"
				}
				if summary.Result.Summary.Outcome != "FAILED" || summary.Result.Summary.AttemptCount != 0 || summary.Result.Summary.FailureCount != 1 {
					t.Fatalf("designated invocation failure facts=%+v", summary.Result.Summary)
				}
				_, failed, err := handleQueryTraceRecords(context.Background(), options, queryTraceRecordsInput{TraceID: traceID, Filter: traceanalysis.RecordFilter{Types: []string{"ERROR_RECORDED"}}})
				if err != nil || failed.Result == nil || len(failed.Result.Items) != 1 || len(failed.Result.Items[0].Failures) != 1 {
					t.Fatalf("failure records=%+v err=%v", failed, err)
				}
				failure := failed.Result.Items[0].Failures[0]
				if failure.Classification != classification || !failure.Terminal || failure.FrameID != "executor-failed" {
					t.Fatalf("failure authority=%+v", failure)
				}
			}
			browserSummary := browser("/api/console/v1/traces/analysis/summary", fmt.Sprintf(`{"source":"IMPORTED","traceId":%q}`, traceID))
			for field, want := range map[string]any{"attributedUsage": summary.Result.Summary.AttributedUsage, "terminalUsage": summary.Result.Summary.TerminalUsage, "attemptCount": summary.Result.Summary.AttemptCount, "failureCount": summary.Result.Summary.FailureCount, "outcome": summary.Result.Summary.Outcome} {
				normalized, _ := json.Marshal(want)
				var value any
				_ = json.Unmarshal(normalized, &value)
				if !reflect.DeepEqual(browserSummary[field], value) {
					t.Fatalf("summary %s mismatch browser=%v MCP=%v", field, browserSummary[field], value)
				}
			}
		})
	}
}
