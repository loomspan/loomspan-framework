package traceanalysis

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

// The oracle comes directly from the Java-written physical inventory, independently
// of the processor's indexes and query projection.
type executorPhysicalRecord struct {
	Sequence int64                      `json:"sequence"`
	Type     string                     `json:"recordType"`
	FrameID  string                     `json:"frameId"`
	Metadata map[string]json.RawMessage `json:"metadata"`
	Data     json.RawMessage            `json:"data"`
}

func TestExecutorDiagnosticsTraverseOrdinaryFiltersPagesAndRetainedContent(t *testing.T) {
	raw, err := os.ReadFile(filepath.Join(fixtureRoot(t), "traces", "java-root-success.ndjson"))
	if err != nil {
		t.Fatal(err)
	}
	var inventory []executorPhysicalRecord
	chunks := map[string][]byte{}
	for _, line := range bytes.Split(bytes.TrimSpace(raw), []byte("\n")) {
		var rec executorPhysicalRecord
		if err := json.Unmarshal(line, &rec); err != nil {
			t.Fatal(err)
		}
		inventory = append(inventory, rec)
		if rec.Type == string(RecordPayloadChunkAppended) {
			var id, fragment string
			if err := json.Unmarshal(rec.Metadata["payloadId"], &id); err != nil {
				t.Fatal(err)
			}
			if err := json.Unmarshal(rec.Data, &fragment); err != nil {
				t.Fatal(err)
			}
			chunks[id] = append(chunks[id], []byte(fragment)...)
		}
	}
	h := newServiceTestHarnessForVersion(t, "trace-java-root-success", string(raw), fixtureCompatibilityVersion)
	types := [][]string{nil, {string(RecordExecutorDiagnostic)}, {string(RecordExecutorDiagnosticNotice)}, {string(RecordExecutorDiagnostic), string(RecordExecutorDiagnosticNotice)}, {string(RecordFrameOpened), string(RecordExecutorDiagnosticNotice)}}
	frames := []string{"", "java-root", "unrelated-frame"}
	for _, representation := range []RecordRepresentation{RecordRepresentationLogical, RecordRepresentationPhysical} {
		for _, selected := range types {
			for _, frame := range frames {
				for _, size := range []int{1, 2} {
					t.Run(fmt.Sprintf("%s/%v/%s/%d", representation, selected, frame, size), func(t *testing.T) {
						var expected []int64
						for _, rec := range inventory {
							if representation == RecordRepresentationLogical && rec.Type == string(RecordPayloadChunkAppended) {
								continue
							}
							if len(selected) > 0 && !containsClosedValue(selected, rec.Type) || frame != "" && rec.FrameID != frame {
								continue
							}
							expected = append(expected, rec.Sequence)
						}
						var actual []int64
						cursor := ""
						for {
							page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Representation: representation, PageSize: size, Cursor: cursor, Filter: RecordFilter{Types: selected, FrameID: frame}})
							if domain != nil {
								t.Fatal(domain)
							}
							for _, rec := range page.Items {
								actual = append(actual, rec.Sequence)
								if rec.Content != nil && len(rec.Content.InlineContent) != 0 {
									t.Fatal("inline=false returned content")
								}
							}
							if !page.HasMore {
								break
							}
							if page.NextCursor == "" || page.NextCursor == cursor {
								t.Fatal("non-progressing page")
							}
							cursor = page.NextCursor
						}
						if !reflect.DeepEqual(actual, expected) {
							t.Fatalf("sequences=%v want %v", actual, expected)
						}
					})
				}
			}
		}
	}
	page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, PageSize: 1000, InlineContent: true, Filter: RecordFilter{Types: []string{string(RecordExecutorDiagnostic), string(RecordExecutorDiagnosticNotice)}}})
	if domain != nil {
		t.Fatal(domain)
	}
	states := map[string]bool{}
	authoredError, notices, perValue := 0, 0, 0
	for _, rec := range page.Items {
		desc := rec.Content
		if desc == nil || !desc.Available || !desc.Complete || desc.ContentRef == "" {
			t.Fatalf("missing retained envelope: %+v", rec)
		}
		source := inventory[rec.Sequence-1]
		expected := []byte(source.Data)
		if rec.IsEnvelope {
			var id string
			_ = json.Unmarshal(source.Metadata["payloadId"], &id)
			expected = chunks[id]
		}
		var retained []byte
		cursor := ""
		for {
			read, failure := h.service.ReadContentRange(context.Background(), targetEvidence(h.scopeID), RangeRequest{Handle: h.handle, ContentRef: desc.ContentRef, MaxBytes: 7, ContinueCursor: cursor})
			if failure != nil {
				t.Fatal(failure)
			}
			part := read.Content
			if read.Encoding == RangeEncodingBase64 {
				part, err = base64.StdEncoding.DecodeString(string(part))
				if err != nil {
					t.Fatal(err)
				}
			}
			retained = append(retained, part...)
			if !read.HasMore {
				break
			}
			if read.NextCursor == "" || read.NextCursor == cursor {
				t.Fatal("non-progressing range")
			}
			cursor = read.NextCursor
		}
		if !bytes.Equal(retained, expected) {
			t.Fatalf("sequence %d lost exact retained JSON", rec.Sequence)
		}
		if desc.InlineOmission == InlineOmissionPerValue {
			perValue++
		} else if !bytes.Equal(desc.InlineContent, expected) {
			t.Fatalf("sequence %d inline mismatch", rec.Sequence)
		}
		var payload map[string]any
		if err := json.Unmarshal(retained, &payload); err != nil {
			t.Fatal(err)
		}
		if rec.Type == string(RecordExecutorDiagnosticNotice) {
			notices++
			if _, exists := payload["severity"]; exists {
				t.Fatal("notice acquired severity")
			}
		} else {
			if payload["severity"] == "ERROR" {
				authoredError++
			}
			states[fmt.Sprint(payload["messageState"])] = true
			states[fmt.Sprint(payload["dataState"])] = true
			if payload["dataState"] == "ABSENT" {
				if _, exists := payload["data"]; exists {
					t.Fatal("absent JSON became explicit null")
				}
			}
		}
		if len(rec.Facts.Failures) > 0 || len(rec.Facts.Attempts) > 0 || len(rec.Facts.Retries) > 0 {
			t.Fatalf("diagnostic generated execution facts: %+v", rec.Facts)
		}
	}
	if authoredError == 0 || notices == 0 || perValue == 0 || !states["COMPLETE"] || !states["ABSENT"] || !states["TRUNCATED"] || !states["UNAVAILABLE"] {
		t.Fatalf("insufficient fixture coverage: error=%d notices=%d large=%d states=%v", authoredError, notices, perValue, states)
	}
	_, domain = h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, Filter: RecordFilter{Types: []string{"EXECUTOR_DIAGNOSTIC_UNKNOWN"}}})
	if domain == nil {
		t.Fatal("unknown record type accepted")
	}
}

