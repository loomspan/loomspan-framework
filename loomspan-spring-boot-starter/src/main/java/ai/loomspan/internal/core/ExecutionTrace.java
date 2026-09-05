package ai.loomspan.internal.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.nio.file.Path;
import java.util.Objects;

public record ExecutionTrace(
        String traceId,
        String sessionId,
        @Nullable String filePath,
        TracePersistencePolicy persistencePolicy,
        boolean errored,
        boolean completed)
{
    @JsonCreator
    public ExecutionTrace(
            @JsonProperty("traceId") String traceId,
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("filePath") @Nullable String filePath,
            @JsonProperty("persistencePolicy") TracePersistencePolicy persistencePolicy,
            @JsonProperty("errored") boolean errored,
            @JsonProperty("completed") boolean completed)
    {
        this.traceId = requireNonBlank(traceId, "traceId");
        this.sessionId = requireNonBlank(sessionId, "sessionId");
        this.filePath = filePath == null || filePath.isBlank() ? null : filePath;
        this.persistencePolicy = persistencePolicy == null ? TracePersistencePolicy.ONERROR : persistencePolicy;
        this.errored = errored;
        this.completed = completed;
    }

    @Nullable
    public Path tracePath()
    {
        return filePath == null ? null : Path.of(filePath);
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
