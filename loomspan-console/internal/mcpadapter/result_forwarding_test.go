package mcpadapter

import (
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
)

func TestMCPRecordRetainsForwardingDecision(t *testing.T) {
	forwarding := &traceanalysis.ResultForwarding{SkillName: "parent", PlanID: "plan", LinkedTaskID: "selected", CapabilityName: "child"}
	record := traceanalysis.RecordSummary{Type: "RESULT_FORWARDED", Facts: traceanalysis.RecordFacts{ResultForwarding: forwarding}}
	dto := mapRecord(record)
	if dto.ResultForwarding == nil || *dto.ResultForwarding != *forwarding {
		t.Fatalf("forwarding changed: %#v", dto)
	}
	if len(dto.Attempts) != 0 {
		t.Fatalf("invented model attempt: %#v", dto.Attempts)
	}
}
