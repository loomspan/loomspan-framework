package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestCleanGeneratedAssetsRemovesOnlyValidatedChildren(t *testing.T) {
	module := t.TempDir()
	assetRoot := filepath.Join(module, "internal", "webassets")
	generated := filepath.Join(assetRoot, "generated")
	if err := os.MkdirAll(generated, 0o755); err != nil {
		t.Fatal(err)
	}
	stale := filepath.Join(generated, "stale.js")
	outside := filepath.Join(module, "outside.txt")
	if err := os.WriteFile(stale, []byte("stale"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(outside, []byte("keep"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := cleanGeneratedAssets(projectPaths{module: module, generated: generated}); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(stale); !os.IsNotExist(err) {
		t.Fatalf("stale file still exists: %v", err)
	}
	if _, err := os.Stat(outside); err != nil {
		t.Fatalf("outside sentinel was changed: %v", err)
	}
	if _, err := os.Stat(filepath.Join(generated, "embed-placeholder.txt")); err != nil {
		t.Fatalf("placeholder was not recreated: %v", err)
	}
}

func TestCleanGeneratedAssetsRejectsEscapes(t *testing.T) {
	module := t.TempDir()
	assetRoot := filepath.Join(module, "internal", "webassets")
	if err := os.MkdirAll(assetRoot, 0o755); err != nil {
		t.Fatal(err)
	}
	outside := filepath.Join(module, "generated")
	if err := os.MkdirAll(outside, 0o755); err != nil {
		t.Fatal(err)
	}
	sentinel := filepath.Join(outside, "sentinel.txt")
	if err := os.WriteFile(sentinel, []byte("keep"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := cleanGeneratedAssets(projectPaths{module: module, generated: outside}); err == nil {
		t.Fatal("cleanGeneratedAssets() accepted an escaped directory")
	}
	if _, err := os.Stat(sentinel); err != nil {
		t.Fatalf("outside sentinel was changed: %v", err)
	}
}

func TestCleanGeneratedAssetsRejectsSymlinkOrReparseBoundary(t *testing.T) {
	module := t.TempDir()
	assetRoot := filepath.Join(module, "internal", "webassets")
	outside := filepath.Join(module, "outside")
	if err := os.MkdirAll(assetRoot, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.MkdirAll(outside, 0o755); err != nil {
		t.Fatal(err)
	}
	generated := filepath.Join(assetRoot, "generated")
	if err := os.Symlink(outside, generated); err != nil {
		t.Skipf("symlink creation is unavailable: %v", err)
	}
	if err := cleanGeneratedAssets(projectPaths{module: module, generated: generated}); err == nil {
		t.Fatal("cleanGeneratedAssets() accepted a symlink boundary")
	}
}
