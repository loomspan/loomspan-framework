package ai.loomspan.internal.runtime.planning;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.core.PlanStatus;
import ai.loomspan.internal.core.PlanTask;
import ai.loomspan.internal.core.PlanTaskStatus;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.SimpleChatClient;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.junit.jupiter.api.Test;
import ai.loomspan.internal.runtime.tool.BoundCapability;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidencePlanningIntegrationTest
{
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void acceptsEitherInvestigatorWithoutRequiringBothAndRendersTheCanonicalExpression()
    {
        for (String investigator : List.of("investigateNetwork", "investigateApp"))
        {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-" + investigator, "test.entry", 3);
            SimpleChatClient client = new SimpleChatClient(incidentPlan(investigator), "unused");

            ExecutionPlan result = ai.loomspan.internal.core.TestExecutionBindings.callWithSession(
                    session, () -> planningService.initializePlan(
                    session,
                    "handle incident",
                    null,
                    incidentDefinition(),
                    client,
                    incidentTools()).orElseThrow());

            assertThat(result.tasks()).extracting(PlanTask::capabilityName)
                    .contains("classifyIncident", investigator, "draftIncidentResponse")
                    .doesNotContain(investigator.equals("investigateNetwork") ? "investigateApp" : "investigateNetwork");
            assertThat(client.getSystemMessagesSeen().getFirst())
                    .contains("classifyIncident and (investigateNetwork or investigateApp)")
                    .contains("For an 'or' group, include any one alternative")
                    .doesNotContain("[investigateNetwork, investigateApp] tool(s)");
        }
    }

    @Test
    void failedPlanningTraceRetainsStructuredBooleanRequirementsWithoutLegacyAliases()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-gap", "test.entry", 3);
        SimpleChatClient client = new SimpleChatClient(classificationOnlyPlan(), "unused");

        assertThatThrownBy(() -> ai.loomspan.internal.core.TestExecutionBindings.callWithSession(
                session, () -> planningService.initializePlan(
                session,
                "handle incident",
                null,
                incidentDefinition(),
                client,
                incidentTools())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Plan validation failed");

        List<TraceRecord> records = new ArrayList<>();
        session.readTraceRecords(records::add);
        assertThat(records).filteredOn(record -> record.recordType() == TraceRecordType.PLAN_VALIDATION_FAILED
                        && java.util.List.of("evidence-coverage").equals(record.metadata().get("issueCodes")))
                .isNotEmpty()
                .allSatisfy(record ->
                {
                    assertThat(record.metadata()).containsKeys(
                            "unsatisfiedClaims", "requiredExpressions", "satisfiedSkills", "unsatisfiedRequirements");
                    assertThat(record.metadata()).doesNotContainKey("missingEvidence");
                    assertThat(record.data().toString())
                            .contains("classifyIncident and (investigateNetwork or investigateApp)")
                            .contains("\"mode\":\"any\"");
                });
        assertThat(client.getSystemMessagesSeen()).anySatisfy(message -> assertThat(message)
                .contains("classifyIncident and (investigateNetwork or investigateApp)")
                .contains("already planned: [classifyIncident, draftIncidentResponse]"));
    }

    private static YamlSkillDefinition incidentDefinition()
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("handleIncident");
        manifest.setDescription("Handle an incident");
        manifest.setModel("gpt-5");
        YamlSkillManifest.OutputSchemaManifest schema = new YamlSkillManifest.OutputSchemaManifest();
        schema.setType("object");
        schema.setAdditionalProperties(false);
        schema.setProperties(Map.of(
                "severity", scalar("string"),
                "likelyCause", scalar("string"),
                "userMessage", scalar("string")));
        schema.setRequired(List.of("severity", "userMessage"));
        manifest.setOutputSchema(schema);
        Map<String, String> evidence = Map.of(
                "severity", "classifyIncident",
                "likelyCause", "classifyIncident and (investigateNetwork or investigateApp)",
                "userMessage", "draftIncidentResponse");
        EffectiveSkillExecutionConfiguration configuration = new EffectiveSkillExecutionConfiguration(
                "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");
        return new YamlSkillDefinition(
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                configuration,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(evidence));
    }

    private static YamlSkillManifest.OutputSchemaManifest scalar(String type)
    {
        YamlSkillManifest.OutputSchemaManifest scalar = new YamlSkillManifest.OutputSchemaManifest();
        scalar.setType(type);
        return scalar;
    }

    private static List<BoundCapability> incidentTools()
    {
        return List.of(
                tool("classifyIncident"),
                tool("investigateNetwork"),
                tool("investigateApp"),
                tool("draftIncidentResponse"));
    }

    private static BoundCapability tool(String name)
    {
        return ai.loomspan.testkit.TestBoundCapabilities.describedCapability(name, "Use " + name);
    }

    private static ExecutionPlan incidentPlan(String investigator)
    {
        return plan(List.of("classifyIncident", investigator, "draftIncidentResponse"));
    }

    private static ExecutionPlan classificationOnlyPlan()
    {
        return plan(List.of("classifyIncident", "draftIncidentResponse", "draftIncidentResponse"));
    }

    private static ExecutionPlan plan(List<String> capabilities)
    {
        List<PlanTask> tasks = java.util.stream.IntStream.range(0, capabilities.size())
                .mapToObj(index -> new PlanTask(
                        "task-" + index,
                        "Task " + index,
                        PlanTaskStatus.PENDING,
                        capabilities.get(index),
                        "Use " + capabilities.get(index),
                        index == 0 ? List.of() : List.of("task-" + (index - 1)),
                        List.of("result"),
                        null,
                        null))
                .toList();
        return new ExecutionPlan(
                "plan-incident",
                "handleIncident",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                tasks);
    }
}