func TestExecutorDiagnosticInlineBudgetsDoNotDiscardRetainedJSON(t *testing.T) {
	var records []map[string]any
	for _, line := range bytes.Split(bytes.TrimSpace([]byte(minimalValidTrace)), []byte("\n")) {
		var rec map[string]any
		if err := json.Unmarshal(line, &rec); err != nil {
			t.Fatal(err)
		}
		records = append(records, rec)
	}
	var expanded []map[string]any
	for _, rec := range records {
		if rec["recordType"] == "TRACE_COMPLETED" {
			for index := 0; index < 6; index++ {
				size := 7000
				if index == 5 {
					size = MaxInlineContentBytes + 1
				}
				expanded = append(expanded, map[string]any{"traceId": "trace-t", "sessionId": "session-t", "timestamp": rec["timestamp"], "recordType": string(RecordExecutorDiagnostic), "metadata": map[string]any{}, "data": map[string]any{"severity": "ERROR", "message": "opaque", "messageState": "COMPLETE", "dataState": "COMPLETE", "data": map[string]any{"usage": size, "outcome": "FAILED", "payload": strings.Repeat("x", size)}}})
			}
		}
		expanded = append(expanded, rec)
	}
	var raw bytes.Buffer
	for index, rec := range expanded {
		rec["sequence"] = index + 1
		encoded, err := json.Marshal(rec)
		if err != nil {
			t.Fatal(err)
		}
		raw.Write(encoded)
		raw.WriteByte('\n')
	}
	h := newServiceTestHarness(t, "trace-t", raw.String())
	page, domain := h.service.QueryRecords(context.Background(), targetEvidence(h.scopeID), RecordQuery{Handle: h.handle, PageSize: 64, InlineContent: true, Filter: RecordFilter{Types: []string{string(RecordExecutorDiagnostic)}}})
	if domain != nil || len(page.Items) != 6 {
		t.Fatalf("page=%+v domain=%v", page, domain)
	}
	for index, rec := range page.Items {
		desc := rec.Content
		if desc == nil || !desc.Available || !desc.Complete || desc.ContentRef == "" {
			t.Fatalf("descriptor=%+v", desc)
		}
		omission := InlineOmissionReason("")
		if index == 4 {
			omission = InlineOmissionAggregate
		}
		if index == 5 {
			omission = InlineOmissionPerValue
		}
		if desc.InlineOmission != omission {
			t.Fatalf("record %d omission=%s want %s", index, desc.InlineOmission, omission)
		}
		result, failure := h.service.ReadContentRange(context.Background(), targetEvidence(h.scopeID), RangeRequest{Handle: h.handle, ContentRef: desc.ContentRef, MaxBytes: MaxRangeBytes})
		if failure != nil || result.HasMore || int64(len(result.Content)) != desc.RetainedBytes || !json.Valid(result.Content) {
			t.Fatalf("lost retained JSON result=%+v failure=%v", result, failure)
		}
		if omission == "" && !bytes.Equal(result.Content, desc.InlineContent) {
			t.Fatal("inline bytes differ")
		}
	}
	summary, failure := h.service.GetSummary(context.Background(), targetEvidence(h.scopeID), SummaryRequest{Handle: h.handle})
	if failure != nil || summary.AttributedUsage.TotalUnits != 14 || summary.AttemptCount != 1 || summary.FailureCount != 0 || summary.Outcome != "SUCCEEDED" {
		t.Fatalf("opaque JSON affected Framework facts: %+v failure=%v", summary, failure)
	}
}
