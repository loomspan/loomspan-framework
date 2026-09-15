package ai.loomspan.internal.runtime.observation.catalog;

import java.util.List;

public record TraceCatalogSlice(long highWaterOrdinal, List<FinalizedTraceCatalogEntry> entries)
{
    public TraceCatalogSlice
    {
        if (highWaterOrdinal < 0)
        {
            throw new IllegalArgumentException("highWaterOrdinal must not be negative");
        }
        entries = List.copyOf(entries);
    }
}
