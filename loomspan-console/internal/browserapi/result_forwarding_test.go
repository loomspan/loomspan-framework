package browserapi

import (
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
)

func TestBrowserRecordRetainsForwardingDecision(t *testing.T) {
	forwarding := &traceanalysis.ResultForwarding{SkillName: "parent", PlanID: "plan", LinkedTaskID: "selected", CapabilityName: "child"}
	record := traceanalysis.RecordSummary{Type: "RESULT_FORWARDED", Facts: traceanalysis.RecordFacts{ResultForwarding: forwarding}}
	dto := boundedRecordDTOValue(record, evidence.Reference{})
	if dto.ResultForwarding == nil || *dto.ResultForwarding != *forwarding {
		t.Fatalf("forwarding changed: %#v", dto)
	}
}
