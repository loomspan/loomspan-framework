//go:build !windows

package workspace

import (
	"errors"
	"os"

	"golang.org/x/sys/unix"
)

type fileLock struct {
	file *os.File
	path string
	held bool
}

func acquireFileLock(path string) (*fileLock, error) {
	_, statErr := os.Lstat(path)
	created := os.IsNotExist(statErr)
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600)
	if err != nil {
		return nil, err
	}
	if created {
		if err := protectNewFile(path); err != nil {
			file.Close()
			return nil, err
		}
	} else if err := verifyProtectedFile(path); err != nil {
		file.Close()
		return nil, err
	}
	if err := unix.Flock(int(file.Fd()), unix.LOCK_EX|unix.LOCK_NB); err != nil {
		file.Close()
		return nil, err
	}
	lock := &fileLock{file: file, path: path, held: true}
	if err := lock.Check(); err != nil {
		_ = lock.Release()
		return nil, err
	}
	return lock, nil
}

func (lock *fileLock) Check() error {
	if lock == nil || !lock.held || lock.file == nil {
		return errors.New("work lock is not held")
	}
	info, err := os.Lstat(lock.path)
	if err != nil {
		return err
	}
	if unsafeEntry(info) {
		return errors.New("work lock path is unsafe")
	}
	if err := verifyProtectedFile(lock.path); err != nil {
		return err
	}
	opened, err := lock.file.Stat()
	if err != nil {
		return err
	}
	if !os.SameFile(opened, info) {
		return errors.New("work lock identity changed")
	}
	return nil
}

func (lock *fileLock) Release() error {
	if lock == nil || !lock.held {
		return nil
	}
	unlockErr := unix.Flock(int(lock.file.Fd()), unix.LOCK_UN)
	closeErr := lock.file.Close()
	lock.held = false
	return errors.Join(unlockErr, closeErr)
}
