package target

import (
	"context"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/applicationclient"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

type fakeClient struct {
	mu       sync.Mutex
	instance applicationclient.Instance
	err      error
	started  chan struct{}
	release  chan struct{}
}

func (client *fakeClient) Probe(context.Context, applicationclient.Credential) (applicationclient.Instance, error) {
	if client.started != nil {
		close(client.started)
		<-client.release
	}
	client.mu.Lock()
	defer client.mu.Unlock()
	return client.instance, client.err
}
func (client *fakeClient) Get(context.Context, string, int64, applicationclient.Credential) ([]byte, string, error) {
	client.mu.Lock()
	defer client.mu.Unlock()
	return []byte(`{}`), client.instance.InstanceID, client.err
}
func (client *fakeClient) OpenActivity(context.Context, string, string, applicationclient.Credential) (*applicationclient.ActivityStream, error) {
	return nil, nil
}
func (client *fakeClient) OpenArtifact(context.Context, string, string, applicationclient.Credential) (*applicationclient.ArtifactStream, error) {
	return nil, nil
}
func (*fakeClient) Close() {}

func fixedInstance(id string) applicationclient.Instance {
	return applicationclient.Instance{
		InstanceID: id, ConsoleCompatibilityVersion: "1.0.0-beta.3-SNAPSHOT",
		ObservedAt: time.Now(), LiveMonitoringAvailable: true,
	}
}

func TestContextCreatesOrRotatesScopeOnlyForAuthoritativeChanges(t *testing.T) {
	ids := []ScopeID{"scope-1", "scope-2", "scope-3"}
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	targetContext, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil }, time.Now)
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	initial := targetContext.Snapshot().Status.TargetScopeID
	if _, err := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); err != nil {
		t.Fatal(err)
	}
	if targetContext.Snapshot().Status.TargetScopeID != initial {
		t.Fatal("first credential rotated the selected scope")
	}
	if _, err := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("k", 32))); err != nil {
		t.Fatal(err)
	}
	if targetContext.Snapshot().Status.TargetScopeID == initial {
		t.Fatal("replacement credential did not rotate")
	}
}

func TestLateProbeResultCannotCommitReturnOrPublishAfterScopeRotation(t *testing.T) {
	oldClient := &fakeClient{
		instance: fixedInstance("11111111-1111-4111-8111-111111111111"),
		started:  make(chan struct{}), release: make(chan struct{}),
	}
	newClient := &fakeClient{instance: fixedInstance("22222222-2222-4222-8222-222222222222")}
	calls := 0
	targetContext, _ := New(func(applicationclient.Address) (ProbeClient, error) {
		calls++
		if calls == 1 {
			return oldClient, nil
		}
		return newClient, nil
	}, func() func() (ScopeID, error) {
		ids := []ScopeID{"scope-1", "scope-2"}
		return func() (ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil }
	}(), time.Now)
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	result := make(chan *consolecore.Error, 1)
	go func() {
		_, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32)))
		result <- domain
	}()
	<-oldClient.started
	if err := targetContext.Select("http://127.0.0.1:8081"); err != nil {
		t.Fatal(err)
	}
	close(oldClient.release)
	if domain := <-result; domain == nil || domain.Code != consolecore.CodeTargetChanged {
		t.Fatalf("late result was not rejected: %#v", domain)
	}
	if targetContext.Snapshot().Status.InstanceID != "" {
		t.Fatal("late result committed an identity")
	}
}

