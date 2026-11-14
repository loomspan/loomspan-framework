package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.schema.CanonicalSchema;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import tools.jackson.databind.JsonNode;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Invocation-local source snapshot and shared complete-output composition authority. */
public final class OutputBindingComposition {
    public record Result(String content, List<Map<String,Object>> provenance, OutputSchemaValidationResult validation) { }
    private final CanonicalSchema original;
    private final OutputBindingProjection projection;
    private final ObjectMapper mapper;

    private final ChildInputBindingAssembler.Assembly selected;
    private MissionContext mission;
    private ai.loomspan.internal.core.ExecutionPlan capturedPlan;
    public OutputBindingComposition(YamlSkillDefinition definition, MissionContext mission, ObjectMapper mapper) {
        this(definition.outputSchema(), definition.outputBindings(), mission.input(), mission.completedTaskResults(), mission.missionFrameId(), mapper);
        if (!definition.identity().toString().equals(mission.skillName())) throw new IllegalArgumentException("binding_source_unavailable: Output owner differs from invocation.");
        this.mission = mission;
        capturedPlan = mission.currentPlan().orElse(null);
        var evidence = new ai.loomspan.internal.runtime.evidence.EvidenceBackedOutputValidator().validate(
                mapper.writeValueAsString(selected.arguments()), definition.evidenceContract(), mission.successfulDirectSkills());
        if (!evidence.complete()) throw new IllegalArgumentException("binding_evidence_validation: Bound output claims lack accepted direct evidence: " + evidence.issues());
        if (definition.outputBindings().stream().anyMatch(b -> b.sourceKind() == ChildInputBinding.SourceKind.CHILD_RESULT)) {
            if (capturedPlan == null) throw new IllegalArgumentException("binding_source_unavailable: Missing accepted producer plan.");
            for (var task : capturedPlan.tasks()) {
                if (task.status() != ai.loomspan.internal.core.PlanTaskStatus.COMPLETED) throw new IllegalArgumentException("binding_source_unavailable: Accepted work is incomplete: " + task.taskId());
                var result = mission.completedTaskResult(task.taskId()).orElseThrow(() -> new IllegalArgumentException("binding_source_unavailable: Missing accepted task result " + task.taskId()));
                if (!Objects.equals(task.capabilityName(),result.skillName())) throw new IllegalArgumentException("binding_source_unavailable: Accepted task producer differs: " + task.taskId());
            }
            for (var result : mission.completedTaskResults()) if (capturedPlan.tasks().stream().noneMatch(t -> t.taskId().equals(result.taskId()))) throw new IllegalArgumentException("binding_source_unavailable: Result is outside accepted plan: " + result.taskId());
        }
    }
    public OutputBindingComposition(CanonicalSchema schema, List<ChildInputBinding> bindings, Map<String,Object> input,
            List<MissionContext.CompletedTaskResult> results, String ownerFrame, ObjectMapper mapper) {
        this.original = schema;
        this.projection = new OutputBindingProjection(original, bindings);
        this.mapper = mapper;
        selected = new ChildInputBindingAssembler().assemble(Map.of(),bindings,input,results,ownerFrame);
        // Each immutable selected value must satisfy its native destination contract before any model call.
        for (var binding : bindings) {
            ObjectSchemaValue value = destination(selected.arguments(), original.tree(), binding.destination().tokens());
            if (value.schema()!=null) {
                var wrapper = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree().createObjectNode();
                wrapper.put("type", "object"); wrapper.putObject("properties").set("value", value.schema());
                var contract = new StandardSchemaCompiler().compile(wrapper, StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.GENERATED);
                Map<String,Object> instance = new LinkedHashMap<>(); instance.put("value", value.value());
                var validation = validate(mapper.writeValueAsString(instance), contract);
                if (!validation.valid()) throw new IllegalArgumentException("binding_output_validation: Destination "
                        + binding.destination().pointer()+": "+validation.issues());
            }
        }
    }
    public OutputBindingProjection projection() { return projection; }
    public boolean modelContributionRequired() { return projection.modelContributionRequired(); }
    public List<Map<String,Object>> provenance() { return selected.provenance(); }
    public String guidance() { return projection.guidance(); }
    public Result composeEmpty() { return compose("{}"); }
    @SuppressWarnings("unchecked")
    public Result compose(String candidate) {
        if (mission != null) {
            if (!Objects.equals(capturedPlan, mission.currentPlan().orElse(null))) throw new IllegalArgumentException("binding_source_unavailable: Accepted plan changed during output composition.");
            if (mission.lifecycle().state() != ai.loomspan.internal.core.MissionLifecycle.State.OPEN) throw new IllegalStateException("Output owning invocation is no longer open.");
        }
        Object decoded = new AcceptedResultDecoder().decode(candidate);
        if (!(decoded instanceof Map<?,?> object)) {
            var invalid = validate(candidate, projection.schema());
            return new Result(candidate,provenance(),invalid.valid() ? OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, List.of(new OutputSchemaValidationIssue("$","Model contribution must be an object.",null,"binding_model_override",null,null,null,null,null,null,null))) : invalid);
        }
        Map<String,Object> contribution = (Map<String,Object>) object;
        var overrides = projection.validateModelContribution(contribution);
        if (!overrides.isEmpty()) return failed(candidate,"binding_model_override",String.join("; ",overrides));
        var contributionValidation = validate(candidate,projection.schema());
        if (!contributionValidation.valid()) return new Result(candidate,provenance(),contributionValidation);
        Map<String,Object> assembled = (Map<String,Object>) DeepInputValues.mutableCopy(contribution);
        for (var binding : projection.bindings()) {
            Map<String,Object> target = assembled;
            Object source = selected.arguments();
            var tokens = binding.destination().tokens();
            for (int i=0;i<tokens.size()-1;i++) {
                String token=tokens.get(i);
                target.computeIfAbsent(token, ignored -> new LinkedHashMap<String,Object>());
                target=(Map<String,Object>)target.get(token);
                source=((Map<?,?>)source).get(token);
            }
            target.put(tokens.getLast(),DeepInputValues.mutableCopy(((Map<?,?>)source).get(tokens.getLast())));
        }
        String content=mapper.writeValueAsString(assembled);
        return new Result(content,provenance(),validate(content,original));
    }
    private OutputSchemaValidationResult validate(String content, CanonicalSchema contract) {
        if (content == null || content.isBlank()) return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.INVALID_JSON,
                List.of(new OutputSchemaValidationIssue("$", "Model contribution must be JSON.", null, "invalid_json", null,null,null,null,null,null,null)));
        JsonNode instance;
        try { instance = ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree().readTree(content); }
        catch (tools.jackson.core.JacksonException ex) {
            return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.INVALID_JSON,
                    List.of(new OutputSchemaValidationIssue("$", ex.getMessage(), null, "invalid_json", null,null,null,null,null,null,null)));
        }
        var errors = contract.validate(instance);
        if (errors.isEmpty()) return OutputSchemaValidationResult.passed();
        return OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, errors.stream()
                .map(error -> new OutputSchemaValidationIssue(error.getInstanceLocation().toString(), error.toString(), null,
                        "schema_validation", null,null,null,null,null,null,null)).toList());
    }
    private Result failed(String candidate,String code,String message) {
        return new Result(candidate,provenance(),OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED,
                List.of(new OutputSchemaValidationIssue("$",message,null,code,null,null,null,null,null,null,null))));
    }
    private record ObjectSchemaValue(Object value,JsonNode schema) { }
    private static ObjectSchemaValue destination(Object value,JsonNode schema,List<String> tokens) {
        for (String token:tokens) {
            value=((Map<?,?>)value).get(token);
            if(schema!=null) schema=schema.path("properties").get(token);
        }
        return new ObjectSchemaValue(value,schema);
    }
}
