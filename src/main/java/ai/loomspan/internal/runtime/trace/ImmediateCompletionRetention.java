package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.FinalizedTraceArtifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public enum ImmediateCompletionRetention implements CompletionGraceRetention
{
    INSTANCE;

    @Override
    public Optional<RetainedArtifact> retainOrDelete(
            Path artifactPath,
            Instant finalizedAt,
            String traceId,
            String sessionId) throws IOException
    {
        Files.deleteIfExists(artifactPath);
        return Optional.empty();
    }

    @Override
    public Optional<ArtifactLease> acquire(FinalizedTraceArtifact artifact)
    {
        return Optional.empty();
    }

    @Override
    public void close()
    {
    }
}
