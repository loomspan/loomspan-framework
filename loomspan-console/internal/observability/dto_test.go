package observability

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestInstanceStatusDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "instance-status.json")
	var status InstanceStatus
	if err := json.Unmarshal(body, &status); err != nil {
		t.Fatal(err)
	}
	if status.InstanceID != "11111111-1111-4111-8111-111111111111" {
		t.Fatalf("unexpected instanceId: %s", status.InstanceID)
	}
	if !status.LiveMonitoringAvailable {
		t.Fatal("expected liveMonitoringAvailable=true")
	}
	if status.RegisteredSkillCount != 3 {
		t.Fatalf("unexpected registeredSkillCount: %d", status.RegisteredSkillCount)
	}
	label := "P7"
	want := []NamespacePublication{
		{Namespace: "default", GenerationID: "generation-fixture", TracePersistencePolicy: "ONERROR"},
		{Namespace: "empty", GenerationID: "generation-empty", PublicationVersion: &label, TracePersistencePolicy: "ALWAYS"},
		{Namespace: "unlabeled", GenerationID: "generation-unlabeled", TracePersistencePolicy: "NEVER"},
	}
	if !reflect.DeepEqual(status.Namespaces, want) {
		t.Fatalf("namespace publications disagree with Java fixture: %#v", status.Namespaces)
	}
}

func TestSkillPageDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "skills-page.json")
	var page Page[SkillSummary]
	if err := json.Unmarshal(body, &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 3 {
		t.Fatalf("expected 3 items, got %d", len(page.Items))
	}
	if page.Items[1].Kind != "JAVA" || page.Items[1].SourcePath == "" {
		t.Fatalf("missing executor summary: %#v", page.Items[1])
	}
	if page.Items[2].Kind != "JAVA" || page.Items[2].BeanName != "dnsSkills" {
		t.Fatalf("missing Java summary: %#v", page.Items[2])
	}
	if page.Items[0].RegisteredName != "default:check-dns" {
		t.Fatalf("unexpected registeredName: %s", page.Items[0].RegisteredName)
	}
	if page.HasMore {
		t.Fatal("expected hasMore=false")
	}
	if page.NextCursor != nil {
		t.Fatalf("expected nextCursor=null, got %v", page.NextCursor)
	}
}

func TestExecutorSkillDetailDecodesFromFixture(t *testing.T) {
	var detail SkillDetail
	if err := json.Unmarshal(readFixture(t, "skill-executor-detail.json"), &detail); err != nil {
		t.Fatal(err)
	}
	if detail.Kind != "JAVA" || detail.SourcePath != "opaque label" || !strings.Contains(detail.Yaml, "executor: fetch-dns") {
		t.Fatalf("unexpected executor detail: %#v", detail)
	}
	if err := validateSkillDetail(detail, "default:fetch-dns"); err != nil {
		t.Fatal(err)
	}
}

func TestSkillDetailDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "skill-detail.json")
	var detail SkillDetail
	if err := json.Unmarshal(body, &detail); err != nil {
		t.Fatal(err)
	}
	if detail.RegisteredName != "default:check-dns" {
		t.Fatalf("unexpected registeredName: %s", detail.RegisteredName)
	}
	if detail.Yaml == "" {
		t.Fatal("expected non-empty yaml")
	}
}

func TestActivePageDecodesFromFixtureWithResumeCursor(t *testing.T) {
	body := readFixture(t, "active-executions-page.json")
	var page ActivePage
	if err := json.Unmarshal(body, &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(page.Items))
	}
	if page.Items[0].SessionID != "session-1" {
		t.Fatalf("unexpected sessionId: %s", page.Items[0].SessionID)
	}
	if page.ResumeCursor == nil || *page.ResumeCursor != "9" {
		t.Fatalf("expected resumeCursor=9, got %v", page.ResumeCursor)
	}
}

func TestActiveExecutionDetailDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "active-execution-detail.json")
	var exec ActiveExecution
	if err := json.Unmarshal(body, &exec); err != nil {
		t.Fatal(err)
	}
	if exec.SessionID != "session-1" {
		t.Fatalf("unexpected sessionId: %s", exec.SessionID)
	}
	if exec.EntrySkill != "default:check-dns" {
		t.Fatalf("unexpected entrySkill: %s", exec.EntrySkill)
	}
	if exec.Usage.SkillInvocations != 1 {
		t.Fatalf("unexpected skillInvocations: %d", exec.Usage.SkillInvocations)
	}
}

func TestActiveExecutionRequiresEveryUsageAndConfiguredLimitMember(t *testing.T) {
	body := readFixture(t, "active-execution-detail.json")
	for objectName, names := range map[string][]string{
		"usage":            requiredUsageMembers,
		"configuredLimits": requiredLimitMembers,
	} {
		for _, name := range names {
			t.Run(objectName+"/"+name, func(t *testing.T) {
				var execution map[string]json.RawMessage
				if err := json.Unmarshal(body, &execution); err != nil {
					t.Fatal(err)
				}
				var members map[string]json.RawMessage
				if err := json.Unmarshal(execution[objectName], &members); err != nil {
					t.Fatal(err)
				}
				delete(members, name)
				execution[objectName], _ = json.Marshal(members)
				mutated, _ := json.Marshal(execution)
				if err := validateActiveExecutionJSON(mutated); err == nil || !strings.Contains(err.Error(), objectName+"."+name+" is missing") {
					t.Fatalf("missing member error = %v", err)
				}
			})
		}
	}
}

