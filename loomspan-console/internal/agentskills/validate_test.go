package agentskills

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

func TestCanonicalRuntimeDebuggingSkillIsValidAndExact(t *testing.T) {
	root := canonicalSkill(t)
	if err := ValidateRuntimeDebugging(root); err != nil {
		t.Fatal(err)
	}
}

func TestRuntimeDebuggingSkillDoesNotTeachRemovedMCPWorkflow(t *testing.T) {
	root := canonicalSkill(t)
	stale := []string{
		"claim to evidence: target scope",
		"artifact handle, payload reference",
		"payload references",
		"expired evidence",
		"changed scope",
	}
	for _, relative := range RuntimeDebuggingFiles {
		content, err := os.ReadFile(filepath.Join(root, filepath.FromSlash(relative)))
		if err != nil {
			t.Fatal(err)
		}
		lower := strings.ToLower(string(content))
		for _, phrase := range stale {
			if strings.Contains(lower, phrase) {
				t.Fatalf("%s still teaches removed MCP workflow %q", relative, phrase)
			}
		}
	}
}

func TestRuntimeDebuggingSkillMatchesInstalledPR52Contract(t *testing.T) {
	root := canonicalSkill(t)
	all := ""
	for _, relative := range RuntimeDebuggingFiles {
		content, err := os.ReadFile(filepath.Join(root, filepath.FromSlash(relative)))
		if err != nil { t.Fatal(err) }
		all += "\n" + string(content)
	}
	required := []string{
		"thirteen", "LOOMSPAN_get_runtime", "LOOMSPAN_list_skills", "LOOMSPAN_get_skill",
		"LOOMSPAN_list_executions", "LOOMSPAN_get_execution", "LOOMSPAN_get_execution_activity",
		"LOOMSPAN_list_traces", "LOOMSPAN_get_trace", "LOOMSPAN_query_trace_plans",
		"LOOMSPAN_query_trace_frames", "LOOMSPAN_query_trace_records",
		"LOOMSPAN_read_trace_content", "LOOMSPAN_read_trace_artifact", "IMPORTED_DESC",
		"omitted `pageSize` defaults", "same non-null plan and group", "distinct non-null task IDs",
		"omits duration, assignment, usage, identity, and outcome", "deterministic text fallback",
	}
	for _, value := range required {
		if !strings.Contains(all, value) { t.Errorf("canonical skill does not route %q", value) }
	}
	for _, retired := range []string{
		"loomspan.runtime-status.v1", "loomspan.skill-inspection.v1",
		"loomspan.active-execution-inspection.v1", "loomspan.recent-activity-inspection.v1",
		"loomspan.trace-inspection.v1", "loomspan.raw-artifact-inspection.v1",
		"all twelve", "bounded active path", "rich plan landmark", "manual raw-file attachment",
		"producer summary", "activity details", "phase/path", "plan/task/unit/group",
		"detail is a later observation, not a richer shape",
		"narrowed `PLAN_CREATED`/`PLAN_UPDATED` descriptors",
	} {
		if strings.Contains(strings.ToLower(all), strings.ToLower(retired)) {
			t.Errorf("canonical skill still contains retired guidance %q", retired)
		}
	}
}

func TestRuntimeDebuggingSkillValidationRejectsUnsafeAndNonPortableVariants(t *testing.T) {
	tests := []struct {
		name   string
		mutate func(*testing.T, string)
		want   string
	}{
		{"extra file", func(t *testing.T, root string) { write(t, filepath.Join(root, "extra.md"), "x") }, "unexpected"},
		{"missing file", func(t *testing.T, root string) { os.Remove(filepath.Join(root, "references", "runtime-model.md")) }, "incomplete"},
		{"unsupported frontmatter", replaceSkill("license: Apache-2.0", "allowed-tools: []\nlicense: Apache-2.0"), "unsupported"},
		{"wrong name", replaceSkill("name: loomspan", "name: another-skill"), "name"},
		{"unknown metadata", replaceSkill("loomspan-version: \"1.0.0-beta.2-SNAPSHOT\"", "another-version: \"1.0.0-beta.2-SNAPSHOT\""), "metadata"},
		{"blank version metadata", replaceSkill("loomspan-version: \"1.0.0-beta.2-SNAPSHOT\"", "loomspan-version: \"\""), "metadata"},
		{"unresolved version metadata", replaceSkill("loomspan-version: \"1.0.0-beta.2-SNAPSHOT\"", "loomspan-version: \"${project.version}\""), "metadata"},
		{"broken reference", replaceSkill("references/runtime-model.md", "references/missing.md"), "reference"},
		{"endpoint", func(t *testing.T, root string) { appendSkill(t, root, "\nUse https://example.invalid/mcp\n") }, "endpoint"},
		{"access key", func(t *testing.T, root string) {
			appendSkill(t, root, "\nlsmcp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n")
		}, "credential"},
		{"authorization header", func(t *testing.T, root string) { appendSkill(t, root, "\nAuthorization: Bearer value\n") }, "header"},
		{"generated trace", func(t *testing.T, root string) { appendSkill(t, root, "\nfixture.ndjson\n") }, "trace"},
		{"scripts directory", func(t *testing.T, root string) { write(t, filepath.Join(root, "scripts", "run.ps1"), "exit 0") }, "unexpected"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			root := copyCanonical(t)
			test.mutate(t, root)
			err := ValidateRuntimeDebugging(root)
			if err == nil || !strings.Contains(strings.ToLower(err.Error()), strings.ToLower(test.want)) {
				t.Fatalf("error = %v, want containing %q", err, test.want)
			}
		})
	}

	if runtime.GOOS != "windows" {
		t.Run("symlink file", func(t *testing.T) {
			root := copyCanonical(t)
			name := filepath.Join(root, "references", "runtime-model.md")
			if err := os.Remove(name); err != nil {
				t.Fatal(err)
			}
			if err := os.Symlink(filepath.Join(root, "SKILL.md"), name); err != nil {
				t.Fatal(err)
			}
			if err := ValidateRuntimeDebugging(root); err == nil {
				t.Fatal("symlink was accepted")
			}
		})
	}
}

func canonicalSkill(t *testing.T) string {
	t.Helper()
	root, err := filepath.Abs(filepath.Join("..", "..", "agent-skills", RuntimeDebuggingSkillName))
	if err != nil {
		t.Fatal(err)
	}
	return root
}

func copyCanonical(t *testing.T) string {
	t.Helper()
	root := filepath.Join(t.TempDir(), RuntimeDebuggingSkillName)
	for _, relative := range RuntimeDebuggingFiles {
		content, err := os.ReadFile(filepath.Join(canonicalSkill(t), filepath.FromSlash(relative)))
		if err != nil {
			t.Fatal(err)
		}
		write(t, filepath.Join(root, filepath.FromSlash(relative)), string(content))
	}
	return root
}

func write(t *testing.T, name, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(name), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(name, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
}

func replaceSkill(old, replacement string) func(*testing.T, string) {
	return func(t *testing.T, root string) {
		name := filepath.Join(root, "SKILL.md")
		content, err := os.ReadFile(name)
		if err != nil {
			t.Fatal(err)
		}
		updated := strings.Replace(string(content), old, replacement, 1)
		if updated == string(content) {
			t.Fatalf("mutation source %q not found", old)
		}
		write(t, name, updated)
	}
}

func appendSkill(t *testing.T, root, suffix string) {
	t.Helper()
	name := filepath.Join(root, "SKILL.md")
	content, err := os.ReadFile(name)
	if err != nil {
		t.Fatal(err)
	}
	write(t, name, string(content)+suffix)
}
