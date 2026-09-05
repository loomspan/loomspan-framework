package ai.loomspan.internal.runtime.observation;

import tools.jackson.databind.node.StringNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveActivityProjectorTest
{
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final Set<TraceRecordType> VISIBLE = EnumSet.of(
            TraceRecordType.TRACE_STARTED,
            TraceRecordType.FRAME_OPENED,
            TraceRecordType.FRAME_CLOSED,
            TraceRecordType.MODEL_REQUEST_SENT,
            TraceRecordType.MODEL_RESPONSE_RECEIVED,
            TraceRecordType.MODEL_ATTEMPT_FAILED,
            TraceRecordType.PLAN_CREATED,
            TraceRecordType.PLAN_UPDATED,
            TraceRecordType.PLAN_VALIDATION_FAILED,
            TraceRecordType.PLAN_RETRY_REQUESTED,
            TraceRecordType.TOOL_CALL_STARTED,
            TraceRecordType.TOOL_CALL_COMPLETED,
            TraceRecordType.TOOL_CALL_FAILED,
            TraceRecordType.STEP_STARTED,
            TraceRecordType.STEP_ACTION_REJECTED,
            TraceRecordType.STEP_COMPLETED,
            TraceRecordType.STEP_FAILED,
            TraceRecordType.ERROR_RECORDED,
            TraceRecordType.TRACE_COMPLETED);

    @Test
    void sharedConcurrentFixtureMatchesEverySelectedActiveBranchPrefix() throws Exception
    {
        Path root = fixtureRoot();
        JsonNode expected = JSON.readTree(root.resolve("expected/canonical-concurrent-contract.json").toFile());
        Map<Long, JsonNode> prefixes = new LinkedHashMap<>();
        expected.path("activeBranchPrefixes").forEach(prefix ->
                prefixes.put(prefix.path("sequence").longValue(), prefix.path("branches")));

        ExecutionProjectionState state = new ExecutionProjectionState(
                "session-canonical-concurrent-contract", "test.entry");
        LiveActivityProjector projector = new LiveActivityProjector();
        for (String line : Files.readAllLines(root.resolve("traces/canonical-concurrent-contract.ndjson")))
        {
            TraceRecord record = JSON.readValue(line, TraceRecord.class);
            LiveActivityProjector.Projection projection = projector.project(state, record);
            JsonNode wanted = prefixes.get(record.sequence());
            if (wanted != null)
            {
                JsonNode actual = JSON.valueToTree(projection.snapshot().activeBranches());
                assertThat(actual)
                        .as("active branches at sequence " + record.sequence())
                        .isEqualTo(wanted);
            }
        }
        assertThat(prefixes).hasSize(10);
    }

    private static Path fixtureRoot()
    {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = cwd.resolve("loomspan-console-fixtures");
        return Files.isDirectory(direct) || Files.isDirectory(cwd.resolve("loomspan-spring-boot-starter"))
                ? direct
                : cwd.getParent().resolve("loomspan-console-fixtures");
    }

    @Test
    void projectsExactlyTheSettledVisibleRecordKinds()
    {
        LiveActivityProjector projector = new LiveActivityProjector();

        for (TraceRecordType type : TraceRecordType.values())
        {
            ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
            TraceFrameType frameType = type == TraceRecordType.FRAME_OPENED
                    || type == TraceRecordType.FRAME_CLOSED
                    ? TraceFrameType.SKILL_EXECUTION
                    : null;
            Map<String, Object> metadata = type == TraceRecordType.TRACE_COMPLETED
                    ? Map.of(
                            "outcome", "SUCCEEDED",
                            "sessionUsageSnapshot", SessionUsageSnapshot.empty())
                    : Map.of();

            LiveActivityProjector.Projection projection;
            if (type == TraceRecordType.FRAME_OPENED || type == TraceRecordType.FRAME_CLOSED)
            {
                projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                        TraceFrameType.ROOT_MISSION, "route", null));
                projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "skill", "root",
                        TraceFrameType.SKILL_EXECUTION, "nested", null));
                projection = type == TraceRecordType.FRAME_OPENED
                        ? projector.project(new ExecutionProjectionState("session", "route"),
                                frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                                        TraceFrameType.ROOT_MISSION, "route", null))
                        : projector.project(state, frame(TraceRecordType.FRAME_CLOSED, 3, "skill", "root",
                                TraceFrameType.SKILL_EXECUTION, "nested", null));
                if (type == TraceRecordType.FRAME_OPENED)
                {
                    state = new ExecutionProjectionState("session", "route");
                    projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                            TraceFrameType.ROOT_MISSION, "route", null));
                    projection = projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "skill", "root",
                            TraceFrameType.SKILL_EXECUTION, "nested", null));
                }
            }
            else
            {
                projection = projector.project(state, record(type, 1, frameType, metadata, null));
            }

            if (type == TraceRecordType.TRACE_COMPLETED)
            {
                assertThat(projection.activity()).as(type.name()).isNull();
                assertThat(projection.heldTerminal()).as(type.name()).isNotNull();
                assertThat(projection.heldTerminal().kind().name()).isEqualTo(type.name());
            }
            else if (VISIBLE.contains(type))
            {
                assertThat(projection.activity()).as(type.name()).isNotNull();
                assertThat(projection.activity().kind().name()).isEqualTo(type.name());
            }
            else
            {
                assertThat(projection.activity()).as(type.name()).isNull();
                assertThat(projection.heldTerminal()).as(type.name()).isNull();
            }
        }
    }

    @Test
    void projectsFailedStepWithExactErrorSummaryAndFailureIdentity()
    {
        ExecutionActivity activity = new LiveActivityProjector().project(
                new ExecutionProjectionState("session", "route"),
                record(TraceRecordType.STEP_FAILED, 1, TraceFrameType.STEP_EXECUTION,
                        Map.of("failureId", "failure-step", "stepNumber", 1), null)).activity();

        assertThat(activity.kind()).isEqualTo(ExecutionActivityKind.STEP_FAILED);
        assertThat(activity.summary()).isEqualTo("Step failed");
        assertThat(activity.details()).containsEntry("failureId", "failure-step");
    }

    @Test
    void projectsEnrichedPlanCreationMetadataAsBoundedNeutralFacts()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");

        ExecutionActivity activity = projector.project(state, record(
                TraceRecordType.PLAN_CREATED,
                1,
                TraceFrameType.PLANNING,
                Map.of(
                        "planId", "framework-plan",
                        "attemptId", "attempt-accepted",
                        "retrySequenceId", "retry-planning",
                        "untrustedExtra", "must-not-project"),
                StringNode.valueOf("secret normalized plan content"))).activity();

        assertThat(activity.kind()).isEqualTo(ExecutionActivityKind.PLAN_CREATED);
        assertThat(activity.summary()).isEqualTo("Plan created");
        assertThat(activity.details())
                .containsEntry("planId", "framework-plan")
                .containsEntry("attemptId", "attempt-accepted")
                .containsEntry("retrySequenceId", "retry-planning")
                .doesNotContainKey("untrustedExtra");
        assertThat(activity.toString()).doesNotContain("secret normalized plan content");
    }

    @Test
    void frameVisibilityIsLimitedToSkillExecutionButAllFramesUpdatePath()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");

        LiveActivityProjector.Projection root = projector.project(state,
                frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                        TraceFrameType.ROOT_MISSION, "route", null));
        LiveActivityProjector.Projection model = projector.project(state,
                frame(TraceRecordType.FRAME_OPENED, 2, "model", "root",
                        TraceFrameType.MODEL_CALL, "model", null));

        assertThat(root.activity()).isNull();
        assertThat(model.activity()).isNull();
        assertThat(model.snapshot().activeBranches()).singleElement()
                .extracting(branch -> branch.path().size()).isEqualTo(2);
        assertThat(model.snapshot().entrySkill()).isEqualTo("route");
    }

    @Test
    void projectsEveryOpenLeafAsCompleteBranchInLeafOpenSequenceOrder()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "step-a", "root",
                TraceFrameType.STEP_EXECUTION, "step-a", assignment("task-a", 1, "group", true)));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 3, "step-b", "root",
                TraceFrameType.STEP_EXECUTION, "step-b", assignment("task-b", 2, "group", true)));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 4, "model-b", "step-b",
                TraceFrameType.MODEL_CALL, "model-b", null));
        LiveActivityProjector.Projection projection = projector.project(state,
                frame(TraceRecordType.FRAME_OPENED, 5, "model-a", "step-a",
                        TraceFrameType.MODEL_CALL, "model-a", null));

        assertThat(projection.snapshot().activeBranches()).hasSize(2);
        assertThat(projection.snapshot().activeBranches()).extracting(ActiveExecutionSnapshot.ActiveBranch::taskId)
                .containsExactly("task-b", "task-a");
        assertThat(projection.snapshot().activeBranches()).extracting(branch ->
                        branch.path().stream().map(ActiveExecutionSnapshot.FramePathEntry::frameId).toList())
                .containsExactly(List.of("root", "step-b", "model-b"), List.of("root", "step-a", "model-a"));
        assertThat(projection.snapshot().activeBranches()).allSatisfy(branch ->
        {
            assertThat(branch.planId()).isEqualTo("plan");
            assertThat(branch.parallelGroup()).isEqualTo("group");
            assertThat(branch.effectiveConcurrency()).isTrue();
            assertThat(branch.path().getFirst()).isEqualTo(
                    new ActiveExecutionSnapshot.FramePathEntry("root", TraceFrameType.ROOT_MISSION, "route"));
        });
    }

    @Test
    void rejectsNonStepAndDuplicateExplicitAssignments()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        assertThatThrownBy(() -> projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "model", "root",
                TraceFrameType.MODEL_CALL, "model", assignment("task-a", 1, null, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("only on a step execution frame");

        ExecutionProjectionState duplicateState = new ExecutionProjectionState("session", "route");
        projector.project(duplicateState, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        projector.project(duplicateState, frame(TraceRecordType.FRAME_OPENED, 2, "step-a", "root",
                TraceFrameType.STEP_EXECUTION, "step-a", assignment("task-a", 1, null, false)));
        assertThatThrownBy(() -> projector.project(duplicateState, frame(TraceRecordType.FRAME_OPENED, 3, "step-a-duplicate", "root",
                TraceFrameType.STEP_EXECUTION, "step-a-duplicate", assignment("task-a", 1, null, false))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate explicit plan/task assignment");
    }

    @Test
    void nestedAssignmentShadowsItsAssignedAncestorForDescendants()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "outer-step", "root",
                TraceFrameType.STEP_EXECUTION, "outer", assignment("outer-plan", "outer-task", 1, null, false)));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 3, "inner-step", "outer-step",
                TraceFrameType.STEP_EXECUTION, "inner", assignment("inner-plan", "inner-task", 1, null, false)));
        LiveActivityProjector.Projection projection = projector.project(state,
                frame(TraceRecordType.FRAME_OPENED, 4, "inner-model", "inner-step",
                        TraceFrameType.MODEL_CALL, "model", null));

        assertThat(projection.snapshot().activeBranches()).singleElement().satisfies(branch ->
        {
            assertThat(branch.planId()).isEqualTo("inner-plan");
            assertThat(branch.taskId()).isEqualTo("inner-task");
            assertThat(branch.path()).extracting(ActiveExecutionSnapshot.FramePathEntry::frameId)
                    .containsExactly("root", "outer-step", "inner-step", "inner-model");
        });
    }

    @Test
    void closingLeavesRemovesOnlyThatBranchAndRestoresTheOpenParentLeaf()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 2, "a", "root",
                TraceFrameType.STEP_EXECUTION, "a", assignment("task-a", 1, "group", true)));
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 3, "b", "root",
                TraceFrameType.STEP_EXECUTION, "b", assignment("task-b", 2, "group", true)));

        LiveActivityProjector.Projection one = projector.project(state,
                frame(TraceRecordType.FRAME_CLOSED, 4, "a", "root", TraceFrameType.STEP_EXECUTION, "a", null));
        assertThat(one.snapshot().activeBranches()).singleElement()
                .extracting(ActiveExecutionSnapshot.ActiveBranch::taskId).isEqualTo("task-b");

        LiveActivityProjector.Projection coordinator = projector.project(state,
                frame(TraceRecordType.FRAME_CLOSED, 5, "b", "root", TraceFrameType.STEP_EXECUTION, "b", null));
        assertThat(coordinator.snapshot().activeBranches()).singleElement().satisfies(branch ->
        {
            assertThat(branch.path()).extracting(ActiveExecutionSnapshot.FramePathEntry::frameId)
                    .containsExactly("root");
            assertThat(branch.planId()).isNull();
            assertThat(branch.taskId()).isNull();
            assertThat(branch.stepNumber()).isNull();
            assertThat(branch.parallelGroup()).isNull();
            assertThat(branch.effectiveConcurrency()).isNull();
        });

        LiveActivityProjector.Projection empty = projector.project(state,
                frame(TraceRecordType.FRAME_CLOSED, 6, "root", null, TraceFrameType.ROOT_MISSION, "route", null));
        assertThat(empty.snapshot().activeBranches()).isEmpty();
    }

    @Test
    void treatsPlanAndStepIdentityWithoutAssignedTaskAsANonTaskBranch()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        var finalSynthesis = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        finalSynthesis.put("planId", "plan");
        finalSynthesis.put("stepNumber", 3);

        LiveActivityProjector.Projection projection = projector.project(state,
                frame(TraceRecordType.FRAME_OPENED, 2, "final", "root",
                        TraceFrameType.STEP_EXECUTION, "route#step-3", finalSynthesis));

        assertThat(projection.snapshot().activeBranches()).singleElement().satisfies(branch ->
        {
            assertThat(branch.planId()).isNull();
            assertThat(branch.taskId()).isNull();
            assertThat(branch.stepNumber()).isNull();
            assertThat(branch.parallelGroup()).isNull();
            assertThat(branch.effectiveConcurrency()).isNull();
            assertThat(branch.path()).extracting(ActiveExecutionSnapshot.FramePathEntry::frameId)
                    .containsExactly("root", "final");
        });
    }

    @Test
    void boundsPathTextAndDoesNotRetainLogicalPayload()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        String route = "\uD83D\uDE00".repeat(300);
        ExecutionProjectionState state = new ExecutionProjectionState("session", route);
        LiveActivityProjector.Projection projection = null;

        for (int index = 0; index < 70; index++)
        {
            projection = projector.project(state, new TraceRecord(
                    "trace", "session", index + 1L, Instant.parse("2026-07-24T12:00:00Z"),
                    TraceRecordType.FRAME_OPENED, "frame-" + index, index == 0 ? null : "frame-" + (index - 1),
                    index == 0 ? TraceFrameType.ROOT_MISSION : TraceFrameType.SKILL_EXECUTION,
                    route, "thread", Map.of(), StringNode.valueOf("SECRET-PAYLOAD")));
        }

        assertThat(projection).isNotNull();
        assertThat(projection.snapshot().activeBranches()).singleElement().satisfies(branch ->
        {
            assertThat(branch.path()).hasSize(70);
            assertThat(branch.path().getFirst().frameId()).isEqualTo("frame-0");
            assertThat(branch.path().getLast().frameId()).isEqualTo("frame-69");
        });
        String finalRoute = projection.snapshot().activeBranches().getFirst().path().getLast().route();
        assertThat(finalRoute.codePointCount(0, finalRoute.length()))
                .isEqualTo(ExecutionObservationLimits.TEXT_CODE_POINTS);
        assertThat(projection.toString()).doesNotContain("SECRET-PAYLOAD");
    }

    @Test
    void terminalUsageReplacesDerivedCountsAndCompletionIsHeld()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, record(
                TraceRecordType.TOOL_CALL_STARTED, 1, null, Map.of("capabilityName", "tool"), null));
        SessionUsageSnapshot terminal = new SessionUsageSnapshot(4, 5, 6, 7, 0, 8, 9, 17, 1, 2, 4);

        LiveActivityProjector.Projection projection = projector.project(state, record(
                TraceRecordType.TRACE_COMPLETED,
                2,
                null,
                Map.of("outcome", "FAILED", "sessionUsageSnapshot", terminal),
                null));

        assertThat(projection.snapshot().usage()).isEqualTo(terminal);
        assertThat(projection.snapshot().outcome()).isEqualTo(ai.loomspan.internal.core.TraceOutcome.FAILED);
        assertThat(projection.activity()).isNull();
        assertThat(projection.heldTerminal().kind()).isEqualTo(ExecutionActivityKind.TRACE_COMPLETED);
        assertThat(projection.heldTerminal().executionStatus()).isEqualTo("FAILED");
        assertThat(projection.heldTerminal().retainedWeight())
                .isEqualTo(expectedRetainedWeight(projection.heldTerminal()));
    }

    private static int expectedRetainedWeight(ExecutionActivity activity)
    {
        int weight = 128
                + ExecutionObservationLimits.utf8Weight(activity.sessionId())
                + ExecutionObservationLimits.utf8Weight(activity.traceId())
                + ExecutionObservationLimits.utf8Weight(activity.frameId())
                + ExecutionObservationLimits.utf8Weight(activity.parentFrameId())
                + ExecutionObservationLimits.utf8Weight(activity.route())
                + ExecutionObservationLimits.utf8Weight(activity.executionStatus())
                + ExecutionObservationLimits.utf8Weight(activity.kind().name())
                + ExecutionObservationLimits.utf8Weight(activity.summary());
        for (Map.Entry<String, Object> entry : activity.details().entrySet())
        {
            weight += ExecutionObservationLimits.utf8Weight(entry.getKey())
                    + ExecutionObservationLimits.utf8Weight(String.valueOf(entry.getValue())) + 8;
        }
        return Math.max(1, weight);
    }

    @Test
    void projectsParentIdentityAndTruthfulExecutionStatus()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        TraceRecord nested = new TraceRecord(
                "trace", "session", 1, Instant.parse("2026-07-24T12:00:00Z"),
                TraceRecordType.TOOL_CALL_STARTED, "child-frame", "parent-frame",
                TraceFrameType.TOOL_INVOCATION, "route", "thread", Map.of(), null);

        ExecutionActivity activity = projector.project(state, nested).activity();

        assertThat(activity.parentFrameId()).isEqualTo("parent-frame");
        assertThat(activity.executionStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void derivesCountsAndNormalizedModelUsageFromCanonicalFacts()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, record(
                TraceRecordType.FRAME_OPENED, 1, TraceFrameType.ROOT_MISSION, Map.of(), null));
        projector.project(state, record(
                TraceRecordType.TOOL_CALL_STARTED, 2, null, Map.of(), null));
        projector.project(state, record(
                TraceRecordType.PLAN_RETRY_REQUESTED, 3, null, Map.of(), null));
        LiveActivityProjector.Projection projection = projector.project(state, record(
                TraceRecordType.MODEL_RESPONSE_RECEIVED,
                4,
                null,
                Map.of("usage", Map.of(
                        "promptUnits", 2,
                        "completionUnits", 3,
                        "totalUnits", 5,
                        "precision", "EXACT")),
                null));

        assertThat(projection.snapshot().usage())
                .isEqualTo(new SessionUsageSnapshot(1, 1, 1, 1, 0, 2, 3, 5, 1, 0, 0));
    }

    @Test
    void toolStartActivityExcludesArgumentsAndCountsOnce()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        LiveActivityProjector.Projection projection = projector.project(state, record(
                TraceRecordType.TOOL_CALL_STARTED,
                1,
                TraceFrameType.TOOL_INVOCATION,
                Map.of("capabilityName", "lookupCustomer", "linkedTaskId", "task-1"),
                StringNode.valueOf("{\"details\":{\"arguments\":{\"password\":\"must-not-render\"}}}")));

        assertThat(projection.snapshot().usage().toolInvocations()).isEqualTo(1);
        assertThat(projection.activity().summary()).isEqualTo("Tool call started");
        assertThat(projection.activity().details())
                .containsEntry("capabilityName", "lookupCustomer")
                .containsEntry("linkedTaskId", "task-1")
                .doesNotContainKey("arguments");
        assertThat(projection.activity().toString()).doesNotContain("must-not-render", "password");
    }

    @Test
    void activityDtoEnforcesTextDetailAndEnvelopeBounds()
    {
        java.util.LinkedHashMap<String, Object> thirtyTwo = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 32; index++)
        {
            thirtyTwo.put("k" + index, "v");
        }
        ExecutionActivity accepted = new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "😀".repeat(600), thirtyTwo, ExecutionObservationLimits.ACTIVITY_UTF8_BYTES);
        assertThat(accepted.summary().codePointCount(0, accepted.summary().length()))
                .isEqualTo(ExecutionObservationLimits.SUMMARY_CODE_POINTS);
        assertThat(accepted.details()).hasSize(ExecutionObservationLimits.DETAIL_FIELDS);

        thirtyTwo.put("overflow", "v");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "summary", thirtyTwo, 100))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "summary", Map.of(), ExecutionObservationLimits.ACTIVITY_UTF8_BYTES + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void providerRetryActivityContainsOnlyBoundedNeutralFacts()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        LiveActivityProjector.Projection projection = projector.project(state, record(
                TraceRecordType.MODEL_ATTEMPT_FAILED, 1, null,
                Map.of("providerAttemptNumber", 2, "attemptReason", "PROVIDER_RETRY",
                        "failureClassification", "TRANSIENT", "failureCategory", "RATE_LIMITED",
                        "retryDecision", "RETRY", "retryDelayMillis", 750,
                        "retryDelaySource", "RETRY_AFTER", "summary", "secret provider body"),
                StringNode.valueOf("secret provider body and partial assistant content")));

        assertThat(projection.activity().summary()).isEqualTo("Provider attempt 2 failed; retrying in 750 ms");
        assertThat(projection.activity().details())
                .containsEntry("failureCategory", "RATE_LIMITED")
                .containsEntry("retryDecision", "RETRY")
                .doesNotContainKey("summary");
        assertThat(projection.activity().toString())
                .doesNotContain("secret provider body", "partial assistant content");
    }

    @Test
    void doesNotTruncateManySimultaneousBranchesAndEnforcesDetailByteBoundaries()
    {
        LiveActivityProjector projector = new LiveActivityProjector();
        ExecutionProjectionState state = new ExecutionProjectionState("session", "route");
        projector.project(state, frame(TraceRecordType.FRAME_OPENED, 1, "root", null,
                TraceFrameType.ROOT_MISSION, "route", null));
        LiveActivityProjector.Projection projection = null;
        for (int index = 0; index < 70; index++)
        {
            projection = projector.project(state, frame(TraceRecordType.FRAME_OPENED, index + 2L,
                    "leaf-" + index, "root", TraceFrameType.SKILL_EXECUTION, "route", null));
        }
        assertThat(projection.snapshot().activeBranches()).hasSize(70);
        assertThat(projection.snapshot().activeBranches()).allSatisfy(branch -> assertThat(branch.path()).hasSize(2));

        java.util.LinkedHashMap<String, Object> exactBytes = new java.util.LinkedHashMap<>();
        for (int index = 0; index < 32; index++)
        {
            String prefix = "k" + index;
            exactBytes.put(prefix + "x".repeat(128 - prefix.length()), "v".repeat(128));
        }
        ExecutionActivity exact = new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "summary", exactBytes, 100);
        assertThat(exact.details()).hasSize(32);

        java.util.LinkedHashMap<String, Object> overBytes = new java.util.LinkedHashMap<>(exactBytes);
        String firstKey = overBytes.keySet().iterator().next();
        overBytes.put(firstKey, "v".repeat(129));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ExecutionActivity(
                0, "session", "trace", 1L, Instant.parse("2026-07-24T12:00:00Z"),
                ExecutionActivityKind.TRACE_STARTED, null, null, null, null, null,
                "summary", overBytes, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("byte limit");
    }

    private TraceRecord record(
            TraceRecordType type,
            long sequence,
            TraceFrameType frameType,
            Map<String, Object> metadata,
            StringNode data)
    {
        return new TraceRecord(
                "trace", "session", sequence, Instant.parse("2026-07-24T12:00:00Z"), type,
                frameType == null ? null : "frame-" + sequence, null, frameType,
                frameType == null ? null : "route", "thread", metadata, data);
    }

    private TraceRecord frame(TraceRecordType type, long sequence, String frameId, String parentFrameId,
            TraceFrameType frameType, String route, tools.jackson.databind.JsonNode data)
    {
        return new TraceRecord("trace", "session", sequence, Instant.parse("2026-07-24T12:00:00Z"), type,
                frameId, parentFrameId, frameType, route, "thread", Map.of(), data);
    }

    private tools.jackson.databind.JsonNode assignment(
            String taskId, int stepNumber, String parallelGroup, boolean effectiveConcurrency)
    {
        return assignment("plan", taskId, stepNumber, parallelGroup, effectiveConcurrency);
    }

    private tools.jackson.databind.JsonNode assignment(
            String planId, String taskId, int stepNumber, String parallelGroup, boolean effectiveConcurrency)
    {
        var node = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        node.put("planId", planId);
        node.put("assignedTaskId", taskId);
        node.put("stepNumber", stepNumber);
        if (parallelGroup == null) node.putNull("parallelGroup"); else node.put("parallelGroup", parallelGroup);
        node.put("effectiveConcurrency", effectiveConcurrency);
        return node;
    }
}
