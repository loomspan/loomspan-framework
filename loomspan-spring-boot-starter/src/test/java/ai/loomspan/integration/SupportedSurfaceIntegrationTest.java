package ai.loomspan.integration;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import jakarta.annotation.security.RolesAllowed;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

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
    void invokesYamlPlannerAndBothLeafKindsThroughSupportedSurface() throws Exception
    {
        try (MockWebServer server = new MockWebServer())
        {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"capabilityName\\\":\\\"supportedSurfaceSkill\\\",\\\"createdAt\\\":\\\"2026-09-12T12:00:00Z\\\",\\\"status\\\":\\\"VALID\\\",\\\"tasks\\\":[{\\\"taskId\\\":\\\"java-task\\\",\\\"title\\\":\\\"Call Java leaf\\\",\\\"status\\\":\\\"PENDING\\\",\\\"capabilityName\\\":\\\"supportedJavaLeaf\\\",\\\"intent\\\":\\\"Echo through Java\\\",\\\"dependsOn\\\":[],\\\"expectedOutputs\\\":[\\\"Java echo\\\"],\\\"parallelGroup\\\":null,\\\"note\\\":\\\"\\\"},{\\\"taskId\\\":\\\"rest-task\\\",\\\"title\\\":\\\"Call REST leaf\\\",\\\"status\\\":\\\"PENDING\\\",\\\"capabilityName\\\":\\\"supportedRestLeaf\\\",\\\"intent\\\":\\\"Echo through REST\\\",\\\"dependsOn\\\":[\\\"java-task\\\"],\\\"expectedOutputs\\\":[\\\"REST echo\\\"],\\\"parallelGroup\\\":null,\\\"note\\\":\\\"\\\"}]}"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":3,"completion_tokens":3,"total_tokens":6}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-java","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"stepAction\\\":\\\"CALL_TOOL\\\",\\\"taskId\\\":\\\"java-task\\\",\\\"toolName\\\":\\\"supportedJavaLeaf\\\",\\\"toolArguments\\\":{\\\"message\\\":\\\"hello through the public API\\\"}}"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":4,"completion_tokens":3,"total_tokens":7}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-rest","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"stepAction\\\":\\\"CALL_TOOL\\\",\\\"taskId\\\":\\\"rest-task\\\",\\\"toolName\\\":\\\"supportedRestLeaf\\\",\\\"toolArguments\\\":{\\\"message\\\":\\\"hello through the public API\\\"}}"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":4,"completion_tokens":3,"total_tokens":7}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-final","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"stepAction\\\":\\\"FINAL_RESPONSE\\\",\\\"finalResponse\\\":\\\"supported surface response\\\"}"},
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
                        assertThat(context).hasSingleBean(SkillInvocationHandoff.class);
                        assertThat(context).hasSingleBean(SkillCatalog.class);

                        SkillTemplate skills = context.getBean(SkillTemplate.class);
                        SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                        SkillCatalog catalog = context.getBean(SkillCatalog.class);
                        assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                                .isSorted()
                                .contains("supportedSurfaceSkill", "supportedJavaLeaf", "supportedRestLeaf");
                        assertThat(catalog.skill("supportedJavaLeaf")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.JAVA);
                        assertThat(catalog.skill("supportedRestLeaf")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.REST);
                        assertThat(catalog.skill("supportedSurfaceSkill")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.YAML);
                        assertThat(catalog.skill("missing")).isEmpty();
                        AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
                        var authorized = UsernamePasswordAuthenticationToken.authenticated(
                                "supported-caller", "unused", AuthorityUtils.createAuthorityList("ROLE_REST_USER"));
                        try {
                            SecurityContextHolder.getContext().setAuthentication(authorized);
                            skills.validate("supportedSurfaceSkill",
                                    Map.of("message", "hello through the public API"));
                            skills.validate("supportedJavaLeaf", new DirectRequest("precheck"));
                            skills.validate("supportedRestLeaf", new DirectRequest("precheck"));
                            var admitted = handoff.handoff("supportedRestLeaf", new DirectRequest("handed-off"));
                            SecurityContextHolder.clearContext();
                            assertThat(admitted.invoke()).isEqualTo("REST: handed-off");
                            admitted.release();
                            assertThat(SupportedSkillConfiguration.handlerAuthentication)
                                    .hasValue("supported-caller");
                            SecurityContextHolder.getContext().setAuthentication(authorized);
                            var admittedRoot = handoff.handoff(
                                    "supportedSurfaceSkill",
                                    Map.of("message", "hello through the public API"));
                            assertThat(admittedRoot.invoke(observed::set))
                                    .isEqualTo("supported surface response");
                            admittedRoot.release();

                            assertThat(observed.get()).isNotNull();
                            assertThat(observed.get().sessionId()).isNotBlank();
                            assertThat(observed.get().events()).isNotNull();
                            assertThat(observed.get().events()).extracting(event -> event.type())
                                    .contains("PLAN_CREATED", "TOOL_CALL", "TOOL_RESULT", "SKILL_FINISHED");
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

                            AtomicInteger preSessionObserverCalls = new AtomicInteger();
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke(
                                    "supportedRestLeaf", Map.of(), ignored -> preSessionObserverCalls.incrementAndGet())))
                                    .isInstanceOf(SkillInputValidationException.class);
                            assertThat(preSessionObserverCalls).hasValue(0);

                            AtomicReference<SkillExecutionView> failedView = new AtomicReference<>();
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke(
                                    "supportedRestLeaf", Map.of("message", "fail"), failedView::set)))
                                    .isInstanceOf(SkillException.class)
                                    .hasMessage("Skill 'supportedRestLeaf' execution failed.");
                            assertThat(failedView.get()).isNotNull();
                            assertThat(failedView.get().events()).isNotEmpty();

                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            "denied-caller", "unused", AuthorityUtils.NO_AUTHORITIES));
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                                    () -> skills.validate("supportedRestLeaf", Map.of("message", "denied"))))
                                    .isInstanceOf(AccessDeniedException.class);
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                                    () -> skills.validate("supportedJavaLeaf", Map.of("message", "denied"))))
                                    .isInstanceOf(AccessDeniedException.class);
                            AtomicReference<SkillExecutionView> rejectedView = new AtomicReference<>();
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke(
                                    "supportedRestLeaf", Map.of("message", "denied"), rejectedView::set)))
                                    .isInstanceOf(AccessDeniedException.class)
                                    .hasMessage("Access denied for capability 'supportedRestLeaf'");
                            assertThat(rejectedView.get()).isNotNull();
                            assertThat(rejectedView.get().events()).extracting(event -> event.type())
                                    .contains("ERROR");
                            assertThat(SupportedSkillConfiguration.handlerCalls).hasValue(authorizedCalls + 1);
                        }
                        finally {
                            SecurityContextHolder.clearContext();
                        }
                    });

            RecordedRequest planningRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(planningRequest).isNotNull();
            assertThat(planningRequest.getPath()).isEqualTo("/v1/chat/completions");
            assertThat(planningRequest.getHeader("Authorization")).isEqualTo("Bearer integration-key");
            assertThat(planningRequest.getBody().readUtf8())
                    .contains("\"model\":\"integration-model\"")
                    .contains("hello through the public API")
                    .contains("Create an ordered flight plan")
                    .contains("supportedJavaLeaf", "supportedRestLeaf");

            RecordedRequest javaRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(javaRequest).isNotNull();
            String javaBody = javaRequest.getBody().readUtf8();
            assertThat(javaBody)
                    .contains("java-task", "supportedJavaLeaf")
                    .doesNotContain("REST: hello through the public API");
            JsonNode javaPayload = new ObjectMapper().readTree(javaBody);
            assertThat(requestMessages(javaPayload))
                    .containsOnlyOnce("--- TOOL ARGUMENT SHAPE ---")
                    .contains("\"message\": \"<string>\"")
                    .doesNotContain("\"values\":");

            RecordedRequest restRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(restRequest).isNotNull();
            String restBody = restRequest.getBody().readUtf8();
            assertThat(restBody)
                    .contains("rest-task", "supportedRestLeaf", "Java: hello through the public API");
            JsonNode restPayload = new ObjectMapper().readTree(restBody);
            assertThat(requestMessages(restPayload))
                    .containsOnlyOnce("--- TOOL ARGUMENT SHAPE ---")
                    .contains("\"message\": \"<string>\"", "\"values\": [ \"<string>\" ]");

            RecordedRequest finalRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(finalRequest).isNotNull();
            assertThat(finalRequest.getBody().readUtf8())
                    .contains("Java: hello through the public API")
                    .contains("REST: hello through the public API");
        }
    }

    private static String requestMessages(JsonNode payload)
    {
        StringBuilder messages = new StringBuilder();
        payload.path("messages").forEach(message -> messages.append(message.path("content").asText()).append('\n'));
        return messages.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(jsr250Enabled = true)
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
                if ("fail".equals(invocation.input().get("message")))
                {
                    throw new IllegalStateException("application REST failure");
                }
                return "REST: " + invocation.input().get("message");
            };
        }
    }

    static class SupportedTarget
    {
        @SkillMethod(name = "supportedJavaLeaf", description = "Echo a message through an application-owned Java leaf.")
        @RolesAllowed("REST_USER")
        String echo(@SkillParam(description = "Message supplied by the parent skill.") String message)
        {
            return "Java: " + message;
        }
    }

    private record DirectRequest(String message)
    {
    }
}
