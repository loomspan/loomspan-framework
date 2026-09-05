package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.FinalizedTraceArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public interface CompletionGraceRetention extends AutoCloseable
{
    Optional<RetainedArtifact> retainOrDelete(Path artifactPath, Instant finalizedAt, String traceId, String sessionId)
            throws IOException;

    Optional<ArtifactLease> acquire(FinalizedTraceArtifact artifact) throws IOException;

    @Override
    void close();

    record RetainedArtifact(Instant expiresAt, long sizeBytes)
    {
        public RetainedArtifact
        {
            if (expiresAt == null)
            {
                throw new NullPointerException("expiresAt must not be null");
            }
            if (sizeBytes < 0)
            {
                throw new IllegalArgumentException("sizeBytes must not be negative");
            }
        }
    }

    interface ArtifactLease extends AutoCloseable
    {
        InputStream input();

        long sizeBytes();

        @Override
        void close() throws IOException;
    }
}
