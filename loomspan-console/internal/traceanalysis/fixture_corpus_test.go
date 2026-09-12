package traceanalysis

import (
	"bytes"
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

// fixtureRoot locates the loomspan-console-fixtures directory without copying
// fixtures into the Go module. It mirrors the Java corpus test's resolution.
func fixtureRoot(t *testing.T) string {
	t.Helper()
	cwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("os.Getwd: %v", err)
	}
	candidates := []string{
		filepath.Join(cwd, "..", "..", "loomspan-console-fixtures"),
		filepath.Join(cwd, "..", "..", "..", "loomspan-console-fixtures"),
	}
	for _, c := range candidates {
		if info, err := os.Stat(filepath.Join(c, "traces")); err == nil && info.IsDir() {
			return c
		}
	}
	t.Fatalf("loomspan-console-fixtures not found relative to %s", cwd)
	return ""
}

// expectedFile is the JSON shape of one committed expected result.
type expectedFile struct {
	Case                    string                  `json:"case"`
	Valid                   bool                    `json:"valid"`
	TraceID                 string                  `json:"traceId,omitempty"`
	SessionID               string                  `json:"sessionId,omitempty"`
	Outcome                 string                  `json:"outcome,omitempty"`
	TerminalFailureID       *string                 `json:"terminalFailureId,omitempty"`
	ConfiguredLimits        *ConfiguredLimits       `json:"configuredLimits,omitempty"`
	AttributedUsage         json.RawMessage         `json:"attributedUsage,omitempty"`
	TerminalUsage           json.RawMessage         `json:"terminalUsage,omitempty"`
	UnattributedUsage       json.RawMessage         `json:"unattributedUsage,omitempty"`
	UsageComplete           bool                    `json:"usageComplete,omitempty"`
	Attempts                json.RawMessage         `json:"attempts,omitempty"`
	Retries                 json.RawMessage         `json:"retries,omitempty"`
	ValidationLinks         json.RawMessage         `json:"validationLinks,omitempty"`
	Frames                  json.RawMessage         `json:"frames,omitempty"`
	PlanProjections         json.RawMessage         `json:"planProjections,omitempty"`
	InheritedAssignments    json.RawMessage         `json:"inheritedFrameAssignments,omitempty"`
	UnframedAttributedUsage json.RawMessage         `json:"unframedAttributedUsage,omitempty"`
	Payloads                json.RawMessage         `json:"payloads,omitempty"`
	Gaps                    json.RawMessage         `json:"gaps,omitempty"`
	Uncertainties           json.RawMessage         `json:"uncertainties,omitempty"`
	ErrorCategory           string                  `json:"errorCategory,omitempty"`
	PlanTransitions         []fixturePlanTransition `json:"planTransitions,omitempty"`
	ActiveBranchPrefixes    []fixtureActivePrefix   `json:"activeBranchPrefixes,omitempty"`
}

