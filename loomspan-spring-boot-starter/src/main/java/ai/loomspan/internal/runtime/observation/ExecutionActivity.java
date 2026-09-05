package ai.loomspan.internal.runtime.observation;

import ai.loomspan.internal.core.TraceFrameType;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ExecutionActivity(
        long deliveryCursor,
        String sessionId,
        String traceId,
        @Nullable Long canonicalSequence,
        Instant timestamp,
        ExecutionActivityKind kind,
        @Nullable String frameId,
        @Nullable String parentFrameId,
        @Nullable TraceFrameType frameType,
        @Nullable String route,
        @Nullable String executionStatus,
        String summary,
        Map<String, Object> details,
        int retainedWeight)
{
    public ExecutionActivity
    {
        if (deliveryCursor < 0)
        {
            throw new IllegalArgumentException("deliveryCursor must not be negative");
        }
        sessionId = requireNonBlank(sessionId, "sessionId");
        traceId = requireNonBlank(traceId, "traceId");
        if (canonicalSequence != null && canonicalSequence <= 0)
        {
            throw new IllegalArgumentException("canonicalSequence must be positive when present");
        }
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        frameId = normalize(frameId);
        parentFrameId = normalize(parentFrameId);
        route = normalize(route);
        executionStatus = normalize(executionStatus);
        summary = ExecutionObservationLimits.truncate(summary, ExecutionObservationLimits.SUMMARY_CODE_POINTS);
        LinkedHashMap<String, Object> safeDetails = new LinkedHashMap<>();
        int detailBytes = 0;
        if (details != null)
        {
            for (Map.Entry<String, Object> entry : details.entrySet())
            {
                String key = requireNonBlank(entry.getKey(), "detail key");
                Object value = immutableScalar(entry.getValue());
                int addedBytes = ExecutionObservationLimits.utf8Weight(key)
                        + ExecutionObservationLimits.utf8Weight(String.valueOf(value));
                if (detailBytes + addedBytes > ExecutionObservationLimits.DETAIL_UTF8_BYTES)
                {
                    throw new IllegalArgumentException("details exceed the retained byte limit");
                }
                safeDetails.put(key, value);
                detailBytes += addedBytes;
            }
        }
        details = Collections.unmodifiableMap(safeDetails);
        if (safeDetails.size() > ExecutionObservationLimits.DETAIL_FIELDS)
        {
            throw new IllegalArgumentException("details exceed the retained field limit");
        }
        if (retainedWeight <= 0 || retainedWeight > ExecutionObservationLimits.ACTIVITY_UTF8_BYTES)
        {
            throw new IllegalArgumentException("retainedWeight must be within the activity limit");
        }
    }

    private static Object immutableScalar(Object value)
    {
        if (value instanceof String text)
        {
            return ExecutionObservationLimits.truncate(text, ExecutionObservationLimits.TEXT_CODE_POINTS);
        }
        if (value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal)
        {
            return value;
        }
        throw new IllegalArgumentException("details may contain only immutable scalar values");
    }

    ExecutionActivity withDeliveryCursor(long cursor)
    {
        return new ExecutionActivity(
                cursor, sessionId, traceId, canonicalSequence, timestamp, kind, frameId, parentFrameId, frameType,
                route, executionStatus, summary, details, retainedWeight);
    }

    ExecutionActivity withTraceAvailability(
            String availability,
            @Nullable String unavailableReason,
            @Nullable Instant expiresAt)
    {
        LinkedHashMap<String, Object> enriched = new LinkedHashMap<>(details);
        enriched.put("applicationTraceAvailability", requireNonBlank(availability, "availability"));
        if (unavailableReason != null)
        {
            enriched.put("applicationTraceUnavailableReason", requireNonBlank(unavailableReason, "unavailableReason"));
        }
        if (expiresAt != null)
        {
            enriched.put("applicationTraceExpiresAt", expiresAt.toString());
        }
        int weight = 128
                + ExecutionObservationLimits.utf8Weight(sessionId)
                + ExecutionObservationLimits.utf8Weight(traceId)
                + ExecutionObservationLimits.utf8Weight(frameId)
                + ExecutionObservationLimits.utf8Weight(parentFrameId)
                + ExecutionObservationLimits.utf8Weight(route)
                + ExecutionObservationLimits.utf8Weight(executionStatus)
                + ExecutionObservationLimits.utf8Weight(kind.name())
                + ExecutionObservationLimits.utf8Weight(summary);
        for (Map.Entry<String, Object> entry : enriched.entrySet())
        {
            weight += ExecutionObservationLimits.utf8Weight(entry.getKey())
                    + ExecutionObservationLimits.utf8Weight(String.valueOf(entry.getValue())) + 8;
        }
        return new ExecutionActivity(
                deliveryCursor, sessionId, traceId, canonicalSequence, timestamp, kind, frameId, parentFrameId,
                frameType, route, executionStatus, summary, enriched, Math.max(1, weight));
    }

    private static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Nullable
    private static String normalize(@Nullable String value)
    {
        return value == null || value.isBlank()
                ? null
                : ExecutionObservationLimits.truncate(value, ExecutionObservationLimits.TEXT_CODE_POINTS);
    }
}
