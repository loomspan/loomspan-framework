package traceanalysis

import (
	"encoding/json"
	"reflect"
	"strconv"
	"strings"
	"testing"
)

func TestFrameAssignmentIgnoresUnassignedPlanStepIdentity(t *testing.T) {
	assignment, valid := decodeFrameAssignment(json.RawMessage(`{"planId":"plan-1","stepNumber":3}`))
	if !valid || assignment != nil {
		t.Fatalf("expected valid unassigned frame metadata, got assignment=%#v valid=%v", assignment, valid)
	}
}

func TestFrameGraphRejectsAssignmentOnNonStepFrame(t *testing.T) {
	graph := newFrameGraph()
	if domain := graph.onFrameOpened(traceFrameRecord(1, RecordFrameOpened, "root", "", FrameRootMission, nil)); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onFrameOpened(traceFrameRecord(2, RecordFrameOpened, "model", "root", FrameModelCall,
		assignmentData("task-a", 1, nil, false))); domain == nil {
		t.Fatal("expected INVALID_FRAME_RELATIONSHIP")
	}
}

func TestFrameGraphProjectsEveryOpenLeafWithNearestAssignment(t *testing.T) {
	graph := newFrameGraph()
	group := "batch"
	records := []*Record{
		traceFrameRecord(1, RecordFrameOpened, "root", "", FrameRootMission, nil),
		traceFrameRecord(2, RecordFrameOpened, "step-a", "root", FrameStepExecution, assignmentData("task-a", 1, &group, true)),
		traceFrameRecord(3, RecordFrameOpened, "step-b", "root", FrameStepExecution, assignmentData("task-b", 2, &group, true)),
		traceFrameRecord(4, RecordFrameOpened, "model-b", "step-b", FrameModelCall, nil),
		traceFrameRecord(5, RecordFrameOpened, "model-a", "step-a", FrameModelCall, nil),
	}
	for _, record := range records {
		if domain := graph.onFrameOpened(record); domain != nil {
			t.Fatal(domain)
		}
	}
	branches, valid := graph.activeBranchesWithResolver(testAssignmentResolver(graph))
	if !valid || len(branches) != 2 {
		t.Fatalf("branches=%+v valid=%v", branches, valid)
	}
	if branches[0].TaskID == nil || *branches[0].TaskID != "task-b" || branches[1].TaskID == nil || *branches[1].TaskID != "task-a" {
		t.Fatalf("leaf order=%+v", branches)
	}
	if got := []string{branches[0].Path[0].FrameID, branches[0].Path[1].FrameID, branches[0].Path[2].FrameID}; !reflect.DeepEqual(got, []string{"root", "step-b", "model-b"}) {
		t.Fatalf("path=%v", got)
	}
	if domain := graph.onFrameClosed(traceFrameRecord(6, RecordFrameClosed, "model-b", "step-b", FrameModelCall, nil)); domain != nil {
		t.Fatal(domain)
	}
	branches, valid = graph.activeBranchesWithResolver(testAssignmentResolver(graph))
	if !valid || len(branches) != 2 || branches[0].Path[len(branches[0].Path)-1].FrameID != "step-b" {
		t.Fatalf("parent leaf was not restored: %+v valid=%v", branches, valid)
	}
}

func TestFrameGraphNestedAssignmentShadowsAncestorForDescendants(t *testing.T) {
	graph := newFrameGraph()
	records := []*Record{
		traceFrameRecord(1, RecordFrameOpened, "root", "", FrameRootMission, nil),
		traceFrameRecord(2, RecordFrameOpened, "outer-step", "root", FrameStepExecution,
			assignmentDataForPlan("outer-plan", "outer-task", 1, nil, false)),
		traceFrameRecord(3, RecordFrameOpened, "inner-step", "outer-step", FrameStepExecution,
			assignmentDataForPlan("inner-plan", "inner-task", 1, nil, false)),
		traceFrameRecord(4, RecordFrameOpened, "inner-model", "inner-step", FrameModelCall, nil),
	}
	for _, record := range records {
		if domain := graph.onFrameOpened(record); domain != nil {
			t.Fatal(domain)
		}
	}
	branches, valid := graph.activeBranchesWithResolver(testAssignmentResolver(graph))
	if !valid || len(branches) != 1 || branches[0].PlanID == nil || *branches[0].PlanID != "inner-plan" ||
		branches[0].TaskID == nil || *branches[0].TaskID != "inner-task" {
		t.Fatalf("nested branch=%+v valid=%v", branches, valid)
	}
}

