package traceanalysis

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRejectedResponseFixtureRetainsReceiptEvidenceAndTerminalAttemptLink(t *testing.T) {
	fixture, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "rejected-provider-response.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	h := newServiceTestHarnessForOutcomeAndVersion(t, "trace-rejected-provider-response", string(fixture), "FAILED", fixtureCompatibilityVersion)
	attempts, domain := h.service.QueryAttempts(context.Background(), targetEvidence(h.scopeID), AttemptQuery{Handle: h.handle, PageSize: 10})
	if domain != nil || len(attempts.Items) != 1 {
		t.Fatalf("attempts=%+v domain=%v", attempts, domain)
	}
	attempt := attempts.Items[0]
	if attempt.Outcome != "FAILED" || attempt.Usage.TotalUnits != 14 || !attempt.UsageComplete || attempt.RetryDecision != "DO_NOT_RETRY" {
		t.Fatalf("rejected response attempt=%+v", attempt)
	}
	failures, domain := h.service.QueryFailures(context.Background(), targetEvidence(h.scopeID), FailureQuery{Handle: h.handle, PageSize: 10})
	if domain != nil || len(failures.Items) != 1 || failures.Items[0].AttemptID != attempt.AttemptID || failures.Items[0].ProviderAttemptContentRef == "" {
		t.Fatalf("terminal rejection linkage=%+v domain=%v", failures, domain)
	}
	receipts, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{
		Handle: h.handle, Representation: RecordRepresentationLogical, PageSize: 10,
		Filter: RecordFilter{Types: []string{string(RecordModelResponseReceived)}, AttemptID: attempt.AttemptID},
	})
	if domain != nil || len(receipts.Items) != 1 || receipts.Items[0].Content == nil {
		t.Fatalf("receipt evidence=%+v domain=%v", receipts, domain)
	}
	content, domain := h.service.ReadContentRange(context.Background(), targetEvidence(h.scopeID), RangeRequest{
		Handle: h.handle, ContentRef: receipts.Items[0].Content.ContentRef, MaxBytes: 64 << 10,
	})
	if domain != nil || content.HasMore || !bytes.Contains(content.Content, []byte(`"status":"incomplete"`)) ||
		!bytes.Contains(content.Content, []byte(`max_output_tokens`)) || !bytes.Contains(content.Content, []byte(`{\"ok\":true}`)) {
		t.Fatalf("receipt content=%q domain=%v", content.Content, domain)
	}
}

func rejectedAttemptRecord(seq int, retryDecision string) string {
	classification, delay, source := "PERMANENT", 0, "NONE"
	if retryDecision == "RETRY" {
		classification, delay, source = "TRANSIENT", 10, "BACKOFF"
	}
	return fmt.Sprintf(`{"traceId":"t","sessionId":"s","sequence":%d,"timestamp":%s,"recordType":"MODEL_ATTEMPT_FAILED","frameId":null,"parentFrameId":null,"frameType":null,"route":null,"threadName":"th","metadata":{"retrySequenceId":"retry-1","attemptId":"attempt-1","attemptNumber":1,"attemptReason":"INITIAL","providerAttemptNumber":1,"failureClassification":%q,"failureCategory":"PROVIDER_RESPONSE_REJECTED","retryDecision":%q,"retryDelayMillis":%d,"retryDelaySource":%q},"data":{"message":"explicitly incomplete","status":"incomplete"}}`, seq, timestampForSeq(seq), classification, retryDecision, delay, source)
}

func TestRejectedReceivedResponseRetainsUsageAndFailedOutcome(t *testing.T) {
	lines := []string{
		requestRecord(2, "retry-1", "attempt-1", 1, false),
		responseRecord(3, "", "retry-1", "attempt-1", 1, 10, 4, 14, "EXACT"),
		rejectedAttemptRecord(4, "DO_NOT_RETRY"),
	}
	g := newAttemptGraph()
	for _, line := range lines {
		rec, domain := decodeRecord([]byte(line), RawAddress{})
		if domain != nil {
			t.Fatal(domain)
		}
		if domain := g.onModelRecord(rec); domain != nil {
			t.Fatal(domain)
		}
	}
	attempts, retries, ok := buildAttemptResults(g, nil)
	if !ok || len(attempts) != 1 || len(retries) != 1 {
		t.Fatalf("unexpected attempt results: %#v %#v %v", attempts, retries, ok)
	}
	a := attempts[0]
	if a.Outcome != "FAILED" || a.RetryDecision != "DO_NOT_RETRY" || a.Usage != (Usage{10, 4, 14}) || !a.UsageComplete {
		t.Fatalf("response rejection lost failure or usage: %#v", a)
	}
	if retries[0].Usage != a.Usage || !retries[0].UsageComplete {
		t.Fatalf("retry usage counted incorrectly: %#v", retries[0])
	}

	// The full processor must also reconcile one receipt's usage with a terminal
	// failure linked to the same received-and-rejected physical attempt.
	terminal := errorRecord(5, "failure-1", true)
	terminal = strings.Replace(terminal, `"failureId":"failure-1"`, `"failureId":"failure-1","attemptId":"attempt-1","retrySequenceId":"retry-1"`, 1)
	raw := startedRecord(1) + "\n" + strings.Join(lines, "\n") + "\n" + terminal + "\n" + completionRecord(6, "FAILED", 10, 4, 14, "failure-1") + "\n"
	m, category, valid := processTrace(t, raw)
	if !valid || m.AttemptCount != 1 || m.Outcome != "FAILED" || m.FailureCount != 1 {
		t.Fatalf("rejected response trace: valid=%v category=%s manifest=%#v", valid, category, m)
	}
}

