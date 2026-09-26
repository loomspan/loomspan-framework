package ai.loomspan.autoconfigure;

import ai.loomspan.internal.core.TracePersistencePolicy;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

@Validated
public class ExecutionTraceProperties
{
    @NotNull
    private TracePersistencePolicy persistence = TracePersistencePolicy.ONERROR;

    public TracePersistencePolicy getPersistence()
    {
        return persistence;
    }

    public void setPersistence(TracePersistencePolicy persistence)
    {
        this.persistence = persistence == null ? TracePersistencePolicy.ONERROR : persistence;
    }
}
