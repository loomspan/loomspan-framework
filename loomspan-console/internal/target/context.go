package target

import (
	"context"
	"errors"
	"fmt"
	"math/rand/v2"
	"sync"
	"sync/atomic"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
)

type ProbeClient interface {
	Probe(context.Context, applicationclient.Credential) (applicationclient.Instance, error)
	Get(context.Context, string, int64, applicationclient.Credential) ([]byte, string, error)
	OpenActivity(parent context.Context, instanceID, afterCursor string, credential applicationclient.Credential) (*applicationclient.ActivityStream, error)
	OpenArtifact(parent context.Context, traceId, instanceID string, credential applicationclient.Credential) (*applicationclient.ArtifactStream, error)
	Close()
}

type ClientFactory func(applicationclient.Address) (ProbeClient, error)
type ScopeIDSource func() (ScopeID, error)
type Clock func() time.Time

type timerHandle interface {
	Stop() bool
}

type ScopeOwner interface {
	InvalidateTargetScope(previous ScopeID, cancelled context.Context)
	ActivateActivity(scope Scope)
}

type ownerRegistration struct {
	name  string
	owner ScopeOwner
}

type state struct {
	diagnostic context.Context
	failures   diagnostics.Repeat
	id         ScopeID
	context    context.Context
	cancel     context.CancelFunc
	address    applicationclient.Address
	client     ProbeClient
	instance   applicationclient.Instance
	status     consolecore.StatusSnapshot
	activated  bool
}

type Snapshot struct {
	Status      consolecore.StatusSnapshot `json:"status"`
	Address     string                     `json:"address,omitempty"`
	Unencrypted bool                       `json:"unencrypted"`
}

type Context struct {
	mu              sync.Mutex
	probeMu         sync.Mutex
	factory         ClientFactory
	scopeIDs        ScopeIDSource
	clock           Clock
	credentials     credentialProvider
	current         *state
	owners          []ownerRegistration
	serving         bool
	invalidating    atomic.Bool
	closed          bool
	retryTimer      timerHandle
	retryAttempt    int
	retryGeneration uint64
	timerFactory    func(time.Duration, func()) timerHandle
	jitter          func(time.Duration) time.Duration
}

func New(factory ClientFactory, scopeIDs ScopeIDSource, clock Clock) (*Context, error) {
	if factory == nil {
		return nil, fmt.Errorf("target client factory is required")
	}
	if scopeIDs == nil {
		scopeIDs = newScopeID
	}
	if clock == nil {
		clock = time.Now
	}
	return &Context{
		factory: factory, scopeIDs: scopeIDs, clock: clock,
		timerFactory: func(delay time.Duration, callback func()) timerHandle {
			return time.AfterFunc(delay, callback)
		},
		jitter: func(delay time.Duration) time.Duration {
			return time.Duration(float64(delay) * (0.8 + 0.4*rand.Float64()))
		},
	}, nil
}

func (target *Context) RegisterOwner(name string, owner ScopeOwner) error {
	if target.invalidating.Load() {
		return fmt.Errorf("target scope owner registration is closed")
	}
	target.mu.Lock()
	defer target.mu.Unlock()
	if name == "" || owner == nil || target.serving || target.invalidating.Load() || target.closed {
		return fmt.Errorf("target scope owner registration is closed")
	}
	for _, registered := range target.owners {
		if registered.name == name {
			return fmt.Errorf("target scope owner %q is already registered", name)
		}
	}
	target.owners = append(target.owners, ownerRegistration{name: name, owner: owner})
	return nil
}

func (target *Context) StartServing() {
	target.mu.Lock()
	target.serving = true
	target.mu.Unlock()
}

func (target *Context) Select(addressValue string) *consolecore.Error {
	if target.invalidating.Load() {
		return consolecore.NewError(consolecore.CodeTargetChanged, "The selected target is changing. Try again.", "", consolecore.Details{}, nil)
	}
	address, err := applicationclient.NormalizeAddress(addressValue)
	if err != nil {
		return consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid HTTP or HTTPS target address.", "", consolecore.Details{}, err)
	}
	client, err := target.factory(address)
	if err != nil {
		return consolecore.NewError(consolecore.CodeInvalidArgument, "The target connection policy is invalid.", "", consolecore.Details{}, err)
	}
	target.mu.Lock()
	defer target.mu.Unlock()
	if target.closed {
		client.Close()
		return target.consoleClosed()
	}
	if target.current != nil && target.current.address.Equal(address) {
		client.Close()
		return nil
	}
	if err := target.rotateLocked(address, client, false, context.Background()); err != nil {
		client.Close()
		return consolecore.NewError(consolecore.CodeConsoleError, "The target could not be selected.", "", consolecore.Details{}, err)
	}
	return nil
}

