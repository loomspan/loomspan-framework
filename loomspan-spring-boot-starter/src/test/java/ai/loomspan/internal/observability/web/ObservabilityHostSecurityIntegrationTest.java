package ai.loomspan.internal.observability.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ObservabilityHostSecurityIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "loomspan.observability.enabled=true",
                "loomspan.observability.auth.api-key=0123456789abcdef0123456789abcdef",
                "loomspan.skills.locations=classpath:/observability-test/*.yaml",
                "loomspan.connections.local.driver=ollama",
                "loomspan.connections.local.base-url=http://localhost:11434",
                "loomspan.models.test.connection=local",
                "loomspan.models.test.provider-model=test-model"
        })
class ObservabilityHostSecurityIntegrationTest
{
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Test
    void hostPassThroughStillRequiresLoomspanKeyAndBusinessRoutesStayHostOwned() throws Exception
    {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> missingKey = client.send(request(ObservabilityApiPaths.INSTANCE, false),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> validKey = client.send(request(ObservabilityApiPaths.INSTANCE, true),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> business = client.send(request("/business", true),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> forwarded = client.send(request("/forward", false),
                HttpResponse.BodyHandlers.ofString());

        assertThat(missingKey.statusCode()).isEqualTo(401);
        assertThat(missingKey.body()).contains("\"code\":\"LOOMSPAN_API_KEY_REJECTED\"");
        assertThat(validKey.statusCode()).isEqualTo(200);
        assertThat(business.statusCode()).isIn(401, 403);
        assertThat(business.body()).doesNotContain("LOOMSPAN_API_KEY_REJECTED");
        assertThat(business.headers().firstValue(ObservabilityApiKeyFilter.INSTANCE_HEADER)).isEmpty();
        assertThat(forwarded.statusCode()).isEqualTo(401);
        assertThat(forwarded.body()).contains("\"code\":\"LOOMSPAN_API_KEY_REJECTED\"");
    }

    private HttpRequest request(String path, boolean withKey)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET();
        if (withKey)
        {
            builder.header(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY);
        }
        return builder.build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({HostSecurity.class, BusinessController.class})
    static class TestApplication
    {
    }

    static class HostSecurity
    {
        @Bean
        SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception
        {
            return http
                    .authorizeHttpRequests(requests -> requests
                            .requestMatchers(ObservabilityApiPaths.ROOT + "/**").permitAll()
                            .requestMatchers("/forward").permitAll()
                            .anyRequest().authenticated())
                    .build();
        }
    }

    @RestController
    static class BusinessController
    {
        @GetMapping("/business")
        String business()
        {
            return "business";
        }

        @GetMapping("/forward")
        void forward(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            "host-operator", null,
                            List.of(new SimpleGrantedAuthority(ObservabilityAccessService.AUTHORITY))));
            request.getRequestDispatcher(ObservabilityApiPaths.INSTANCE).forward(request, response);
        }
    }
}