func TestRejectedResponseLifecycleRejectsDuplicatesAndReversedReceipt(t *testing.T) {
	for name, lines := range map[string][]string{
		"duplicate receipt":              {responseRecord(3, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT"), responseRecord(4, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT")},
		"duplicate failure":              {rejectedAttemptRecord(3, "DO_NOT_RETRY"), rejectedAttemptRecord(4, "DO_NOT_RETRY")},
		"receipt after failure":          {rejectedAttemptRecord(3, "DO_NOT_RETRY"), responseRecord(4, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT")},
		"failure repeated after receipt": {responseRecord(3, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT"), rejectedAttemptRecord(4, "DO_NOT_RETRY"), rejectedAttemptRecord(5, "DO_NOT_RETRY")},
	} {
		t.Run(name, func(t *testing.T) {
			raw := startedRecord(1) + "\n" + requestRecord(2, "retry-1", "attempt-1", 1, false) + "\n" + strings.Join(lines, "\n") + "\n" + completionRecord(6, "SUCCEEDED", 2, 2, 4, "") + "\n"
			_, category, valid := processTrace(t, raw)
			if valid || category != CategoryInvalidAttempt {
				t.Fatalf("invalid lifecycle accepted: valid=%v category=%s", valid, category)
			}
		})
	}
}

func TestReceivedFailedAttemptCannotStartSemanticRetry(t *testing.T) {
	for _, decision := range []string{"DO_NOT_RETRY", "RETRY"} {
		t.Run(decision, func(t *testing.T) {
			g := newAttemptGraph()
			for _, line := range []string{requestRecord(2, "retry-1", "attempt-1", 1, false), responseRecord(3, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT"), rejectedAttemptRecord(4, decision)} {
				rec, domain := decodeRecord([]byte(line), RawAddress{})
				if domain != nil {
					t.Fatal(domain)
				}
				if domain := g.onModelRecord(rec); domain != nil {
					t.Fatal(domain)
				}
			}
			rec, domain := decodeRecord([]byte(requestRecord(5, "retry-1", "attempt-2", 2, false)), RawAddress{})
			if domain != nil {
				t.Fatal(domain)
			}
			if g.onModelRecord(rec) == nil {
				t.Fatal("failed receipt incorrectly enabled semantic retry")
			}
			if decision == "RETRY" {
				// An explicit transient provider retry remains valid after a received
				// failed envelope, and is not confused with semantic correction.
				var metadata map[string]any
				if err := json.Unmarshal(rec.Metadata, &metadata); err != nil {
					t.Fatal(err)
				}
				metadata["attemptReason"], metadata["providerAttemptNumber"] = "PROVIDER_RETRY", 2
				rec.Metadata, _ = json.Marshal(metadata)
				if domain := g.onModelRecord(rec); domain != nil {
					t.Fatal(domain)
				}
			}
		})
	}
}

func TestReceivedAttemptCannotFailAfterNextAttemptStarts(t *testing.T) {
	g := newAttemptGraph()
	for _, line := range []string{
		requestRecord(2, "retry-1", "attempt-1", 1, false),
		responseRecord(3, "", "retry-1", "attempt-1", 1, 1, 1, 2, "EXACT"),
		requestRecord(4, "retry-1", "attempt-2", 2, false),
	} {
		rec, domain := decodeRecord([]byte(line), RawAddress{})
		if domain != nil {
			t.Fatal(domain)
		}
		if domain := g.onModelRecord(rec); domain != nil {
			t.Fatal(domain)
		}
	}
	rec, domain := decodeRecord([]byte(rejectedAttemptRecord(5, "DO_NOT_RETRY")), RawAddress{})
	if domain != nil {
		t.Fatal(domain)
	}
	if g.onModelRecord(rec) == nil {
		t.Fatal("late failure retroactively invalidated a semantic retry")
	}
}