func (target *Context) SelectAndConnect(parent context.Context, addressValue string, credential []byte) (Snapshot, *consolecore.Error) {
	if target.invalidating.Load() {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeTargetChanged, "The selected target is changing. Try again.", "", consolecore.Details{}, nil)
	}
	if err := applicationclient.ValidateCredential(credential); err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid application key.", "", consolecore.Details{}, err)
	}
	address, err := applicationclient.NormalizeAddress(addressValue)
	if err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid HTTP or HTTPS target address.", "", consolecore.Details{}, err)
	}
	client, err := target.factory(address)
	if err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "The target connection policy is invalid.", "", consolecore.Details{}, err)
	}
	target.mu.Lock()
	if target.closed {
		target.mu.Unlock()
		client.Close()
		return target.Snapshot(), target.consoleClosed()
	}
	if err := target.rotateLocked(address, client, true, parent); err != nil {
		target.mu.Unlock()
		client.Close()
		return target.Snapshot(), consolecore.NewError(consolecore.CodeConsoleError, "The target could not be selected.", "", consolecore.Details{}, err)
	}
	_, err = target.credentials.install(credential)
	scopeID := target.current.id
	target.mu.Unlock()
	if err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid application key.", "", consolecore.Details{}, err)
	}
	return target.probe(parent, true, scopeID)
}

func (target *Context) SupplyCredential(parent context.Context, credential []byte) (Snapshot, *consolecore.Error) {
	if target.invalidating.Load() {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeTargetChanged, "The selected target is changing. Try again.", "", consolecore.Details{}, nil)
	}
	if err := applicationclient.ValidateCredential(credential); err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid application key.", "", consolecore.Details{}, err)
	}
	target.mu.Lock()
	if target.closed {
		target.mu.Unlock()
		return target.Snapshot(), target.consoleClosed()
	}
	if target.current == nil {
		target.mu.Unlock()
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Select a target before supplying a key.", "", consolecore.Details{}, nil)
	}
	hadCredential := target.credentials.hasCredential()
	if hadCredential {
		client, err := target.factory(target.current.address)
		if err != nil {
			target.mu.Unlock()
			return target.Snapshot(), consolecore.NewError(consolecore.CodeConsoleError, "The credential could not be replaced.", "", consolecore.Details{}, err)
		}
		if err := target.rotateLocked(target.current.address, client, true, parent); err != nil {
			target.mu.Unlock()
			client.Close()
			return target.Snapshot(), consolecore.NewError(consolecore.CodeConsoleError, "The credential could not be replaced.", "", consolecore.Details{}, err)
		}
	}
	_, err := target.credentials.install(credential)
	scopeID := target.current.id
	target.mu.Unlock()
	if err != nil {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Enter a valid application key.", "", consolecore.Details{}, err)
	}
	return target.probe(parent, true, scopeID)
}

func (target *Context) Recheck(parent context.Context) (Snapshot, *consolecore.Error) {
	if target.invalidating.Load() {
		return target.Snapshot(), consolecore.NewError(consolecore.CodeTargetChanged, "The selected target is changing. Try again.", "", consolecore.Details{}, nil)
	}
	target.mu.Lock()
	if target.closed {
		target.mu.Unlock()
		return target.Snapshot(), target.consoleClosed()
	}
	if target.current == nil {
		target.mu.Unlock()
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Select a target first.", "", consolecore.Details{}, nil)
	}
	scopeID := target.current.id
	target.mu.Unlock()
	return target.probe(parent, true, scopeID)
}

func (target *Context) Capture() (Scope, *consolecore.Error) {
	if target.invalidating.Load() {
		return Scope{}, consolecore.NewError(consolecore.CodeTargetChanged, "The selected target is changing. Try again.", "", consolecore.Details{}, nil)
	}
	target.mu.Lock()
	defer target.mu.Unlock()
	if target.current == nil {
		return Scope{}, consolecore.NewError(consolecore.CodeInvalidArgument, "Select a target first.", "", consolecore.Details{}, nil)
	}
	return Scope{
		ID: target.current.id, Context: target.current.context, Target: target.current.address,
		InstanceID: target.current.instance.InstanceID, client: target.current.client,
		credential: target.credentials.capability(), authority: target,
	}, nil
}