type fixturePlanTransition struct {
	Sequence             int64    `json:"sequence"`
	PlanID               string   `json:"planId"`
	Kind                 string   `json:"kind"`
	TaskIDs              []string `json:"taskIds"`
	ParallelGroup        *string  `json:"parallelGroup"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	Outcome              *string  `json:"outcome"`
}

type fixtureActivePrefix struct {
	Sequence int64          `json:"sequence"`
	Branches []activeBranch `json:"branches"`
}

const fixtureCompatibilityVersion = "1.0.0-beta.4-SNAPSHOT"

func TestCanonicalConcurrentFixtureMatchesTransitionsPrefixesAndOverlap(t *testing.T) {
	root := fixtureRoot(t)
	traceBytes, err := os.ReadFile(filepath.Join(root, "traces", "canonical-concurrent-contract.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	expectedBytes, err := os.ReadFile(filepath.Join(root, "expected", "canonical-concurrent-contract.json"))
	if err != nil {
		t.Fatal(err)
	}
	var expected expectedFile
	if err := json.Unmarshal(expectedBytes, &expected); err != nil {
		t.Fatal(err)
	}
	prefixes := make(map[int64][]activeBranch, len(expected.ActiveBranchPrefixes))
	for _, prefix := range expected.ActiveBranchPrefixes {
		prefixes[prefix.Sequence] = prefix.Branches
	}
	frames, plans := newFrameGraph(), newPlanGraph()
	for _, line := range bytes.Split(bytes.TrimSpace(traceBytes), []byte("\n")) {
		record, domain := decodeRecord(line, RawAddress{Length: int64(len(line))})
		if domain != nil {
			t.Fatal(domain)
		}
		switch record.Type {
		case RecordFrameOpened:
			if domain := frames.onFrameOpened(record); domain != nil {
				t.Fatal(domain)
			}
		case RecordFrameClosed:
			if domain := frames.onFrameClosed(record); domain != nil {
				t.Fatal(domain)
			}
		}
		if domain := plans.onRecord(record, frames); domain != nil {
			t.Fatal(domain)
		}
		if want, selected := prefixes[record.Sequence]; selected {
			resolver, assignmentDomain := plans.assignmentResolver(frames, expected.TraceID)
			if assignmentDomain != nil {
				t.Fatal(assignmentDomain)
			}
			got, valid := frames.activeBranchesWithResolver(resolver)
			if !valid || !reflect.DeepEqual(got, want) {
				t.Fatalf("prefix %d branches=%+v want=%+v valid=%v", record.Sequence, got, want, valid)
			}
		}
	}
	if domain := plans.validateAssignments(frames, expected.TraceID); domain != nil {
		t.Fatal(domain)
	}
	gotTransitions := plans.transitions()
	if len(gotTransitions) != len(expected.PlanTransitions) {
		t.Fatalf("transitions=%+v want=%+v", gotTransitions, expected.PlanTransitions)
	}
	for index, got := range gotTransitions {
		want := expected.PlanTransitions[index]
		var outcome *string
		if got.outcome != "" {
			value := got.outcome
			outcome = &value
		}
		if got.sequence != want.Sequence || got.planID != want.PlanID || got.kind != want.Kind ||
			!reflect.DeepEqual(got.taskIDs, want.TaskIDs) || !reflect.DeepEqual(got.group, want.ParallelGroup) ||
			!reflect.DeepEqual(got.effectiveConcurrency, want.EffectiveConcurrency) || !reflect.DeepEqual(outcome, want.Outcome) {
			t.Fatalf("transition %d=%+v want=%+v", index, got, want)
		}
	}
	for _, pair := range [][2]string{{"step-b", "step-a"}} {
		left, right := frames.frames[pair[0]], frames.frames[pair[1]]
		if left.openedMillis >= right.closedMillis || right.openedMillis >= left.closedMillis {
			t.Fatalf("expected overlapping intervals for %s and %s", pair[0], pair[1])
		}
	}
	if frames.frames["step-c"] != nil {
		t.Fatal("dispatch-rejected task-c must not have an assignment frame")
	}
}

// TestFixtureCorpusMatchesJavaExpectedSemantics processes every Java fixture in
// place and compares the neutral semantic result or exact invalidity category
// against the committed expected file.
func TestFixtureCorpusMatchesJavaExpectedSemantics(t *testing.T) {
	root := fixtureRoot(t)
	traceDir := filepath.Join(root, "traces")
	expectedDir := filepath.Join(root, "expected")

	entries, err := os.ReadDir(traceDir)
	if err != nil {
		t.Fatalf("read traces dir: %v", err)
	}
	sort.Slice(entries, func(i, j int) bool { return entries[i].Name() < entries[j].Name() })

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".ndjson") {
			continue
		}
		name := strings.TrimSuffix(entry.Name(), ".ndjson")
		t.Run(name, func(t *testing.T) {
			tracePath := filepath.Join(traceDir, entry.Name())
			expectedPath := filepath.Join(expectedDir, name+".json")

			expectedBytes, err := os.ReadFile(expectedPath)
			if err != nil {
				t.Fatalf("read expected %s: %v", name, err)
			}
			var expected expectedFile
			if err := json.Unmarshal(expectedBytes, &expected); err != nil {
				t.Fatalf("parse expected %s: %v", name, err)
			}

			traceBytes, err := os.ReadFile(tracePath)
			if err != nil {
				t.Fatalf("read trace %s: %v", name, err)
			}

			sink := &fakeSink{}
			processor := newProcessorForVersion(fixtureCompatibilityVersion)
			_, domain := processor.Process(artifact.ProcessRequest{
				Context: context.Background(),
				Metadata: artifact.TraceMetadata{
					TraceID:   expected.TraceID,
					SessionID: expected.SessionID,
					Outcome:   expected.Outcome,
				},
				Raw:  bytesReader(traceBytes),
				Sink: sink,
			})

			if !expected.Valid {
				if domain == nil {
					t.Fatalf("expected invalid artifact for %s, got success", name)
				}
				if domain.Code != consolecore.CodeInvalidArtifact {
					t.Fatalf("expected INVALID_ARTIFACT for %s, got %v", name, domain.Code)
				}
				cat, ok := categoryOf(domain)
				if !ok {
					t.Fatalf("expected invalidity category for %s, got none", name)
				}
				if string(cat) != expected.ErrorCategory {
					t.Fatalf("expected category %s for %s, got %s", expected.ErrorCategory, name, cat)
				}
				return
			}

			if domain != nil {
				t.Fatalf("expected valid artifact for %s, got error: %v (category=%v)", name, domain, categoryOfSafe(domain))
			}
			assertFixtureRecordHistogram(t, sink, traceBytes)
			// Valid cases: build the full analysisResult from the manifest and
			// fact indexes, then compare against the committed expected file.
			// This catches calculation regressions that preserve identity/outcome.
			result := buildAnalysisResultFromSink(t, sink)
			compareAnalysisResult(t, name, result, expected)
		})
	}
}

func assertFixtureRecordHistogram(t *testing.T, sink *fakeSink, traceBytes []byte) {
	t.Helper()
	var m manifest
	if err := json.Unmarshal(sink.components[ComponentManifest], &m); err != nil {
		t.Fatalf("parse manifest histogram: %v", err)
	}
	physicalRecords := int64(0)
	expectedCounts := map[TraceRecordType]int64{}
	for _, line := range bytes.Split(traceBytes, []byte("\n")) {
		if len(bytes.TrimSpace(line)) > 0 {
			physicalRecords++
			var physical struct {
				RecordType TraceRecordType `json:"recordType"`
			}
			if err := json.Unmarshal(line, &physical); err != nil {
				t.Fatalf("parse physical record histogram oracle: %v", err)
			}
			expectedCounts[physical.RecordType]++
		}
	}
	histogramTotal := int64(0)
	for recordType, count := range m.RecordCountsByType {
		_, known := knownRecordType(string(recordType))
		if !known || count <= 0 {
			t.Fatalf("invalid histogram entry %q=%d", recordType, count)
		}
		histogramTotal += count
	}
	if histogramTotal != m.RecordCount || m.RecordCount != physicalRecords {
		t.Fatalf("histogram=%d manifest=%d physical=%d", histogramTotal, m.RecordCount, physicalRecords)
	}
	if !reflect.DeepEqual(m.RecordCountsByType, expectedCounts) {
		t.Fatalf("histogram=%v physical oracle=%v", m.RecordCountsByType, expectedCounts)
	}
}

func TestFixtureCorpusExposesCurrentEntrySkillPlanIdentityAndAcceptedAttempt(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "current-plan-semantic-evidence.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	sink := &fakeSink{}
	processed, domain := newProcessorForVersion(fixtureCompatibilityVersion).Process(artifact.ProcessRequest{Context: context.Background(), Raw: bytesReader(traceBytes), Sink: sink})
	if domain != nil {
		t.Fatalf("process fixture: %v", domain)
	}
	if processed.Metadata.EntrySkill != "test.entry" {
		t.Fatalf("entrySkill=%q", processed.Metadata.EntrySkill)
	}
	h := newServiceTestHarnessForVersion(t, "trace-current-plan-semantic-evidence", string(traceBytes), fixtureCompatibilityVersion)
	page, queryDomain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Representation: RecordRepresentationLogical, InlineContent: true, PageSize: 1000})
	if queryDomain != nil {
		t.Fatalf("query records: %v", queryDomain)
	}
	primaryCreated, nestedCreated, primaryUpdates, nestedUpdates, aggregateOmissions, explicitNull := 0, 0, 0, 0, 0, 0
	for _, record := range page.Items {
		if record.Content != nil && record.Content.InlineOmission == InlineOmissionAggregate {
			aggregateOmissions++
		}
		if record.Type == string(RecordEvidenceRecorded) && record.Content != nil && string(record.Content.InlineContent) == "null" {
			explicitNull++
		}
		if record.Facts.Plan == nil {
			continue
		}
		plan := record.Facts.Plan
		encodedPlan, err := json.Marshal(plan)
		if err != nil || plan.CapabilityName == "" || string(encodedPlan) != `{"planId":"`+plan.PlanID+`","capabilityName":"`+plan.CapabilityName+`"}` {
			t.Fatalf("plan reference is not minimal: %s err=%v", encodedPlan, err)
		}
		switch plan.PlanID {
		case "framework-primary-plan":
			if record.Type == string(RecordPlanCreated) {
				primaryCreated++
			} else {
				primaryUpdates++
			}
		case "framework-nested-plan":
			if record.Type == string(RecordPlanCreated) {
				nestedCreated++
			} else {
				nestedUpdates++
			}
		default:
			t.Fatalf("unexpected inferred plan: %+v", plan)
		}
	}
	if primaryCreated != 1 || nestedCreated != 1 || primaryUpdates != 11 || nestedUpdates != 1 || aggregateOmissions == 0 || explicitNull != 1 {
		t.Fatalf("primary=%d nested=%d primaryUpdates=%d nestedUpdates=%d aggregateOmissions=%d explicitNull=%d", primaryCreated, nestedCreated, primaryUpdates, nestedUpdates, aggregateOmissions, explicitNull)
	}
	search, searchDomain := h.service.Search(context.Background(), targetEvidence(h.scopeID), SearchQuery{Handle: h.handle, Text: "INC-2401", PageSize: 10})
	if searchDomain != nil {
		t.Fatalf("search fixture: %v", searchDomain)
	}
	if len(search.Items) != 2 || search.HasMore || len(search.SearchLimitations) != 0 {
		t.Fatalf("search=%+v", search)
	}
	seenSequences := map[int64]bool{}
	for _, match := range search.Items {
		if match.SearchedField != "content" || match.ContentID == "" || seenSequences[match.Sequence] {
			t.Fatalf("non-compact or duplicate semantic match: %+v", match)
		}
		seenSequences[match.Sequence] = true
	}
	if len(search.ContentDescriptors) != 2 {
		t.Fatalf("content descriptors=%+v", search.ContentDescriptors)
	}
}

func TestGeneratedRepeatedContentFixtureUsesOnePageDescriptor(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "repeated-search-content.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForVersion(t, "trace-repeated-search-content", string(traceBytes), fixtureCompatibilityVersion)
	page, domain := h.service.Search(context.Background(), targetEvidence(h.scopeID), SearchQuery{Handle: h.handle, Text: "needle", PageSize: 2})
	if domain != nil {
		t.Fatal(domain)
	}
	if len(page.Items) != 2 || !page.HasMore || page.NextCursor == "" || len(page.ContentDescriptors) != 1 {
		t.Fatalf("first page=%+v", page)
	}
	if page.Items[0].Sequence != page.Items[1].Sequence || page.Items[0].ContentID != "c1" || page.Items[1].ContentID != "c1" || page.Items[0].MatchOffset == page.Items[1].MatchOffset {
		t.Fatalf("first matches=%+v", page.Items)
	}
	continued, domain := h.service.Search(context.Background(), targetEvidence(h.scopeID), SearchQuery{Handle: h.handle, Text: "needle", PageSize: 2, Cursor: page.NextCursor})
	if domain != nil || len(continued.Items) != 1 || continued.HasMore || len(continued.ContentDescriptors) != 1 || continued.Items[0].ContentID != "c1" {
		t.Fatalf("continued page=%+v domain=%v", continued, domain)
	}
	if continued.ContentDescriptors[0].ContentRef != page.ContentDescriptors[0].ContentRef {
		t.Fatalf("descriptor changed across pages: first=%+v continued=%+v", page.ContentDescriptors, continued.ContentDescriptors)
	}
}

func TestAdvisorRetryFixtureProjectsRecordLocalValidationStatusWithoutChangingOwnership(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "advisor-retry.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Contains(traceBytes, []byte(`"recordType":"STRUCTURED_OUTPUT_RECORDED"`)) ||
		!bytes.Contains(traceBytes, []byte(`"status":"RETRYING"`)) ||
		!bytes.Contains(traceBytes, []byte(`"status":"PASSED"`)) ||
		!bytes.Contains(traceBytes, []byte(`"status":"retrying"`)) ||
		!bytes.Contains(traceBytes, []byte(`"status":"passed"`)) {
		t.Fatal("advisor-retry fixture does not preserve both producer status casings")
	}

	h := newServiceTestHarnessForVersion(t, "trace-advisor-retry", string(traceBytes), fixtureCompatibilityVersion)
	queryStatus := func(representation RecordRepresentation, status string) RecordSummary {
		t.Helper()
		page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{
			Handle: h.handle, Representation: representation, PageSize: 10,
			Filter: RecordFilter{Types: []string{string(RecordStructuredOutputRecorded)}, ValidationStatus: status},
		})
		if domain != nil || len(page.Items) != 1 || page.HasMore {
			t.Fatalf("%s %s query=%+v domain=%v", representation, status, page, domain)
		}
		if page.Items[0].ValidationStatus != status {
			t.Fatalf("%s %s projection=%q", representation, status, page.Items[0].ValidationStatus)
		}
		return page.Items[0]
	}

	for _, representation := range []RecordRepresentation{RecordRepresentationLogical, RecordRepresentationPhysical} {
		if retry := queryStatus(representation, "retrying"); retry.Sequence != 5 {
			t.Fatalf("%s retry sequence=%d", representation, retry.Sequence)
		}
		if passed := queryStatus(representation, "passed"); passed.Sequence != 10 {
			t.Fatalf("%s passed sequence=%d", representation, passed.Sequence)
		}
	}

	var traversed []RecordSummary
	cursor := ""
	for {
		page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{
			Handle: h.handle, Representation: RecordRepresentationLogical, PageSize: 1, Cursor: cursor,
			Filter: RecordFilter{Types: []string{string(RecordStructuredOutputRecorded)}},
		})
		if domain != nil || len(page.Items) != 1 {
			t.Fatalf("page-size-one traversal=%+v domain=%v", page, domain)
		}
		traversed = append(traversed, page.Items[0])
		if !page.HasMore {
			break
		}
		cursor = page.NextCursor
	}
	if len(traversed) != 2 || traversed[0].Sequence != 5 || traversed[0].ValidationStatus != "retrying" ||
		traversed[1].Sequence != 10 || traversed[1].ValidationStatus != "passed" {
		t.Fatalf("status traversal=%+v", traversed)
	}

	allRecords, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{
		Handle: h.handle, Representation: RecordRepresentationLogical, PageSize: 20,
	})
	if domain != nil {
		t.Fatalf("query all records: %v", domain)
	}
	validationOwners := map[int64]int{}
	for _, record := range allRecords.Items {
		if len(record.Facts.Validations) > 0 {
			validationOwners[record.Sequence] = len(record.Facts.Validations)
		}
		if record.Type == string(RecordStructuredOutputRecorded) && len(record.Facts.Validations) != 0 {
			t.Fatalf("outcome record owns relationship facts: %+v", record)
		}
	}
	if !reflect.DeepEqual(validationOwners, map[int64]int{6: 1, 9: 1}) {
		t.Fatalf("validation relationship owners=%v", validationOwners)
	}

	summary, domain := h.service.GetSummary(context.Background(), targetEvidence(h.scopeID), SummaryRequest{Handle: h.handle})
	if domain != nil || summary.RecordCount != 11 || summary.AttemptCount != 2 || summary.RetryCount != 1 ||
		summary.ValidationCount != 2 || summary.FailureCount != 0 || summary.TerminalFailureID != nil {
		t.Fatalf("summary=%+v domain=%v", summary, domain)
	}
}

func TestRecoveredProviderAttemptDiagnosticIsQueryableSearchableAndRangeReadable(t *testing.T) {
	traceBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "recovered-provider-attempt-diagnostic.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForVersion(t, "trace-recovered-provider-attempt-diagnostic", string(traceBytes), fixtureCompatibilityVersion)

	records, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{
		Handle: h.handle, Representation: RecordRepresentationLogical, PageSize: 10,
		Filter: RecordFilter{
			Types: []string{string(RecordModelAttemptFailed)}, FrameID: "model",
			AttemptID: "attempt-provider-1", RetrySequenceID: "retry-provider",
		},
	})
	if domain != nil || len(records.Items) != 1 {
		t.Fatalf("failed-attempt query=%+v domain=%v", records, domain)
	}
	record := records.Items[0]
	if record.Content == nil || !record.Content.Available || !record.Content.Complete ||
		record.Content.ContentRef == "" || record.Content.ContentType != "application/json" ||
		record.Content.InlineEligibility || len(record.Content.InlineContent) != 0 {
		t.Fatalf("descriptor-default failed-attempt content=%+v", record.Content)
	}
	if record.FailureID != "" || len(record.Facts.Failures) != 0 || len(record.Facts.Attempts) != 1 {
		t.Fatalf("failed attempt was misclassified or lost normalized facts: %+v", record)
	}
	if got := record.Facts.Attempts[0]; got.AttemptID != "attempt-provider-1" || got.ContentRef != record.Content.ContentRef ||
		got.FailureClassification != "TRANSIENT" || got.FailureCategory != "TIMEOUT" || got.RetryDecision != "RETRY" {
		t.Fatalf("attempt facts=%+v record content=%+v", got, record.Content)
	}

	var exact bytes.Buffer
	for _, line := range bytes.Split(bytes.TrimSpace(traceBytes), []byte("\n")) {
		var physical struct {
			RecordType string          `json:"recordType"`
			Metadata   json.RawMessage `json:"metadata"`
			Data       json.RawMessage `json:"data"`
		}
		if err := json.Unmarshal(line, &physical); err != nil {
			t.Fatal(err)
		}
		if physical.RecordType != string(RecordPayloadChunkAppended) {
			continue
		}
		var metadata struct {
			PayloadID string `json:"payloadId"`
		}
		if err := json.Unmarshal(physical.Metadata, &metadata); err != nil {
			t.Fatal(err)
		}
		if metadata.PayloadID == "payload-1" {
			var chunk string
			if err := json.Unmarshal(physical.Data, &chunk); err != nil {
				t.Fatal(err)
			}
			exact.WriteString(chunk)
		}
	}

	var reconstructed bytes.Buffer
	request := RangeRequest{Handle: h.handle, ContentRef: record.Content.ContentRef, MaxBytes: 97}
	for {
		page, rangeDomain := h.service.ReadContentRange(context.Background(), targetEvidence(h.scopeID), request)
		if rangeDomain != nil {
			t.Fatalf("read content range: %v", rangeDomain)
		}
		if page.Encoding != RangeEncodingText || page.ContentType != "application/json" || page.ActualStart != int64(reconstructed.Len()) {
			t.Fatalf("range metadata=%+v", page)
		}
		reconstructed.Write(page.Content)
		if !page.HasMore {
			if page.ActualEnd != page.TotalLength {
				t.Fatalf("final range=%+v", page)
			}
			break
		}
		request = RangeRequest{Handle: h.handle, ContentRef: record.Content.ContentRef, ContinueCursor: page.NextCursor, MaxBytes: 97}
	}
	if !bytes.Equal(reconstructed.Bytes(), exact.Bytes()) || !bytes.Contains(reconstructed.Bytes(), []byte("fixture suppressed context")) ||
		!bytes.Contains(reconstructed.Bytes(), []byte("gateway timeout evidence")) || !json.Valid(reconstructed.Bytes()) {
		t.Fatalf("reconstructed content did not preserve the exact diagnostic envelope")
	}

	search, searchDomain := h.service.Search(context.Background(), targetEvidence(h.scopeID), SearchQuery{
		Handle: h.handle, Text: "fixture suppressed context", PageSize: 10,
	})
	if searchDomain != nil || len(search.Items) != 1 || len(search.ContentDescriptors) != 1 ||
		search.Items[0].Sequence != record.Sequence || search.Items[0].SearchedField != "content" ||
		search.ContentDescriptors[0].ContentRef != record.Content.ContentRef {
		t.Fatalf("search=%+v domain=%v", search, searchDomain)
	}

	attempts, attemptDomain := h.service.QueryAttempts(context.Background(), targetEvidence(h.scopeID), AttemptQuery{Handle: h.handle, PageSize: 10})
	failures, failureDomain := h.service.QueryFailures(context.Background(), targetEvidence(h.scopeID), FailureQuery{Handle: h.handle, PageSize: 10})
	if attemptDomain != nil || failureDomain != nil || len(attempts.Items) != 2 || len(failures.Items) != 0 ||
		attempts.Items[0].Outcome != "FAILED" || attempts.Items[1].Outcome != "SUCCEEDED" {
		t.Fatalf("attempts=%+v failures=%+v attemptDomain=%v failureDomain=%v", attempts, failures, attemptDomain, failureDomain)
	}
}

func TestProcessorPublishesOnlyValidatedTraceStartedEntrySkill(t *testing.T) {
	cases := []struct {
		name string
		raw  string
		want string
	}{
		{name: "valid", raw: `{"entrySkill":"actual.skill"}`, want: "actual.skill"},
		{name: "missing", raw: `{"misleading":"filename.skill"}`},
		{name: "blank", raw: `{"entrySkill":"  "}`},
		{name: "non-string", raw: `{"entrySkill":{"name":"model.skill"}}`},
	}
	for _, test := range cases {
		t.Run(test.name, func(t *testing.T) {
			record := &Record{Metadata: json.RawMessage(test.raw)}
			got, ok := extractEntrySkill(record)
			if got != test.want || ok != (test.want != "") {
				t.Fatalf("entrySkill=%q present=%t", got, ok)
			}
		})
	}
}

func TestToolLifecycleFixturesExposeOneCanonicalStartAndTerminalRecord(t *testing.T) {
	root := fixtureRoot(t)
	cases := []struct {
		name         string
		terminalType string
		outcome      string
	}{
		{name: "planned-tool-success", terminalType: string(RecordToolCallCompleted), outcome: "SUCCEEDED"},
		{name: "unplanned-tool-failure", terminalType: string(RecordToolCallFailed), outcome: "FAILED"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			raw, err := os.ReadFile(filepath.Join(root, "traces", tc.name+".ndjson"))
			if err != nil {
				t.Fatalf("read fixture: %v", err)
			}
			sink := &fakeSink{}
			_, domain := newProcessorForVersion(fixtureCompatibilityVersion).Process(artifact.ProcessRequest{
				Context: context.Background(),
				Metadata: artifact.TraceMetadata{
					TraceID: "trace-" + tc.name, SessionID: "session-" + tc.name, Outcome: tc.outcome,
				},
				Raw: bytesReader(raw), Sink: sink,
			})
			if domain != nil {
				t.Fatalf("process fixture: %v", domain)
			}

			lines := bytes.Split(bytes.TrimSpace(raw), []byte("\n"))
			index := sink.components[artifact.ComponentName(ComponentRecordIndex)]
			if got, want := len(index)/recordIndexRowWidth, len(lines); got != want {
				t.Fatalf("record index reachability: got %d rows, want %d", got, want)
			}

			var starts, terminals []map[string]any
			for position, line := range lines {
				row := readRecordIndexRow(index[position*recordIndexRowWidth : (position+1)*recordIndexRowWidth])
				var record map[string]any
				if err := json.Unmarshal(line, &record); err != nil {
					t.Fatalf("parse indexed record %d: %v", position, err)
				}
				if row.Sequence != int64(record["sequence"].(float64)) {
					t.Fatalf("indexed sequence %d did not match record", row.Sequence)
				}
				switch record["recordType"] {
				case string(RecordToolCallStarted):
					starts = append(starts, record)
				case tc.terminalType:
					terminals = append(terminals, record)
				}
			}
			if len(starts) != 1 || len(terminals) != 1 {
				t.Fatalf("got %d starts and %d %s records", len(starts), len(terminals), tc.terminalType)
			}
			if starts[0]["frameId"] != terminals[0]["frameId"] {
				t.Fatalf("start and terminal frame identity differed")
			}
			if starts[0]["sequence"].(float64) >= terminals[0]["sequence"].(float64) {
				t.Fatalf("terminal did not follow start")
			}
		})
	}
}

// buildAnalysisResultFromSink reads the manifest and every fact index from the
// fake sink and assembles a complete analysisResult for comparison against the
// committed expected file.
func buildAnalysisResultFromSink(t *testing.T, sink *fakeSink) analysisResult {
	t.Helper()
	manifestBytes, ok := sink.components[ComponentManifest]
	if !ok {
		t.Fatal("expected manifest component")
	}
	var m manifest
	if err := json.Unmarshal(manifestBytes, &m); err != nil {
		t.Fatalf("parse manifest: %v", err)
	}
	result := analysisResult{
		Valid:     true,
		TraceID:   m.TraceID,
		SessionID: m.SessionID,
		Outcome:   m.Outcome,
	}
	result.TerminalFailureID = m.TerminalFailureID
	result.ConfiguredLimits = m.ConfiguredLimits

	// Usage index: ATTRIBUTED, UNATTRIBUTED, UNFRAMED_ATTRIBUTED, TERMINAL.
	usageFacts := readFactRowsRaw(t, sink, ComponentUsageIndex)
	if len(usageFacts) >= 4 {
		result.AttributedUsage = extractUsageFact(usageFacts[0], "ATTRIBUTED")
		result.UnattributedUsage = extractUsageFact(usageFacts[1], "UNATTRIBUTED")
		result.UnframedAttributedUsage = extractUsageFact(usageFacts[2], "UNFRAMED_ATTRIBUTED")
		result.TerminalUsage = extractUsageFact(usageFacts[3], "TERMINAL")
	}

	// Attempt index.
	result.Attempts = readFactRows[attemptResult](t, sink, ComponentAttemptIndex)

	// Retry results are not written as a separate index; derive from attempts.
	result.Retries = buildRetryResultsFromAttempts(result.Attempts)

	// Validation links.
	result.ValidationLinks = readFactRows[validationLink](t, sink, ComponentValidationIdx)

	// Frames.
	result.Frames = readFactRows[frameResult](t, sink, ComponentFrameIndex)
	result.Plans = readFactRows[PlanSummary](t, sink, ComponentPlanIndex)

	// Payloads.
	result.Payloads = readFactRows[payloadDescriptor](t, sink, ComponentPayloadIndex)

	// Gaps.
	result.Gaps = readFactRows[gapResult](t, sink, ComponentGapIndex)

	// UsageComplete: true unless any attempt has incomplete usage.
	result.UsageComplete = true
	for _, a := range result.Attempts {
		if !a.UsageComplete {
			result.UsageComplete = false
			break
		}
	}

	return result
}

// readFactRows reads length-prefixed JSON rows from a sink component and
// unmarshals each into the target type.
func readFactRows[T any](t *testing.T, sink *fakeSink, name component) []T {
	t.Helper()
	raw, ok := sink.components[artifact.ComponentName(name)]
	if !ok {
		return nil
	}
	r := bytes.NewReader(raw)
	var out []T
	for r.Len() > 0 {
		row, err := readLengthPrefixed(r)
		if err != nil {
			t.Fatalf("read fact row from %s: %v", name, err)
		}
		var v T
		if err := json.Unmarshal(row, &v); err != nil {
			t.Fatalf("parse fact row from %s: %v", name, err)
		}
		out = append(out, v)
	}
	return out
}

// readFactRowsRaw returns raw length-prefixed rows as []map[string]any for
// usage facts that use map[string]any.
func readFactRowsRaw(t *testing.T, sink *fakeSink, name component) []map[string]any {
	t.Helper()
	raw, ok := sink.components[artifact.ComponentName(name)]
	if !ok {
		return nil
	}
	r := bytes.NewReader(raw)
	var out []map[string]any
	for r.Len() > 0 {
		row, err := readLengthPrefixed(r)
		if err != nil {
			t.Fatalf("read fact row from %s: %v", name, err)
		}
		var v map[string]any
		if err := json.Unmarshal(row, &v); err != nil {
			t.Fatalf("parse fact row from %s: %v", name, err)
		}
		out = append(out, v)
	}
	return out
}

// extractUsageFact extracts a Usage from a usage fact map.
func extractUsageFact(fact map[string]any, kind string) Usage {
	return Usage{
		PromptUnits:     jsonInt64(fact["promptUnits"]),
		CompletionUnits: jsonInt64(fact["completionUnits"]),
		TotalUnits:      jsonInt64(fact["totalUnits"]),
	}
}

// jsonInt64 converts a JSON-decoded number to int64.
func jsonInt64(v any) int64 {
	f, ok := v.(float64)
	if !ok {
		return 0
	}
	return int64(f)
}

// buildRetryResultsFromAttempts aggregates attempt results into retry results
// matching the processor's buildAttemptResults logic.
func buildRetryResultsFromAttempts(attempts []attemptResult) []retryResult {
	retryUsage := map[string]Usage{}
	retryComplete := map[string]bool{}
	retryOrder := []string{}
	for _, a := range attempts {
		if _, seen := retryUsage[a.RetrySequenceID]; !seen {
			retryOrder = append(retryOrder, a.RetrySequenceID)
			retryComplete[a.RetrySequenceID] = true
		}
		var ok bool
		retryUsage[a.RetrySequenceID], ok = retryUsage[a.RetrySequenceID].plus(a.Usage)
		if !ok {
			panic("overflow in test helper buildRetryResultsFromAttempts")
		}
		retryComplete[a.RetrySequenceID] = retryComplete[a.RetrySequenceID] && a.UsageComplete
	}
	retries := make([]retryResult, 0, len(retryOrder))
	for _, rid := range retryOrder {
		retries = append(retries, retryResult{
			RetrySequenceID: rid,
			Usage:           retryUsage[rid],
			UsageComplete:   retryComplete[rid],
		})
	}
	return retries
}

// compareAnalysisResult compares the processor's calculated result against the
// committed expected file field by field.
func compareAnalysisResult(t *testing.T, name string, result analysisResult, expected expectedFile) {
	t.Helper()
	if result.TraceID != expected.TraceID {
		t.Errorf("traceId mismatch for %s: got %s want %s", name, result.TraceID, expected.TraceID)
	}
	if result.SessionID != expected.SessionID {
		t.Errorf("sessionId mismatch for %s: got %s want %s", name, result.SessionID, expected.SessionID)
	}
	if result.Outcome != expected.Outcome {
		t.Errorf("outcome mismatch for %s: got %s want %s", name, result.Outcome, expected.Outcome)
	}
	if (result.TerminalFailureID == nil) != (expected.TerminalFailureID == nil) {
		t.Errorf("terminalFailureId presence mismatch for %s: got %v want %v", name, result.TerminalFailureID, expected.TerminalFailureID)
	}
	if result.TerminalFailureID != nil && expected.TerminalFailureID != nil && *result.TerminalFailureID != *expected.TerminalFailureID {
		t.Errorf("terminalFailureId mismatch for %s: got %s want %s", name, *result.TerminalFailureID, *expected.TerminalFailureID)
	}
	if (result.ConfiguredLimits == nil) != (expected.ConfiguredLimits == nil) ||
		(result.ConfiguredLimits != nil && expected.ConfiguredLimits != nil && *result.ConfiguredLimits != *expected.ConfiguredLimits) {
		t.Errorf("configuredLimits mismatch for %s: got %v want %v", name, result.ConfiguredLimits, expected.ConfiguredLimits)
	}
	// Usage comparison.
	if len(expected.AttributedUsage) > 0 {
		compareUsage(t, name, "attributedUsage", result.AttributedUsage, expected.AttributedUsage)
	}
	if len(expected.TerminalUsage) > 0 {
		compareUsage(t, name, "terminalUsage", result.TerminalUsage, expected.TerminalUsage)
	}
	if len(expected.UnattributedUsage) > 0 {
		compareUsage(t, name, "unattributedUsage", result.UnattributedUsage, expected.UnattributedUsage)
	}
	if len(expected.UnframedAttributedUsage) > 0 {
		compareUsage(t, name, "unframedAttributedUsage", result.UnframedAttributedUsage, expected.UnframedAttributedUsage)
	}
	// UsageComplete.
	if expected.UsageComplete && !result.UsageComplete {
		t.Errorf("usageComplete mismatch for %s: got false want true", name)
	}
	// Attempts.
	compareJSONArrays(t, name, "attempts", result.Attempts, expected.Attempts)
	// Retries.
	compareJSONArrays(t, name, "retries", result.Retries, expected.Retries)
	// ValidationLinks.
	compareJSONArrays(t, name, "validationLinks", result.ValidationLinks, expected.ValidationLinks)
	// Frames.
	compareJSONArrays(t, name, "frames", projectFixtureFrames(result.Frames), expected.Frames)
	if len(expected.PlanProjections) > 0 {
		compareJSONArrays(t, name, "planProjections", projectFixturePlans(result.Plans), expected.PlanProjections)
	}
	if len(expected.InheritedAssignments) > 0 {
		compareJSONArrays(t, name, "inheritedFrameAssignments", projectFixtureAssignments(result.Frames), expected.InheritedAssignments)
	}
	// Payloads.
	compareJSONArrays(t, name, "payloads", result.Payloads, expected.Payloads)
	// Gaps.
	compareJSONArrays(t, name, "gaps", result.Gaps, expected.Gaps)
	// Uncertainties: not written as a separate index; compare counts via manifest.
	// The manifest's UncertaintyCount is checked separately.
}

type fixturePlanTask struct {
	TaskID               string   `json:"taskId"`
	Status               string   `json:"status"`
	AssignedFrameID      *string  `json:"assignedFrameId"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	FailureIDs           []string `json:"failureIds"`
}

