package traceanalysis

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/evidence"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/target"
)

func TestPlanExecutionUnitObservedOverlapTruthTable(t *testing.T) {
	group := "g"
	trueValue, falseValue := true, false
	frameID := func(value string) *string { return &value }
	mode := func(value bool) *bool { return &value }
	frames := &frameGraph{frames: map[string]*frameBuild{
		"a":    {frameID: "a", openedMillis: 0, closedMillis: 10, closed: true},
		"b":    {frameID: "b", openedMillis: 5, closedMillis: 15, closed: true},
		"c":    {frameID: "c", openedMillis: 10, closedMillis: 20, closed: true},
		"open": {frameID: "open", openedMillis: 1},
	}}
	cases := []struct {
		name  string
		tasks []PlanTaskSummary
		want  *bool
	}{
		{"singleton", []PlanTaskSummary{{TaskID: "a"}}, nil},
		{"group-never-admitted", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group}, {TaskID: "b", ParallelGroup: &group}}, nil},
		{"positive-width", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("a")}, {TaskID: "b", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("b")}}, &trueValue},
		{"adjacent-false", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group, EffectiveConcurrency: mode(false), AssignedFrameID: frameID("a")}, {TaskID: "c", ParallelGroup: &group, EffectiveConcurrency: mode(false), AssignedFrameID: frameID("c")}}, &falseValue},
		{"never-admitted-does-not-prevent-false", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("a")}, {TaskID: "c", ParallelGroup: &group}}, &falseValue},
		{"admitted-missing-unknown", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("a")}, {TaskID: "b", ParallelGroup: &group, EffectiveConcurrency: mode(true)}}, nil},
		{"admitted-incomplete-unknown", []PlanTaskSummary{{TaskID: "a", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("a")}, {TaskID: "b", ParallelGroup: &group, EffectiveConcurrency: mode(true), AssignedFrameID: frameID("open")}}, nil},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := observedOverlap(tc.tasks, frames)
			if got == nil != (tc.want == nil) || got != nil && *got != *tc.want {
				t.Fatalf("got=%v want=%v", got, tc.want)
			}
		})
	}
}

func TestServiceQueryPlansCanonicalOrderPaginationExactSelectionAndCompleteAdmission(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "current-plan-semantic-evidence.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForVersion(t, "trace-current-plan-semantic-evidence", string(traceBytes), fixtureCompatibilityVersion)
	first, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 1})
	if domain != nil || len(first.Items) != 1 || !first.HasMore || first.NextCursor == "" {
		t.Fatalf("first=%+v domain=%v", first, domain)
	}
	second, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 1, Cursor: first.NextCursor})
	if domain != nil || len(second.Items) != 1 || second.HasMore || first.Items[0].CreationSequence >= second.Items[0].CreationSequence {
		t.Fatalf("second=%+v domain=%v", second, domain)
	}
	selected, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PlanID: "framework-nested-plan", PageSize: 1})
	if domain != nil || len(selected.Items) != 1 || selected.Items[0].PlanID != "framework-nested-plan" {
		t.Fatalf("selected=%+v domain=%v", selected, domain)
	}
	if _, changed := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PlanID: "framework-nested-plan", PageSize: 1, Cursor: first.NextCursor}); changed == nil {
		t.Fatal("changed selector accepted continuation")
	}
	if _, limited := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 10, Admit: func(PlanSummary) bool { return false }}); limited == nil {
		t.Fatal("over-budget first whole plan accepted")
	}
	count := 0
	partial, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 10, Admit: func(PlanSummary) bool { count++; return count == 1 }})
	if domain != nil || len(partial.Items) != 1 || !partial.HasMore || partial.NextCursor == "" {
		t.Fatalf("partial=%+v domain=%v", partial, domain)
	}
	resumed, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 10, Cursor: partial.NextCursor, Admit: func(PlanSummary) bool { return true }})
	if domain != nil || len(resumed.Items) != 1 || resumed.Items[0].PlanID == partial.Items[0].PlanID {
		t.Fatalf("resumed=%+v domain=%v", resumed, domain)
	}
	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	if _, domain := h.service.QueryPlans(cancelled, targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 1}); domain == nil {
		t.Fatal("cancelled plan query succeeded")
	}
	if _, domain := h.service.QueryPlans(context.Background(), evidence.ForTarget(target.ScopeID("other")), PlanQuery{Handle: h.handle, PageSize: 1}); domain == nil {
		t.Fatal("wrong-scope plan query succeeded")
	}
	if _, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: artifact.Handle("wrong"), PageSize: 1}); domain == nil {
		t.Fatal("wrong-handle plan query succeeded")
	}
	imported, importDomain := h.artifacts.Import(context.Background(), strings.NewReader(string(traceBytes)), int64(len(traceBytes)))
	if importDomain != nil {
		t.Fatal(importDomain)
	}
	importedPage, domain := h.service.QueryPlans(context.Background(), evidence.ForImported(), PlanQuery{Handle: imported.Handle, PageSize: 1})
	if domain != nil || importedPage.Context.Evidence != evidence.ForImported() || len(importedPage.Items) != 1 || importedPage.NextCursor == "" {
		t.Fatalf("imported=%+v domain=%v", importedPage, domain)
	}
	if _, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 1, Cursor: importedPage.NextCursor}); domain == nil {
		t.Fatal("imported cursor crossed evidence owner")
	}
}

