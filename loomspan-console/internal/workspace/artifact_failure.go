package workspace

import (
	"errors"
	"fmt"
)

// FatalError marks loss of the process-wide workspace invariant. Ordinary
// artifact errors remain request-scoped and are returned unchanged.
type FatalError struct {
	cause error
}

func (failure *FatalError) Error() string {
	return "workspace invariant lost after artifact failure"
}

func (failure *FatalError) Unwrap() error {
	return failure.cause
}

func IsFatal(err error) bool {
	var fatal *FatalError
	return errors.As(err, &fatal)
}

func (workspace *Workspace) ClassifyArtifactFailure(artifactError error, cleanup func() error) error {
	if cleanup == nil {
		return &FatalError{cause: fmt.Errorf("artifact cleanup is unavailable")}
	}
	if err := cleanup(); err != nil {
		return &FatalError{cause: fmt.Errorf("artifact cleanup failed: %w", err)}
	}
	if err := workspace.Check(); err != nil {
		return &FatalError{cause: err}
	}
	return artifactError
}
