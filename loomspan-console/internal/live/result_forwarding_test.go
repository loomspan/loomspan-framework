package live

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestForwardedActivityRequiresSelectedTaskIdentity(t *testing.T) {
	activity := makeActivity("1", "session", string(KindResultForwarded))
	fields := map[string]any{"skillName": "parent", "planId": "plan", "linkedTaskId": "task", "capabilityName": "child"}
	activity.Details, _ = json.Marshal(fields)
	if err := activity.Validate(); err != nil {
		t.Fatal(err)
	}
	for _, field := range []string{"skillName", "planId", "linkedTaskId", "capabilityName"} {
		for _, value := range []any{nil, "", 42, []string{"x"}} {
			original := fields[field]
			fields[field] = value
			activity.Details, _ = json.Marshal(fields)
			if activity.Validate() == nil {
				t.Fatalf("accepted %s=%#v", field, value)
			}
			fields[field] = original
		}
	}
}

func TestForwardedActivityAcceptsBoundedTaskIdentityPreview(t *testing.T) {
	activity := makeActivity("1", "session", string(KindResultForwarded))
	// The Java projector keeps the first 256 code points of scalar details.
	// A valid nonblank task ID can have its nonblank suffix beyond that bound.
	activity.Details, _ = json.Marshal(map[string]string{
		"skillName": "parent", "planId": "plan", "capabilityName": "child",
		"linkedTaskId": strings.Repeat(" ", 256),
	})
	if err := activity.Validate(); err != nil {
		t.Fatalf("rejected bounded producer preview: %v", err)
	}
}

func TestAssemblyActivityRequiresOwnerAndContributionDecision(t *testing.T) {
	activity := makeActivity("1", "session", string(KindResultAssembled))
	activity.Details = json.RawMessage(`{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":false}`)
	if err := activity.Validate(); err != nil {
		t.Fatal(err)
	}
	for _, raw := range []string{`{"skillName":"parent","modelContributionRequired":false}`, `{"skillName":"parent","owningMissionFrameId":"owner","modelContributionRequired":null}`} {
		activity.Details = json.RawMessage(raw)
		if activity.Validate() == nil {
			t.Fatalf("accepted malformed assembly preview %s", raw)
		}
	}
}
