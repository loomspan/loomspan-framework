package workspace

import (
	"bufio"
	"context"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestOpenWorkspaceCreatesExactProtectedLayout(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	owned, err := Open(root)
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	for _, name := range []string{MarkerName, LockName, TransientName} {
		if _, err := os.Stat(filepath.Join(root, name)); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
	}
}

func TestOpenWorkspaceRejectsUnmarkedDirectoryWithoutMutation(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	if err := os.Mkdir(root, 0o700); err != nil {
		t.Fatal(err)
	}
	sentinel := filepath.Join(root, "sentinel")
	if err := os.WriteFile(sentinel, []byte("keep"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(root); err == nil {
		t.Fatal("unmarked directory accepted")
	}
	if content, err := os.ReadFile(sentinel); err != nil || string(content) != "keep" {
		t.Fatalf("sentinel changed: %q %v", content, err)
	}
}

func TestRestartCleanupNeverAdoptsPriorEntries(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	first, err := Open(root)
	if err != nil {
		t.Fatal(err)
	}
	stale := filepath.Join(first.Transient, "nested", "stale")
	if err := os.MkdirAll(filepath.Dir(stale), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(stale, []byte("old"), 0o600); err != nil {
		t.Fatal(err)
	}
	first.Close()
	second, err := Open(root)
	if err != nil {
		t.Fatal(err)
	}
	defer second.Close()
	if _, err := os.Stat(stale); !os.IsNotExist(err) {
		t.Fatalf("stale entry remains: %v", err)
	}
}

func TestCleanupTransientRejectsNestedSymlinkAndPreservesOutsideSentinel(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	owned, err := Open(root)
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	outside := filepath.Join(t.TempDir(), "sentinel")
	if err := os.WriteFile(outside, []byte("keep"), 0o600); err != nil {
		t.Fatal(err)
	}
	link := filepath.Join(owned.Transient, "link")
	if err := os.Symlink(outside, link); err != nil {
		t.Skipf("symbolic link facility unavailable: %v", err)
	}
	if err := owned.Cleanup(); err == nil {
		t.Fatal("nested link accepted")
	}
	if content, err := os.ReadFile(outside); err != nil || string(content) != "keep" {
		t.Fatalf("outside sentinel changed: %q %v", content, err)
	}
}

func TestWorkspaceCheckDetectsMarkerChange(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	if err := os.WriteFile(filepath.Join(owned.Root, MarkerName), []byte("wrong\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := owned.Check(); err == nil {
		t.Fatal("marker change was not detected")
	}
}

func TestWorkspaceCheckDetectsLockPathLoss(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	if err := os.Remove(filepath.Join(owned.Root, LockName)); err != nil {
		t.Skipf("platform prevents replacing an open lock path: %v", err)
	}
	if err := owned.Check(); err == nil {
		t.Fatal("lock path loss was not detected")
	}
}

func TestOpenWorkspaceRejectsWeakExistingProtection(t *testing.T) {
	root := filepath.Join(t.TempDir(), "work")
	if err := os.Mkdir(root, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, MarkerName), []byte(MarkerContent), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(root); err == nil {
		t.Fatal("weak existing workspace was accepted")
	}
}

func TestWorkLockExcludesAnotherProcessUntilRelease(t *testing.T) {
	path := filepath.Join(t.TempDir(), "work")
	command := exec.Command(os.Args[0], "-test.run=TestWorkLockHelperProcess")
	command.Env = append(os.Environ(), "LOOMSPAN_WORK_LOCK_HELPER=1", "LOOMSPAN_WORK_LOCK_PATH="+path)
	stdin, err := command.StdinPipe()
	if err != nil {
		t.Fatal(err)
	}
	stdout, err := command.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	if err := command.Start(); err != nil {
		t.Fatal(err)
	}
	line, err := bufio.NewReader(stdout).ReadString('\n')
	if err != nil || line != "locked\n" {
		t.Fatalf("helper readiness=%q err=%v", line, err)
	}
	_, contention := Open(path)
	if contention == nil {
		t.Fatal("work lock admitted another process")
	}
	_ = stdin.Close()
	if err := command.Wait(); err != nil {
		t.Fatal(err)
	}
	reopened, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer reopened.Close()
	if !strings.Contains(contention.Error(), reopened.Root) ||
		!strings.Contains(contention.Error(), filepath.Join(reopened.Root, LockName)) {
		t.Fatalf("contention error omits the contended paths: %v", contention)
	}
}

func TestWorkLockHelperProcess(t *testing.T) {
	if os.Getenv("LOOMSPAN_WORK_LOCK_HELPER") != "1" {
		return
	}
	path := os.Getenv("LOOMSPAN_WORK_LOCK_PATH")
	if strings.TrimSpace(path) == "" {
		os.Exit(2)
	}
	owned, err := Open(path)
	if err != nil {
		os.Exit(3)
	}
	_, _ = os.Stdout.WriteString("locked\n")
	_, _ = io.Copy(io.Discard, os.Stdin)
	_ = owned.Close()
	os.Exit(0)
}

func TestWorkspaceMonitorReportsInvariantLoss(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	context, cancel := context.WithCancel(context.Background())
	defer cancel()
	fatal := make(chan error, 1)
	go owned.Monitor(context, time.Millisecond, func(err error) { fatal <- err })
	if err := os.WriteFile(filepath.Join(owned.Root, MarkerName), []byte("wrong\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	select {
	case err := <-fatal:
		if err == nil {
			t.Fatal("nil fatal error")
		}
	case <-time.After(time.Second):
		t.Fatal("workspace monitor did not report invariant loss")
	}
}
