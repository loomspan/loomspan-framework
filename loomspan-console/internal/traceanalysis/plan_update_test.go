package traceanalysis

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strconv"
	"strings"
	"testing"
)

func TestPlanGraphUpdateFieldChanges(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{"planning": {frameID: "planning", frameType: FramePlanning}}}
	graph := newPlanGraph()
	note := "null"
	data := func(a, b, status string, note *string) json.RawMessage {
		snapshot, _ := decodePlanSnapshot(planData(a, b, status), "plan-1")
		// Deliberately reverse lexical order; accepted task order is authoritative.
		snapshot.Tasks[0].TaskID, snapshot.Tasks[1].TaskID = "z", "a"
		snapshot.Tasks[0].Note, snapshot.Tasks[1].Note = note, note
		encoded, _ := json.Marshal(snapshot)
		return encoded
	}
	records := []*Record{
		planRecord(1, RecordPlanCreated, "planning", data("PENDING", "PENDING", "VALID", nil)),
		planRecord(4, RecordPlanUpdated, "planning", data("PENDING", "PENDING", "VALID", &note)),
		planRecord(5, RecordPlanUpdated, "planning", data("PENDING", "PENDING", "VALID", &note)),
		planTransitionRecord(7, "planning", data("IN_PROGRESS", "IN_PROGRESS", "VALID", nil), `{"kind":"ADMISSION","taskIds":["z","a"],"parallelGroup":"batch","effectiveConcurrency":true}`),
		planTransitionRecord(9, "planning", data("COMPLETED", "FAILED", "STALE", &note), `{"kind":"JOIN","taskIds":["z","a"],"parallelGroup":"batch","outcome":"FAILED"}`),
		planRecord(11, RecordPlanUpdated, "planning", data("COMPLETED", "FAILED", "INVALID", &note)),
	}
	for _, record := range records {
		if err := graph.onRecord(record, frames); err != nil {
			t.Fatal(err)
		}
	}
	updates := graph.updates()
	if updates[1] != nil || updates[4].PreviousSequence != 1 || updates[5].PreviousSequence != 4 || updates[7].PreviousSequence != 5 || updates[9].PreviousSequence != 7 {
		t.Fatalf("wrong predecessor: %+v", updates)
	}
	if updates[5].Changes == nil || len(*updates[5].Changes) != 0 || updates[5].Availability != "AVAILABLE" {
		t.Fatal("unchanged must be empty success")
	}
	if changes := *updates[4].Changes; len(changes) != 2 || changes[0].Before != nil || *changes[0].After != "null" {
		t.Fatalf("null/text: %+v", changes)
	}
	wantFields := []string{".status", "z.status", "z.note", "a.status", "a.note"}
	fields := []string{}
	for _, change := range *updates[9].Changes {
		fields = append(fields, change.TaskID+"."+change.Field)
	}
	if !reflect.DeepEqual(fields, wantFields) || *(*updates[9].Changes)[3].After != "FAILED" {
		t.Fatalf("order/values: %+v", *updates[9].Changes)
	}
	if changes := *updates[11].Changes; len(changes) != 1 || changes[0].TaskID != "" || *changes[0].After != "INVALID" {
		t.Fatalf("plan only: %+v", changes)
	}
}

func TestPlanGraphFieldChangesRespectInterleavedPlanIdentity(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{"outer": {frameID: "outer", frameType: FramePlanning}, "inner": {frameID: "inner", frameType: FramePlanning}}}
	graph := newPlanGraph()
	for i, plan := range []string{"outer", "inner", "outer", "inner", "outer"} {
		kind := RecordPlanUpdated
		if i < 2 {
			kind = RecordPlanCreated
		}
		record := planRecord(int64(i+1), kind, plan, planData("PENDING", "PENDING", "VALID"))
		record.Data = json.RawMessage(strings.ReplaceAll(string(record.Data), "plan-1", plan))
		record.Metadata = json.RawMessage(`{"planId":"` + plan + `"}`)
		if err := graph.onRecord(record, frames); err != nil {
			t.Fatal(err)
		}
	}
	updates := graph.updates()
	if updates[3].PreviousSequence != 1 || updates[4].PreviousSequence != 2 || updates[5].PreviousSequence != 3 {
		t.Fatalf("cross-plan predecessor: %+v", updates)
	}
}

