package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DefaultExecutionTraceRecorderAttachmentTest
{
    private final DefaultExecutionTraceRecorder recorder = new DefaultExecutionTraceRecorder(
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    private static Map<String, Object> descriptor(String pointer, String source)
    {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("attachment", true); result.put("fieldPath", pointer); result.put("name", null);
        result.put("contentType", "application/pdf"); result.put("mediaType", "PDF"); result.put("source", source);
        result.put("sizeBytes", 4L); result.put("sha256", "abc");
        result.put("metadata", Map.of("token", "canonical-value", "note", "exact"));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static ExecutionFrame frame(String id, String parent, TraceFrameType type, String route)
    {
        return new ExecutionFrame(id, parent, OperationType.CAPABILITY, type, route, Map.of(), Instant.EPOCH, null, null);
    }

    @Test
    void bindsCollectionToCapturedMissionWhileAnotherFrameIsActive()
    {
        var session = TestLoomspanSessions.create("attachment-recorder-owner", "default-owner", 5, null, TracePersistencePolicy.ALWAYS);
        var binding = TestExecutionBindings.missionBinding(session);
        var missionId = binding.requireMission().missionFrameId();
        binding.branch().push(frame("planning", missionId, TraceFrameType.PLANNING, "default-owner#planning"));
        binding.branch().push(frame("model", "planning", TraceFrameType.MODEL_CALL, "default-owner#model"));
        var descriptors = List.of(descriptor("/a.b", "ref://one.pdf"), descriptor("/a/b", "resource"));
        ExecutionBindingScope.runWith(binding, () -> recorder.recordAttachments(session, "default-owner", descriptors));
        var records = attachmentRecords(session);
        assertThat(records).hasSize(1);
        var record = records.getFirst();
        assertThat(record.recordType()).isEqualTo(TraceRecordType.FRAME_METADATA_RECORDED);
        assertThat(record.frameId()).isEqualTo(missionId);
        assertThat(record.frameType()).isEqualTo(TraceFrameType.ROOT_MISSION);
        assertThat(record.route()).isEqualTo("default-owner");
        assertThat(record.data().path("attachments").get(0).path("fieldPath").asText()).isEqualTo("/a.b");
        assertThat(record.data().path("attachments").get(1).path("fieldPath").asText()).isEqualTo("/a/b");
        assertThat(record.data().path("attachments").get(0).path("name").isNull()).isTrue();
        assertThat(record.data().path("attachments").get(0).path("metadata").path("token").asText()).isEqualTo("canonical-value");
        assertThat(record.data().toString()).doesNotContain("ByteArrayResource", "byteArray");
    }

    @Test
    void isolatesNestedSameRouteMissionMetadataByFrameIdentity()
    {
        var session = TestLoomspanSessions.create("attachment-recorder-nested", "default-owner", 5, null, TracePersistencePolicy.ALWAYS);
        var parent = TestExecutionBindings.missionBinding(session);
        var parentId = parent.requireMission().missionFrameId();
        var childFrame = frame("child", parentId, TraceFrameType.SKILL_EXECUTION, "default-owner");
        parent.branch().push(childFrame);
        var childMission = new MissionContext(session, "default-owner", "child", parent.requireMission());
        var child = parent.withMission(childMission);
        ExecutionBindingScope.runWith(child, () -> recorder.recordAttachments(session, "default-owner", List.of(descriptor("/child", "resource"))));
        parent.branch().close(childFrame);
        ExecutionBindingScope.runWith(parent, () -> recorder.recordAttachments(session, "default-owner", List.of(descriptor("/parent", "ref://parent.pdf"))));
        var records = attachmentRecords(session);
        assertThat(records).extracting(TraceRecord::frameId).containsExactly("child", parentId);
        assertThat(records).extracting(record -> record.data().path("attachments").get(0).path("fieldPath").asText())
                .containsExactly("/child", "/parent");
    }

    @Test
    void rejectsWrongMissionOrSessionAndDoesNotInventEmptyCollections()
    {
        var session = TestLoomspanSessions.create("attachment-recorder-guards", "default-owner", 3, null, TracePersistencePolicy.ALWAYS);
        var binding = TestExecutionBindings.missionBinding(session);
        var other = TestLoomspanSessions.create("attachment-recorder-other", "default-owner", 3, null, TracePersistencePolicy.ALWAYS);
        ExecutionBindingScope.runWith(binding, () -> {
            assertThatThrownBy(() -> recorder.recordAttachments(session, "default-other", List.of(descriptor("/x", "resource"))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("owning mission");
            assertThatThrownBy(() -> recorder.recordAttachments(other, "default-owner", List.of(descriptor("/x", "resource"))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("session");
            recorder.recordAttachments(session, "default-owner", List.of());
        });
        var records = attachmentRecords(session);
        assertThat(records).isEmpty();
    }

    private static List<TraceRecord> attachmentRecords(LoomspanSession session)
    {
        var records = new ArrayList<TraceRecord>();
        session.readTraceRecords(record -> {
            if (record.recordType() == TraceRecordType.FRAME_METADATA_RECORDED) records.add(record);
        });
        return records;
    }
}
