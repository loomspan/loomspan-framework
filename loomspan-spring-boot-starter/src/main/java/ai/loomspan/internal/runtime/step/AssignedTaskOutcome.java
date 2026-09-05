package ai.loomspan.internal.runtime.step;

import ai.loomspan.internal.linter.LinterOutcome;
import ai.loomspan.internal.outputschema.OutputSchemaOutcome;
import org.springframework.lang.Nullable;

import java.util.Objects;

public sealed interface AssignedTaskOutcome
{
    record Success(String result,
            @Nullable LinterOutcome linterOutcome,
            @Nullable OutputSchemaOutcome outputSchemaOutcome) implements AssignedTaskOutcome
    {
        public Success
        {
            result = Objects.requireNonNull(result, "result must not be null");
        }
    }

    record Failure(Throwable failure,
            String failureId,
            @Nullable LinterOutcome linterOutcome,
            @Nullable OutputSchemaOutcome outputSchemaOutcome) implements AssignedTaskOutcome
    {
        public Failure
        {
            failure = Objects.requireNonNull(failure, "failure must not be null");
            failureId = requireNonBlank(failureId, "failureId");
        }
    }

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
