package main

import (
	"bytes"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agenteval"
)

func TestAgentEvalVerificationRejectsStaleSummary(t *testing.T) {
	destination, cases := historicalReplayFixture(t)
	if err := os.WriteFile(filepath.Join(destination, "summary.md"), []byte("stale\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := verifyAgentEvalResults(destination, cases, true); err == nil || !strings.Contains(err.Error(), "summary.md is stale") {
		t.Fatalf("stale summary error=%v", err)
	}
}

func TestHistoricalReplayKeepsCurrentDigestGateAndRejectsChangedEvidence(t *testing.T) {
	directory, cases := historicalReplayFixture(t)
	if err := verifyAgentEvalResults(directory, cases, true); err != nil {
		t.Fatal(err)
	}
	if err := verifyAgentEvalResults(directory, cases, false); err == nil || !strings.Contains(err.Error(), "skill digest") {
		t.Fatalf("historical package accepted as current: %v", err)
	}
	recordPath := filepath.Join(directory, "bounded-tools-only.json")
	raw, err := os.ReadFile(recordPath)
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(recordPath, bytes.ReplaceAll(raw, []byte(`"deterministic-replay"`), []byte(`"headless"`)), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := verifyAgentEvalResults(directory, cases, true); err == nil || !strings.Contains(err.Error(), "deterministic-replay") {
		t.Fatalf("native evidence accepted as historical replay: %v", err)
	}
	if err := os.WriteFile(recordPath, raw, 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "skill-package", "SKILL.md"), []byte("changed instructions\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := verifyAgentEvalResults(directory, cases, true); err == nil || !strings.Contains(err.Error(), "skill digest") {
		t.Fatalf("changed archived package accepted: %v", err)
	}
}

func historicalReplayFixture(t *testing.T) (string, map[string]agenteval.Case) {
	t.Helper()
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
	err = filepath.WalkDir(source, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		relative, err := filepath.Rel(source, path)
		if err != nil {
			return err
		}
		target := filepath.Join(destination, relative)
		if entry.IsDir() {
			return os.MkdirAll(target, 0o700)
		}
		raw, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		return os.WriteFile(target, raw, 0o600)
	})
	if err != nil {
		t.Fatal(err)
	}
	return destination, cases
}
