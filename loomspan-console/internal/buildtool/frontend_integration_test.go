//go:build integration

package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestCleanFrontendBuildIsDeterministic(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	version, err := readProductVersion(filepath.Join(paths.repository, "pom.xml"))
	if err != nil {
		t.Fatal(err)
	}
	context := pipelineContext{paths: paths, productVersion: version}
	runner := realRunner{}
	buildAssets := func() []byte {
		t.Helper()
		for _, current := range []phase{phaseCleanAssets, phaseViteBuild, phaseGenerateManifest, phaseVerifyManifest} {
			if err := runner.run(current, context); err != nil {
				t.Fatalf("%s: %v", current, err)
			}
		}
		raw, err := os.ReadFile(filepath.Join(paths.generated, "loomspan-assets.json"))
		if err != nil {
			t.Fatal(err)
		}
		return raw
	}
	first := buildAssets()
	second := buildAssets()
	if string(first) != string(second) {
		t.Fatal("two clean frontend builds produced different asset manifests")
	}
}

func TestTamperedFrontendAssetFailsVerificationBeforeCompilation(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	version, err := readProductVersion(filepath.Join(paths.repository, "pom.xml"))
	if err != nil {
		t.Fatal(err)
	}
	context := pipelineContext{paths: paths, productVersion: version}
	runner := realRunner{}
	buildAssets := func() {
		t.Helper()
		for _, current := range []phase{phaseCleanAssets, phaseViteBuild, phaseGenerateManifest, phaseVerifyManifest} {
			if err := runner.run(current, context); err != nil {
				t.Fatalf("%s: %v", current, err)
			}
		}
	}
	buildAssets()
	t.Cleanup(buildAssets)

	javascript, err := filepath.Glob(filepath.Join(paths.generated, "assets", "*.js"))
	if err != nil {
		t.Fatal(err)
	}
	if len(javascript) == 0 {
		t.Fatal("Vite emitted no JavaScript asset to tamper with")
	}
	file, err := os.OpenFile(javascript[0], os.O_APPEND|os.O_WRONLY, 0)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := file.WriteString("\n// tampered\n"); err != nil {
		_ = file.Close()
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}
	if err := runner.run(phaseVerifyManifest, context); err == nil {
		t.Fatal("asset verification accepted tampered Vite output")
	}
}
