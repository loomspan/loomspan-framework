package ai.loomspan.internal.runtime.trace;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.tool.DefaultCapabilityInvoker;
import ai.loomspan.internal.security.SkillAccessPolicy;
import ai.loomspan.internal.skill.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual invoker frames round-trip through the canonical session writer and reader. */
class DeclaredChildInputBindingsTraceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC);
    private static ChildInputBinding binding(String destination, ChildInputBinding.SourceKind kind, String path, String skill) {
        return new ChildInputBinding(ObjectFieldPath.parse(destination, false), kind, ObjectFieldPath.parse(path, true), skill);
    }
    private static CapabilityMetadata capability() {
        var resolver = new SkillInputContractResolver();
        var contract = resolver.resolveFromToolSchema("""
                {"type":"object","properties":{"caseId":{"type":"string"},"evidence":{"type":"object","additionalProperties":true},"count":{"type":"integer"}},"required":["caseId","evidence","count"],"additionalProperties":false}
                """);
        return new CapabilityMetadata("java:consumer", "consumer", "consume", SkillExecutionDescriptor.none(),
                SkillAccessPolicy.unrestricted(), args -> "DONE", CapabilityKind.JAVA_SKILL,
                new CapabilityToolDescriptor("consumer", "consume", resolver.toJsonSchema(contract), null), contract, null);
    }
    private static YamlSkillDefinition definition(List<ChildInputBinding> bindings) {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("parent"); manifest.setDescription("parent"); manifest.setModel("model"); manifest.setPlanningMode(true);
        return new YamlSkillDefinition(new ByteArrayResource(new byte[0]), manifest,
                new EffectiveSkillExecutionConfiguration("model", "local", AiDriver.OPENAI, "local", null),
                ai.loomspan.internal.runtime.evidence.EvidenceContract.empty(), null,
                List.of(new AllowedSkillConstraint("consumer", null, null, false, bindings)));
    }
    private static PlanTask task(String id, String skill, PlanTaskStatus status, List<String> dependencies) {
        return new PlanTask(id, id, status, skill, "work", dependencies, List.of(), null, null);
    }
    private static List<TraceRecord> records(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>(); session.readTraceRecords(records::add); return records;
    }
    @Test void canonicalEvidenceDistinguishesRawArgumentsDeliveredInputAndSourceProvenance() {
        var session = TestLoomspanSessions.withId("binding-trace", "parent", 5);
        var execution = TestExecutionBindings.missionBinding(session);
        var mission = execution.requireMission();
        mission.captureInput(Map.of("requestId", "source-case"));
        String originalResult = "{\"data\":{\"amount\":12345,\"facts\":[\"unchanged\"]}}";
        mission.recordCompletedTaskResult("producer-task", "producer", originalResult);
        mission.storePlan(new ExecutionPlan("plan", "parent", CLOCK.instant(), List.of(
                task("producer-task", "producer", PlanTaskStatus.COMPLETED, List.of()),
                task("consumer-task", "consumer", PlanTaskStatus.PENDING, List.of("producer-task")))));
        var router = mock(CapabilityExecutionRouter.class);
        when(router.executeAssembled(any(), anyMap(), eq(session), isNull(), anyList())).thenReturn("DONE");
        var state = new DefaultExecutionStateService(CLOCK);
        var invoker = new DefaultCapabilityInvoker(router, mock(PlanningService.class), state);
        var bindings = List.of(binding("/caseId", ChildInputBinding.SourceKind.INPUT, "/requestId", null),
                binding("/evidence", ChildInputBinding.SourceKind.CHILD_RESULT, "/data", "producer"));
        Map<String,Object> rawArguments = Map.of("count", "7");
        ExecutionBindingScope.supplyWith(execution, () -> {
            state.recordStepEvent(session, execution.branch().leaf().orElseThrow(), TraceRecordType.STEP_ACTION_PROPOSED,
                    Map.of("taskId", "consumer-task"), Map.of("toolArguments", rawArguments));
            var bound = invoker.bind(session, definition(bindings), List.of(capability()), null).getFirst();
            assertThat(bound.invokeAssigned(rawArguments, "consumer-task", mission.completedTaskResults())).isEqualTo("DONE");
            return null;
        });
        var read = records(session);
        var tool = read.stream().filter(record -> record.recordType() == TraceRecordType.FRAME_OPENED && record.frameType() == TraceFrameType.TOOL_INVOCATION).findFirst().orElseThrow();
        assertThat(tool.parentFrameId()).isEqualTo(mission.missionFrameId());
        var payload = tool.data();
        assertThat(payload.path("linkedTaskId").asText()).isEqualTo("consumer-task");
        assertThat(payload.path("arguments").path("caseId").asText()).isEqualTo("source-case");
        assertThat(payload.path("arguments").path("count").intValue()).isEqualTo(7);
        assertThat(payload.path("arguments").path("count").isIntegralNumber()).isTrue();
        assertThat(payload.path("arguments").path("evidence").path("facts").get(0).asText()).isEqualTo("unchanged");
        var provenance = payload.path("inputBindings");
        assertThat(provenance.size()).isEqualTo(2);
        assertThat(provenance.get(0).path("destination").asText()).isEqualTo("/caseId");
        assertThat(provenance.get(0).path("sourceKind").asText()).isEqualTo("input");
        assertThat(provenance.get(0).path("sourcePath").asText()).isEqualTo("/requestId");
        var result = provenance.get(1);
        assertThat(result.path("destination").asText()).isEqualTo("/evidence");
        assertThat(result.path("sourceKind").asText()).isEqualTo("child_result");
        assertThat(result.path("sourcePath").asText()).isEqualTo("/data");
        assertThat(result.path("sourceTaskId").asText()).isEqualTo("producer-task");
        assertThat(result.path("sourceSkill").asText()).isEqualTo("producer");
        assertThat(result.path("parentMissionFrameId").asText()).isEqualTo(mission.missionFrameId());
        assertThat(result.propertyNames()).containsExactlyInAnyOrder("destination", "sourceKind", "sourcePath", "sourceTaskId", "sourceSkill", "parentMissionFrameId");
        assertThat(provenance.toString()).doesNotContain("12345", "unchanged");
        assertThat(read).filteredOn(record -> record.recordType() == TraceRecordType.STEP_ACTION_PROPOSED).singleElement()
                .satisfies(record -> assertThat(record.data().path("toolArguments").path("count").asText()).isEqualTo("7"));
        assertThat(mission.completedTaskResult("producer-task").orElseThrow().result()).isEqualTo(originalResult);
        verify(router).executeAssembled(any(), argThat(args -> args.get("caseId").equals("source-case")), eq(session), isNull(), anyList());
    }
    @Test void failedSourceRecordsRawArgumentsAndAttributableBindingWithoutDispatch() {
        var session = TestLoomspanSessions.withId("binding-failed-trace", "parent", 5);
        var execution = TestExecutionBindings.missionBinding(session);
        execution.requireMission().captureInput(Map.of("requestId", "source-case"));
        var router = mock(CapabilityExecutionRouter.class);
        var state = new DefaultExecutionStateService(CLOCK);
        var invoker = new DefaultCapabilityInvoker(router, mock(PlanningService.class), state);
        var bindings = List.of(binding("/caseId", ChildInputBinding.SourceKind.INPUT, "/missing", null));
        assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(execution, () ->
                invoker.bind(session, definition(bindings), List.of(capability()), null).getFirst()
                        .invokeAssigned(Map.of("count", "7"), "consumer-task", List.of())))
                .hasMessageContaining("binding_source_unavailable").hasMessageContaining("/missing").hasMessageContaining("/caseId");
        var read = records(session);
        assertThat(read).noneMatch(record -> record.recordType() == TraceRecordType.FRAME_OPENED && record.frameType() == TraceFrameType.TOOL_INVOCATION);
        assertThat(read).filteredOn(record -> record.recordType() == TraceRecordType.TOOL_CALL_FAILED).singleElement().satisfies(record -> {
            assertThat(record.metadata()).containsEntry("linkedTaskId", "consumer-task");
            assertThat(record.data().toString()).contains("binding_source_unavailable", "/missing", "/caseId", "parentMissionFrameId", "\"count\":\"7\"");
        });
        verifyNoInteractions(router);
    }
}