func testAssignmentResolver(graph *frameGraph) *assignmentResolver {
	resolver := &assignmentResolver{frames: graph, exact: map[planTaskKey]*planTaskAssignment{}}
	for _, frame := range graph.frames {
		if frame.assignment == nil {
			continue
		}
		assignment := frame.assignment
		frameID := frame.frameID
		resolver.exact[planTaskKey{assignment.planID, assignment.taskID}] = &planTaskAssignment{
			planID: assignment.planID, taskID: assignment.taskID, stepNumber: assignment.stepNumber,
			parallelGroup: copyStringPointer(assignment.parallelGroup), effectiveConcurrency: assignment.effectiveConcurrency,
			assignedFrameID: &frameID,
		}
	}
	return resolver
}

func traceFrameRecord(sequence int64, recordType TraceRecordType, frameID, parentID string, frameType TraceFrameType, data json.RawMessage) *Record {
	return &Record{TraceID: "trace", Sequence: sequence, Type: recordType, FrameID: frameID,
		ParentFrameID: parentID, HasParentFrame: parentID != "", FrameType: frameType, HasFrameType: true,
		Route: frameID, Data: data}
}

func assignmentData(taskID string, step int, group *string, effective bool) json.RawMessage {
	return assignmentDataForPlan("plan-1", taskID, step, group, effective)
}

func assignmentDataForPlan(planID, taskID string, step int, group *string, effective bool) json.RawMessage {
	groupJSON := "null"
	if group != nil {
		encoded, _ := json.Marshal(*group)
		groupJSON = string(encoded)
	}
	return json.RawMessage(`{"planId":"` + planID + `","assignedTaskId":"` + taskID + `","stepNumber":` + strconv.Itoa(step) +
		`,"parallelGroup":` + groupJSON + `,"effectiveConcurrency":` + strconv.FormatBool(effective) + `}`)
}

func TestPlanGraphConsumesAuthoritativeAdmissionAndJoin(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
	}}
	graph := newPlanGraph()
	records := []*Record{
		planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")),
		planTransitionRecord(2, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID"), `{"kind":"ADMISSION","taskIds":["task-a","task-b"],"parallelGroup":"batch","effectiveConcurrency":true}`),
		planTransitionRecord(3, "root", planData("COMPLETED", "FAILED", "STALE"), `{"kind":"JOIN","taskIds":["task-a","task-b"],"parallelGroup":"batch","outcome":"FAILED"}`),
	}
	for _, record := range records {
		if domain := graph.onRecord(record, frames); domain != nil {
			t.Fatal(domain)
		}
	}
	transitions := graph.transitions()
	for sequence, reference := range graph.references() {
		if reference.PlanID != "plan-1" || reference.CapabilityName != "planner" {
			t.Fatalf("record %d plan identity=%+v", sequence, reference)
		}
	}
	if len(transitions) != 2 {
		t.Fatalf("transitions=%+v", transitions)
	}
	if transitions[0].kind != "ADMISSION" || transitions[0].sequence != 2 ||
		!reflect.DeepEqual(transitions[0].taskIDs, []string{"task-a", "task-b"}) ||
		transitions[0].group == nil || *transitions[0].group != "batch" {
		t.Fatalf("admission=%+v", transitions[0])
	}
	if transitions[1].kind != "JOIN" || transitions[1].outcome != "FAILED" ||
		!reflect.DeepEqual(transitions[1].taskIDs, []string{"task-a", "task-b"}) {
		t.Fatalf("join=%+v", transitions[1])
	}
}

