package traceanalysis

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
)

func TestResultForwardingRequiresAuthoritativeScalarIdentity(t *testing.T) {
	fields := map[string]any{"skillName": "parent", "planId": "plan-1", "linkedTaskId": "task-1", "capabilityName": "child"}
	body, _ := json.Marshal(fields)
	result, valid := decodeResultForwarding(body)
	if !valid || result.SkillName != "parent" || result.LinkedTaskID != "task-1" || result.CapabilityName != "child" || result.PlanID != "plan-1" {
		t.Fatalf("forwarding=%#v valid=%v", result, valid)
	}
	for _, field := range []string{"skillName", "planId", "linkedTaskId", "capabilityName"} {
		for _, value := range []any{nil, "", "  ", 7, map[string]any{"value": "x"}, []string{"x"}} {
			original := fields[field]
			fields[field] = value
			body, _ := json.Marshal(fields)
			if _, valid := decodeResultForwarding(body); valid {
				t.Fatalf("accepted %s=%#v", field, value)
			}
			fields[field] = original
		}
		original := fields[field]
		delete(fields, field)
		body, _ := json.Marshal(fields)
		if _, valid := decodeResultForwarding(body); valid {
			t.Fatalf("accepted missing %s", field)
		}
		fields[field] = original
	}
}

func TestForwardingFactsSurviveStoredProjection(t *testing.T) {
	selected := &ResultForwarding{SkillName: "parent", PlanID: "plan", LinkedTaskID: "selected", CapabilityName: "child"}
	facts := persistedRecordFacts{ResultForwarding: selected}
	if facts.empty() {
		t.Fatal("forwarding discarded as empty")
	}
	stored, _ := json.Marshal(facts)
	var restored persistedRecordFacts
	if err := json.Unmarshal(stored, &restored); err != nil {
		t.Fatal(err)
	}
	if *restored.ResultForwarding != *selected {
		t.Fatalf("forwarding changed: %#v", restored)
	}
}

func TestForwardingCorpusRetainsExactSelectedTaskInQueriedRecordFacts(t *testing.T) {
	for _, test := range []struct {
		name  string
		count int
	}{
		{"forwarded-child-result", 1}, {"nested-forwarded-child-result", 2},
	} {
		t.Run(test.name, func(t *testing.T) {
			raw, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", test.name+".ndjson"))
			if err != nil {
				t.Fatal(err)
			}
			var expected []ResultForwarding
			var traceID string
			for _, line := range bytes.Split(bytes.TrimSpace(raw), []byte("\n")) {
				var record struct {
					TraceID  string          `json:"traceId"`
					Type     string          `json:"recordType"`
					Metadata json.RawMessage `json:"metadata"`
				}
				if err := json.Unmarshal(line, &record); err != nil {
					t.Fatal(err)
				}
				traceID = record.TraceID
				if record.Type == "RESULT_FORWARDED" {
					selected, valid := decodeResultForwarding(record.Metadata)
					if !valid {
						t.Fatal("invalid fixture forwarding identity")
					}
					expected = append(expected, *selected)
				}
			}
			expectedBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "expected", test.name+".json"))
			if err != nil {
				t.Fatal(err)
			}
			var projected struct {
				ResultForwardings []ResultForwarding `json:"resultForwardings"`
			}
			if err := json.Unmarshal(expectedBytes, &projected); err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(projected.ResultForwardings, expected) {
				t.Fatalf("Java reference forwarding=%#v canonical=%#v", projected.ResultForwardings, expected)
			}
			if len(expected) != test.count {
				t.Fatalf("forwarding count=%d want=%d", len(expected), test.count)
			}
			h := newServiceTestHarnessForVersion(t, traceID, string(raw), fixtureCompatibilityVersion)
			page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Representation: RecordRepresentationLogical, Filter: RecordFilter{Types: []string{"RESULT_FORWARDED"}}, PageSize: 64})
			if domain != nil {
				t.Fatal(domain)
			}
			if len(page.Items) != len(expected) {
				t.Fatalf("queried records=%d expected=%d", len(page.Items), len(expected))
			}
			for index, record := range page.Items {
				if record.Facts.ResultForwarding == nil || *record.Facts.ResultForwarding != expected[index] {
					t.Fatalf("identity changed: %#v", record.Facts)
				}
				if len(record.Facts.Attempts) != 0 {
					t.Fatal("forwarding invented model attempts")
				}
			}
			// A malformed scalar cannot become successful forwarding provenance.
			var first map[string]any
			lines := bytes.Split(bytes.TrimSpace(raw), []byte("\n"))
			for index, line := range lines {
				if err := json.Unmarshal(line, &first); err != nil {
					t.Fatal(err)
				}
				if first["recordType"] != "RESULT_FORWARDED" {
					continue
				}
				first["metadata"].(map[string]any)["linkedTaskId"] = map[string]any{"task": "wrong"}
				lines[index], _ = json.Marshal(first)
				break
			}
			sink := &fakeSink{}
			_, invalid := newProcessorForVersion(fixtureCompatibilityVersion).Process(artifact.ProcessRequest{Context: context.Background(), Raw: bytesReader(bytes.Join(lines, []byte("\n"))), Sink: sink})
			if invalid == nil {
				t.Fatal("malformed forwarding identity was accepted")
			}
		})
	}
}
