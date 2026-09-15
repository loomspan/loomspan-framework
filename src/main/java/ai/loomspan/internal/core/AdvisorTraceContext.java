package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record AdvisorTraceContext(
        String advisorName,
        @Nullable String skillName,
        int attempt,
        String status,
        Map<String, Object> modelAttempt)
{
    public AdvisorTraceContext
    {
        advisorName = requireNonBlank(advisorName, "advisorName");
        if (skillName != null && skillName.isBlank())
        {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (attempt <= 0)
        {
            throw new IllegalArgumentException("attempt must be greater than zero");
        }
        status = requireNonBlank(status, "status");
        modelAttempt = modelAttempt == null ? Map.of() : Map.copyOf(modelAttempt);
    }

    public Map<String, Object> metadata()
    {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("advisorName", advisorName);
        if (skillName != null)
        {
            metadata.put("skillName", skillName);
        }
        metadata.put("attempt", attempt);
        metadata.put("status", status);
        metadata.putAll(modelAttempt);
        return Map.copyOf(metadata);
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
