package agenteval

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agentskills"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpadapter"
)

const CaseSchemaVersion = 1

var CurrentTools = []string{
	mcpadapter.RuntimeToolName, mcpadapter.ListSkillsToolName, mcpadapter.GetSkillToolName,
	mcpadapter.ListExecutionsToolName, mcpadapter.GetExecutionToolName, mcpadapter.GetExecutionActivityToolName,
	mcpadapter.ListTracesToolName, mcpadapter.GetTraceToolName, mcpadapter.QueryTracePlansToolName,
	mcpadapter.QueryTraceFramesToolName, mcpadapter.QueryTraceRecordsToolName,
	mcpadapter.ReadTraceContentToolName, mcpadapter.ReadTraceArtifactToolName,
}

type FixtureReference struct {
	Path   string `json:"path"`
	SHA256 string `json:"sha256"`
}
type fixtureManifest struct {
	SchemaVersion int                `json:"schemaVersion"`
	Fixtures      []FixtureReference `json:"fixtures"`
}
type Case struct {
	SchemaVersion       int                `json:"schemaVersion"`
	ID                  string             `json:"id"`
	PairID              string             `json:"pairId"`
	Mode                string             `json:"mode"`
	DeveloperPrompt     string             `json:"developerPrompt"`
	Fixtures            []FixtureReference `json:"fixtures"`
	AllowedTools        []string           `json:"allowedTools"`
	RequiredFacts       []string           `json:"requiredFacts"`
	RequiredLimitations []string           `json:"requiredLimitations"`
	ForbiddenClaims     []string           `json:"forbiddenClaims"`
	ForbiddenActions    []string           `json:"forbiddenActions"`
}

func RepositoryRoot() (string, error) {
	_, source, _, ok := runtime.Caller(0)
	if !ok {
		return "", fmt.Errorf("resolve source")
	}
	return filepath.Abs(filepath.Join(filepath.Dir(source), "..", "..", ".."))
}

// RuntimeSkillDigest identifies the complete canonical six-file package, not
// only its entrypoint. Length-delimited names and bytes make the digest
// independent of platform path separators and unambiguous across file splits.
func RuntimeSkillDigest() (string, error) {
	repo, err := RepositoryRoot()
	if err != nil {
		return "", err
	}
	root := filepath.Join(repo, "loomspan-console", "agent-skills", agentskills.RuntimeDebuggingSkillName)
	return skillPackageDigest(root)
}

func skillPackageDigest(root string) (string, error) {
	hash := sha256.New()
	for _, name := range agentskills.RuntimeDebuggingFiles {
		content, err := os.ReadFile(filepath.Join(root, filepath.FromSlash(name)))
		if err != nil {
			return "", err
		}
		_, _ = fmt.Fprintf(hash, "%d:%s%d:", len(name), name, len(content))
		_, _ = hash.Write(content)
	}
	return hex.EncodeToString(hash.Sum(nil)), nil
}

func LoadCases() (map[string]Case, error) {
	repo, err := RepositoryRoot()
	if err != nil {
		return nil, err
	}
	dir := filepath.Join(repo, "loomspan-console", "agent-evals", "cases")
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	out := map[string]Case{}
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".json" {
			return nil, fmt.Errorf("unexpected case path %q", entry.Name())
		}
		raw, err := os.ReadFile(filepath.Join(dir, entry.Name()))
		if err != nil {
			return nil, err
		}
		var value Case
		dec := json.NewDecoder(bytes.NewReader(raw))
		dec.DisallowUnknownFields()
		if err := dec.Decode(&value); err != nil {
			return nil, fmt.Errorf("parse %s: %w", entry.Name(), err)
		}
		if err := requireJSONEOF(dec); err != nil {
			return nil, fmt.Errorf("parse %s: %w", entry.Name(), err)
		}
		if err := ValidateCase(value, repo); err != nil {
			return nil, fmt.Errorf("case %s: %w", entry.Name(), err)
		}
		if strings.TrimSuffix(entry.Name(), ".json") != value.ID {
			return nil, fmt.Errorf("filename does not match id")
		}
		if _, found := out[value.ID]; found {
			return nil, fmt.Errorf("duplicate case %q", value.ID)
		}
		out[value.ID] = value
	}
	if err := validatePairs(out); err != nil {
		return nil, err
	}
	if err := validateFixtureManifest(filepath.Join(repo, "loomspan-console", "agent-evals", "fixtures.json"), out); err != nil {
		return nil, err
	}
	return out, nil
}

