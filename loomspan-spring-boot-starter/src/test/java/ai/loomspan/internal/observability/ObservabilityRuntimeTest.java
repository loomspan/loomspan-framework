package ai.loomspan.internal.observability;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.observability.web.ObservabilityActivityDelivery;
import ai.loomspan.internal.observability.web.ObservabilityArtifactDelivery;
import ai.loomspan.internal.runtime.observation.ActiveExecutionRegistry;
import ai.loomspan.internal.runtime.observation.ActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.catalog.FinalizedTraceCatalog;
import ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog;
import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ObservabilityRuntimeTest
{
    @Test
    void closesActivityThenArtifactDeliveryBeforeRetentionAndCatalog()
    {
        ObservabilityActivityDelivery activity = mock(ObservabilityActivityDelivery.class);
        ObservabilityArtifactDelivery artifact = mock(ObservabilityArtifactDelivery.class);
        CompletionGraceRetention retention = mock(CompletionGraceRetention.class);
        FinalizedTraceCatalog traces = mock(FinalizedTraceCatalog.class);
        ObservabilityRuntime runtime = runtime(activity, artifact, retention, traces);

        runtime.close();
        runtime.close();

        InOrder order = inOrder(activity, artifact, retention, traces);
        order.verify(activity).close();
        order.verify(artifact).close();
        order.verify(retention).close();
        order.verify(traces).close();
        verifyNoMoreInteractions(activity, artifact, retention, traces);
    }

    @Test
    void shutdownFailurePreservesFirstFailureAndSuppressesLaterFailures()
    {
        ObservabilityActivityDelivery activity = mock(ObservabilityActivityDelivery.class);
        ObservabilityArtifactDelivery artifact = mock(ObservabilityArtifactDelivery.class);
        CompletionGraceRetention retention = mock(CompletionGraceRetention.class);
        FinalizedTraceCatalog traces = mock(FinalizedTraceCatalog.class);
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second");
        org.mockito.Mockito.doThrow(first).when(activity).close();
        org.mockito.Mockito.doThrow(second).when(retention).close();

        assertThatThrownBy(() -> runtime(activity, artifact, retention, traces).close())
                .isSameAs(first)
                .satisfies(failure -> org.assertj.core.api.Assertions.assertThat(failure.getSuppressed())
                        .containsExactly(second));
    }

    private static ObservabilityRuntime runtime(
            ObservabilityActivityDelivery activity,
            ObservabilityArtifactDelivery artifact,
            CompletionGraceRetention retention,
            FinalizedTraceCatalog traces)
    {
        return new ObservabilityRuntime(
                UUID.randomUUID(), Clock.systemUTC(), mock(ExecutionObservationHandleFactory.class),
                activity, artifact, retention, mock(ActiveExecutionRegistry.class),
                mock(ActivityReplayBuffer.class), new LiveMonitoringAvailability(),
                mock(RegisteredSkillCatalog.class), traces, new LoomspanProperties.Observability(),
                new LoomspanProperties.Session.Quotas(), TracePersistencePolicy.ONERROR);
    }
}
