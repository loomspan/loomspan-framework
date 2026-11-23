package ai.loomspan.internal.core;

/** Exact portable identity used by callable and executor registrations. */
public record SkillIdentity(String namespace, String localName)
{
    public SkillIdentity
    {
        namespace = requireNamespace(namespace);
        validateLocalName(localName);
        int length = namespace.length() + 2 + localName.length();
        if (length > 64) throw new IllegalArgumentException("skill identity '" + namespace + ":" + localName
                + "' length " + length + " exceeds limit 64");
    }

    public static SkillIdentity parse(String reference)
    {
        if (reference == null) throw new IllegalArgumentException("skill reference must not be null");
        int separator = reference.indexOf(':');
        if (separator < 1 || separator != reference.lastIndexOf(':') || separator == reference.length() - 1)
            throw new IllegalArgumentException("invalid qualified skill reference '" + reference + "'; expected namespace:local-name");
        return new SkillIdentity(reference.substring(0, separator), reference.substring(separator + 1));
    }

    /** Resolve input at an authoring or host-entry boundary, never during canonical lookup. */
    public static SkillIdentity resolve(String reference, String ownerNamespace)
    {
        if (reference == null || reference.indexOf(':') >= 0) return parse(reference);
        return new SkillIdentity(ownerNamespace, reference);
    }

    public static String requireNamespace(String namespace)
    {
        component(namespace, "namespace");
        return namespace;
    }

    public static void validateLocalName(String localName) { component(localName, "local name"); }

    private static void component(String value, String field)
    {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        if (value.length() > 64) throw new IllegalArgumentException(field + " '" + value + "' length "
                + value.length() + " exceeds limit 64");
        if (!value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*"))
            throw new IllegalArgumentException("invalid " + field + " '" + value
                    + "'; must match [a-z][a-z0-9]*(?:-[a-z0-9]+)* using 1–64 ASCII characters");
        if (value.contains("anthropic") || value.contains("claude"))
            throw new IllegalArgumentException("invalid " + field + " '" + value + "'; must not contain anthropic or claude");
    }

    @Override public String toString() { return namespace + ":" + localName; }
}
