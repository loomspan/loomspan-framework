package ai.loomspan.internal.core;

import ai.loomspan.api.RecoverableSkillException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;

/** The Java invocation boundary only: never searches an application's arbitrary cause chain. */
final class SkillFailurePolicy
{
    private SkillFailurePolicy() {}

    static Throwable unwrapInvocationFailure(Throwable failure)
    {
        while (true)
        {
            Throwable target;
            if (failure instanceof InvocationTargetException wrapper) target = wrapper.getTargetException();
            else if (failure instanceof UndeclaredThrowableException wrapper) target = wrapper.getUndeclaredThrowable();
            else return failure;
            if (target == null || target == failure) return failure;
            failure = target;
        }
    }

    static RecoverableSkillException recoverable(Throwable failure)
    {
        return unwrapInvocationFailure(failure) instanceof RecoverableSkillException recoverable ? recoverable : null;
    }

    static RuntimeException propagate(Throwable failure)
    {
        Throwable unwrapped = unwrapInvocationFailure(failure);
        if (unwrapped instanceof RuntimeException runtime) return runtime;
        if (unwrapped instanceof Error error) throw error;
        // Checked application failures still require a transparent infrastructure representation.
        return new UndeclaredThrowableException(unwrapped);
    }
}
