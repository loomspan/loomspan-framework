package browserauth

import (
	"bytes"
	"context"
	"encoding/base64"
	"sync"
	"testing"
	"time"
)

func TestPairingChallengeUsesExactlyThirtyTwoRandomBytesAndBase64URL(t *testing.T) {
	pairing := NewPairing(nil, bytes.NewReader(bytes.Repeat([]byte{0xa5}, 32)))
	secret, err := pairing.Create(context.Background(), false)
	if err != nil {
		t.Fatal(err)
	}
	decoded, err := base64.RawURLEncoding.DecodeString(secret)
	if err != nil || len(decoded) != 32 || len(secret) != 43 {
		t.Fatalf("secret shape length=%d decoded=%d err=%v", len(secret), len(decoded), err)
	}
}

func TestPairingChallengeCanBeConsumedExactlyOnce(t *testing.T) {
	pairing := NewPairing(nil, bytes.NewReader(bytes.Repeat([]byte{1}, 32)))
	secret, _ := pairing.Create(context.Background(), false)
	if !pairing.Consume(context.Background(), secret) || pairing.Consume(context.Background(), secret) {
		t.Fatal("pairing challenge was not exactly once")
	}
}

func TestPairingChallengeReplacementExpiryAndShutdownInvalidate(t *testing.T) {
	now := time.Unix(100, 0)
	entropy := bytes.NewReader(append(bytes.Repeat([]byte{1}, 32), bytes.Repeat([]byte{2}, 32)...))
	pairing := NewPairing(func() time.Time { return now }, entropy)
	first, _ := pairing.Create(context.Background(), false)
	second, _ := pairing.Create(context.Background(), false)
	if pairing.Consume(context.Background(), first) || !pairing.Consume(context.Background(), second) {
		t.Fatal("replacement semantics failed")
	}
	thirdReader := bytes.NewReader(bytes.Repeat([]byte{3}, 32))
	pairing = NewPairing(func() time.Time { return now }, thirdReader)
	third, _ := pairing.Create(context.Background(), false)
	now = now.Add(PairingLifetime)
	if pairing.Consume(context.Background(), third) {
		t.Fatal("expired challenge accepted")
	}
}

func TestConcurrentPairingExchangeAdmitsOne(t *testing.T) {
	pairing := NewPairing(nil, bytes.NewReader(bytes.Repeat([]byte{4}, 32)))
	secret, _ := pairing.Create(context.Background(), false)
	var wait sync.WaitGroup
	wait.Add(2)
	results := make(chan bool, 2)
	for range 2 {
		go func() {
			defer wait.Done()
			results <- pairing.Consume(context.Background(), secret)
		}()
	}
	wait.Wait()
	close(results)
	successes := 0
	for result := range results {
		if result {
			successes++
		}
	}
	if successes != 1 {
		t.Fatalf("successes=%d", successes)
	}
}

func TestManualChallengeRateLimitsThirtySeconds(t *testing.T) {
	now := time.Unix(100, 0)
	pairing := NewPairing(func() time.Time { return now }, bytes.NewReader(bytes.Repeat([]byte{5}, 64)))
	if _, err := pairing.Create(context.Background(), true); err != nil {
		t.Fatal(err)
	}
	if _, err := pairing.Create(context.Background(), true); err == nil {
		t.Fatal("manual challenge was not rate limited")
	}
	now = now.Add(ManualPairingDelay)
	if _, err := pairing.Create(context.Background(), true); err != nil {
		t.Fatal(err)
	}
}
