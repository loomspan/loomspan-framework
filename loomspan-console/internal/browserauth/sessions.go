package browserauth

import (
	"crypto/rand"
	"crypto/subtle"
	"fmt"
	"io"
	"sync"
	"time"
)

const (
	MaxSessions        = 8
	MaxTabs            = 16
	SessionIdle        = 8 * time.Hour
	DisconnectedTabTTL = 2 * time.Minute
)

type tab struct {
	id          string
	csrf        []byte
	lastSeen    time.Time
	relay       bool
	cancelRelay func()
}

type session struct {
	id         string
	lastActive time.Time
	tabs       map[string]*tab
}

type Registry struct {
	mu       sync.Mutex
	clock    Clock
	entropy  io.Reader
	sessions map[string]*session
	closed   bool
}

type Bootstrap struct {
	SessionID string
	TabID     string
	CSRF      string
}

func NewRegistry(clock Clock, entropy io.Reader) *Registry {
	if clock == nil {
		clock = time.Now
	}
	if entropy == nil {
		entropy = rand.Reader
	}
	return &Registry{clock: clock, entropy: entropy, sessions: make(map[string]*session)}
}

func (registry *Registry) CreateSession() (string, error) {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.expireLocked()
	if registry.closed {
		return "", fmt.Errorf("browser sessions are unavailable")
	}
	if len(registry.sessions) >= MaxSessions {
		return "", fmt.Errorf("browser session limit reached")
	}
	id, err := Generate(registry.entropy)
	if err != nil {
		return "", err
	}
	registry.sessions[id] = &session{id: id, lastActive: registry.clock(), tabs: make(map[string]*tab)}
	return id, nil
}

func (registry *Registry) Authenticate(id string) bool {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.expireLocked()
	session := registry.findSessionLocked(id)
	if registry.closed || session == nil {
		return false
	}
	session.lastActive = registry.clock()
	return true
}

func (registry *Registry) Bootstrap(sessionID, requestedTab string) (Bootstrap, error) {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.expireLocked()
	current := registry.findSessionLocked(sessionID)
	if registry.closed || current == nil {
		return Bootstrap{}, fmt.Errorf("browser session is invalid")
	}
	now := registry.clock()
	current.lastActive = now
	selected := current.tabs[requestedTab]
	if selected == nil {
		if registry.tabCountLocked() >= MaxTabs {
			return Bootstrap{}, fmt.Errorf("browser tab limit reached")
		}
		id, err := Generate(registry.entropy)
		if err != nil {
			return Bootstrap{}, err
		}
		selected = &tab{id: id}
		current.tabs[id] = selected
	}
	token, err := Generate(registry.entropy)
	if err != nil {
		return Bootstrap{}, err
	}
	selected.csrf, _ = decodeSecret(token)
	selected.lastSeen = now
	return Bootstrap{SessionID: sessionID, TabID: selected.id, CSRF: token}, nil
}

func (registry *Registry) ValidateCSRF(sessionID, tabID, candidate string) bool {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.expireLocked()
	current := registry.findSessionLocked(sessionID)
	if current == nil {
		return false
	}
	selected := current.tabs[tabID]
	if selected == nil || !compareSecret(selected.csrf, candidate) {
		return false
	}
	now := registry.clock()
	current.lastActive = now
	selected.lastSeen = now
	return true
}

func (registry *Registry) ReleaseTab(sessionID, tabID string) {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	if current := registry.findSessionLocked(sessionID); current != nil {
		if selected := current.tabs[tabID]; selected != nil && selected.cancelRelay != nil {
			selected.cancelRelay()
		}
		delete(current.tabs, tabID)
	}
}

func (registry *Registry) AdmitRelay(sessionID, tabID string, cancel func()) (func(), error) {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.expireLocked()
	current := registry.findSessionLocked(sessionID)
	if current == nil || current.tabs[tabID] == nil || current.tabs[tabID].relay {
		return nil, fmt.Errorf("relay admission rejected")
	}
	current.tabs[tabID].relay = true
	current.tabs[tabID].cancelRelay = cancel
	current.lastActive = registry.clock()
	return func() {
		registry.mu.Lock()
		defer registry.mu.Unlock()
		if active := registry.findSessionLocked(sessionID); active != nil && active.tabs[tabID] != nil {
			active.tabs[tabID].relay = false
			active.tabs[tabID].cancelRelay = nil
			active.tabs[tabID].lastSeen = registry.clock()
		}
	}, nil
}

func (registry *Registry) Close() {
	registry.mu.Lock()
	defer registry.mu.Unlock()
	registry.closed = true
	for _, current := range registry.sessions {
		for _, selected := range current.tabs {
			if selected.cancelRelay != nil {
				selected.cancelRelay()
			}
		}
	}
	clear(registry.sessions)
}

func (registry *Registry) findSessionLocked(candidate string) *session {
	decoded, ok := decodeSecret(candidate)
	if !ok {
		return nil
	}
	for _, current := range registry.sessions {
		expected, ok := decodeSecret(current.id)
		if ok && subtle.ConstantTimeCompare(expected, decoded) == 1 {
			return current
		}
	}
	return nil
}

func (registry *Registry) tabCountLocked() int {
	count := 0
	for _, current := range registry.sessions {
		count += len(current.tabs)
	}
	return count
}

func (registry *Registry) expireLocked() {
	now := registry.clock()
	for id, current := range registry.sessions {
		for _, selected := range current.tabs {
			if selected.relay {
				current.lastActive = now
				break
			}
		}
		if now.Sub(current.lastActive) >= SessionIdle {
			for _, selected := range current.tabs {
				if selected.cancelRelay != nil {
					selected.cancelRelay()
				}
			}
			delete(registry.sessions, id)
			continue
		}
		for tabID, selected := range current.tabs {
			if !selected.relay && now.Sub(selected.lastSeen) >= DisconnectedTabTTL {
				if selected.cancelRelay != nil {
					selected.cancelRelay()
				}
				delete(current.tabs, tabID)
			}
		}
	}
}
