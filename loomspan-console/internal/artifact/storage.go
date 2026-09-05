package artifact

import (
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"time"
)

// writableFile is the minimal interface for a bundle component file being
// written to disk. It allows injecting short writes, sync failures, and close
// failures in tests.
type writableFile interface {
	io.Writer
	Sync() error
	Close() error
}

// fileSystem is the smallest internal seam needed to inject filesystem
// failures (short write, sync, close, rename, remove, ENOSPC, directory
// creation, and recursive bundle cleanup) without mocking service state
// transitions. The real implementation uses os operations beneath the verified
// transient subtree.
type fileSystem interface {
	mkdirAll(path string, perm os.FileMode) error
	create(path string) (writableFile, error)
	rename(oldpath, newpath string) error
	remove(path string) error
	removeAll(path string) error
	open(path string) (io.ReadCloser, error)
	openSeekable(path string) (io.ReadSeekCloser, error)
	readDir(dir string) ([]os.DirEntry, error)
}

// realFileSystem wraps os and filepath operations.
type realFileSystem struct{}

func (realFileSystem) mkdirAll(path string, perm os.FileMode) error {
	return os.MkdirAll(path, perm)
}

func (realFileSystem) create(path string) (writableFile, error) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, 0o600)
	if err != nil {
		return nil, err
	}
	return file, nil
}

func (realFileSystem) rename(oldpath, newpath string) error {
	return retryWindows(os.Rename, oldpath, newpath)
}

func (realFileSystem) remove(path string) error {
	return os.Remove(path)
}

func (realFileSystem) removeAll(path string) error {
	return retryWindowsRemoveAll(path)
}

func (realFileSystem) open(path string) (io.ReadCloser, error) {
	return os.Open(path)
}

func (realFileSystem) openSeekable(path string) (io.ReadSeekCloser, error) {
	return os.Open(path)
}

func (realFileSystem) readDir(dir string) ([]os.DirEntry, error) {
	return os.ReadDir(dir)
}

func (realFileSystem) stat(path string) (os.FileInfo, error) {
	return os.Stat(path)
}

// storage manages the service-owned artifacts directory beneath the verified
// transient subtree and performs all filesystem operations through the
// injectable fileSystem seam. Each installed artifact lives in one random
// bundle directory containing the raw NDJSON component and processor-created
// derived components.
type storage struct {
	dir     string
	fs      fileSystem
	entropy func() ([]byte, error)
}

func newStorage(transientDir string, fs fileSystem, entropy func() ([]byte, error)) (*storage, error) {
	if fs == nil {
		fs = realFileSystem{}
	}
	if entropy == nil {
		entropy = cryptoRandBytes
	}
	dir := filepath.Join(transientDir, "artifacts")
	if err := fs.mkdirAll(dir, 0o700); err != nil {
		return nil, fmt.Errorf("create artifact directory: %w", err)
	}
	return &storage{dir: dir, fs: fs, entropy: entropy}, nil
}

// randomName generates a random hex-encoded name and returns its absolute path
// under the artifacts directory. The name is uncorrelated with the opaque
// handle so the handle never encodes a path.
func (s *storage) randomName(prefix string) (string, error) {
	data, err := s.entropy()
	if err != nil {
		return "", fmt.Errorf("generate bundle directory name: %w", err)
	}
	return filepath.Join(s.dir, prefix+hex.EncodeToString(data)), nil
}

// createStagingDir creates a protected random staging directory under the
// artifacts directory. The returned path is never exposed to callers.
func (s *storage) createStagingDir() (string, error) {
	path, err := s.randomName("staging-")
	if err != nil {
		return "", err
	}
	if err := s.fs.mkdirAll(path, 0o700); err != nil {
		return "", fmt.Errorf("create staging directory: %w", err)
	}
	return path, nil
}

// installedDirName generates a random installed bundle directory name. The name
// is uncorrelated with the opaque handle so the handle never encodes a path.
func (s *storage) installedDirName() (string, error) {
	return s.randomName("installed-")
}

// componentPath returns the absolute path of one named component inside a
// bundle directory. The caller has already validated the component name.
func (s *storage) componentPath(bundleDir string, name ComponentName) string {
	return filepath.Join(bundleDir, string(name))
}

// createComponent creates a named component file inside the bundle directory.
// The returned writableFile is owned by the caller. The path is never exposed
// to callers.
func (s *storage) createComponent(bundleDir string, name ComponentName) (writableFile, string, error) {
	path := s.componentPath(bundleDir, name)
	file, err := s.fs.create(path)
	if err != nil {
		return nil, "", err
	}
	return file, path, nil
}

// openComponent opens a named component file inside the installed bundle for
// seekable reading. Used by leases to provide read access without exposing the
// path.
func (s *storage) openComponent(bundleDir string, name ComponentName) (io.ReadSeekCloser, error) {
	return s.fs.openSeekable(s.componentPath(bundleDir, name))
}

// renameDir atomically moves a staging directory to its installed location. The
// destination must be on the same filesystem as the staging directory.
func (s *storage) renameDir(oldpath, newpath string) error {
	return s.fs.rename(oldpath, newpath)
}

// removeBundle recursively removes a bundle directory. A missing directory is
// not an error.
func (s *storage) removeBundle(path string) error {
	if path == "" {
		return nil
	}
	err := s.fs.removeAll(path)
	if err != nil && os.IsNotExist(err) {
		return nil
	}
	return err
}

// removeAllContents removes every bundle directory under the artifacts
// directory. Used during scope invalidation, shutdown, and restart cleanup.
func (s *storage) removeAllContents() error {
	entries, err := s.fs.readDir(s.dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	for _, entry := range entries {
		path := filepath.Join(s.dir, entry.Name())
		if err := s.fs.removeAll(path); err != nil && !os.IsNotExist(err) {
			return err
		}
	}
	return nil
}

// retryWindowsRenameRetries is the number of retry attempts for filesystem
// operations on Windows. Windows can transiently deny access to files that are
// still being scanned by antivirus or have lingering handles from a recently
// closed process.
const retryWindowsRenameRetries = 5

// retryWindowsRenameDelay is the initial backoff delay between retries.
const retryWindowsRenameDelay = 10 * time.Millisecond

// retryWindows wraps os.Rename with a short retry loop on Windows. On
// non-Windows platforms it calls fn directly. Windows can transiently fail
// rename with "Access is denied" when antivirus scanners or delayed handle
// releases hold a lock on the source or destination path.
func retryWindows(fn func(string, string) error, oldpath, newpath string) error {
	err := fn(oldpath, newpath)
	if err == nil || runtime.GOOS != "windows" {
		return err
	}
	for i := 0; i < retryWindowsRenameRetries; i++ {
		time.Sleep(retryWindowsRenameDelay << i)
		if err = fn(oldpath, newpath); err == nil {
			return nil
		}
	}
	return err
}

// retryWindowsRemoveAll wraps os.RemoveAll with a short retry loop on Windows.
// Windows can transiently fail recursive removal when file handles are not yet
// fully released by the kernel or antivirus.
func retryWindowsRemoveAll(path string) error {
	err := os.RemoveAll(path)
	if err == nil || runtime.GOOS != "windows" {
		return err
	}
	for i := 0; i < retryWindowsRenameRetries; i++ {
		time.Sleep(retryWindowsRenameDelay << i)
		if err = os.RemoveAll(path); err == nil {
			return nil
		}
	}
	return err
}
