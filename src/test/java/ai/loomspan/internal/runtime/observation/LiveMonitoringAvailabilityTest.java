package ai.loomspan.internal.runtime.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveMonitoringAvailabilityTest
{
    @Test
    void transitionsOnceAndPreservesFirstFailure()
    {
        LiveMonitoringAvailability availability = new LiveMonitoringAvailability();

        assertThat(availability.isAvailable()).isTrue();
        assertThat(availability.fail("PROJECTION_FAILED", new IllegalArgumentException("secret"))).isTrue();
        assertThat(availability.fail("REPLAY_PUBLICATION_FAILED", new IllegalStateException("later"))).isFalse();

        assertThat(availability.isAvailable()).isFalse();
        assertThat(availability.firstFailure()).get()
                .extracting(LiveMonitoringAvailability.Failure::operation)
                .isEqualTo("PROJECTION_FAILED");
        assertThat(availability.firstFailure().orElseThrow().exceptionClass())
                .isEqualTo(IllegalArgumentException.class.getName());
    }
}