func TestPlanGraphRejectsLegacyBlockedTaskAndJoinVocabulary(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
	}}

	t.Run("task status", func(t *testing.T) {
		graph := newPlanGraph()
		if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "BLOCKED", "VALID")), frames); domain == nil {
			t.Fatal("expected INVALID_PLAN_LINEAGE")
		}
	})

	t.Run("join outcome", func(t *testing.T) {
		graph := newPlanGraph()
		if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
			t.Fatal(domain)
		}
		if domain := graph.onRecord(planTransitionRecord(2, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID"),
			`{"kind":"ADMISSION","taskIds":["task-a","task-b"],"parallelGroup":"batch","effectiveConcurrency":true}`), frames); domain != nil {
			t.Fatal(domain)
		}
		if domain := graph.onRecord(planTransitionRecord(3, "root", planData("COMPLETED", "FAILED", "STALE"),
			`{"kind":"JOIN","taskIds":["task-a","task-b"],"parallelGroup":"batch","outcome":"BLOCKED"}`), frames); domain == nil {
			t.Fatal("expected INVALID_PLAN_LINEAGE")
		}
	})
}

func TestPlanGraphAcceptsCleanupJoinForOnlyTasksFoldedAtCutoff(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning},
	}}
	graph := newPlanGraph()
	records := []*Record{
		planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")),
		planTransitionRecord(2, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID"), `{"kind":"ADMISSION","taskIds":["task-a","task-b"],"parallelGroup":"batch","effectiveConcurrency":true}`),
		planTransitionRecord(3, "root", planData("COMPLETED", "IN_PROGRESS", "STALE"), `{"kind":"JOIN","taskIds":["task-a"],"parallelGroup":"batch","outcome":"COMPLETED"}`),
	}
	for _, record := range records {
		if domain := graph.onRecord(record, frames); domain != nil {
			t.Fatal(domain)
		}
	}
}

func TestPlanGraphRejectsImpossibleCompleteTransitions(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
	}}
	tests := map[string]json.RawMessage{
		"completion without admission": planData("COMPLETED", "PENDING", "VALID"),
		"task reorder":                 json.RawMessage(`{"planId":"plan-1","capabilityName":"planner","createdAt":"2026-08-29T00:00:00Z","status":"VALID","tasks":[{"taskId":"task-b","title":"B","status":"PENDING","capabilityName":"b","intent":"B","dependsOn":[],"expectedOutputs":[],"parallelGroup":"batch","note":null},{"taskId":"task-a","title":"A","status":"PENDING","capabilityName":"a","intent":"A","dependsOn":[],"expectedOutputs":[],"parallelGroup":"batch","note":null}]}`),
		"admission on stale plan":      planData("IN_PROGRESS", "IN_PROGRESS", "STALE"),
	}
	for name, updateData := range tests {
		t.Run(name, func(t *testing.T) {
			graph := newPlanGraph()
			if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
				t.Fatal(domain)
			}
			if domain := graph.onRecord(planRecord(2, RecordPlanUpdated, "root", updateData), frames); domain == nil {
				t.Fatal("expected INVALID_PLAN_LINEAGE")
			} else if category, ok := categoryOf(domain); !ok || category != CategoryInvalidPlanLineage {
				t.Fatalf("category=%v domain=%v", category, domain)
			}
		})
	}
}

func TestPlanGraphRejectsTaskProgressAfterPlanBecameStale(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
	}}
	graph := newPlanGraph()
	if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(planRecord(2, RecordPlanUpdated, "root", planData("PENDING", "PENDING", "STALE")), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(planRecord(3, RecordPlanUpdated, "root", planData("IN_PROGRESS", "IN_PROGRESS", "STALE")), frames); domain == nil {
		t.Fatal("expected INVALID_PLAN_LINEAGE")
	}
}

func TestPlanGraphRejectsPartialEnabledGroupUsingAssignmentFacts(t *testing.T) {
	group := "batch"
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
		"step-a": {frameID: "step-a", frameType: FrameStepExecution,
			assignment: &frameAssignment{planID: "plan-1", taskID: "task-a", stepNumber: 1, parallelGroup: &group, effectiveConcurrency: true}},
	}}
	graph := newPlanGraph()
	if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(planTransitionRecord(2, "root", planData("IN_PROGRESS", "PENDING", "VALID"),
		`{"kind":"ADMISSION","taskIds":["task-a"],"parallelGroup":"batch","effectiveConcurrency":true}`), frames); domain == nil {
		t.Fatal("expected INVALID_PLAN_LINEAGE")
	}
}