func (target *Context) revalidateAfterMismatch(parent context.Context, expected ScopeID) {
	_, _ = target.probe(parent, false, expected)
}

func (target *Context) IsCurrent(scope ScopeID) bool {
	if target.invalidating.Load() {
		return false
	}
	target.mu.Lock()
	defer target.mu.Unlock()
	return !target.closed && target.current != nil && target.current.id == scope
}

func (target *Context) RequireCurrent(scope ScopeID) *consolecore.Error {
	target.mu.Lock()
	defer target.mu.Unlock()
	if !target.closed && target.current != nil && target.current.id == scope {
		return nil
	}
	current := ""
	if target.current != nil {
		current = string(target.current.id)
	}
	return consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.",
		string(scope), consolecore.Details{CurrentTargetScopeID: current}, nil)
}

func (target *Context) PublishCurrent(scope ScopeID, publish func()) *consolecore.Error {
	target.mu.Lock()
	if target.closed || target.current == nil || target.current.id != scope {
		domain := target.changedLocked(scope)
		target.mu.Unlock()
		return domain
	}
	target.mu.Unlock()
	publish()
	return nil
}

// PublishCurrentAtomic linearizes one bounded publication with target rotation.
// Callers must pre-encode the response and keep publish free of unbounded work.
func (target *Context) PublishCurrentAtomic(scope ScopeID, publish func()) *consolecore.Error {
	target.mu.Lock()
	defer target.mu.Unlock()
	if target.closed || target.current == nil || target.current.id != scope {
		return target.changedLocked(scope)
	}
	publish()
	return nil
}

func (target *Context) Snapshot() Snapshot {
	if target.invalidating.Load() {
		return Snapshot{Status: consolecore.NoTargetStatus(target.clock())}
	}
	target.mu.Lock()
	defer target.mu.Unlock()
	if target.current == nil {
		return Snapshot{Status: consolecore.NoTargetStatus(target.clock())}
	}
	status := target.current.status
	status.ObservedAt = target.clock()
	return Snapshot{Status: status, Address: target.current.address.String(), Unencrypted: target.current.address.Unencrypted()}
}

