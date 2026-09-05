package ai.loomspan.internal.observability.web;

import java.time.Duration;

final class ObservabilityDeliveryLimits
{
    static final int OPEN_SUBSCRIPTIONS = 16;
    static final int OPEN_ARTIFACT_DOWNLOADS = 8;
    static final int PENDING_ACTIVITY_FRAMES = 256;
    static final long PENDING_BYTES = 1024L * 1024L;
    static final int REPLAY_BATCH = 256;
    static final Duration WRITE_READINESS_DEADLINE = Duration.ofSeconds(5);
    static final Duration ARTIFACT_DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private ObservabilityDeliveryLimits() {}
}