func TestConcurrentMutationsProbeOnlyTheirCommittedScope(t *testing.T) {
	ids := []ScopeID{"scope-a", "scope-b"}
	client := &fakeClient{
		instance: fixedInstance("11111111-1111-4111-8111-111111111111"),
	}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) {
			id := ids[0]
			ids = ids[1:]
			return id, nil
		},
		time.Now,
	)
	defer targetContext.Close()

	type operationResult struct {
		snapshot Snapshot
		domain   *consolecore.Error
	}
	firstResult := make(chan operationResult, 1)
	secondResult := make(chan operationResult, 1)
	targetContext.probeMu.Lock()
	probeLockHeld := true
	defer func() {
		if probeLockHeld {
			targetContext.probeMu.Unlock()
		}
	}()
	go func() {
		snapshot, domain := targetContext.SelectAndConnect(
			context.Background(),
			"http://127.0.0.1:8081",
			[]byte(strings.Repeat("a", 32)),
		)
		firstResult <- operationResult{snapshot: snapshot, domain: domain}
	}()
	waitForSelectedAddress(t, targetContext, "http://127.0.0.1:8081")
	go func() {
		snapshot, domain := targetContext.SelectAndConnect(
			context.Background(),
			"http://127.0.0.1:8082",
			[]byte(strings.Repeat("b", 32)),
		)
		secondResult <- operationResult{snapshot: snapshot, domain: domain}
	}()
	waitForSelectedAddress(t, targetContext, "http://127.0.0.1:8082")
	targetContext.probeMu.Unlock()
	probeLockHeld = false

	first := <-firstResult
	if first.domain == nil || first.domain.Code != consolecore.CodeTargetChanged {
		t.Fatalf("superseded mutation returned another scope's result: snapshot=%#v error=%#v", first.snapshot, first.domain)
	}
	second := <-secondResult
	if second.domain != nil || second.snapshot.Address != "http://127.0.0.1:8082" ||
		second.snapshot.Status.TargetScopeID != "scope-b" {
		t.Fatalf("current mutation did not return its committed scope: snapshot=%#v error=%#v", second.snapshot, second.domain)
	}
}

func waitForSelectedAddress(t *testing.T, targetContext *Context, expected string) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		targetContext.mu.Lock()
		current := targetContext.current
		matches := current != nil && current.address.String() == expected
		targetContext.mu.Unlock()
		if matches {
			return
		}
		time.Sleep(time.Millisecond)
	}
	t.Fatalf("target address %q was not selected", expected)
}

func TestContextEstablishesFirstIdentityWithoutRotationAndRotatesChangedIdentity(t *testing.T) {
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	ids := []ScopeID{"scope-1", "scope-2"}
	targetContext, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil }, time.Now)
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	initial := targetContext.Snapshot().Status.TargetScopeID
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32))); domain != nil {
		t.Fatal(domain)
	}
	if targetContext.Snapshot().Status.TargetScopeID != initial {
		t.Fatal("first identity rotated")
	}
	client.mu.Lock()
	client.instance = fixedInstance("22222222-2222-4222-8222-222222222222")
	client.mu.Unlock()
	if _, domain := targetContext.Recheck(context.Background()); domain != nil {
		t.Fatal(domain)
	}
	snapshot := targetContext.Snapshot()
	if snapshot.Status.TargetScopeID == initial || snapshot.Status.InstanceID != "22222222-2222-4222-8222-222222222222" {
		t.Fatalf("changed identity did not rotate atomically: %#v", snapshot)
	}
}

func TestOrdinaryResponseMismatchRequestsAuthoritativeRevalidation(t *testing.T) {
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	ids := []ScopeID{"scope-1", "scope-2"}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) {
			id := ids[0]
			ids = ids[1:]
			return id, nil
		},
		time.Now,
	)
	defer targetContext.Close()
	if domain := targetContext.Select("http://127.0.0.1:8080"); domain != nil {
		t.Fatal(domain)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32))); domain != nil {
		t.Fatal(domain)
	}
	scope, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	client.mu.Lock()
	client.instance = fixedInstance("22222222-2222-4222-8222-222222222222")
	client.mu.Unlock()

	if _, domain = scope.Upstream(context.Background(), scope.Target.SkillsEndpoint(), 1024); domain == nil ||
		domain.Code != consolecore.CodeTargetChanged {
		t.Fatalf("expected mismatched response to be rejected, got %#v", domain)
	}
	snapshot := targetContext.Snapshot()
	if snapshot.Status.TargetScopeID != "scope-2" ||
		snapshot.Status.InstanceID != "22222222-2222-4222-8222-222222222222" {
		t.Fatalf("authoritative revalidation did not rotate runtime identity: %#v", snapshot)
	}
}

