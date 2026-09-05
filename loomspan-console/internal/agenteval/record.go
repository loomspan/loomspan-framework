package agenteval

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const RecordSchemaVersion = 1

type Operation struct {
	Tool         string `json:"tool"`
	ArgumentHash string `json:"argumentHash"`
	ResultHash   string `json:"resultHash"`
	Continuation bool   `json:"continuation,omitempty"`
	Externalized bool   `json:"externalized,omitempty"`
	RawRead      bool   `json:"rawRead,omitempty"`
}
type ClientEvent struct {
	Kind         string          `json:"kind"`
	Tool         string          `json:"tool,omitempty"`
	Arguments    json.RawMessage `json:"arguments,omitempty"`
	Result       json.RawMessage `json:"result,omitempty"`
	Continuation bool            `json:"continuation,omitempty"`
	Externalized bool            `json:"externalized,omitempty"`
	RawRead      bool            `json:"rawRead,omitempty"`
	Action       string          `json:"action,omitempty"`
}
type ClientEventStream struct {
	SchemaVersion            int                 `json:"schemaVersion"`
	RunID                    string              `json:"runId"`
	ConversationID           string              `json:"conversationId"`
	CaseID                   string              `json:"caseId"`
	Mode                     string              `json:"mode"`
	Client                   string              `json:"client"`
	ClientBuild              string              `json:"clientBuild"`
	Model                    string              `json:"model"`
	ConsoleCommit            string              `json:"consoleCommit"`
	SkillDigest              string              `json:"skillDigest,omitempty"`
	EventStreamKind          string              `json:"eventStreamKind"`
	EventStreamComplete      bool                `json:"eventStreamComplete"`
	Events                   []ClientEvent       `json:"events"`
	SupportedFacts           []string            `json:"supportedFacts"`
	Limitations              []string            `json:"limitations"`
	FactEvidence             map[string]string   `json:"factEvidence"`
	LimitationEvidence       map[string]string   `json:"limitationEvidence"`
	FactResultEvidence       map[string][]string `json:"factResultEvidence"`
	LimitationResultEvidence map[string][]string `json:"limitationResultEvidence"`
	ClarifiedAmbiguity       bool                `json:"clarifiedAmbiguity,omitempty"`
	UnsupportedClaims        []string            `json:"unsupportedClaims,omitempty"`
	UnnecessaryCalls         int                 `json:"unnecessaryCalls"`
}
type Record struct {
	SchemaVersion            int                 `json:"schemaVersion"`
	RunID                    string              `json:"runId"`
	ConversationID           string              `json:"conversationId"`
	CaseID                   string              `json:"caseId"`
	Mode                     string              `json:"mode"`
	Client                   string              `json:"client"`
	ClientBuild              string              `json:"clientBuild"`
	Model                    string              `json:"model"`
	ConsoleCommit            string              `json:"consoleCommit"`
	SkillDigest              string              `json:"skillDigest,omitempty"`
	EventStreamKind          string              `json:"eventStreamKind"`
	EventStreamComplete      bool                `json:"eventStreamComplete"`
	Operations               []Operation         `json:"operations"`
	ClientActions            []string            `json:"clientActions,omitempty"`
	Answer                   string              `json:"answer"`
	SupportedFacts           []string            `json:"supportedFacts"`
	Limitations              []string            `json:"limitations"`
	FactEvidence             map[string]string   `json:"factEvidence"`
	LimitationEvidence       map[string]string   `json:"limitationEvidence"`
	FactResultEvidence       map[string][]string `json:"factResultEvidence"`
	LimitationResultEvidence map[string][]string `json:"limitationResultEvidence"`
	ClarifiedAmbiguity       bool                `json:"clarifiedAmbiguity,omitempty"`
	UnsupportedClaims        []string            `json:"unsupportedClaims,omitempty"`
	UnnecessaryCalls         int                 `json:"unnecessaryCalls"`
	Passed                   bool                `json:"passed"`
	Failures                 []string            `json:"failures,omitempty"`
}

