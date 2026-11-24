package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.SkillIdentity;
import ai.loomspan.internal.skill.SkillGenerationManager;
import com.schibsted.spt.data.jslt.Expression;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutorInvocationLifecycleIntegrationTest
{
    @TempDir Path directory;

    private ApplicationContextRunner runner(Decisions executor)
    {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withBean(Decisions.class, () -> executor)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                        "loomspan.session.mission-timeout=5s", "loomspan.execution-trace.persistence=always", "loomspan.shutdown.timeout=400ms");
    }

    private void leaf() throws Exception
    {
        Files.writeString(directory.resolve("leaf.yaml"),
                "name: leaf\ndescription: Application decision\nexecutor: decide\nconfig: {question: approve}\n");
    }

    @Test void directInvocationKeepsPreciseBusinessValuesAndLiveBudget() throws Exception
    {
        leaf();
        var bean = new Decisions();
        runner(bean).run(context -> {
            assertThat(context).hasNotFailed();
            var value = new java.math.BigDecimal("0.12345678901234567890123456789");
            assertThat(context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of("probability", value)))
                    .isEqualTo("approved");
            assertThat(bean.call.input()).containsEntry("probability", value);
            assertThat(bean.call.skillConfig()).containsEntry("question", "approve");
            assertThat(bean.call.executorConfigGenerationId()).isEqualTo(context.getBean(SkillReloader.class).snapshot("default").generationId());
            assertThat(bean.entryRemaining).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(5));
            assertThat(bean.call.isCancelled()).isFalse();
            assertThat(bean.call.diagnostics()).isNotNull();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void childObservesOriginalParentBudgetAfterPlanningDelay(boolean explicit) throws Exception
    {
        leaf();
        Files.writeString(directory.resolve("parent.yaml"), ExplicitJavaFailureHandlingIntegrationTest.parent("parent", "default:leaf", explicit));
        try (var provider = new MockWebServer())
        {
            if (explicit)
            {
                var json = new tools.jackson.databind.ObjectMapper();
                provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.text(json.writeValueAsString(Map.of(
                        "capabilityName", "default:parent", "createdAt", "2026-11-01T00:00:00Z", "status", "VALID",
                        "tasks", List.of(ExplicitJavaFailureHandlingIntegrationTest.task("default:leaf")))))
                        .setBodyDelay(700, TimeUnit.MILLISECONDS));
                provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.text(json.writeValueAsString(Map.of(
                        "stepAction", "CALL_TOOL", "taskId", "child", "toolName", "default:leaf", "toolArguments", Map.of()))));
            }
            else
            {
                provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.tool("default:leaf").setBodyDelay(700, TimeUnit.MILLISECONDS));
                provider.enqueue(ExplicitJavaFailureHandlingIntegrationTest.text("approved"));
            }
            provider.start();
            var bean = new Decisions();
            runner(bean).withPropertyValues("loomspan.connections.local.driver=openai",
                    "loomspan.connections.local.base-url=" + provider.url("/v1"), "loomspan.connections.local.api-key=fixture",
                    "loomspan.connections.local.provider-retry.enabled=false", "loomspan.models.local.connection=local",
                    "loomspan.models.local.provider-model=fixture").run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(SkillTemplate.class).invoke("default", "parent", Map.of())).isEqualTo("approved");
                // Child gets its own five-second budget; the already-spent parent budget must win.
                assertThat(bean.entryRemaining).isPositive().isLessThan(Duration.ofMillis(4500));
                assertThat(bean.call.skillName()).isEqualTo("default:leaf");
                var records = ai.loomspan.internal.core.ExecutorDiagnosticTestSupport.logicalRecords(bean.session);
                var diagnostics = records.stream().filter(r -> r.recordType() == ai.loomspan.internal.core.TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED).toList();
                assertThat(diagnostics).hasSize(1);
                assertThat(diagnostics.getFirst().route()).isEqualTo("default:leaf");
                assertThat(records).anySatisfy(r -> {
                    assertThat(r.recordType()).isEqualTo(ai.loomspan.internal.core.TraceRecordType.FRAME_OPENED);
                    assertThat(r.frameId()).isEqualTo(diagnostics.getFirst().frameId());
                    assertThat(r.route()).isEqualTo("default:leaf");
                });
                assertThat(records.stream().filter(r -> r.recordType() == ai.loomspan.internal.core.TraceRecordType.TOOL_CALL_STARTED)).hasSize(1);
                assertThat(records.stream().filter(r -> r.recordType() == ai.loomspan.internal.core.TraceRecordType.TOOL_CALL_COMPLETED)).hasSize(1);
            });
        }
    }

    @Test void capturedInvocationObservesCallerCancellationAcrossThreadsWithoutClearingInterrupt() throws Exception
    {
        leaf();
        addTransform();
        var bean = new Decisions(); bean.block = true;
        runner(bean).run(context -> {
            assertThat(context).hasNotFailed();
            var failure = new AtomicReference<Throwable>();
            var callerInterrupted = new AtomicBoolean();
            Thread caller = Thread.ofPlatform().unstarted(() -> {
                try { context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of()); }
                catch (Throwable thrown) { failure.set(thrown); callerInterrupted.set(Thread.currentThread().isInterrupted()); }
            });
            try
            {
                caller.start(); assertThat(bean.entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(bean.call.input()).isEqualTo(Map.of("transformed", true));
                assertThat(bean.call.isCancelled()).isFalse();
                Duration first = bean.call.remainingTime().orElseThrow();
                caller.interrupt();
                assertThat(bean.stopped.await(5, TimeUnit.SECONDS)).isTrue();
                caller.join(5000);
                assertThat(bean.call.isCancelled()).isTrue();
                assertLateDiagnosticsIgnored(bean);
                assertThat(bean.call.remainingTime().orElseThrow()).isLessThanOrEqualTo(first);
                assertThat(bean.interrupted).isTrue();
                assertThat(bean.operations).hasValue(1);
                assertThat(callerInterrupted).isTrue();
                assertThat(failure.get()).isInstanceOf(SkillException.class);
            }
            catch (InterruptedException ex) { throw new AssertionError(ex); }
            finally { bean.release.countDown(); }
        });
    }

    private void addTransform() throws Exception
    {
        Files.writeString(directory.resolve("leaf.yaml"), """
                input_transform:
                  language: jslt
                  expression: '{"transformed": true}'
                """, StandardOpenOption.APPEND);
    }

    @Test void cancellationDuringEvaluationPreventsExecutorEntry() throws Exception
    {
        leaf();
        addTransform();
        var bean = new Decisions();
        var evaluationEntered = new CountDownLatch(1);
        var evaluationInterrupted = new CountDownLatch(1);
        var releaseEvaluation = new CountDownLatch(1);
        var invocationFailure = new AtomicReference<Throwable>();
        try (var worker = Executors.newSingleThreadExecutor())
        {
            runner(bean).withInitializer(application -> application.addBeanFactoryPostProcessor(factory ->
                    factory.registerSingleton("LoomspanMissionExecutor", worker))).run(context -> {
                assertThat(context).hasNotFailed();
                var manager = context.getBean(SkillGenerationManager.class);
                var capture = manager.capture(SkillIdentity.parse("default:leaf"));
                try (var lease = capture.lease())
                {
                    var definition = capture.snapshot().generation("default").definition("default:leaf");
                    Object manifest = ReflectionTestUtils.getField(definition, "manifest");
                    Object transform = ReflectionTestUtils.getField(manifest, "compiledInputTransform");
                    var prepared = (Expression) ReflectionTestUtils.getField(transform, "expression");
                    var delayed = mock(Expression.class);
                    when(delayed.apply(any(JsonNode.class))).thenAnswer(call -> {
                        evaluationEntered.countDown();
                        boolean released = false;
                        while (!released)
                        {
                            try { released = releaseEvaluation.await(5, TimeUnit.SECONDS); }
                            catch (InterruptedException ignored) { evaluationInterrupted.countDown(); }
                        }
                        return prepared.apply(call.getArgument(0));
                    });
                    ReflectionTestUtils.setField(transform, "expression", delayed);
                }
                var template = context.getBean(SkillTemplate.class);
                Thread caller = Thread.ofPlatform().start(() -> {
                    try { template.invoke("default", "leaf", Map.of()); }
                    catch (Throwable thrown) { invocationFailure.set(thrown); }
                });
                try
                {
                    assertThat(evaluationEntered.await(5, TimeUnit.SECONDS)).isTrue();
                    caller.interrupt();
                    assertThat(evaluationInterrupted.await(5, TimeUnit.SECONDS)).isTrue();
                    releaseEvaluation.countDown();
                    caller.join(5000);
                    assertThat(caller.isAlive()).isFalse();
                    worker.submit(() -> {}).get(5, TimeUnit.SECONDS);
                    assertThat(invocationFailure.get()).isInstanceOf(SkillException.class)
                            .hasStackTraceContaining("LoomspanMissionTimeoutException");
                    assertThat(bean.operations).hasValue(0);
                }
                finally
                {
                    releaseEvaluation.countDown();
                    if (caller.isAlive()) caller.interrupt();
                }
            });
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void latePhysicalWorkObservesExpiryAndFrameworkShutdown(boolean shutdown) throws Exception
    {
        leaf();
        var bean = new Decisions(); bean.block = true; bean.ignoreCancellation = true;
        runner(bean).withPropertyValues("loomspan.session.mission-timeout=1s").run(context -> {
            assertThat(context).hasNotFailed();
            var failure = new AtomicReference<Throwable>();
            Thread caller = Thread.ofPlatform().unstarted(() -> {
                try { context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of()); }
                catch (Throwable thrown) { failure.set(thrown); }
            });
            try
            {
                caller.start(); assertThat(bean.entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(bean.call.remainingTime().orElseThrow()).isPositive();
                assertThat(bean.call.isCancelled()).isFalse();
                if (shutdown) context.getBean(ai.loomspan.internal.core.FrameworkExecutionLifecycle.class).stop();
                caller.join(5000);
                assertThat(caller.isAlive()).isFalse();
                assertThat(failure.get()).isInstanceOf(SkillException.class);
                assertThat(bean.call.remainingTime()).contains(Duration.ZERO);
                assertThat(bean.call.isCancelled()).isTrue();
                assertLateDiagnosticsIgnored(bean);
                // Logical completion has fenced the result while physical application work remains.
                assertThat(bean.stopped.getCount()).isEqualTo(1);
            }
            catch (InterruptedException ex) { throw new AssertionError(ex); }
            finally
            {
                bean.release.countDown();
                try { assertThat(bean.stopped.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException ex) { throw new AssertionError(ex); }
            }
        });
    }

    private static void assertLateDiagnosticsIgnored(Decisions bean)
    {
        var before = ai.loomspan.internal.core.ExecutorDiagnosticTestSupport.logicalRecords(bean.session);
        bean.call.diagnostics().warn("after cutoff", new Object());
        var after = ai.loomspan.internal.core.ExecutorDiagnosticTestSupport.logicalRecords(bean.session);
        assertThat(after.stream().filter(r -> r.recordType() == ai.loomspan.internal.core.TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED).count())
                .isEqualTo(before.stream().filter(r -> r.recordType() == ai.loomspan.internal.core.TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED).count());
    }

    public static class Decisions
    {
        volatile SkillExecutorInvocation call;
        volatile ai.loomspan.internal.core.LoomspanSession session;
        volatile Duration entryRemaining;
        volatile boolean block;
        volatile boolean ignoreCancellation;
        volatile boolean interrupted;
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), stopped = new CountDownLatch(1);
        final AtomicInteger operations = new AtomicInteger();
        @SkillExecutor(name = "decide") public String decide(SkillExecutorInvocation invocation)
        {
            call = invocation;
            session = ai.loomspan.internal.core.ExecutionBindingScope.requireCurrent().session();
            invocation.diagnostics().info("executor entered");
            entryRemaining = invocation.remainingTime().orElseThrow();
            operations.incrementAndGet(); entered.countDown();
            if (block)
            {
                try
                {
                    while (ignoreCancellation || (!invocation.isCancelled() && !Thread.currentThread().isInterrupted()
                            && !invocation.remainingTime().orElseThrow().isZero()))
                    {
                        try { if (release.await(10, TimeUnit.MILLISECONDS)) break; }
                        catch (InterruptedException ex) { if (!ignoreCancellation) Thread.currentThread().interrupt(); }
                    }
                    interrupted = Thread.currentThread().isInterrupted();
                }
                finally { stopped.countDown(); }
            }
            return "approved";
        }
    }
}
