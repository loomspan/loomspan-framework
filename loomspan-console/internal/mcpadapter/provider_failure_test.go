package mcpadapter

import (
	"testing"

	"github.com/loomspan/loomspan-framework/loomspan-console/internal/traceanalysis"
)

func TestFailureAdapterPreservesOptionalProviderAttemptContentReference(t *testing.T) {
	withReference := mapFailure(traceanalysis.FailureSummary{FailureID: "failure", ProviderAttemptContentRef: "opaque-attempt"})
	if withReference.ProviderAttemptContentRef != "opaque-attempt" {
		t.Fatalf("provider attempt content ref=%q", withReference.ProviderAttemptContentRef)
	}
	withoutReference := mapFailure(traceanalysis.FailureSummary{FailureID: "ordinary"})
	if withoutReference.ProviderAttemptContentRef != "" {
		t.Fatalf("ordinary failure gained provider attempt content ref=%q", withoutReference.ProviderAttemptContentRef)
	}
}
