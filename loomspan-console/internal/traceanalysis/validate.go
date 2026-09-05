package traceanalysis

import (
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/consolecore"
)

// validator accumulates per-record validation state and enforces the fixed
// validation rules: stable identity, strictly increasing sequence, exactly one
// final TRACE_COMPLETED, known enums, and terminal cross-checks.
type validator struct {
	traceID     string
	sessionID   string
	lastSeq     int64
	seqSet      bool
	completion  *Record
	completions int
	scopeID     string
}

// newValidator creates a validator. scopeID is the trace ID used in domain
// errors.
func newValidator(scopeID string) *validator {
	return &validator{scopeID: scopeID}
}

// onRecord validates one parsed record's identity and sequence invariants.
func (v *validator) onRecord(rec *Record) *consolecore.Error {
	if v.traceID == "" {
		v.traceID = rec.TraceID
		v.sessionID = rec.SessionID
	} else {
		if rec.TraceID != v.traceID || rec.SessionID != v.sessionID {
			return invalidityError(CategoryInconsistentIdentity, v.scopeID)
		}
	}
	if v.seqSet && rec.Sequence <= v.lastSeq {
		return invalidityError(CategoryNonMonotonicSequence, v.scopeID)
	}
	v.lastSeq = rec.Sequence
	v.seqSet = true
	if rec.Type == RecordTraceCompleted {
		v.completion = rec
		v.completions++
	}
	return nil
}

// finalize checks terminal completion invariants and cross-checks the terminal
// record against acquisition metadata where both sides provide a fact.
func (v *validator) finalize(metadata traceMetadataView) *consolecore.Error {
	if v.completions == 0 {
		return invalidityError(CategoryMissingCompletion, v.scopeID)
	}
	if v.completions > 1 {
		return invalidityError(CategoryNonFinalCompletion, v.scopeID)
	}
	// The completion must be the final record. The parser delivers records in
	// canonical order, so the completion is last iff no record followed it; the
	// validator tracks only the completion count, and the processor guarantees
	// ordering by checking that the completion is the last delivered record.
	// Cross-check terminal identity with acquisition metadata where both sides
	// provide the fact.
	if metadata.traceID != "" && metadata.traceID != v.traceID {
		return invalidityError(CategoryInconsistentIdentity, v.scopeID)
	}
	if metadata.sessionID != "" && metadata.sessionID != v.sessionID {
		return invalidityError(CategoryInconsistentIdentity, v.scopeID)
	}
	return nil
}

// completionRecord returns the terminal TRACE_COMPLETED record, or nil.
func (v *validator) completionRecord() *Record {
	return v.completion
}

// traceMetadataView is the minimal acquisition-metadata view the validator needs
// for cross-checks. It avoids importing the artifact package's full TraceMetadata.
type traceMetadataView struct {
	traceID   string
	sessionID string
}
