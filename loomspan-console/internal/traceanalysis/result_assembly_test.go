package traceanalysis

import (
	"bytes"
	"context"
	"encoding/json"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/artifact"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

func TestAssemblyDecoderRequiresCompleteOwnedProvenance(t *testing.T) {
	good := `{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[{"destination":"/evidence","sourceKind":"input","sourcePath":"","parentMissionFrameId":"owner"}]}`
	assembly, valid := decodeResultAssembly(json.RawMessage(good))
	if !valid || assembly.ModelContributionRequired || assembly.OutputBindings[0].SourcePath != "" {
		t.Fatalf("assembly=%#v valid=%v", assembly, valid)
	}
	for _, raw := range []string{
		`{"skillName":"parent","owningMissionFrameId":"owner","outputBindings":[]}`,
		`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":null,"outputBindings":[{"destination":"/e","sourceKind":"input","sourcePath":"","parentMissionFrameId":"owner"}]}`,
		`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[{"destination":"/e","sourceKind":"input","sourcePath":null,"parentMissionFrameId":"owner"}]}`,
		`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[{"destination":"/e","sourceKind":"input","sourcePath":"","parentMissionFrameId":"other"}]}`,
		`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[{"destination":"/e","sourceKind":"child_result","sourcePath":"","parentMissionFrameId":"owner","sourceTaskId":"task","sourceSkill":"child"}]}`,
		`{"skillName":"parent","skillName":"other","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[]}`,
		`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false,"outputBindings":[{"destination":"/e","sourceKind":"input","sourcePath":"","parentMissionFrameId":"owner","sourceTaskId":"task"}]}`,
	} {
		if _, valid := decodeResultAssembly(json.RawMessage(raw)); valid {
			t.Fatalf("accepted malformed authority: %s", raw)
		}
	}
	facts := persistedRecordFacts{ResultAssembly: assembly}
	if facts.empty() {
		t.Fatal("assembly lost as empty")
	}
	encoded, _ := json.Marshal(facts)
	var restored persistedRecordFacts
	if err := json.Unmarshal(encoded, &restored); err != nil || !reflect.DeepEqual(restored.ResultAssembly, assembly) {
		t.Fatalf("stored authority changed: %s", encoded)
	}
}

func TestJavaAssemblyCorpusRetainsAuthorityAndAssembledPayload(t *testing.T) {
	for _, fixture := range []struct{ name, content string }{{"assembled-input-result", "9007199254740993"}, {"assembled-child-result", "Original model wording"}} {
		t.Run(fixture.name, func(t *testing.T) {
			raw, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", fixture.name+".ndjson"))
			if err != nil {
				t.Fatal(err)
			}
			expectedBytes, err := os.ReadFile(filepath.Join(fixtureRoot(t), "expected", fixture.name+".json"))
			if err != nil {
				t.Fatal(err)
			}
			var expected struct {
				ResultAssemblies []ResultAssembly `json:"resultAssemblies"`
			}
			if err := json.Unmarshal(expectedBytes, &expected); err != nil {
				t.Fatal(err)
			}
			h := newServiceTestHarnessForVersion(t, "trace-"+fixture.name, string(raw), fixtureCompatibilityVersion)
			page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Representation: RecordRepresentationLogical, InlineContent: true, Filter: RecordFilter{Types: []string{"RESULT_ASSEMBLED"}}, PageSize: 64})
			if domain != nil {
				t.Fatal(domain)
			}
			if len(page.Items) != 1 || len(expected.ResultAssemblies) != 1 || !reflect.DeepEqual(page.Items[0].Facts.ResultAssembly, &expected.ResultAssemblies[0]) {
				t.Fatalf("assembly authority mismatch: %#v", page.Items)
			}
			if fixture.name == "assembled-child-result" {
				for _, malformed := range []struct{ field, value string }{{"sourceTaskId", "unrelated-task"}, {"sourceSkill", "other-skill"}, {"parentMissionFrameId", "other-owner"}} {
					lines := bytes.Split(bytes.TrimSpace(raw), []byte("\n"))
					for index, line := range lines {
						var record map[string]any
						if json.Unmarshal(line, &record) != nil {
							t.Fatal("invalid canonical fixture")
						}
						if record["recordType"] != "RESULT_ASSEMBLED" {
							continue
						}
						provenance := record["metadata"].(map[string]any)["outputBindings"].([]any)[0].(map[string]any)
						provenance[malformed.field] = malformed.value
						lines[index], _ = json.Marshal(record)
					}
					_, domain := newProcessorForVersion(fixtureCompatibilityVersion).Process(artifact.ProcessRequest{Context: context.Background(), Raw: bytesReader(bytes.Join(lines, []byte("\n"))), Sink: &fakeSink{}})
					if domain == nil {
						t.Fatalf("accepted cross-source provenance %s", malformed.field)
					}
				}
			}
			if len(page.Items[0].Facts.Attempts) != 0 {
				t.Fatal("assembly invented model attempt")
			}
			if page.Items[0].Content == nil || !strings.Contains(string(page.Items[0].Content.InlineContent), fixture.content) || page.Items[0].Content.ContentRef == "" {
				t.Fatalf("assembled output content missing: %#v", page.Items[0].Content)
			}
		})
	}
}
