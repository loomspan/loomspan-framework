//go:build windows

package profile

import (
	"path/filepath"
	"testing"
)

func TestDefaultConfigAndStateRootsFollowWindowsContract(t *testing.T) {
	environment := PathEnvironment{
		AppData:      `C:\Users\developer\AppData\Roaming`,
		LocalAppData: `C:\Users\developer\AppData\Local`,
	}
	configPath, err := defaultConfigPath(environment)
	if err != nil {
		t.Fatal(err)
	}
	workspaceParent, err := defaultWorkspaceParent(environment)
	if err != nil {
		t.Fatal(err)
	}
	if configPath != filepath.Join(environment.AppData, "loomspan", "Console", "config.yaml") {
		t.Fatalf("config path=%q", configPath)
	}
	if workspaceParent != filepath.Join(environment.LocalAppData, "loomspan", "Console", "workspaces") {
		t.Fatalf("workspace parent=%q", workspaceParent)
	}
}

func TestWindowsDefaultsRejectRelativePlatformRoots(t *testing.T) {
	if _, err := defaultConfigPath(PathEnvironment{AppData: `relative\roaming`}); err == nil {
		t.Fatal("relative APPDATA was accepted")
	}
	if _, err := defaultWorkspaceParent(PathEnvironment{LocalAppData: `relative\local`}); err == nil {
		t.Fatal("relative LOCALAPPDATA was accepted")
	}
}
