//go:build !windows

package profile

import (
	"fmt"
	"path/filepath"
	"runtime"
)

const pathCaseInsensitive = false

func defaultConfigPath(environment PathEnvironment) (string, error) {
	if runtime.GOOS == "darwin" {
		if !filepath.IsAbs(environment.Home) {
			return "", fmt.Errorf("home directory must be an absolute path")
		}
		return filepath.Join(environment.Home, "Library", "Application Support", "loomspan Console", "config.yaml"), nil
	}
	root := environment.XDGConfigHome
	if !filepath.IsAbs(root) {
		if !filepath.IsAbs(environment.Home) {
			return "", fmt.Errorf("home directory must be an absolute path")
		}
		root = filepath.Join(environment.Home, ".config")
	}
	return filepath.Join(root, "loomspan-console", "config.yaml"), nil
}

func defaultWorkspaceParent(environment PathEnvironment) (string, error) {
	if runtime.GOOS == "darwin" {
		if !filepath.IsAbs(environment.Home) {
			return "", fmt.Errorf("home directory must be an absolute path")
		}
		return filepath.Join(environment.Home, "Library", "Caches", "loomspan Console", "workspaces"), nil
	}
	root := environment.XDGStateHome
	if !filepath.IsAbs(root) {
		if !filepath.IsAbs(environment.Home) {
			return "", fmt.Errorf("home directory must be an absolute path")
		}
		root = filepath.Join(environment.Home, ".local", "state")
	}
	return filepath.Join(root, "loomspan-console", "workspaces"), nil
}
