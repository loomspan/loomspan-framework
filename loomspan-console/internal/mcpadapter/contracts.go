package mcpadapter

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/google/jsonschema-go/jsonschema"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/live"
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/observability"
	"github.com/modelcontextprotocol/go-sdk/mcp"
)

const (
	maxMCPPageSize         = 64
	defaultMCPListPageSize = 16
)

type toolEnvelope[T any] struct {
	Result *T              `json:"result,omitempty"`
	Error  *domainErrorDTO `json:"error,omitempty"`
}

type domainErrorDTO struct {
	Code    consolecore.Code `json:"code"`
	Message string           `json:"message"`
	Details errorDetailsDTO  `json:"details"`
}

type errorDetailsDTO struct {
	ExpectedCompatibilityVersion string `json:"expectedCompatibilityVersion,omitempty"`
	ObservedCompatibilityVersion string `json:"observedCompatibilityVersion,omitempty"`
	LimitName                    string `json:"limitName,omitempty"`
	LimitValue                   int64  `json:"limitValue,omitempty"`
	RawDownloadAvailable         *bool  `json:"rawDownloadAvailable,omitempty"`
}

type skillSummaryDTO struct {
	RegisteredName string `json:"registeredName"`
	Source         string `json:"source"`
	SourcePath     string `json:"sourcePath,omitempty"`
	BeanName       string `json:"beanName,omitempty"`
	Method         string `json:"method,omitempty"`
}

type skillDetailDTO struct {
	RegisteredName string `json:"registeredName"`
	Source         string `json:"source"`
	SourcePath     string `json:"sourcePath,omitempty"`
	BeanName       string `json:"beanName,omitempty"`
	Method         string `json:"method,omitempty"`
	YAML           string `json:"yaml,omitempty"`
}

type skillListResult struct {
	ObservedAt   time.Time         `json:"observedAt"`
	Items        []skillSummaryDTO `json:"items"`
	HasMore      bool              `json:"hasMore"`
	Continuation string            `json:"continuation,omitempty"`
}

type skillDetailResult struct {
	ObservedAt time.Time      `json:"observedAt"`
	Skill      skillDetailDTO `json:"skill"`
}

type framePathDTO struct {
	FrameID   string `json:"frameId"`
	FrameType string `json:"frameType"`
	Route     string `json:"route"`
}

type executionListItemDTO struct {
	SessionID         string    `json:"sessionId"`
	TraceID           string    `json:"traceId"`
	Status            string    `json:"status"`
	Phase             string    `json:"phase"`
	UpdatedAt         time.Time `json:"updatedAt"`
	ActiveBranchCount int       `json:"activeBranchCount"`
}

type activeBranchDTO struct {
	PlanID               *string        `json:"planId"`
	TaskID               *string        `json:"taskId"`
	StepNumber           *int           `json:"stepNumber"`
	ParallelGroup        *string        `json:"parallelGroup"`
	EffectiveConcurrency *bool          `json:"effectiveConcurrency"`
	Path                 []framePathDTO `json:"path"`
}

type executionDetailDTO struct {
	SessionID             string                         `json:"sessionId"`
	TraceID               string                         `json:"traceId"`
	LastCanonicalSequence int                            `json:"lastCanonicalSequence"`
	StartedAt             time.Time                      `json:"startedAt"`
	UpdatedAt             time.Time                      `json:"updatedAt"`
	ElapsedMillis         int64                          `json:"elapsedMillis"`
	EntrySkill            string                         `json:"entrySkill"`
	Status                string                         `json:"status"`
	Phase                 string                         `json:"phase"`
	ActiveBranches        []activeBranchDTO              `json:"activeBranches"`
	Usage                 observability.Usage            `json:"usage"`
	ConfiguredLimits      observability.ConfiguredLimits `json:"configuredLimits"`
}

type executionListResult struct {
	ObservedAt   time.Time              `json:"observedAt"`
	Items        []executionListItemDTO `json:"items"`
	HasMore      bool                   `json:"hasMore"`
	Continuation string                 `json:"continuation,omitempty"`
}

type executionDetailResult struct {
	ObservedAt time.Time          `json:"observedAt"`
	Execution  executionDetailDTO `json:"execution"`
}

