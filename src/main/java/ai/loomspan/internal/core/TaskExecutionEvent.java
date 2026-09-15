package ai.loomspan.internal.core;

import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record TaskExecutionEvent(String eventId, String capabilityName, @Nullable String linkedTaskId, Map<String, Object> details, @Nullable String note)
{
    public TaskExecutionEvent
    {
        eventId = requireNonBlank(eventId, "eventId");
        capabilityName = requireNonBlank(capabilityName, "capabilityName");
        linkedTaskId = normalizeNullable(linkedTaskId);
        details = details == null ? Map.of() : Map.copyOf(details);
        note = normalizeNullable(note);
    }

    public static TaskExecutionEvent linked(String capabilityName, String linkedTaskId, Map<String, Object> details, @Nullable String note)
    {
        return new TaskExecutionEvent(UUID.randomUUID().toString(), capabilityName, linkedTaskId, details, note);
    }

    public static TaskExecutionEvent unlinked(String capabilityName, Map<String, Object> details, @Nullable String note)
    {
        return new TaskExecutionEvent(UUID.randomUUID().toString(), capabilityName, null, details, note);
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

    private static String normalizeNullable(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return value;
    }
}
