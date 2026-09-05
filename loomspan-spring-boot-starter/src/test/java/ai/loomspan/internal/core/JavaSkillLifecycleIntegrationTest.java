package ai.loomspan.internal.core;

import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.security.*;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JavaSkillLifecycleIntegrationTest {
    @Test void rootAndNestedJavaShareFramesAndRestoreParentWithoutModelRequests() {
        var registry = new InMemoryCapabilityRegistry();
        var coordinator = new AtomicReference<ExecutionCoordinator>();
        var session = TestLoomspanSessions.withId("java-nested", "javaRoot", 5, null, TracePersistencePolicy.ALWAYS);
        registry.register("javaChild", javaSkill("javaChild", args -> {
            assertThat(ExecutionBindingScope.requireCurrent().requireMission().skillName()).isEqualTo("javaChild");
            return "child result";
        }));
        registry.register("javaRoot", javaSkill("javaRoot", args -> {
            var parent = ExecutionBindingScope.requireCurrent().requireMission();
            assertThat(coordinator.get().execute("javaChild", "child", Map.of(), session, null)).isEqualTo("child result");
            assertThat(ExecutionBindingScope.requireCurrent().requireMission()).isSameAs(parent);
            return "root result";
        }));
        coordinator.set(coordinator(registry));
        assertThat(coordinator.get().execute("javaRoot", "root", Map.of("input", "safe"), session, null)).isEqualTo("root result");
        var records = records(session);
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.FRAME_OPENED))
                .extracting(TraceRecord::route).containsExactly("javaRoot", "javaChild");
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.FRAME_CLOSED))
                .extracting(TraceRecord::route).containsExactly("javaChild", "javaRoot");
        assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.TRACE_COMPLETED)).hasSize(1);
        assertThat(records.getLast().metadata()).containsEntry("remainingFrames", 0);
        assertThat(session.getExecutionTrace().completed()).isTrue();
        assertThat(ExecutionBindingScope.current()).isEmpty();
    }
    @Test void failureAndCancellationCloseRootOnceWithNoEvidence() {
        for (RuntimeException failure : List.of(new AccessDeniedException("denied"), new CancellationException("cancelled"))) {
            var registry = new InMemoryCapabilityRegistry();
            registry.register("javaRoot", javaSkill("javaRoot", args -> { throw failure; }));
            var session = TestLoomspanSessions.withId("java-failure-" + failure.getClass().getSimpleName(), "javaRoot", 3);
            assertThatThrownBy(() -> coordinator(registry).execute("javaRoot", "root", session, null)).isSameAs(failure);
            var records = records(session);
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.FRAME_CLOSED)).hasSize(1);
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.TRACE_COMPLETED)).hasSize(1);
            assertThat(records.stream().filter(r -> r.recordType() == TraceRecordType.EVIDENCE_RECORDED)).isEmpty();
            assertThat(records.getLast().metadata()).containsEntry("remainingFrames", 0);
            assertThat(ExecutionBindingScope.current()).isEmpty();
        }
    }
    private static ExecutionCoordinator coordinator(CapabilityRegistry registry) {
        MissionExecutionEngine engine = (s,d,o,i,m,t,p,a) -> { throw new AssertionError("Java dispatched a model engine"); };
        return new ExecutionCoordinator(
                mock(YamlSkillCatalog.class),
                registry,
                (d,m) -> { throw new AssertionError("Java created a model interaction"); },
                (n,s,a) -> List.of(),
                (s,d,c,a) -> List.of(),
                engine,
                engine,
                new DefaultExecutionStateService(Clock.systemUTC()),
                new DefaultAccessGuard(),
                (v,s) -> v,
                new ScopedAuthentication(null),
                new ai.loomspan.internal.runtime.MissionWorkExecutor(new DefaultExecutionStateService(Clock.systemUTC()), java.time.Duration.ofSeconds(5), java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor(), new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
    }
    private static CapabilityMetadata javaSkill(String name, CapabilityInvoker invoker) {
        return new CapabilityMetadata(name, name, "Java test", SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(),
                invoker, CapabilityKind.JAVA_SKILL, CapabilityToolDescriptor.generic(name, "Java test"),
                new SkillSource(null, "testBean", name + "()"));
    }
    private static List<TraceRecord> records(LoomspanSession session) {
        List<TraceRecord> records = new ArrayList<>(); session.readTraceRecords(records::add); return records;
    }
}