type fixturePlanUnit struct {
	ParallelGroup        *string  `json:"parallelGroup"`
	TaskIDs              []string `json:"taskIds"`
	EffectiveConcurrency *bool    `json:"effectiveConcurrency"`
	ObservedOverlap      *bool    `json:"observedOverlap"`
}

type fixturePlanProjection struct {
	PlanID           string            `json:"planId"`
	Status           string            `json:"status"`
	CreationSequence int64             `json:"creationSequence"`
	Tasks            []fixturePlanTask `json:"tasks"`
	ExecutionUnits   []fixturePlanUnit `json:"executionUnits"`
}

func projectFixturePlans(plans []PlanSummary) []fixturePlanProjection {
	out := make([]fixturePlanProjection, len(plans))
	for i, plan := range plans {
		projected := fixturePlanProjection{PlanID: plan.PlanID, Status: plan.Status, CreationSequence: plan.CreationSequence,
			Tasks: make([]fixturePlanTask, len(plan.Tasks)), ExecutionUnits: make([]fixturePlanUnit, len(plan.ExecutionUnits))}
		for taskIndex, task := range plan.Tasks {
			projected.Tasks[taskIndex] = fixturePlanTask{TaskID: task.TaskID, Status: task.Status,
				AssignedFrameID: task.AssignedFrameID, EffectiveConcurrency: task.EffectiveConcurrency,
				FailureIDs: append([]string{}, task.FailureIDs...)}
		}
		for unitIndex, unit := range plan.ExecutionUnits {
			projected.ExecutionUnits[unitIndex] = fixturePlanUnit{ParallelGroup: unit.ParallelGroup,
				TaskIDs: append([]string{}, unit.TaskIDs...), EffectiveConcurrency: unit.EffectiveConcurrency, ObservedOverlap: unit.ObservedOverlap}
		}
		out[i] = projected
	}
	return out
}

