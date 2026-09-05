package ai.loomspan.internal.runtime.observation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LiveMonitoringAvailability
{
    private final AtomicReference<Failure> firstFailure = new AtomicReference<>();

    public boolean isAvailable()
    {
        return firstFailure.get() == null;
    }

    public Optional<Failure> firstFailure()
    {
        return Optional.ofNullable(firstFailure.get());
    }

    boolean fail(String operation, Throwable failure)
    {
        Objects.requireNonNull(failure, "failure must not be null");
        Failure candidate = new Failure(operation, failure.getClass().getName());
        return firstFailure.compareAndSet(null, candidate);
    }

    public record Failure(String operation, String exceptionClass)
    {
        public Failure
        {
            operation = requireNonBlank(operation, "operation");
            exceptionClass = requireNonBlank(exceptionClass, "exceptionClass");
        }

        private static String requireNonBlank(String value, String name)
        {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isBlank())
            {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value;
        }
    }
}
