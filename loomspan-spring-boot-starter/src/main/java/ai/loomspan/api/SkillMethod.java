package ai.loomspan.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one callable skill on an application Spring bean method.
 * Its exact name is shared with YAML skills; no companion manifest is required.
 * The method signature and {@link SkillParam} annotations define its inputs.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkillMethod
{
    /** Exact skill name; empty uses the canonical Java method name. */
    String name() default "";

    /** Description presented to callers and parent models. */
    String description();
}
