package mcpadapter

import (
	"context"
	"fmt"
	"sync"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/mcpcredential"
)

const mutationDrainTimeout = requestTimeout + 5*time.Second

type Lifecycle struct {
	store         *mcpcredential.Store
	tracker       *Tracker
	closeSessions func()
	mutationGate  chan struct{}
	shutdown      chan struct{}
	stateMu       sync.Mutex
	shuttingDown  bool
	drainCancel   context.CancelFunc
}

func NewLifecycle(store *mcpcredential.Store, tracker *Tracker, closeSessions func()) *Lifecycle {
	gate := make(chan struct{}, 1)
	gate <- struct{}{}
	return &Lifecycle{store: store, tracker: tracker, closeSessions: closeSessions, mutationGate: gate, shutdown: make(chan struct{})}
}

func (lifecycle *Lifecycle) Status() mcpcredential.Snapshot { return lifecycle.store.Snapshot() }
func (lifecycle *Lifecycle) Reveal() (string, error)        { return lifecycle.store.Reveal() }

func (lifecycle *Lifecycle) Enable(ctx context.Context) (credential string, err error) {
	if err := lifecycle.acquireMutation(ctx); err != nil {
		return "", err
	}
	defer lifecycle.releaseMutation()
	before := lifecycle.store.Snapshot()
	defer lifecycle.diagnosticTransition(ctx, before)
	prepared, err := lifecycle.store.Prepare()
	if err != nil {
		return "", err
	}
	return lifecycle.mutatePrepared(prepared, func() (string, error) { return lifecycle.store.CommitEnable(prepared) })
}

func (lifecycle *Lifecycle) Regenerate(ctx context.Context) (credential string, err error) {
	if err := lifecycle.acquireMutation(ctx); err != nil {
		return "", err
	}
	defer lifecycle.releaseMutation()
	before := lifecycle.store.Snapshot()
	defer lifecycle.diagnosticTransition(ctx, before)
	prepared, err := lifecycle.store.Prepare()
	if err != nil {
		return "", err
	}
	return lifecycle.mutatePrepared(prepared, func() (string, error) { return lifecycle.store.CommitRegenerate(prepared) })
}

func (lifecycle *Lifecycle) Disable(ctx context.Context) error {
	if err := lifecycle.acquireMutation(ctx); err != nil {
		return err
	}
	defer lifecycle.releaseMutation()
	before := lifecycle.store.Snapshot()
	defer lifecycle.diagnosticTransition(ctx, before)
	_, err := lifecycle.mutate(func() (string, error) { return "", lifecycle.store.Disable() })
	return err
}

func (lifecycle *Lifecycle) RemoveInvalid(ctx context.Context) error {
	if err := lifecycle.acquireMutation(ctx); err != nil {
		return err
	}
	defer lifecycle.releaseMutation()
	before := lifecycle.store.Snapshot()
	defer lifecycle.diagnosticTransition(ctx, before)
	_, err := lifecycle.mutate(func() (string, error) { return "", lifecycle.store.RemoveInvalid() })
	return err
}

func (lifecycle *Lifecycle) mutatePrepared(prepared *mcpcredential.Prepared, commit func() (string, error)) (string, error) {
	drain, cancel, err := lifecycle.beginMutationDrain()
	if err != nil {
		lifecycle.store.Discard(prepared)
		return "", err
	}
	defer lifecycle.finishMutationDrain(cancel)
	if err := lifecycle.tracker.Freeze(drain, false, lifecycle.closeSessions); err != nil {
		_ = lifecycle.tracker.Reopen()
		lifecycle.store.Discard(prepared)
		return "", err
	}
	defer lifecycle.tracker.Reopen()
	return lifecycle.commitUnlessShuttingDown(prepared, commit)
}

func (lifecycle *Lifecycle) mutate(commit func() (string, error)) (string, error) {
	drain, cancel, err := lifecycle.beginMutationDrain()
	if err != nil {
		return "", err
	}
	defer lifecycle.finishMutationDrain(cancel)
	if err := lifecycle.tracker.Freeze(drain, false, lifecycle.closeSessions); err != nil {
		_ = lifecycle.tracker.Reopen()
		return "", err
	}
	defer lifecycle.tracker.Reopen()
	return lifecycle.commitUnlessShuttingDown(nil, commit)
}

