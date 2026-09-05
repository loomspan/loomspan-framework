package lifecycle

import (
	"context"
	"errors"
	"testing"
)

func TestCoordinatorPreservesFirstFatalCauseAndNotifiesOnce(t *testing.T) {
	coordinator := New(context.Background())
	first := errors.New("first")
	coordinator.Fatal(first)
	coordinator.Fatal(errors.New("second"))
	if !errors.Is(coordinator.Cause(), first) {
		t.Fatalf("cause=%v", coordinator.Cause())
	}
}
