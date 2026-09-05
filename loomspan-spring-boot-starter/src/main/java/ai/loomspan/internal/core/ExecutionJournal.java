package ai.loomspan.internal.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public final class ExecutionJournal
{
    private final List<JournalEntry> entries;

    @JsonCreator
    public ExecutionJournal(@JsonProperty("entries") List<JournalEntry> entries)
    {
        // The journal is a derived projection over the canonical trace, not a runtime append target.
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    @JsonProperty("entries")
    public List<JournalEntry> getEntriesSnapshot()
    {
        return List.copyOf(entries);
    }
}
