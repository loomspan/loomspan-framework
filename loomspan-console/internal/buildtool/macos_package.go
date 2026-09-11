package main

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agentskills"
)

const (
	macOSApplicationName = "Loomspan Console.app"
	macOSBundleID        = "ai.loomspan.console"
	macOSIconFilename    = "Loomspan.icns"
)

type macOSSigningConfiguration struct {
	identity, notaryKeyPath, notaryKeyID, notaryIssuerID string
}

func macOSSigningConfigurationFromEnvironment() (macOSSigningConfiguration, error) {
	configuration := macOSSigningConfiguration{
		identity:       strings.TrimSpace(os.Getenv("LOOMSPAN_MACOS_SIGNING_IDENTITY")),
		notaryKeyPath:  strings.TrimSpace(os.Getenv("LOOMSPAN_MACOS_NOTARY_KEY_PATH")),
		notaryKeyID:    strings.TrimSpace(os.Getenv("LOOMSPAN_MACOS_NOTARY_KEY_ID")),
		notaryIssuerID: strings.TrimSpace(os.Getenv("LOOMSPAN_MACOS_NOTARY_ISSUER_ID")),
	}
	notaryValues := 0
	for _, value := range []string{configuration.notaryKeyPath, configuration.notaryKeyID, configuration.notaryIssuerID} {
		if value != "" {
			notaryValues++
		}
	}
	if notaryValues != 0 && notaryValues != 3 {
		return macOSSigningConfiguration{}, fmt.Errorf("macOS notarization requires key path, key ID, and issuer ID together")
	}
	if notaryValues == 3 && configuration.identity == "" {
		return macOSSigningConfiguration{}, fmt.Errorf("macOS notarization requires a Developer ID signing identity")
	}
	return configuration, nil
}

func (configuration macOSSigningConfiguration) notarizes() bool {
	return configuration.notaryKeyPath != ""
}

func assembleMacOSApplication(context pipelineContext, signing macOSSigningConfiguration) (string, error) {
	application := filepath.Join(context.paths.build, macOSApplicationName)
	iconset := filepath.Join(context.paths.build, "Loomspan.iconset")
	icon := filepath.Join(context.paths.build, macOSIconFilename)
	for _, target := range []string{application, iconset, icon} {
		if err := removeBuildOutput(context.paths.build, target); err != nil {
			return "", err
		}
	}
	if err := os.MkdirAll(iconset, 0o755); err != nil {
		return "", err
	}
	sourceIcon := filepath.Join(context.paths.release, "icon.png")
	sizes := []struct {
		filename string
		pixels   int
	}{
		{"icon_16x16.png", 16}, {"icon_16x16@2x.png", 32},
		{"icon_32x32.png", 32}, {"icon_32x32@2x.png", 64},
		{"icon_128x128.png", 128}, {"icon_128x128@2x.png", 256},
		{"icon_256x256.png", 256}, {"icon_256x256@2x.png", 512},
		{"icon_512x512.png", 512}, {"icon_512x512@2x.png", 1024},
	}
	for _, size := range sizes {
		pixels := fmt.Sprintf("%d", size.pixels)
		if err := runCommand(context.paths.module, nil, "sips", "-z", pixels, pixels, sourceIcon, "--out", filepath.Join(iconset, size.filename)); err != nil {
			return "", fmt.Errorf("generate macOS icon %s: %w", size.filename, err)
		}
	}
	if err := runCommand(context.paths.module, nil, "iconutil", "-c", "icns", iconset, "-o", icon); err != nil {
		return "", fmt.Errorf("compile macOS icon: %w", err)
	}
	if err := removeBuildOutput(context.paths.build, iconset); err != nil {
		return "", err
	}
	macOSDirectory := filepath.Join(application, "Contents", "MacOS")
	resourcesDirectory := filepath.Join(application, "Contents", "Resources")
	if err := os.MkdirAll(macOSDirectory, 0o755); err != nil {
		return "", err
	}
	if err := os.MkdirAll(resourcesDirectory, 0o755); err != nil {
		return "", err
	}
	if err := copyRegularFile(filepath.Join(context.paths.build, executableName()), filepath.Join(macOSDirectory, "loomspan-console"), 0o755); err != nil {
		return "", err
	}
	if err := copyRegularFile(icon, filepath.Join(resourcesDirectory, macOSIconFilename), 0o644); err != nil {
		return "", err
	}
	if err := os.WriteFile(filepath.Join(application, "Contents", "Info.plist"), macOSInfoPlist(context.productVersion), 0o644); err != nil {
		return "", err
	}
	identity := signing.identity
	timestamp := "--timestamp"
	if identity == "" {
		identity = "-"
		timestamp = "--timestamp=none"
	}
	if err := runCommand(context.paths.module, nil, "codesign", "--force", "--options", "runtime", timestamp, "--sign", identity, application); err != nil {
		return "", fmt.Errorf("sign macOS application: %w", err)
	}
	if signing.notarizes() {
		if err := notarizeMacOSApplication(context.paths.build, application, signing); err != nil {
			return "", err
		}
	}
	return application, nil
}

