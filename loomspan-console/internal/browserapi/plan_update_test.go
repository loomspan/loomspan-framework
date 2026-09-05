package browserapi

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
)

func updateRecord(note string) traceanalysis.RecordSummary {
	changes := []traceanalysis.PlanFieldChange{{TaskID: "task-1", Field: "note", Before: nil, After: &note}}
	return traceanalysis.RecordSummary{Sequence: 20, Type: "PLAN_UPDATED", Facts: traceanalysis.RecordFacts{Plan: &traceanalysis.PlanReference{PlanID: "plan-1", CapabilityName: "investigateNetwork"}, PlanUpdate: &traceanalysis.PlanUpdate{PreviousSequence: 10, Availability: "AVAILABLE", Changes: &changes}}, Content: &traceanalysis.ContentDescriptor{ContentRef: "content-ref", Available: true}}
}

func TestBrowserRecordAdmissionPreservesCompleteComparisons(t *testing.T) {
	for _, ref := range []evidence.Reference{evidence.ForTarget(target.ScopeID("scope-1")), {Source: evidence.SourceImported}} {
		item := updateRecord(strings.Repeat("<\n", 9000))
		admit := newRecordPageAdmission(ref)
		if !admit(item) || admit(item) {
			t.Fatal("two medium complete comparisons must paginate")
		}
		if !newRecordPageAdmission(ref)(item) || boundedRecordDTOValue(item, ref).PlanUpdate.Availability != "AVAILABLE" {
			t.Fatal("later page must retain full comparison")
		}
		item = updateRecord("x")
		encoded, _ := json.Marshal(recordDTOValue(item))
		remaining := traceanalysis.MaxDescriptorResponseBytes - recordPageOverhead(ref) - len(encoded)
		*(*item.Facts.PlanUpdate.Changes)[0].After = strings.Repeat("x", remaining+1)
		if !newRecordPageAdmission(ref)(item) || boundedRecordDTOValue(item, ref).PlanUpdate.Availability != "AVAILABLE" {
			t.Fatal("exact empty-page boundary")
		}
		*(*item.Facts.PlanUpdate.Changes)[0].After += "x"
		limited := boundedRecordDTOValue(item, ref)
		if limited.PlanUpdate.Availability != "LIMIT_EXCEEDED" || limited.PlanUpdate.Changes != nil || limited.Plan == nil || limited.Content.ContentRef != "content-ref" {
			t.Fatal("over empty-page limit must preserve other evidence")
		}
		cursor := strings.Repeat("x", maxTraceAnalysisJSONBody)
		response, _ := json.Marshal(pageDTO[recordDTO]{Source: ref.Source, TargetScopeID: string(ref.TargetScope), Items: []recordDTO{limited}, HasMore: true, NextCursor: &cursor})
		if len(response)+1 > traceanalysis.MaxDescriptorResponseBytes {
			t.Fatal("envelope budget exceeded")
		}
		item.Route = strings.Repeat("x", traceanalysis.MaxDescriptorResponseBytes)
		if newRecordPageAdmission(ref)(item) {
			t.Fatal("metadata-only overflow accepted")
		}
	}
}

func TestTraceAnalysisRecordsProjectAndBoundPlanUpdates(t *testing.T) {
	router, _, cookie, fake := traceAnalysisRouter(t)
	changed := updateRecord("<script>\n雪\"\\")
	unchanged := updateRecord("")
	empty := []traceanalysis.PlanFieldChange{}
	unchanged.Facts.PlanUpdate.Changes = &empty
	limited := updateRecord(strings.Repeat("<", traceanalysis.MaxDescriptorResponseBytes))
	missing := updateRecord("")
	missing.Facts.PlanUpdate = nil
	fake.recordPage = traceanalysis.Page[traceanalysis.RecordSummary]{Items: []traceanalysis.RecordSummary{changed, unchanged, limited, missing}}
	response := traceAnalysisRequest(router, "/api/console/v1/traces/analysis/records", `{"source":"TARGET","traceId":"trace-1","representation":"LOGICAL"}`, cookie)
	if response.Code != http.StatusOK || response.Body.Len() > traceanalysis.MaxDescriptorResponseBytes {
		t.Fatalf("response %d %s", response.Code, response.Body.String())
	}
	var page pageDTO[recordDTO]
	if err := json.Unmarshal(response.Body.Bytes(), &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 4 || page.Items[0].PlanUpdate.PreviousSequence != 10 || (*page.Items[0].PlanUpdate.Changes)[0].Before != nil || *(*page.Items[0].PlanUpdate.Changes)[0].After != "<script>\n雪\"\\" {
		t.Fatal("changed projection lost exact values")
	}
	if page.Items[1].PlanUpdate.Changes == nil || len(*page.Items[1].PlanUpdate.Changes) != 0 || page.Items[2].PlanUpdate.Availability != "LIMIT_EXCEEDED" || page.Items[2].PlanUpdate.Changes != nil || page.Items[3].PlanUpdate != nil {
		t.Fatal("missing/empty/limit distinctions lost")
	}
	fake.recordPage.Items = []traceanalysis.RecordSummary{updateRecord(strings.Repeat("<\n", 9000)), updateRecord(strings.Repeat("<\n", 9000))}
	response = traceAnalysisRequest(router, "/api/console/v1/traces/analysis/records", `{"source":"TARGET","traceId":"trace-1"}`, cookie)
	if err := json.Unmarshal(response.Body.Bytes(), &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 1 || !page.HasMore || page.NextCursor == nil {
		t.Fatal("route failed to supply complete-item admission")
	}
}
