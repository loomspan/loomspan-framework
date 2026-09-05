package consolecore

import (
	"errors"
	"fmt"
	"strings"
	"testing"
)

func TestDomainErrorsHaveStableCodesTypedBoundedDetailsAndSafeFormatting(t *testing.T) {
	secret := "LOOMSPAN_" + "TEST_APPLICATION_KEY_DO_NOT_LEAK"
	err := NewError(CodeTargetUnavailable, "The selected target is unavailable.", "scope-1",
		Details{TransportCategory: "timeout"}, errors.New(secret))
	for _, rendered := range []string{err.Error(), fmt.Sprintf("%v", err), fmt.Sprintf("%+v", err), fmt.Sprintf("%#v", err)} {
		if strings.Contains(rendered, secret) {
			t.Fatal("domain error formatting exposed its internal cause")
		}
	}
	if err.Code != CodeTargetUnavailable || err.Details.TransportCategory != "timeout" {
		t.Fatalf("unexpected error: %#v", err)
	}
}
