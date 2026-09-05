//go:build !windows

package profile

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"syscall"
)

func rejectUnsafePath(path string) error {
	absolute, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	current := string(filepath.Separator)
	for _, part := range strings.Split(strings.TrimPrefix(absolute, current), string(filepath.Separator)) {
		current = filepath.Join(current, part)
		info, err := os.Lstat(current)
		if err != nil {
			return err
		}
		if info.Mode()&os.ModeSymlink != 0 {
			return fmt.Errorf("%s is a symbolic link", current)
		}
	}
	return nil
}

func resolveSafeDirectory(path string) (string, error) {
	info, err := os.Lstat(path)
	if err != nil {
		return "", err
	}
	if info.Mode()&os.ModeSymlink != 0 {
		return "", fmt.Errorf("%s is a symbolic link", path)
	}
	resolved, err := filepath.EvalSymlinks(path)
	if err != nil {
		return "", err
	}
	absolute, err := filepath.Abs(resolved)
	if err != nil {
		return "", err
	}
	absolute = filepath.Clean(absolute)
	if err := rejectUnsafePath(absolute); err != nil {
		return "", err
	}
	return absolute, nil
}

func verifyProtectedDirectory(path string) error {
	return verifyOwnedMode(path, true, 0o700)
}

func verifyProtectedFile(path string) error {
	return verifyOwnedMode(path, false, 0o600)
}

func protectNewDirectory(path string) error { return os.Chmod(path, 0o700) }
func protectNewFile(path string) error      { return os.Chmod(path, 0o600) }

func verifyOwnedMode(path string, directory bool, mode os.FileMode) error {
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	stat, ok := info.Sys().(*syscall.Stat_t)
	if !ok || stat.Uid != uint32(os.Getuid()) {
		return fmt.Errorf("directory is not owned by the current user")
	}
	if info.IsDir() != directory {
		return fmt.Errorf("unexpected file type")
	}
	if info.Mode().Perm() != mode {
		return fmt.Errorf("permissions are %03o, require %03o", info.Mode().Perm(), mode)
	}
	return nil
}
