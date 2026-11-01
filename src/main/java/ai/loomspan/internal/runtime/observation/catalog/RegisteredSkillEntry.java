package ai.loomspan.internal.runtime.observation.catalog;

import java.util.Objects;

/** Immutable source-specific diagnostic snapshot of one callable registration. */
public record RegisteredSkillEntry(String registeredName, String kind, String declaration, String sourcePath,
        String executorName, String beanName, String method, String yaml, String outputSchema)
{
    public RegisteredSkillEntry
    {
        validateLocation(registeredName, kind, declaration, sourcePath, executorName, beanName, method);
        if ("YAML".equals(declaration)) Objects.requireNonNull(yaml, "yaml must not be null");
        else if (yaml != null) throw new IllegalArgumentException("SkillMethod declarations must not contain YAML");
    }

    public Summary summary()
    {
        return new Summary(registeredName, kind, declaration, sourcePath, executorName, beanName, method);
    }

    public record Summary(String registeredName, String kind, String declaration, String sourcePath, String executorName, String beanName, String method)
    {
        public Summary { validateLocation(registeredName, kind, declaration, sourcePath, executorName, beanName, method); }
    }

    public static void validateLocation(String name, String kind, String declaration, String path, String executor, String bean, String method)
    {
        requireNonBlank(name, "registeredName");
        if (!"YAML".equals(kind) && !"TYPESAFE".equals(kind) && !"JAVA".equals(kind))
            throw new IllegalArgumentException("kind must be YAML, TYPESAFE or JAVA");
        if ("YAML".equals(declaration))
        {
            requireNonBlank(path, "sourcePath");
            if ("JAVA".equals(kind)) requireNonBlank(executor, "executorName");
            else if (executor != null || bean != null || method != null)
                throw new IllegalArgumentException("Model and TypeSafe declarations must not contain Java locations");
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
