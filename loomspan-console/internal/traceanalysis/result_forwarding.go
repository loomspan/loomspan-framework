package traceanalysis

import (
	"encoding/json"
	"strings"
)

func decodeResultForwarding(raw json.RawMessage) (*ResultForwarding, bool) {
	_, valid := decodeUniqueObject(raw)
	if !valid {
		return nil, false
	}
	var result ResultForwarding
	if err := json.Unmarshal(raw, &result); err != nil {
		return nil, false
	}
	for _, value := range []string{result.SkillName, result.PlanID, result.LinkedTaskID, result.CapabilityName} {
		if strings.TrimSpace(value) == "" {
			return nil, false
		}
	}
	return &result, true
}