func TestPlanUpdateOversizeIsExplicitAndDoesNotInvalidateTrace(t *testing.T) {
	before, _ := decodePlanSnapshot(planData("PENDING", "PENDING", "VALID"), "plan-1")
	after, _ := decodePlanSnapshot(planData("PENDING", "PENDING", "VALID"), "plan-1")
	note := "x"
	after.Tasks[0].Note = &note
	base := comparePlanSnapshots(before, after, 1)
	encoded, _ := json.Marshal(base)
	note = strings.Repeat("x", MaxDescriptorResponseBytes-len(encoded)+1)
	exact := comparePlanSnapshots(before, after, 1)
	encoded, _ = json.Marshal(exact)
	if exact.Availability != "AVAILABLE" || len(encoded) != MaxDescriptorResponseBytes {
		t.Fatalf("exact boundary: %s %d", exact.Availability, len(encoded))
	}
	note += "x"
	limited := comparePlanSnapshots(before, after, 1)
	if limited.Availability != "LIMIT_EXCEEDED" || limited.Changes != nil || limited.PreviousSequence != 1 {
		t.Fatalf("limit: %+v", limited)
	}
	note = strings.Repeat("<\n", 20000)
	if comparePlanSnapshots(before, after, 1).Availability != "LIMIT_EXCEEDED" {
		t.Fatal("JSON escape expansion not counted")
	}
	note = "<tag>\n雪\\\""
	update := comparePlanSnapshots(before, after, 1)
	note = "mutated"
	if *(*update.Changes)[0].After != "<tag>\n雪\\\"" {
		t.Fatal("comparison retained mutable pointer")
	}
	// An otherwise valid combined row may be near the fact-store bound.
	row := persistedRecordFacts{Plan: &PlanReference{PlanID: strings.Repeat("p", maxFactRowBytes-150)}, PlanUpdate: exact}
	body, err := marshalRecordFacts(row)
	if err != nil || len(body) > maxFactRowBytes {
		t.Fatalf("fallback failed %d %v", len(body), err)
	}
	var decoded persistedRecordFacts
	if json.Unmarshal(body, &decoded) != nil || decoded.PlanUpdate.Availability != "LIMIT_EXCEEDED" || decoded.PlanUpdate.Changes != nil {
		t.Fatal("combined row silently lost comparison")
	}
	row.PlanUpdate = nil
	row.Plan.PlanID = strings.Repeat("p", maxFactRowBytes)
	body, _ = marshalRecordFacts(row)
	if len(body) <= maxFactRowBytes {
		t.Fatal("unrelated oversize row was altered")
	}
}

func TestRecordFactsRoundTripPlanUpdateChanges(t *testing.T) {
	raw, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "current-plan-semantic-evidence.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForVersion(t, "trace-current-plan-semantic-evidence", string(raw), fixtureCompatibilityVersion)
	previous := map[string]int64{}
	var cursor string
	seen := map[int64]bool{}
	for {
		admitted := 0
		page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, PageSize: 100, Cursor: cursor, Admit: func(RecordSummary) bool { admitted++; return admitted <= 2 }})
		if domain != nil {
			t.Fatal(domain)
		}
		for _, record := range page.Items {
			if seen[record.Sequence] {
				t.Fatal("pagination repeated record")
			}
			seen[record.Sequence] = true
			if record.Facts.Plan == nil {
				continue
			}
			id := record.Facts.Plan.PlanID
			update := record.Facts.PlanUpdate
			if record.Type == string(RecordPlanUpdated) {
				if update == nil || update.PreviousSequence != previous[id] || update.Availability != "AVAILABLE" || update.Changes == nil {
					t.Fatalf("roundtrip %d: %+v", record.Sequence, update)
				}
				for _, representation := range []RecordRepresentation{RecordRepresentationLogical, RecordRepresentationPhysical} {
					exact, err := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Filter: RecordFilter{MinSequence: &record.Sequence, MaxSequence: &record.Sequence}, Representation: representation})
					if err != nil || len(exact.Items) != 1 || !reflect.DeepEqual(exact.Items[0].Facts.PlanUpdate, update) {
						t.Fatalf("exact query: %+v %v", exact, err)
					}
				}
			} else if update != nil {
				t.Fatal("creation has diff")
			}
			previous[id] = record.Sequence
		}
		if !page.HasMore {
			break
		}
		cursor = page.NextCursor
	}
	if len(previous) < 2 || len(seen) < 10 {
		t.Fatal("fixture did not exercise nested pagination")
	}
}