type fixtureInheritedAssignment struct {
	FrameID              string  `json:"frameId"`
	PlanID               string  `json:"planId"`
	TaskID               string  `json:"taskId"`
	StepNumber           int64   `json:"stepNumber"`
	ParallelGroup        *string `json:"parallelGroup"`
	EffectiveConcurrency bool    `json:"effectiveConcurrency"`
}

func projectFixtureAssignments(frames []frameResult) []fixtureInheritedAssignment {
	out := make([]fixtureInheritedAssignment, 0)
	for _, frame := range frames {
		if frame.PlanID == nil || frame.TaskID == nil || frame.StepNumber == nil || frame.EffectiveConcurrency == nil {
			continue
		}
		out = append(out, fixtureInheritedAssignment{FrameID: frame.FrameID, PlanID: *frame.PlanID,
			TaskID: *frame.TaskID, StepNumber: *frame.StepNumber, ParallelGroup: frame.ParallelGroup,
			EffectiveConcurrency: *frame.EffectiveConcurrency})
	}
	return out
}

// fixtureFrameResult is the Java-produced base frame shape. Assignment
// inheritance is compared separately through inheritedFrameAssignments, while
// query-only cross references and completeness flags remain focused Go tests.
type fixtureFrameResult struct {
	FrameID                 string  `json:"frameId"`
	ParentFrameID           *string `json:"parentFrameId"`
	FrameType               string  `json:"frameType"`
	Route                   string  `json:"route,omitempty"`
	InclusiveDurationMillis *int64  `json:"inclusiveDurationMillis"`
	SelfDurationMillis      *int64  `json:"selfDurationMillis"`
	DirectUsage             Usage   `json:"directUsage"`
	DescendantUsage         Usage   `json:"descendantUsage"`
	InclusiveUsage          Usage   `json:"inclusiveUsage"`
}