func (target *Context) probe(parent context.Context, manual bool, expected ScopeID) (Snapshot, *consolecore.Error) {
	target.probeMu.Lock()
	defer target.probeMu.Unlock()

	target.mu.Lock()
	if target.closed {
		target.mu.Unlock()
		return target.Snapshot(), target.consoleClosed()
	}
	current := target.current
	if current == nil {
		target.mu.Unlock()
		return target.Snapshot(), consolecore.NewError(consolecore.CodeInvalidArgument, "Select a target first.", "", consolecore.Details{}, nil)
	}
	if current.id != expected {
		snapshot, domain := target.snapshotLocked(), target.changedLocked(expected)
		target.mu.Unlock()
		return snapshot, domain
	}
	if manual {
		target.stopRetryLocked()
	}
	if current.diagnostic == nil {
		current.diagnostic = diagnostics.WithScope(diagnostics.Detach(current.context, parent, "target.probe"), string(current.id))
		current.context = diagnostics.WithScope(diagnostics.Detach(current.context, parent, "target.scope"), string(current.id))
	}
	diagnostic := current.diagnostic
	previous := current.status
	defer func() {
		target.mu.Lock()
		defer target.mu.Unlock()
		if target.current == current {
			target.transitionsLocked(current, previous)
		}
	}()
	credential := target.credentials.capability()
	if credential == nil {
		current.status.TargetAuthentication = consolecore.AuthenticationRequired
		current.status.ObservedAt = target.clock()
		target.mu.Unlock()
		return target.Snapshot(), consolecore.NewError(consolecore.CodeTargetAuthentication, "An application key is required.", string(current.id), consolecore.Details{}, nil)
	}
	scopeID, scopeContext, client := current.id, current.context, current.client
	target.mu.Unlock()

	operation, cancel := context.WithCancel(parent)
	stop := context.AfterFunc(scopeContext, cancel)
	instance, err := client.Probe(operation, credential)
	stop()
	cancel()

	target.mu.Lock()
	if target.closed || target.current == nil || target.current.id != scopeID {
		snapshot, domain := target.snapshotLocked(), target.changedLocked(scopeID)
		target.mu.Unlock()
		return snapshot, domain
	}
	// Retained body-read causes must not replace the client's response mapping.
	var mappedFailure *applicationclient.Failure
	if errors.Is(err, context.Canceled) && parent.Err() != nil && !errors.As(err, &mappedFailure) {
		snapshot := target.snapshotLocked()
		target.mu.Unlock()
		return snapshot, consolecore.NewError(
			consolecore.CodeTargetUnavailable,
			"The target check was canceled.",
			string(scopeID),
			consolecore.Details{},
			err,
		)
	}
	if err != nil {
		domain := target.commitFailureLocked(target.current, err)
		current.failures.Failure(diagnostic, diagnostics.Annotate(domain, diagnostics.Facts{Endpoint: "instance", Expected: parent.Err() != nil}))
		if manual && parent.Err() == nil && !diagnostics.Extract(domain).Expected {
			diagnostics.Link(diagnostics.WithScope(parent, string(scopeID)), diagnostic)
		}
		var failure *applicationclient.Failure
		if errors.As(err, &failure) && failure.Retryable {
			target.scheduleRetryLocked(scopeID)
		}
		snapshot := target.snapshotLocked()
		target.mu.Unlock()
		return snapshot, domain
	}
	if target.current.instance.InstanceID != "" && target.current.instance.InstanceID != instance.InstanceID {
		address := target.current.address
		newClient, factoryErr := target.factory(address)
		if factoryErr != nil {
			snapshot := target.snapshotLocked()
			domain := consolecore.NewError(consolecore.CodeConsoleError, "The target runtime changed but could not be re-established.", string(scopeID), consolecore.Details{}, factoryErr)
			current.failures.Failure(diagnostic, domain)
			if manual && parent.Err() == nil {
				diagnostics.Link(diagnostics.WithScope(parent, string(scopeID)), diagnostic)
			}
			target.mu.Unlock()
			return snapshot, domain
		}
		if rotationErr := target.rotateLocked(address, newClient, true, parent); rotationErr != nil {
			newClient.Close()
			snapshot := target.snapshotLocked()
			domain := consolecore.NewError(consolecore.CodeConsoleError, "The target runtime could not be reset.", string(scopeID), consolecore.Details{}, rotationErr)
			current.failures.Failure(diagnostic, domain)
			if manual && parent.Err() == nil {
				diagnostics.Link(diagnostics.WithScope(parent, string(scopeID)), diagnostic)
			}
			target.mu.Unlock()
			return snapshot, domain
		}
	}
	if target.current != current {
		current = target.current
		previous = current.status
		current.diagnostic = diagnostics.WithScope(diagnostics.Detach(current.context, parent, "target.probe"), string(current.id))
		current.context = diagnostics.WithScope(diagnostics.Detach(current.context, parent, "target.scope"), string(current.id))
		diagnostic = current.diagnostic
	}
	current.failures.Recover(diagnostic)
	target.current.instance = instance
	target.current.status.TargetConnection = consolecore.ConnectionReachable
	target.current.status.TargetAuthentication = consolecore.AuthenticationEstablished
	target.current.status.JavaGoCompatibility = consolecore.CompatibilityCompatible
	target.current.status.RuntimeIdentity = consolecore.RuntimeEstablished
	target.current.status.InstanceID = instance.InstanceID
	if instance.LiveMonitoringAvailable {
		target.current.status.LiveMonitoring = consolecore.LiveAvailable
	} else {
		target.current.status.LiveMonitoring = consolecore.LiveUnavailable
	}
	target.current.status.ObservedAt = target.clock()
	target.stopRetryLocked()
	needActivation := !target.current.activated
	target.current.activated = true
	scope := Scope{
		ID: target.current.id, Context: target.current.context, Target: target.current.address,
		InstanceID: target.current.instance.InstanceID, client: target.current.client,
		credential: target.credentials.capability(), authority: target,
	}
	owners := append([]ownerRegistration(nil), target.owners...)
	snapshot := target.snapshotLocked()
	target.mu.Unlock()
	if needActivation {
		for _, registration := range owners {
			registration.owner.ActivateActivity(scope)
		}
	}
	return snapshot, nil
}