var hashPattern = regexp.MustCompile(`^[0-9a-f]{64}$`)
var sensitivePatterns = []*regexp.Regexp{
	regexp.MustCompile(`lsmcp_[A-Za-z0-9_-]{20,}`),
	regexp.MustCompile(`(?i)authorization\s*:\s*bearer`),
	regexp.MustCompile(`(?i)artifactHandle|targetScopeId|evidenceOwner`),
	regexp.MustCompile(`(?i)[A-Za-z]:\\`),
	regexp.MustCompile(`/(?:home|Users|tmp|var|etc|opt|srv|mnt|Volumes|private)/`),
}
var windowsAbsolutePathPattern = regexp.MustCompile(`(?i)(^|[\s'"([{=,:;])[A-Z]:[\\/]`)
var windowsRootedPathPattern = regexp.MustCompile(`(^|[\s'"([{=,:;])\\{1,2}[^\\\s]`)
var fileURLPattern = regexp.MustCompile(`(?i)\bfile:/+`)

var sensitiveJSONKeys = map[string]bool{
	"authorization":       true,
	"proxy-authorization": true,
}

func ValidateRecord(value Record, cases map[string]Case) error {
	caseValue, ok := cases[value.CaseID]
	if !ok {
		return fmt.Errorf("unknown case")
	}
	if value.SchemaVersion != RecordSchemaVersion || value.RunID == "" || value.ConversationID == "" || value.Mode != caseValue.Mode || value.Client == "" || value.ClientBuild == "" || value.Model == "" || value.ConsoleCommit == "" || strings.TrimSpace(value.Answer) == "" {
		return fmt.Errorf("missing required record metadata")
	}
	if value.UnnecessaryCalls < 0 {
		return fmt.Errorf("unnecessary call count cannot be negative")
	}
	if value.EventStreamKind != "headless" && value.EventStreamKind != "deterministic-replay" {
		return fmt.Errorf("unknown event stream kind")
	}
	if !value.EventStreamComplete {
		return fmt.Errorf("incomplete event stream")
	}
	if value.Mode == "skill-assisted" && !hashPattern.MatchString(value.SkillDigest) {
		return fmt.Errorf("skill-assisted record needs digest")
	}
	if value.Mode == "tools-only" && value.SkillDigest != "" {
		return fmt.Errorf("tools-only record has skill digest")
	}
	allowed := map[string]bool{}
	for _, tool := range caseValue.AllowedTools {
		allowed[tool] = true
	}
	if len(value.Operations) == 0 {
		return fmt.Errorf("record has no observed operations")
	}
	requests := map[string]bool{}
	results := map[string]bool{}
	for _, op := range value.Operations {
		if !allowed[op.Tool] || !validEvidenceHash(op.ArgumentHash) || !validEvidenceHash(op.ResultHash) {
			return fmt.Errorf("invalid operation metadata")
		}
		request := op.Tool + "\x00" + op.ArgumentHash
		if requests[request] {
			return fmt.Errorf("duplicate operation request metadata")
		}
		requests[request] = true
		results[op.ResultHash] = true
	}
	if len(value.ClientActions) != 0 {
		return fmt.Errorf("record contains unapproved client actions")
	}
	if !sameSet(value.SupportedFacts, caseValue.RequiredFacts) || !sameSet(value.Limitations, caseValue.RequiredLimitations) {
		return fmt.Errorf("record oracle facts differ")
	}
	if err := validateAnswerEvidence(caseValue.RequiredFacts, value.FactEvidence, value.Answer); err != nil {
		return fmt.Errorf("fact evidence: %w", err)
	}
	if err := validateAnswerEvidence(caseValue.RequiredLimitations, value.LimitationEvidence, value.Answer); err != nil {
		return fmt.Errorf("limitation evidence: %w", err)
	}
	if err := validateResultEvidence(caseValue.RequiredFacts, value.FactResultEvidence, results); err != nil {
		return fmt.Errorf("fact result evidence: %w", err)
	}
	if err := validateResultEvidence(caseValue.RequiredLimitations, value.LimitationResultEvidence, results); err != nil {
		return fmt.Errorf("limitation result evidence: %w", err)
	}
	raw, _ := json.Marshal(value)
	if rawContainsSensitiveData(raw) {
		return fmt.Errorf("record contains sensitive or internal data")
	}
	if rawContainsAbsoluteMachinePath(raw) {
		return fmt.Errorf("record contains an absolute machine path")
	}
	return nil
}
func validEvidenceHash(value string) bool {
	return hashPattern.MatchString(value) && value != strings.Repeat(value[:1], len(value))
}
func ReadRecord(name string, cases map[string]Case) (Record, error) {
	raw, err := os.ReadFile(filepath.Clean(name))
	if err != nil {
		return Record{}, err
	}
	var value Record
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&value); err != nil {
		return Record{}, err
	}
	if err := requireJSONEOF(dec); err != nil {
		return Record{}, err
	}
	return value, ValidateRecord(value, cases)
}
func ImportRecord(eventsFile, answerFile string, cases map[string]Case) (Record, error) {
	raw, err := os.ReadFile(filepath.Clean(eventsFile))
	if err != nil {
		return Record{}, err
	}
	if rawContainsSensitiveData(raw) {
		return Record{}, fmt.Errorf("client events contain sensitive or internal data")
	}
	var stream ClientEventStream
	decoder := json.NewDecoder(bytes.NewReader(raw))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&stream); err != nil {
		return Record{}, fmt.Errorf("parse complete client events: %w", err)
	}
	if err := requireJSONEOF(decoder); err != nil {
		return Record{}, fmt.Errorf("parse complete client events: %w", err)
	}
	if rawContainsAbsoluteMachinePath(raw) {
		return Record{}, fmt.Errorf("client events contain an absolute machine path")
	}
	if stream.SchemaVersion != 1 || stream.EventStreamKind != "headless" || !stream.EventStreamComplete || len(stream.Events) == 0 {
		return Record{}, fmt.Errorf("client event stream must be a complete nonempty headless export")
	}
	value := Record{
		SchemaVersion: RecordSchemaVersion, RunID: stream.RunID, ConversationID: stream.ConversationID,
		CaseID: stream.CaseID, Mode: stream.Mode, Client: stream.Client, ClientBuild: stream.ClientBuild,
		Model: stream.Model, ConsoleCommit: stream.ConsoleCommit, SkillDigest: stream.SkillDigest,
		EventStreamKind: stream.EventStreamKind, EventStreamComplete: true,
		SupportedFacts: stream.SupportedFacts, Limitations: stream.Limitations,
		FactEvidence: stream.FactEvidence, LimitationEvidence: stream.LimitationEvidence,
		FactResultEvidence: stream.FactResultEvidence, LimitationResultEvidence: stream.LimitationResultEvidence,
		ClarifiedAmbiguity: stream.ClarifiedAmbiguity, UnsupportedClaims: stream.UnsupportedClaims,
		UnnecessaryCalls: stream.UnnecessaryCalls,
	}
	for _, event := range stream.Events {
		switch event.Kind {
		case "tool":
			if event.Tool == "" || event.Action != "" || len(event.Arguments) == 0 || len(event.Result) == 0 || !json.Valid(event.Arguments) || !json.Valid(event.Result) {
				return Record{}, fmt.Errorf("invalid tool event")
			}
			value.Operations = append(value.Operations, Operation{Tool: event.Tool, ArgumentHash: Hash(event.Arguments), ResultHash: Hash(event.Result), Continuation: event.Continuation, Externalized: event.Externalized, RawRead: event.RawRead})
		case "client-action":
			return Record{}, fmt.Errorf("client event stream contains an unapproved client action")
		default:
			return Record{}, fmt.Errorf("unknown client event kind %q", event.Kind)
		}
	}
	answer, err := os.ReadFile(filepath.Clean(answerFile))
	if err != nil {
		return Record{}, err
	}
	value.Answer = strings.TrimSpace(string(answer))
	if containsAbsoluteMachinePath(value.Answer) {
		return Record{}, fmt.Errorf("answer contains an absolute machine path")
	}
	if err := ValidateRecord(value, cases); err != nil {
		return Record{}, err
	}
	return value, nil
}
func CanonicalJSON(value Record) ([]byte, error) {
	var out bytes.Buffer
	enc := json.NewEncoder(&out)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	if err := enc.Encode(value); err != nil {
		return nil, err
	}
	return out.Bytes(), nil
}
func Hash(value []byte) string { sum := sha256.Sum256(value); return hex.EncodeToString(sum[:]) }

