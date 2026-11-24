package ai.loomspan.internal.runtime.planning;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;

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
        for (String investigator : List.of("default:investigate-network", "default:investigate-app"))
        {
            DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
            DefaultPlanningService planningService = new DefaultPlanningService(stateService);
            LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId(
                    "session-" + investigator.substring(investigator.indexOf(':') + 1), "default:test-entry", 3);
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
                    .contains("default:classify-incident", investigator, "default:draft-incident-response")
                    .doesNotContain(investigator.equals("default:investigate-network") ? "default:investigate-app" : "default:investigate-network");
            assertThat(client.getSystemMessagesSeen().getFirst())
                    .contains("default:classify-incident and (default:investigate-network or default:investigate-app)")
                    .contains("For an 'or' group, include any one alternative")
                    .doesNotContain("[default:investigate-network, default:investigate-app] tool(s)");
        }
    }

    @Test
    void failedPlanningTraceRetainsStructuredBooleanRequirementsWithoutLegacyAliases()
    {
        DefaultExecutionStateService stateService = new DefaultExecutionStateService(FIXED_CLOCK);
        DefaultPlanningService planningService = new DefaultPlanningService(stateService);
        LoomspanSession session = ai.loomspan.internal.core.TestLoomspanSessions.withId("session-gap", "default:test-entry", 3);
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
                            .contains("default:classify-incident and (default:investigate-network or default:investigate-app)")
                            .contains("\"mode\":\"any\"");
                });
        assertThat(client.getSystemMessagesSeen()).anySatisfy(message -> assertThat(message)
                .contains("default:classify-incident and (default:investigate-network or default:investigate-app)")
                .contains("already planned: [default:classify-incident, default:draft-incident-response]"));
    }

    private static YamlSkillDefinition incidentDefinition()
    {
        YamlSkillManifest manifest = new YamlSkillManifest();
        manifest.setName("handle-incident");
        manifest.setDescription("Handle an incident");
        manifest.setModel("gpt-5");
        tools.jackson.databind.node.ObjectNode schema = LoomspanJacksonCodecs.defaults().schemaTree().createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("properties", LoomspanJacksonCodecs.defaults().schemaTree().valueToTree(Map.of(
                "severity", scalar("string"),
                "likelyCause", scalar("string"),
                "userMessage", scalar("string"))));
        schema.set("required", LoomspanJacksonCodecs.defaults().schemaTree().valueToTree(List.of("severity", "userMessage")));
        manifest.setOutputSchema(schema);
        Map<String, String> evidence = Map.of(
                "severity", "default:classify-incident",
                "likelyCause", "default:classify-incident and (default:investigate-network or default:investigate-app)",
                "userMessage", "default:draft-incident-response");
        EffectiveSkillExecutionConfiguration configuration = new EffectiveSkillExecutionConfiguration(
                "gpt-5", "test-connection", AiDriver.OPENAI, "openai/gpt-5", "medium");
        return new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "default",
                new org.springframework.core.io.ByteArrayResource(new byte[0]),
                manifest,
                configuration,
                ai.loomspan.internal.runtime.evidence.TestEvidenceContracts.compiled(evidence));
    }

    private static tools.jackson.databind.node.ObjectNode scalar(String type)
    {
        tools.jackson.databind.node.ObjectNode scalar = LoomspanJacksonCodecs.defaults().schemaTree().createObjectNode();
        scalar.put("type", type);
        return scalar;
    }

    private static List<BoundCapability> incidentTools()
    {
        return List.of(
                tool("default:classify-incident"),
                tool("default:investigate-network"),
                tool("default:investigate-app"),
                tool("default:draft-incident-response"));
    }

    private static BoundCapability tool(String name)
    {
        return ai.loomspan.testkit.TestBoundCapabilities.describedCapability(name, "Use " + name);
    }

    private static ExecutionPlan incidentPlan(String investigator)
    {
        return plan(List.of("default:classify-incident", investigator, "default:draft-incident-response"));
    }

    private static ExecutionPlan classificationOnlyPlan()
    {
        return plan(List.of("default:classify-incident", "default:draft-incident-response", "default:draft-incident-response"));
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
                "default:handle-incident",
                Instant.parse("2026-03-15T12:00:00Z"),
                PlanStatus.VALID,
                tasks);
    }
}
