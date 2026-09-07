package browserauth

import (
	"context"
	"crypto/rand"
	"fmt"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
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
	diagnostic context.Context
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
	return &Pairing{diagnostic: context.Background(), clock: clock, entropy: entropy}
}

func (pairing *Pairing) Create(ctx context.Context, manual bool) (string, error) {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	if pairing.closed {
		return "", diagnostics.Annotate(fmt.Errorf("pairing is unavailable"), diagnostics.Facts{Expected: true})
	}
	now := pairing.clock()
	if manual && !pairing.lastManual.IsZero() && now.Sub(pairing.lastManual) < ManualPairingDelay {
		return "", diagnostics.Annotate(fmt.Errorf("manual pairing is rate limited"), diagnostics.Facts{Expected: true})
	}
	secret, err := Generate(pairing.entropy)
	if err != nil {
		return "", diagnostics.Annotate(err, diagnostics.Facts{Cause: "entropy"})
	}
	decoded, _ := decodeSecret(secret)
	pairing.diagnostic = diagnostics.Detach(context.Background(), ctx, "browser.pairing")
	pairing.current = decoded
	pairing.expires = now.Add(PairingLifetime)
	if manual {
		pairing.lastManual = now
	}
	return secret, nil
}

func (pairing *Pairing) Consume(ctx context.Context, candidate string) bool {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	if pairing.closed || pairing.current == nil || !pairing.clock().Before(pairing.expires) {
		if pairing.current != nil {
			diagnostics.Event(pairing.diagnostic, "expired", "")
		}
		pairing.current = nil
		return false
	}
	if !compareSecret(pairing.current, candidate) {
		return false
	}
	pairing.current = nil
	diagnostics.Event(diagnostics.Operation(ctx, "browser.pairing"), "paired", "")
	return true
}

func (pairing *Pairing) Close() {
	pairing.mu.Lock()
	defer pairing.mu.Unlock()
	if pairing.current != nil {
		diagnostics.Event(pairing.diagnostic, "closed", "")
	}
	pairing.closed = true
	pairing.current = nil
}
