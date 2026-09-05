package ai.loomspan.internal.skillapi;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.core.ExecutionJournal;
import ai.loomspan.internal.core.JournalEntry;
import ai.loomspan.internal.core.JournalEntryType;
import ai.loomspan.internal.core.JournalLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillExecutionViewMapperTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillExecutionViewMapper mapper = new SkillExecutionViewMapper(objectMapper);

    @Test
    void mapsSelectedJournalEntriesToPublicEventsInOrder()
    {
        ExecutionJournal journal = new ExecutionJournal(List.of(
                entry(JournalLevel.INFO, JournalEntryType.THOUGHT, objectMapper.createObjectNode().put("message", "thinking")),
                entry(JournalLevel.WARN, JournalEntryType.MODEL_ATTEMPT_FAILURE,
                        objectMapper.createObjectNode().put("attemptId", "attempt-1").put("retryDecision", "RETRY")),
                entry(JournalLevel.ERROR, JournalEntryType.ERROR, objectMapper.createObjectNode().put("message", "failed"))));

        var view = mapper.map("session-1", journal);

        assertThat(view.events()).extracting(event -> event.type())
                .containsExactly("THOUGHT", "MODEL_ATTEMPT_FAILURE", "ERROR");
        assertThat(view.events()).extracting(event -> event.level())
                .containsExactly("INFO", "WARN", "ERROR");
        assertThat(view.events().get(0).details()).containsEntry("message", "thinking");
        assertThat(view.events().get(1).details())
                .containsEntry("attemptId", "attempt-1")
                .containsEntry("retryDecision", "RETRY");
    }

    @Test
    void convertsScalarAndArrayPayloadsWithoutJsonNode()
    {
        var array = objectMapper.createArrayNode().add("one").add(2);
        ExecutionJournal journal = new ExecutionJournal(List.of(
                entry(JournalLevel.INFO, JournalEntryType.THOUGHT, objectMapper.getNodeFactory().textNode("hello")),
                entry(JournalLevel.INFO, JournalEntryType.PLAN_CREATED, array)));

        var view = mapper.map("session-1", journal);

        assertThat(view.events().get(0).details()).containsEntry("message", "hello");
        assertThat(view.events().get(1).details().get("value")).isEqualTo(List.of("one", 2));
        assertThat(view.events())
                .allSatisfy(event -> assertThat(event.details().values())
                        .noneMatch(value -> value instanceof tools.jackson.databind.JsonNode));
    }

    private JournalEntry entry(JournalLevel level, JournalEntryType type, tools.jackson.databind.JsonNode payload)
    {
        return new JournalEntry(Instant.parse("2026-07-15T12:00:00Z"), level, type, payload, "frame-1", "route-1");
    }
}