func TestPlanProjectionUsesAcceptedOrderAdmissionsAssignmentsAndTransitions(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "canonical-concurrent-contract.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForVersion(t, "trace-canonical-concurrent-contract", string(traceBytes), fixtureCompatibilityVersion)
	page, domain := h.service.QueryPlans(context.Background(), targetEvidence(h.scopeID), PlanQuery{Handle: h.handle, PageSize: 10})
	if domain != nil || len(page.Items) != 1 {
		t.Fatalf("page=%+v domain=%v", page, domain)
	}
	plan := page.Items[0]
	if len(plan.Tasks) == 0 || len(plan.ExecutionUnits) == 0 || len(plan.Transitions) == 0 {
		t.Fatalf("incomplete projection: %+v", plan)
	}
	for index, task := range plan.Tasks {
		if task.StepNumber != int64(index+1) {
			t.Fatalf("task order: %+v", plan.Tasks)
		}
	}
	cleanup := plan.Tasks[len(plan.Tasks)-1]
	if cleanup.TaskID != "task-c" || cleanup.EffectiveConcurrency == nil || !*cleanup.EffectiveConcurrency || cleanup.AssignedFrameID != nil {
		t.Fatalf("dispatch-without-frame assignment=%+v", cleanup)
	}
	if taskA := plan.Tasks[3]; len(taskA.FailureIDs) != 1 || taskA.FailureIDs[0] != "failure-model-a" {
		t.Fatalf("descendant failure linkage=%+v", taskA.FailureIDs)
	}
	encoded, err := json.Marshal(plan.Tasks[0])
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(encoded), `"dependsOn":null`) || strings.Contains(string(encoded), `"expectedOutputs":null`) {
		t.Fatalf("accepted task arrays lost their empty-array shape: %s", encoded)
	}
	if len(plan.ExecutionUnits) != 3 || plan.ExecutionUnits[0].ObservedOverlap == nil || *plan.ExecutionUnits[0].ObservedOverlap || plan.ExecutionUnits[2].ObservedOverlap == nil || !*plan.ExecutionUnits[2].ObservedOverlap {
		t.Fatalf("overlap facts=%+v", plan.ExecutionUnits)
	}
	if plan.Transitions[len(plan.Transitions)-1].Outcome != "FAILED" {
		t.Fatalf("transitions=%+v", plan.Transitions)
	}
	detailed, frameDomain := h.service.QueryFrames(context.Background(), targetEvidence(h.scopeID), FrameQuery{Handle: h.handle, Projection: FrameProjectionDetailed, PageSize: 100})
	if frameDomain != nil {
		t.Fatal(frameDomain)
	}
	foundInherited := false
	for _, frame := range detailed.Items {
		if frame.FrameID == "model-a" {
			foundInherited = frame.PlanID != nil && *frame.PlanID == plan.PlanID && frame.TaskID != nil && *frame.TaskID == "task-a" && frame.EffectiveConcurrency != nil && *frame.EffectiveConcurrency
		}
	}
	if !foundInherited {
		t.Fatal("detailed descendant did not inherit nearest assignment")
	}
	compact, frameDomain := h.service.QueryFrames(context.Background(), targetEvidence(h.scopeID), FrameQuery{Handle: h.handle, Projection: FrameProjectionCompact, PageSize: 100})
	if frameDomain != nil {
		t.Fatal(frameDomain)
	}
	for _, frame := range compact.Items {
		if frame.PlanID != nil || frame.TaskID != nil || frame.StepNumber != nil || frame.ParallelGroup != nil || frame.EffectiveConcurrency != nil {
			t.Fatalf("compact assignment leaked: %+v", frame)
		}
	}
}