func macOSInfoPlist(version string) []byte {
	bundleVersion := strings.SplitN(version, "-", 2)[0]
	return []byte(fmt.Sprintf(`<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "https://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDevelopmentRegion</key>
  <string>en</string>
  <key>CFBundleDisplayName</key>
  <string>Loomspan Console</string>
  <key>CFBundleExecutable</key>
  <string>loomspan-console</string>
  <key>CFBundleIconFile</key>
  <string>%s</string>
  <key>CFBundleIdentifier</key>
  <string>%s</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>Loomspan Console</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>%s</string>
  <key>CFBundleVersion</key>
  <string>%s</string>
  <key>LoomspanProductVersion</key>
  <string>%s</string>
  <key>NSHighResolutionCapable</key>
  <true/>
</dict>
</plist>
`, macOSIconFilename, macOSBundleID, bundleVersion, bundleVersion, version))
}

func macOSApplicationPackageFiles(application string) []packageFile {
	files := []packageFile{
		{name: filepath.ToSlash(filepath.Join(macOSApplicationName, "Contents", "Info.plist")), source: filepath.Join(application, "Contents", "Info.plist"), mode: 0o644},
		{name: filepath.ToSlash(filepath.Join(macOSApplicationName, "Contents", "MacOS", "loomspan-console")), source: filepath.Join(application, "Contents", "MacOS", "loomspan-console"), mode: 0o755},
		{name: filepath.ToSlash(filepath.Join(macOSApplicationName, "Contents", "Resources", macOSIconFilename)), source: filepath.Join(application, "Contents", "Resources", macOSIconFilename), mode: 0o644},
		{name: filepath.ToSlash(filepath.Join(macOSApplicationName, "Contents", "_CodeSignature", "CodeResources")), source: filepath.Join(application, "Contents", "_CodeSignature", "CodeResources"), mode: 0o644},
	}
	ticket := filepath.Join(application, "Contents", "CodeResources")
	if info, err := os.Lstat(ticket); err == nil && info.Mode().IsRegular() && info.Mode()&os.ModeSymlink == 0 {
		files = append(files, packageFile{name: filepath.ToSlash(filepath.Join(macOSApplicationName, "Contents", "CodeResources")), source: ticket, mode: 0o644})
	}
	return files
}

func createMacOSDiskImage(context pipelineContext, request packageRequest, signing macOSSigningConfiguration) (packageArtifact, error) {
	stage, err := os.MkdirTemp(context.paths.build, "loomspan-dmg-")
	if err != nil {
		return packageArtifact{}, err
	}
	defer os.RemoveAll(stage)
	if err := copyMacOSApplication(request.macOSApp, filepath.Join(stage, macOSApplicationName)); err != nil {
		return packageArtifact{}, err
	}
	for _, file := range []struct{ source, destination string }{
		{request.license, filepath.Join(stage, "LICENSE")},
		{request.readme, filepath.Join(stage, "README.md")},
	} {
		if err := copyRegularFile(file.source, file.destination, 0o644); err != nil {
			return packageArtifact{}, err
		}
	}
	for _, relative := range agentskills.RuntimeDebuggingFiles {
		if err := copyRegularFile(filepath.Join(request.skill, filepath.FromSlash(relative)), filepath.Join(stage, "skills", agentskills.RuntimeDebuggingSkillName, filepath.FromSlash(relative)), 0o644); err != nil {
			return packageArtifact{}, err
		}
	}
	if err := os.Symlink("/Applications", filepath.Join(stage, "Applications")); err != nil {
		return packageArtifact{}, fmt.Errorf("create Applications shortcut: %w", err)
	}
	filename := filepath.Join(context.paths.dist, "loomspan-console-"+context.productVersion+"-macos-arm64.dmg")
	if err := os.MkdirAll(context.paths.dist, 0o755); err != nil {
		return packageArtifact{}, err
	}
	if err := os.Remove(filename); err != nil && !os.IsNotExist(err) {
		return packageArtifact{}, err
	}
	if err := runCommand(context.paths.module, nil, "hdiutil", "create", "-volname", "Loomspan Console", "-srcfolder", stage, "-ov", "-format", "UDZO", filename); err != nil {
		return packageArtifact{}, fmt.Errorf("create macOS disk image: %w", err)
	}
	if signing.identity != "" {
		if err := runCommand(context.paths.module, nil, "codesign", "--force", "--timestamp", "--sign", signing.identity, filename); err != nil {
			return packageArtifact{}, fmt.Errorf("sign macOS disk image: %w", err)
		}
	}
	if signing.notarizes() {
		if err := submitAndStaple(filename, signing); err != nil {
			return packageArtifact{}, err
		}
	}
	contents, err := os.ReadFile(filename)
	if err != nil {
		return packageArtifact{}, err
	}
	return writeChecksumSidecar(filename, contents)
}