func TestPlanGraphRejectsAssignmentThatWasNotAdmittedOrHasWrongStepOrMixedGroupMode(t *testing.T) {
	group := "batch"
	for name, assignments := range map[string]map[string]*frameAssignment{
		"not admitted": {
			"step-a": {planID: "plan-1", taskID: "task-a", stepNumber: 1, parallelGroup: &group, effectiveConcurrency: false},
		},
		"wrong step": {
			"step-a": {planID: "plan-1", taskID: "task-a", stepNumber: 2, parallelGroup: &group, effectiveConcurrency: false},
		},
		"mixed group mode": {
			"step-a": {planID: "plan-1", taskID: "task-a", stepNumber: 1, parallelGroup: &group, effectiveConcurrency: true},
			"step-b": {planID: "plan-1", taskID: "task-b", stepNumber: 2, parallelGroup: &group, effectiveConcurrency: false},
		},
	} {
		t.Run(name, func(t *testing.T) {
			frames := &frameGraph{frames: map[string]*frameBuild{
				"root":     {frameID: "root", frameType: FrameRootMission},
				"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
			}}
			for frameID, assignment := range assignments {
				frames.frames[frameID] = &frameBuild{frameID: frameID, frameType: FrameStepExecution, assignment: assignment}
			}
			graph := newPlanGraph()
			if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
				t.Fatal(domain)
			}
			if name != "not admitted" {
				if domain := graph.onRecord(planTransitionRecord(2, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID"),
					`{"kind":"ADMISSION","taskIds":["task-a","task-b"],"parallelGroup":"batch","effectiveConcurrency":true}`), frames); domain != nil {
					t.Fatal(domain)
				}
			}
			if domain := graph.validateAssignments(frames, "trace"); domain == nil {
				t.Fatal("expected INVALID_PLAN_LINEAGE")
			}
		})
	}
}

func TestPlanGraphRejectsDuplicateExactAssignment(t *testing.T) {
	group := "batch"
	assignment := &frameAssignment{planID: "plan-1", taskID: "task-a", stepNumber: 1, parallelGroup: &group, effectiveConcurrency: true}
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning},
		"step-a-1": {frameID: "step-a-1", frameType: FrameStepExecution, assignment: assignment},
		"step-a-2": {frameID: "step-a-2", frameType: FrameStepExecution, assignment: assignment},
	}}
	graph := newPlanGraph()
	if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(planTransitionRecord(2, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID"),
		`{"kind":"ADMISSION","taskIds":["task-a","task-b"],"parallelGroup":"batch","effectiveConcurrency":true}`), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.validateAssignments(frames, "trace"); domain == nil {
		t.Fatal("expected INVALID_PLAN_LINEAGE")
	}
}

func TestPlanGraphRejectsCreatedPlanWithAlreadyStartedTask(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root": {frameID: "root", frameType: FrameRootMission}, "planning": {frameID: "planning", frameType: FramePlanning},
	}}
	if domain := newPlanGraph().onRecord(planRecord(1, RecordPlanCreated, "planning", planData("IN_PROGRESS", "PENDING", "VALID")), frames); domain == nil {
		t.Fatal("expected INVALID_PLAN_LINEAGE")
	}
}

func TestDecodePlanSnapshotRejectsMalformedCanonicalIdentityAndGroups(t *testing.T) {
	base := string(planData("PENDING", "PENDING", "VALID"))
	for name, data := range map[string]string{
		"invalid createdAt":  strings.Replace(base, `"2026-08-29T00:00:00Z"`, `"not-an-instant"`, 1),
		"invalid group":      strings.ReplaceAll(base, `"batch"`, `"bad group"`),
		"singleton group":    strings.Replace(base, `"parallelGroup":"batch"`, `"parallelGroup":null`, 1),
		"blank capability":   strings.Replace(base, `"capabilityName":"a"`, `"capabilityName":" "`, 1),
		"unknown dependency": strings.Replace(base, `"dependsOn":[]`, `"dependsOn":["missing"]`, 1),
	} {
		t.Run(name, func(t *testing.T) {
			if _, valid := decodePlanSnapshot(json.RawMessage(data), "plan-1"); valid {
				t.Fatal("expected malformed canonical plan snapshot")
			}
		})
	}
}

