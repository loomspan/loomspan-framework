package ai.loomspan.internal.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public interface ExecutionTraceHandle
{
        TraceRecord append(
                        TraceRecordType recordType,
                        ExecutionFrame frame,
                        TraceFrameType frameType,
                        Map<String, Object> metadata,
                        Object data) throws IOException;

        TraceRecord append(TraceRecordType recordType, Map<String, Object> metadata, Object data) throws IOException;

        ExecutionTrace snapshot();

        Path tracePath();

        void markErrored();

        Optional<FinalizedTraceArtifact> finalizeTrace(TraceCompletion completion) throws IOException;

        void readRecords(Consumer<TraceRecord> consumer) throws IOException;
}
