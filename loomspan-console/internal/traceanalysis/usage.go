package traceanalysis

import (
	"bytes"
	"encoding/json"
)

// usagePayload is the strict decode target for a metadata usage object.
type usagePayload struct {
	PromptUnits     *int64  `json:"promptUnits"`
	CompletionUnits *int64  `json:"completionUnits"`
	TotalUnits      *int64  `json:"totalUnits"`
	Precision       *string `json:"precision"`
}

// terminalSnapshot is the strict decode target for the TRACE_COMPLETED
// sessionUsageSnapshot metadata field.
type terminalSnapshot struct {
	PromptUnits     *int64 `json:"promptUnits"`
	CompletionUnits *int64 `json:"completionUnits"`
	TotalUnits      *int64 `json:"totalUnits"`
}

// jsonUnmarshal is a thin wrapper to keep encoding/json imports localized.
func jsonUnmarshal(raw []byte, v any) error {
	return json.Unmarshal(raw, v)
}

// isNullRaw reports whether raw is a JSON null literal.
func isNullRaw(raw json.RawMessage) bool {
	return len(raw) == 0 || bytes.Equal(raw, nullBytes)
}

// validateUsageComponents validates one response usage value. When complete, each
// normalized total must be nonnegative and at least each provided component.
// Negative components are always invalid. The plan does not require
// total = prompt + completion.
func validateUsageComponents(u Usage, complete bool) bool {
	if u.PromptUnits < 0 || u.CompletionUnits < 0 || u.TotalUnits < 0 {
		return false
	}
	if complete {
		if u.TotalUnits < u.PromptUnits || u.TotalUnits < u.CompletionUnits {
			return false
		}
	}
	return true
}

// usageCalculator accumulates attributed, unframed attributed, and terminal
// usage and derives the unattributed terminal remainder component-by-component.
type usageCalculator struct {
	attributed         Usage
	unframedAttributed Usage
	terminal           Usage
	terminalSet        bool
	allComplete        bool
}

// newUsageCalculator creates a calculator. allComplete starts true and is
// cleared when any response usage is unavailable or missing.
func newUsageCalculator() *usageCalculator {
	return &usageCalculator{allComplete: true}
}

// addAttributed adds one physical MODEL_RESPONSE_RECEIVED usage to attributed
// totals. unframed reports whether the response had no frameId. It reports false
// if the accumulation overflows int64.
func (c *usageCalculator) addAttributed(u Usage, complete bool, unframed bool) bool {
	var ok bool
	c.attributed, ok = c.attributed.plus(u)
	if !ok {
		return false
	}
	if unframed {
		c.unframedAttributed, ok = c.unframedAttributed.plus(u)
		if !ok {
			return false
		}
	}
	if !complete {
		c.allComplete = false
	}
	return true
}

// setTerminal records the terminal session usage snapshot.
func (c *usageCalculator) setTerminal(u Usage) {
	c.terminal = u
	c.terminalSet = true
}

// unattributed derives the terminal unattributed remainder component-by-component
// as terminal minus attributed. It rejects any negative component as a
// contradictory reconciliation.
func (c *usageCalculator) unattributed() (Usage, bool) {
	r, ok := c.terminal.minus(c.attributed)
	if !ok {
		return Usage{}, false
	}
	if r.PromptUnits < 0 || r.CompletionUnits < 0 || r.TotalUnits < 0 {
		return Usage{}, false
	}
	return r, true
}

// extractTerminalUsage extracts the sessionUsageSnapshot from a TRACE_COMPLETED
// record's metadata and validates its components.
func extractTerminalUsage(rec *Record) (Usage, bool) {
	m, err := rec.metadataObject()
	if err != nil {
		return Usage{}, false
	}
	raw, ok := m["sessionUsageSnapshot"]
	if !ok || isNullRaw(raw) {
		return Usage{}, false
	}
	var snap terminalSnapshot
	if err := jsonUnmarshal(raw, &snap); err != nil {
		return Usage{}, false
	}
	if snap.PromptUnits == nil || snap.CompletionUnits == nil || snap.TotalUnits == nil {
		return Usage{}, false
	}
	u := Usage{PromptUnits: *snap.PromptUnits, CompletionUnits: *snap.CompletionUnits, TotalUnits: *snap.TotalUnits}
	if u.PromptUnits < 0 || u.CompletionUnits < 0 || u.TotalUnits < 0 {
		return Usage{}, false
	}
	return u, true
}

// extractOutcome extracts the terminal outcome from a TRACE_COMPLETED record.
func extractOutcome(rec *Record) (TraceOutcome, bool) {
	s := rec.metadataStringOrEmpty("outcome")
	if s == "" {
		return "", false
	}
	return knownOutcome(s)
}

// extractTerminalFailureID extracts the terminalFailureId from a TRACE_COMPLETED
// record. It returns the value and whether it was present and non-null.
func extractTerminalFailureID(rec *Record) (string, bool) {
	id := rec.metadataStringOrEmpty("terminalFailureId")
	return id, id != ""
}
