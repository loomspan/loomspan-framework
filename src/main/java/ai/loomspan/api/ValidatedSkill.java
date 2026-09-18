package ai.loomspan.api;

import java.util.Objects;

/** Immutable callable identity and kind in a successfully checked skill proposal. */
public record ValidatedSkill(String name, SkillKind kind)
{
    public ValidatedSkill
    {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        kind = Objects.requireNonNull(kind, "kind must not be null");
    }
}
