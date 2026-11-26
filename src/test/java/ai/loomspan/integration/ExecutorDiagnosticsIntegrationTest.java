package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ExecutorDiagnosticsIntegrationTest
{
    @TempDir Path directory;

    private ApplicationContextRunner runner(Decisions bean) throws Exception
    {
        Files.writeString(directory.resolve("leaf.yaml"), "name: leaf\ndescription: Decision\nexecutor: decide\n");
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class, LoomspanJacksonAutoConfiguration.class,
                LoomspanAutoConfiguration.class, LoomspanAiAutoConfiguration.class))
                .withBean(Decisions.class, () -> bean)
                .withPropertyValues("loomspan.skills.locations=" + directory.toUri() + "*.yaml", "loomspan.execution-trace.persistence=always");
    }

    @Test void publicInvocationRecordsSupportedDiagnosticAndKeepsResult() throws Exception
    {
        var bean = new Decisions();
        bean.action = call -> call.diagnostics().info("request ready", Map.of("token", "kept verbatim"));
        runner(bean).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            var records = bean.records();
            var diagnostic = records.stream().filter(r -> r.recordType().name().equals("EXECUTOR_DIAGNOSTIC_RECORDED")).toList();
            assertThat(diagnostic).hasSize(1);
            assertThat(diagnostic.getFirst().data().path("message").asText()).isEqualTo("request ready");
            assertThat(diagnostic.getFirst().data().path("severity").asText()).isEqualTo("INFO");
            assertThat(diagnostic.getFirst().data().path("data").path("token").asText()).isEqualTo("kept verbatim");
            assertThat(records).noneMatch(r -> r.recordType() == TraceRecordType.ERROR_RECORDED);
        });
    }

    @Test void snapshotsIsolationConcurrentOrderAndLateWritesUseOneExistingFrame() throws Exception
    {
        var bean = new Decisions();
        var nested = new ArrayList<Object>(List.of("original"));
        bean.action = call -> {
            call.diagnostics().info("snapshot", Map.of("token", nested)); nested.clear();
            try (var pool = Executors.newFixedThreadPool(4))
            {
                var futures = new ArrayList<Future<?>>();
                for (int id = 0; id < 16; id++)
                {
                    int value = id;
                    futures.add(pool.submit(() -> call.diagnostics().warn("parallel", Map.of("id", value))));
                }
                for (var future : futures) future.get(5, TimeUnit.SECONDS);
            }
            catch (Exception ex) { throw new IllegalStateException(ex); }
            call.diagnostics().error("severity does not fail");
            call.diagnostics().info("m".repeat(9000), new Object());
        };
        runner(bean).run(context -> {
            var skills = context.getBean(SkillTemplate.class);
            assertThat(skills.invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            var first = bean.session;
            var records = bean.records();
            var authored = records.stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED).toList();
            assertThat(authored).hasSize(19);
            assertThat(authored.getFirst().data().at("/data/token/0").asText()).isEqualTo("original");
            assertThat(authored.stream().map(TraceRecord::frameId).distinct()).hasSize(1);
            assertThat(authored.stream().map(TraceRecord::sequence)).isSorted().doesNotHaveDuplicates();
            assertThat(authored.stream().filter(r -> r.data().has("data") && r.data().get("data").has("id"))
                    .map(r -> r.data().at("/data/id").asInt())).containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0,16).boxed().toList());
            assertThat(authored.getLast().data().path("messageState").asText()).isEqualTo("TRUNCATED");
            assertThat(authored.getLast().data().path("dataState").asText()).isEqualTo("UNAVAILABLE");
            assertThat(records).noneMatch(r -> r.recordType() == TraceRecordType.ERROR_RECORDED);
            var frame = records.stream().filter(r -> r.recordType() == TraceRecordType.FRAME_OPENED).findFirst().orElseThrow();
            assertThat(frame.data().path("executorName").asText()).isEqualTo("default:decide");
            assertThat(frame.data().path("executorConfigGenerationId").asText()).isEqualTo(bean.call.executorConfigGenerationId());
            assertThat(records).anySatisfy(r -> {
                assertThat(r.recordType()).isEqualTo(TraceRecordType.FRAME_METADATA_RECORDED);
                assertThat(r.data().path("executorOutput").asText()).isEqualTo("approved");
            });
            bean.call.diagnostics().info("after close", new Object());
            assertThat(bean.records()).hasSize(records.size());
            bean.action = call -> call.diagnostics().info("second invocation");
            assertThat(skills.invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            assertThat(bean.session).isNotSameAs(first);
            assertThat(bean.records().stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED)).hasSize(1);
        });
    }

    @ParameterizedTest @ValueSource(ints = {0,1,2,3,4})
    void ordinaryDiagnosticFailuresKeepSuccessAndOriginalExecutorFailure(int seam) throws Exception
    {
        var bean = new Decisions();
        bean.action = call -> { ExecutorDiagnosticTestSupport.fail(call, seam); call.diagnostics().error("failure seam", new Object()); };
        runner(bean).run(context -> {
            var skills = context.getBean(SkillTemplate.class);
            assertThat(skills.invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            assertThat(bean.records()).noneMatch(r -> r.recordType() == TraceRecordType.ERROR_RECORDED);
            var sentinel = new RecoverableSkillException("original feedback", new IllegalStateException("original cause"));
            bean.action = call -> {
                ExecutorDiagnosticTestSupport.fail(call, seam);
                call.diagnostics().error("failure seam", new Object());
                throw sentinel;
            };
            assertThatThrownBy(() -> skills.invoke("default", "leaf", Map.of()))
                    .isInstanceOf(RecoverableSkillException.class).hasMessage(sentinel.getMessage()).hasCause(sentinel)
                    .satisfies(failure -> assertThat(((ai.loomspan.api.SkillException) failure).getSessionId()).isNotBlank());
            var failed = bean.records();
            assertThat(failed).noneMatch(r -> r.recordType() == TraceRecordType.FRAME_METADATA_RECORDED && r.data().has("executorOutput"));
            int count = failed.size(); bean.call.diagnostics().info("late after failure"); assertThat(bean.records()).hasSize(count);
            var terminal = new IllegalArgumentException("original terminal failure");
            bean.action = call -> {
                ExecutorDiagnosticTestSupport.fail(call, seam);
                call.diagnostics().warn("failure seam", new Object());
                throw terminal;
            };
            assertThatThrownBy(() -> skills.invoke("default", "leaf", Map.of())).hasRootCause(terminal);
        });
    }

    @Test void invocationBudgetExhaustionDoesNotLeakToTheNextInvocation() throws Exception
    {
        var bean = new Decisions();
        bean.action = call -> { for (int i = 0; i < 200; i++) call.diagnostics().info("event " + i); };
        runner(bean).run(context -> {
            var skills = context.getBean(SkillTemplate.class);
            assertThat(skills.invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            var records = bean.records();
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED)).hasSize(128);
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED))
                    .singleElement().satisfies(r -> assertThat(r.data().path("reason").asText()).isEqualTo("INVOCATION_EVENT_LIMIT"));
            bean.action = call -> call.diagnostics().info("fresh budget");
            assertThat(skills.invoke("default", "leaf", Map.of()).text()).isEqualTo("approved");
            assertThat(bean.records().stream().filter(r -> r.recordType() == TraceRecordType.EXECUTOR_DIAGNOSTIC_RECORDED)).hasSize(1);
        });
    }

    @Test void callerExpressionFailureRemainsExecutionWork() throws Exception
    {
        var bean = new Decisions();
        var original = new IllegalStateException("caller expression");
        bean.action = call -> call.diagnostics().info("not entered", expression(original));
        runner(bean).run(context -> assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("default", "leaf", Map.of()))
                .hasRootCause(original));
    }

    private static Object expression(RuntimeException failure) { throw failure; }

    public static class Decisions
    {
        Consumer<SkillExecutorInvocation> action = call -> {};
        volatile SkillExecutorInvocation call;
        volatile LoomspanSession session;
        @SkillExecutor(name = "decide") public String decide(SkillExecutorInvocation invocation)
        {
            call = invocation;
            session = ExecutionBindingScope.requireCurrent().session();
            action.accept(invocation);
            return "approved";
        }
        List<TraceRecord> records() { return ExecutorDiagnosticTestSupport.logicalRecords(session); }
    }
}
