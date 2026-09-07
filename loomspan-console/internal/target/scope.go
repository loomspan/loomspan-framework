package target

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

type ScopeID string

type Scope struct {
	ID         ScopeID
	Context    context.Context
	Target     applicationclient.Address
	InstanceID string
	client     ProbeClient
	credential applicationclient.Credential
	authority  *Context
}

func (scope Scope) Probe(parent context.Context) (applicationclient.Instance, error) {
	if scope.client == nil || scope.credential == nil {
		return applicationclient.Instance{}, fmt.Errorf("target scope has no application access")
	}
	operation, cancel := context.WithCancel(parent)
	stop := context.AfterFunc(scope.Context, cancel)
	defer func() {
		stop()
		cancel()
	}()
	return scope.client.Probe(operation, scope.credential)
}

func (scope Scope) Upstream(parent context.Context, endpoint string, maxBytes int64) ([]byte, *consolecore.Error) {
	if scope.credential == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetAuthentication, "An application key is required.", string(scope.ID), consolecore.Details{}, nil)
	}
	if scope.client == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target has no application access.", string(scope.ID), consolecore.Details{}, nil)
	}
	operation, cancel := context.WithCancel(parent)
	stop := context.AfterFunc(scope.Context, cancel)
	defer func() {
		stop()
		cancel()
	}()
	body, instanceID, err := scope.client.Get(operation, endpoint, maxBytes, scope.credential)
	if instanceID != "" && scope.InstanceID != "" && scope.InstanceID != instanceID {
		if scope.Context.Err() != nil {
			return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, nil)
		}
		if scope.authority != nil {
			scope.authority.revalidateAfterMismatch(parent, scope.ID)
			if domain := scope.authority.RequireCurrent(scope.ID); domain != nil {
				return nil, domain
			}
		}
		return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, nil)
	}
	if err != nil {
		// Body-read diagnostics retain cancellation below an already mapped
		// Failure; preserve that response before handling transport cancellation.
		var failure *applicationclient.Failure
		if errors.As(err, &failure) {
			return nil, failure.ConsoleError(string(scope.ID))
		}
		if errors.Is(err, context.Canceled) {
			if parent.Err() != nil {
				return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The operation was canceled.", string(scope.ID), consolecore.Details{}, err)
			}
			return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, err)
		}
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", string(scope.ID), consolecore.Details{}, err)
	}
	return body, nil
}

func (scope Scope) RequireCurrent() *consolecore.Error {
	if scope.authority == nil {
		return nil
	}
	return scope.authority.RequireCurrent(scope.ID)
}

func (scope Scope) OpenActivity(parent context.Context, afterCursor string) (*applicationclient.ActivityStream, *consolecore.Error) {
	if scope.credential == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetAuthentication, "An application key is required.", string(scope.ID), consolecore.Details{}, nil)
	}
	if scope.client == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target has no application access.", string(scope.ID), consolecore.Details{}, nil)
	}
	if err := parent.Err(); err != nil {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The operation was canceled.", string(scope.ID), consolecore.Details{}, err)
	}
	operation, cancel := context.WithCancel(parent)
	stopScopeCancellation := context.AfterFunc(scope.Context, cancel)
	stream, err := scope.client.OpenActivity(operation, scope.InstanceID, afterCursor, scope.credential)
	if err != nil {
		stopScopeCancellation()
		cancel()
		var mismatch *applicationclient.InstanceMismatch
		if errors.As(err, &mismatch) && scope.authority != nil {
			if scope.Context.Err() != nil {
				return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, nil)
			}
			scope.authority.revalidateAfterMismatch(parent, scope.ID)
			if domain := scope.authority.RequireCurrent(scope.ID); domain != nil {
				return nil, domain
			}
		}
		var failure *applicationclient.Failure
		if errors.As(err, &failure) {
			return nil, failure.ConsoleError(string(scope.ID))
		}
		if errors.Is(err, context.Canceled) {
			if parent.Err() != nil {
				return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The operation was canceled.", string(scope.ID), consolecore.Details{}, err)
			}
			return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, err)
		}
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", string(scope.ID), consolecore.Details{}, err)
	}
	context.AfterFunc(operation, func() {
		_ = stream.Close()
	})
	stream.AddCloseHook(func() {
		stopScopeCancellation()
		cancel()
	})
	return stream, nil
}

func (scope Scope) RevalidateInstance(parent context.Context) *consolecore.Error {
	if scope.authority == nil {
		return nil
	}
	scope.authority.revalidateAfterMismatch(parent, scope.ID)
	return scope.authority.RequireCurrent(scope.ID)
}

// OpenArtifact opens a streaming GET to the authenticated Java finalized-trace
// artifact endpoint for traceId within the current target scope. It combines
// caller and scope cancellation the same way OpenActivity does: scope rotation
// cancels the upstream stream and returns TARGET_CHANGED, while caller
// cancellation is request-scoped. A late or mismatched instance identity
// triggers revalidation and cannot publish a stream.
//
// The returned stream is owned by the caller and is closed automatically when
// the operation context is cancelled. Callers must still close it to release
// the upstream connection when finished.
func (scope Scope) OpenArtifact(parent context.Context, traceId string) (*applicationclient.ArtifactStream, *consolecore.Error) {
	if scope.credential == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetAuthentication, "An application key is required.", string(scope.ID), consolecore.Details{}, nil)
	}
	if scope.client == nil {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target has no application access.", string(scope.ID), consolecore.Details{}, nil)
	}
	if err := parent.Err(); err != nil {
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The operation was canceled.", string(scope.ID), consolecore.Details{}, err)
	}
	operation, cancel := context.WithCancel(parent)
	stopScopeCancellation := context.AfterFunc(scope.Context, cancel)
	stream, err := scope.client.OpenArtifact(operation, traceId, scope.InstanceID, scope.credential)
	if err != nil {
		stopScopeCancellation()
		cancel()
		var mismatch *applicationclient.InstanceMismatch
		if errors.As(err, &mismatch) && scope.authority != nil {
			if scope.Context.Err() != nil {
				return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, nil)
			}
			scope.authority.revalidateAfterMismatch(parent, scope.ID)
			if domain := scope.authority.RequireCurrent(scope.ID); domain != nil {
				return nil, domain
			}
		}
		var failure *applicationclient.Failure
		if errors.As(err, &failure) {
			return nil, failure.ConsoleError(string(scope.ID))
		}
		if errors.Is(err, context.Canceled) {
			if parent.Err() != nil {
				return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The operation was canceled.", string(scope.ID), consolecore.Details{}, err)
			}
			return nil, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.", string(scope.ID), consolecore.Details{}, err)
		}
		return nil, consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", string(scope.ID), consolecore.Details{}, err)
	}
	context.AfterFunc(operation, func() {
		_ = stream.Close()
	})
	stream.AddCloseHook(func() {
		stopScopeCancellation()
		cancel()
	})
	return stream, nil
}

func (scope Scope) GoString() string {
	return fmt.Sprintf("Scope{ID:%q,Target:%q,InstanceID:%q}", scope.ID, scope.Target.String(), scope.InstanceID)
}

func newScopeID() (ScopeID, error) {
	var data [16]byte
	if _, err := rand.Read(data[:]); err != nil {
		return "", fmt.Errorf("generate target scope")
	}
	data[6] = (data[6] & 0x0f) | 0x40
	data[8] = (data[8] & 0x3f) | 0x80
	return ScopeID(fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		data[0:4], data[4:6], data[6:8], data[8:10], data[10:16])), nil
}
