package ai.loomspan.internal.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AdvisorTraceFact(
        AdvisorTraceContext context,
        Direction direction,
        Kind kind,
        Map<String, Object> attributes)
{
    public AdvisorTraceFact
    {
        context = Objects.requireNonNull(context, "context must not be null");
        direction = Objects.requireNonNull(direction, "direction must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static AdvisorTraceFact request(AdvisorTraceContext context, Kind kind, Map<String, Object> attributes)
    {
        return new AdvisorTraceFact(context, Direction.REQUEST, kind, attributes);
    }

    public static AdvisorTraceFact response(AdvisorTraceContext context, Kind kind, Map<String, Object> attributes)
    {
        return new AdvisorTraceFact(context, Direction.RESPONSE, kind, attributes);
    }

    public static AdvisorTraceFact retryRequested(AdvisorTraceContext context, String detail)
    {
        return request(context, Kind.RETRY_REQUESTED, detail == null || detail.isBlank() ? Map.of() : Map.of("detail", detail));
    }

    public static AdvisorTraceFact retryRequested(AdvisorTraceContext context, List<?> issues)
    {
        return request(context, Kind.RETRY_REQUESTED, issues == null || issues.isEmpty() ? Map.of() : Map.of("issues", issues));
    }

    public static AdvisorTraceFact passed(AdvisorTraceContext context, String candidate)
    {
        return response(context, Kind.PASSED, candidate == null ? Map.of() : Map.of("candidate", candidate));
    }

    public static AdvisorTraceFact exhausted(AdvisorTraceContext context, String candidate, String detail)
    {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        if (candidate != null)
        {
            attributes.put("candidate", candidate);
        }
        if (detail != null && !detail.isBlank())
        {
            attributes.put("detail", detail);
        }
        return response(context, Kind.EXHAUSTED, attributes);
    }

    public static AdvisorTraceFact exhausted(AdvisorTraceContext context, List<?> issues)
    {
        return response(context, Kind.EXHAUSTED, issues == null || issues.isEmpty() ? Map.of() : Map.of("issues", issues));
    }

    public enum Direction
    {
        REQUEST,
        RESPONSE
    }

    public enum Kind
    {
        RETRY_REQUESTED,
        PASSED,
        EXHAUSTED
    }
}
