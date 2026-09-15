package ai.loomspan.internal.runtime;

import java.time.Duration;
import java.util.Objects;

public class LoomspanMissionTimeoutException extends RuntimeException
{
    public LoomspanMissionTimeoutException(String sessionId, String skillName, Duration missionTimeout, Throwable cause)
    {
        super("Mission execution timed out for session %s while running skill %s after %s."
                .formatted(
                        Objects.requireNonNull(sessionId, "sessionId must not be null"),
                        Objects.requireNonNull(skillName, "skillName must not be null"),
                        Objects.requireNonNull(missionTimeout, "missionTimeout must not be null")),
                cause);
    }
}