// activityFactsDTO is deliberately closed. Its fields are selected recorded
// scalars; arbitrary activity properties and framework-authored prose do not
// cross the MCP boundary. json.Number preserves the exact upstream spelling.
type activityFactsDTO struct {
	SkillName                    *string      `json:"skillName,omitempty"`
	CapabilityName               *string      `json:"capabilityName,omitempty"`
	TaskID                       *string      `json:"taskId,omitempty"`
	PlanID                       *string      `json:"planId,omitempty"`
	StepNumber                   *json.Number `json:"stepNumber,omitempty"`
	RetrySequenceID              *string      `json:"retrySequenceId,omitempty"`
	AttemptID                    *string      `json:"attemptId,omitempty"`
	AttemptNumber                *json.Number `json:"attemptNumber,omitempty"`
	ProviderAttemptNumber        *json.Number `json:"providerAttemptNumber,omitempty"`
	AttemptReason                *string      `json:"attemptReason,omitempty"`
	Unplanned                    *bool        `json:"unplanned,omitempty"`
	Retry                        *bool        `json:"retry,omitempty"`
	Exhausted                    *bool        `json:"exhausted,omitempty"`
	FailureID                    *string      `json:"failureId,omitempty"`
	Classification               *string      `json:"classification,omitempty"`
	FailureClassification        *string      `json:"failureClassification,omitempty"`
	FailureCategory              *string      `json:"failureCategory,omitempty"`
	ExceptionType                *string      `json:"exceptionType,omitempty"`
	Outcome                      *string      `json:"outcome,omitempty"`
	TerminalFailureID            *string      `json:"terminalFailureId,omitempty"`
	RetryDecision                *string      `json:"retryDecision,omitempty"`
	RetryDelayMillis             *json.Number `json:"retryDelayMillis,omitempty"`
	RetryDelaySource             *string      `json:"retryDelaySource,omitempty"`
	ApplicationTraceAvailability *string      `json:"applicationTraceAvailability,omitempty"`
	ApplicationTraceExpiresAt    *string      `json:"applicationTraceExpiresAt,omitempty"`
}

type activityDTO struct {
	Cursor            string            `json:"cursor"`
	SessionID         string            `json:"sessionId"`
	TraceID           string            `json:"traceId"`
	CanonicalSequence *int64            `json:"canonicalSequence,omitempty"`
	Timestamp         time.Time         `json:"timestamp"`
	Kind              live.ActivityKind `json:"kind"`
	ExecutionStatus   string            `json:"executionStatus,omitempty"`
	FrameID           string            `json:"frameId,omitempty"`
	ParentFrameID     string            `json:"parentFrameId,omitempty"`
	FrameType         string            `json:"frameType,omitempty"`
	Route             string            `json:"route,omitempty"`
	Facts             activityFactsDTO  `json:"facts"`
}

type cursorRangeDTO struct {
	FirstCursor string `json:"firstCursor"`
	LastCursor  string `json:"lastCursor"`
}

type continuityDTO struct {
	IntervalID  string          `json:"intervalId"`
	FirstCursor string          `json:"firstCursor,omitempty"`
	LastCursor  string          `json:"lastCursor,omitempty"`
	ObservedAt  time.Time       `json:"observedAt,omitempty"`
	Reset       *live.ResetFact `json:"reset,omitempty"`
}

type coverageDTO struct {
	GlobalEvictedThroughCursor  string          `json:"globalEvictedThroughCursor,omitempty"`
	SessionStartCursor          string          `json:"sessionStartCursor,omitempty"`
	SessionEvictedThroughCursor string          `json:"sessionEvictedThroughCursor,omitempty"`
	SessionRetainedCursorRange  *cursorRangeDTO `json:"sessionRetainedCursorRange,omitempty"`
}

type activityResult struct {
	ObservedAt          time.Time       `json:"observedAt"`
	Items               []activityDTO   `json:"items"`
	ReturnedCursorRange *cursorRangeDTO `json:"returnedCursorRange,omitempty"`
	HasMore             bool            `json:"hasMore"`
	Continuation        string          `json:"continuation,omitempty"`
	Continuity          *continuityDTO  `json:"continuity,omitempty"`
	Coverage            coverageDTO     `json:"coverage"`
}

