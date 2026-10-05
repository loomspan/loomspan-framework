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

func TestAssemblyDecisionRetainsProvenance(t *testing.T) {
	assembly := &traceanalysis.ResultAssembly{SkillName: "parent", OwningMissionFrameID: "owner", OutputBindings: []traceanalysis.OutputBindingProvenance{{Destination: "/evidence", SourceKind: "input", SourcePath: "", ParentMissionFrameID: "owner"}}}
	record := traceanalysis.RecordSummary{Type: "RESULT_ASSEMBLED", Facts: traceanalysis.RecordFacts{ResultAssembly: assembly}}
	dto := boundedRecordDTOValue(record, evidence.Reference{})
	if dto.ResultAssembly != assembly {
		t.Fatalf("assembly authority changed: %#v", dto)
	}
}
