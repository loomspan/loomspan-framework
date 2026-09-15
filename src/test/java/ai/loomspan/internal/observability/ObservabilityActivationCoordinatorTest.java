package ai.loomspan.internal.observability;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.runtime.observation.ActiveExecutionRegistry;
import ai.loomspan.internal.runtime.observation.ActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.catalog.FinalizedTraceCatalog;
import ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog;
import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import ai.loomspan.internal.observability.web.ObservabilityActivityDelivery;
import ai.loomspan.internal.observability.web.ObservabilityArtifactDelivery;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ObservabilityActivationCoordinatorTest
{
    @Test
    void closeDisablesActivationAndClosesOwnedRuntimeResources()
    {
        CompletionGraceRetention retention = mock(CompletionGraceRetention.class);
        FinalizedTraceCatalog traces = mock(FinalizedTraceCatalog.class);
        ObservabilityActivityDelivery delivery = mock(ObservabilityActivityDelivery.class);
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                UUID.randomUUID(),
                Clock.systemUTC(),
                mock(ExecutionObservationHandleFactory.class),
                delivery,
                mock(ObservabilityArtifactDelivery.class),
                retention,
                mock(ActiveExecutionRegistry.class),
                mock(ActivityReplayBuffer.class),
                new LiveMonitoringAvailability(),
                mock(RegisteredSkillCatalog.class),
                traces,
                new LoomspanProperties.Observability(),
                new LoomspanProperties.Session.Quotas(),
                TracePersistencePolicy.ONERROR);
        ObservabilityActivationCoordinator coordinator = new ObservabilityActivationCoordinator();
        coordinator.enable(runtime);

        coordinator.close();
        coordinator.close();

        assertThat(coordinator.state()).isEqualTo(ObservabilityActivationCoordinator.State.DISABLED);
        assertThat(coordinator.runtime()).isEmpty();
        verify(delivery).close();
        verify(retention).close();
        verify(traces).close();
    }
}
