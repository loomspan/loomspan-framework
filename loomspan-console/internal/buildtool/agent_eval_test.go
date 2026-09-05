package main

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agenteval"
)

func TestAgentEvalVerificationRejectsStaleSummary(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	cases, err := agenteval.LoadCases()
	if err != nil {
		t.Fatal(err)
	}
	source := filepath.Join(paths.agentEvals, "results", "2026-09-05")
	destination := t.TempDir()
	entries, err := os.ReadDir(source)
	if err != nil {
		t.Fatal(err)
	}
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		raw, err := os.ReadFile(filepath.Join(source, entry.Name()))
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(destination, entry.Name()), raw, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.WriteFile(filepath.Join(destination, "summary.md"), []byte("stale\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := verifyAgentEvalResults(destination, cases); err == nil || !strings.Contains(err.Error(), "summary.md is stale") {
		t.Fatalf("stale summary error=%v", err)
	}
}
