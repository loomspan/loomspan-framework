package ai.loomspan.internal.security;

import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.CapabilityMetadata;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

public interface AccessGuard
{
    @Nullable
    Authentication resolveAuthentication(@Nullable Authentication invocationAuthentication, LoomspanSession session);

    boolean canAccess(CapabilityMetadata capability, LoomspanSession session, @Nullable Authentication invocationAuthentication);

    void checkAccess(CapabilityMetadata capability, LoomspanSession session, @Nullable Authentication invocationAuthentication);
}