func TestAssignmentResolverRejectsRawFrameAssignmentWithoutCanonicalRelationship(t *testing.T) {
	frame := &frameBuild{frameID: "step", frameType: FrameStepExecution, assignment: &frameAssignment{
		planID: "plan", taskID: "task", stepNumber: 1,
	}}
	frames := &frameGraph{rootID: "step", order: []string{"step"}, frames: map[string]*frameBuild{"step": frame}}
	resolver := &assignmentResolver{frames: frames, exact: map[planTaskKey]*planTaskAssignment{}}
	if assignment, valid := resolver.nearestAssignedAncestor("step"); valid || assignment != nil {
		t.Fatalf("raw decoder assignment bypassed canonical relationship: assignment=%+v valid=%v", assignment, valid)
	}
}

func TestPlanProjectionCorrelatesFailuresThroughNearestAssignedAncestor(t *testing.T) {
	frames := &frameGraph{rootID: "root", order: []string{"root", "outer-planning", "outer-step", "outer-child", "inner-planning", "inner-step", "inner-model", "unassigned"}, frames: map[string]*frameBuild{}}
	add := func(id, parent string, kind TraceFrameType) {
		frames.frames[id] = &frameBuild{frameID: id, parentFrameID: parent, hasParent: parent != "", frameType: kind, opened: true, closed: true}
	}
	add("root", "", FrameRootMission)
	add("outer-planning", "root", FramePlanning)
	add("outer-step", "root", FrameStepExecution)
	add("outer-child", "outer-step", FrameModelCall)
	add("inner-planning", "outer-step", FramePlanning)
	add("inner-step", "outer-step", FrameStepExecution)
	add("inner-model", "inner-step", FrameModelCall)
	add("unassigned", "root", FrameModelCall)
	outerFrame, innerFrame := "outer-step", "inner-step"
	resolver := &assignmentResolver{frames: frames, exact: map[planTaskKey]*planTaskAssignment{
		{"outer-plan", "outer-task"}: {planID: "outer-plan", taskID: "outer-task", stepNumber: 1, assignedFrameID: &outerFrame},
		{"inner-plan", "inner-task"}: {planID: "inner-plan", taskID: "inner-task", stepNumber: 1, assignedFrameID: &innerFrame},
	}}
	frames.frames["outer-step"].assignment = &frameAssignment{planID: "outer-plan", taskID: "outer-task", stepNumber: 1}
	frames.frames["inner-step"].assignment = &frameAssignment{planID: "inner-plan", taskID: "inner-task", stepNumber: 1}
	task := func(id string) []planTaskSnapshot {
		return []planTaskSnapshot{{TaskID: id, Title: id, Status: "COMPLETED", DependsOn: []string{}, ExpectedOutputs: []string{}}}
	}
	graph := &planGraph{lineages: map[string]*planLineageBuild{
		"outer-plan": {planID: "outer-plan", planningFrameID: "outer-planning", records: []planRecordBuild{{sequence: 1, created: true}}, snapshot: &planSnapshot{PlanID: "outer-plan", CapabilityName: "outer", CreatedAt: "2026-08-30T00:00:00Z", Status: "VALID", Tasks: task("outer-task")}},
		"inner-plan": {planID: "inner-plan", planningFrameID: "inner-planning", records: []planRecordBuild{{sequence: 2, created: true}}, snapshot: &planSnapshot{PlanID: "inner-plan", CapabilityName: "inner", CreatedAt: "2026-08-30T00:00:00Z", Status: "VALID", Tasks: task("inner-task")}},
	}}
	summaries, domain := graph.summaries(frames, resolver, []failureResult{{FailureID: "outer-failure", FrameID: "outer-child"}, {FailureID: "inner-failure", FrameID: "inner-model"}, {FailureID: "unassigned-failure", FrameID: "unassigned"}}, "trace")
	if domain != nil || len(summaries) != 2 {
		t.Fatalf("summaries=%+v domain=%v", summaries, domain)
	}
	if got := summaries[0].Tasks[0].FailureIDs; len(got) != 1 || got[0] != "outer-failure" {
		t.Fatalf("outer failures=%v", got)
	}
	if got := summaries[1].Tasks[0].FailureIDs; len(got) != 1 || got[0] != "inner-failure" {
		t.Fatalf("inner failures=%v", got)
	}
}
