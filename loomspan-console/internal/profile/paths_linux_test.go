//go:build linux

package profile

import (
	"path/filepath"
	"testing"
)

func TestLinuxDefaultsUseAbsoluteXDGRootsWithoutHome(t *testing.T) {
	environment := PathEnvironment{
		XDGConfigHome: "/xdg/config",
		XDGStateHome:  "/xdg/state",
	}
	configPath, err := defaultConfigPath(environment)
	if err != nil {
		t.Fatal(err)
	}
	workspaceParent, err := defaultWorkspaceParent(environment)
	if err != nil {
		t.Fatal(err)
	}
	if configPath != filepath.FromSlash("/xdg/config/loomspan-console/config.yaml") {
		t.Fatalf("config path=%q", configPath)
	}
	if workspaceParent != filepath.FromSlash("/xdg/state/loomspan-console/workspaces") {
		t.Fatalf("workspace parent=%q", workspaceParent)
	}
}

func TestLinuxDefaultsIgnoreRelativeXDGRoots(t *testing.T) {
	environment := PathEnvironment{
		Home:          "/home/developer",
		XDGConfigHome: "relative/config",
		XDGStateHome:  "relative/state",
	}
	configPath, err := defaultConfigPath(environment)
	if err != nil {
		t.Fatal(err)
	}
	workspaceParent, err := defaultWorkspaceParent(environment)
	if err != nil {
		t.Fatal(err)
	}
	if configPath != filepath.FromSlash("/home/developer/.config/loomspan-console/config.yaml") {
		t.Fatalf("config path=%q", configPath)
	}
	if workspaceParent != filepath.FromSlash("/home/developer/.local/state/loomspan-console/workspaces") {
		t.Fatalf("workspace parent=%q", workspaceParent)
	}
}

func TestLinuxDefaultsRejectRelativeHomeFallback(t *testing.T) {
	environment := PathEnvironment{
		Home:          "relative/home",
		XDGConfigHome: "relative/config",
		XDGStateHome:  "relative/state",
	}
	if _, err := defaultConfigPath(environment); err == nil {
		t.Fatal("relative home fallback was accepted for configuration")
	}
	if _, err := defaultWorkspaceParent(environment); err == nil {
		t.Fatal("relative home fallback was accepted for workspace state")
	}
}
