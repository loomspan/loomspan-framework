package ai.loomspan.internal.runtime.step;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.api.SkillDocument;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.tool.BoundCapability;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Opt-in capture using immutable sibling-suite sources, with no provider or tool side effects. */
@EnabledIfSystemProperty(named = "pr18.diagnostic", matches = "true")
class Pr18RecordedHandoffDiagnosticTest {
    @Test
    @SuppressWarnings("unchecked")
    void evaluatesRetainedLiveActionsWithUnchangedFrameworkContract() throws Exception {
        Path output = Path.of("ai/thoughts/evidence/2026-10-03-PR-18-generated-input-guidance");
        var codecs = LoomspanJacksonCodecs.defaults();
        var child = loadRecordedSkills().getSkill("compareOptions");
        var contract = new SkillInputContractResolver().resolveYamlCapability(child);
        assertRecordedBoundaries(contract);
        var validator = new ai.loomspan.internal.runtime.input.SkillInputValidator();
        assertThat(validator.validate(Map.of("forbiddenRootField", "unchanged"), contract).issues())
                .extracting(ai.loomspan.internal.runtime.input.SkillInputValidationIssue::code).contains("unknown_field");
        List<Map<String,Object>> outcomes = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var files = Files.list(output)) {
            var engine = new StepLoopMissionExecutionEngine(mock(PlanningService.class),
                    new DefaultExecutionStateService(Clock.systemUTC()), Duration.ofSeconds(30), executor, new NoOpSessionUsageService());
            for (Path file : files.filter(p -> p.getFileName().toString().matches("live-.*-response\\.json")).sorted().toList()) {
                String actionFile = file.getFileName().toString().replace("-response.json", "-action.json");
                try {
                    var saved = codecs.planningJson().readValue(Files.readString(file), Map.class);
                    var response = (Map<String,Object>)saved.get("response");
                    var choice = ((List<Map<String,Object>>)response.get("choices")).getFirst();
                    String content = (String)((Map<String,Object>)choice.get("message")).get("content");
                    // Use the exact runtime parser, including its fenced-block handling; no parallel parsing policy.
                    Object parsed = ReflectionTestUtils.invokeMethod(engine, "parseStepAction", content, false);
                    StepAction action = ReflectionTestUtils.invokeMethod(parsed, "action");
                    if (action == null) {
                        StepActionCorrection.Failure failure = ReflectionTestUtils.invokeMethod(parsed, "failure");
                        outcomes.add(Map.of("responseSource",file.getFileName().toString(),"parseValid",false,"valid",false,
                                "error",failure.reason()));
                        continue;
                    }
                    Files.writeString(output.resolve(actionFile), codecs.planningJson().writeValueAsString(action));
                    var result = validator.validate(action.toolArguments(), contract);
                    outcomes.add(Map.of("source",actionFile,"responseSource",file.getFileName().toString(),
                            "parseValid",true,"valid",result.valid(),"issues",result.issues()));
                } catch (RuntimeException error) {
                    outcomes.add(Map.of("responseSource",file.getFileName().toString(),"parseValid",false,"valid",false,
                            "errorType",error.getClass().getName(),"error",Objects.toString(error.getMessage(),"")));
                }
            }
        }
        Files.writeString(output.resolve("framework-contract-validation.json"), codecs.planningJson().writeValueAsString(outcomes));
    }
    @Test
    @SuppressWarnings("unchecked")
    void capturesRecordedHandoffAtActualEngineModelBoundary() throws Exception {
        Path output = Path.of("ai/thoughts/evidence/2026-10-03-PR-18-generated-input-guidance");
        var codecs = LoomspanJacksonCodecs.defaults();
        var json = codecs.planningJson();
        Map<String,Object> data = json.readValue(Files.readString(output.resolve("diagnostic-input.json")), Map.class);
        var catalog = loadRecordedSkills();
        var parent = catalog.getSkill("planResolution");
        var child = catalog.getSkill("compareOptions");
        var config = child.executionConfiguration();
        var childManifest = child.manifest();
        var contract = new SkillInputContractResolver().resolveYamlCapability(child);
        assertRecordedBoundaries(contract);
        Files.writeString(output.resolve("resolved-input-contract.json"), json.writeValueAsString(contract));
        var metadata = new CapabilityMetadata("yaml:compareOptions", "compareOptions", childManifest.getDescription(),
                SkillExecutionDescriptor.from(config), null, args -> null, CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("compareOptions", childManifest.getDescription()), contract, null);
        BoundCapability tool = new BoundCapability(metadata, (args,taskId) -> { throw new AssertionError("capture must not execute tool"); });
        Map<String,String> assignment = (Map<String,String>)data.get("task");
        List<PlanTask> tasks = new ArrayList<>();
        for (Map<String,String> row : (List<Map<String,String>>)data.get("tasks")) {
            boolean assigned = row.get("id").equals(assignment.get("ID"));
            tasks.add(new PlanTask(row.get("id"), row.get("title"), assigned ? PlanTaskStatus.PENDING : PlanTaskStatus.COMPLETED,
                    assigned ? "compareOptions" : row.get("id").substring(5), assigned ? assignment.get("Intent") : null,
                    List.of(), assigned ? List.of(assignment.get("Expected outputs")) : List.of(), null, null));
        }
        var plan = new ExecutionPlan((String)data.get("planId"), "planResolution", Instant.parse("2026-10-04T01:16:40Z"), tasks);
        var state = new DefaultExecutionStateService(Clock.systemUTC());
        PlanningService planning = mock(PlanningService.class);
        when(planning.initializePlan(any(),any(),any(),any(),any(),any())).thenAnswer(invocation -> {
            state.storePlan(plan);
            var mission = ExecutionBindingScope.requireCurrent().requireMission();
            for (Map<String,String> prior : (List<Map<String,String>>)data.get("completed"))
                mission.recordCompletedTaskResult(prior.get("taskId"), prior.get("skillName"), prior.get("result"));
            return Optional.of(plan);
        });
        var session = TestLoomspanSessions.withId("pr18-recorded-handoff", "test.entry", 3);
        var binding = TestExecutionBindings.missionBinding(session);
        ModelInteraction model = request -> {
            try {
                Files.writeString(output.resolve("framework-model-request.json"), json.writeValueAsString(Map.of(
                        "systemPrompt",request.systemPrompt(), "userText",request.input().userText(),
                        "planning",request.planning(), "traceContext",request.traceContext())));
            } catch (java.io.IOException error) { throw new RuntimeException(error); }
            assertThat(request.systemPrompt()).contains("ID: task-compare-options", "$.context", "additional");
            assertThat(request.systemPrompt()).contains("At `$` (top level): Required fields: [assetId, caseId, context]. Optional declared fields: []. Only these fields are allowed: [assetId, caseId, context].",
                    "At `$.context`: Required fields: [assetContext, equipmentAssessment, referenceEvidence, serviceHistory, serviceTerms]. Optional declared fields: []. Additional fields are allowed with any JSON value");
            for (Map<String,String> prior : (List<Map<String,String>>)data.get("completed"))
                assertThat(request.systemPrompt()).contains(json.writeValueAsString(prior.get("result")));
            throw new CapturedRequest();
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var engine = new StepLoopMissionExecutionEngine(planning,state,Duration.ofSeconds(30),executor,new NoOpSessionUsageService());
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding, () -> engine.executeMission(session,parent,
                    (String)data.get("objective"),(Map<String,Object>)data.get("missionInput"),model,List.of(tool),true,null)))
                    .isInstanceOf(CapturedRequest.class);
        }
    }
    private static YamlSkillCatalog loadRecordedSkills() throws Exception {
        Path source = Path.of("../loomspan-sidecar-test-suite/evidence/glm-skill-contract-experiment-20261003");
        var properties = new LoomspanProperties();
        var connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(AiDriver.OPENAI);
        properties.setConnections(Map.of("diagnostic", connection));
        var model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("diagnostic");
        model.setProviderModel("z-ai/glm-5.3-flash");
        model.setThinkingLevels(Set.of("medium"));
        properties.setModels(Map.of("reasoning", model));
        var catalog = new YamlSkillCatalog(properties);
        catalog.loadSupplied(List.of(
                new SkillDocument("recorded-planResolution", Files.readString(source.resolve("planResolution.yaml"))),
                new SkillDocument("recorded-compareOptions", Files.readString(source.resolve("compareOptions.yaml")))));
        return catalog;
    }
    private static void assertRecordedBoundaries(ai.loomspan.internal.runtime.input.SkillInputContract contract) {
        assertThat(contract.schema().allowsAdditionalProperties()).isFalse();
        var context = contract.schema().properties().get("context");
        assertThat(context.allowsAdditionalProperties()).isTrue();
        assertThat(context.required()).containsExactlyInAnyOrder("equipmentAssessment", "assetContext", "serviceHistory", "referenceEvidence", "serviceTerms");
    }
    private static final class CapturedRequest extends RuntimeException { }
}
