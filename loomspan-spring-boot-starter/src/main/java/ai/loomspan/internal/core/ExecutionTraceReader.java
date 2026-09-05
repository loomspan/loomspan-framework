package ai.loomspan.internal.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface ExecutionTraceReader
{
    void read(Path tracePath, Consumer<TraceRecord> consumer) throws IOException;
}
