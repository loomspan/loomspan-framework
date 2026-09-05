//go:build darwin

package profile

import (
	"path/filepath"
	"testing"
)

func TestDarwinDefaultsUseAbsoluteHomeRoot(t *testing.T) {
	environment := PathEnvironment{Home: "/Users/developer"}
	configPath, err := defaultConfigPath(environment)
	if err != nil {
		t.Fatal(err)
	}
	workspaceParent, err := defaultWorkspaceParent(environment)
	if err != nil {
		t.Fatal(err)
	}
	if configPath != filepath.FromSlash("/Users/developer/Library/Application Support/loomspan Console/config.yaml") {
		t.Fatalf("config path=%q", configPath)
	}
	if workspaceParent != filepath.FromSlash("/Users/developer/Library/Caches/loomspan Console/workspaces") {
		t.Fatalf("workspace parent=%q", workspaceParent)
	}
}

func TestDarwinDefaultsRejectRelativeHomeRoot(t *testing.T) {
	environment := PathEnvironment{Home: "relative/home"}
	if _, err := defaultConfigPath(environment); err == nil {
		t.Fatal("relative home was accepted for configuration")
	}
	if _, err := defaultWorkspaceParent(environment); err == nil {
		t.Fatal("relative home was accepted for workspace state")
	}
}
