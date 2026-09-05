package traceanalysis

import (
	"bytes"
	"encoding/json"
	"reflect"
	"sort"
	"strings"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

type planRecordBuild struct {
	sequence        int64
	created         bool
	attemptID       string
	retrySequenceID string
	transition      *planTransition
	update          *PlanUpdate
}

type planTransition struct {
	sequence             int64
	planID               string
	kind                 string
	taskIDs              []string
	group                *string
	outcome              string
	effectiveConcurrency *bool
}

type planSnapshot struct {
	PlanID         string             `json:"planId"`
	CapabilityName string             `json:"capabilityName"`
	CreatedAt      string             `json:"createdAt"`
	Status         string             `json:"status"`
	Tasks          []planTaskSnapshot `json:"tasks"`
}

type planTaskSnapshot struct {
	TaskID          string   `json:"taskId"`
	Title           string   `json:"title"`
	Status          string   `json:"status"`
	CapabilityName  *string  `json:"capabilityName"`
	Intent          *string  `json:"intent"`
	DependsOn       []string `json:"dependsOn"`
	ExpectedOutputs []string `json:"expectedOutputs"`
	ParallelGroup   *string  `json:"parallelGroup"`
	Note            *string  `json:"note"`
}

type planLineageBuild struct {
	planID          string
	planningFrameID string
	records         []planRecordBuild
	snapshot        *planSnapshot
}

type planGraph struct {
	lineages map[string]*planLineageBuild
}

func newPlanGraph() *planGraph { return &planGraph{lineages: map[string]*planLineageBuild{}} }

func (g *planGraph) onRecord(rec *Record, frames *frameGraph) *consolecore.Error {
	if rec.Type != RecordPlanCreated && rec.Type != RecordPlanUpdated {
		return nil
	}
	planID := rec.metadataStringOrEmpty("planId")
	if strings.TrimSpace(planID) == "" {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	lineage := g.lineages[planID]
	if rec.Type == RecordPlanCreated {
		if lineage != nil || rec.FrameID == "" {
			return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
		}
		frame := frames.frames[rec.FrameID]
		if frame == nil || frame.frameType != FramePlanning {
			return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
		}
		snapshot, valid := decodePlanSnapshot(rec.Data, planID)
		if !valid || snapshot.Status != "VALID" || !allTasksPending(snapshot.Tasks) {
			return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
		}
		lineage = &planLineageBuild{planID: planID, planningFrameID: rec.FrameID, snapshot: snapshot}
		g.lineages[planID] = lineage
		lineage.records = append(lineage.records, planRecordBuild{
			sequence: rec.Sequence, created: true,
			attemptID: rec.metadataStringOrEmpty("attemptId"), retrySequenceID: rec.metadataStringOrEmpty("retrySequenceId"),
		})
		return nil
	}
	if lineage == nil {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	next, valid := decodePlanSnapshot(rec.Data, planID)
	if !valid {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	if lineage.snapshot == nil {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	transition, valid := decodePlanTransition(rec.Metadata, rec.Sequence, planID)
	if !valid {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	if !validatePlanSnapshotTransition(lineage.snapshot, next, transition) {
		return invalidityError(CategoryInvalidPlanLineage, rec.TraceID)
	}
	update := comparePlanSnapshots(lineage.snapshot, next, lineage.records[len(lineage.records)-1].sequence)
	lineage.snapshot = next
	lineage.records = append(lineage.records, planRecordBuild{sequence: rec.Sequence, transition: transition, update: update})
	return nil
}

func decodePlanSnapshot(raw json.RawMessage, metadataPlanID string) (*planSnapshot, bool) {
	if len(raw) == 0 || string(raw) == "null" {
		return nil, false
	}
	var shape map[string]json.RawMessage
	if json.Unmarshal(raw, &shape) != nil {
		return nil, false
	}
	_, hasTasks := shape["tasks"]
	_, hasStatus := shape["status"]
	_, hasCapability := shape["capabilityName"]
	if !hasTasks || !hasStatus || !hasCapability {
		return nil, false
	}
	var snapshot planSnapshot
	if json.Unmarshal(raw, &snapshot) != nil || strings.TrimSpace(snapshot.PlanID) == "" || snapshot.PlanID != metadataPlanID ||
		strings.TrimSpace(snapshot.CapabilityName) == "" || strings.TrimSpace(snapshot.CreatedAt) == "" || !validInstant(snapshot.CreatedAt) ||
		!validPlanStatus(snapshot.Status) || snapshot.Tasks == nil {
		return nil, false
	}
	seen := map[string]struct{}{}
	for i := range snapshot.Tasks {
		task := &snapshot.Tasks[i]
		if strings.TrimSpace(task.TaskID) == "" || strings.TrimSpace(task.Title) == "" || !validTaskStatus(task.Status) ||
			task.DependsOn == nil || task.ExpectedOutputs == nil {
			return nil, false
		}
		if _, duplicate := seen[task.TaskID]; duplicate {
			return nil, false
		}
		seen[task.TaskID] = struct{}{}
		if task.ParallelGroup != nil && strings.TrimSpace(*task.ParallelGroup) == "" {
			return nil, false
		}
		if task.ParallelGroup != nil && !validParallelGroup(*task.ParallelGroup) ||
			!nonblankIfPresent(task.CapabilityName) || !nonblankIfPresent(task.Intent) || !nonblankIfPresent(task.Note) ||
			!nonblankStrings(task.DependsOn) || !nonblankStrings(task.ExpectedOutputs) {
			return nil, false
		}
	}
	if !validGroupRuns(snapshot.Tasks) || !validTaskDependencies(snapshot.Tasks) {
		return nil, false
	}
	return &snapshot, true
}

func decodePlanTransition(metadata json.RawMessage, sequence int64, planID string) (*planTransition, bool) {
	metadataFields, ok := decodeUniqueObject(metadata)
	if !ok {
		return nil, false
	}
	raw, present := metadataFields["transition"]
	if !present {
		return nil, true
	}
	if bytes.Equal(bytes.TrimSpace(raw), nullBytes) {
		return nil, false
	}
	fields, ok := decodeUniqueObject(raw)
	if !ok {
		return nil, false
	}
	readString := func(name string) (string, bool) {
		value, present := fields[name]
		if !present || bytes.Equal(bytes.TrimSpace(value), nullBytes) {
			return "", false
		}
		var decoded string
		if json.Unmarshal(value, &decoded) != nil || strings.TrimSpace(decoded) == "" {
			return "", false
		}
		return decoded, true
	}
	kind, ok := readString("kind")
	if !ok || kind != "ADMISSION" && kind != "JOIN" {
		return nil, false
	}
	tasksRaw, present := fields["taskIds"]
	var taskIDs []string
	if !present || json.Unmarshal(tasksRaw, &taskIDs) != nil || len(taskIDs) == 0 {
		return nil, false
	}
	seen := map[string]struct{}{}
	for _, taskID := range taskIDs {
		if strings.TrimSpace(taskID) == "" {
			return nil, false
		}
		if _, duplicate := seen[taskID]; duplicate {
			return nil, false
		}
		seen[taskID] = struct{}{}
	}
	groupRaw, present := fields["parallelGroup"]
	if !present {
		return nil, false
	}
	var group *string
	if !bytes.Equal(bytes.TrimSpace(groupRaw), nullBytes) {
		var value string
		if json.Unmarshal(groupRaw, &value) != nil || strings.TrimSpace(value) == "" {
			return nil, false
		}
		group = &value
	}
	transition := &planTransition{sequence: sequence, planID: planID, kind: kind, taskIDs: taskIDs, group: group}
	if kind == "ADMISSION" {
		if len(fields) != 4 {
			return nil, false
		}
		effectiveRaw, present := fields["effectiveConcurrency"]
		var effective bool
		if !present || bytes.Equal(bytes.TrimSpace(effectiveRaw), nullBytes) ||
			json.Unmarshal(effectiveRaw, &effective) != nil || effective && group == nil {
			return nil, false
		}
		transition.effectiveConcurrency = &effective
	} else {
		if len(fields) != 4 {
			return nil, false
		}
		outcome, ok := readString("outcome")
		if !ok || outcome != "COMPLETED" && outcome != "FAILED" {
			return nil, false
		}
		transition.outcome = outcome
	}
	return transition, true
}

func validatePlanSnapshotTransition(before, after *planSnapshot, transition *planTransition) bool {
	if before.PlanID != after.PlanID || before.CapabilityName != after.CapabilityName || before.CreatedAt != after.CreatedAt ||
		len(before.Tasks) != len(after.Tasks) || planStatusRank(after.Status) < planStatusRank(before.Status) {
		return false
	}
	admitted := make([]int, 0)
	joined := make([]int, 0)
	for i := range before.Tasks {
		left, right := before.Tasks[i], after.Tasks[i]
		if !sameTaskIdentity(left, right) {
			return false
		}
		switch left.Status + "->" + right.Status {
		case "PENDING->PENDING", "IN_PROGRESS->IN_PROGRESS", "COMPLETED->COMPLETED", "FAILED->FAILED":
		case "PENDING->IN_PROGRESS":
			admitted = append(admitted, i)
		case "IN_PROGRESS->COMPLETED", "IN_PROGRESS->FAILED":
			joined = append(joined, i)
		default:
			return false
		}
	}
	if len(admitted) > 0 && len(joined) > 0 {
		return false
	}
	indices, kind := admitted, "ADMISSION"
	if len(indices) == 0 {
		indices, kind = joined, "JOIN"
	}
	if len(indices) == 0 {
		return transition == nil
	}
	if transition == nil || transition.kind != kind {
		return false
	}
	if before.Status != "VALID" {
		return false
	}
	group, valid := transitionedGroup(after.Tasks, indices, kind == "ADMISSION")
	if !valid {
		return false
	}
	taskIDs := make([]string, len(indices))
	outcome := ""
	for i, index := range indices {
		taskIDs[i] = after.Tasks[index].TaskID
		if kind == "JOIN" && after.Tasks[index].Status == "FAILED" {
			outcome = "FAILED"
		}
	}
	if kind == "JOIN" && outcome == "" {
		outcome = "COMPLETED"
	}
	if kind == "ADMISSION" && after.Status != "VALID" ||
		kind == "JOIN" && outcome == "COMPLETED" && after.Status != "VALID" && after.Status != "STALE" ||
		kind == "JOIN" && outcome == "FAILED" && after.Status != "STALE" {
		return false
	}
	if !reflect.DeepEqual(transition.taskIDs, taskIDs) || !nullableStringEqual(transition.group, group) ||
		kind == "JOIN" && transition.outcome != outcome {
		return false
	}
	if kind == "ADMISSION" {
		if transition.effectiveConcurrency == nil || len(taskIDs) > 1 && !*transition.effectiveConcurrency {
			return false
		}
		if *transition.effectiveConcurrency {
			expected := make([]string, 0)
			for _, task := range after.Tasks {
				if nullableStringEqual(task.ParallelGroup, group) {
					expected = append(expected, task.TaskID)
				}
			}
			if !reflect.DeepEqual(expected, taskIDs) {
				return false
			}
		}
	}
	return true
}

func transitionedGroup(tasks []planTaskSnapshot, indices []int, requireCompleteGroup bool) (*string, bool) {
	first := tasks[indices[0]].ParallelGroup
	if len(indices) > 1 && first == nil {
		return nil, false
	}
	for position, index := range indices {
		if requireCompleteGroup && position > 0 && index != indices[position-1]+1 {
			return nil, false
		}
		if !nullableStringEqual(first, tasks[index].ParallelGroup) {
			return nil, false
		}
	}
	if requireCompleteGroup && len(indices) > 1 {
		groupCount := 0
		for _, task := range tasks {
			if nullableStringEqual(first, task.ParallelGroup) {
				groupCount++
			}
		}
		if groupCount != len(indices) {
			return nil, false
		}
	}
	if first == nil {
		return nil, true
	}
	value := *first
	return &value, true
}

func sameTaskIdentity(left, right planTaskSnapshot) bool {
	left.Status, right.Status = "", ""
	left.Note, right.Note = nil, nil
	return reflect.DeepEqual(left, right)
}

func nullableStringEqual(left, right *string) bool {
	return left == nil && right == nil || left != nil && right != nil && *left == *right
}

func validTaskStatus(status string) bool {
	switch status {
	case "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED":
		return true
	default:
		return false
	}
}

func allTasksPending(tasks []planTaskSnapshot) bool {
	for _, task := range tasks {
		if task.Status != "PENDING" {
			return false
		}
	}
	return true
}

func validInstant(value string) bool {
	_, err := time.Parse(time.RFC3339Nano, value)
	return err == nil
}

func nonblankIfPresent(value *string) bool { return value == nil || strings.TrimSpace(*value) != "" }

func nonblankStrings(values []string) bool {
	for _, value := range values {
		if strings.TrimSpace(value) == "" {
			return false
		}
	}
	return true
}

func validParallelGroup(value string) bool {
	if len(value) == 0 || len(value) > 64 {
		return false
	}
	for index, char := range []byte(value) {
		alphaNumeric := char >= 'A' && char <= 'Z' || char >= 'a' && char <= 'z' || char >= '0' && char <= '9'
		if !alphaNumeric && (index == 0 || char != '_' && char != '-') {
			return false
		}
	}
	return true
}

func validGroupRuns(tasks []planTaskSnapshot) bool {
	counts := map[string]int{}
	lastIndex := map[string]int{}
	for index, task := range tasks {
		if task.ParallelGroup == nil {
			continue
		}
		group := *task.ParallelGroup
		if previous, seen := lastIndex[group]; seen && previous != index-1 {
			return false
		}
		lastIndex[group], counts[group] = index, counts[group]+1
	}
	for _, count := range counts {
		if count < 2 {
			return false
		}
	}
	return true
}

func validTaskDependencies(tasks []planTaskSnapshot) bool {
	units := make(map[string]int, len(tasks))
	unit := -1
	var previousGroup *string
	for _, task := range tasks {
		if task.ParallelGroup == nil || previousGroup == nil || *task.ParallelGroup != *previousGroup {
			unit++
		}
		units[task.TaskID] = unit
		previousGroup = task.ParallelGroup
	}
	for _, task := range tasks {
		seen := map[string]struct{}{}
		for _, dependency := range task.DependsOn {
			dependencyUnit, known := units[dependency]
			if !known || dependencyUnit >= units[task.TaskID] {
				return false
			}
			if _, duplicate := seen[dependency]; duplicate {
				return false
			}
			seen[dependency] = struct{}{}
		}
	}
	return true
}

func validPlanStatus(status string) bool { return planStatusRank(status) >= 0 }

func planStatusRank(status string) int {
	switch status {
	case "VALID":
		return 0
	case "STALE":
		return 1
	case "INVALID":
		return 2
	default:
		return -1
	}
}

func (g *planGraph) transitions() []planTransition {
	var out []planTransition
	for _, lineage := range g.lineages {
		for _, record := range lineage.records {
			if record.transition != nil {
				copy := *record.transition
				copy.taskIDs = append([]string(nil), copy.taskIDs...)
				out = append(out, copy)
			}
		}
	}
	sort.Slice(out, func(i, j int) bool { return out[i].sequence < out[j].sequence })
	return out
}

func (g *planGraph) validateAssignments(frames *frameGraph, scopeID string) *consolecore.Error {
	_, domain := g.assignmentResolver(frames, scopeID)
	return domain
}

func (g *planGraph) assignmentResolver(frames *frameGraph, scopeID string) (*assignmentResolver, *consolecore.Error) {
	resolver := &assignmentResolver{frames: frames, exact: map[planTaskKey]*planTaskAssignment{}}
	for _, transition := range g.transitions() {
		if transition.kind == "ADMISSION" {
			for _, taskID := range transition.taskIDs {
				key := planTaskKey{planID: transition.planID, taskID: taskID}
				if resolver.exact[key] != nil || transition.effectiveConcurrency == nil {
					return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
				}
				lineage := g.lineages[transition.planID]
				if lineage == nil || lineage.snapshot == nil {
					return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
				}
				for index, task := range lineage.snapshot.Tasks {
					if task.TaskID == taskID {
						resolver.exact[key] = &planTaskAssignment{planID: transition.planID, taskID: taskID,
							stepNumber: int64(index + 1), parallelGroup: copyStringPointer(task.ParallelGroup),
							effectiveConcurrency: *transition.effectiveConcurrency}
						break
					}
				}
				if resolver.exact[key] == nil {
					return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
				}
			}
		}
	}
	for _, frame := range frames.frames {
		if frame.assignment == nil {
			continue
		}
		key := planTaskKey{planID: frame.assignment.planID, taskID: frame.assignment.taskID}
		canonical := resolver.exact[key]
		if canonical == nil || canonical.assignedFrameID != nil {
			return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
		}
		lineage := g.lineages[frame.assignment.planID]
		if lineage == nil || lineage.snapshot == nil {
			return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
		}
		matched := false
		for index, task := range lineage.snapshot.Tasks {
			if task.TaskID == frame.assignment.taskID {
				matched = nullableStringEqual(task.ParallelGroup, frame.assignment.parallelGroup) && frame.assignment.stepNumber == int64(index+1)
				break
			}
		}
		if !matched {
			return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
		}
		if frame.assignment.effectiveConcurrency != canonical.effectiveConcurrency {
			return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
		}
		frameID := frame.frameID
		canonical.assignedFrameID = &frameID
	}
	return resolver, nil
}

func (g *planGraph) references() map[int64]PlanReference {
	out := map[int64]PlanReference{}
	for _, lineage := range g.lineages {
		for _, record := range lineage.records {
			out[record.sequence] = PlanReference{PlanID: lineage.planID, CapabilityName: lineage.snapshot.CapabilityName}
		}
	}
	return out
}

func (g *planGraph) summaries(frames *frameGraph, resolver *assignmentResolver, failures []failureResult, scopeID string) ([]PlanSummary, *consolecore.Error) {
	lineages := make([]*planLineageBuild, 0, len(g.lineages))
	for _, lineage := range g.lineages {
		lineages = append(lineages, lineage)
	}
	sort.Slice(lineages, func(i, j int) bool { return lineages[i].records[0].sequence < lineages[j].records[0].sequence })
	failuresByTask := map[planTaskKey][]string{}
	for _, failure := range failures {
		if failure.FrameID == "" {
			continue
		}
		assignment, ok := resolver.nearestAssignedAncestor(failure.FrameID)
		if !ok {
			return nil, invalidityError(CategoryInvalidFrameRelationship, scopeID)
		}
		if assignment != nil {
			key := planTaskKey{assignment.planID, assignment.taskID}
			failuresByTask[key] = append(failuresByTask[key], failure.FailureID)
		}
	}
	result := make([]PlanSummary, 0, len(lineages))
	for _, lineage := range lineages {
		traceRoot, mission, ok := planOwnership(frames, lineage.planningFrameID)
		if !ok {
			return nil, invalidityError(CategoryInvalidPlanLineage, scopeID)
		}
		created := lineage.records[0]
		summary := PlanSummary{PlanID: lineage.planID, CapabilityName: lineage.snapshot.CapabilityName, CreatedAt: lineage.snapshot.CreatedAt,
			Status: lineage.snapshot.Status, CreationSequence: created.sequence, TraceRootFrameID: traceRoot, MissionFrameID: mission,
			PlanningFrameID: lineage.planningFrameID, AttemptID: created.attemptID, RetrySequenceID: created.retrySequenceID,
			Tasks: []PlanTaskSummary{}, ExecutionUnits: []PlanExecutionUnitSummary{}, Transitions: []PlanTransitionSummary{}}
		for index, task := range lineage.snapshot.Tasks {
			a := resolver.exactTask(lineage.planID, task.TaskID)
			projected := PlanTaskSummary{StepNumber: int64(index + 1), TaskID: task.TaskID, Title: task.Title, Status: task.Status,
				CapabilityName: copyStringPointer(task.CapabilityName), Intent: copyStringPointer(task.Intent), DependsOn: append([]string{}, task.DependsOn...),
				ExpectedOutputs: append([]string{}, task.ExpectedOutputs...), ParallelGroup: copyStringPointer(task.ParallelGroup), Note: copyStringPointer(task.Note),
				FailureIDs: append([]string{}, failuresByTask[planTaskKey{lineage.planID, task.TaskID}]...)}
			if a != nil {
				effective := a.effectiveConcurrency
				projected.EffectiveConcurrency = &effective
				projected.AssignedFrameID = copyStringPointer(a.assignedFrameID)
			}
			summary.Tasks = append(summary.Tasks, projected)
		}
		for position, start := 1, 0; start < len(summary.Tasks); position++ {
			end := start + 1
			group := summary.Tasks[start].ParallelGroup
			if group != nil {
				for end < len(summary.Tasks) && nullableStringEqual(group, summary.Tasks[end].ParallelGroup) {
					end++
				}
			}
			unit := PlanExecutionUnitSummary{Position: int64(position), ParallelGroup: copyStringPointer(group), TaskIDs: []string{}}
			for _, task := range summary.Tasks[start:end] {
				unit.TaskIDs = append(unit.TaskIDs, task.TaskID)
				if unit.EffectiveConcurrency == nil && task.EffectiveConcurrency != nil {
					unit.EffectiveConcurrency = copyBoolPointer(task.EffectiveConcurrency)
				}
			}
			unit.ObservedOverlap = observedOverlap(summary.Tasks[start:end], frames)
			summary.ExecutionUnits = append(summary.ExecutionUnits, unit)
			start = end
		}
		for _, record := range lineage.records {
			if record.transition != nil {
				t := record.transition
				summary.Transitions = append(summary.Transitions, PlanTransitionSummary{Sequence: t.sequence, Kind: t.kind, TaskIDs: append([]string(nil), t.taskIDs...), ParallelGroup: copyStringPointer(t.group), EffectiveConcurrency: copyBoolPointer(t.effectiveConcurrency), Outcome: t.outcome})
			}
		}
		result = append(result, summary)
	}
	return result, nil
}

func planOwnership(frames *frameGraph, planningFrameID string) (string, string, bool) {
	current, traceRoot, mission := planningFrameID, "", ""
	seen := map[string]bool{}
	for current != "" && !seen[current] {
		seen[current] = true
		frame := frames.frames[current]
		if frame == nil {
			return "", "", false
		}
		if mission == "" && frame.frameType == FrameRootMission {
			mission = current
		}
		if !frame.hasParent {
			traceRoot = current
			break
		}
		current = frame.parentFrameID
	}
	return traceRoot, mission, traceRoot != "" && mission != ""
}

func observedOverlap(tasks []PlanTaskSummary, frames *frameGraph) *bool {
	if len(tasks) < 2 || tasks[0].ParallelGroup == nil {
		return nil
	}
	intervals := []frameInterval{}
	admitted := 0
	complete := true
	for _, task := range tasks {
		if task.EffectiveConcurrency == nil {
			continue
		}
		admitted++
		if task.AssignedFrameID == nil {
			complete = false
			continue
		}
		frame := frames.frames[*task.AssignedFrameID]
		if frame == nil || !frame.closed {
			complete = false
			continue
		}
		intervals = append(intervals, frameInterval{frame.openedMillis, frame.closedMillis})
	}
	if admitted == 0 {
		return nil
	}
	for i := range intervals {
		for j := i + 1; j < len(intervals); j++ {
			start := intervals[i].start
			if intervals[j].start > start {
				start = intervals[j].start
			}
			end := intervals[i].end
			if intervals[j].end < end {
				end = intervals[j].end
			}
			if start < end {
				value := true
				return &value
			}
		}
	}
	if !complete {
		return nil
	}
	value := false
	return &value
}

// comparePlanSnapshots runs only after immutable task identities and order have
// been validated. Account each encoded change once, retaining no partial result.
func comparePlanSnapshots(before, after *planSnapshot, previousSequence int64) *PlanUpdate {
	changes := make([]PlanFieldChange, 0)
	update := &PlanUpdate{PreviousSequence: previousSequence, Availability: "AVAILABLE", Changes: &changes}
	encoded, _ := json.Marshal(update)
	size := len(encoded)
	add := func(taskID, field string, left, right *string) bool {
		if nullableStringEqual(left, right) {
			return true
		}
		change := PlanFieldChange{TaskID: taskID, Field: field, Before: copyStringPointer(left), After: copyStringPointer(right)}
		encoded, _ := json.Marshal(change)
		size += len(encoded)
		if len(changes) > 0 {
			size++
		}
		if size > MaxDescriptorResponseBytes {
			return false
		}
		changes = append(changes, change)
		return true
	}
	if !add("", "status", &before.Status, &after.Status) {
		return update.Limited()
	}
	for i := range before.Tasks {
		left, right := &before.Tasks[i], &after.Tasks[i]
		if !add(right.TaskID, "status", &left.Status, &right.Status) || !add(right.TaskID, "note", left.Note, right.Note) {
			return update.Limited()
		}
	}
	return update
}

func (g *planGraph) updates() map[int64]*PlanUpdate {
	out := map[int64]*PlanUpdate{}
	for _, lineage := range g.lineages {
		for _, record := range lineage.records {
			if record.update != nil {
				out[record.sequence] = record.update
			}
		}
	}
	return out
}
