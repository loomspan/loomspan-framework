package workspace

import (
	"fmt"
	"os"
	"path/filepath"
)

func cleanTransient(root string) error {
	transient := filepath.Join(root, TransientName)
	if filepath.Dir(transient) != root {
		return fmt.Errorf("transient path escapes work directory")
	}
	info, err := os.Lstat(transient)
	if os.IsNotExist(err) {
		if err := os.Mkdir(transient, 0o700); err != nil {
			return err
		}
		return protectNewDirectory(transient)
	}
	if err != nil {
		return err
	}
	if unsafeEntry(info) || !info.IsDir() {
		return fmt.Errorf("transient path is not a safe directory")
	}
	if err := verifyProtectedDirectory(transient); err != nil {
		return err
	}
	if err := inspectTree(transient); err != nil {
		return err
	}
	entries, err := os.ReadDir(transient)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		if err := removeTree(filepath.Join(transient, entry.Name())); err != nil {
			return err
		}
	}
	return nil
}

func inspectTree(directory string) error {
	entries, err := os.ReadDir(directory)
	if err != nil {
		return err
	}
	for _, entry := range entries {
		path := filepath.Join(directory, entry.Name())
		info, err := os.Lstat(path)
		if err != nil {
			return err
		}
		if unsafeEntry(info) {
			return fmt.Errorf("unsafe link or reparse boundary in transient workspace")
		}
		if info.IsDir() {
			if err := inspectTree(path); err != nil {
				return err
			}
		}
	}
	return nil
}

func removeTree(path string) error {
	info, err := os.Lstat(path)
	if err != nil {
		return err
	}
	if unsafeEntry(info) {
		return fmt.Errorf("unsafe link or reparse boundary in transient workspace")
	}
	if info.IsDir() {
		entries, err := os.ReadDir(path)
		if err != nil {
			return err
		}
		for _, entry := range entries {
			if err := removeTree(filepath.Join(path, entry.Name())); err != nil {
				return err
			}
		}
	}
	return os.Remove(path)
}
