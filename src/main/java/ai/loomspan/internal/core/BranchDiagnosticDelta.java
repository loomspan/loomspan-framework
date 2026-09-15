package ai.loomspan.internal.core;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import org.springframework.lang.Nullable;

record BranchDiagnosticDelta(
        @Nullable LinterOutcome linterOutcome,
        @Nullable OutputSchemaOutcome outputSchemaOutcome)
{
    static BranchDiagnosticDelta empty()
    {
        return new BranchDiagnosticDelta(null, null);
    }
}
