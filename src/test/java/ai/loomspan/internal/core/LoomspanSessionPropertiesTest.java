package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.ExecutionTraceProperties;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillReloader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

class LoomspanSessionPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                    LoomspanAutoConfiguration.class,
                    ai.loomspan.autoconfigure.LoomspanAiAutoConfiguration.class))
            .withPropertyValues("loomspan.skills.locations=classpath:/skills/none/**/*.yaml");

    @Test
    void bindsDefaultAndOverriddenSessionProperties() {
        contextRunner.run(context -> {
            LoomspanProperties.Session properties = context.getBean(LoomspanProperties.class).getSession();
            assertThat(properties.getMaxDepth()).isEqualTo(32);
            assertThat(properties.getMissionTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(context.getBean(LoomspanProperties.class).getShutdown().getTimeout())
                    .isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getQuotas().getMaxSkillInvocations()).isEqualTo(64);
            assertThat(properties.getQuotas().getMaxToolInvocations()).isEqualTo(128);
            assertThat(properties.getQuotas().getMaxLinterRetries()).isEqualTo(32);
            assertThat(properties.getQuotas().getMaxModelCalls()).isEqualTo(64);
            assertThat(properties.getQuotas().getMaxUsageUnits()).isEqualTo(200_000);
            assertThat(context.getBean(ExecutionTraceProperties.class).getPersistence()).isEqualTo(TracePersistencePolicy.ONERROR);
            var manager = context.getBean(ai.loomspan.internal.skill.SkillGenerationManager.class);
            context.getBean(LoomspanProperties.class).getSession().setMaxDepth(99);
            assertThat(manager.active().runtime().properties().getSession().getMaxDepth()).isEqualTo(32);
            var skillOnly = manager.prepare(java.util.List.of());
            assertThat(skillOnly.runtime().properties().getSession().getMaxDepth()).isEqualTo(32);
            skillOnly.close();
        });

        contextRunner
                .withPropertyValues(
                        "loomspan.session.max-depth=3",
                        "loomspan.session.mission-timeout=5s",
                        "loomspan.shutdown.timeout=7s",
                        "loomspan.session.quotas.max-skill-invocations=4",
                        "loomspan.session.quotas.max-tool-invocations=9",
                        "loomspan.session.quotas.max-linter-retries=7",
                        "loomspan.session.quotas.max-model-calls=5",
                        "loomspan.session.quotas.max-usage-units=1234",
                        "loomspan.execution-trace.persistence=always")
                .run(context -> {
                    LoomspanProperties.Session properties = context.getBean(LoomspanProperties.class).getSession();
                    ExecutionTraceProperties executionTraceProperties = context.getBean(ExecutionTraceProperties.class);
                    LoomspanSessionRunner runner = context.getBean(LoomspanSessionRunner.class);

                    assertThat(properties.getMaxDepth()).isEqualTo(3);
                    assertThat(properties.getMissionTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(context.getBean(LoomspanProperties.class).getShutdown().getTimeout())
                            .isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.getQuotas().getMaxSkillInvocations()).isEqualTo(4);
                    assertThat(properties.getQuotas().getMaxToolInvocations()).isEqualTo(9);
                    assertThat(properties.getQuotas().getMaxLinterRetries()).isEqualTo(7);
                    assertThat(properties.getQuotas().getMaxModelCalls()).isEqualTo(5);
                    assertThat(properties.getQuotas().getMaxUsageUnits()).isEqualTo(1234);
                    assertThat(executionTraceProperties.getPersistence()).isEqualTo(TracePersistencePolicy.ALWAYS);
                    assertThat(runner.callWithNewSession("test.entry", ai.loomspan.testkit.TestSkillGenerations.empty(), LoomspanSession::getMaxDepth)).isEqualTo(3);
                });
    }

    @Test
    void rejectsInvalidMaxDepthValues() {
        contextRunner
                .withPropertyValues("loomspan.session.max-depth=0")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasRootCauseInstanceOf(BindValidationException.class);
                });

        contextRunner
                .withPropertyValues("loomspan.session.mission-timeout=0s")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasRootCauseInstanceOf(IllegalArgumentException.class);
                    assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
                            .hasMessageContaining("missionTimeout must be greater than zero");
                });

        contextRunner.withPropertyValues("loomspan.shutdown.timeout=0s").run(context -> {
            assertThat(context.getStartupFailure()).isNotNull().hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()))
                    .hasMessageContaining("loomspan.shutdown.timeout must be greater than zero");
        });

        contextRunner.withPropertyValues("loomspan.shutdown.timeout=-1s").run(context ->
                assertThat(context.getStartupFailure()).isNotNull()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));

        contextRunner
                .withPropertyValues("loomspan.session.quotas.max-model-calls=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LoomspanProperties.class).getSession().getQuotas().getMaxModelCalls()).isZero();
                });

        contextRunner
                .withPropertyValues("loomspan.session.quotas.max-model-calls=-1")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasRootCauseInstanceOf(BindValidationException.class);
                });
    }

    @Test
    void oldTopLevelTraceKeyDoesNotBind()
    {
        contextRunner.withPropertyValues("execution-trace.persistence=always").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ExecutionTraceProperties.class).getPersistence())
                    .isEqualTo(TracePersistencePolicy.ONERROR);
        });
    }

    @Test
    void capturedGenerationKeepsTracePolicyAndDepthAcrossPublication()
    {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SkillReloader reloader = context.getBean(SkillReloader.class);
            var manager = context.getBean(ai.loomspan.internal.skill.SkillGenerationManager.class);
            LoomspanSessionRunner runner = context.getBean(LoomspanSessionRunner.class);
            var a = reloader.prepare(java.util.List.of(), new ExecutionConfiguration("""
                    loomspan:
                      session:
                        max-depth: 2
                        quotas:
                          max-skill-invocations: 1
                      execution-trace:
                        persistence: never
                    """));
            reloader.publish(a);
            var captured = manager.capture();
            try
            {
                var b = reloader.prepare(java.util.List.of(), new ExecutionConfiguration("""
                        loomspan:
                          session:
                            max-depth: 5
                            quotas:
                              max-skill-invocations: 2
                          execution-trace:
                            persistence: always
                        """));
                reloader.publish(b);
                TracePersistencePolicy oldPolicy = runner.callWithNewSession("test.entry", captured.generation(),
                        session -> session.getExecutionTrace().persistencePolicy());
                assertThat(oldPolicy).isEqualTo(TracePersistencePolicy.NEVER);
                assertThat(runner.callWithNewSession("test.entry", captured.generation(), LoomspanSession::getMaxDepth))
                        .isEqualTo(2);
                TracePersistencePolicy newPolicy = runner.callWithNewSession("test.entry", manager.active(),
                        session -> session.getExecutionTrace().persistencePolicy());
                assertThat(newPolicy).isEqualTo(TracePersistencePolicy.ALWAYS);
                assertThat(runner.callWithNewSession("test.entry", manager.active(), LoomspanSession::getMaxDepth))
                        .isEqualTo(5);
                var usage = context.getBean(ai.loomspan.internal.runtime.usage.SessionUsageService.class);
                runner.callWithNewSession("test.entry", captured.generation(), session -> {
                    usage.recordMissionStart(session, "test.entry");
                    assertThatThrownBy(() -> usage.recordMissionStart(session, "test.entry"))
                            .isInstanceOf(ai.loomspan.internal.runtime.LoomspanQuotaExceededException.class);
                    return null;
                });
                runner.callWithNewSession("test.entry", manager.active(), session -> {
                    usage.recordMissionStart(session, "test.entry");
                    usage.recordMissionStart(session, "test.entry");
                    return null;
                });
            }
            finally { captured.lease().close(); }
        });
    }
}
