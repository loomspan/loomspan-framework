package ai.loomspan.internal.core;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.api.extensions.v1.ExecutorDiagnostics;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Internal construction bridge; no application construction or lifecycle SPI. */
public final class ExecutorInvocations
{
    private static final MethodHandle CONSTRUCTOR = constructor();
    private ExecutorInvocations() {}

    private static MethodHandle constructor()
    {
        try
        {
            return MethodHandles.privateLookupIn(SkillExecutorInvocation.class, MethodHandles.lookup())
                    .findConstructor(SkillExecutorInvocation.class, MethodType.methodType(void.class,
                            Map.class, Map.class, Map.class, Map.class, String.class, String.class,
                            BooleanSupplier.class, Supplier.class, ExecutorDiagnostics.class));
        }
        catch (ReflectiveOperationException failure) { throw new ExceptionInInitializerError(failure); }
    }

    public static SkillExecutorInvocation create(Map<String, Object> input, Map<String, Object> skillConfig,
            Map<String, Object> executorConfig, Map<String, Object> consumerExecutorConfig, String skillName, String generationId,
            BooleanSupplier cancelled, Supplier<Optional<Duration>> remaining, ExecutorDiagnostics diagnostics)
    {
        try
        {
            return (SkillExecutorInvocation) CONSTRUCTOR.invokeExact(input, skillConfig, executorConfig, consumerExecutorConfig,
                    skillName, generationId, cancelled, remaining, diagnostics);
        }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot construct executor invocation", failure); }
    }
}
