package agenteval

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestCasesUseCurrentToolsAndCanonicalFixtures(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	if len(cases) != 8 {
		t.Fatalf("cases=%d", len(cases))
	}
	for _, c := range cases {
		if !contains(c.AllowedTools, "LOOMSPAN_query_trace_plans") {
			t.Errorf("%s omits plan query", c.ID)
		}
	}
}

func TestPairValidationRejectsDifferentEvidenceBetweenModes(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	toolsOnly := cases["live-tools-only"]
	skillAssisted := cases["live-skill-assisted"]
	skillAssisted.RequiredFacts = append([]string{}, skillAssisted.RequiredFacts...)
	skillAssisted.RequiredFacts[0] = "different fact"
	if err := validatePairs(map[string]Case{toolsOnly.ID: toolsOnly, skillAssisted.ID: skillAssisted}); err == nil {
		t.Fatal("pair with different oracle facts was accepted")
	}
}
func TestCaseRejectsUnknownFieldsAndBadDigest(t *testing.T) {
	repo, _ := RepositoryRoot()
	c := Case{SchemaVersion: 1, ID: "x", PairID: "x", Mode: "tools-only", DeveloperPrompt: "x", Fixtures: []FixtureReference{{Path: "pom.xml", SHA256: "bad"}}, AllowedTools: []string{CurrentTools[0]}, RequiredFacts: []string{"f"}, RequiredLimitations: []string{"l"}, ForbiddenClaims: []string{"c"}, ForbiddenActions: []string{"a"}}
	if ValidateCase(c, repo) == nil {
		t.Fatal("bad digest accepted")
	}
	_ = os.WriteFile(filepath.Join(t.TempDir(), "unused"), nil, 0o600)
}

func TestPublishedSchemasDeclareEveryArtifactField(t *testing.T) {
	repo, _ := RepositoryRoot()
	for _, schemaName := range []string{"case", "record"} {
		raw, err := os.ReadFile(filepath.Join(repo, "loomspan-console", "agent-evals", "schema", schemaName+".schema.json"))
		if err != nil {
			t.Fatal(err)
		}
		var schema struct {
			Properties map[string]json.RawMessage `json:"properties"`
		}
		if err := json.Unmarshal(raw, &schema); err != nil {
			t.Fatal(err)
		}
		if len(schema.Properties) == 0 {
			t.Fatalf("%s schema declares no properties", schemaName)
		}
		pattern := filepath.Join(repo, "loomspan-console", "agent-evals", "cases", "*.json")
		if schemaName == "record" {
			pattern = filepath.Join(repo, "loomspan-console", "agent-evals", "results", "2026-09-05", "*.json")
		}
		files, _ := filepath.Glob(pattern)
		for _, name := range files {
			var artifact map[string]json.RawMessage
			raw, err := os.ReadFile(name)
			if err != nil {
				t.Fatal(err)
			}
			if err := json.Unmarshal(raw, &artifact); err != nil {
				t.Fatal(err)
			}
			for field := range artifact {
				if _, ok := schema.Properties[field]; !ok {
					t.Errorf("%s schema omits %s from %s", schemaName, field, filepath.Base(name))
				}
			}
		}
	}
}
func contains(v []string, w string) bool {
	for _, x := range v {
		if x == w {
			return true
		}
	}
	return false
}
