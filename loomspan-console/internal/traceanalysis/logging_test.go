package traceanalysis

import (
	"github.com/loomspan/loomspan-framework/loomspan-console/internal/diagnostics"
	"testing"
)

func TestAnalysisFailureCategoryReachesOwner(t *testing.T) {
	for _, tc := range []struct {
		category     InvalidityCategory
		cause, limit string
		value        int64
	}{
		{CategoryMalformedJSON, "MALFORMED_JSON", "", 0},
		{CategoryLineTooLarge, "line_limit", "maxPhysicalLineBytes", maxPhysicalLineBytes},
		{CategoryExcessiveJSONDepth, "depth_limit", "maxJSONDepth", maxJSONDepth},
	} {
		f := diagnostics.Extract(invalidityError(tc.category, "trace-canary"))
		if f.Cause != tc.cause || f.LimitName != tc.limit || f.LimitValue != tc.value {
			t.Fatalf("facts: %#v", f)
		}
	}
}
