package main

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

func TestProjectDeclarationsMatchPinnedToolchains(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	nodeVersion, err := os.ReadFile(filepath.Join(paths.module, ".node-version"))
	if err != nil {
		t.Fatal(err)
	}
	if strings.TrimSpace(string(nodeVersion)) != requiredNode {
		t.Fatalf(".node-version = %q", nodeVersion)
	}
	goModule, err := os.ReadFile(filepath.Join(paths.module, "go.mod"))
	if err != nil {
		t.Fatal(err)
	}
	if !regexp.MustCompile(`(?m)^go 1\.26\.0$`).Match(goModule) ||
		!regexp.MustCompile(`(?m)^toolchain go1\.26\.5$`).Match(goModule) {
		t.Fatalf("go.mod does not declare the pinned toolchain:\n%s", goModule)
	}
}

func TestMCPDependenciesAndSDKBoundaryArePinned(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	goModule := readTestFile(t, filepath.Join(paths.module, "go.mod"))
	if !regexp.MustCompile(`(?m)^\s*github\.com/modelcontextprotocol/go-sdk v1\.7\.0$`).MatchString(goModule) {
		t.Fatal("go.mod does not pin the official MCP Go SDK at v1.7.0")
	}
	manifest := readTestFile(t, filepath.Join(paths.module, "mcp-conformance", "package.json"))
	lock := readTestFile(t, filepath.Join(paths.module, "mcp-conformance", "package-lock.json"))
	const conformanceRevision = "c321dd32035556e6769d3724a8ee97d87c3faaac"
	if !strings.Contains(manifest, conformanceRevision) || !strings.Contains(lock, conformanceRevision) {
		t.Fatal("MCP conformance manifest and lockfile must pin the reviewed revision")
	}
	err = filepath.WalkDir(paths.module, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() && path != paths.module {
			switch entry.Name() {
			case ".git", "build", "dist", "node_modules":
				return fs.SkipDir
			}
		}
		if entry.IsDir() || filepath.Ext(path) != ".go" || strings.Contains(filepath.ToSlash(path), "/internal/mcpadapter/") || filepath.Base(path) == "projectdeclarations_test.go" {
			return nil
		}
		contents, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		if strings.Contains(string(contents), "github.com/modelcontextprotocol/go-sdk") {
			t.Errorf("official MCP SDK import escaped internal/mcpadapter: %s", path)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}

func TestOfficialAgentSkillValidatorIsPinnedAndRequired(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	const revision = "69ef37e9424c0a7ea9dd2293b559e43ec8176379"
	manifest := readTestFile(t, filepath.Join(paths.module, "skills-ref-validation", "pyproject.toml"))
	lock := readTestFile(t, filepath.Join(paths.module, "skills-ref-validation", "uv.lock"))
	if !strings.Contains(manifest, revision) || !strings.Contains(lock, revision) || !strings.Contains(lock, "skills-ref") {
		t.Fatal("official skills-ref validator must be locked to the reviewed revision")
	}
	for _, workflow := range []string{"console-ci.yml", "console-release.yml"} {
		contents := readTestFile(t, filepath.Join(paths.repository, ".github", "workflows", workflow))
		for _, required := range []string{
			"uv==0.11.7",
			"uv run --frozen --project skills-ref-validation skills-ref validate ./agent-skills/loomspan",
			"skills-ref validate ../agent-skills/loomspan-docs",
			"skills-ref validate ../agent-skills/bootstrap",
			"python ../scripts/loomspan_version.py check",
			"python -m unittest discover ../scripts/tests",
		} {
			if !strings.Contains(contents, required) {
				t.Errorf("%s does not require pinned Agent Skill validation %q", workflow, required)
			}
		}
	}
}

func TestReleaseLicenseAndRuntimeDocumentExist(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	license, err := os.ReadFile(filepath.Join(paths.repository, "LICENSE"))
	if err != nil {
		t.Fatal(err)
	}
	if digest := fmt.Sprintf("%x", sha256.Sum256(license)); digest != "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30" {
		t.Fatalf("LICENSE is not the canonical Apache License 2.0 text: %s", digest)
	}
	readme, err := os.ReadFile(filepath.Join(paths.release, "README.md"))
	if err != nil {
		t.Fatal(err)
	}
	for _, required := range []string{"--version", "--no-open-browser", "SHA256SUMS", "no JVM", "Target keys"} {
		if !strings.Contains(string(readme), required) {
			t.Errorf("release README does not contain %q", required)
		}
	}
}

func TestReleaseAndAuthoringDocumentationReferenceCanonicalSkillContract(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	documents := map[string]string{
		"Console README": readTestFile(t, filepath.Join(paths.module, "README.md")),
		"release README": readTestFile(t, filepath.Join(paths.release, "README.md")),
	}
	for name, contents := range documents {
		for _, required := range []string{"skills/loomspan/", "copy", "link", "unversioned", "MCP"} {
			if !strings.Contains(contents, required) {
				t.Errorf("%s does not contain %q", name, required)
			}
		}
	}
	contractVerification := readTestFile(t, filepath.Join(paths.module, "docs", "mcp-contract-verification.md"))
	for _, required := range []string{"MCP contract verification", "tools/list", "official conformance", "DNS-rebinding"} {
		if !strings.Contains(contractVerification, required) {
			t.Errorf("MCP contract verification does not contain %q", required)
		}
	}
	authoringRoot := filepath.Join(paths.repository, "agent-skills", "loomspan-docs", "references", "skill-authoring")
	authoringREADME := readTestFile(t, filepath.Join(authoringRoot, "README.md"))
	authoringTopic := readTestFile(t, filepath.Join(authoringRoot, "traces-and-debugging.md"))
	for _, required := range []string{"traces-and-debugging.md", "packaged Agent Skill"} {
		if !strings.Contains(authoringREADME, required) {
			t.Errorf("authoring README does not contain %q", required)
		}
	}
	for _, required := range []string{"loomspan", "thirteen", "LOOMSPAN_query_trace_plans", "tools/list", "defense in depth", "Omitted `pageSize` defaults to 64", "omits duration, assignment, usage, identity detail, and outcome"} {
		if !strings.Contains(authoringTopic, required) {
			t.Errorf("authoring debugging topic does not contain %q", required)
		}
	}
	for name, contents := range map[string]string{
		"Console README": documents["Console README"], "release README": documents["release README"],
		"contract verification": contractVerification, "authoring topic": authoringTopic,
	} {
		for _, retired := range []string{"loomspan.runtime-status.v1", "loomspan.trace-inspection.v1", "all twelve", "MCP plan presentation remains deferred", "bounded active path", "capability manifest fixtures", "narrowed `PLAN_CREATED`/`PLAN_UPDATED` descriptor"} {
			if strings.Contains(contents, retired) {
				t.Errorf("%s contains retired MCP guidance %q", name, retired)
			}
		}
	}
}

func TestLoomspanDocsSkillIsSelfContained(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(filepath.Join(paths.repository, "ai", "skill-authoring")); !os.IsNotExist(err) {
		t.Fatalf("legacy ai/skill-authoring directory still exists or cannot be inspected: %v", err)
	}

	skillRoot := filepath.Join(paths.repository, "agent-skills", "loomspan-docs")
	skillDefinition := readTestFile(t, filepath.Join(skillRoot, "SKILL.md"))
	javaAPIIndex := readTestFile(t, filepath.Join(skillRoot, "references", "java-api", "README.md"))
	for _, required := range []string{"`java-api`", "references/java-api/README.md", "compatibility-and-boundaries.md"} {
		if !strings.Contains(skillDefinition, required) {
			t.Errorf("loomspan-docs SKILL.md does not route %q", required)
		}
	}
	for _, required := range []string{
		"SkillTemplate", "SkillExecutionView", "SkillExecutionEvent", "SkillMethod",
		"SkillParam", "SkillException", "SkillInputValidationException", "SkillInputValidationIssue",
		"LoomspanPublicSurfaceArchitectureTest", "no supported Java SPI",
	} {
		if !strings.Contains(javaAPIIndex, required) {
			t.Errorf("java-api README does not contain %q", required)
		}
	}
	linkPattern := regexp.MustCompile(`\[[^\]]+\]\(([^)]+)\)`)
	err = filepath.WalkDir(skillRoot, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() || filepath.Ext(path) != ".md" {
			return nil
		}
		contents, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		for _, match := range linkPattern.FindAllStringSubmatch(string(contents), -1) {
			target := strings.SplitN(match[1], "#", 2)[0]
			if target == "" || strings.HasPrefix(target, "#") || strings.HasPrefix(target, "http://") || strings.HasPrefix(target, "https://") || strings.HasPrefix(target, "mailto:") {
				continue
			}
			resolved := filepath.Clean(filepath.Join(filepath.Dir(path), filepath.FromSlash(target)))
			relative, err := filepath.Rel(skillRoot, resolved)
			if err != nil {
				return err
			}
			if relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
				return fmt.Errorf("%s links outside the installable skill: %s", path, match[1])
			}
			if _, err := os.Stat(resolved); err != nil {
				return fmt.Errorf("%s has unresolved local link %s: %w", path, match[1], err)
			}
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}

func TestConsoleWorkflowsArePinnedAndLeastPrivilege(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	workflowDirectory := filepath.Join(paths.repository, ".github", "workflows")
	ci := readTestFile(t, filepath.Join(workflowDirectory, "console-ci.yml"))
	releaseWorkflow := readTestFile(t, filepath.Join(workflowDirectory, "console-release.yml"))
	for name, contents := range map[string]string{"console-ci.yml": ci, "console-release.yml": releaseWorkflow} {
		if strings.Contains(contents, "pull_request_target") {
			t.Fatalf("%s executes pull_request_target", name)
		}
		for _, skill := range []string{"../agent-skills/loomspan-docs", "../agent-skills/bootstrap"} {
			if !strings.Contains(contents, skill) {
				t.Errorf("%s does not validate the distributable skill %s", name, skill)
			}
		}
		for _, line := range strings.Split(contents, "\n") {
			if strings.Contains(line, "uses:") && !regexp.MustCompile(`uses: [^@]+@[0-9a-f]{40} # v[0-9]+$`).MatchString(strings.TrimSpace(line)) {
				t.Errorf("%s has an unpinned action: %s", name, line)
			}
		}
	}
	for _, required := range []string{"pull_request:", "contents: read", "go-version: 1.26.5", "node-version: 24.18.0", "npm@12.0.2", "-f ../pom.xml help:evaluate", "go run ./internal/buildtool verify", "npm --prefix web run test:e2e", "go run ./internal/buildtool mcp-conformance", "windows-x86_64", "linux-x86_64", "macos-arm64", "macos-x86_64", "macos-15-intel"} {
		if !strings.Contains(ci, required) {
			t.Errorf("CI workflow does not contain %q", required)
		}
	}
	for _, required := range []string{"windows-latest", "ubuntu-latest", "macos-15", "windows-x86_64", "linux-x86_64", "macos-arm64", "workflow_dispatch:", "tags: [\"v*\"]", "SHA256SUMS"} {
		if !strings.Contains(releaseWorkflow, required) {
			t.Errorf("release workflow does not contain %q", required)
		}
	}
	if count := strings.Count(releaseWorkflow, "contents: write"); count != 1 {
		t.Fatalf("release workflow contains %d write grants, want exactly one", count)
	}
}

func readTestFile(t *testing.T, filename string) string {
	t.Helper()
	contents, err := os.ReadFile(filename)
	if err != nil {
		t.Fatal(err)
	}
	return string(contents)
}

func TestPackageManifestUsesExactDirectVersions(t *testing.T) {
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	raw, err := os.ReadFile(filepath.Join(paths.web, "package.json"))
	if err != nil {
		t.Fatal(err)
	}
	var manifest struct {
		Private         bool              `json:"private"`
		PackageManager  string            `json:"packageManager"`
		Engines         map[string]string `json:"engines"`
		Dependencies    map[string]string `json:"dependencies"`
		DevDependencies map[string]string `json:"devDependencies"`
	}
	if err := json.Unmarshal(raw, &manifest); err != nil {
		t.Fatal(err)
	}
	if !manifest.Private || manifest.PackageManager != "npm@"+requiredNPM ||
		manifest.Engines["node"] != requiredNode || manifest.Engines["npm"] != requiredNPM {
		t.Fatalf("package metadata does not match pinned tools: %+v", manifest)
	}
	expected := map[string]string{
		"@axe-core/playwright": "4.12.1",
		"@tailwindcss/vite":    "4.3.3", "react": "19.2.8", "react-aria-components": "1.19.0",
		"react-dom": "19.2.8", "react-router": "8.3.0", "tailwindcss": "4.3.3",
		"@playwright/test": "1.62.0", "@testing-library/dom": "10.4.1",
		"@testing-library/jest-dom": "7.0.0", "@testing-library/react": "16.3.2",
		"@testing-library/user-event": "14.6.1", "@types/react": "19.2.17",
		"@types/react-dom": "19.2.3", "@vitejs/plugin-react": "6.0.4",
		"@vitest/coverage-v8": "4.1.10", "jsdom": "29.1.1", "typescript": "7.0.2",
		"vite": "8.1.5", "vitest": "4.1.10",
	}
	actual := make(map[string]string)
	for name, version := range manifest.Dependencies {
		actual[name] = version
	}
	for name, version := range manifest.DevDependencies {
		actual[name] = version
	}
	if len(actual) != len(expected) {
		t.Fatalf("direct dependency count = %d, want %d", len(actual), len(expected))
	}
	for name, version := range expected {
		if actual[name] != version {
			t.Errorf("%s = %q, want %q", name, actual[name], version)
		}
	}
}
