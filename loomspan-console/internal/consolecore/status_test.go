package consolecore

import (
	"testing"
	"time"
)

func TestStatusSnapshotRepresentsEveryIndependentTargetFactCombination(t *testing.T) {
	now := time.Date(2026, 7, 27, 0, 0, 0, 0, time.UTC)
	if err := NoTargetStatus(now).Validate(); err != nil {
		t.Fatal(err)
	}
	selected := StatusSnapshot{
		ObservedAt: now, TargetScopeID: "scope-1", TargetSelection: SelectionSelected,
		TargetConnection: ConnectionUnknown, TargetAuthentication: AuthenticationRequired,
		JavaGoCompatibility: CompatibilityNotChecked, RuntimeIdentity: RuntimeNotEstablished,
		LiveMonitoring: LiveUnknown,
	}
	if err := selected.Validate(); err != nil {
		t.Fatal(err)
	}
	selected.RuntimeIdentity = RuntimeEstablished
	if err := selected.Validate(); err == nil {
		t.Fatal("accepted established identity without instance ID")
	}
	selected.InstanceID = "instance-1"
	if err := selected.Validate(); err != nil {
		t.Fatal(err)
	}
}
