package ai.loomspan.internal.skillapi;

import tools.jackson.databind.ObjectMapper;
import ai.loomspan.internal.core.ExecutionJournal;
import ai.loomspan.internal.core.JournalEntry;
import ai.loomspan.internal.core.JournalEntryType;
import ai.loomspan.internal.core.JournalLevel;
import ai.loomspan.internal.core.LoomspanSession;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkillExecutionViewMapperTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SkillExecutionViewMapper mapper = new SkillExecutionViewMapper(objectMapper);

    @Test
    void observerMappingRetainsAttachmentCollectionWithExistingJournalException() {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("attachment", true);
        descriptor.put("fieldPath", "/photos/0/a~1b~0");
        descriptor.put("name", null);
        descriptor.put("source", "resource");
        descriptor.put("contentType", "image/jpeg");
        descriptor.put("mediaType", "IMAGE");
        descriptor.put("metadata", Map.of("token", "canonical value", "label", "unchanged"));
        var data = objectMapper.valueToTree(Map.of("attachments", List.of(descriptor,
                Map.of("fieldPath", "/other", "source", "ref://original"))));
        var metadata = new ai.loomspan.internal.core.TraceRecord("trace", "session-1", 1,
                Instant.parse("2026-07-15T12:00:00Z"), ai.loomspan.internal.core.TraceRecordType.FRAME_METADATA_RECORDED,
                "mission", null, ai.loomspan.internal.core.TraceFrameType.ROOT_MISSION, "default-skill", "worker", Map.of(), data);
        var closed = new ai.loomspan.internal.core.TraceRecord("trace", "session-1", 2,
                Instant.parse("2026-07-15T12:00:01Z"), ai.loomspan.internal.core.TraceRecordType.FRAME_CLOSED,
                "mission", null, ai.loomspan.internal.core.TraceFrameType.ROOT_MISSION, "default-skill", "worker",
                Map.of("status", "failed"), null);
        var journal = new ai.loomspan.internal.runtime.trace.ExecutionJournalProjector().project(List.of(metadata, closed));

        var view = mapper.map("session-1", journal);

        assertThat(view.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("SKILL_FINISHED");
            assertThat(event.frameId()).isEqualTo("mission");
            assertThat(event.details()).containsEntry("status", "failed");
            assertThat(event.details().get("attachments")).isInstanceOf(List.class);
            var attachments = (List<?>) event.details().get("attachments");
            assertThat(attachments).hasSize(2);
            var first = (Map<?, ?>) attachments.getFirst();
            assertThat(first.get("fieldPath")).isEqualTo("/photos/0/a~1b~0");
            assertThat(first.containsKey("name")).isTrue();
            assertThat(first.get("name")).isNull();
            assertThat(first.get("source")).isEqualTo("resource");
            assertThat(((Map<?, ?>) first.get("metadata")).get("token")).isEqualTo("[redacted]");
            assertThat(((Map<?, ?>) first.get("metadata")).get("label")).isEqualTo("unchanged");
            assertThat(((Map<?, ?>) attachments.get(1)).get("source")).isEqualTo("ref://original");
            assertThat(first.containsKey("sizeBytes")).isFalse();
            assertThatThrownBy(() -> attachments.clear()).isInstanceOf(UnsupportedOperationException.class);
        });
        assertThat(metadata.data().path("attachments").get(0).path("metadata").path("token").asText())
                .isEqualTo("canonical value");
    }

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

    @Test
    void refusesToMapSessionWithoutRetainedFinalizedHistory()
    {
        LoomspanSession session = org.mockito.Mockito.mock(LoomspanSession.class);
        org.mockito.Mockito.when(session.getSessionId()).thenReturn("session-without-history");
        org.mockito.Mockito.when(session.getFinalizedExecutionJournal()).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> mapper.map(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Finalized execution history is unavailable");
    }

    private JournalEntry entry(JournalLevel level, JournalEntryType type, tools.jackson.databind.JsonNode payload)
    {
        return new JournalEntry(Instant.parse("2026-07-15T12:00:00Z"), level, type, payload, "frame-1", "route-1");
    }
}
