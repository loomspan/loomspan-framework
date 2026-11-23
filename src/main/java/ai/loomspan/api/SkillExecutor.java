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
    /** Exact portable local name; empty uses the Java method name verbatim.
     * The component grammar and pair length limit are specified by {@link SkillMethod#name()}. */
    String name() default "";
    /** Exact owning namespace, using the component rules of {@link SkillMethod#name()}. */
    String namespace() default "default";
    /** Exact dot-separated map path in the executor owner's namespace and, for foreign
     * references, the executing YAML leaf's namespace. Owner selection is required;
     * missing consumer entries are empty. Empty selects neither map. */
    String config() default "";
}
