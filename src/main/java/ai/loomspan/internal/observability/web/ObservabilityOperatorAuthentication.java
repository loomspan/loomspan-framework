package ai.loomspan.internal.observability.web;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serial;
import java.util.List;

final class ObservabilityOperatorAuthentication extends AbstractAuthenticationToken
{
    @Serial
    private static final long serialVersionUID = 1L;

    ObservabilityOperatorAuthentication()
    {
        super(List.of(new SimpleGrantedAuthority(ObservabilityAccessService.AUTHORITY)));
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials()
    {
        return "";
    }

    @Override
    public Object getPrincipal()
    {
        return "loomspan-operator";
    }
}
