package consolecore

import (
	"fmt"
	"time"
)

type Selection string
type Connection string
type Authentication string
type Compatibility string
type RuntimeIdentity string
type LiveMonitoring string

const (
	SelectionNone     Selection = "NONE"
	SelectionSelected Selection = "SELECTED"

	ConnectionNotApplicable Connection = "NOT_APPLICABLE"
	ConnectionUnknown       Connection = "UNKNOWN"
	ConnectionReachable     Connection = "REACHABLE"
	ConnectionUnavailable   Connection = "UNAVAILABLE"

	AuthenticationNotApplicable Authentication = "NOT_APPLICABLE"
	AuthenticationUnknown       Authentication = "UNKNOWN"
	AuthenticationRequired      Authentication = "REQUIRED"
	AuthenticationEstablished   Authentication = "ESTABLISHED"
	AuthenticationBlocked       Authentication = "BLOCKED"

	CompatibilityNotApplicable Compatibility = "NOT_APPLICABLE"
	CompatibilityNotChecked    Compatibility = "NOT_CHECKED"
	CompatibilityCompatible    Compatibility = "COMPATIBLE"
	CompatibilityIncompatible  Compatibility = "INCOMPATIBLE"

	RuntimeNotApplicable  RuntimeIdentity = "NOT_APPLICABLE"
	RuntimeNotEstablished RuntimeIdentity = "NOT_ESTABLISHED"
	RuntimeEstablished    RuntimeIdentity = "ESTABLISHED"

	LiveNotApplicable LiveMonitoring = "NOT_APPLICABLE"
	LiveUnknown       LiveMonitoring = "UNKNOWN"
	LiveAvailable     LiveMonitoring = "AVAILABLE"
	LiveUnavailable   LiveMonitoring = "UNAVAILABLE"
)

type StatusSnapshot struct {
	ObservedAt           time.Time       `json:"observedAt"`
	TargetScopeID        string          `json:"targetScopeId,omitempty"`
	TargetSelection      Selection       `json:"targetSelection"`
	TargetConnection     Connection      `json:"targetConnection"`
	TargetAuthentication Authentication  `json:"targetAuthentication"`
	JavaGoCompatibility  Compatibility   `json:"javaGoCompatibility"`
	RuntimeIdentity      RuntimeIdentity `json:"runtimeIdentity"`
	InstanceID           string          `json:"instanceId,omitempty"`
	LiveMonitoring       LiveMonitoring  `json:"liveMonitoring"`
}

func NoTargetStatus(observedAt time.Time) StatusSnapshot {
	return StatusSnapshot{
		ObservedAt: observedAt, TargetSelection: SelectionNone,
		TargetConnection:     ConnectionNotApplicable,
		TargetAuthentication: AuthenticationNotApplicable,
		JavaGoCompatibility:  CompatibilityNotApplicable,
		RuntimeIdentity:      RuntimeNotApplicable, LiveMonitoring: LiveNotApplicable,
	}
}

func (snapshot StatusSnapshot) Validate() error {
	if snapshot.ObservedAt.IsZero() {
		return fmt.Errorf("status observation time is required")
	}
	if snapshot.TargetSelection == SelectionNone {
		if snapshot.TargetScopeID != "" || snapshot.InstanceID != "" ||
			snapshot.TargetConnection != ConnectionNotApplicable ||
			snapshot.TargetAuthentication != AuthenticationNotApplicable ||
			snapshot.JavaGoCompatibility != CompatibilityNotApplicable ||
			snapshot.RuntimeIdentity != RuntimeNotApplicable ||
			snapshot.LiveMonitoring != LiveNotApplicable {
			return fmt.Errorf("no-target status contains target facts")
		}
		return nil
	}
	if snapshot.TargetSelection != SelectionSelected || snapshot.TargetScopeID == "" {
		return fmt.Errorf("selected target status requires a scope")
	}
	if (snapshot.RuntimeIdentity == RuntimeEstablished) != (snapshot.InstanceID != "") {
		return fmt.Errorf("runtime identity and instance ID must be established together")
	}
	return nil
}
