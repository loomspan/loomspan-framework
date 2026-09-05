package observability

import (
	"encoding/json"
	"fmt"
	"time"
)

type InstanceStatus struct {
	TargetScopeID               string    `json:"targetScopeId"`
	InstanceID                  string    `json:"instanceId"`
	ConsoleCompatibilityVersion string    `json:"consoleCompatibilityVersion"`
	ObservedAt                  time.Time `json:"observedAt"`
	LiveMonitoringAvailable     bool      `json:"liveMonitoringAvailable"`
	RegisteredSkillCount        int       `json:"registeredSkillCount"`
	ActiveExecutionCount        int       `json:"activeExecutionCount"`
	CatalogedTraceCount         int       `json:"catalogedTraceCount"`
	TracePersistencePolicy      string    `json:"tracePersistencePolicy"`
	CompletionGraceTtl          string    `json:"completionGraceTtl"`
	TraceCatalogMetadataTtl     string    `json:"traceCatalogMetadataTtl"`
}

type SkillSummary struct {
	RegisteredName string `json:"registeredName"`
	Source         string `json:"source"`
	SourcePath     string `json:"sourcePath,omitempty"`
	BeanName       string `json:"beanName,omitempty"`
	Method         string `json:"method,omitempty"`
	Href           string `json:"href,omitempty"`
}

type SkillDetail struct {
	TargetScopeID  string `json:"targetScopeId"`
	RegisteredName string `json:"registeredName"`
	Source         string `json:"source"`
	SourcePath     string `json:"sourcePath,omitempty"`
	BeanName       string `json:"beanName,omitempty"`
	Method         string `json:"method,omitempty"`
	Yaml           string `json:"yaml,omitempty"`
}

type FramePathEntry struct {
	FrameID   string `json:"frameId"`
	FrameType string `json:"frameType"`
	Route     string `json:"route"`
}

type ActiveBranch struct {
	PlanID               *string          `json:"planId"`
	TaskID               *string          `json:"taskId"`
	StepNumber           *int             `json:"stepNumber"`
	ParallelGroup        *string          `json:"parallelGroup"`
	EffectiveConcurrency *bool            `json:"effectiveConcurrency"`
	Path                 []FramePathEntry `json:"path"`
}

type Usage struct {
	SkillInvocations          int `json:"skillInvocations"`
	ToolInvocations           int `json:"toolInvocations"`
	LinterRetries             int `json:"linterRetries"`
	ModelCalls                int `json:"modelCalls"`
	ProviderAttempts          int `json:"providerAttempts"`
	PromptUnits               int `json:"promptUnits"`
	CompletionUnits           int `json:"completionUnits"`
	UsageUnits                int `json:"usageUnits"`
	ExactModelResponses       int `json:"exactModelResponses"`
	HeuristicModelResponses   int `json:"heuristicModelResponses"`
	UnavailableModelResponses int `json:"unavailableModelResponses"`
}

type ConfiguredLimits struct {
	MaxSkillInvocations int `json:"maxSkillInvocations"`
	MaxToolInvocations  int `json:"maxToolInvocations"`
	MaxLinterRetries    int `json:"maxLinterRetries"`
	MaxModelCalls       int `json:"maxModelCalls"`
	MaxProviderAttempts int `json:"maxProviderAttempts"`
	MaxUsageUnits       int `json:"maxUsageUnits"`
}

type ActiveExecution struct {
	TargetScopeID         string           `json:"targetScopeId"`
	SessionID             string           `json:"sessionId"`
	TraceID               string           `json:"traceId"`
	LastCanonicalSequence int              `json:"lastCanonicalSequence"`
	StartedAt             time.Time        `json:"startedAt"`
	UpdatedAt             time.Time        `json:"updatedAt"`
	ElapsedMillis         int64            `json:"elapsedMillis"`
	EntrySkill            string           `json:"entrySkill"`
	Status                string           `json:"status"`
	Phase                 string           `json:"phase"`
	Summary               string           `json:"summary"`
	ActiveBranches        []ActiveBranch   `json:"activeBranches"`
	Usage                 Usage            `json:"usage"`
	ConfiguredLimits      ConfiguredLimits `json:"configuredLimits"`
}

type Trace struct {
	TargetScopeID             string    `json:"targetScopeId"`
	TraceID                   string    `json:"traceId"`
	SessionID                 string    `json:"sessionId"`
	EntrySkill                string    `json:"entrySkill"`
	Outcome                   string    `json:"outcome"`
	FinalizedAt               time.Time `json:"finalizedAt"`
	SizeBytes                 int64     `json:"sizeBytes"`
	PersistencePolicy         string    `json:"persistencePolicy"`
	ApplicationTraceExpiresAt time.Time `json:"applicationTraceExpiresAt"`
	LocalAvailable            bool      `json:"localAvailable"`
	ArtifactHandle            string    `json:"artifactHandle,omitempty"`
	ApplicationAvailability   string    `json:"applicationAvailability,omitempty"`
}

type Page[T any] struct {
	TargetScopeID string    `json:"targetScopeId"`
	Items         []T       `json:"items"`
	HasMore       bool      `json:"hasMore"`
	NextCursor    *string   `json:"nextCursor"`
	ObservedAt    time.Time `json:"observedAt"`
}

type ActivePage struct {
	Page[ActiveExecution]
	ResumeCursor *string `json:"resumeCursor"`
}

type ListRequest struct {
	Cursor   string
	PageSize int
}

// Reject source-inapplicable fields even when an upstream sends an empty value.
func validateSkillWireFields(data []byte, source string, detail bool) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}
	forbidden := []string{"beanName", "method"}
	if source == "JAVA" {
		forbidden = []string{"sourcePath", "yaml"}
	}
	if !detail {
		forbidden = append(forbidden, "yaml")
	}
	for _, name := range forbidden {
		if value, present := fields[name]; present && string(value) != "null" {
			return fmt.Errorf("%s is not applicable to this skill source", name)
		}
	}
	return nil
}

func (skill *SkillSummary) UnmarshalJSON(data []byte) error {
	type wire SkillSummary
	var decoded wire
	if err := json.Unmarshal(data, &decoded); err != nil {
		return err
	}
	candidate := SkillSummary(decoded)
	if err := validateSkillSummary(candidate); err != nil {
		return err
	}
	if err := validateSkillWireFields(data, candidate.Source, false); err != nil {
		return err
	}
	*skill = candidate
	return nil
}

func (skill *SkillDetail) UnmarshalJSON(data []byte) error {
	type wire SkillDetail
	var decoded wire
	if err := json.Unmarshal(data, &decoded); err != nil {
		return err
	}
	candidate := SkillDetail(decoded)
	if err := validateSkillDetail(candidate, candidate.RegisteredName); err != nil {
		return err
	}
	if err := validateSkillWireFields(data, candidate.Source, true); err != nil {
		return err
	}
	*skill = candidate
	return nil
}
