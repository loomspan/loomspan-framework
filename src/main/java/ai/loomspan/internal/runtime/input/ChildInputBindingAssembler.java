package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.core.MissionContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles detached receiving inputs using only invocation-local accepted sources. */
public final class ChildInputBindingAssembler
{
    public record Assembly(Map<String, Object> arguments, List<List<String>> exactPaths,
            List<Map<String, Object>> provenance) { }

    @SuppressWarnings("unchecked")
    public Assembly assemble(Map<String, Object> modelArguments, List<ChildInputBinding> bindings,
            Map<String, Object> parentInput, List<MissionContext.CompletedTaskResult> preUnitResults,
            String parentFrameId)
    {
        Map<String, Object> assembled = (Map<String, Object>) DeepInputValues.mutableCopy(
                modelArguments == null ? Map.of() : modelArguments);
        List<List<String>> exactPaths = new ArrayList<>();
        List<Map<String, Object>> provenance = new ArrayList<>();
        for (ChildInputBinding binding : bindings)
        {
            // Reject even equal values before selecting source data.
            checkDestination(assembled, binding.destination());
            Object source = parentInput;
            MissionContext.CompletedTaskResult result = null;
            if (binding.sourceKind() == ChildInputBinding.SourceKind.CHILD_RESULT)
            {
                List<MissionContext.CompletedTaskResult> matches = preUnitResults.stream()
                        .filter(candidate -> binding.skill().equals(candidate.skillName())).toList();
                if (matches.size() != 1)
                    throw failure(matches.size() > 1 ? "binding_source_ambiguous" : "binding_source_unavailable", binding, "Expected exactly one accepted successful direct result for '"
                            + binding.skill() + "'; found " + matches.size() + ".");
                result = matches.getFirst();
                source = result.decodedResult();
            }
            Object selected = select(source, binding);
            insert(assembled, binding.destination().tokens(), DeepInputValues.mutableCopy(selected));
            exactPaths.add(binding.destination().tokens());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("destination", binding.destination().pointer());
            entry.put("sourceKind", binding.sourceKind() == ChildInputBinding.SourceKind.INPUT ? "input" : "child_result");
            entry.put("sourcePath", binding.sourcePath().pointer());
            entry.put("parentMissionFrameId", parentFrameId);
            if (result != null)
            {
                entry.put("sourceTaskId", result.taskId());
                entry.put("sourceSkill", result.skillName());
            }
            provenance.add(DeepInputValues.immutableMap(entry));
        }
        return new Assembly(DeepInputValues.immutableMap(assembled), List.copyOf(exactPaths), List.copyOf(provenance));
    }

    public static void checkDestination(Map<String, Object> arguments, ObjectFieldPath destination)
    {
        Object current = arguments;
        for (int i = 0; i < destination.tokens().size(); i++)
        {
            String token = destination.tokens().get(i);
            if (!(current instanceof Map<?, ?> object))
                throw new IllegalArgumentException("binding_model_override: Nonobject ancestor of bound destination " + destination.pointer());
            if (!object.containsKey(token)) return;
            if (i == destination.tokens().size() - 1)
                throw new IllegalArgumentException("binding_model_override: Model supplied bound destination " + destination.pointer());
            current = object.get(token);
        }
    }

    private Object select(Object source, ChildInputBinding binding)
    {
        Object current = source;
        for (String token : binding.sourcePath().tokens())
        {
            if (!(current instanceof Map<?, ?> object) || !object.containsKey(token))
                throw failure("binding_source_unavailable", binding,
                        "Missing object source path " + binding.sourcePath().pointer() + ".");
            current = object.get(token);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private void insert(Map<String, Object> root, List<String> tokens, Object value)
    {
        Map<String, Object> object = root;
        for (int i = 0; i < tokens.size() - 1; i++)
        {
            String token = tokens.get(i);
            object.computeIfAbsent(token, ignored -> new LinkedHashMap<String, Object>());
            object = (Map<String, Object>) object.get(token);
        }
        object.put(tokens.getLast(), value);
    }

    private IllegalArgumentException failure(String code, ChildInputBinding binding, String message)
    {
        return new IllegalArgumentException(code + ": Destination " + binding.destination().pointer() + ": " + message);
    }
}