func TestScopeRotationDoesNotWaitForSocketPublication(t *testing.T) {
	ids := []ScopeID{"scope-1", "scope-2"}
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) {
			id := ids[0]
			ids = ids[1:]
			return id, nil
		},
		time.Now,
	)
	defer targetContext.Close()
	if domain := targetContext.Select("http://127.0.0.1:8080"); domain != nil {
		t.Fatal(domain)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32))); domain != nil {
		t.Fatal(domain)
	}
	scope, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	publishing := make(chan struct{})
	release := make(chan struct{})
	published := make(chan *consolecore.Error, 1)
	go func() {
		published <- targetContext.PublishCurrent(scope.ID, func() {
			close(publishing)
			<-release
		})
	}()
	<-publishing
	rotated := make(chan *consolecore.Error, 1)
	go func() {
		rotated <- targetContext.Select("http://127.0.0.1:8081")
	}()
	select {
	case domain := <-rotated:
		if domain != nil {
			t.Fatal(domain)
		}
	case <-time.After(time.Second):
		t.Fatal("scope rotation was blocked by response publication")
	}
	close(release)
	if domain := <-published; domain != nil {
		t.Fatal(domain)
	}
}

func TestAtomicPublicationRejectsRotatedScopeBeforeWriting(t *testing.T) {
	ids := []ScopeID{"scope-1", "scope-2"}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) {
			return &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}, nil
		},
		func() (ScopeID, error) {
			id := ids[0]
			ids = ids[1:]
			return id, nil
		},
		time.Now,
	)
	defer targetContext.Close()
	if domain := targetContext.Select("http://127.0.0.1:8080"); domain != nil {
		t.Fatal(domain)
	}
	old, domain := targetContext.Capture()
	if domain != nil {
		t.Fatal(domain)
	}
	if domain := targetContext.Select("http://127.0.0.1:8081"); domain != nil {
		t.Fatal(domain)
	}
	wrote := false
	domain = targetContext.PublishCurrentAtomic(old.ID, func() { wrote = true })
	if domain == nil || domain.Code != consolecore.CodeTargetChanged {
		t.Fatalf("expected TARGET_CHANGED, got %#v", domain)
	}
	if wrote {
		t.Fatal("old-scope publication ran after target rotation")
	}
}

type ownerRecorder struct {
	name      string
	order     *[]string
	activated *[]string
}

func (owner ownerRecorder) InvalidateTargetScope(ScopeID, context.Context) {
	*owner.order = append(*owner.order, owner.name)
}

func (owner ownerRecorder) ActivateActivity(scope Scope) {
	if owner.activated != nil {
		*owner.activated = append(*owner.activated, owner.name)
	}
}

func TestRotationInvalidatesOwnersInRegistrationOrder(t *testing.T) {
	ids := []ScopeID{"scope-1", "scope-2"}
	targetContext, _ := New(func(applicationclient.Address) (ProbeClient, error) { return &fakeClient{}, nil },
		func() (ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil }, time.Now)
	var order []string
	if err := targetContext.RegisterOwner("skills", ownerRecorder{"skills", &order, nil}); err != nil {
		t.Fatal(err)
	}
	if err := targetContext.RegisterOwner("artifacts", ownerRecorder{"artifacts", &order, nil}); err != nil {
		t.Fatal(err)
	}
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	if err := targetContext.Select("http://127.0.0.1:8081"); err != nil {
		t.Fatal(err)
	}
	if strings.Join(order, ",") != "skills,artifacts" {
		t.Fatalf("owner order=%v", order)
	}
}

func TestActivateActivityCalledOnceOnFirstProbeAndNotOnRecheck(t *testing.T) {
	client := &fakeClient{instance: fixedInstance("11111111-1111-4111-8111-111111111111")}
	ids := []ScopeID{"scope-1", "scope-2"}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) { id := ids[0]; ids = ids[1:]; return id, nil },
		time.Now,
	)
	defer targetContext.Close()
	var order []string
	var activated []string
	if err := targetContext.RegisterOwner("activity", ownerRecorder{"activity", &order, &activated}); err != nil {
		t.Fatal(err)
	}
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	if _, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32))); domain != nil {
		t.Fatal(domain)
	}
	if len(activated) != 1 || activated[0] != "activity" {
		t.Fatalf("expected single activation call, got %v", activated)
	}
	if _, domain := targetContext.Recheck(context.Background()); domain != nil {
		t.Fatal(domain)
	}
	if len(activated) != 1 {
		t.Fatalf("recheck should not trigger activation again, got %v", activated)
	}
}

type fakeTimer struct {
	callback func()
	stopped  bool
}

