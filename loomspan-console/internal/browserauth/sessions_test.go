package browserauth

import (
	"bytes"
	"testing"
	"time"
)

func deterministicEntropy() *bytes.Reader {
	data := make([]byte, 32*128)
	for index := range data {
		data[index] = byte(index/32 + 1)
	}
	return bytes.NewReader(data)
}

func TestRegistryAdmitsEightSessionsAndRejectsNinthWithoutEviction(t *testing.T) {
	registry := NewRegistry(nil, deterministicEntropy())
	var first string
	for index := 0; index < MaxSessions; index++ {
		id, err := registry.CreateSession()
		if err != nil {
			t.Fatal(err)
		}
		if index == 0 {
			first = id
		}
	}
	if _, err := registry.CreateSession(); err == nil {
		t.Fatal("ninth session admitted")
	}
	if !registry.Authenticate(first) {
		t.Fatal("existing session was evicted")
	}
}

func TestBootstrapRotatesOnlyRequestingTabsToken(t *testing.T) {
	registry := NewRegistry(nil, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	first, _ := registry.Bootstrap(sessionID, "")
	second, _ := registry.Bootstrap(sessionID, "")
	rotated, _ := registry.Bootstrap(sessionID, first.TabID)
	if registry.ValidateCSRF(sessionID, first.TabID, first.CSRF) {
		t.Fatal("stale token accepted")
	}
	if !registry.ValidateCSRF(sessionID, first.TabID, rotated.CSRF) {
		t.Fatal("rotated token rejected")
	}
	if !registry.ValidateCSRF(sessionID, second.TabID, second.CSRF) {
		t.Fatal("other tab token was invalidated")
	}
}

func TestSessionExpiresAfterEightIdleHours(t *testing.T) {
	now := time.Unix(100, 0)
	registry := NewRegistry(func() time.Time { return now }, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	now = now.Add(SessionIdle)
	if registry.Authenticate(sessionID) {
		t.Fatal("expired session authenticated")
	}
}

func TestHeartbeatKeepsTabRegisteredAndDisconnectedTabExpires(t *testing.T) {
	now := time.Unix(100, 0)
	registry := NewRegistry(func() time.Time { return now }, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	active, _ := registry.Bootstrap(sessionID, "")
	now = now.Add(DisconnectedTabTTL - time.Second)
	if !registry.ValidateCSRF(sessionID, active.TabID, active.CSRF) {
		t.Fatal("heartbeat rejected before tab expiry")
	}
	now = now.Add(DisconnectedTabTTL - time.Second)
	if !registry.ValidateCSRF(sessionID, active.TabID, active.CSRF) {
		t.Fatal("heartbeat did not extend tab registration")
	}

	disconnected, _ := registry.Bootstrap(sessionID, "")
	now = now.Add(DisconnectedTabTTL)
	if registry.ValidateCSRF(sessionID, disconnected.TabID, disconnected.CSRF) {
		t.Fatal("disconnected tab remained registered after expiry")
	}
}

func TestSessionAuthenticationRejectsNonCanonicalCredentialShapes(t *testing.T) {
	registry := NewRegistry(nil, deterministicEntropy())
	sessionID, err := registry.CreateSession()
	if err != nil {
		t.Fatal(err)
	}
	for _, candidate := range []string{
		"",
		sessionID[:len(sessionID)-1],
		sessionID + "=",
		"not-base64!",
	} {
		if registry.Authenticate(candidate) {
			t.Fatalf("noncanonical session credential authenticated: %q", candidate)
		}
	}
	if !registry.Authenticate(sessionID) {
		t.Fatal("canonical session credential was rejected")
	}
}

func TestRegistryAdmitsSixteenTabsAcrossSessionsAndRejectsSeventeenth(t *testing.T) {
	registry := NewRegistry(nil, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	for index := 0; index < MaxTabs; index++ {
		if _, err := registry.Bootstrap(sessionID, ""); err != nil {
			t.Fatalf("tab %d: %v", index, err)
		}
	}
	if _, err := registry.Bootstrap(sessionID, ""); err == nil {
		t.Fatal("seventeenth tab admitted")
	}
}

func TestActiveRelayKeepsSessionAliveAndOneRelayPerTabIsEnforced(t *testing.T) {
	now := time.Unix(100, 0)
	registry := NewRegistry(func() time.Time { return now }, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	bootstrap, _ := registry.Bootstrap(sessionID, "")
	release, err := registry.AdmitRelay(sessionID, bootstrap.TabID, func() {})
	if err != nil {
		t.Fatal(err)
	}
	if _, err := registry.AdmitRelay(sessionID, bootstrap.TabID, func() {}); err == nil {
		t.Fatal("second relay admitted")
	}
	now = now.Add(SessionIdle)
	if !registry.Authenticate(sessionID) {
		t.Fatal("live relay did not keep session active")
	}
	release()
}

func TestReleaseTabCancelsActiveRelay(t *testing.T) {
	registry := NewRegistry(nil, deterministicEntropy())
	sessionID, _ := registry.CreateSession()
	bootstrap, _ := registry.Bootstrap(sessionID, "")
	cancelled := make(chan struct{})
	_, err := registry.AdmitRelay(sessionID, bootstrap.TabID, func() { close(cancelled) })
	if err != nil {
		t.Fatal(err)
	}
	registry.ReleaseTab(sessionID, bootstrap.TabID)
	select {
	case <-cancelled:
	case <-time.After(time.Second):
		t.Fatal("tab release did not cancel its relay")
	}
}
