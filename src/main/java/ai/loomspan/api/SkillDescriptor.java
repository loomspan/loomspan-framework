package ai.loomspan.api;

import java.util.Objects;

/**
 * Names are canonical qualified namespace-localName identities.
 * This immutable metadata grants no authorization or execution reservation.
 * Read-only public metadata for a registered skill. */
public record SkillDescriptor(String name, String description, SkillKind kind, String inputSchema, String outputSchema)
{
    public SkillDescriptor
    {
        name = requireNonBlank(name, "name");
        description = requireNonBlank(description, "description");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema must not be null");
    }

    private static String requireNonBlank(String value, String field)
    {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
