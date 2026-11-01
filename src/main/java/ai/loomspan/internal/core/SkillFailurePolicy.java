package ai.loomspan.internal.core;

import ai.loomspan.api.RecoverableSkillException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/** One failure policy for invocation, orchestration and canonical recording. */
public final class SkillFailurePolicy
{
    private SkillFailurePolicy() {}

    public static Throwable unwrapInvocationFailure(Throwable failure)
    {
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (int depth = 0; depth < 64 && visited.add(failure); depth++)
        {
            Throwable target;
            if (failure instanceof InvocationTargetException wrapper) target = wrapper.getTargetException();
            else if (failure instanceof UndeclaredThrowableException wrapper) target = wrapper.getUndeclaredThrowable();
            else if (failure instanceof java.util.concurrent.ExecutionException wrapper) target = wrapper.getCause();
            else if (failure instanceof org.springframework.ai.tool.execution.ToolExecutionException wrapper) target = wrapper.getCause();
            else return failure;
            if (target == null || target == failure) return failure;
            failure = target;
        }
        return failure;
    }

    public static RecoverableSkillException recoverable(Throwable failure)
    {
        return unwrapInvocationFailure(failure) instanceof RecoverableSkillException recoverable ? recoverable : null;
    }

    public static RuntimeException propagate(Throwable failure)
    {
        Throwable unwrapped = unwrapInvocationFailure(failure);
        if (unwrapped instanceof RuntimeException runtime) return runtime;
        if (unwrapped instanceof Error error) throw error;
        // Checked application failures still require a transparent infrastructure representation.
        return new UndeclaredThrowableException(unwrapped);
    }
}
