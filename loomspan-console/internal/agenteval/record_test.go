package agenteval

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRecordFailsClosedOnSensitiveOrIncompleteEvidence(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	var c Case
	for _, v := range cases {
		c = v
		break
	}
	r := validRecord(c)
	r.EventStreamComplete = false
	if ValidateRecord(r, cases) == nil {
		t.Fatal("incomplete stream accepted")
	}
	r = validRecord(c)
	r.Answer = "Authorization: Bearer secret"
	if ValidateRecord(r, cases) == nil {
		t.Fatal("secret accepted")
	}
	r = validRecord(c)
	r.Answer = `local export C:\opendev\private\events.json`
	if ValidateRecord(r, cases) == nil {
		t.Fatal("absolute machine path accepted")
	}
	for _, path := range []string{`local export /root/private/events.json`, `local export /data/private/events.json`, `local export D:/private/events.json`, `local export \\server\share\events.json`, `local export file:///workspace/events.json`} {
		r = validRecord(c)
		r.Answer = path
		if ValidateRecord(r, cases) == nil {
			t.Fatalf("absolute machine path accepted: %s", path)
		}
	}
	r = validRecord(c)
	r.Answer += ` reference https://example.invalid/docs/path`
	if err := ValidateRecord(r, cases); err != nil {
		t.Fatalf("ordinary URL rejected as a machine path: %v", err)
	}
	r = validRecord(c)
	r.EventStreamKind = "invented"
	if ValidateRecord(r, cases) == nil {
		t.Fatal("unknown event stream kind accepted")
	}
	r = validRecord(c)
	r.UnnecessaryCalls = -1
	if ValidateRecord(r, cases) == nil {
		t.Fatal("negative unnecessary-call count accepted")
	}
}

func TestImportRecordRejectsAbsolutePathInsideToolPayload(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	c := cases["live-tools-only"]
	stream := ClientEventStream{
		SchemaVersion: 1, RunID: "run", ConversationID: "conversation", CaseID: c.ID, Mode: c.Mode,
		Client: "Codex", ClientBuild: "test", Model: "test", ConsoleCommit: "commit",
		EventStreamKind: "headless", EventStreamComplete: true,
		Events:         []ClientEvent{{Kind: "tool", Tool: "LOOMSPAN_list_executions", Arguments: json.RawMessage(`{"pageSize":16}`), Result: json.RawMessage(`{"result":{"sourcePath":"/workspace/team/skill.yaml"}}`)}},
		SupportedFacts: c.RequiredFacts, Limitations: c.RequiredLimitations,
		FactEvidence: evidenceFor(c.RequiredFacts), LimitationEvidence: evidenceFor(c.RequiredLimitations),
	}
	resultHash := Hash(stream.Events[0].Result)
	stream.FactResultEvidence = resultEvidenceFor(c.RequiredFacts, resultHash)
	stream.LimitationResultEvidence = resultEvidenceFor(c.RequiredLimitations, resultHash)
	dir := t.TempDir()
	eventsName, answerName := filepath.Join(dir, "events.json"), filepath.Join(dir, "answer.txt")
	raw, _ := json.Marshal(stream)
	_ = os.WriteFile(eventsName, raw, 0o600)
	_ = os.WriteFile(answerName, []byte(strings.Join(append(append([]string{}, c.RequiredFacts...), c.RequiredLimitations...), ". ")), 0o600)
	if _, err := ImportRecord(eventsName, answerName, cases); err == nil || !strings.Contains(err.Error(), "absolute machine path") {
		t.Fatalf("absolute tool-payload path error=%v", err)
	}
}

func TestImportRecordRejectsStructuredAuthorizationHeader(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	c := cases["live-tools-only"]
	stream := ClientEventStream{
		SchemaVersion: 1, RunID: "run", ConversationID: "conversation", CaseID: c.ID, Mode: c.Mode,
		Client: "Codex", ClientBuild: "test", Model: "test", ConsoleCommit: "commit",
		EventStreamKind: "headless", EventStreamComplete: true,
		Events: []ClientEvent{{
			Kind: "tool", Tool: "LOOMSPAN_list_executions", Arguments: json.RawMessage(`{"pageSize":16}`),
			Result: json.RawMessage(`{"headers":{"Authorization":"Bearer secret"}}`),
		}},
		SupportedFacts: c.RequiredFacts, Limitations: c.RequiredLimitations,
		FactEvidence: evidenceFor(c.RequiredFacts), LimitationEvidence: evidenceFor(c.RequiredLimitations),
	}
	resultHash := Hash(stream.Events[0].Result)
	stream.FactResultEvidence = resultEvidenceFor(c.RequiredFacts, resultHash)
	stream.LimitationResultEvidence = resultEvidenceFor(c.RequiredLimitations, resultHash)
	dir := t.TempDir()
	eventsName, answerName := filepath.Join(dir, "events.json"), filepath.Join(dir, "answer.txt")
	raw, _ := json.Marshal(stream)
	_ = os.WriteFile(eventsName, raw, 0o600)
	_ = os.WriteFile(answerName, []byte(strings.Join(append(append([]string{}, c.RequiredFacts...), c.RequiredLimitations...), ". ")), 0o600)
	if _, err := ImportRecord(eventsName, answerName, cases); err == nil || !strings.Contains(err.Error(), "sensitive") {
		t.Fatalf("structured authorization header error=%v", err)
	}
}

