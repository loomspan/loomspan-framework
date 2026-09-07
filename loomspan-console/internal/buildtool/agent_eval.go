package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"sort"
	"strings"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/agenteval"
)

func runAgentEval(arguments []string) error {
	if len(arguments) == 0 {
		return fmt.Errorf("usage: agent-eval <serve|record|score|verify|verify-replay|summarize>")
	}
	cases, err := agenteval.LoadCases()
	if err != nil {
		return err
	}
	switch arguments[0] {
	case "verify", "verify-replay":
		flags := flag.NewFlagSet("agent-eval "+arguments[0], flag.ContinueOnError)
		results := flags.String("results", "", "dated result directory")
		if err := flags.Parse(arguments[1:]); err != nil {
			return err
		}
		if *results == "" {
			return fmt.Errorf("verify requires --results")
		}
		return verifyAgentEvalResults(*results, cases, arguments[0] == "verify-replay")
	case "score":
		flags := flag.NewFlagSet("agent-eval score", flag.ContinueOnError)
		name := flags.String("record", "", "record file")
		if err := flags.Parse(arguments[1:]); err != nil {
			return err
		}
		record, err := agenteval.ReadRecord(*name, cases)
		if err != nil {
			return err
		}
		scoreErr := agenteval.Score(&record, cases[record.CaseID])
		raw, marshalErr := agenteval.CanonicalJSON(record)
		if marshalErr != nil {
			return marshalErr
		}
		if err := os.WriteFile(*name, raw, 0o600); err != nil {
			return err
		}
		return scoreErr
	case "record":
		flags := flag.NewFlagSet("agent-eval record", flag.ContinueOnError)
		input := flags.String("client-events", "", "complete native client event JSON")
		answer := flags.String("answer", "", "unedited final answer file")
		output := flags.String("output", "", "new record file")
		if err := flags.Parse(arguments[1:]); err != nil {
			return err
		}
		if *input == "" || *answer == "" || *output == "" {
			return fmt.Errorf("record requires --client-events, --answer, and --output")
		}
		record, err := agenteval.ImportRecord(*input, *answer, cases)
		if err != nil {
			return err
		}
		raw, err := agenteval.CanonicalJSON(record)
		if err != nil {
			return err
		}
		if err := os.MkdirAll(filepath.Dir(*output), 0o755); err != nil {
			return err
		}
		file, err := os.OpenFile(*output, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
		if err != nil {
			return err
		}
		defer file.Close()
		_, err = file.Write(raw)
		return err
	case "summarize":
		flags := flag.NewFlagSet("agent-eval summarize", flag.ContinueOnError)
		results := flags.String("results", "", "dated result directory")
		if err := flags.Parse(arguments[1:]); err != nil {
			return err
		}
		return summarizeAgentEvalResults(*results, cases)
	case "serve":
		flags := flag.NewFlagSet("agent-eval serve", flag.ContinueOnError)
		caseID := flags.String("case", "", "case ID")
		output := flags.String("output", "", "protected temporary session directory")
		if err := flags.Parse(arguments[1:]); err != nil {
			return err
		}
		value, ok := cases[*caseID]
		if !ok || *output == "" {
			return fmt.Errorf("serve requires a known --case and --output")
		}
		paths, err := resolveProjectPaths()
		if err != nil {
			return err
		}
		version, err := readProductVersion(filepath.Join(paths.repository, "pom.xml"))
		if err != nil {
			return err
		}
		server, err := agenteval.StartServer(*output, value, version)
		if err != nil {
			return err
		}
		defer server.Close(context.Background())
		session := map[string]string{"endpoint": server.Endpoint, "key": server.Key, "caseId": value.ID}
		raw, _ := json.MarshalIndent(session, "", "  ")
		if err := os.WriteFile(filepath.Join(*output, "session.json"), append(raw, '\n'), 0o600); err != nil {
			return err
		}
		fmt.Printf("Evaluation session ready; protected connection details: %s\n", filepath.Join(*output, "session.json"))
		ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
		defer stop()
		<-ctx.Done()
		return nil
	default:
		return fmt.Errorf("unknown agent-eval command %q", arguments[0])
	}
}

func readAgentEvalRecords(directory string, cases map[string]agenteval.Case) ([]agenteval.Record, error) {
	var records []agenteval.Record
	entries, err := os.ReadDir(directory)
	if err != nil {
		return nil, err
	}
	for _, entry := range entries {
		if entry.IsDir() || filepath.Ext(entry.Name()) != ".json" {
			continue
		}
		record, err := agenteval.ReadRecord(filepath.Join(directory, entry.Name()), cases)
		if err != nil {
			return nil, fmt.Errorf("%s: %w", entry.Name(), err)
		}
		records = append(records, record)
	}
	return records, nil
}
func verifyAgentEvalResults(directory string, cases map[string]agenteval.Case, historicalReplay bool) error {
	records, err := readAgentEvalRecords(directory, cases)
	if err != nil {
		return err
	}
	if historicalReplay {
		err = agenteval.ValidateHistoricalReplay(records, cases, filepath.Join(directory, "skill-package"))
	} else {
		err = agenteval.ValidateResults(records, cases)
	}
	if err != nil {
		return err
	}
	want := renderAgentEvalSummary(records)
	got, err := os.ReadFile(filepath.Join(directory, "summary.md"))
	if err != nil {
		return err
	}
	if string(got) != want {
		if historicalReplay {
			return fmt.Errorf("historical summary.md is stale relative to the preserved records")
		}
		return fmt.Errorf("summary.md is stale; run agent-eval summarize")
	}
	return nil
}
func summarizeAgentEvalResults(directory string, cases map[string]agenteval.Case) error {
	records, err := readAgentEvalRecords(directory, cases)
	if err != nil {
		return err
	}
	if err := agenteval.ValidateResults(records, cases); err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(directory, "summary.md"), []byte(renderAgentEvalSummary(records)), 0o644)
}
func renderAgentEvalSummary(records []agenteval.Record) string {
	sort.Slice(records, func(i, j int) bool { return records[i].CaseID < records[j].CaseID })
	var out strings.Builder
	out.WriteString("# Agent evaluation summary\n\nThe current paired matrix replays successfully from sanitized repository replay metadata. It is not fresh provider/client evidence.\n\n")
	for _, r := range records {
		fmt.Fprintf(&out, "- `%s` — %s / %s — passed=%t, calls=%d, unnecessary=%d\n", r.CaseID, r.Client, r.Model, r.Passed, len(r.Operations), r.UnnecessaryCalls)
	}
	return out.String()
}
