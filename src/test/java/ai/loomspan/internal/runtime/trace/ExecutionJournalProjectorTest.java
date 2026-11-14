package ai.loomspan.internal.runtime.trace;

import tools.jackson.databind.json.JsonMapper;
import ai.loomspan.internal.core.JournalEntry;
import ai.loomspan.internal.core.JournalEntryType;
import ai.loomspan.internal.core.JournalLevel;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ExecutionJournalProjectorTest {

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void projectsAttachmentMetadataIntoFinishedFramesForListAndStreamWithoutChangingCanonicalRecords() throws Exception {
        var descriptor = new LinkedHashMap<String, Object>();
        descriptor.put("attachment", true);
        descriptor.put("fieldPath", "/a~1b/0/~0");
        descriptor.put("name", null);
        descriptor.put("contentType", "application/pdf");
        descriptor.put("mediaType", "PDF");
        descriptor.put("source", "ref://original/file.pdf");
        descriptor.put("sizeBytes", 12);
        descriptor.put("sha256", "hash");
        descriptor.put("metadata", Map.of("Authorization", "original value", "label", "exact"));
        var metadata = missionRecord(2, TraceRecordType.FRAME_METADATA_RECORDED, "parent", null,
                Map.of(), Map.of("attachments", List.of(descriptor)));
        var records = List.of(
                missionRecord(1, TraceRecordType.FRAME_OPENED, "parent", null, Map.of(), Map.of()),
                metadata,
                missionRecord(3, TraceRecordType.FRAME_CLOSED, "parent", null, Map.of("status", "failed"), Map.of()));
        var canonicalBefore = metadata.data().deepCopy();
        var projector = new ExecutionJournalProjector();
        var handle = mock(ai.loomspan.internal.core.ExecutionTraceHandle.class);
        doAnswer(invocation -> {
            Consumer<TraceRecord> consumer = invocation.getArgument(0);
            records.forEach(consumer);
            return null;
        }).when(handle).readRecords(any());

        var list = projector.project(records).getEntriesSnapshot();
        var streamed = projector.project(handle).getEntriesSnapshot();

        assertThat(streamed).isEqualTo(list);
        assertThat(list).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.SKILL_STARTED, JournalEntryType.SKILL_FINISHED);
        assertThat(list.getFirst().payload().has("attachments")).isFalse();
        var finished = list.getLast();
        assertThat(finished.payload().path("status").asText()).isEqualTo("failed");
        var attachment = finished.payload().path("attachments").get(0);
        assertThat(attachment.path("name").isNull()).isTrue();
        assertThat(attachment.path("fieldPath").asText()).isEqualTo("/a~1b/0/~0");
        assertThat(attachment.path("source").asText()).isEqualTo("ref://original/file.pdf");
        assertThat(attachment.path("sizeBytes").intValue()).isEqualTo(12);
        assertThat(attachment.path("sha256").asText()).isEqualTo("hash");
        assertThat(attachment.path("metadata").path("Authorization").asText()).isEqualTo("[redacted]");
        assertThat(attachment.path("metadata").path("label").asText()).isEqualTo("exact");
        assertThat(metadata.data()).isEqualTo(canonicalBefore);
        assertThat(metadata.data().path("attachments").get(0).path("metadata").path("Authorization").asText())
                .isEqualTo("original value");
    }

    @Test
    void isolatesInterleavedSameRouteFramesAndConcurrentRepeatedProjections() {
        var projector = new ExecutionJournalProjector();
        var records = List.of(
                missionRecord(1, TraceRecordType.FRAME_OPENED, "parent", null, Map.of(), Map.of()),
                missionRecord(2, TraceRecordType.FRAME_OPENED, "child", "parent", Map.of(), Map.of()),
                missionRecord(3, TraceRecordType.FRAME_METADATA_RECORDED, "child", "parent", Map.of(),
                        Map.of("attachments", List.of(Map.of("fieldPath", "/child", "source", "resource")))),
                missionRecord(4, TraceRecordType.FRAME_METADATA_RECORDED, "parent", null, Map.of(),
                        Map.of("attachments", List.of(Map.of("fieldPath", "/parent", "source", "ref://parent")))),
                missionRecord(5, TraceRecordType.FRAME_CLOSED, "child", "parent", Map.of("status", "aborted"), Map.of()),
                missionRecord(6, TraceRecordType.FRAME_CLOSED, "parent", null, Map.of("status", "completed"), Map.of()));
        var entries = projector.project(records).getEntriesSnapshot();
        assertThat(entries.get(2).frameId()).isEqualTo("child");
        assertThat(entries.get(2).payload().path("attachments").get(0).path("fieldPath").asText()).isEqualTo("/child");
        assertThat(entries.get(3).frameId()).isEqualTo("parent");
        assertThat(entries.get(3).payload().path("attachments").get(0).path("fieldPath").asText()).isEqualTo("/parent");

        // An unfinished projection cannot donate metadata to another projection with the same frame ID.
        projector.project(records.subList(0, 4));
        var closeOnly = List.of(records.getLast());
        var withAttachments = CompletableFuture.supplyAsync(() -> projector.project(records).getEntriesSnapshot());
        var withoutAttachments = CompletableFuture.supplyAsync(() -> projector.project(closeOnly).getEntriesSnapshot());
        assertThat(withAttachments.join()).isEqualTo(entries);
        assertThat(withoutAttachments.join().getFirst().payload().has("attachments")).isFalse();
    }

    @Test
    void doesNotInventAttachmentsForFailedMaterializationOrEmptyMetadata() {
        var records = List.of(
                missionRecord(1, TraceRecordType.FRAME_OPENED, "failed", null, Map.of(), Map.of()),
                missionRecord(2, TraceRecordType.FRAME_METADATA_RECORDED, "failed", null, Map.of(),
                        Map.of("other", "metadata")),
                missionRecord(3, TraceRecordType.FRAME_METADATA_RECORDED, "failed", null, Map.of(),
                        Map.of("attachments", List.of())),
                missionRecord(4, TraceRecordType.FRAME_CLOSED, "failed", null, Map.of("status", "failed"), Map.of()));
        var entries = new ExecutionJournalProjector().project(records).getEntriesSnapshot();
        assertThat(entries).hasSize(2);
        assertThat(entries.getLast().payload().has("attachments")).isFalse();
    }

    private TraceRecord missionRecord(long sequence, TraceRecordType type, String frameId, String parentId,
            Map<String, Object> metadata, Object data) {
        return new TraceRecord("trace", "session", sequence, Instant.parse("2026-03-24T12:00:00Z"),
                type, frameId, parentId, parentId == null ? ai.loomspan.internal.core.TraceFrameType.ROOT_MISSION
                        : ai.loomspan.internal.core.TraceFrameType.SKILL_EXECUTION,
                "default-sameSkill", "worker", metadata, OBJECT_MAPPER.valueToTree(data));
    }

    @Test
    void projectsMissionLifecycleWithoutExposingInputOrAuthentication() {
        var started = new TraceRecord("trace", "session", 1, Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.FRAME_OPENED, "root", null,
                ai.loomspan.internal.core.TraceFrameType.ROOT_MISSION, "default-javaRoot", "worker",
                Map.of(), OBJECT_MAPPER.valueToTree(Map.of("password", "secret", "authentication", "credentials")));
        var finished = new TraceRecord("trace", "session", 2, Instant.parse("2026-03-24T12:00:01Z"),
                TraceRecordType.FRAME_CLOSED, "root", null,
                ai.loomspan.internal.core.TraceFrameType.ROOT_MISSION, "default-javaRoot", "worker",
                Map.of("status", "completed"), null);
        var entries = new ExecutionJournalProjector().project(List.of(started, finished)).getEntriesSnapshot();
        assertThat(entries).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.SKILL_STARTED, JournalEntryType.SKILL_FINISHED);
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.frameId()).isEqualTo("root");
            assertThat(entry.route()).isEqualTo("default-javaRoot");
            assertThat(entry.payload().toString()).doesNotContain("secret", "credentials", "authentication");
        });
        assertThat(entries.getLast().payload().path("status").asText()).isEqualTo("completed");
    }

    @Test
    void projectsFailedModelAttemptAsCompactWarningWithoutDiagnosticBody()
    {
        TraceRecord failedAttempt = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.MODEL_ATTEMPT_FAILED,
                "frame-1",
                null,
                null,
                "default-rootVisibleSkill",
                "main",
                Map.of(
                        "attemptId", "attempt-1",
                        "attemptNumber", 1,
                        "providerAttemptNumber", 1,
                        "retrySequenceId", "retry-1",
                        "attemptReason", "INITIAL",
                        "failureClassification", "TRANSIENT",
                        "failureCategory", "TIMEOUT",
                        "retryDecision", "RETRY",
                        "retryDelayMillis", 25,
                        "retryDelaySource", "POLICY"),
                OBJECT_MAPPER.valueToTree(Map.of("diagnostics", List.of(Map.of(
                        "kind", "JAVA_STACK_TRACE",
                        "text", "stack must remain trace content only")))));

        List<JournalEntry> entries = new ExecutionJournalProjector().project(List.of(failedAttempt))
                .getEntriesSnapshot();

        assertThat(entries).singleElement().satisfies(entry ->
        {
            assertThat(entry.level()).isEqualTo(JournalLevel.WARN);
            assertThat(entry.type()).isEqualTo(JournalEntryType.MODEL_ATTEMPT_FAILURE);
            assertThat(entry.payload().toString()).doesNotContain("stack must remain trace content only", "diagnostics");
            assertThat(entry.payload().get("sourceRecordType").textValue())
                    .isEqualTo(TraceRecordType.MODEL_ATTEMPT_FAILED.name());
            assertThat(entry.payload().get("attemptId").textValue()).isEqualTo("attempt-1");
            assertThat(entry.payload().get("failureClassification").textValue()).isEqualTo("TRANSIENT");
            assertThat(entry.payload().get("failureCategory").textValue()).isEqualTo("TIMEOUT");
            assertThat(entry.payload().get("retryDecision").textValue()).isEqualTo("RETRY");
            assertThat(entry.payload().get("retryDelayMillis").intValue()).isEqualTo(25);
        });
    }

    @Test
    void derivesSanitizedDeveloperFacingJournalFromTrace() {
        ExecutionJournalProjector projector = new ExecutionJournalProjector();
        TraceRecord toolCall = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_STARTED,
                "frame-1",
                null,
                null,
                "default-rootVisibleSkill",
                "main",
                Map.of("capabilityName", "deploy.service", "linkedTaskId", "task-1"),
                OBJECT_MAPPER.valueToTree(Map.of(
                        "details", Map.of(
                                "arguments", Map.of(
                                        "Authorization", "Bearer super-secret",
                                        "token", "secret-value",
                                        "target", "service-a")))));
        TraceRecord error = new TraceRecord(
                "trace-1",
                "session-1",
                2,
                Instant.parse("2026-03-24T12:00:01Z"),
                TraceRecordType.ERROR_RECORDED,
                "frame-1",
                null,
                null,
                "default-rootVisibleSkill",
                "main",
                Map.of("exceptionType", "java.lang.IllegalStateException"),
                OBJECT_MAPPER.valueToTree(Map.of("message", "boom", "apiKey", "top-secret")));

        List<JournalEntry> entries = projector.project(List.of(toolCall, error)).getEntriesSnapshot();

        assertThat(entries).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.TOOL_CALL, JournalEntryType.ERROR);
        assertThat(entries.getFirst().payload().get("capabilityName").textValue()).isEqualTo("deploy.service");
        assertThat(entries.getFirst().payload().get("details").get("arguments").get("Authorization").textValue()).isEqualTo("[redacted]");
        assertThat(entries.getFirst().payload().get("details").get("arguments").get("token").textValue()).isEqualTo("[redacted]");
        assertThat(entries.getFirst().payload().get("details").get("arguments").get("target").textValue()).isEqualTo("service-a");
        assertThat(entries.get(1).payload().get("message").textValue()).isEqualTo("boom");
        assertThat(entries.get(1).payload().get("sourceRecordType").textValue()).isEqualTo(TraceRecordType.ERROR_RECORDED.name());
        assertThat(entries.get(1).payload().get("exceptionType").textValue()).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void errorProjectionPreservesClassificationExactMessageAndBothFailureIdentities() {
        var error = new TraceRecord("trace", "session", 1, Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.ERROR_RECORDED, "frame", null, null, "default-skill", "main",
                Map.of("failureId", "outer", "causedByFailureId", "prior"),
                OBJECT_MAPPER.valueToTree(Map.of("classification", "TERMINAL", "exceptionMessage", "  exact\n\u96ea\t",
                        "message", "Contextual summary", "exceptionType", "java.lang.IllegalStateException")));
        var payload = new ExecutionJournalProjector().project(List.of(error)).getEntriesSnapshot().getFirst().payload();
        assertThat(payload.path("classification").asText()).isEqualTo("TERMINAL");
        assertThat(payload.path("exceptionMessage").asText()).isEqualTo("  exact\n\u96ea\t");
        assertThat(payload.path("message").asText()).isEqualTo("Contextual summary");
        assertThat(payload.path("failureId").asText()).isEqualTo("outer");
        assertThat(payload.path("causedByFailureId").asText()).isEqualTo("prior");
    }

    @Test
    void preservesDistinctToolFailureAndErrorRecords() {
        ExecutionJournalProjector projector = new ExecutionJournalProjector();
        TraceRecord toolFailure = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_FAILED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of(
                        "capabilityName", "deploy.service",
                        "linkedTaskId", "task-1",
                        "message", "boom",
                        "exceptionType", "java.lang.IllegalStateException"),
                OBJECT_MAPPER.valueToTree(Map.of("arguments", Map.of("target", "service-a"))));
        TraceRecord genericError = new TraceRecord(
                "trace-1",
                "session-1",
                2,
                Instant.parse("2026-03-24T12:00:01Z"),
                TraceRecordType.ERROR_RECORDED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of(),
                OBJECT_MAPPER.valueToTree(Map.of(
                        "tool", "deploy.service",
                        "linkedTaskId", "task-1",
                        "message", "boom",
                        "exceptionType", "java.lang.IllegalStateException")));

        List<JournalEntry> entries = projector.project(List.of(toolFailure, genericError)).getEntriesSnapshot();

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.TOOL_FAILURE, JournalEntryType.ERROR);
        assertThat(entries.getFirst().payload().get("tool").textValue()).isEqualTo("deploy.service");
        assertThat(entries.getFirst().payload().get("message").textValue()).isEqualTo("boom");
        assertThat(entries.getFirst().payload().get("sourceRecordType").textValue()).isEqualTo(TraceRecordType.TOOL_CALL_FAILED.name());
        assertThat(entries.getFirst().payload().get("details").get("arguments").get("target").textValue()).isEqualTo("service-a");
        assertThat(entries.get(1).payload().get("tool").textValue()).isEqualTo("deploy.service");
        assertThat(entries.get(1).payload().get("message").textValue()).isEqualTo("boom");
        assertThat(entries.get(1).payload().get("sourceRecordType").textValue()).isEqualTo(TraceRecordType.ERROR_RECORDED.name());
    }

    @Test
    void surfacesNestedToolFailureSummaryFromTracePayload() {
        ExecutionJournalProjector projector = new ExecutionJournalProjector();
        TraceRecord toolFailure = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_FAILED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of("capabilityName", "deploy.service"),
                OBJECT_MAPPER.valueToTree(Map.of(
                        "arguments", Map.of("token", "secret-token"),
                        "failure", Map.of(
                                "linkedTaskId", "task-1",
                                "message", "router exploded",
                                "exceptionType", "java.lang.IllegalStateException",
                                "authorization", "Bearer abc"))));

        List<JournalEntry> entries = projector.project(List.of(toolFailure)).getEntriesSnapshot();

        assertThat(entries).singleElement().extracting(JournalEntry::type).isEqualTo(JournalEntryType.TOOL_FAILURE);
        assertThat(entries.getFirst().payload().get("tool").textValue()).isEqualTo("deploy.service");
        assertThat(entries.getFirst().payload().get("linkedTaskId").textValue()).isEqualTo("task-1");
        assertThat(entries.getFirst().payload().get("message").textValue()).isEqualTo("router exploded");
        assertThat(entries.getFirst().payload().get("exceptionType").textValue()).isEqualTo("java.lang.IllegalStateException");
        assertThat(entries.getFirst().payload().get("details").get("authorization").textValue()).isEqualTo("[redacted]");
    }

    @Test
    void preservesRepeatedLegitimateJournalEvents() {
        ExecutionJournalProjector projector = new ExecutionJournalProjector();
        TraceRecord firstToolResult = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_COMPLETED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of("capabilityName", "deploy.service", "linkedTaskId", "task-1"),
                OBJECT_MAPPER.valueToTree(Map.of("details", Map.of("result", "ok"))));
        TraceRecord secondToolResult = new TraceRecord(
                "trace-1",
                "session-1",
                2,
                Instant.parse("2026-03-24T12:00:01Z"),
                TraceRecordType.TOOL_CALL_COMPLETED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of("capabilityName", "deploy.service", "linkedTaskId", "task-1"),
                OBJECT_MAPPER.valueToTree(Map.of("details", Map.of("result", "ok"))));

        List<JournalEntry> entries = projector.project(List.of(firstToolResult, secondToolResult)).getEntriesSnapshot();

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(JournalEntry::type)
                .containsExactly(JournalEntryType.TOOL_RESULT, JournalEntryType.TOOL_RESULT);
    }

    @Test
    void doesNotInferUnplannedToolCallsFromLegacyMessageText() {
        ExecutionJournalProjector projector = new ExecutionJournalProjector();
        TraceRecord toolCall = new TraceRecord(
                "trace-1",
                "session-1",
                1,
                Instant.parse("2026-03-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_STARTED,
                "frame-1",
                null,
                null,
                "deploy.service",
                "main",
                Map.of("capabilityName", "deploy.service"),
                OBJECT_MAPPER.valueToTree(Map.of("message", "No unique ready task matched this tool call")));

        List<JournalEntry> entries = projector.project(List.of(toolCall)).getEntriesSnapshot();

        assertThat(entries).singleElement().extracting(JournalEntry::type).isEqualTo(JournalEntryType.TOOL_CALL);
    }
}