func (target *Context) commitFailureLocked(current *state, err error) *consolecore.Error {
	var failure *applicationclient.Failure
	if !errors.As(err, &failure) {
		current.status.TargetConnection = consolecore.ConnectionUnavailable
		return consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", string(current.id), consolecore.Details{}, err)
	}
	details := consolecore.Details{TransportCategory: string(failure.Category)}
	switch failure.Kind {
	case applicationclient.FailureAuthentication:
		current.status.TargetConnection = consolecore.ConnectionReachable
		current.status.TargetAuthentication = consolecore.AuthenticationRequired
		return consolecore.NewError(consolecore.CodeTargetAuthentication, "The application key was rejected.", string(current.id), details, err)
	case applicationclient.FailureAccess:
		current.status.TargetConnection = consolecore.ConnectionReachable
		current.status.TargetAuthentication = consolecore.AuthenticationBlocked
		return consolecore.NewError(consolecore.CodeTargetAccessBlocked, "The selected target denied access before Loomspan authentication.", string(current.id), details, err)
	case applicationclient.FailureIncompatible:
		current.status.TargetConnection = consolecore.ConnectionReachable
		current.status.TargetAuthentication = consolecore.AuthenticationEstablished
		current.status.JavaGoCompatibility = consolecore.CompatibilityIncompatible
		details.ExpectedCompatibilityVersion = failure.Expected
		details.ObservedCompatibilityVersion = failure.Observed
		return consolecore.NewError(consolecore.CodeIncompatibleTarget, "The selected target uses a different Loomspan release.", string(current.id), details, err)
	case applicationclient.FailureInvalidArgument:
		return consolecore.NewError(consolecore.CodeInvalidArgument, "The request was invalid.", string(current.id), details, err)
	case applicationclient.FailureInvalidCursor:
		return consolecore.NewError(consolecore.CodeInvalidCursor, "The continuation is invalid.", string(current.id), details, err)
	case applicationclient.FailureStaleCursor:
		return consolecore.NewError(consolecore.CodeStaleCursor, "The continuation belongs to another application instance.", string(current.id), details, err)
	case applicationclient.FailureNotFound:
		return consolecore.NewError(consolecore.CodeNotFound, "The requested observability resource was not found.", string(current.id), details, err)
	case applicationclient.FailureLimitExceeded:
		return consolecore.NewError(consolecore.CodeLimitExceeded, "The observability response exceeds the configured limit.", string(current.id), details, err)
	case applicationclient.FailureLiveMonitoringUnavailable:
		return consolecore.NewError(consolecore.CodeLiveMonitoringUnavailable, "Live execution monitoring is unavailable.", string(current.id), details, err)
	default:
		if failure.Category == applicationclient.CategoryUpstreamProtocol {
			current.status.TargetConnection = consolecore.ConnectionReachable
		} else {
			current.status.TargetConnection = consolecore.ConnectionUnavailable
		}
		return consolecore.NewError(consolecore.CodeTargetUnavailable, "The selected target is unavailable.", string(current.id), details, err)
	}
}

func (target *Context) rotateLocked(address applicationclient.Address, client ProbeClient, preserveCredential bool, source context.Context) error {
	target.stopRetryLocked()
	id, err := target.scopeIDs()
	if err != nil {
		return err
	}
	if target.current != nil {
		old := target.current
		old.failures.Reset()
		diagnostics.Event(diagnostics.Operation(old.context, "target.selection"), "rotated", "selected")
		old.cancel()
		target.current = nil
		old.client.Close()
		target.invalidating.Store(true)
		for _, registration := range target.owners {
			registration.owner.InvalidateTargetScope(old.id, old.context)
		}
		target.invalidating.Store(false)
	}
	if !preserveCredential {
		target.credentials.close()
		target.credentials = credentialProvider{}
	}
	scopeContext, cancel := context.WithCancel(diagnostics.WithScope(diagnostics.Detach(context.Background(), source, "target.scope"), string(id)))
	target.current = &state{
		id: id, context: scopeContext, cancel: cancel, address: address, client: client,
		status: consolecore.StatusSnapshot{
			ObservedAt: target.clock(), TargetScopeID: string(id),
			TargetSelection:      consolecore.SelectionSelected,
			TargetConnection:     consolecore.ConnectionUnknown,
			TargetAuthentication: consolecore.AuthenticationRequired,
			JavaGoCompatibility:  consolecore.CompatibilityNotChecked,
			RuntimeIdentity:      consolecore.RuntimeNotEstablished,
			LiveMonitoring:       consolecore.LiveUnknown,
		},
	}
	diagnostics.Event(diagnostics.Operation(diagnostics.WithScope(scopeContext, string(id)), "target.selection"), "selected", "")
	return nil
}

