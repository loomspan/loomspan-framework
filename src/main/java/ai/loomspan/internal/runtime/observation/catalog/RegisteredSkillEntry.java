package ai.loomspan.internal.runtime.observation.catalog;

import ai.loomspan.internal.core.SkillIdentity;

import java.util.Objects;

/** Immutable source-specific diagnostic snapshot of one callable registration. */
public record RegisteredSkillEntry(String namespace, String localName, String kind, String declaration, String sourcePath,
        String executorName, String beanName, String method, String yaml, String outputSchema)
{
    public RegisteredSkillEntry
    {
        new SkillIdentity(namespace, localName);
        validateLocation(namespace + ":" + localName, kind, declaration, sourcePath, executorName, beanName, method);
        if ("YAML".equals(declaration)) Objects.requireNonNull(yaml, "yaml must not be null");
        else if (yaml != null) throw new IllegalArgumentException("SkillMethod declarations must not contain YAML");
    }

    public String registeredName() { return namespace + ":" + localName; }

    public Summary summary()
    {
        return new Summary(namespace, localName, kind, declaration, sourcePath, executorName, beanName, method);
    }

    public record Summary(String namespace, String localName, String kind, String declaration, String sourcePath, String executorName, String beanName, String method)
    {
        public String registeredName() { return namespace + ":" + localName; }
        public Summary
        {
            new SkillIdentity(namespace, localName);
            validateLocation(namespace + ":" + localName, kind, declaration, sourcePath, executorName, beanName, method);
        }
    }

    public static void validateLocation(String name, String kind, String declaration, String path, String executor, String bean, String method)
    {
        SkillIdentity.parse(name);
        if (!"YAML".equals(kind) && !"JAVA".equals(kind))
            throw new IllegalArgumentException("kind must be YAML or JAVA");
        if ("YAML".equals(declaration))
        {
            requireNonBlank(path, "sourcePath");
            if ("JAVA".equals(kind)) requireNonBlank(executor, "executorName");
            else if (executor != null || bean != null || method != null)
                throw new IllegalArgumentException("Model declarations must not contain Java locations");
        }
        else if ("SKILL_METHOD".equals(declaration))
        {
            if (!"JAVA".equals(kind) || path != null || executor != null)
                throw new IllegalArgumentException("SkillMethod declarations require JAVA and forbid sourcePath/executorName");
        }
        else throw new IllegalArgumentException("declaration must be YAML or SKILL_METHOD");
        if ("JAVA".equals(kind)) {
            requireNonBlank(bean, "beanName");
            requireNonBlank(method, "method");
        }
    }

    private static void requireNonBlank(String value, String name)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
