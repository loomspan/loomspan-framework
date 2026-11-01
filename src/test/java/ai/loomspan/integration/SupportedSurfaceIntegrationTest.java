package ai.loomspan.integration;

import ai.loomspan.testkit.FixtureExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
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
    @Test void javaFoundationUsesOnlySupportedTypesAndKeepsExecutorsReferenceOnly()
    {
        NamespaceStartupIntegrationTest.runner().withBean(JavaFoundation.class).run(context -> {
            assertThat(context).hasNotFailed();
            var skills = context.getBean(SkillTemplate.class);
            assertThat(skills.invoke("default-foundationText", Map.of())).isEqualTo("  exact text\n");
            assertThat(skills.invoke("default-foundationNull", Map.of())).isEmpty();
            var recoverable = org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke("default-foundationRecoverable", Map.of()));
            assertThat(recoverable).isInstanceOf(ai.loomspan.api.RecoverableSkillException.class).hasMessage("correct input");
            var terminal = org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke("default-foundationTerminal", Map.of()));
            assertThat(terminal).isInstanceOf(SkillException.class).hasRootCauseInstanceOf(IllegalStateException.class);
            assertThat(context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("default").skills())
                    .extracting(ai.loomspan.api.SkillDescriptor::name).doesNotContain("default-foundationExecutor");
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke("default-foundationExecutor", Map.of())))
                    .isInstanceOf(SkillException.class);
            assertThat(context.getBean(JavaFoundation.class).executorCalls).hasValue(0);
            var invocation = new ai.loomspan.api.SkillExecutorInvocation(Map.of(), Map.of(), Map.of(), "sales-create", "generation");
            assertThat(invocation.skillName()).isEqualTo("sales-create");
        });
    }

    static class JavaFoundation
    {
        final AtomicInteger executorCalls = new AtomicInteger();
        @SkillMethod(name="foundationText", description="Exact text") public String text() { return "  exact text\n"; }
        @SkillMethod(name="foundationNull", description="Empty text") public void empty() {}
        @SkillMethod(name="foundationRecoverable", description="Designated failure") public String recoverable() {
            throw new ai.loomspan.api.RecoverableSkillException("correct input");
        }
        @SkillMethod(name="foundationTerminal", description="Terminal failure") public String terminal() { throw new IllegalStateException("terminal"); }
        @ai.loomspan.api.SkillExecutor(name="foundationExecutor") public String execute(ai.loomspan.api.SkillExecutorInvocation invocation) {
            executorCalls.incrementAndGet(); return invocation.skillName();
        }
    }

    @Test void completeNamespaceManagementUsesOnlySupportedTypes() {
        NamespaceStartupIntegrationTest.runner().run(context -> {
            var reloader=context.getBean(ai.loomspan.api.SkillReloader.class);
            var configuration=new ai.loomspan.api.ExecutionConfiguration("loomspan: {}");
            assertThat(reloader.validate("EMPTY", List.of(), configuration).valid()).isTrue();
            try(var candidate=reloader.prepare("EMPTY", List.of(), configuration,
                    ai.loomspan.api.NamespaceVisibility.PUBLIC, Map.of())) {
                reloader.publish(candidate);
                var catalog=reloader.snapshot("empty");
                assertThat(catalog.namespace()).isEqualTo("empty");
                assertThat(catalog.generationId()).isEqualTo(candidate.generationId());
                assertThat(catalog.skills()).isEmpty();
                reloader.remove("Empty", catalog.generationId());
            }
            try(var recreated=reloader.prepare("empty", List.of(), configuration)) {
                reloader.publish(recreated);
                assertThat(reloader.snapshot("empty").generationId()).isEqualTo(recreated.generationId());
            }
        });
        assertThat(java.util.Arrays.stream(ai.loomspan.api.SkillReloader.class.getMethods())
                .filter(method -> method.getName().equals("prepare") || method.getName().equals("validate")))
                .allMatch(method -> method.getParameterCount() >= 3 && method.getParameterTypes()[0] == String.class
                        && method.getParameterTypes()[2] == ai.loomspan.api.ExecutionConfiguration.class);
        assertThat(java.util.Arrays.stream(ai.loomspan.api.PreparedSkillUpdate.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("baseId", "baseGenerationId");
    }

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
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"capabilityName\\\":\\\"default-supportedSurfaceSkill\\\",\\\"createdAt\\\":\\\"2026-09-12T12:00:00Z\\\",\\\"status\\\":\\\"VALID\\\",\\\"tasks\\\":[{\\\"taskId\\\":\\\"java-task\\\",\\\"title\\\":\\\"Call Java leaf\\\",\\\"status\\\":\\\"PENDING\\\",\\\"capabilityName\\\":\\\"default-supportedJavaLeaf\\\",\\\"intent\\\":\\\"Echo through Java\\\",\\\"dependsOn\\\":[],\\\"expectedOutputs\\\":[\\\"Java echo\\\"],\\\"parallelGroup\\\":null,\\\"note\\\":\\\"\\\"},{\\\"taskId\\\":\\\"executor-task\\\",\\\"title\\\":\\\"Call EXECUTOR leaf\\\",\\\"status\\\":\\\"PENDING\\\",\\\"capabilityName\\\":\\\"default-supportedExecutorLeaf\\\",\\\"intent\\\":\\\"Echo through EXECUTOR\\\",\\\"dependsOn\\\":[\\\"java-task\\\"],\\\"expectedOutputs\\\":[\\\"EXECUTOR echo\\\"],\\\"parallelGroup\\\":null,\\\"note\\\":\\\"\\\"}]}"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":3,"completion_tokens":3,"total_tokens":6}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-java","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"stepAction\\\":\\\"CALL_TOOL\\\",\\\"taskId\\\":\\\"java-task\\\",\\\"toolName\\\":\\\"default-supportedJavaLeaf\\\",\\\"toolArguments\\\":{\\\"message\\\":\\\"hello through the public API\\\"}}"},
                                         "finish_reason":"stop"}],
                             "usage":{"prompt_tokens":4,"completion_tokens":3,"total_tokens":7}}
                            """));
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"id":"chatcmpl-supported-surface-executor","object":"chat.completion","created":1,
                             "model":"integration-model",
                             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\\"stepAction\\\":\\\"CALL_TOOL\\\",\\\"taskId\\\":\\\"executor-task\\\",\\\"toolName\\\":\\\"default-supportedExecutorLeaf\\\",\\\"toolArguments\\\":{\\\"message\\\":\\\"hello through the public API\\\"}}"},
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
                        assertThat(context).doesNotHaveBean(SkillCatalog.class);

                        SkillTemplate skills = context.getBean(SkillTemplate.class);
                        SkillInvocationHandoff handoff = context.getBean(SkillInvocationHandoff.class);
                        SkillCatalog catalog = context.getBean(ai.loomspan.api.SkillReloader.class).snapshot("DEFAULT");
                        assertThat(catalog.namespace()).isEqualTo("default");
                        assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                                .isSorted()
                                .contains("default-supportedSurfaceSkill", "default-supportedJavaLeaf", "default-supportedExecutorLeaf");
                        assertThat(catalog.skill("default-supportedJavaLeaf")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.JAVA);
                        assertThat(catalog.skill("default-supportedExecutorLeaf")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.JAVA);
                        assertThat(catalog.skill("default-supportedSurfaceSkill")).get()
                                .extracting(descriptor -> descriptor.kind()).isEqualTo(SkillKind.YAML);
                        assertThat(catalog.skill("default-missing")).isEmpty();
                        AtomicReference<SkillExecutionView> observed = new AtomicReference<>();
                        var authorized = UsernamePasswordAuthenticationToken.authenticated(
                                "supported-caller", "unused", AuthorityUtils.createAuthorityList("ROLE_EXECUTOR_USER"));
                        try {
                            SecurityContextHolder.getContext().setAuthentication(authorized);
                            skills.validate("default-supportedSurfaceSkill",
                                    Map.of("message", "hello through the public API"));
                            skills.validate("default-supportedJavaLeaf", new DirectRequest("precheck"));
                            skills.validate("default-supportedExecutorLeaf", new DirectRequest("precheck"));
                            var admitted = handoff.handoff("default-supportedExecutorLeaf", new DirectRequest("handed-off"));
                            assertThat(admitted.generationId()).isEqualTo(catalog.generationId());
                            SecurityContextHolder.clearContext();
                            assertThat(admitted.invoke()).isEqualTo("EXECUTOR: handed-off");
                            assertThat(SupportedSkillConfiguration.lastInvocation.get().executorConfigGenerationId())
                                    .isEqualTo(admitted.generationId());
                            assertThat(SupportedSkillConfiguration.lastInvocation.get().skillName())
                                    .isEqualTo("default-supportedExecutorLeaf");
                            admitted.release();
                            assertThat(SupportedSkillConfiguration.handlerAuthentication)
                                    .hasValue("supported-caller");
                            SecurityContextHolder.getContext().setAuthentication(authorized);
                            var admittedRoot = handoff.handoff(
                                    "default-supportedSurfaceSkill",
                                    Map.of("message", "hello through the public API"));
                            assertThat(admittedRoot.generationId()).isEqualTo(catalog.generationId());
                            assertThat(admittedRoot.invoke(observed::set))
                                    .isEqualTo("supported surface response");
                            assertThat(SupportedSkillConfiguration.lastInvocation.get().executorConfigGenerationId())
                                    .isEqualTo(admittedRoot.generationId());
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
                            assertThat(skills.invoke("default-supportedExecutorLeaf", mutableInput))
                                    .isEqualTo("EXECUTOR: direct");
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
                                    "default-supportedExecutorLeaf", Map.of(), ignored -> preSessionObserverCalls.incrementAndGet())))
                                    .isInstanceOf(SkillInputValidationException.class);
                            assertThat(preSessionObserverCalls).hasValue(0);

                            AtomicReference<SkillExecutionView> failedView = new AtomicReference<>();
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke(
                                    "default-supportedExecutorLeaf", Map.of("message", "fail"), failedView::set)))
                                    .isInstanceOf(SkillException.class)
                                    .hasMessage("Skill 'default-supportedExecutorLeaf' execution failed.");
                            assertThat(failedView.get()).isNotNull();
                            assertThat(failedView.get().events()).isNotEmpty();

                            SecurityContextHolder.getContext().setAuthentication(
                                    UsernamePasswordAuthenticationToken.authenticated(
                                            "denied-caller", "unused", AuthorityUtils.NO_AUTHORITIES));
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                                    () -> skills.validate("default-supportedExecutorLeaf", Map.of("message", "denied"))))
                                    .isInstanceOf(AccessDeniedException.class);
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                                    () -> skills.validate("default-supportedJavaLeaf", Map.of("message", "denied"))))
                                    .isInstanceOf(AccessDeniedException.class);
                            AtomicReference<SkillExecutionView> rejectedView = new AtomicReference<>();
                            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> skills.invoke(
                                    "default-supportedExecutorLeaf", Map.of("message", "denied"), rejectedView::set)))
                                    .isInstanceOf(AccessDeniedException.class)
                                    .hasMessage("Access denied for capability 'default-supportedExecutorLeaf'");
                            assertThat(rejectedView.get()).isNull();
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
                    .contains("default-supportedJavaLeaf", "default-supportedExecutorLeaf");

            RecordedRequest javaRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(javaRequest).isNotNull();
            String javaBody = javaRequest.getBody().readUtf8();
            assertThat(javaBody)
                    .contains("java-task", "default-supportedJavaLeaf")
                    .doesNotContain("EXECUTOR: hello through the public API");
            JsonNode javaPayload = new ObjectMapper().readTree(javaBody);
            assertThat(requestMessages(javaPayload))
                    .containsOnlyOnce("--- TOOL ARGUMENT SHAPE ---")
                    .contains("\"message\": \"<string>\"")
                    .doesNotContain("\"values\":");

            RecordedRequest executorRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(executorRequest).isNotNull();
            String executorBody = executorRequest.getBody().readUtf8();
            assertThat(executorBody)
                    .contains("executor-task", "default-supportedExecutorLeaf", "Java: hello through the public API");
            JsonNode executorPayload = new ObjectMapper().readTree(executorBody);
            assertThat(requestMessages(executorPayload))
                    .containsOnlyOnce("--- TOOL ARGUMENT SHAPE ---")
                    .contains("\"message\": \"<string>\"", "\"values\": [ \"<string>\" ]");

            RecordedRequest finalRequest = server.takeRequest(2, TimeUnit.SECONDS);
            assertThat(finalRequest).isNotNull();
            assertThat(finalRequest.getBody().readUtf8())
                    .contains("Java: hello through the public API")
                    .contains("EXECUTOR: hello through the public API");
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
        private static final AtomicReference<SkillExecutorInvocation> lastInvocation = new AtomicReference<>();
        private static final AtomicInteger handlerCalls = new AtomicInteger();

        @Bean
        SupportedTarget supportedTargetBean()
        {
            return new SupportedTarget();
        }

        @Bean
        FixtureExecutor executorSkillHandler()
        {
            return new FixtureExecutor(invocation -> {
                handlerCalls.incrementAndGet();
                handlerAuthentication.set(SecurityContextHolder.getContext().getAuthentication().getName());
                lastInvocation.set(invocation);
                if ("fail".equals(invocation.input().get("message")))
                {
                    throw new IllegalStateException("application EXECUTOR failure");
                }
                return "EXECUTOR: " + invocation.input().get("message");
            });
        }
    }

    static class SupportedTarget
    {
        @SkillMethod(name = "supportedJavaLeaf", description = "Echo a message through an application-owned Java leaf.")
        @RolesAllowed("EXECUTOR_USER")
        String echo(@SkillParam(description = "Message supplied by the parent skill.") String message)
        {
            return "Java: " + message;
        }
    }

    private record DirectRequest(String message)
    {
    }
}
