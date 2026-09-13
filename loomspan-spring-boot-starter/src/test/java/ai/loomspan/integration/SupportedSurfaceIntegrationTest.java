package ai.loomspan.integration;

import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedSurfaceIntegrationTest
{
    @Test
    void invokesLlmBackedYamlSkillThroughSupportedSurfaceAndStandardConnectionConfiguration() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":null,
                                         "tool_calls":[{"id":"Java-call","type":"function","function":
                                         {"name":"supportedJavaLeaf","arguments":"{\\\"message\\\":\\\"hello through the public API\\\"}"}},
                                         {"id":"REST-call","type":"function","function":
                                         {"name":"supportedRestLeaf","arguments":"{\\\"message\\\":\\\"hello through the public API\\\"}"}}]},
                                         "finish_reason":"tool_calls"}],
                             "usage":{"prompt_tokens":3,"completion_tokens":3,"total_tokens":6}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-final","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"supported surface response"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":4,"completion_tokens":3,"total_tokens":7}}
                            """));

            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            ConfigurationPropertiesAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                            LoomspanAutoConfiguration.class,
                            ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
                    .withUserConfiguration(SupportedSkillConfiguration.class)
                    .withPropertyValues(
                            "loomspan.skills.locations=classpath:/skills/integration/*.yml",
                            "loomspan.connections.integration.driver=openai",
                            "loomspan.connections.integration.base-url=" + server.url("/v1"),
                            "loomspan.connections.integration.api-key=integration-key",
                            "loomspan.models.integration.connection=integration",
                            "loomspan.models.integration.provider-model=integration-model",
                            "loomspan.session.mission-timeout=20s")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(SkillTemplate.class);

                        SkillTemplate skills = context.getBean(SkillTemplate.class);
                        AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
                        var authorized = UsernamePasswordAuthenticationToken.authenticated(
                                "supported-caller", "unused", AuthorityUtils.createAuthorityList("ROLE_REST_USER"));
                        try {
                            SecurityContextHolder.getContext().setAuthentication(authorized);
                            assertThat(skills.invoke(
                                    "supportedSurfaceSkill",
                                    Map.of("message", "hello through the public API"),
                                    observed::set))
                                    .isEqualTo("supported surface response");

                            assertThat(observed.get()).isNotNull();
                            assertThat(observed.get().sessionId()).isNotBlank();
                            assertThat(observed.get().events()).isNotNull();
                            List<Object> mutableValues = new ArrayList<>(List.of("first"));
                            Map<String, Object> mutableInput = new LinkedHashMap<>();
                            mutableInput.put("message", "direct");
                            mutableInput.put("values", mutableValues);
                            assertThat(skills.invoke("supportedRestLeaf", mutableInput))
                                    .isEqualTo("REST: direct");
                            mutableValues.add("late");
                            assertThat((List<Object>) SupportedSkillConfiguration.lastInvocation.get().input().get("values"))
                                    .containsExactly("first");
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                                    ((List<Object>) SupportedSkillConfiguration.lastInvocation.get().input().get("values"))
                                            .add("forbidden")))
                                    .isInstanceOf(UnsupportedOperationException.class);
                            assertThat(SupportedSkillConfiguration.handlerAuthentication).hasValue("supported-caller");
                            int authorizedCalls = SupportedSkillConfiguration.handlerCalls.get();

                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            "denied-caller", "unused", AuthorityUtils.NO_AUTHORITIES));
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                                    () -> skills.invoke("supportedRestLeaf", Map.of("message", "denied"))))
                                    .isInstanceOf(AccessDeniedException.class);
                            assertThat(SupportedSkillConfiguration.handlerCalls).hasValue(authorizedCalls);
                        }
                        finally {
                            SecurityContextHolder.clearContext();
                        }
                    });

            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getPath()).isEqualTo("/v1/chat/completions");
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer integration-key");
            assertThat(request.getBody().readUtf8())
                    .contains("\"model\":\"integration-model\"")
                    .contains("hello through the public API")
                    .contains("supportedJavaLeaf");

            RecordedRequest finalRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(finalRequest).isNotNull();
            assertThat(finalRequest.getBody().readUtf8())
                    .contains("Java: hello through the public API")
                    .contains("REST: hello through the public API");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SupportedSkillConfiguration
    {
        private static final AtomicReference<String> handlerAuthentication = new AtomicReference<>();
        private static final AtomicReference<RestSkillInvocation> lastInvocation = new AtomicReference<>();
        private static final AtomicInteger handlerCalls = new AtomicInteger();

        @Bean
        SupportedTarget supportedTargetBean()
        {
            return new SupportedTarget();
        }

        @Bean
        RestSkillHandler restSkillHandler()
        {
            return invocation -> {
                handlerCalls.incrementAndGet();
                handlerAuthentication.set(SecurityContextHolder.getContext().getAuthentication().getName());
                lastInvocation.set(invocation);
                return "REST: " + invocation.input().get("message");
            };
        }
    }

    static class SupportedTarget
    {
        @SkillMethod(name = "supportedJavaLeaf", description = "Echo a message through an application-owned Java leaf.")
        String echo(@SkillParam(description = "Message supplied by the parent skill.") String message)
        {
            return "Java: " + message;
        }
    }
}
