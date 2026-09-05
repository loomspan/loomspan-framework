//go:build !windows

package profile

import (
	"os"
	"path/filepath"
	"testing"
)

func TestOpenProfileCanonicalizesSymlinkedAncestor(t *testing.T) {
	base := t.TempDir()
	realParent := filepath.Join(base, "real")
	if err := os.Mkdir(realParent, 0o700); err != nil {
		t.Fatal(err)
	}
	alias := filepath.Join(base, "alias")
	if err := os.Symlink(realParent, alias); err != nil {
		t.Fatal(err)
	}

	owned, err := Open(filepath.Join(alias, "profile", "config.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	canonicalParent, err := filepath.EvalSymlinks(realParent)
	if err != nil {
		t.Fatal(err)
	}
	if want := filepath.Join(canonicalParent, "profile"); owned.Directory != want {
		t.Fatalf("profile directory = %q, want %q", owned.Directory, want)
	}
}

func TestOpenProfileRejectsSymlinkedProfileDirectory(t *testing.T) {
	base := t.TempDir()
	target := filepath.Join(base, "target")
	if err := os.Mkdir(target, 0o700); err != nil {
		t.Fatal(err)
	}
	selected := filepath.Join(base, "selected")
	if err := os.Symlink(target, selected); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(filepath.Join(selected, "config.yaml")); err == nil {
		t.Fatal("symlinked profile directory was accepted")
	}
}
