package ai.loomspan.api;

import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.api.extensions.v1.ExecutorDiagnostics;
import ai.loomspan.internal.core.ExecutorInvocations;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.time.Duration;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class VersionedExecutorInvocationTest
{
    @Test void frameworkOwnsConstructionAndOldClassIsAbsent()
    {
        assertThat(SkillExecutorInvocation.class.isRecord()).isFalse();
        assertThat(SkillExecutorInvocation.class.getConstructors()).isEmpty();
        assertThat(SkillExecutorInvocation.class.getDeclaredMethods()).allSatisfy(method ->
                assertThat(method.getParameterCount()).isZero());
        assertThat(getClass().getClassLoader().getResource("ai/loomspan/api/SkillExecutorInvocation.class")).isNull();
    }

    @Test void diagnosticDeclarationsHaveExactlyThreeLevelsWithOptionalRestrictedJson() throws Exception
    {
        assertThat(ExecutorDiagnostics.class.getDeclaredMethods()).hasSize(6);
        for (String name : java.util.List.of("info", "warn", "error"))
        {
            assertThat(ExecutorDiagnostics.class.getMethod(name, String.class).getReturnType()).isEqualTo(void.class);
            assertThat(ExecutorDiagnostics.class.getMethod(name, String.class, Object.class).getReturnType()).isEqualTo(void.class);
        }
        assertThat(SkillExecutorInvocation.class.getMethod("diagnostics").getReturnType()).isEqualTo(ExecutorDiagnostics.class);
    }

    @Test void readsSuppliedStateLiveWithoutClearingInterruption()
    {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var remaining = new java.util.concurrent.atomic.AtomicReference<Optional<Duration>>(Optional.empty());
        var call = ExecutorInvocations.create(Map.of(), Map.of(), Map.of(), "default-leaf", "generation",
                cancelled::get, remaining::get);
        assertThat(call.isCancelled()).isFalse();
        assertThat(call.remainingTime()).isEmpty();
        cancelled.set(true); remaining.set(Optional.of(Duration.ZERO));
        Thread.currentThread().interrupt();
        try
        {
            assertThat(call.isCancelled()).isTrue();
            assertThat(call.remainingTime()).contains(Duration.ZERO);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThatThrownBy(call::diagnostics).isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("not yet available");
        }
        finally { Thread.interrupted(); }
    }
}