func (lifecycle *Lifecycle) Shutdown(ctx context.Context) error {
	lifecycle.stateMu.Lock()
	if !lifecycle.shuttingDown {
		lifecycle.shuttingDown = true
		close(lifecycle.shutdown)
	}
	cancelMutation := lifecycle.drainCancel
	lifecycle.stateMu.Unlock()

	// Permanently close admission before waking a temporary freeze. Its
	// deferred Reopen then observes permanent=true and cannot create a gap.
	lifecycle.tracker.beginFreeze(true, lifecycle.closeSessions)
	if cancelMutation != nil {
		cancelMutation()
	}
	if err := lifecycle.tracker.waitForDrain(ctx); err != nil {
		return fmt.Errorf("drain MCP requests: %w", err)
	}
	return nil
}

func (lifecycle *Lifecycle) acquireMutation(ctx context.Context) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-lifecycle.shutdown:
		return diagnostics.Annotate(fmt.Errorf("MCP lifecycle is shutting down"), diagnostics.Facts{Expected: true, Cause: "shutdown"})
	case <-lifecycle.mutationGate:
		if err := ctx.Err(); err != nil {
			lifecycle.releaseMutation()
			return err
		}
		lifecycle.stateMu.Lock()
		shuttingDown := lifecycle.shuttingDown
		lifecycle.stateMu.Unlock()
		if shuttingDown {
			lifecycle.releaseMutation()
			return diagnostics.Annotate(fmt.Errorf("MCP lifecycle is shutting down"), diagnostics.Facts{Expected: true, Cause: "shutdown"})
		}
		return nil
	}
}

func (lifecycle *Lifecycle) releaseMutation() { lifecycle.mutationGate <- struct{}{} }

func (lifecycle *Lifecycle) beginMutationDrain() (context.Context, context.CancelFunc, error) {
	lifecycle.stateMu.Lock()
	defer lifecycle.stateMu.Unlock()
	if lifecycle.shuttingDown {
		return nil, nil, diagnostics.Annotate(fmt.Errorf("MCP lifecycle is shutting down"), diagnostics.Facts{Expected: true, Cause: "shutdown"})
	}
	drain, cancel := context.WithTimeout(context.Background(), mutationDrainTimeout)
	lifecycle.drainCancel = cancel
	return drain, cancel, nil
}

func (lifecycle *Lifecycle) finishMutationDrain(cancel context.CancelFunc) {
	cancel()
	lifecycle.stateMu.Lock()
	lifecycle.drainCancel = nil
	lifecycle.stateMu.Unlock()
}

func (lifecycle *Lifecycle) commitUnlessShuttingDown(prepared *mcpcredential.Prepared, commit func() (string, error)) (string, error) {
	lifecycle.stateMu.Lock()
	defer lifecycle.stateMu.Unlock()
	if lifecycle.shuttingDown {
		if prepared != nil {
			lifecycle.store.Discard(prepared)
		}
		return "", diagnostics.Annotate(fmt.Errorf("MCP lifecycle is shutting down"), diagnostics.Facts{Expected: true, Cause: "shutdown"})
	}
	return commit()
}

// The mutation gate remains held while this compares the committed state.
func (lifecycle *Lifecycle) diagnosticTransition(ctx context.Context, before mcpcredential.Snapshot) {
	after := lifecycle.store.Snapshot()
	if before.State != after.State {
		diagnostics.Event(diagnostics.Operation(ctx, "mcp.authentication"), credentialDiagnosticState(after.State), credentialDiagnosticState(before.State))
	} else if before.Generation != after.Generation {
		diagnostics.Event(diagnostics.Operation(ctx, "mcp.authentication"), "rotated", credentialDiagnosticState(before.State))
	}
}
func credentialDiagnosticState(state mcpcredential.State) string {
	switch state {
	case mcpcredential.Enabled:
		return "enabled"
	case mcpcredential.Disabled:
		return "disabled"
	case mcpcredential.DisabledInvalid:
		return "invalid"
	default:
		return "unknown"
	}
}
