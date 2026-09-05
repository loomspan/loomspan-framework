package profile

import (
	"fmt"
	"os"
	"path/filepath"
	"sync"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/config"
)

const LockFileName = ".loomspan-console.lock"

type Profile struct {
	Directory  string
	ConfigPath string
	Config     config.File
	Resolved   config.Resolved
	mu         sync.Mutex
	lock       *fileLock
	closed     bool
}

func Open(configSelection string) (*Profile, error) {
	configPath, err := ResolveConfigPath(configSelection)
	if err != nil {
		return nil, err
	}
	directory, err := ResolveProfileDirectory(configPath)
	if err != nil {
		return nil, err
	}
	if filepath.Dir(configPath) != directory {
		configPath = filepath.Join(directory, filepath.Base(configPath))
	}
	if err := verifyProtectedDirectory(directory); err != nil {
		return nil, fmt.Errorf("profile protection: %w", err)
	}
	lockPath := filepath.Join(directory, LockFileName)
	lock, err := acquireFileLock(lockPath)
	if err != nil {
		return nil, fmt.Errorf("profile is already owned by another loomspan Console process or cannot be locked: profile directory %s: lock file %s: %w", directory, lockPath, err)
	}
	profile := &Profile{Directory: directory, ConfigPath: configPath, lock: lock}
	if err := profile.loadOrCreateConfig(); err != nil {
		_ = lock.Release()
		return nil, err
	}
	return profile, nil
}

func (profile *Profile) loadOrCreateConfig() error {
	if err := rejectUnsafePath(profile.ConfigPath); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("configuration path is unsafe: %w", err)
	}
	file, err := os.Open(profile.ConfigPath)
	if os.IsNotExist(err) {
		if err := atomicCreate(profile.ConfigPath, []byte(config.DefaultYAML)); err != nil {
			return fmt.Errorf("create default configuration: %w", err)
		}
		file, err = os.Open(profile.ConfigPath)
	}
	if err != nil {
		return fmt.Errorf("open configuration: %w", err)
	}
	defer file.Close()
	if err := verifyProtectedFile(profile.ConfigPath); err != nil {
		return fmt.Errorf("configuration protection: %w", err)
	}
	decoded, resolved, err := config.Decode(profile.ConfigPath, file)
	if err != nil {
		return err
	}
	profile.Config = decoded
	profile.Resolved = resolved
	return nil
}

func atomicCreate(path string, content []byte) error {
	temp, err := os.CreateTemp(filepath.Dir(path), ".config-*.tmp")
	if err != nil {
		return err
	}
	tempPath := temp.Name()
	defer os.Remove(tempPath)
	if err := temp.Chmod(0o600); err != nil {
		temp.Close()
		return err
	}
	if err := protectNewFile(tempPath); err != nil {
		temp.Close()
		return err
	}
	if _, err := temp.Write(content); err != nil {
		temp.Close()
		return err
	}
	if err := temp.Sync(); err != nil {
		temp.Close()
		return err
	}
	if err := temp.Close(); err != nil {
		return err
	}
	if _, err := os.Stat(path); err == nil {
		return fmt.Errorf("configuration appeared during creation")
	} else if !os.IsNotExist(err) {
		return err
	}
	return os.Rename(tempPath, path)
}

func (profile *Profile) Check() error {
	if profile == nil {
		return fmt.Errorf("profile lock is not held")
	}
	profile.mu.Lock()
	defer profile.mu.Unlock()
	if profile.closed || profile.lock == nil {
		return fmt.Errorf("profile lock is not held")
	}
	if err := profile.lock.Check(); err != nil {
		return err
	}
	if err := rejectUnsafePath(profile.Directory); err != nil {
		return err
	}
	return verifyProtectedDirectory(profile.Directory)
}

func (profile *Profile) Close() error {
	if profile == nil {
		return nil
	}
	profile.mu.Lock()
	defer profile.mu.Unlock()
	if profile.closed {
		return nil
	}
	profile.closed = true
	return profile.lock.Release()
}