func TestActiveExecutionPreservesObservedProviderZeroAndDisabledLimit(t *testing.T) {
	body := readFixture(t, "active-execution-detail.json")
	var execution map[string]json.RawMessage
	if err := json.Unmarshal(body, &execution); err != nil {
		t.Fatal(err)
	}
	for objectName, memberName := range map[string]string{"usage": "providerAttempts", "configuredLimits": "maxProviderAttempts"} {
		var members map[string]json.RawMessage
		if err := json.Unmarshal(execution[objectName], &members); err != nil {
			t.Fatal(err)
		}
		members[memberName] = json.RawMessage("0")
		execution[objectName], _ = json.Marshal(members)
	}
	mutated, _ := json.Marshal(execution)
	if err := validateActiveExecutionJSON(mutated); err != nil {
		t.Fatal(err)
	}
	var decoded ActiveExecution
	if err := json.Unmarshal(mutated, &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded.Usage.ProviderAttempts != 0 || decoded.ConfiguredLimits.MaxProviderAttempts != 0 {
		t.Fatalf("explicit zeros changed: usage=%d limit=%d", decoded.Usage.ProviderAttempts, decoded.ConfiguredLimits.MaxProviderAttempts)
	}
}

func TestActiveExecutionRejectsLegacyAndExtraBranchFields(t *testing.T) {
	body := readFixture(t, "active-execution-detail.json")
	var execution map[string]json.RawMessage
	if err := json.Unmarshal(body, &execution); err != nil {
		t.Fatal(err)
	}
	for _, legacy := range []string{"activePath", "totalFrameDepth", "activePathTruncated"} {
		t.Run(legacy, func(t *testing.T) {
			mutated := make(map[string]json.RawMessage, len(execution)+1)
			for name, value := range execution {
				mutated[name] = value
			}
			mutated[legacy] = json.RawMessage("null")
			encoded, _ := json.Marshal(mutated)
			if err := validateActiveExecutionJSON(encoded); err == nil || !strings.Contains(err.Error(), "legacy active-execution field") {
				t.Fatalf("legacy field error = %v", err)
			}
		})
	}

	branchBody, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-executions", "valid", "concurrent-siblings.json"))
	if err != nil {
		t.Fatal(err)
	}
	mutated := strings.Replace(string(branchBody), `"path":`, `"unexpected":true,"path":`, 1)
	if err := validateActiveExecutionJSON([]byte(mutated)); err == nil || !strings.Contains(err.Error(), "non-canonical fields") {
		t.Fatalf("extra branch field error = %v", err)
	}
}

func TestActiveExecutionDecodesCanonicalActiveBranchesAndExplicitNulls(t *testing.T) {
	body := []byte(`{
		"sessionId":"session-1",
		"traceId":"trace-1",
		"startedAt":"2026-07-25T11:59:55Z",
		"updatedAt":"2026-07-25T11:59:59Z",
		"activeBranches":[{
			"planId":null,"taskId":null,"stepNumber":null,"parallelGroup":null,"effectiveConcurrency":null,
			"path":[{"frameId":"frame-root","frameType":"ROOT_MISSION","route":"default:check-dns"}]
		}]
	}`)
	var execution ActiveExecution
	if err := json.Unmarshal(body, &execution); err != nil {
		t.Fatal(err)
	}
	if len(execution.ActiveBranches) != 1 {
		t.Fatalf("expected one active branch, got %d", len(execution.ActiveBranches))
	}
	branch := execution.ActiveBranches[0]
	if branch.PlanID != nil || branch.TaskID != nil || branch.StepNumber != nil || branch.ParallelGroup != nil || branch.EffectiveConcurrency != nil {
		t.Fatalf("explicit null assignment changed: %#v", branch)
	}
	entry := branch.Path[0]
	if entry.FrameID != "frame-root" || entry.FrameType != "ROOT_MISSION" || entry.Route != "default:check-dns" {
		t.Fatalf("unexpected active-branch entry: %#v", entry)
	}
	encoded, err := json.Marshal(execution)
	if err != nil {
		t.Fatal(err)
	}
	for _, field := range []string{`"planId":null`, `"taskId":null`, `"effectiveConcurrency":null`, `"frameId":"frame-root"`, `"frameType":"ROOT_MISSION"`, `"route":"default:check-dns"`} {
		if !json.Valid(encoded) || !containsJSONField(encoded, field) {
			t.Fatalf("encoded active execution does not preserve %s: %s", field, encoded)
		}
	}
}

func TestValidateActiveBranchesRejectsUnknownFrameType(t *testing.T) {
	branches := []ActiveBranch{{Path: []FramePathEntry{
		{FrameID: "root", FrameType: "ROOT_MISSION", Route: "root"},
		{FrameID: "child", FrameType: "UNKNOWN", Route: "child"},
	}}}
	if err := validateActiveBranches(branches); err == nil || !strings.Contains(err.Error(), "frameType is unknown") {
		t.Fatalf("expected unknown frame type rejection, got %v", err)
	}
}

func TestSharedActiveExecutionFixtureCorpus(t *testing.T) {
	root := filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", "active-executions")
	for _, kind := range []string{"valid", "invalid"} {
		entries, err := os.ReadDir(filepath.Join(root, kind))
		if err != nil {
			t.Fatal(err)
		}
		for _, entry := range entries {
			entry := entry
			t.Run(kind+"/"+entry.Name(), func(t *testing.T) {
				body, err := os.ReadFile(filepath.Join(root, kind, entry.Name()))
				if err != nil {
					t.Fatal(err)
				}
				rawErr := validateActiveExecutionJSON(body)
				var execution ActiveExecution
				decodeErr := json.Unmarshal(body, &execution)
				semanticErr := error(nil)
				if decodeErr == nil {
					semanticErr = validateActiveExecution(execution)
				}
				if kind == "valid" {
					if rawErr != nil || decodeErr != nil || semanticErr != nil {
						t.Fatalf("valid fixture rejected: raw=%v decode=%v semantic=%v", rawErr, decodeErr, semanticErr)
					}
				} else if rawErr == nil && decodeErr == nil && semanticErr == nil {
					t.Fatal("invalid fixture accepted")
				}
			})
		}
	}
}

func TestTracePageDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "traces-page.json")
	var page Page[Trace]
	if err := json.Unmarshal(body, &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(page.Items))
	}
	if page.Items[0].TraceID != "trace-1" {
		t.Fatalf("unexpected traceId: %s", page.Items[0].TraceID)
	}
}

