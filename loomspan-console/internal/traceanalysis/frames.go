package traceanalysis

import (
	"bytes"
	"encoding/json"
	"sort"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

// frameBuild is the working state for one frame during iterative processing.
type frameBuild struct {
	frameID             string
	parentFrameID       string
	hasParent           bool
	frameType           TraceFrameType
	route               string
	openedMillis        int64
	openedSequence      int64
	closedMillis        int64
	opened              bool
	closed              bool
	children            []string
	directUsage         Usage
	directUsageComplete bool
	skillNames          map[string]struct{}
	outcome             *string
	attemptIDs          map[string]struct{}
	retrySequenceIDs    map[string]struct{}
	validationStatuses  map[string]struct{}
	failureIDs          map[string]struct{}
	assignment          *frameAssignment
}

type frameAssignment struct {
	planID               string
	taskID               string
	stepNumber           int64
	parallelGroup        *string
	effectiveConcurrency bool
}

type planTaskKey struct{ planID, taskID string }

type planTaskAssignment struct {
	planID, taskID       string
	stepNumber           int64
	parallelGroup        *string
	effectiveConcurrency bool
	assignedFrameID      *string
}

// assignmentResolver is the single authority for exact task assignment and
// nearest assigned-frame ancestry. It deliberately stores no inherited map.
type assignmentResolver struct {
	frames *frameGraph
	exact  map[planTaskKey]*planTaskAssignment
}

func (r *assignmentResolver) exactTask(planID, taskID string) *planTaskAssignment {
	if r == nil {
		return nil
	}
	return r.exact[planTaskKey{planID: planID, taskID: taskID}]
}

func (r *assignmentResolver) nearestAssignedAncestor(frameID string) (*planTaskAssignment, bool) {
	if r == nil || r.frames == nil {
		return nil, false
	}
	seen := map[string]struct{}{}
	for current := r.frames.frames[frameID]; current != nil; current = r.frames.frames[current.parentFrameID] {
		if _, duplicate := seen[current.frameID]; duplicate {
			return nil, false
		}
		seen[current.frameID] = struct{}{}
		if current.assignment != nil {
			a := current.assignment
			if exact := r.exactTask(a.planID, a.taskID); exact != nil && exact.assignedFrameID != nil && *exact.assignedFrameID == current.frameID {
				return exact, true
			}
			return nil, false
		}
		if !current.hasParent {
			return nil, current.frameID == r.frames.rootID
		}
	}
	return nil, false
}

type activeFramePathEntry struct {
	FrameID   string `json:"frameId"`
	FrameType string `json:"frameType"`
	Route     string `json:"route"`
}

type activeBranch struct {
	PlanID               *string                `json:"planId"`
	TaskID               *string                `json:"taskId"`
	StepNumber           *int64                 `json:"stepNumber"`
	ParallelGroup        *string                `json:"parallelGroup"`
	EffectiveConcurrency *bool                  `json:"effectiveConcurrency"`
	Path                 []activeFramePathEntry `json:"path"`
}

// frameGraph holds the working frame state and produces the final frame results,
// gaps, and uncertainties. It uses iterative parent traversal with explicit
// visitation states to reject cycles and support arbitrarily deep valid frame
// trees without stack growth.
type frameGraph struct {
	frames map[string]*frameBuild
	order  []string // insertion (first-open) order
	rootID string
}

// newFrameGraph creates an empty frame graph.
func newFrameGraph() *frameGraph {
	return &frameGraph{frames: map[string]*frameBuild{}}
}

// onFrameOpened records a FRAME_OPENED record.
func (g *frameGraph) onFrameOpened(rec *Record) *consolecore.Error {
	if rec.FrameID == "" || !rec.HasFrameType {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if existing, dup := g.frames[rec.FrameID]; dup && existing.opened {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if rec.HasParentFrame && rec.ParentFrameID == rec.FrameID {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if rec.HasParentFrame {
		parent, ok := g.frames[rec.ParentFrameID]
		if !ok || parent.closed {
			return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
		}
	} else {
		if rec.FrameType != FrameRootMission || g.rootID != "" {
			return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
		}
	}
	assignment, valid := decodeFrameAssignment(rec.Data)
	if !valid {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if assignment != nil && rec.FrameType != FrameStepExecution {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	f := &frameBuild{
		frameID:             rec.FrameID,
		parentFrameID:       rec.ParentFrameID,
		hasParent:           rec.HasParentFrame,
		frameType:           rec.FrameType,
		route:               rec.Route,
		openedMillis:        rec.TimestampMillis,
		openedSequence:      rec.Sequence,
		opened:              true,
		directUsageComplete: true,
		skillNames:          map[string]struct{}{},
		attemptIDs:          map[string]struct{}{},
		retrySequenceIDs:    map[string]struct{}{},
		validationStatuses:  map[string]struct{}{},
		failureIDs:          map[string]struct{}{},
		assignment:          assignment,
	}
	g.frames[rec.FrameID] = f
	g.order = append(g.order, rec.FrameID)
	if rec.HasParentFrame {
		if parent, ok := g.frames[rec.ParentFrameID]; ok {
			parent.children = append(parent.children, rec.FrameID)
		}
	} else if rec.FrameType == FrameRootMission {
		g.rootID = rec.FrameID
	}
	return nil
}

// onFrameClosed records a FRAME_CLOSED record.
func (g *frameGraph) onFrameClosed(rec *Record) *consolecore.Error {
	if rec.FrameID == "" {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	f, ok := g.frames[rec.FrameID]
	if !ok || !f.opened {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if f.closed {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if !rec.HasFrameType || rec.FrameType != f.frameType {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if rec.HasParentFrame != f.hasParent || rec.ParentFrameID != f.parentFrameID {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	if rec.Route != f.route || rec.TimestampMillis < f.openedMillis {
		return invalidityError(CategoryInvalidFrameRelationship, rec.TraceID)
	}
	f.closedMillis = rec.TimestampMillis
	f.closed = true
	return nil
}

func decodeFrameAssignment(raw json.RawMessage) (*frameAssignment, bool) {
	if len(raw) == 0 || bytes.Equal(raw, nullBytes) {
		return nil, true
	}
	if trimmed := bytes.TrimSpace(raw); len(trimmed) == 0 || trimmed[0] != '{' {
		return nil, true
	}
	fields, ok := decodeUniqueObject(raw)
	if !ok {
		return nil, false
	}
	keys := []string{"assignedTaskId", "parallelGroup", "effectiveConcurrency"}
	hasAny := false
	for _, key := range keys {
		if _, present := fields[key]; present {
			hasAny = true
		}
	}
	if !hasAny {
		return nil, true
	}
	readString := func(name string) (string, bool) {
		value, present := fields[name]
		if !present || bytes.Equal(value, nullBytes) {
			return "", false
		}
		var decoded string
		if json.Unmarshal(value, &decoded) != nil || strings.TrimSpace(decoded) == "" {
			return "", false
		}
		return decoded, true
	}
	planID, planOK := readString("planId")
	taskID, taskOK := readString("assignedTaskId")
	stepRaw, stepOK := fields["stepNumber"]
	effectiveRaw, effectiveOK := fields["effectiveConcurrency"]
	var step int64
	var effective bool
	if !planOK || !taskOK || !stepOK || json.Unmarshal(stepRaw, &step) != nil || step <= 0 ||
		!effectiveOK || json.Unmarshal(effectiveRaw, &effective) != nil {
		return nil, false
	}
	var group *string
	if groupRaw, present := fields["parallelGroup"]; present && !bytes.Equal(groupRaw, nullBytes) {
		var decoded string
		if json.Unmarshal(groupRaw, &decoded) != nil || strings.TrimSpace(decoded) == "" {
			return nil, false
		}
		group = &decoded
	}
	if effective && group == nil {
		return nil, false
	}
	return &frameAssignment{planID: planID, taskID: taskID, stepNumber: step,
		parallelGroup: group, effectiveConcurrency: effective}, true
}

func (g *frameGraph) activeBranchesWithResolver(resolver *assignmentResolver) ([]activeBranch, bool) {
	leaves := make([]*frameBuild, 0)
	for _, id := range g.order {
		frame := g.frames[id]
		if frame.closed {
			continue
		}
		openChild := false
		for _, childID := range frame.children {
			if child := g.frames[childID]; child != nil && !child.closed {
				openChild = true
				break
			}
		}
		if !openChild {
			leaves = append(leaves, frame)
		}
	}
	sort.SliceStable(leaves, func(i, j int) bool { return leaves[i].openedSequence < leaves[j].openedSequence })
	branches := make([]activeBranch, 0, len(leaves))
	for _, leaf := range leaves {
		chain := make([]*frameBuild, 0)
		seen := map[string]struct{}{}
		for current := leaf; current != nil; {
			if current.closed {
				return nil, false
			}
			if _, duplicate := seen[current.frameID]; duplicate {
				return nil, false
			}
			seen[current.frameID] = struct{}{}
			chain = append(chain, current)
			if !current.hasParent {
				break
			}
			current = g.frames[current.parentFrameID]
			if current == nil {
				return nil, false
			}
		}
		if len(chain) == 0 || chain[len(chain)-1].frameID != g.rootID {
			return nil, false
		}
		path := make([]activeFramePathEntry, len(chain))
		for i := range chain {
			frame := chain[len(chain)-1-i]
			path[i] = activeFramePathEntry{FrameID: frame.frameID, FrameType: string(frame.frameType), Route: frame.route}
		}
		branch := activeBranch{Path: path}
		nearest, valid := resolver.nearestAssignedAncestor(leaf.frameID)
		if !valid {
			return nil, false
		}
		if nearest != nil {
			planID, taskID, step, effective := nearest.planID, nearest.taskID, nearest.stepNumber, nearest.effectiveConcurrency
			branch.PlanID, branch.TaskID, branch.StepNumber, branch.EffectiveConcurrency = &planID, &taskID, &step, &effective
			if nearest.parallelGroup != nil {
				group := *nearest.parallelGroup
				branch.ParallelGroup = &group
			}
		}
		branches = append(branches, branch)
	}
	return branches, true
}

// addDirectUsage adds response usage to an explicitly recorded frame. It
// rejects unknown frame references and arithmetic overflow.
func (g *frameGraph) addDirectUsage(frameID string, u Usage) (bool, bool) {
	return g.addDirectUsageWithCompleteness(frameID, u, true)
}

func (g *frameGraph) addDirectUsageWithCompleteness(frameID string, u Usage, complete bool) (bool, bool) {
	if f, ok := g.frames[frameID]; ok {
		var arithmeticOK bool
		f.directUsage, arithmeticOK = f.directUsage.plus(u)
		f.directUsageComplete = f.directUsageComplete && complete
		return true, arithmeticOK
	}
	return false, false
}

// associateRecord captures explicit record-to-frame cross references. It does
// not infer relationships from adjacency or text.
func (g *frameGraph) associateRecord(rec *Record) {
	if rec.FrameID == "" {
		return
	}
	f, ok := g.frames[rec.FrameID]
	if !ok {
		return
	}
	addSetValue(f.skillNames, rec.metadataStringOrEmpty("skillName"))
	addSetValue(f.attemptIDs, rec.metadataStringOrEmpty("attemptId"))
	addSetValue(f.retrySequenceIDs, rec.metadataStringOrEmpty("retrySequenceId"))
	if rec.Type == RecordAdvisorRequestMutation || rec.Type == RecordAdvisorResponseMutation {
		addSetValue(f.validationStatuses, rec.metadataStringOrEmpty("status"))
	}
	if rec.Type == RecordErrorRecorded {
		addSetValue(f.failureIDs, rec.metadataStringOrEmpty("failureId"))
	}
	if rec.Type == RecordFrameClosed {
		if status := rec.metadataStringOrEmpty("status"); status != "" {
			f.outcome = &status
		}
	}
}

func addSetValue(set map[string]struct{}, value string) {
	if value != "" {
		set[value] = struct{}{}
	}
}

func sortedSetValues(set map[string]struct{}) []string {
	if len(set) == 0 {
		return nil
	}
	values := make([]string, 0, len(set))
	for value := range set {
		values = append(values, value)
	}
	sort.Strings(values)
	return values
}

// validate checks the frame graph for missing parents, cycles, close-before-open,
// and complete child intervals outside their complete parent. It uses iterative
// traversal with explicit visitation states so deep valid chains do not grow the
// stack.
func (g *frameGraph) validate() *consolecore.Error {
	for _, id := range g.order {
		f := g.frames[id]
		if f.hasParent {
			if _, ok := g.frames[f.parentFrameID]; !ok {
				return invalidityError(CategoryInvalidFrameRelationship, f.frameID)
			}
		}
	}
	// Cycle detection via color marking: WHITE (unvisited), GRAY (on current
	// path), BLACK (fully validated). This is O(N) total: each frame's parent
	// chain is walked only until a BLACK ancestor is reached, and each frame is
	// marked BLACK exactly once.
	const (
		white = 0
		gray  = 1
		black = 2
	)
	colors := make(map[string]int, len(g.order))
	for _, id := range g.order {
		if colors[id] == black {
			continue
		}
		// Walk the parent chain from id, marking frames GRAY. If we hit a GRAY
		// frame, it's a cycle. If we hit a BLACK frame or a root, the chain is
		// acyclic; mark all GRAY frames BLACK.
		var path []string
		current := id
		for current != "" {
			if colors[current] == black {
				break
			}
			if colors[current] == gray {
				return invalidityError(CategoryInvalidFrameRelationship, id)
			}
			colors[current] = gray
			path = append(path, current)
			f, ok := g.frames[current]
			if !ok {
				break
			}
			if !f.hasParent {
				break
			}
			current = f.parentFrameID
		}
		for _, p := range path {
			colors[p] = black
		}
	}
	// Complete child interval outside its complete parent.
	for _, id := range g.order {
		f := g.frames[id]
		if f.closed {
			if _, ok := subChecked(f.closedMillis, f.openedMillis); !ok {
				return invalidityError(CategoryInvalidFrameRelationship, f.frameID)
			}
		}
		if !f.closed || !f.hasParent {
			continue
		}
		parent, ok := g.frames[f.parentFrameID]
		if !ok || !parent.closed {
			continue
		}
		if f.openedMillis < parent.openedMillis || f.closedMillis > parent.closedMillis {
			return invalidityError(CategoryInvalidFrameRelationship, f.frameID)
		}
	}
	return nil
}

// results computes the final frame results, gaps, and uncertainties in canonical
// (first-open) order. Duration and usage calculations match the Java fixture
// corpus exactly. It reports false if any usage accumulation overflows int64.
func (g *frameGraph) resultsWithResolver(resolver *assignmentResolver) ([]frameResult, []gapResult, []uncertaintyResult, bool) {
	// Compute descendant usage bottom-up in one pass: process frames in reverse
	// insertion order so children are settled before parents, then accumulate
	// each frame's (direct + descendant) usage into its parent's descendant total.
	// This is O(frames + parent edges) instead of O(frames^2 * depth).
	descendant, descendantComplete, ok := g.computeDescendantUsage()
	if !ok {
		return nil, nil, nil, false
	}
	frames := make([]frameResult, 0, len(g.order))
	var gaps []gapResult
	var uncertainties []uncertaintyResult
	for _, id := range g.order {
		f := g.frames[id]
		assignment, ancestryOK := resolver.nearestAssignedAncestor(id)
		if !ancestryOK {
			return nil, nil, nil, false
		}
		var inclusiveDuration *int64
		var selfDuration *int64
		var closedTimestamp *int64
		if f.closed {
			d := f.closedMillis - f.openedMillis
			inclusiveDuration = &d
			closed := f.closedMillis
			closedTimestamp = &closed
			selfDuration = computeSelfDuration(f, g, &uncertainties)
		}
		desc := descendant[id]
		descComplete := descendantComplete[id]
		direct := f.directUsage
		inclusive, ok := direct.plus(desc)
		if !ok {
			return nil, nil, nil, false
		}
		var parentID *string
		if f.hasParent {
			p := f.parentFrameID
			parentID = &p
		}
		frames = append(frames, frameResult{
			FrameID:                 f.frameID,
			ParentFrameID:           parentID,
			ChildFrameIDs:           append([]string(nil), f.children...),
			FrameType:               string(f.frameType),
			Route:                   f.route,
			OpenedTimestampMillis:   f.openedMillis,
			ClosedTimestampMillis:   closedTimestamp,
			InclusiveDurationMillis: inclusiveDuration,
			SelfDurationMillis:      selfDuration,
			DirectUsage:             direct,
			DirectUsageComplete:     f.directUsageComplete,
			DescendantUsage:         desc,
			DescendantUsageComplete: descComplete,
			InclusiveUsage:          inclusive,
			InclusiveUsageComplete:  f.directUsageComplete && descComplete,
			SkillNames:              sortedSetValues(f.skillNames),
			Outcome:                 copyStringPointer(f.outcome),
			AttemptIDs:              sortedSetValues(f.attemptIDs),
			RetrySequenceIDs:        sortedSetValues(f.retrySequenceIDs),
			ValidationStatuses:      sortedSetValues(f.validationStatuses),
			FailureIDs:              sortedSetValues(f.failureIDs),
		})
		if assignment != nil {
			last := &frames[len(frames)-1]
			last.PlanID, last.TaskID = copyStringPointer(&assignment.planID), copyStringPointer(&assignment.taskID)
			step, effective := assignment.stepNumber, assignment.effectiveConcurrency
			last.StepNumber, last.ParallelGroup, last.EffectiveConcurrency = &step, copyStringPointer(assignment.parallelGroup), &effective
		}
		if !f.closed {
			gaps = append(gaps, gapResult{Kind: "OPEN_FRAME_NOT_CLOSED", FrameID: f.frameID})
		}
	}
	return frames, gaps, uncertainties, true
}

func copyStringPointer(value *string) *string {
	if value == nil {
		return nil
	}
	copy := *value
	return &copy
}

// computeDescendantUsage computes descendant usage for every frame in a single
// bottom-up pass. Frames are processed in reverse insertion (first-open) order
// so children are settled before parents. Each frame's inclusive usage (direct
// plus its own descendant usage) is added to its parent's descendant total.
// This is O(frames + parent edges). It reports false if any accumulation
// overflows int64.
func (g *frameGraph) computeDescendantUsage() (map[string]Usage, map[string]bool, bool) {
	descendant := make(map[string]Usage, len(g.order))
	complete := make(map[string]bool, len(g.order))
	for _, id := range g.order {
		complete[id] = true
	}
	for i := len(g.order) - 1; i >= 0; i-- {
		id := g.order[i]
		f := g.frames[id]
		// inclusive = direct + descendant (already computed for this frame)
		inclusive, ok := f.directUsage.plus(descendant[id])
		if !ok {
			return nil, nil, false
		}
		if f.hasParent {
			if _, ok := g.frames[f.parentFrameID]; !ok {
				continue
			}
			pDesc, ok := descendant[f.parentFrameID].plus(inclusive)
			if !ok {
				return nil, nil, false
			}
			descendant[f.parentFrameID] = pDesc
			complete[f.parentFrameID] = complete[f.parentFrameID] && f.directUsageComplete && complete[id]
		}
	}
	return descendant, complete, true
}

// computeSelfDuration subtracts the union of immediate complete child intervals.
// Overlap is valid concurrency evidence; only an incomplete child is uncertain.
func computeSelfDuration(f *frameBuild, g *frameGraph, uncertainties *[]uncertaintyResult) *int64 {
	if len(f.children) == 0 {
		d := f.closedMillis - f.openedMillis
		return &d
	}
	intervals := make([]frameInterval, 0, len(f.children))
	for _, childID := range f.children {
		child := g.frames[childID]
		if !child.closed {
			*uncertainties = append(*uncertainties, uncertaintyResult{
				Kind: "SELF_DURATION_UNAVAILABLE_INCOMPLETE_CHILD", FrameID: f.frameID,
			})
			return nil
		}
		intervals = append(intervals, frameInterval{
			start: child.openedMillis,
			end:   child.closedMillis,
		})
	}
	sort.Slice(intervals, func(i, j int) bool {
		return intervals[i].start < intervals[j].start || intervals[i].start == intervals[j].start && intervals[i].end < intervals[j].end
	})
	currentStart, currentEnd := intervals[0].start, intervals[0].end
	var childDuration int64
	for _, iv := range intervals[1:] {
		if iv.start < currentEnd {
			if iv.end > currentEnd {
				currentEnd = iv.end
			}
			continue
		}
		width, ok := subChecked(currentEnd, currentStart)
		if !ok {
			return nil
		}
		childDuration, ok = addChecked(childDuration, width)
		if !ok {
			return nil
		}
		currentStart, currentEnd = iv.start, iv.end
	}
	width, ok := subChecked(currentEnd, currentStart)
	if !ok {
		return nil
	}
	childDuration, ok = addChecked(childDuration, width)
	if !ok {
		return nil
	}
	inclusive, ok := subChecked(f.closedMillis, f.openedMillis)
	if !ok {
		return nil
	}
	self, ok := subChecked(inclusive, childDuration)
	if !ok {
		return nil
	}
	return &self
}

// frameInterval is one complete child frame's time interval.
type frameInterval struct {
	start int64
	end   int64
}
