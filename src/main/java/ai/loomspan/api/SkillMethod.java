package ai.loomspan.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one callable skill on an application Spring bean method.
 * Its exact name shares the callable namespace with YAML declarations; no companion manifest is required.
 * The method signature and {@link SkillParam} annotations define its inputs.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkillMethod
{
    /** Exact local name matching {@code [a-z][a-z0-9]*(?:-[a-z0-9]+)*}.
     * empty uses the Java method name verbatim. Invalid names fail startup.
     * Each component has 1–64 ASCII characters and excludes {@code anthropic} and {@code claude}.
     * Namespace length plus two plus local-name length must be at most 64. */
    String name() default "";

    /** Exact owning namespace, using the same component rules as {@link #name()}. */
    String namespace() default "default";

    /** Description presented to callers and parent models. */
    String description();
}
