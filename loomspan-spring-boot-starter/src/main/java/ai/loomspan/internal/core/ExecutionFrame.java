package ai.loomspan.internal.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ExecutionFrame(
        String frameId,
        String parentFrameId,
        OperationType operationType,
        TraceFrameType traceFrameType,
        String route,
        Map<String, Object> parameters,
        Instant openedAt)
{
    public ExecutionFrame
    {
        frameId = requireNonBlank(frameId, "frameId");
        operationType = Objects.requireNonNull(operationType, "operationType must not be null");
        traceFrameType = Objects.requireNonNull(traceFrameType, "traceFrameType must not be null");
        route = requireNonBlank(route, "route");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
