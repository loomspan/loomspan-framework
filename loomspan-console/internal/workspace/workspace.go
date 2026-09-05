package workspace

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
)

const (
	MarkerName    = ".loomspan-console-work"
	MarkerContent = "loomspan-console-work-v1\n"
	LockName      = ".lock"
	TransientName = "transient"
)

type Workspace struct {
	Root          string
	Transient     string
	lock          *fileLock
	rootInfo      os.FileInfo
	markerInfo    os.FileInfo
	transientInfo os.FileInfo
	mu            sync.Mutex
	closed        bool
}

func Open(selection string) (*Workspace, error) {
	absolute, err := filepath.Abs(selection)
	if err != nil {
		return nil, fmt.Errorf("resolve work directory: %w", err)
	}
	root := filepath.Clean(absolute)
	created := false
	if _, err := os.Lstat(root); os.IsNotExist(err) {
		if err := os.MkdirAll(root, 0o700); err != nil {
			return nil, fmt.Errorf("create work directory: %w", err)
		}
		if err := protectNewDirectory(root); err != nil {
			return nil, fmt.Errorf("protect work directory: %w", err)
		}
		created = true
	} else if err != nil {
		return nil, fmt.Errorf("inspect work directory: %w", err)
	}
	root, err = resolveSafeDirectory(root)
	if err != nil {
		return nil, fmt.Errorf("work directory is unsafe: %w", err)
	}
	if err := verifyProtectedDirectory(root); err != nil {
		return nil, fmt.Errorf("work directory protection: %w", err)
	}
	if created {
		markerPath := filepath.Join(root, MarkerName)
		if err := os.WriteFile(markerPath, []byte(MarkerContent), 0o600); err != nil {
			return nil, fmt.Errorf("create work marker: %w", err)
		}
		if err := protectNewFile(markerPath); err != nil {
			return nil, fmt.Errorf("protect work marker: %w", err)
		}
	} else if err := verifyMarker(root); err != nil {
		return nil, err
	}
	lockPath := filepath.Join(root, LockName)
	lock, err := acquireFileLock(lockPath)
	if err != nil {
		return nil, fmt.Errorf("work directory is already owned by another loomspan Console process or cannot be locked: work directory %s: lock file %s: %w", root, lockPath, err)
	}
	workspace := &Workspace{Root: root, Transient: filepath.Join(root, TransientName), lock: lock}
	if err := workspace.cleanAndCapture(); err != nil {
		_ = lock.Release()
		return nil, err
	}
	return workspace, nil
}

func verifyMarker(root string) error {
	path := filepath.Join(root, MarkerName)
	info, err := os.Lstat(path)
	if err != nil {
		if os.IsNotExist(err) {
			return fmt.Errorf("refusing unmarked work directory %s", root)
		}
		return fmt.Errorf("inspect work marker: %w", err)
	}
	if info.Mode()&os.ModeSymlink != 0 || !info.Mode().IsRegular() {
		return fmt.Errorf("work marker is unsafe")
	}
	if err := verifyProtectedFile(path); err != nil {
		return fmt.Errorf("work marker protection: %w", err)
	}
	content, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("read work marker: %w", err)
	}
	if !bytes.Equal(content, []byte(MarkerContent)) {
		return fmt.Errorf("work marker has unsupported content")
	}
	return nil
}

func (workspace *Workspace) cleanAndCapture() error {
	if err := cleanTransient(workspace.Root); err != nil {
		return fmt.Errorf("clean transient workspace: %w", err)
	}
	rootInfo, err := os.Stat(workspace.Root)
	if err != nil {
		return err
	}
	markerInfo, err := os.Stat(filepath.Join(workspace.Root, MarkerName))
	if err != nil {
		return err
	}
	transientInfo, err := os.Stat(workspace.Transient)
	if err != nil {
		return err
	}
	workspace.rootInfo = rootInfo
	workspace.markerInfo = markerInfo
	workspace.transientInfo = transientInfo
	return nil
}

func (workspace *Workspace) Check() error {
	workspace.mu.Lock()
	defer workspace.mu.Unlock()
	if workspace.closed || workspace.lock == nil {
		return fmt.Errorf("work lock is not held")
	}
	if err := workspace.lock.Check(); err != nil {
		return err
	}
	if unsafe, err := unsafePath(workspace.Root); err != nil || unsafe {
		return fmt.Errorf("work directory safety changed")
	}
	if err := verifyProtectedDirectory(workspace.Root); err != nil {
		return err
	}
	if err := verifyProtectedDirectory(workspace.Transient); err != nil {
		return err
	}
	if err := verifyMarker(workspace.Root); err != nil {
		return err
	}
	checks := []struct {
		path string
		want os.FileInfo
	}{
		{workspace.Root, workspace.rootInfo},
		{filepath.Join(workspace.Root, MarkerName), workspace.markerInfo},
		{workspace.Transient, workspace.transientInfo},
	}
	for _, check := range checks {
		info, err := os.Stat(check.path)
		if err != nil || !os.SameFile(check.want, info) {
			return fmt.Errorf("workspace identity changed")
		}
	}
	probe, err := os.CreateTemp(workspace.Transient, ".health-*")
	if err != nil {
		return fmt.Errorf("workspace probe create failed")
	}
	probeName := probe.Name()
	if _, err := probe.Write([]byte("loomspan-console-workspace-health\n")); err != nil {
		probe.Close()
		os.Remove(probeName)
		return fmt.Errorf("workspace probe write failed")
	}
	if err := probe.Sync(); err != nil {
		probe.Close()
		os.Remove(probeName)
		return fmt.Errorf("workspace probe sync failed")
	}
	closeErr := probe.Close()
	removeErr := os.Remove(probeName)
	if err := errors.Join(closeErr, removeErr); err != nil {
		return fmt.Errorf("workspace probe cleanup failed")
	}
	return nil
}

func (workspace *Workspace) Cleanup() error {
	if workspace == nil {
		return nil
	}
	if err := workspace.Check(); err != nil {
		return err
	}
	workspace.mu.Lock()
	defer workspace.mu.Unlock()
	if err := cleanTransient(workspace.Root); err != nil {
		return err
	}
	info, err := os.Stat(workspace.Transient)
	if err == nil {
		workspace.transientInfo = info
	}
	return err
}

func (workspace *Workspace) Close() error {
	if workspace == nil {
		return nil
	}
	workspace.mu.Lock()
	defer workspace.mu.Unlock()
	if workspace.closed {
		return nil
	}
	workspace.closed = true
	return workspace.lock.Release()
}
