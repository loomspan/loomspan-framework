package ai.loomspan.internal.observability.web;

import org.springframework.security.core.Authentication;

public final class ObservabilityAccessService
{
    public enum Operation
    {
        INSTANCE_READ, SKILL_READ, ACTIVE_READ, ACTIVITY_SUBSCRIBE, TRACE_READ, TRACE_ARTIFACT_READ
    }
    public static final String AUTHORITY = "LOOMSPAN_OPERATOR";

    public void require(Operation operation, Authentication authentication)
    {
        if (operation == null || !(authentication instanceof ObservabilityOperatorAuthentication)
                || !authentication.isAuthenticated())
        {
            throw new ObservabilityException(401,
                    ObservabilityProblem.Code.LOOMSPAN_API_KEY_REJECTED, "loomspan API key was rejected");
        }
    }
}