func TestOversizedPlanComparisonKeepsAcquiredArtifactAndContent(t *testing.T) {
	testPlanComparisonArtifact(t, strings.Repeat("<", 25000), "LIMIT_EXCEEDED")
}

func TestRecordFactsRoundTripExactNullablePlanNotes(t *testing.T) {
	testPlanComparisonArtifact(t, "<tag>\n雪\\\"", "AVAILABLE")
}

func testPlanComparisonArtifact(t *testing.T, prefix, availability string) {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "current-plan-semantic-evidence.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	var rewritten strings.Builder
	for _, line := range strings.Split(strings.TrimSpace(string(raw)), "\n") {
		var record map[string]json.RawMessage
		if err := json.Unmarshal([]byte(line), &record); err != nil {
			t.Fatal(err)
		}
		if string(record["recordType"]) == `"PLAN_CREATED"` || string(record["recordType"]) == `"PLAN_UPDATED"` {
			var snapshot planSnapshot
			if err := json.Unmarshal(record["data"], &snapshot); err != nil {
				t.Fatal(err)
			}
			snapshot.Tasks = []planTaskSnapshot{{TaskID: "task-1", Title: "Task", Status: "PENDING", DependsOn: []string{}, ExpectedOutputs: []string{}}}
			note := prefix + string(record["sequence"])
			if string(record["recordType"]) == `"PLAN_UPDATED"` {
				snapshot.Tasks[0].Note = &note
			}
			record["data"], _ = json.Marshal(snapshot)
		}
		encoded, _ := json.Marshal(record)
		rewritten.Write(encoded)
		rewritten.WriteByte('\n')
	}
	h := newServiceTestHarnessForVersion(t, "trace-current-plan-semantic-evidence", rewritten.String(), fixtureCompatibilityVersion)
	page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Filter: RecordFilter{Types: []string{"PLAN_UPDATED"}}, PageSize: 100})
	if domain != nil || len(page.Items) == 0 {
		t.Fatalf("artifact invalidated: %v", domain)
	}
	previous := map[string]*string{}
	for _, record := range page.Items {
		if record.Facts.Plan == nil || record.Facts.PlanUpdate == nil || record.Facts.PlanUpdate.Availability != availability || record.Content == nil || record.Content.ContentRef == "" || record.Raw.Length == 0 {
			t.Fatalf("lost forensic/navigation evidence at %d", record.Sequence)
		}
		update := record.Facts.PlanUpdate
		if availability == "LIMIT_EXCEEDED" {
			if update.Changes != nil {
				t.Fatal("limited comparison retained partial values")
			}
		} else {
			want := prefix + strconv.FormatInt(record.Sequence, 10)
			if update.Changes == nil || len(*update.Changes) != 1 {
				t.Fatal("missing complete note comparison")
			}
			change := (*update.Changes)[0]
			if change.After == nil || *change.After != want || !nullableStringEqual(change.Before, previous[record.Facts.Plan.PlanID]) {
				t.Fatalf("nullable values lost at %d: %+v", record.Sequence, change)
			}
			previous[record.Facts.Plan.PlanID] = &want
		}
	}
	plans, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle})
	if domain != nil || len(plans.Items) < 2 {
		t.Fatalf("plans unavailable: %v", domain)
	}
}
