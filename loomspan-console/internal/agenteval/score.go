package agenteval

import (
	"fmt"
	"strings"
)

func Score(record *Record, caseValue Case) error {
	var failures []string
	answer := strings.ToLower(record.Answer)
	toolCalls := map[string]int{}
	hasContinuation := false
	hasExternalized := false
	hasRawRead := false
	if !sameSet(record.SupportedFacts, caseValue.RequiredFacts) {
		failures = append(failures, "missing required facts")
	}
	if !sameSet(record.Limitations, caseValue.RequiredLimitations) {
		failures = append(failures, "missing required limitations")
	}
	if err := validateAnswerEvidence(caseValue.RequiredFacts, record.FactEvidence, record.Answer); err != nil {
		failures = append(failures, "unsupported fact annotations: "+err.Error())
	}
	if err := validateAnswerEvidence(caseValue.RequiredLimitations, record.LimitationEvidence, record.Answer); err != nil {
		failures = append(failures, "unsupported limitation annotations: "+err.Error())
	}
	for _, claim := range caseValue.ForbiddenClaims {
		if strings.Contains(answer, strings.ToLower(claim)) {
			failures = append(failures, "forbidden claim: "+claim)
		}
	}
	for _, claim := range record.UnsupportedClaims {
		if strings.TrimSpace(claim) != "" {
			failures = append(failures, "unsupported claim: "+claim)
		}
	}
	for _, op := range record.Operations {
		toolCalls[op.Tool]++
		hasContinuation = hasContinuation || op.Continuation
		hasExternalized = hasExternalized || op.Externalized
		hasRawRead = hasRawRead || op.RawRead
		if op.RawRead && op.Tool != "LOOMSPAN_read_trace_artifact" {
			failures = append(failures, "raw-read marker is attached to a non-raw tool")
		}
		for _, forbidden := range caseValue.ForbiddenActions {
			if strings.Contains(strings.ToLower(op.Tool), strings.ToLower(forbidden)) {
				failures = append(failures, "forbidden action")
			}
		}
	}
	for _, action := range record.ClientActions {
		// Evaluation runs are tools-only at the client boundary. There is no
		// approved non-MCP action vocabulary, so fail closed instead of trying
		// to enumerate every shell, filesystem, browser, network, or plugin
		// spelling that a client export could contain.
		failures = append(failures, "unapproved client action: "+action)
	}
	switch caseValue.PairID {
	case "live-concurrency":
		if toolCalls["LOOMSPAN_list_executions"] == 0 || toolCalls["LOOMSPAN_get_execution"] < len(caseValue.Fixtures) {
			failures = append(failures, "live execution discovery and every fixture detail were not observed")
		}
	case "finalized-plans":
		if toolCalls["LOOMSPAN_list_traces"] == 0 || toolCalls["LOOMSPAN_get_trace"] < len(caseValue.Fixtures) || toolCalls["LOOMSPAN_query_trace_plans"] < 2 || toolCalls["LOOMSPAN_query_trace_frames"] < 2 || toolCalls["LOOMSPAN_query_trace_records"] == 0 {
			failures = append(failures, "finalized plan, nested assignment, retry, and terminal evidence were not all observed")
		}
	case "imported-ambiguity":
		if toolCalls["LOOMSPAN_list_traces"] < 2 || !hasContinuation {
			failures = append(failures, "complete imported discovery was not observed")
		}
		for _, tool := range []string{"LOOMSPAN_get_trace", "LOOMSPAN_query_trace_plans", "LOOMSPAN_query_trace_frames", "LOOMSPAN_query_trace_records", "LOOMSPAN_read_trace_content", "LOOMSPAN_read_trace_artifact"} {
			if toolCalls[tool] != 0 {
				failures = append(failures, "trace-specific inspection continued before ambiguity was resolved")
				break
			}
		}
	case "bounded-content-raw":
		if toolCalls["LOOMSPAN_query_trace_records"] < 2 || toolCalls["LOOMSPAN_read_trace_content"] < 2 || toolCalls["LOOMSPAN_read_trace_artifact"] == 0 {
			failures = append(failures, "semantic, exact-content, and raw traversal was not observed")
		}
		if !hasContinuation || !hasExternalized || !hasRawRead {
			failures = append(failures, "bounded continuation, client externalization, and raw-read observations are required")
		}
	}
	if caseValue.PairID == "imported-ambiguity" && !record.ClarifiedAmbiguity {
		failures = append(failures, "ambiguity was not handled")
	}
	record.Failures = failures
	record.Passed = len(failures) == 0
	if !record.Passed {
		return fmt.Errorf("evaluation failed %d gates", len(failures))
	}
	return nil
}

func ValidateResults(records []Record, cases map[string]Case) error {
	expectedSkillDigest, err := RuntimeSkillDigest()
	if err != nil {
		return err
	}
	return validateResults(records, cases, expectedSkillDigest)
}

// ValidateHistoricalReplay verifies recorded replays against their preserved
// package. It does not establish evaluation coverage of the current package.
func ValidateHistoricalReplay(records []Record, cases map[string]Case, packageDirectory string) error {
	for _, record := range records {
		if record.EventStreamKind != "deterministic-replay" {
			return fmt.Errorf("historical replay verification requires deterministic-replay records")
		}
	}
	digest, err := skillPackageDigest(packageDirectory)
	if err != nil {
		return err
	}
	return validateResults(records, cases, digest)
}

func validateResults(records []Record, cases map[string]Case, expectedSkillDigest string) error {
	if len(records) != len(cases) {
		return fmt.Errorf("result matrix has %d records, want %d", len(records), len(cases))
	}
	seen := map[string]bool{}
	type pairMetadata struct{ client, clientBuild, model, consoleCommit string }
	pairs := map[string]pairMetadata{}
	var checkoutIdentity string
	for _, record := range records {
		if seen[record.CaseID] {
			return fmt.Errorf("duplicate case result")
		}
		seen[record.CaseID] = true
		caseValue := cases[record.CaseID]
		if checkoutIdentity == "" {
			checkoutIdentity = record.ConsoleCommit
		}
		if record.ConsoleCommit != checkoutIdentity {
			return fmt.Errorf("result matrix contains multiple checkout identities")
		}
		if record.Mode == "skill-assisted" && record.SkillDigest != expectedSkillDigest {
			return fmt.Errorf("%s skill digest does not match the verification package", record.CaseID)
		}
		metadata := pairMetadata{record.Client, record.ClientBuild, record.Model, record.ConsoleCommit}
		if previous, ok := pairs[caseValue.PairID]; ok && previous != metadata {
			return fmt.Errorf("%s pair metadata differs between modes", caseValue.PairID)
		}
		pairs[caseValue.PairID] = metadata
		copy := record
		if err := Score(&copy, caseValue); err != nil {
			return fmt.Errorf("%s: %w", record.CaseID, err)
		}
		if !record.Passed || len(record.Failures) > 0 {
			return fmt.Errorf("%s lacks derived passing score", record.CaseID)
		}
	}
	return nil
}
