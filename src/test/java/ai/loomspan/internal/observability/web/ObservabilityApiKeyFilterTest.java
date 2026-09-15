package ai.loomspan.internal.observability.web;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import ai.loomspan.internal.observability.ObservabilityRuntime;
import ai.loomspan.internal.runtime.observation.ActiveExecutionRegistry;
import ai.loomspan.internal.runtime.observation.ActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.catalog.FinalizedTraceCatalog;
import ai.loomspan.internal.runtime.observation.catalog.RegisteredSkillCatalog;
import ai.loomspan.internal.runtime.trace.CompletionGraceRetention;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@ExtendWith(OutputCaptureExtension.class)
class ObservabilityApiKeyFilterTest
{
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void establishesOnlyOperatorAuthorityAndRestoresHostContext() throws Exception
    {
        ObservabilityActivationCoordinator activation = enabledActivation();
        ObservabilityApiKeyFilter filter = new ObservabilityApiKeyFilter(
                activation, new ObservabilityJsonCodec(), new ObservabilityProblemMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ObservabilityApiPaths.INSTANCE);
        request.addHeader(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        var hostAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                "host-user", null, List.of(new SimpleGrantedAuthority("HOST_USER")));
        var hostContext = new SecurityContextImpl(hostAuthentication);
        SecurityContextHolder.setContext(hostContext);

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
        {
            var current = SecurityContextHolder.getContext().getAuthentication();
            assertThat(current.getName()).isEqualTo("loomspan-operator");
            assertThat(current).isInstanceOf(ObservabilityOperatorAuthentication.class);
            assertThat(current.getAuthorities())
                    .extracting(authority -> authority.getAuthority())
                    .containsExactly(ObservabilityAccessService.AUTHORITY);
        });

        assertThat(SecurityContextHolder.getContext()).isSameAs(hostContext);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(hostAuthentication);
        activation.close();
    }

    @Test
    void accessServiceRejectsHostPrincipalWithLookalikeAuthority()
    {
        var hostAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                "host-user", null, List.of(new SimpleGrantedAuthority("LOOMSPAN_OPERATOR")));

        assertThatThrownBy(() -> new ObservabilityAccessService().require(
                ObservabilityAccessService.Operation.INSTANCE_READ, hostAuthentication))
                .isInstanceOf(ObservabilityException.class)
                .extracting(failure -> ((ObservabilityException) failure).problem().code())
                .isEqualTo(ObservabilityProblem.Code.LOOMSPAN_API_KEY_REJECTED);
    }

    @Test
    void rejectsDuplicateAndOversizedHeadersWithoutInstanceIdentity() throws Exception
    {
        ObservabilityActivationCoordinator activation = enabledActivation();
        ObservabilityApiKeyFilter filter = new ObservabilityApiKeyFilter(
                activation, new ObservabilityJsonCodec(), new ObservabilityProblemMapper());
        for (MockHttpServletRequest request : List.of(duplicateRequest(), oversizedRequest()))
        {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
            {
                throw new AssertionError("Rejected credentials must not reach the handler");
            });
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader(ObservabilityApiKeyFilter.INSTANCE_HEADER)).isNull();
            assertThat(response.getContentAsString()).contains("LOOMSPAN_API_KEY_REJECTED");
        }
        activation.close();
    }

    @Test
    void logsSanitizedUnexpectedFailure(CapturedOutput output) throws Exception
    {
        ObservabilityActivationCoordinator activation = enabledActivation();
        ObservabilityApiKeyFilter filter = new ObservabilityApiKeyFilter(
                activation, new ObservabilityJsonCodec(), new ObservabilityProblemMapper());
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
        {
            throw new IllegalStateException("sensitive-exception-detail");
        });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("APPLICATION_ERROR");
        assertThat(output).contains("loomspan observability request failed",
                "exceptionClass=java.lang.IllegalStateException");
        assertThat(output).doesNotContain("sensitive-exception-detail", KEY);
        activation.close();
    }

    @Test
    void propagatesJvmErrorsWithoutLoggingTheirDetails(CapturedOutput output)
    {
        ObservabilityActivationCoordinator activation = enabledActivation();
        ObservabilityApiKeyFilter filter = new ObservabilityApiKeyFilter(
                activation, new ObservabilityJsonCodec(), new ObservabilityProblemMapper());
        MockHttpServletRequest request = authenticatedRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AssertionError fatal = new AssertionError("fatal-sensitive-detail");

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
        {
            throw fatal;
        })).isSameAs(fatal);

        assertThat(output).doesNotContain("fatal-sensitive-detail");
        activation.close();
    }

    private static MockHttpServletRequest duplicateRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ObservabilityApiPaths.INSTANCE);
        request.addHeader(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        request.addHeader(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        return request;
    }

    private static MockHttpServletRequest oversizedRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ObservabilityApiPaths.INSTANCE);
        request.addHeader(ObservabilityApiKeyFilter.API_KEY_HEADER, "x".repeat(513));
        return request;
    }

    private static MockHttpServletRequest authenticatedRequest()
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", ObservabilityApiPaths.INSTANCE);
        request.addHeader(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        return request;
    }

    private static ObservabilityActivationCoordinator enabledActivation()
    {
        LoomspanProperties.Observability configuration = new LoomspanProperties.Observability();
        configuration.getAuth().setApiKey(KEY);
        ObservabilityRuntime runtime = new ObservabilityRuntime(
                UUID.randomUUID(),
                Clock.systemUTC(),
                mock(ExecutionObservationHandleFactory.class),
                mock(ObservabilityActivityDelivery.class),
                mock(ObservabilityArtifactDelivery.class),
                mock(CompletionGraceRetention.class),
                mock(ActiveExecutionRegistry.class),
                mock(ActivityReplayBuffer.class),
                new LiveMonitoringAvailability(),
                mock(RegisteredSkillCatalog.class),
                mock(FinalizedTraceCatalog.class),
                configuration,
                new LoomspanProperties.Session.Quotas(),
                TracePersistencePolicy.ONERROR);
        ObservabilityActivationCoordinator activation = new ObservabilityActivationCoordinator();
        activation.enable(runtime);
        return activation;
    }
}
