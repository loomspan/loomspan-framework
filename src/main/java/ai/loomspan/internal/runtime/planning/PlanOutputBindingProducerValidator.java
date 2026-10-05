package ai.loomspan.internal.runtime.planning;

import ai.loomspan.internal.core.ExecutionPlan;
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import java.util.ArrayList;
import java.util.List;

/** Output ownership requires unique direct producers, without introducing execution edges. */
public final class PlanOutputBindingProducerValidator {
    public List<String> validate(ExecutionPlan plan, List<ChildInputBinding> bindings) {
        List<String> issues = new ArrayList<>();
        for (String producer : producers(bindings)) {
            long count = plan.tasks().stream().filter(task -> producer.equals(task.capabilityName())).count();
            if (count != 1) issues.add("Output binding producer: requires exactly one direct task for '" + producer + "'; found " + count);
        }
        return List.copyOf(issues);
    }

    public static String render(List<ChildInputBinding> bindings) {
        List<String> producers = producers(bindings);
        return bindings.isEmpty() ? "" : "\nDeclared final output bindings:\n"
                + String.join("\n", producers.stream().map(producer -> "- Include exactly one direct task for '" + producer
                    + "', even when its allowed_skills entry is optional. Output bindings add no dependency edges or ordering constraints.").toList())
                + "\n- Complete every accepted task before output assembly. Framework supplies bound output values; generate only unbound fields.\n";
    }

    private static List<String> producers(List<ChildInputBinding> bindings) {
        return bindings.stream().filter(binding -> binding.sourceKind() == ChildInputBinding.SourceKind.CHILD_RESULT)
                .map(ChildInputBinding::skill).distinct().toList();
    }
}
