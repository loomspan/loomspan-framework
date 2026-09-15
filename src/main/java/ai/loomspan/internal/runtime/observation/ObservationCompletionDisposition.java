package ai.loomspan.internal.runtime.observation;

import ai.loomspan.internal.core.FinalizedTraceArtifact;
import ai.loomspan.internal.core.TraceOutcome;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ObservationCompletionDisposition(
        Status status,
        @Nullable TraceOutcome outcome,
        Instant closedAt,
        Optional<FinalizedTraceArtifact> finalizedArtifact)
{
    public ObservationCompletionDisposition
    {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(closedAt, "closedAt must not be null");
        finalizedArtifact = finalizedArtifact == null ? Optional.empty() : finalizedArtifact;
        if (status == Status.CORE_FINALIZATION_FAILED && finalizedArtifact.isPresent())
        {
            throw new IllegalArgumentException("Core finalization failure cannot carry a finalized artifact");
        }
    }

    public ObservationCompletionDisposition(Status status, @Nullable TraceOutcome outcome, Instant closedAt)
    {
        this(status, outcome, closedAt, Optional.empty());
    }

    public enum Status
    {
        CORE_FINALIZATION_SUCCEEDED,
        CORE_FINALIZATION_FAILED
    }
}
