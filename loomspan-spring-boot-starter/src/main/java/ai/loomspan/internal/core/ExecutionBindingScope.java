package ai.loomspan.internal.core;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Sole ambient holder for Loomspan execution identity. */
public final class ExecutionBindingScope
{
    private static final ThreadLocal<ExecutionBinding> CURRENT = new ThreadLocal<>();

    private ExecutionBindingScope() {}

    public static Optional<ExecutionBinding> current()
    {
        return Optional.ofNullable(CURRENT.get());
    }

    public static ExecutionBinding requireCurrent()
    {
        ExecutionBinding binding = CURRENT.get();
        if (binding == null) throw new IllegalStateException("No Loomspan execution binding is active on the current thread.");
        return binding;
    }

    public static void runWith(ExecutionBinding binding, Runnable action)
    {
        callUnchecked(binding, () -> { action.run(); return null; });
    }

    public static <T> T supplyWith(ExecutionBinding binding, Supplier<T> action)
    {
        return callUnchecked(binding, action::get);
    }

    public static <T> T callWith(ExecutionBinding binding, Callable<T> action) throws Exception
    {
        Objects.requireNonNull(action, "action must not be null");
        return scoped(binding, action);
    }

    private static <T> T callUnchecked(ExecutionBinding binding, Callable<T> action)
    {
        try
        {
            return scoped(binding, action);
        }
        catch (RuntimeException | Error ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("Unexpected checked exception from execution binding scope", ex);
        }
    }

    private static <T> T scoped(ExecutionBinding binding, Callable<T> action) throws Exception
    {
        Objects.requireNonNull(binding, "binding must not be null");
        ExecutionBinding previous = CURRENT.get();
        if (previous != null && previous.session() != binding.session())
        {
            throw new IllegalArgumentException("Cannot bind an execution from a different session inside the current scope.");
        }
        CURRENT.set(binding);
        try
        {
            return action.call();
        }
        finally
        {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
