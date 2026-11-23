package ai.loomspan.autoconfigure;

import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.runtime.trace.DefaultExecutionTraceHandle;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.util.Objects;

@Validated
public class ExecutionTraceProperties
{
    @NotNull
    private Path directory = DefaultExecutionTraceHandle.defaultDirectory();

    public Path getDirectory()
    {
        return directory;
    }

    public void setDirectory(Path directory)
    {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
    }

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
