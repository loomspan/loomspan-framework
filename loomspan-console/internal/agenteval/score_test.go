package agenteval

import (
	"strings"
	"testing"
)

func TestScoringIsOrderAndProseIndependent(t *testing.T) {
	cases, err := LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range cases {
		r := validRecord(c)
		for i, j := 0, len(r.SupportedFacts)-1; i < j; i, j = i+1, j-1 {
			r.SupportedFacts[i], r.SupportedFacts[j] = r.SupportedFacts[j], r.SupportedFacts[i]
		}
		r.Answer = "Grounded observations: " + strings.Join(append(append([]string{}, r.SupportedFacts...), r.Limitations...), "; ")
		if err := Score(&r, c); err != nil {
			t.Fatalf("%s: %v: %v", c.ID, err, r.Failures)
		}
	}
}

func TestScoringRejectsOracleLabelsNotGroundedInAnswer(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["live-tools-only"]
	r := validRecord(c)
	r.Answer = "A generic answer with no reviewed criterion evidence."
	if Score(&r, c) == nil {
		t.Fatal("self-declared oracle labels passed without answer evidence")
	}
}
func TestScoringRejectsUnsupportedClaims(t *testing.T) {
	cases, _ := LoadCases()
	for _, c := range cases {
		r := validRecord(c)
		r.UnsupportedClaims = []string{"caused the failure"}
		if Score(&r, c) == nil {
			t.Fatal("unsupported claim accepted")
		}
		break
	}
}

func TestScoringRejectsSelfDeclaredFactsWithoutRequiredEvidencePath(t *testing.T) {
	cases, _ := LoadCases()
	for _, c := range cases {
		r := validRecord(c)
		r.Operations = r.Operations[:1]
		if Score(&r, c) == nil {
			t.Fatalf("%s accepted an incomplete operation path", c.ID)
		}
	}
}

func TestAmbiguousImportRejectsTraceSpecificInspection(t *testing.T) {
	cases, _ := LoadCases()
	c := cases["imported-tools-only"]
	r := validRecord(c)
	r.Operations = append(r.Operations, Operation{Tool: "LOOMSPAN_get_trace", ArgumentHash: Hash([]byte("selected ambiguous trace")), ResultHash: Hash([]byte("ambiguous trace result"))})
	if Score(&r, c) == nil {
		t.Fatal("trace-specific inspection after unresolved import ambiguity was accepted")
	}
}

func TestResultsRejectPairedRunsWithDifferentClientBuilds(t *testing.T) {
	cases, _ := LoadCases()
	var pair []Record
	for _, c := range cases {
		if c.PairID == "live-concurrency" {
			pair = append(pair, validRecord(c))
		}
	}
	pair[1].ClientBuild = "different"
	if ValidateResults(pair, map[string]Case{pair[0].CaseID: cases[pair[0].CaseID], pair[1].CaseID: cases[pair[1].CaseID]}) == nil {
		t.Fatal("mismatched paired metadata accepted")
	}
}

func TestResultsRejectStaleSkillPackageDigest(t *testing.T) {
	cases, _ := LoadCases()
	var records []Record
	selected := map[string]Case{}
	for _, c := range cases {
		if c.PairID == "live-concurrency" {
			record := validRecord(c)
			if c.Mode == "skill-assisted" {
				record.SkillDigest = strings.Repeat("f", 64)
			}
			record.Passed = true
			records = append(records, record)
			selected[c.ID] = c
		}
	}
	if ValidateResults(records, selected) == nil {
		t.Fatal("stale skill package digest accepted")
	}
}