func planRecord(sequence int64, recordType TraceRecordType, frameID string, data json.RawMessage) *Record {
	return &Record{TraceID: "trace", Sequence: sequence, Type: recordType, FrameID: frameID,
		Metadata: json.RawMessage(`{"planId":"plan-1","attemptId":"attempt-1","retrySequenceId":"retry-1"}`), Data: data}
}

func planTransitionRecord(sequence int64, frameID string, data json.RawMessage, transition string) *Record {
	return &Record{TraceID: "trace", Sequence: sequence, Type: RecordPlanUpdated, FrameID: frameID,
		Metadata: json.RawMessage(`{"planId":"plan-1","transition":` + transition + `}`), Data: data}
}

func TestPlanGraphRejectsLifecycleChangeWithoutAuthoritativeTransition(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning},
	}}
	graph := newPlanGraph()
	if domain := graph.onRecord(planRecord(1, RecordPlanCreated, "planning", planData("PENDING", "PENDING", "VALID")), frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(planRecord(2, RecordPlanUpdated, "root", planData("IN_PROGRESS", "IN_PROGRESS", "VALID")), frames); domain == nil {
		t.Fatal("expected INVALID_PLAN_LINEAGE")
	}
}

func TestDecodePlanTransitionRejectsMalformedExactObjects(t *testing.T) {
	valid := `{"planId":"plan-1","transition":{"kind":"ADMISSION","taskIds":["task-a"],"parallelGroup":null,"effectiveConcurrency":false}}`
	if transition, ok := decodePlanTransition(json.RawMessage(valid), 2, "plan-1"); !ok || transition == nil || transition.effectiveConcurrency == nil || *transition.effectiveConcurrency {
		t.Fatalf("valid admission=%+v ok=%v", transition, ok)
	}
	invalid := map[string]string{
		"null":           `{"planId":"plan-1","transition":null}`,
		"non-object":     `{"planId":"plan-1","transition":[]}`,
		"missing group":  `{"planId":"plan-1","transition":{"kind":"ADMISSION","taskIds":["task-a"],"effectiveConcurrency":false}}`,
		"null mode":      `{"planId":"plan-1","transition":{"kind":"ADMISSION","taskIds":["task-a"],"parallelGroup":null,"effectiveConcurrency":null}}`,
		"unknown field":  `{"planId":"plan-1","transition":{"kind":"ADMISSION","taskIds":["task-a"],"parallelGroup":null,"effectiveConcurrency":false,"extra":1}}`,
		"duplicate task": `{"planId":"plan-1","transition":{"kind":"ADMISSION","taskIds":["task-a","task-a"],"parallelGroup":null,"effectiveConcurrency":false}}`,
		"cross field":    `{"planId":"plan-1","transition":{"kind":"JOIN","taskIds":["task-a"],"parallelGroup":null,"effectiveConcurrency":false}}`,
	}
	for name, raw := range invalid {
		t.Run(name, func(t *testing.T) {
			if _, ok := decodePlanTransition(json.RawMessage(raw), 2, "plan-1"); ok {
				t.Fatal("expected rejection")
			}
		})
	}
}

func planData(firstStatus, secondStatus, planStatus string) json.RawMessage {
	return json.RawMessage(`{"planId":"plan-1","capabilityName":"planner","createdAt":"2026-08-29T00:00:00Z","status":"` + planStatus + `","tasks":[` +
		`{"taskId":"task-a","title":"A","status":"` + firstStatus + `","capabilityName":"a","intent":"A","dependsOn":[],"expectedOutputs":[],"parallelGroup":"batch","note":null},` +
		`{"taskId":"task-b","title":"B","status":"` + secondStatus + `","capabilityName":"b","intent":"B","dependsOn":[],"expectedOutputs":[],"parallelGroup":"batch","note":null}]}`)
}

