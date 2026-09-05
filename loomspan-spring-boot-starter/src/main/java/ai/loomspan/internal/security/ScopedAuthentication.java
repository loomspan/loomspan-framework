package ai.loomspan.internal.security;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/** Installs an isolated context only for the synchronous Spring proxy boundary. */
public final class ScopedAuthentication
{
    private final SecurityContextHolderStrategy strategy;

    public ScopedAuthentication(@Nullable SecurityContextHolderStrategy strategy)
    {
        this.strategy = strategy == null ? SecurityContextHolder.getContextHolderStrategy() : strategy;
    }

    public Scope open(@Nullable Authentication authentication)
    {
        SecurityContext previous = strategy.getContext();
        SecurityContext current = strategy.createEmptyContext();
        current.setAuthentication(authentication);
        strategy.setContext(current);
        return () -> strategy.setContext(previous);
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable
    {
        @Override
        void close();
    }
}
