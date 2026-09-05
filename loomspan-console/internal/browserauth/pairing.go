package browserauth

import (
	"crypto/rand"
	"fmt"
	"io"
	"sync"
	"time"
)

const (
	PairingLifetime    = 5 * time.Minute
	ManualPairingDelay = 30 * time.Second
)

type Clock func() time.Time

type Pairing struct {
	mu         sync.Mutex
	clock      Clock
	entropy    io.Reader
	current    []byte
	expires    time.Time
	lastManual time.Time
	closed     bool
}

func NewPairing(clock Clock, entropy io.Reader) *Pairing {
	if clock == nil {
		clock = time.Now
	}
	if entropy == nil {
		entropy = rand.Reader
	}
	return &Pairing{clock: clock, entropy: entropy}
}

func (pairing *Pairing) Create(manual bool) (string, error) {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	if pairing.closed {
		return "", fmt.Errorf("pairing is unavailable")
	}
	now := pairing.clock()
	if manual && !pairing.lastManual.IsZero() && now.Sub(pairing.lastManual) < ManualPairingDelay {
		return "", fmt.Errorf("manual pairing is rate limited")
	}
	secret, err := Generate(pairing.entropy)
	if err != nil {
		return "", err
	}
	decoded, _ := decodeSecret(secret)
	pairing.current = decoded
	pairing.expires = now.Add(PairingLifetime)
	if manual {
		pairing.lastManual = now
	}
	return secret, nil
}

func (pairing *Pairing) Consume(candidate string) bool {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	if pairing.closed || pairing.current == nil || !pairing.clock().Before(pairing.expires) {
		pairing.current = nil
		return false
	}
	if !compareSecret(pairing.current, candidate) {
		return false
	}
	pairing.current = nil
	return true
}

func (pairing *Pairing) Close() {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	pairing.closed = true
	pairing.current = nil
}
