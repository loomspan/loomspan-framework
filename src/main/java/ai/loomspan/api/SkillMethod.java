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
    /** Local declaration name; empty uses the canonical Java method name. */
    String name() default "";

    /** Owning namespace; normalized to lowercase, 1–32 ASCII letters, digits or underscores. */
    String namespace() default "default";

    /** Description presented to callers and parent models. */
    String description();
}
