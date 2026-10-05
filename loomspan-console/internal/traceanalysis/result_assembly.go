package traceanalysis

import (
	"encoding/json"
	"strings"
)

// ResultAssembly records the producer's validated output ownership decision.
// It contains source identities, never copies of selected source values.
type ResultAssembly struct {
	SkillName                 string                    `json:"skillName"`
	OwningMissionFrameID      string                    `json:"owningMissionFrameId"`
	PlanID                    string                    `json:"planId,omitempty"`
	ModelContributionRequired bool                      `json:"modelContributionRequired"`
	OutputBindings            []OutputBindingProvenance `json:"outputBindings"`
}

type OutputBindingProvenance struct {
	Destination          string `json:"destination"`
	SourceKind           string `json:"sourceKind"`
	SourcePath           string `json:"sourcePath"`
	ParentMissionFrameID string `json:"parentMissionFrameId"`
	SourceTaskID         string `json:"sourceTaskId,omitempty"`
	SourceSkill          string `json:"sourceSkill,omitempty"`
}

func decodeResultAssembly(raw json.RawMessage) (*ResultAssembly, bool) {
	fields, valid := decodeUniqueObject(raw)
	if !valid {
		return nil, false
	}
	for field := range fields {
		switch field {
		case "skillName", "owningMissionFrameId", "planId", "modelContributionRequired", "outputBindings", "recordedAt":
		default:
			return nil, false
		}
	}
	if string(fields["modelContributionRequired"]) != "true" && string(fields["modelContributionRequired"]) != "false" {
		return nil, false
	}
	var result ResultAssembly
	if json.Unmarshal(raw, &result) != nil || strings.TrimSpace(result.SkillName) == "" || strings.TrimSpace(result.OwningMissionFrameID) == "" || len(result.OutputBindings) == 0 {
		return nil, false
	}
	if plan, present := fields["planId"]; present && (string(plan) == "null" || strings.TrimSpace(result.PlanID) == "") {
		return nil, false
	}
	var entries []json.RawMessage
	if json.Unmarshal(fields["outputBindings"], &entries) != nil {
		return nil, false
	}
	destinations := map[string]bool{}
	for i, entry := range entries {
		fields, valid := decodeUniqueObject(entry)
		if !valid {
			return nil, false
		}
		for field := range fields {
			switch field {
			case "destination", "sourceKind", "sourcePath", "parentMissionFrameId", "sourceTaskId", "sourceSkill":
			default:
				return nil, false
			}
		}
		binding := result.OutputBindings[i]
		if !validObjectPointer(binding.Destination, false) || !validObjectPointer(binding.SourcePath, true) || binding.ParentMissionFrameID != result.OwningMissionFrameID {
			return nil, false
		}
		var path string
		if string(fields["sourcePath"]) == "null" || json.Unmarshal(fields["sourcePath"], &path) != nil {
			return nil, false
		}
		for destination := range destinations {
			if destination == binding.Destination || strings.HasPrefix(destination, binding.Destination+"/") || strings.HasPrefix(binding.Destination, destination+"/") {
				return nil, false
			}
		}
		destinations[binding.Destination] = true
		switch binding.SourceKind {
		case "input":
			if _, exists := fields["sourceTaskId"]; exists {
				return nil, false
			}
			if _, exists := fields["sourceSkill"]; exists {
				return nil, false
			}
		case "child_result":
			if result.PlanID == "" || strings.TrimSpace(binding.SourceTaskID) == "" || strings.TrimSpace(binding.SourceSkill) == "" {
				return nil, false
			}
		default:
			return nil, false
		}
	}
	return &result, true
}

func validObjectPointer(value string, root bool) bool {
	if value == "" {
		return root
	}
	if !strings.HasPrefix(value, "/") {
		return false
	}
	for i := 0; i < len(value); i++ {
		if value[i] == '~' {
			i++
			if i >= len(value) || (value[i] != '0' && value[i] != '1') {
				return false
			}
		}
	}
	return true
}