func submitAndStaple(filename string, signing macOSSigningConfiguration) error {
	if err := submitNotarization(filename, signing); err != nil {
		return err
	}
	if err := runCommand(filepath.Dir(filename), nil, "xcrun", "stapler", "staple", filename); err != nil {
		return fmt.Errorf("staple %s: %w", filepath.Base(filename), err)
	}
	return nil
}

func notarizeMacOSApplication(buildDirectory, application string, signing macOSSigningConfiguration) error {
	archive := filepath.Join(buildDirectory, "Loomspan-Console-notarization.zip")
	if err := os.Remove(archive); err != nil && !os.IsNotExist(err) {
		return err
	}
	defer os.Remove(archive)
	if err := runCommand(buildDirectory, nil, "ditto", "-c", "-k", "--keepParent", application, archive); err != nil {
		return fmt.Errorf("prepare macOS application for notarization: %w", err)
	}
	if err := submitNotarization(archive, signing); err != nil {
		return err
	}
	if err := runCommand(buildDirectory, nil, "xcrun", "stapler", "staple", application); err != nil {
		return fmt.Errorf("staple macOS application: %w", err)
	}
	return nil
}

func submitNotarization(filename string, signing macOSSigningConfiguration) error {
	if err := runCommand(filepath.Dir(filename), nil, "xcrun", "notarytool", "submit", filename,
		"--key", signing.notaryKeyPath, "--key-id", signing.notaryKeyID, "--issuer", signing.notaryIssuerID, "--wait"); err != nil {
		return fmt.Errorf("notarize %s: %w", filepath.Base(filename), err)
	}
	return nil
}

func copyMacOSApplication(source, destination string) error {
	for _, file := range macOSApplicationPackageFiles(source) {
		relative := strings.TrimPrefix(file.name, macOSApplicationName+"/")
		if err := copyRegularFile(file.source, filepath.Join(destination, filepath.FromSlash(relative)), file.mode); err != nil {
			return err
		}
	}
	return nil
}

func copyRegularFile(source, destination string, mode os.FileMode) error {
	info, err := os.Lstat(source)
	if err != nil {
		return err
	}
	if !info.Mode().IsRegular() || info.Mode()&os.ModeSymlink != 0 {
		return fmt.Errorf("copy source %s must be a regular non-symlink file", source)
	}
	contents, err := os.ReadFile(source)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0o755); err != nil {
		return err
	}
	return os.WriteFile(destination, contents, mode)
}

func removeBuildOutput(buildDirectory, target string) error {
	relative, err := filepath.Rel(buildDirectory, target)
	if err != nil || relative == "." || filepath.IsAbs(relative) || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return fmt.Errorf("refusing to remove build output outside %s", buildDirectory)
	}
	return os.RemoveAll(target)
}

func validateMacOSInfoPlist(contents []byte, version string) error {
	for _, required := range [][]byte{[]byte(macOSBundleID), []byte(macOSIconFilename), []byte("loomspan-console"), []byte(version)} {
		if !bytes.Contains(contents, required) {
			return fmt.Errorf("macOS Info.plist does not contain %q", required)
		}
	}
	return nil
}