var readOnlyAnnotations = func() *mcp.ToolAnnotations {
	falseValue := false
	return &mcp.ToolAnnotations{
		ReadOnlyHint: true, DestructiveHint: &falseValue,
		IdempotentHint: true, OpenWorldHint: &falseValue,
	}
}()

func successResult[T any](value T, fallback string) (*mcp.CallToolResult, toolEnvelope[T], error) {
	return &mcp.CallToolResult{Content: []mcp.Content{&mcp.TextContent{Text: fallback}}},
		toolEnvelope[T]{Result: &value}, nil
}

func domainFailure[T any](domain *consolecore.Error) (*mcp.CallToolResult, toolEnvelope[T], error) {
	dto := mapDomainError(domain)
	return &mcp.CallToolResult{
		Content: []mcp.Content{&mcp.TextContent{Text: string(dto.Code) + ": " + dto.Message}},
		IsError: true,
	}, toolEnvelope[T]{Error: &dto}, nil
}

func mapDomainError(domain *consolecore.Error) domainErrorDTO {
	if domain == nil {
		domain = consolecore.NewError(consolecore.CodeConsoleError, "The Console operation could not be completed.", "", consolecore.Details{}, nil)
	}
	return domainErrorDTO{
		Code: domain.Code, Message: domain.Message, Details: errorDetailsDTO{
			ExpectedCompatibilityVersion: domain.Details.ExpectedCompatibilityVersion,
			ObservedCompatibilityVersion: domain.Details.ObservedCompatibilityVersion,
			LimitName:                    domain.Details.LimitName, LimitValue: domain.Details.LimitValue,
			RawDownloadAvailable: domain.Details.RawDownloadAvailable,
		},
	}
}

type lineWriter struct{ lines []string }

func (writer *lineWriter) quoted(name, value string) {
	encoded, _ := json.Marshal(value) // Encoding a Go string cannot fail.
	writer.lines = append(writer.lines, name+": "+string(encoded))
}

func (writer *lineWriter) integer(name string, value int64) {
	writer.lines = append(writer.lines, fmt.Sprintf("%s: %d", name, value))
}

func (writer *lineWriter) boolean(name string, value bool) {
	writer.lines = append(writer.lines, fmt.Sprintf("%s: %t", name, value))
}

func (writer *lineWriter) time(name string, value time.Time) {
	writer.quoted(name, value.UTC().Format(time.RFC3339Nano))
}

func (writer *lineWriter) continuation(value string) {
	if value == "" {
		writer.lines = append(writer.lines, "continuation: -")
		return
	}
	writer.quoted("continuation", value)
}

func (writer *lineWriter) String() string { return strings.Join(writer.lines, "\n") + "\n" }

func appendCommon(writer *lineWriter, observedAt time.Time) {
	writer.time("observedAt", observedAt)
}

func mapExecutionListItem(source observability.ActiveExecution) executionListItemDTO {
	return executionListItemDTO{SessionID: source.SessionID, TraceID: source.TraceID, Status: source.Status,
		Phase: source.Phase, UpdatedAt: source.UpdatedAt, ActiveBranchCount: len(source.ActiveBranches)}
}

func mapExecutionDetail(source observability.ActiveExecution) executionDetailDTO {
	branches := make([]activeBranchDTO, 0, len(source.ActiveBranches))
	for _, branch := range source.ActiveBranches {
		path := make([]framePathDTO, 0, len(branch.Path))
		for _, entry := range branch.Path {
			path = append(path, framePathDTO{FrameID: entry.FrameID, FrameType: entry.FrameType, Route: entry.Route})
		}
		branches = append(branches, activeBranchDTO{PlanID: branch.PlanID, TaskID: branch.TaskID,
			StepNumber: branch.StepNumber, ParallelGroup: branch.ParallelGroup,
			EffectiveConcurrency: branch.EffectiveConcurrency, Path: path})
	}
	return executionDetailDTO{
		SessionID: source.SessionID, TraceID: source.TraceID,
		LastCanonicalSequence: source.LastCanonicalSequence,
		StartedAt:             source.StartedAt, UpdatedAt: source.UpdatedAt,
		ElapsedMillis: source.ElapsedMillis, EntrySkill: source.EntrySkill,
		Status: source.Status, Phase: source.Phase, ActiveBranches: branches,
		Usage: source.Usage, ConfiguredLimits: source.ConfiguredLimits,
	}
}