func projectFixtureFrames(frames []frameResult) []fixtureFrameResult {
	out := make([]fixtureFrameResult, len(frames))
	for i, frame := range frames {
		out[i] = fixtureFrameResult{
			FrameID: frame.FrameID, ParentFrameID: frame.ParentFrameID, FrameType: frame.FrameType,
			Route: frame.Route, InclusiveDurationMillis: frame.InclusiveDurationMillis,
			SelfDurationMillis: frame.SelfDurationMillis, DirectUsage: frame.DirectUsage,
			DescendantUsage: frame.DescendantUsage, InclusiveUsage: frame.InclusiveUsage,
		}
	}
	return out
}

// compareUsage compares a Usage value against a JSON RawMessage from the
// expected file.
func compareUsage(t *testing.T, name, field string, got Usage, expectedRaw json.RawMessage) {
	t.Helper()
	var expected Usage
	if err := json.Unmarshal(expectedRaw, &expected); err != nil {
		t.Fatalf("parse %s for %s: %v", field, name, err)
	}
	if got != expected {
		t.Errorf("%s mismatch for %s: got %+v want %+v", field, name, got, expected)
	}
}

// compareJSONArrays compares a calculated slice against the expected JSON array
// by normalizing both to canonical JSON. Nil slices are treated as empty arrays
// to match the Java fixture corpus convention.
func compareJSONArrays[T any](t *testing.T, name, field string, got []T, expectedRaw json.RawMessage) {
	t.Helper()
	// Treat nil and empty as equivalent.
	if len(got) == 0 {
		got = []T{}
	}
	// Normalize expected: if it's null or missing, treat as empty array.
	var expNorm any
	if len(expectedRaw) == 0 || string(expectedRaw) == "null" {
		expNorm = []any{}
	} else if err := json.Unmarshal(expectedRaw, &expNorm); err != nil {
		t.Fatalf("normalize expected %s for %s: %v", field, name, err)
	}
	gotJSON, err := json.Marshal(got)
	if err != nil {
		t.Fatalf("marshal %s for %s: %v", field, name, err)
	}
	var gotNorm any
	if err := json.Unmarshal(gotJSON, &gotNorm); err != nil {
		t.Fatalf("normalize got %s for %s: %v", field, name, err)
	}
	gotCanonical, _ := json.Marshal(gotNorm)
	expCanonical, _ := json.Marshal(expNorm)
	if string(gotCanonical) != string(expCanonical) {
		t.Errorf("%s mismatch for %s:\n got: %s\nwant: %s", field, name, gotCanonical, expCanonical)
	}
}

// categoryOfSafe returns the category string or "<none>".
func categoryOfSafe(err *consolecore.Error) string {
	cat, ok := categoryOf(err)
	if !ok {
		return "<none>"
	}
	return string(cat)
}

// bytesReader wraps a byte slice as an io.Reader.
func bytesReader(data []byte) *bytes.Reader {
	return bytes.NewReader(data)
}