func TestTraceDetailDecodesFromFixture(t *testing.T) {
	body := readFixture(t, "trace-detail.json")
	var trace Trace
	if err := json.Unmarshal(body, &trace); err != nil {
		t.Fatal(err)
	}
	if trace.TraceID != "trace-1" {
		t.Fatalf("unexpected traceId: %s", trace.TraceID)
	}
	if trace.Outcome != "SUCCEEDED" {
		t.Fatalf("unexpected outcome: %s", trace.Outcome)
	}
}

func TestContinuationPageDecodesWithCursor(t *testing.T) {
	body := readFixture(t, "continuation-page.json")
	var page Page[SkillSummary]
	if err := json.Unmarshal(body, &page); err != nil {
		t.Fatal(err)
	}
	if !page.HasMore {
		t.Fatal("expected hasMore=true")
	}
	if page.NextCursor == nil {
		t.Fatal("expected non-null nextCursor")
	}
}

func TestEmptyPageDecodesWithZeroItems(t *testing.T) {
	body := readFixture(t, "empty-page.json")
	var page Page[SkillSummary]
	if err := json.Unmarshal(body, &page); err != nil {
		t.Fatal(err)
	}
	if len(page.Items) != 0 {
		t.Fatalf("expected 0 items, got %d", len(page.Items))
	}
	if page.HasMore {
		t.Fatal("expected hasMore=false")
	}
}

func readFixture(t *testing.T, name string) []byte {
	t.Helper()
	body, err := os.ReadFile(filepath.Join("..", "..", "..", "loomspan-console-fixtures", "application-rest", name))
	if err != nil {
		t.Fatal(err)
	}
	return body
}

func containsJSONField(body []byte, field string) bool {
	return string(body) != "" && len(field) > 0 &&
		strings.Contains(string(body), field)
}

func TestJavaSkillDetailFixtureHasOnlyJavaLocation(t *testing.T) {
	var detail SkillDetail
	if err := json.Unmarshal(readFixture(t, "skill-java-detail.json"), &detail); err != nil {
		t.Fatal(err)
	}
	if err := validateSkillDetail(detail, "default:lookup-dns"); err != nil {
		t.Fatal(err)
	}
	if detail.Kind != "JAVA" || detail.BeanName != "dnsSkills" || detail.Method != "example.DnsSkills.lookup(java.lang.String)" || detail.SourcePath != "" || detail.Yaml != "" {
		t.Fatalf("unexpected Java detail: %#v", detail)
	}
}

func TestSkillSourceVariantsRejectMalformedDetails(t *testing.T) {
	for _, detail := range []SkillDetail{
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", SourcePath: "skill.yaml", Yaml: "name: skill"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "UNKNOWN", Declaration: "YAML"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "TYPESAFE", Declaration: "YAML", SourcePath: "skill.yaml", Yaml: "name: skill"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "YAML", Declaration: "YAML", Yaml: "name: skill"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "YAML", Declaration: "YAML", SourcePath: "skill.yaml", Yaml: "name: skill", BeanName: "bean"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "YAML", Declaration: "YAML", SourcePath: "skill.yaml"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "YAML", Declaration: "YAML", SourcePath: "skill.yaml", Yaml: "name: skill", Method: "lookup()"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "JAVA", Declaration: "SKILL_METHOD", BeanName: "bean"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "JAVA", Declaration: "SKILL_METHOD", Method: "lookup()"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "JAVA", Declaration: "SKILL_METHOD", BeanName: "bean", Method: "lookup()", SourcePath: "skill.yaml"},
		{RegisteredName: "default:skill", Namespace: "default", LocalName: "skill", Kind: "JAVA", Declaration: "SKILL_METHOD", BeanName: "bean", Method: "lookup()", Yaml: "name: skill"},
	} {
		if err := validateSkillDetail(detail, detail.RegisteredName); err == nil {
			t.Errorf("accepted malformed source: %#v", detail)
		}
	}
}