func (timer *fakeTimer) Stop() bool {
	timer.stopped = true
	return true
}

func TestRetryCoordinatorUsesOneTimerAndBoundedSchedule(t *testing.T) {
	failure := &applicationclient.Failure{
		Kind: applicationclient.FailureUnavailable, Category: applicationclient.CategoryConnection, Retryable: true,
	}
	client := &fakeClient{err: failure}
	targetContext, _ := New(func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) { return "scope-1", nil }, time.Now)
	var delays []time.Duration
	var timers []*fakeTimer
	targetContext.timerFactory = func(delay time.Duration, callback func()) timerHandle {
		delays = append(delays, delay)
		timer := &fakeTimer{callback: callback}
		timers = append(timers, timer)
		return timer
	}
	targetContext.jitter = func(delay time.Duration) time.Duration { return delay }
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	_, _ = targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32)))
	if len(delays) != 1 || delays[0] != time.Second {
		t.Fatalf("first retry delays=%v", delays)
	}
	timers[0].callback()
	if len(delays) != 2 || delays[1] != 2*time.Second {
		t.Fatalf("second retry delays=%v", delays)
	}
	_, _ = targetContext.Recheck(context.Background())
	if !timers[1].stopped {
		t.Fatal("manual recheck did not preempt the pending retry")
	}
}

func TestCallerCancellationDoesNotPublishFailureOrScheduleRetry(t *testing.T) {
	client := &fakeClient{err: context.Canceled}
	targetContext, _ := New(
		func(applicationclient.Address) (ProbeClient, error) { return client, nil },
		func() (ScopeID, error) { return "scope-1", nil },
		time.Now,
	)
	var timers int
	targetContext.timerFactory = func(time.Duration, func()) timerHandle {
		timers++
		return &fakeTimer{}
	}
	if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
		t.Fatal(err)
	}
	parent, cancel := context.WithCancel(context.Background())
	cancel()
	_, domain := targetContext.SupplyCredential(
		parent,
		[]byte(strings.Repeat("a", 32)),
	)
	if domain == nil || !errors.Is(domain, context.Canceled) {
		t.Fatalf("caller cancellation was not retained: %#v", domain)
	}
	status := targetContext.Snapshot().Status
	if status.TargetConnection != consolecore.ConnectionUnknown || timers != 0 {
		t.Fatalf("cancellation published failure or retry: status=%#v timers=%d", status, timers)
	}
}

func TestContextFailureMappingCoversNewFailureKinds(t *testing.T) {
	tests := []struct {
		name string
		kind applicationclient.FailureKind
		code consolecore.Code
	}{
		{"invalid_argument", applicationclient.FailureInvalidArgument, consolecore.CodeInvalidArgument},
		{"invalid_cursor", applicationclient.FailureInvalidCursor, consolecore.CodeInvalidCursor},
		{"stale_cursor", applicationclient.FailureStaleCursor, consolecore.CodeStaleCursor},
		{"not_found", applicationclient.FailureNotFound, consolecore.CodeNotFound},
		{"limit_exceeded", applicationclient.FailureLimitExceeded, consolecore.CodeLimitExceeded},
		{"live_monitoring_unavailable", applicationclient.FailureLiveMonitoringUnavailable, consolecore.CodeLiveMonitoringUnavailable},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			client := &fakeClient{err: &applicationclient.Failure{Kind: test.kind}}
			targetContext, _ := New(
				func(applicationclient.Address) (ProbeClient, error) { return client, nil },
				func() (ScopeID, error) { return "scope-1", nil },
				time.Now,
			)
			targetContext.timerFactory = func(time.Duration, func()) timerHandle { return &fakeTimer{} }
			if err := targetContext.Select("http://127.0.0.1:8080"); err != nil {
				t.Fatal(err)
			}
			_, domain := targetContext.SupplyCredential(context.Background(), []byte(strings.Repeat("a", 32)))
			if domain == nil {
				t.Fatalf("expected error for %s", test.name)
			}
			if domain.Code != test.code {
				t.Fatalf("expected %s, got %s", test.code, domain.Code)
			}
			if domain.TargetScopeID != "scope-1" {
				t.Fatalf("expected targetScopeId=scope-1, got %s", domain.TargetScopeID)
			}
		})
	}
}
