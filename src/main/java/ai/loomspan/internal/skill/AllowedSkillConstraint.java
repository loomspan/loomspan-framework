package ai.loomspan.internal.skill;

import org.springframework.lang.Nullable;

import java.util.Objects;
import java.util.List;
import ai.loomspan.internal.runtime.input.ChildInputBinding;
import ai.loomspan.internal.runtime.input.ObjectFieldPath;

/** Immutable normalized task-count contract for one direct child skill. */
public record AllowedSkillConstraint(
        String name,
        @Nullable Integer minTasks,
        @Nullable Integer maxTasks,
        boolean required,
        List<ChildInputBinding> inputBindings)
{
    public AllowedSkillConstraint
    {
        Objects.requireNonNull(name, "name must not be null");
        inputBindings = inputBindings == null ? List.of() : List.copyOf(inputBindings);
        if (minTasks != null && minTasks < 0)
        {
            throw new IllegalArgumentException("minTasks must be non-negative");
        }
        if (maxTasks != null && maxTasks < 0)
        {
            throw new IllegalArgumentException("maxTasks must be non-negative");
        }
        if (maxTasks != null && maxTasks < Math.max(minTasks == null ? 0 : minTasks, required ? 1 : 0))
        {
            throw new IllegalArgumentException("maxTasks must not be less than the effective minimum");
        }
    }

    public int effectiveMinTasks()
    {
        return Math.max(minTasks == null ? 0 : minTasks, required ? 1 : 0);
    }

    public boolean hasMinimumConstraint()
    {
        return effectiveMinTasks() > 0;
    }

    public boolean hasMaximumConstraint()
    {
        return maxTasks != null;
    }

    static AllowedSkillConstraint from(YamlSkillManifest.AllowedSkillManifest manifest)
    {
        return new AllowedSkillConstraint(
                manifest.getName(),
                manifest.getMinTasks(),
                manifest.getMaxTasks(),
                Boolean.TRUE.equals(manifest.getRequired()),
                manifest.getInputBindings().entrySet().stream().map(entry -> {
                    var value = entry.getValue();
                    return new ChildInputBinding(ObjectFieldPath.parse(entry.getKey(), false),
                            switch (value.getFrom()) {
                                case "input" -> ChildInputBinding.SourceKind.INPUT;
                                case "child_result" -> ChildInputBinding.SourceKind.CHILD_RESULT;
                                default -> throw new IllegalArgumentException("unknown binding source kind");
                            }, ObjectFieldPath.parse(value.getPath(), true), value.getSkill());
                }).toList());
    }
}