func rawContainsAbsoluteMachinePath(raw []byte) bool {
	var value any
	if json.Unmarshal(raw, &value) != nil {
		return false
	}
	return anyContainsAbsoluteMachinePath(value)
}

func rawContainsSensitiveData(raw []byte) bool {
	var value any
	if json.Unmarshal(raw, &value) != nil {
		return false
	}
	return anyContainsSensitiveData(value)
}

func anyContainsSensitiveData(value any) bool {
	switch typed := value.(type) {
	case string:
		for _, pattern := range sensitivePatterns {
			if pattern.MatchString(typed) {
				return true
			}
		}
	case []any:
		for _, item := range typed {
			if anyContainsSensitiveData(item) {
				return true
			}
		}
	case map[string]any:
		for key, item := range typed {
			if sensitiveJSONKeys[strings.ToLower(strings.TrimSpace(key))] || anyContainsSensitiveData(item) {
				return true
			}
		}
	}
	return false
}

func anyContainsAbsoluteMachinePath(value any) bool {
	switch typed := value.(type) {
	case string:
		return containsAbsoluteMachinePath(typed)
	case []any:
		for _, item := range typed {
			if anyContainsAbsoluteMachinePath(item) {
				return true
			}
		}
	case map[string]any:
		for _, item := range typed {
			if anyContainsAbsoluteMachinePath(item) {
				return true
			}
		}
	}
	return false
}

