package ai.loomspan.internal.skill;

import org.springframework.lang.Nullable;

import java.util.Objects;

/** Immutable normalized task-count contract for one direct child skill. */
public record AllowedSkillConstraint(
        String name,
        @Nullable Integer minTasks,
        @Nullable Integer maxTasks,
        boolean required)
{
    public AllowedSkillConstraint
    {
        Objects.requireNonNull(name, "name must not be null");
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
                Boolean.TRUE.equals(manifest.getRequired()));
    }
}
