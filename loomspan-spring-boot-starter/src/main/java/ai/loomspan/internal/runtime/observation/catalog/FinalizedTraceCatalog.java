package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.core.FinalizedTraceArtifact;
import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;

import java.io.IOException;
import java.util.Optional;

public interface FinalizedTraceCatalog extends AutoCloseable
{
    FinalizedTraceCatalogEntry publish(FinalizedTraceArtifact artifact);

    Optional<FinalizedTraceCatalogEntry> find(String traceId);

    Optional<ArtifactAcquisition> acquire(String traceId) throws IOException;

    TraceCatalogSlice list(long highWaterOrdinal, long beforeOrdinal, int limit);

    int catalogedTraceCount();

    @Override
    void close();

    record ArtifactAcquisition(
            String traceId,
            long sizeBytes,
            CompletionGraceRetention.ArtifactLease lease)
    {
        public ArtifactAcquisition
        {
            if (traceId == null || traceId.isBlank())
            {
                throw new IllegalArgumentException("traceId must not be blank");
            }
            if (sizeBytes < 0)
            {
                throw new IllegalArgumentException("sizeBytes must not be negative");
            }
            if (lease == null)
            {
                throw new NullPointerException("lease must not be null");
            }
        }
    }
}
