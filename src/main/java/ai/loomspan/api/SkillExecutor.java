package ai.loomspan.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares reference-only Java execution behavior on a Spring bean method.
 * Accepts exactly one {@link ai.loomspan.api.extensions.v1.SkillExecutorInvocation}; it creates no callable skill or input schema.
 * Executor-managed requests and retries do not reserve the native provider-attempt budget.
 * Framework-accounted usage covers native Framework model calls; executor diagnostic usage
 * does not contribute. Zero Framework-accounted usage does not measure executor usage.
 * Namespace and local-name rules are the same as {@link SkillMethod}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SkillExecutor
{
    /** Local declaration name; empty uses the canonical Java method name. */
    String name() default "";
    /** Owning namespace, normalized to lowercase. */
    String namespace() default "default";
    /** Relative configuration path in the executor namespace; empty selects no configuration. */
    String config() default "";
}
