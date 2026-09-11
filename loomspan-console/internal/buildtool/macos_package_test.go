package main

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agentskills"
)

func TestMacOSInfoPlistDeclaresNativeApplication(t *testing.T) {
	contents := macOSInfoPlist("1.2.3-rc.1")
	if err := validateMacOSInfoPlist(contents, "1.2.3-rc.1"); err != nil {
		t.Fatal(err)
	}
	for _, required := range []string{
		"<key>CFBundleShortVersionString</key>\n  <string>1.2.3</string>",
		"<key>CFBundleVersion</key>\n  <string>1.2.3</string>",
		"<key>LoomspanProductVersion</key>\n  <string>1.2.3-rc.1</string>",
		"<key>CFBundlePackageType</key>\n  <string>APPL</string>",
	} {
		if !strings.Contains(string(contents), required) {
			t.Errorf("Info.plist does not contain %q", required)
		}
	}
}

func TestMacOSSigningConfigurationIsOptionalAndFailsClosed(t *testing.T) {
	for _, name := range []string{
		"LOOMSPAN_MACOS_SIGNING_IDENTITY",
		"LOOMSPAN_MACOS_NOTARY_KEY_PATH",
		"LOOMSPAN_MACOS_NOTARY_KEY_ID",
		"LOOMSPAN_MACOS_NOTARY_ISSUER_ID",
	} {
		t.Setenv(name, "")
	}
	configuration, err := macOSSigningConfigurationFromEnvironment()
	if err != nil || configuration.identity != "" || configuration.notarizes() {
		t.Fatalf("empty configuration = %+v, %v", configuration, err)
	}

	t.Setenv("LOOMSPAN_MACOS_NOTARY_KEY_ID", "KEY")
	if _, err := macOSSigningConfigurationFromEnvironment(); err == nil {
		t.Fatal("partial notarization configuration was accepted")
	}

	t.Setenv("LOOMSPAN_MACOS_NOTARY_KEY_PATH", "/tmp/key.p8")
	t.Setenv("LOOMSPAN_MACOS_NOTARY_ISSUER_ID", "issuer")
	if _, err := macOSSigningConfigurationFromEnvironment(); err == nil {
		t.Fatal("notarization without a signing identity was accepted")
	}

	t.Setenv("LOOMSPAN_MACOS_SIGNING_IDENTITY", "Developer ID Application: Loomspan")
	configuration, err = macOSSigningConfigurationFromEnvironment()
	if err != nil || !configuration.notarizes() {
		t.Fatalf("complete configuration = %+v, %v", configuration, err)
	}
}

func TestRemoveBuildOutputRejectsEscapes(t *testing.T) {
	build := t.TempDir()
	inside := filepath.Join(build, "generated")
	if err := os.MkdirAll(inside, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := removeBuildOutput(build, inside); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(inside); !os.IsNotExist(err) {
		t.Fatalf("validated build output still exists: %v", err)
	}

	outside := filepath.Join(filepath.Dir(build), "outside")
	if err := removeBuildOutput(build, outside); err == nil {
		t.Fatal("escaped build output was accepted")
	}
	if err := removeBuildOutput(build, build); err == nil {
		t.Fatal("build root was accepted")
	}
}

func TestMacOSNativeApplicationAndDiskImage(t *testing.T) {
	if runtime.GOOS != "darwin" || runtime.GOARCH != "arm64" {
		t.Skip("requires the supported native macOS ARM64 release host")
	}
	paths, err := resolveProjectPaths()
	if err != nil {
		t.Fatal(err)
	}
	root := t.TempDir()
	paths.build = filepath.Join(root, "build")
	paths.dist = filepath.Join(root, "dist")
	if err := os.MkdirAll(paths.build, 0o755); err != nil {
		t.Fatal(err)
	}
	testExecutable, err := os.Executable()
	if err != nil {
		t.Fatal(err)
	}
	if err := copyRegularFile(testExecutable, filepath.Join(paths.build, "loomspan-console"), 0o755); err != nil {
		t.Fatal(err)
	}
	context := pipelineContext{paths: paths, productVersion: "1.2.3"}
	application, err := assembleMacOSApplication(context, macOSSigningConfiguration{})
	if err != nil {
		t.Fatal(err)
	}
	if err := runCommand(paths.module, nil, "codesign", "--verify", "--deep", "--strict", application); err != nil {
		t.Fatal(err)
	}
	icon, err := os.ReadFile(filepath.Join(application, "Contents", "Resources", macOSIconFilename))
	if err != nil || len(icon) < 4 || string(icon[:4]) != "icns" {
		t.Fatalf("generated icon is not ICNS: %v", err)
	}
	target, _ := supportedReleaseTarget("darwin", "arm64")
	request := packageRequest{
		version: "1.2.3", target: target, executable: filepath.Join(paths.build, "loomspan-console"), macOSApp: application,
		license: filepath.Join(paths.repository, "LICENSE"), readme: filepath.Join(paths.release, "README.md"),
		skill: filepath.Join(paths.agentSkills, agentskills.RuntimeDebuggingSkillName), outputDirectory: paths.dist,
	}
	if _, err := writeReleasePackage(request); err != nil {
		t.Fatal(err)
	}
	dmg, err := createMacOSDiskImage(context, request, macOSSigningConfiguration{})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := commandOutput(paths.module, nil, "hdiutil", "verify", dmg.file); err != nil {
		t.Fatal(err)
	}
}
