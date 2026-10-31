package ai.loomspan.internal.core;

import java.util.Locale;

/** Canonical internal identity. Only namespace case is normalized; authored content is never repaired. */
public record SkillIdentity(String namespace, String localName)
{
    public SkillIdentity
    {
        namespace = normalizeNamespace(namespace);
        validateLocalName(localName);
        int length = namespace.length() + 1 + localName.length();
        if (length > 64) throw new IllegalArgumentException("skill identity '" + namespace + "-" + localName
                + "' length " + length + " exceeds limit 64");
    }

    public static SkillIdentity parse(String reference)
    {
        if (reference == null) throw new IllegalArgumentException("skill reference must not be null");
        int separator = reference.indexOf('-');
        if (separator < 1 || separator != reference.lastIndexOf('-') || separator == reference.length() - 1)
            throw new IllegalArgumentException("invalid qualified skill reference '" + reference + "'; expected namespace-localName");
        return new SkillIdentity(reference.substring(0, separator), reference.substring(separator + 1));
    }

    /** Resolve input at an authoring or host-entry boundary, never during canonical lookup. */
    public static SkillIdentity resolve(String reference, String ownerNamespace)
    {
        if (reference == null || reference.indexOf('-') >= 0) return parse(reference);
        return new SkillIdentity(ownerNamespace, reference);
    }

    public static String normalizeNamespace(String namespace)
    {
        component(namespace, "namespace", 32);
        return namespace.toLowerCase(Locale.ROOT);
    }

    public static void validateLocalName(String localName) { component(localName, "local name", 64); }

    private static void component(String value, String field, int limit)
    {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        if (value.length() > limit) throw new IllegalArgumentException(field + " '" + value + "' length "
                + value.length() + " exceeds limit " + limit);
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new IllegalArgumentException("invalid " + field + " '" + value
                    + "'; must start with an ASCII letter or underscore and contain only ASCII letters, digits or underscores");
    }

    @Override public String toString() { return namespace + "-" + localName; }
}