func containsAbsoluteMachinePath(value string) bool {
	if windowsAbsolutePathPattern.MatchString(value) || windowsRootedPathPattern.MatchString(value) || fileURLPattern.MatchString(value) {
		return true
	}
	for index := 0; index < len(value); index++ {
		if value[index] != '/' || (index+1 < len(value) && value[index+1] == '/') {
			continue
		}
		if index == 0 || strings.ContainsRune(" \t\r\n'\"([{=,:;", rune(value[index-1])) {
			return true
		}
	}
	return false
}

func sameSet(a, b []string) bool {
	if len(a) != len(b) || duplicate(a) || duplicate(b) {
		return false
	}
	seen := map[string]bool{}
	for _, v := range a {
		seen[v] = true
	}
	for _, v := range b {
		if !seen[v] {
			return false
		}
	}
	return true
}

func validateAnswerEvidence(criteria []string, evidence map[string]string, answer string) error {
	if len(evidence) != len(criteria) {
		return fmt.Errorf("evidence keys do not match the oracle")
	}
	lowerAnswer := strings.ToLower(answer)
	for _, criterion := range criteria {
		excerpt, ok := evidence[criterion]
		excerpt = strings.TrimSpace(excerpt)
		if !ok || len(excerpt) < 8 || !strings.Contains(lowerAnswer, strings.ToLower(excerpt)) {
			return fmt.Errorf("criterion %q lacks a quoted answer excerpt", criterion)
		}
	}
	return nil
}

func validateResultEvidence(criteria []string, evidence map[string][]string, results map[string]bool) error {
	if len(evidence) != len(criteria) {
		return fmt.Errorf("evidence keys do not match the oracle")
	}
	for _, criterion := range criteria {
		resultHashes, ok := evidence[criterion]
		if !ok || len(resultHashes) == 0 || duplicate(resultHashes) {
			return fmt.Errorf("criterion %q does not reference an observed tool result", criterion)
		}
		for _, resultHash := range resultHashes {
			if !results[resultHash] {
				return fmt.Errorf("criterion %q does not reference an observed tool result", criterion)
			}
		}
	}
	return nil
}