func TestPlanGraphDerivesMinimalReferencesAndStableNestedMissionOwnership(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"trace-root":     {frameID: "trace-root", frameType: FrameRootMission},
		"nested-skill":   {frameID: "nested-skill", frameType: FrameSkillExecution, hasParent: true, parentFrameID: "trace-root"},
		"nested-mission": {frameID: "nested-mission", frameType: FrameRootMission, hasParent: true, parentFrameID: "nested-skill"},
		"planning":       {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "nested-mission"},
		"step":           {frameID: "step", frameType: FrameStepExecution, hasParent: true, parentFrameID: "nested-mission"},
	}}
	graph := newPlanGraph()
	created := &Record{TraceID: "trace", Sequence: 10, Type: RecordPlanCreated, FrameID: "planning", Metadata: json.RawMessage(`{"planId":"plan-1","attemptId":"attempt-1","retrySequenceId":"retry-1"}`), Data: planData("PENDING", "PENDING", "VALID")}
	updated := &Record{TraceID: "trace", Sequence: 20, Type: RecordPlanUpdated, FrameID: "step", Metadata: json.RawMessage(`{"planId":"plan-1"}`), Data: planData("PENDING", "PENDING", "VALID")}
	if domain := graph.onRecord(created, frames); domain != nil {
		t.Fatal(domain)
	}
	if domain := graph.onRecord(updated, frames); domain != nil {
		t.Fatal(domain)
	}
	references := graph.references()
	for _, sequence := range []int64{10, 20} {
		if references[sequence].PlanID != "plan-1" {
			t.Fatalf("sequence %d reference=%+v", sequence, references[sequence])
		}
	}
	resolver, domain := graph.assignmentResolver(frames, "trace")
	if domain != nil {
		t.Fatal(domain)
	}
	summaries, domain := graph.summaries(frames, resolver, nil, "trace")
	if domain != nil || len(summaries) != 1 {
		t.Fatalf("summaries=%+v domain=%v", summaries, domain)
	}
	summary := summaries[0]
	if summary.TraceRootFrameID != "trace-root" || summary.MissionFrameID != "nested-mission" || summary.PlanningFrameID != "planning" || summary.AttemptID != "attempt-1" || summary.RetrySequenceID != "retry-1" {
		t.Fatalf("summary lineage=%+v", summary)
	}
}

func TestPlanGraphRejectsInvalidLineage(t *testing.T) {
	frames := &frameGraph{frames: map[string]*frameBuild{
		"root":     {frameID: "root", frameType: FrameRootMission},
		"planning": {frameID: "planning", frameType: FramePlanning, hasParent: true, parentFrameID: "root"},
		"step":     {frameID: "step", frameType: FrameStepExecution, hasParent: true, parentFrameID: "root"},
	}}
	tests := map[string][]*Record{
		"missing plan id":  {{TraceID: "trace", Sequence: 1, Type: RecordPlanCreated, FrameID: "planning", Metadata: json.RawMessage(`{}`)}},
		"blank plan id":    {{TraceID: "trace", Sequence: 1, Type: RecordPlanCreated, FrameID: "planning", Metadata: json.RawMessage(`{"planId":"   "}`)}},
		"outside planning": {{TraceID: "trace", Sequence: 1, Type: RecordPlanCreated, FrameID: "step", Metadata: json.RawMessage(`{"planId":"p"}`)}},
		"update first":     {{TraceID: "trace", Sequence: 1, Type: RecordPlanUpdated, FrameID: "step", Metadata: json.RawMessage(`{"planId":"p"}`)}},
		"duplicate creation": {
			{TraceID: "trace", Sequence: 1, Type: RecordPlanCreated, FrameID: "planning", Metadata: json.RawMessage(`{"planId":"p"}`)},
			{TraceID: "trace", Sequence: 2, Type: RecordPlanCreated, FrameID: "planning", Metadata: json.RawMessage(`{"planId":"p"}`)},
		},
	}
	for name, records := range tests {
		t.Run(name, func(t *testing.T) {
			graph := newPlanGraph()
			var domainErr any
			for _, record := range records {
				if domain := graph.onRecord(record, frames); domain != nil {
					domainErr = domain
					category, ok := categoryOf(domain)
					if !ok || category != CategoryInvalidPlanLineage {
						t.Fatalf("category=%v domain=%v", category, domain)
					}
					break
				}
			}
			if domainErr == nil {
				t.Fatal("expected INVALID_PLAN_LINEAGE")
			}
		})
	}
}
