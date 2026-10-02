package ai.loomspan.internal.core;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;

import java.util.Map;

public final class MissionInputMessageFormatter
{
    private static final ObjectMapper OBJECT_MAPPER =
            ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().applicationConversion();

    private MissionInputMessageFormatter()
    {
    }

    public static String buildUserMessage(String objective, @Nullable Map<String, Object> missionInput)
    {
        if (missionInput == null || missionInput.isEmpty())
        {
            return objective;
        }

        return """
                Mission objective:
                %s

                Canonical mission input:
                %s

                Use the canonical mission input object as the source of truth for structured fields.
                """.formatted(
                objective == null || objective.isBlank() ? "(none)" : objective,
                prettyPrint(missionInput));
    }

    private static String prettyPrint(Map<String, Object> missionInput)
    {
        try
        {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(missionInput);
        }
        catch (JacksonException ex)
        {
            throw new IllegalStateException("Failed to serialize canonical mission input", ex);
        }
    }
}
