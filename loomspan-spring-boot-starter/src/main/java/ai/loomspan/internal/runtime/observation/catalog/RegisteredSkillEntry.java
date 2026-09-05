package ai.loomspan.internal.runtime.observation.catalog;

import java.util.Objects;

/** Immutable source-specific diagnostic snapshot of one callable registration. */
public record RegisteredSkillEntry(String registeredName, String source, String sourcePath,
        String beanName, String method, String yaml)
{
    public RegisteredSkillEntry
    {
        validateLocation(registeredName, source, sourcePath, beanName, method);
        if ("YAML".equals(source)) Objects.requireNonNull(yaml, "yaml must not be null");
        else if (yaml != null) throw new IllegalArgumentException("Java skills must not contain YAML");
    }

    public Summary summary()
    {
        return new Summary(registeredName, source, sourcePath, beanName, method);
    }

    public record Summary(String registeredName, String source, String sourcePath, String beanName, String method)
    {
        public Summary { validateLocation(registeredName, source, sourcePath, beanName, method); }
    }

    public static void validateLocation(String name, String source, String path, String bean, String method)
    {
        requireNonBlank(name, "registeredName");
        if ("YAML".equals(source))
        {
            requireNonBlank(path, "sourcePath");
            if (bean != null || method != null) throw new IllegalArgumentException("YAML skills must not contain Java locations");
        }
        else if ("JAVA".equals(source))
        {
            requireNonBlank(bean, "beanName");
            requireNonBlank(method, "method");
            if (path != null) throw new IllegalArgumentException("Java skills must not contain sourcePath");
        }
        else throw new IllegalArgumentException("source must be YAML or JAVA");
    }

    private static void requireNonBlank(String value, String name)
    {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
