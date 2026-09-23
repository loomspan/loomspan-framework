package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ObservabilityActuatorCoexistenceIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.server.port=0",
                "management.endpoint.health.probes.enabled=true",
                "loomspan.observability.enabled=true",
                "loomspan.observability.auth.api-key=0123456789abcdef0123456789abcdef",
                "loomspan.skills.locations=classpath:/observability-no-skills/*.yaml"
        })
class ObservabilityActuatorCoexistenceIntegrationTest
{
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Autowired
    ObservabilityActivationCoordinator activation;

    @Autowired
    List<HandlerMapping> handlerMappings;

    @Test
    void actuatorSeparateManagementPortDoesNotDisableAuthenticatedConsole() throws Exception
    {
        assertThat(handlerMappings)
                .extracting(mapping -> mapping.getClass().getSimpleName())
                .contains("AdditionalHealthEndpointPathsWebMvcHandlerMapping");
        assertThat(activation.state()).isEqualTo(ObservabilityActivationCoordinator.State.ENABLED);

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> validKey = client.send(request(true), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> missingKey = client.send(request(false), HttpResponse.BodyHandlers.ofString());

        assertThat(validKey.statusCode()).isEqualTo(200);
        assertThat(validKey.body()).contains("\"consoleCompatibilityVersion\":\"1.0.0-beta.5\"");
        assertThat(missingKey.statusCode()).isEqualTo(401);
        assertThat(missingKey.body()).contains("\"code\":\"LOOMSPAN_API_KEY_REJECTED\"");
    }

    private HttpRequest request(boolean withKey)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + ObservabilityApiPaths.INSTANCE)).GET();
        if (withKey)
        {
            builder.header(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        }
        return builder.build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class,
            ServletWebSecurityAutoConfiguration.class }, excludeName =
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration")
    static class TestApplication
    {
    }
}
