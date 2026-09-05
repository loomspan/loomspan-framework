package profile

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

func TestOpenProfileCreatesProtectedDirectoryLockAndDefaultConfig(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile", "config.yaml")
	owned, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(content) == "" || owned.Resolved.ListenerAddress == "" {
		t.Fatal("default config was not loaded")
	}
}

func TestProfileLockExcludesUntilRelease(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile", "config.yaml")
	first, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	_, err = Open(path)
	if err == nil {
		t.Fatal("second profile lock was admitted")
	}
	if !strings.Contains(err.Error(), first.Directory) ||
		!strings.Contains(err.Error(), filepath.Join(first.Directory, LockFileName)) {
		t.Fatalf("contention error omits the contended paths: %v", err)
	}
	if err := first.Close(); err != nil {
		t.Fatal(err)
	}
	second, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	if err := second.Close(); err != nil {
		t.Fatal(err)
	}
}

func TestProfileIdentityIsStableAndDistinct(t *testing.T) {
	root := t.TempDir()
	first := WorkspaceLeaf(filepath.Join(root, "one"))
	second := WorkspaceLeaf(filepath.Join(root, "two"))
	if len(first) != 64 || first == second || first != WorkspaceLeaf(filepath.Join(root, ".", "one")) {
		t.Fatalf("leaves first=%q second=%q", first, second)
	}
}

func TestDefaultConfigRoundTripsThroughStrictLoader(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile", "config.yaml")
	first, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	first.Close()
	second, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	second.Close()
}

func TestOpenProfileRejectsWeakExistingProtection(t *testing.T) {
	directory := filepath.Join(t.TempDir(), "profile")
	if err := os.Mkdir(directory, 0o755); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(filepath.Join(directory, "config.yaml")); err == nil {
		t.Fatal("weak existing profile was accepted")
	}
}

func TestProfileLockExcludesAnotherProcessUntilRelease(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile", "config.yaml")
	command := exec.Command(os.Args[0], "-test.run=TestProfileLockHelperProcess")
	command.Env = append(os.Environ(), "LOOMSPAN_PROFILE_LOCK_HELPER=1", "LOOMSPAN_PROFILE_LOCK_PATH="+path)
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
	if _, err := Open(path); err == nil {
		t.Fatal("profile lock admitted another process")
	}
	_ = stdin.Close()
	if err := command.Wait(); err != nil {
		t.Fatal(err)
	}
	reopened, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	reopened.Close()
}

func TestProfileMonitorReportsLockPathLoss(t *testing.T) {
	path := filepath.Join(t.TempDir(), "profile", "config.yaml")
	owned, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	lockPath := filepath.Join(owned.Directory, LockFileName)
	if err := os.Remove(lockPath); err != nil {
		t.Skipf("platform prevents replacing an open lock path: %v", err)
	}
	runContext, cancel := context.WithCancel(context.Background())
	defer cancel()
	fatal := make(chan error, 1)
	go owned.Monitor(runContext, time.Millisecond, func(err error) { fatal <- err })
	select {
	case err := <-fatal:
		if err == nil || !strings.Contains(err.Error(), "profile invariant lost") {
			t.Fatalf("unexpected fatal error: %v", err)
		}
	case <-time.After(time.Second):
		t.Fatal("profile monitor did not report lock path loss")
	}
}

func TestProfileLockHelperProcess(t *testing.T) {
	if os.Getenv("LOOMSPAN_PROFILE_LOCK_HELPER") != "1" {
		return
	}
	path := os.Getenv("LOOMSPAN_PROFILE_LOCK_PATH")
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