func TestImportRecordDerivesHashesFromToolEvents(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	c := cases["live-tools-only"]
	stream := ClientEventStream{
		SchemaVersion: 1, RunID: "run", ConversationID: "conversation", CaseID: c.ID, Mode: c.Mode,
		Client: "Codex", ClientBuild: "test", Model: "test", ConsoleCommit: "commit",
		EventStreamKind: "headless", EventStreamComplete: true,
		Events:         []ClientEvent{{Kind: "tool", Tool: "LOOMSPAN_list_executions", Arguments: json.RawMessage(`{"pageSize":16}`), Result: json.RawMessage(`{"result":{"items":[]}}`)}},
		SupportedFacts: c.RequiredFacts, Limitations: c.RequiredLimitations,
		FactEvidence: evidenceFor(c.RequiredFacts), LimitationEvidence: evidenceFor(c.RequiredLimitations),
	}
	resultHash := Hash(stream.Events[0].Result)
	stream.FactResultEvidence = resultEvidenceFor(c.RequiredFacts, resultHash)
	stream.LimitationResultEvidence = resultEvidenceFor(c.RequiredLimitations, resultHash)
	dir := t.TempDir()
	eventsName, answerName := filepath.Join(dir, "events.json"), filepath.Join(dir, "answer.txt")
	raw, _ := json.Marshal(stream)
	if err := os.WriteFile(eventsName, raw, 0o600); err != nil {
		t.Fatal(err)
	}
	answerText := strings.Join(append(append([]string{}, c.RequiredFacts...), c.RequiredLimitations...), ". ")
	if err := os.WriteFile(answerName, []byte(answerText), 0o600); err != nil {
		t.Fatal(err)
	}
	record, err := ImportRecord(eventsName, answerName, cases)
	if err != nil {
		t.Fatal(err)
	}
	if record.Operations[0].ArgumentHash != Hash(stream.Events[0].Arguments) || len(record.ClientActions) != 0 {
		t.Fatalf("import did not derive event evidence: %#v", record)
	}
}

func TestImportRecordRejectsEveryClientActionByDefault(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	c := cases["live-tools-only"]
	stream := ClientEventStream{
		SchemaVersion: 1, RunID: "run", ConversationID: "conversation", CaseID: c.ID, Mode: c.Mode,
		Client: "Codex", ClientBuild: "test", Model: "test", ConsoleCommit: "commit",
		EventStreamKind: "headless", EventStreamComplete: true,
		Events:         []ClientEvent{{Kind: "client-action", Action: "invoke an unclassified helper"}},
		SupportedFacts: c.RequiredFacts, Limitations: c.RequiredLimitations,
	}
	dir := t.TempDir()
	eventsName, answerName := filepath.Join(dir, "events.json"), filepath.Join(dir, "answer.txt")
	raw, _ := json.Marshal(stream)
	_ = os.WriteFile(eventsName, raw, 0o600)
	_ = os.WriteFile(answerName, []byte("answer"), 0o600)
	if _, err := ImportRecord(eventsName, answerName, cases); err == nil || !strings.Contains(err.Error(), "unapproved client action") {
		t.Fatalf("unclassified client action error=%v", err)
	}
}

