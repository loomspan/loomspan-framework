package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.core.MissionContext;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import ai.loomspan.internal.skill.YamlSkillManifest.OutputSchemaManifest;
import java.util.*;
import tools.jackson.databind.ObjectMapper;

/** Invocation-local source snapshot and shared complete-output composition authority. */
public final class OutputBindingComposition {
    public record Result(String content, List<Map<String,Object>> provenance, OutputSchemaValidationResult validation) { }
    private final OutputSchemaManifest original;
    private final OutputBindingProjection projection;
    private final ObjectMapper mapper;
    private final OutputSchemaValidator validator;
    private final ChildInputBindingAssembler.Assembly selected;
    private MissionContext mission;
    private ai.loomspan.internal.core.ExecutionPlan capturedPlan;
    public OutputBindingComposition(YamlSkillDefinition definition, MissionContext mission, ObjectMapper mapper) {
        this(definition.outputSchema(), definition.outputBindings(), mission.input(), mission.completedTaskResults(), mission.missionFrameId(), mapper);
        if (!definition.manifest().getName().equals(mission.skillName())) throw new IllegalArgumentException("binding_source_unavailable: Output owner differs from invocation.");
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
    public OutputBindingComposition(OutputSchemaManifest schema, List<ChildInputBinding> bindings, Map<String,Object> input,
            List<MissionContext.CompletedTaskResult> results, String ownerFrame, ObjectMapper mapper) {
        this.original = OutputBindingProjection.copy(schema);
        this.projection = new OutputBindingProjection(original, bindings);
        this.mapper = mapper; validator = new OutputSchemaValidator(mapper);
        selected = new ChildInputBindingAssembler().assemble(Map.of(),bindings,input,results,ownerFrame);
        // Each immutable selected value must satisfy its native destination contract before any model call.
        for (var binding : bindings) {
            ObjectSchemaValue value = destination(selected.arguments(), original, binding.destination().tokens());
            if (value.schema()!=null) {
                var validation = validator.validate(mapper.writeValueAsString(value.value()),value.schema());
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
            var invalid = validator.validate(candidate, projection.schema());
            return new Result(candidate,provenance(),invalid.valid() ? OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED, List.of(new OutputSchemaValidationIssue("$","Model contribution must be an object.",null,"binding_model_override",null,null,null,null,null,null,null))) : invalid);
        }
        Map<String,Object> contribution = (Map<String,Object>) object;
        var overrides = projection.validateModelContribution(contribution);
        if (!overrides.isEmpty()) return failed(candidate,"binding_model_override",String.join("; ",overrides));
        var contributionValidation = validator.validate(candidate,projection.schema());
        if (!contributionValidation.valid()) return new Result(candidate,provenance(),contributionValidation);
        // Canonicalize only object containers on bound paths; retain all unbound values and field spellings.
        Map<String,Object> assembled = (Map<String,Object>) DeepInputValues.mutableCopy(contribution);
        for (var binding : projection.bindings()) {
            Map<String,Object> target = assembled;
            Object source = selected.arguments();
            var tokens = binding.destination().tokens();
            for (int i=0;i<tokens.size()-1;i++) {
                String token=tokens.get(i);
                String actual=target.keySet().stream().filter(k->OutputSchemaValidator.propertyNamesMatch(k, token)).findFirst().orElse(token);
                Object child=target.remove(actual);
                if (child==null) child=new LinkedHashMap<String,Object>();
                target.put(token,child); target=(Map<String,Object>)child;
                source=((Map<?,?>)source).get(token);
            }
            target.put(tokens.getLast(),DeepInputValues.mutableCopy(((Map<?,?>)source).get(tokens.getLast())));
        }
        String content=mapper.writeValueAsString(assembled);
        return new Result(content,provenance(),validator.validate(content,original));
    }
    private Result failed(String candidate,String code,String message) {
        return new Result(candidate,provenance(),OutputSchemaValidationResult.failed(OutputSchemaFailureMode.SCHEMA_VALIDATION_FAILED,
                List.of(new OutputSchemaValidationIssue("$",message,null,code,null,null,null,null,null,null,null))));
    }
    private record ObjectSchemaValue(Object value,OutputSchemaManifest schema) { }
    private static ObjectSchemaValue destination(Object value,OutputSchemaManifest schema,List<String> tokens) {
        for (String token:tokens) {
            value=((Map<?,?>)value).get(token);
            if(schema!=null) schema=schema.getProperties().entrySet().stream().filter(e->OutputSchemaValidator.propertyNamesMatch(e.getKey(), token)).map(Map.Entry::getValue).findFirst().orElse(null);
        }
        return new ObjectSchemaValue(value,schema);
    }
}
