//go:build windows

package profile

import (
	"fmt"
	"path/filepath"
)

const pathCaseInsensitive = true

func defaultConfigPath(environment PathEnvironment) (string, error) {
	if !filepath.IsAbs(environment.AppData) {
		return "", fmt.Errorf("APPDATA must be an absolute path")
	}
	return filepath.Join(environment.AppData, "loomspan", "Console", "config.yaml"), nil
}

func defaultWorkspaceParent(environment PathEnvironment) (string, error) {
	if !filepath.IsAbs(environment.LocalAppData) {
		return "", fmt.Errorf("LOCALAPPDATA must be an absolute path")
	}
	return filepath.Join(environment.LocalAppData, "loomspan", "Console", "workspaces"), nil
}