func TestSkillDecodersRejectInapplicableFieldsByPresence(t *testing.T) {
	for _, body := range []string{
		`{"registeredName":"default:java-skill","namespace":"default","localName":"java-skill","kind":"JAVA","declaration":"SKILL_METHOD","beanName":"bean","method":"lookup()","sourcePath":""}`,
		`{"registeredName":"default:java-skill","namespace":"default","localName":"java-skill","kind":"JAVA","declaration":"SKILL_METHOD","beanName":"bean","method":"lookup()","yaml":null}`,
		`{"registeredName":"default:model-skill","namespace":"default","localName":"model-skill","kind":"YAML","declaration":"YAML","sourcePath":"skill.yaml","beanName":null}`,
		`{"registeredName":"default:model-skill","namespace":"default","localName":"model-skill","kind":"YAML","declaration":"YAML","sourcePath":"skill.yaml","method":""}`,
	} {
		var detail SkillDetail
		if err := json.Unmarshal([]byte(body), &detail); err == nil {
			t.Fatalf("accepted inapplicable detail field: %s", body)
		}
	}

	for _, body := range []string{
		`{"registeredName":"default:java-skill","namespace":"default","localName":"java-skill","kind":"JAVA","declaration":"SKILL_METHOD","beanName":"bean","method":"lookup()","sourcePath":null}`,
		`{"registeredName":"default:model-skill","namespace":"default","localName":"model-skill","kind":"YAML","declaration":"YAML","sourcePath":"skill.yaml","beanName":null}`,
	} {
		var summary SkillSummary
		if err := json.Unmarshal([]byte(body), &summary); err == nil {
			t.Fatalf("accepted inapplicable summary field: %s", body)
		}
	}
}

func TestInstanceNamespaceWireValidation(t *testing.T) {
	valid := []string{`[]`, `[{"namespace":"empty","generationId":"g7","publicationVersion":null,"tracePersistencePolicy":"NEVER"}]`, `[{"namespace":"empty","generationId":"g7","publicationVersion":null,"tracePersistencePolicy":"ALWAYS"}]`, `[{"namespace":"empty","generationId":"g7","publicationVersion":null,"tracePersistencePolicy":"ONERROR"}]`, `[{"namespace":"empty","generationId":"g7","publicationVersion":"","tracePersistencePolicy":"ONERROR"}]`, `[{"namespace":"empty","generationId":"g7","publicationVersion":"  P7 ☃\n<b>  ","tracePersistencePolicy":"ONERROR"}]`}
	for _, namespaces := range valid {
		var status InstanceStatus
		if err := json.Unmarshal([]byte(`{"namespaces":`+namespaces+`}`), &status); err != nil {
			t.Fatalf("valid %s: %v", namespaces, err)
		}
		encoded, err := json.Marshal(status)
		var roundtrip InstanceStatus
		if err != nil || json.Unmarshal(encoded, &roundtrip) != nil || !reflect.DeepEqual(roundtrip.Namespaces, status.Namespaces) {
			t.Fatalf("round trip: %s, %v", encoded, err)
		}
	}
	invalid := []string{
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":null}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":null,"tracePersistencePolicy":null}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":null,"tracePersistencePolicy":""}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":null,"tracePersistencePolicy":"UNKNOWN"}]}`,
		`{}`, `{"namespaces":null}`, `{"namespaces":{}}`,
		`{"namespaces":[null]}`, `{"namespaces":[{}]}`,
		`{"namespaces":[{"namespace":"bad_name","generationId":"g","publicationVersion":null,"tracePersistencePolicy":"ONERROR"}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":" ","publicationVersion":null,"tracePersistencePolicy":"ONERROR"}]}`,
		`{"namespaces":[{"namespace":"empty","publicationVersion":null,"tracePersistencePolicy":"ONERROR"}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g"}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":7,"tracePersistencePolicy":"ONERROR"}]}`,
		`{"namespaces":[{"namespace":"empty","generationId":"g","publicationVersion":null,"tracePersistencePolicy":"ONERROR"},{"namespace":"empty","generationId":"h","publicationVersion":"P8","tracePersistencePolicy":"ONERROR"}]}`,
	}
	for _, wire := range invalid {
		var status InstanceStatus
		if err := json.Unmarshal([]byte(wire), &status); err == nil {
			t.Fatalf("accepted invalid %s", wire)
		}
	}
}