func TestImportRecordRejectsPreassembledRecord(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["live-tools-only"]
	raw, _ := CanonicalJSON(validRecord(c))
	dir := t.TempDir()
	eventsName, answerName := filepath.Join(dir, "events.json"), filepath.Join(dir, "answer.txt")
	_ = os.WriteFile(eventsName, raw, 0o600)
	_ = os.WriteFile(answerName, []byte("answer"), 0o600)
	if _, err := ImportRecord(eventsName, answerName, cases); err == nil {
		t.Fatal("preassembled record accepted as native client events")
	}
}
func TestCanonicalRecordIsStable(t *testing.T) {
	cases, _ := LoadCases()
	var c Case
	for _, v := range cases {
		c = v
		break
	}
	r := validRecord(c)
	a, _ := CanonicalJSON(r)
	b, _ := CanonicalJSON(r)
	if string(a) != string(b) || !strings.HasSuffix(string(a), "\n") {
		t.Fatal("canonical JSON unstable")
	}
}
func TestRecordRejectsPlaceholderEvidenceHashes(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["live-tools-only"]
	r := validRecord(c)
	r.Operations[0].ArgumentHash = strings.Repeat("a", 64)
	if ValidateRecord(r, cases) == nil {
		t.Fatal("placeholder evidence hash accepted")
	}
}
func TestRecordRejectsDuplicateRequestsUsedToInflateCoverage(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["live-tools-only"]
	r := validRecord(c)
	r.Operations = append(r.Operations, r.Operations[len(r.Operations)-1])
	if ValidateRecord(r, cases) == nil {
		t.Fatal("duplicate request metadata accepted")
	}
}
func TestRecordRejectsCriterionEvidenceWithoutObservedResult(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["live-tools-only"]
	r := validRecord(c)
	r.FactResultEvidence[c.RequiredFacts[0]] = []string{Hash([]byte("unobserved result"))}
	if ValidateRecord(r, cases) == nil {
		t.Fatal("criterion evidence referencing an unobserved result was accepted")
	}
}
func validRecord(c Case) Record {
	digest := Hash([]byte("valid-record-test-skill"))
	counts := map[string]int{}
	operation := func(tool string) Operation {
		counts[tool]++
		suffix := fmt.Sprintf(" %d", counts[tool])
		return Operation{Tool: tool, ArgumentHash: Hash([]byte(tool + suffix + " arguments")), ResultHash: Hash([]byte(tool + suffix + " result"))}
	}
	var ops []Operation
	switch c.PairID {
	case "live-concurrency":
		ops = []Operation{operation("LOOMSPAN_list_executions")}
		for range c.Fixtures {
			ops = append(ops, operation("LOOMSPAN_get_execution"))
		}
	case "finalized-plans":
		ops = []Operation{operation("LOOMSPAN_list_traces")}
		for range c.Fixtures {
			ops = append(ops, operation("LOOMSPAN_get_trace"))
		}
		ops = append(ops, operation("LOOMSPAN_query_trace_plans"), operation("LOOMSPAN_query_trace_plans"), operation("LOOMSPAN_query_trace_frames"), operation("LOOMSPAN_query_trace_frames"), operation("LOOMSPAN_query_trace_records"))
	case "imported-ambiguity":
		listed := operation("LOOMSPAN_list_traces")
		listed.Continuation = true
		listed2 := operation("LOOMSPAN_list_traces")
		listed2.ArgumentHash = Hash([]byte("continued list arguments"))
		ops = []Operation{listed, listed2}
	case "bounded-content-raw":
		records := operation("LOOMSPAN_query_trace_records")
		records.Continuation = true
		records.Externalized = true
		records2 := operation("LOOMSPAN_query_trace_records")
		records2.ArgumentHash = Hash([]byte("continued record arguments"))
		content := operation("LOOMSPAN_read_trace_content")
		content2 := operation("LOOMSPAN_read_trace_content")
		content2.ArgumentHash = Hash([]byte("continued content arguments"))
		raw := operation("LOOMSPAN_read_trace_artifact")
		raw.RawRead = true
		ops = []Operation{records, records2, content, content2, raw}
	}
	skill := ""
	if c.Mode == "skill-assisted" {
		skill, _ = RuntimeSkillDigest()
	}
	answer := strings.Join(append(append([]string{}, c.RequiredFacts...), c.RequiredLimitations...), ". ")
	resultEvidence := func(criteria []string) map[string][]string {
		evidence := make(map[string][]string, len(criteria))
		for _, criterion := range criteria {
			evidence[criterion] = []string{ops[len(ops)-1].ResultHash}
		}
		return evidence
	}
	return Record{SchemaVersion: 1, RunID: "run", ConversationID: "conversation", CaseID: c.ID, Mode: c.Mode, Client: "Codex", ClientBuild: "test", Model: "test", ConsoleCommit: digest, SkillDigest: skill, EventStreamKind: "headless", EventStreamComplete: true, Operations: ops, Answer: answer, SupportedFacts: c.RequiredFacts, Limitations: c.RequiredLimitations, FactEvidence: evidenceFor(c.RequiredFacts), LimitationEvidence: evidenceFor(c.RequiredLimitations), FactResultEvidence: resultEvidence(c.RequiredFacts), LimitationResultEvidence: resultEvidence(c.RequiredLimitations), ClarifiedAmbiguity: true}
}

func evidenceFor(criteria []string) map[string]string {
	evidence := make(map[string]string, len(criteria))
	for _, criterion := range criteria {
		evidence[criterion] = criterion
	}
	return evidence
}

func resultEvidenceFor(criteria []string, resultHash string) map[string][]string {
	evidence := make(map[string][]string, len(criteria))
	for _, criterion := range criteria {
		evidence[criterion] = []string{resultHash}
	}
	return evidence
}
