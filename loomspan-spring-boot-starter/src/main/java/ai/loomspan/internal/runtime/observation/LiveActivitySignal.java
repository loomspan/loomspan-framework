package ai.loomspan.internal.runtime.observation;

/**
 * Internal, non-blocking notification seam between synchronous observation
 * publication and runtime-owned activity delivery.
 */
public interface LiveActivitySignal
{
    LiveActivitySignal NO_OP = new LiveActivitySignal()
    {
        @Override
        public void activityAvailable() {}

        @Override
        public void liveUnavailable() {}
    };

    void activityAvailable();

    void liveUnavailable();
}
