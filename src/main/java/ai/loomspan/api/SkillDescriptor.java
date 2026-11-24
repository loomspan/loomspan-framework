package ai.loomspan.api;

import java.util.Objects;

/**
 * The exact namespace and local name identify the registered skill.
 * This immutable metadata grants no authorization or execution reservation.
 * Read-only public metadata for a registered skill. */
public record SkillDescriptor(String namespace, String localName, String description, SkillKind kind, String inputSchema, String outputSchema)
{
    public SkillDescriptor
    {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(localName, "localName must not be null");
        new ai.loomspan.internal.core.SkillIdentity(namespace, localName);
        description = requireNonBlank(description, "description");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        inputSchema = Objects.requireNonNull(inputSchema, "inputSchema must not be null");
    }

    /** Colon-qualified display and authored reference for this exact identity. */
    public String qualifiedName() { return namespace + ":" + localName; }

    private static String requireNonBlank(String value, String field)
    {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