func mapActivity(source live.Activity) (activityDTO, error) {
	var raw map[string]json.RawMessage
	if err := json.Unmarshal(source.Details, &raw); err != nil {
		return activityDTO{}, err
	}
	var facts activityFactsDTO
	stringFact := func(key string, target **string) error {
		if value, ok := raw[key]; ok {
			var v string
			if err := json.Unmarshal(value, &v); err != nil {
				return err
			}
			*target = &v
		}
		return nil
	}
	boolFact := func(key string, target **bool) error {
		if value, ok := raw[key]; ok {
			var v bool
			if err := json.Unmarshal(value, &v); err != nil {
				return err
			}
			*target = &v
		}
		return nil
	}
	numberFact := func(key string, target **json.Number) error {
		if value, ok := raw[key]; ok {
			n := json.Number(string(value))
			if _, err := n.Float64(); err != nil {
				return err
			}
			*target = &n
		}
		return nil
	}
	for _, item := range []struct {
		key    string
		target **string
	}{{"skillName", &facts.SkillName}, {"capabilityName", &facts.CapabilityName}, {"linkedTaskId", &facts.TaskID}, {"planId", &facts.PlanID}, {"retrySequenceId", &facts.RetrySequenceID}, {"attemptId", &facts.AttemptID}, {"attemptReason", &facts.AttemptReason}, {"failureId", &facts.FailureID}, {"classification", &facts.Classification}, {"failureClassification", &facts.FailureClassification}, {"failureCategory", &facts.FailureCategory}, {"exceptionType", &facts.ExceptionType}, {"outcome", &facts.Outcome}, {"terminalFailureId", &facts.TerminalFailureID}, {"retryDecision", &facts.RetryDecision}, {"retryDelaySource", &facts.RetryDelaySource}, {"applicationTraceAvailability", &facts.ApplicationTraceAvailability}, {"applicationTraceExpiresAt", &facts.ApplicationTraceExpiresAt}} {
		if err := stringFact(item.key, item.target); err != nil {
			return activityDTO{}, err
		}
	}
	for _, item := range []struct {
		key    string
		target **bool
	}{{"unplanned", &facts.Unplanned}, {"retry", &facts.Retry}, {"exhausted", &facts.Exhausted}} {
		if err := boolFact(item.key, item.target); err != nil {
			return activityDTO{}, err
		}
	}
	for _, item := range []struct {
		key    string
		target **json.Number
	}{{"stepNumber", &facts.StepNumber}, {"attemptNumber", &facts.AttemptNumber}, {"providerAttemptNumber", &facts.ProviderAttemptNumber}, {"retryDelayMillis", &facts.RetryDelayMillis}} {
		if err := numberFact(item.key, item.target); err != nil {
			return activityDTO{}, err
		}
	}
	return activityDTO{
		Cursor:    source.Cursor,
		SessionID: source.SessionID, TraceID: source.TraceID,
		CanonicalSequence: source.CanonicalSequence, Timestamp: source.Timestamp,
		Kind: source.Kind, ExecutionStatus: source.ExecutionStatus,
		FrameID: source.FrameID, ParentFrameID: source.ParentFrameID,
		FrameType: source.FrameType, Route: source.Route, Facts: facts,
	}, nil
}

func pageInputSchema[T any]() *jsonschema.Schema {
	schema, err := jsonschema.For[T](nil)
	if err != nil {
		panic(err)
	}
	minimum, maximum := float64(1), float64(maxMCPPageSize)
	schema.Properties["pageSize"].Minimum = &minimum
	schema.Properties["pageSize"].Maximum = &maximum
	if property := schema.Properties["sessionId"]; property != nil {
		minimumLength := 1
		property.MinLength = &minimumLength
		property.Pattern = `.*\S.*`
	}
	return schema
}

func nonblankInputSchema[T any](field string) *jsonschema.Schema {
	schema, err := jsonschema.For[T](nil)
	if err != nil {
		panic(err)
	}
	minimum := 1
	schema.Properties[field].MinLength = &minimum
	schema.Properties[field].Pattern = `.*\S.*`
	return schema
}
