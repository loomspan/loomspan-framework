package workspace

import (
	"errors"
	"os"
	"path/filepath"
	"testing"
)

func TestArtifactLocalFailureRemainsScopedOnlyAfterCleanupAndHealthyProbe(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	artifactError := errors.New("artifact rejected")
	got := owned.ClassifyArtifactFailure(artifactError, func() error { return nil })
	if !errors.Is(got, artifactError) || IsFatal(got) {
		t.Fatalf("classification=%v", got)
	}
}

func TestArtifactLocalFailureBecomesFatalWhenCleanupOrProbeFails(t *testing.T) {
	owned, err := Open(filepath.Join(t.TempDir(), "work"))
	if err != nil {
		t.Fatal(err)
	}
	defer owned.Close()
	if got := owned.ClassifyArtifactFailure(errors.New("artifact"), func() error { return errors.New("cleanup") }); !IsFatal(got) {
		t.Fatalf("cleanup classification=%v", got)
	}
	if err := os.WriteFile(filepath.Join(owned.Root, MarkerName), []byte("wrong\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	if got := owned.ClassifyArtifactFailure(errors.New("artifact"), func() error { return nil }); !IsFatal(got) {
		t.Fatalf("probe classification=%v", got)
	}
}