func (target *Context) scheduleRetryLocked(scope ScopeID) {
	if target.closed || target.current == nil || target.current.id != scope || target.retryTimer != nil {
		return
	}
	delays := [...]time.Duration{
		time.Second, 2 * time.Second, 4 * time.Second, 8 * time.Second, 16 * time.Second, 30 * time.Second,
	}
	index := target.retryAttempt
	if index >= len(delays) {
		index = len(delays) - 1
	}
	delay := target.jitter(delays[index])
	minimum, maximum := delays[index]*8/10, delays[index]*12/10
	if delay < minimum {
		delay = minimum
	}
	if delay > maximum {
		delay = maximum
	}
	target.retryAttempt++
	target.retryGeneration++
	generation := target.retryGeneration
	target.retryTimer = target.timerFactory(delay, func() {
		target.mu.Lock()
		if target.retryTimer == nil || target.retryGeneration != generation ||
			target.closed || target.current == nil || target.current.id != scope {
			target.mu.Unlock()
			return
		}
		target.retryTimer = nil
		retryContext := target.current.diagnostic
		target.mu.Unlock()
		_, _ = target.probe(retryContext, false, scope)
	})
}

func (target *Context) stopRetryLocked() {
	if target.retryTimer != nil {
		target.retryTimer.Stop()
		target.retryTimer = nil
	}
	target.retryGeneration++
	target.retryAttempt = 0
}

func (target *Context) snapshotLocked() Snapshot {
	if target.current == nil {
		return Snapshot{Status: consolecore.NoTargetStatus(target.clock())}
	}
	status := target.current.status
	status.ObservedAt = target.clock()
	return Snapshot{Status: status, Address: target.current.address.String(), Unencrypted: target.current.address.Unencrypted()}
}

func (target *Context) changedLocked(previous ScopeID) *consolecore.Error {
	current := ""
	if target.current != nil {
		current = string(target.current.id)
	}
	return consolecore.NewError(consolecore.CodeTargetChanged, "The selected target changed. Start this operation again.",
		string(previous), consolecore.Details{CurrentTargetScopeID: current}, nil)
}

func (target *Context) consoleClosed() *consolecore.Error {
	return consolecore.NewError(consolecore.CodeConsoleError, "The Console is shutting down.", "", consolecore.Details{}, nil)
}

func (target *Context) Close() {
	target.mu.Lock()
	if target.closed {
		target.mu.Unlock()
		return
	}
	target.closed = true
	target.stopRetryLocked()
	var old *state
	if target.current != nil {
		old = target.current
		old.failures.Reset()
		diagnostics.Event(diagnostics.Operation(old.context, "target.selection"), "closed", "selected")
		old.cancel()
		target.current = nil
		old.client.Close()
	}
	target.invalidating.Store(true)
	owners := append([]ownerRegistration(nil), target.owners...)
	target.mu.Unlock()
	if old != nil {
		for _, registration := range owners {
			registration.owner.InvalidateTargetScope(old.id, old.context)
		}
	}
	target.mu.Lock()
	target.invalidating.Store(false)
	target.credentials.close()
	target.mu.Unlock()
	target.probeMu.Lock()
	target.probeMu.Unlock()
}

func (target *Context) GoString() string {
	snapshot := target.Snapshot()
	return fmt.Sprintf("TargetContext{scope:%q,address:%q,authentication:%q}", snapshot.Status.TargetScopeID, snapshot.Address, snapshot.Status.TargetAuthentication)
}

func (target *Context) transitionsLocked(current *state, previous consolecore.StatusSnapshot) {
	ctx := current.diagnostic
	if ctx == nil {
		ctx = current.context
	}
	if current.status.TargetConnection != previous.TargetConnection {
		state := "disconnected"
		if current.status.TargetConnection == consolecore.ConnectionReachable {
			state = "connected"
		}
		previousState := "none"
		if previous.TargetConnection == consolecore.ConnectionReachable {
			previousState = "connected"
		} else if previous.TargetConnection == consolecore.ConnectionUnavailable {
			previousState = "disconnected"
		}
		diagnostics.Event(diagnostics.Operation(ctx, "target.connection"), state, previousState)
	}
	if current.status.TargetAuthentication != previous.TargetAuthentication {
		state := "required"
		if current.status.TargetAuthentication == consolecore.AuthenticationEstablished {
			state = "accepted"
		}
		if current.status.TargetAuthentication == consolecore.AuthenticationBlocked {
			state = "blocked"
		}
		previousState := "required"
		if previous.TargetAuthentication == consolecore.AuthenticationEstablished {
			previousState = "accepted"
		} else if previous.TargetAuthentication == consolecore.AuthenticationBlocked {
			previousState = "blocked"
		}
		diagnostics.Event(diagnostics.Operation(ctx, "target.authentication"), state, previousState)
	}
}
