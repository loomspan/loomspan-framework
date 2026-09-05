package profile

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

type PathEnvironment struct {
	Home          string
	AppData       string
	LocalAppData  string
	XDGConfigHome string
	XDGStateHome  string
}

func CurrentPathEnvironment() PathEnvironment {
	home, _ := os.UserHomeDir()
	return PathEnvironment{
		Home:          home,
		AppData:       os.Getenv("APPDATA"),
		LocalAppData:  os.Getenv("LOCALAPPDATA"),
		XDGConfigHome: os.Getenv("XDG_CONFIG_HOME"),
		XDGStateHome:  os.Getenv("XDG_STATE_HOME"),
	}
}

func ResolveConfigPath(selected string) (string, error) {
	if selected == "" {
		var err error
		selected, err = defaultConfigPath(CurrentPathEnvironment())
		if err != nil {
			return "", err
		}
	}
	absolute, err := filepath.Abs(selected)
	if err != nil {
		return "", fmt.Errorf("resolve config path: %w", err)
	}
	return filepath.Clean(absolute), nil
}

func ResolveProfileDirectory(configPath string) (string, error) {
	parent := filepath.Dir(configPath)
	_, statErr := os.Lstat(parent)
	created := os.IsNotExist(statErr)
	if statErr != nil && !created {
		return "", fmt.Errorf("inspect profile directory: %w", statErr)
	}
	if err := os.MkdirAll(parent, 0o700); err != nil {
		return "", fmt.Errorf("create profile directory: %w", err)
	}
	if created {
		if err := protectNewDirectory(parent); err != nil {
			return "", fmt.Errorf("protect profile directory: %w", err)
		}
	}
	resolved, err := resolveSafeDirectory(parent)
	if err != nil {
		return "", fmt.Errorf("profile directory is unsafe: %w", err)
	}
	return resolved, nil
}

func DefaultWorkspacePath(profileDirectory string) (string, error) {
	parent, err := defaultWorkspaceParent(CurrentPathEnvironment())
	if err != nil {
		return "", err
	}
	return filepath.Join(parent, WorkspaceLeaf(profileDirectory)), nil
}

func WorkspaceLeaf(profileDirectory string) string {
	normalized := filepath.Clean(profileDirectory)
	if pathCaseInsensitive {
		normalized = strings.ToLower(normalized)
	}
	sum := sha256.Sum256([]byte(normalized))
	return hex.EncodeToString(sum[:])
}
