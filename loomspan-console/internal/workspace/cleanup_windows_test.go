//go:build windows

package workspace

import (
	"os"
	"os/exec"
	"path/filepath"
	"testing"
)

func TestCleanupTransientRejectsWindowsJunctionAndPreservesOutsideSentinel(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	outside := t.TempDir()
	sentinel := filepath.Join(outside, "sentinel")
	if err := os.WriteFile(sentinel, []byte("keep"), 0o600); err != nil {
		t.Fatal(err)
	}
	junction := filepath.Join(owned.Transient, "junction")
	output, err := exec.Command("cmd.exe", "/c", "mklink", "/J", junction, outside).CombinedOutput()
	if err != nil {
		t.Fatalf("Windows junction facility unavailable: %v: %s", err, output)
	}
	t.Cleanup(func() { _ = os.Remove(junction) })
	if err := owned.Cleanup(); err == nil {
		t.Fatal("junction was accepted")
	}
	if content, err := os.ReadFile(sentinel); err != nil || string(content) != "keep" {
		t.Fatalf("outside sentinel changed: %q %v", content, err)
	}
}