func validateFixtureManifest(name string, cases map[string]Case) error {
	raw, err := os.ReadFile(name)
	if err != nil {
		return err
	}
	var manifest fixtureManifest
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&manifest); err != nil {
		return err
	}
	if err := requireJSONEOF(decoder); err != nil {
		return err
	}
	if manifest.SchemaVersion != 1 || len(manifest.Fixtures) == 0 {
		return fmt.Errorf("invalid fixture manifest")
	}
	want := map[string]string{}
	for _, value := range cases {
		for _, fixture := range value.Fixtures {
			want[fixture.Path] = fixture.SHA256
		}
	}
	if len(want) != len(manifest.Fixtures) {
		return fmt.Errorf("fixture manifest does not contain the exact referenced set")
	}
	seen := map[string]bool{}
	for _, fixture := range manifest.Fixtures {
		if seen[fixture.Path] || want[fixture.Path] != fixture.SHA256 {
			return fmt.Errorf("fixture manifest entry mismatch")
		}
		seen[fixture.Path] = true
	}
	return nil
}

func ValidateCase(value Case, repo string) error {
	if value.SchemaVersion != CaseSchemaVersion || value.ID == "" || value.PairID == "" || (value.Mode != "tools-only" && value.Mode != "skill-assisted") || strings.TrimSpace(value.DeveloperPrompt) == "" {
		return fmt.Errorf("invalid required case fields")
	}
	for _, values := range [][]string{value.AllowedTools, value.RequiredFacts, value.RequiredLimitations, value.ForbiddenClaims, value.ForbiddenActions} {
		if len(values) == 0 || duplicate(values) {
			return fmt.Errorf("oracle collections must be nonempty and unique")
		}
	}
	allowed := map[string]bool{}
	for _, tool := range CurrentTools {
		allowed[tool] = true
	}
	for _, tool := range value.AllowedTools {
		if !allowed[tool] {
			return fmt.Errorf("unknown tool %q", tool)
		}
	}
	if len(value.Fixtures) == 0 {
		return fmt.Errorf("fixtures are required")
	}
	for _, fixture := range value.Fixtures {
		clean := filepath.Clean(filepath.FromSlash(fixture.Path))
		if filepath.IsAbs(clean) || strings.HasPrefix(filepath.ToSlash(clean), "../") {
			return fmt.Errorf("unsafe fixture path")
		}
		raw, err := os.ReadFile(filepath.Join(repo, clean))
		if err != nil {
			return err
		}
		digest := sha256.Sum256(raw)
		if hex.EncodeToString(digest[:]) != fixture.SHA256 {
			return fmt.Errorf("fixture digest mismatch for %s", fixture.Path)
		}
	}
	serialized, _ := json.Marshal(value)
	for _, retired := range []string{"capabilities\"", "artifactHandle", "targetScopeId", "evidenceOwner"} {
		if bytes.Contains(serialized, []byte(retired)) {
			return fmt.Errorf("retired selector %q", retired)
		}
	}
	return nil
}

func validatePairs(cases map[string]Case) error {
	pairs := map[string]map[string]Case{}
	for _, value := range cases {
		if pairs[value.PairID] == nil {
			pairs[value.PairID] = map[string]Case{}
		}
		if _, found := pairs[value.PairID][value.Mode]; found {
			return fmt.Errorf("duplicate pair mode")
		}
		pairs[value.PairID][value.Mode] = value
	}
	for id, modes := range pairs {
		toolsOnly, hasToolsOnly := modes["tools-only"]
		skillAssisted, hasSkillAssisted := modes["skill-assisted"]
		if !hasToolsOnly || !hasSkillAssisted {
			return fmt.Errorf("pair %q is incomplete", id)
		}
		if !equivalentPairCase(toolsOnly, skillAssisted) {
			return fmt.Errorf("pair %q differs between modes", id)
		}
	}
	return nil
}

func equivalentPairCase(a, b Case) bool {
	return a.PairID == b.PairID &&
		a.DeveloperPrompt == b.DeveloperPrompt &&
		fixtureReferencesEqual(a.Fixtures, b.Fixtures) &&
		sameSet(a.AllowedTools, b.AllowedTools) &&
		sameSet(a.RequiredFacts, b.RequiredFacts) &&
		sameSet(a.RequiredLimitations, b.RequiredLimitations) &&
		sameSet(a.ForbiddenClaims, b.ForbiddenClaims) &&
		sameSet(a.ForbiddenActions, b.ForbiddenActions)
}

func fixtureReferencesEqual(a, b []FixtureReference) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func SortedCaseIDs(cases map[string]Case) []string {
	ids := make([]string, 0, len(cases))
	for id := range cases {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	return ids
}
func duplicate(values []string) bool {
	seen := map[string]bool{}
	for _, value := range values {
		if value == "" || seen[value] {
			return true
		}
		seen[value] = true
	}
	return false
}

func requireJSONEOF(decoder *json.Decoder) error {
	var trailing any
	if err := decoder.Decode(&trailing); err == nil {
		return fmt.Errorf("unexpected trailing JSON value")
	} else if err != io.EOF {
		return err
	}
	return nil
}
