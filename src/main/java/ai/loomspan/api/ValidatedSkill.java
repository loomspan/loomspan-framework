package ai.loomspan.api;

import java.util.Objects;

/**
 * The namespace and local name identify the callable in the checked namespace proposal.
 * Immutable callable identity and kind in a successfully checked skill proposal. */
public record ValidatedSkill(String namespace, String localName, SkillKind kind)
{
    public ValidatedSkill
    {
        Objects.requireNonNull(namespace, "namespace must not be null");
        Objects.requireNonNull(localName, "localName must not be null");
        new ai.loomspan.internal.core.SkillIdentity(namespace, localName);
        kind = Objects.requireNonNull(kind, "kind must not be null");
    }

    /** Colon-qualified display and authored reference for this exact identity. */
    public String qualifiedName() { return namespace + ":" + localName; }
}
