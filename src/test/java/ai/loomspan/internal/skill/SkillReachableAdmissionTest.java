package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.security.SkillRoleEvaluator;
import ai.loomspan.internal.skillapi.DefaultSkillTemplate;
import ai.loomspan.internal.skillapi.DefaultSkillInvocationHandoff;
import ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.trace.ImmediateCompletionRetention;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.autoconfigure.LoomspanProperties;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.*;
import static ai.loomspan.internal.skill.SkillReloaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillReachableAdmissionTest {
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    @Test void validationRejectsRemovedOptionalReachableDependencyButIgnoresUnreachableBrokenSkill() {
        var f = new Fixture(null);
        var dep = f.reloader.prepare("dep", List.of(model("leaf")), MODEL, NamespaceVisibility.PUBLIC);
        f.reloader.publish(dep);
        f.reloader.publish(f.reloader.prepare("caller", List.of(calls("root", "dep-leaf"), model("safe")), MODEL));
        var router = mock(CapabilityExecutionRouter.class);
        var template = new DefaultSkillTemplate(f.manager, router, new LoomspanSessionRunner(4),
                new tools.jackson.databind.ObjectMapper(), new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
        f.reloader.remove("dep", dep.generationId());
        assertThatThrownBy(() -> template.validate("caller-root", Map.of())).isInstanceOf(SkillException.class);
        template.validate("caller-safe", Map.of());
        verifyNoInteractions(router);
        f.manager.destroy();
    }

    @Test void allRootOverloadsUseFreshQualifiedTargetsAcrossReplacementRemovalAndRecreation() {
        for (String namespace : List.of("default", "caller")) {
            var f = new Fixture(null);
            var dep = publish(f, "dep", List.of(model("leaf")), NamespaceVisibility.PUBLIC);
            publish(f, namespace, List.of(calls("root", "dep-leaf"), model("safe")), NamespaceVisibility.PRIVATE);
            var retained = f.reloader.snapshot(namespace);
            String descriptorName = retained.skill(namespace + "-root").orElseThrow().name();
            var router = mock(CapabilityExecutionRouter.class);
            when(router.execute(any(), any(), any(), any())).thenAnswer(call -> {
                var binding = ExecutionBindingScope.requireCurrent();
                assertThat(binding.snapshot().entry().toString()).isEqualTo(descriptorName);
                assertThat(binding.snapshot().generation("dep").id()).isEqualTo(f.manager.active("dep").id());
                return binding.generation().id();
            });
            var template = template(f, router);
            var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
            try {
                for (int revision = 0; revision < 2; revision++) {
                    var current = publish(f, namespace, List.of(calls("root", "dep-leaf"), model("safe")), NamespaceVisibility.PRIVATE);
                    publish(f, "dep", List.of(model("leaf")), NamespaceVisibility.PUBLIC);
                    // Namespace normalization applies; business data cannot choose another root.
                    String target = namespace.toUpperCase(Locale.ROOT) + "-root";
                    Map<String, Object> input = Map.of("namespace", "missing", "skill", "dep-leaf");
                    template.validate(descriptorName, input);
                    template.validate(target, (Object) input);
                    assertThat(template.invoke(descriptorName, input)).isEqualTo(current.generationId());
                    assertThat(template.invoke(target, (Object) input)).isEqualTo(current.generationId());
                    var views = new AtomicInteger();
                    Consumer<SkillExecutionView> observer = view -> views.incrementAndGet();
                    assertThat(template.invoke(target, input, observer)).isEqualTo(current.generationId());
                    assertThat(template.invoke(target, (Object) input, observer)).isEqualTo(current.generationId());
                    try (var admitted = owned(handoff.handoff(target, input))) {
                        assertThat(admitted.generationId()).isEqualTo(current.generationId());
                        assertThat(admitted.invoke()).isEqualTo(current.generationId());
                    }
                    try (var admitted = owned(handoff.handoff(descriptorName, (Object) input))) {
                        assertThat(admitted.invoke(observer)).isEqualTo(current.generationId());
                    }
                    assertThat(views).hasValue(3);
                    assertThat(retained.generationId()).isNotEqualTo(current.generationId());
                    f.reloader.remove(namespace, current.generationId());
                    assertAllOperationsReject(template, handoff, target);
                }
                f.reloader.remove("dep", f.manager.active("dep").id());
                // Missing namespaces are not created by any root overload.
                assertAllOperationsReject(template, handoff, descriptorName);
                assertThatThrownBy(() -> f.manager.active(namespace)).isInstanceOf(SkillReloadException.class);
            } finally { f.lifecycle.destroy(); f.manager.destroy(); }
        }
    }

    @Test void rootBoundariesEnforceBothSizeLimitsAndPreserveLocalNameCase() {
        var f = new Fixture(null);
        var router = mock(CapabilityExecutionRouter.class);
        var template = template(f, router);
        var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
        String namespace = "n".repeat(32), local = "L".repeat(31);
        publish(f, namespace, List.of(model(local)), NamespaceVisibility.PRIVATE);
        try {
            template.validate(namespace.toUpperCase(Locale.ROOT) + "-" + local, Map.of());
            for (String invalid : List.of("root", "a-b-c", "n".repeat(33) + "-x", namespace + "-" + local + "L",
                    namespace + "-" + local.toLowerCase(Locale.ROOT), "missing-root"))
                assertAllOperationsReject(template, handoff, invalid);
            verifyNoInteractions(router);
        } finally { f.lifecycle.destroy(); f.manager.destroy(); }
    }

    @Test void dependencyOnlyChangesPermitCallerPublicationButRejectEveryFreshRootOperation() {
        for (String change : List.of("delete", "contract", "private")) {
            var f = new Fixture(null);
            var leaf = SkillDependencyValidatorTest.producer("leaf", "string");
            publish(f, "dep", List.of(leaf), NamespaceVisibility.PUBLIC);
            var docs = List.of(SkillDependencyValidatorTest.forward("root", "dep-leaf"), model("safe"));
            publish(f, "caller", docs, NamespaceVisibility.PRIVATE);
            var router = mock(CapabilityExecutionRouter.class);
            when(router.execute(any(), any(), any(), any())).thenReturn("old");
            var template = template(f, router); var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
            var admitted = handoff.handoff("caller-root", Map.of());
            var candidate = f.reloader.prepare("caller", docs, MODEL);
            String schema = candidate.snapshot().skill("caller-root").orElseThrow().outputSchema();
            try {
                if (change.equals("delete")) f.reloader.remove("dep", f.manager.active("dep").id());
                else publish(f, "dep", List.of(change.equals("contract") ? SkillDependencyValidatorTest.producer("leaf", "integer") : leaf),
                        change.equals("private") ? NamespaceVisibility.PRIVATE : NamespaceVisibility.PUBLIC);
                f.reloader.publish(candidate);
                assertAllOperationsReject(template, handoff, "caller-root");
                verifyNoInteractions(router);
                template.validate("caller-safe", Map.of());
                assertThat(template.invoke("caller-safe", Map.of())).isEqualTo("old");
                assertThat(admitted.invoke()).isEqualTo("old");
                assertThat(f.reloader.snapshot("caller").skill("caller-root").orElseThrow().outputSchema()).isEqualTo(schema);
            } finally { admitted.release(); candidate.close(); f.lifecycle.destroy(); f.manager.destroy(); }
        }
    }

    @Test void optionalRestrictedChildrenAreCapturedWithoutEagerRbacAndValidationNeverReserves() {
        var builds = new AtomicInteger();
        var f = new Fixture((p, policy) -> { builds.incrementAndGet(); return runtime(p, policy); });
        var child = new SkillDocument("leaf", model("leaf").yaml() + "rbac_roles: [ADMIN]\n");
        var dep = publish(f, "dep", List.of(child), NamespaceVisibility.PUBLIC);
        var root = new SkillDocument("root", model("root").yaml() + "rbac_roles: [USER]\nplanning_mode: true\nallowed_skills: [{name: dep-leaf, required: false, max_tasks: 1}]\n");
        publish(f, "caller", List.of(root, model("safe")), NamespaceVisibility.PRIVATE);
        var router = mock(CapabilityExecutionRouter.class);
        var template = template(f, router);
        var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
        var retired = new CopyOnWriteArrayList<String>(); f.manager.onGenerationRetired(retired::add);
        int before = builds.get();
        try {
            authenticate("user", "USER");
            template.validate("caller-root", Map.of());
            try (var admitted = owned(handoff.handoff("caller-root", Map.of()))) {
                assertThat(admitted.generationId()).isEqualTo(f.manager.active("caller").id());
            }
            assertThat(builds).hasValue(before);
            verifyNoInteractions(router);
            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> template.validate("caller-root", Map.of())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> handoff.handoff("caller-root", Map.of())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            authenticate("user", "USER");
            f.reloader.remove("dep", dep.generationId());
            assertThat(retired).contains(dep.generationId()); // No validation or abandoned handoff reservation remains.
            assertAllOperationsReject(template, handoff, "caller-root");
            template.validate("caller-safe", Map.of());
            assertThat(template.invoke("caller-safe", Map.of())).isEqualTo("null");
        } finally { f.lifecycle.destroy(); f.manager.destroy(); }
    }

    @Test void queuedWorkRetainsAllOldGenerationsVisibilityAndStableEntryId() {
        var f = new Fixture(null);
        var dep = publish(f, "dep", List.of(model("leaf")), NamespaceVisibility.PUBLIC);
        var root = publish(f, "caller", List.of(calls("root", "dep-leaf")), NamespaceVisibility.PRIVATE);
        var retired = new CopyOnWriteArrayList<String>(); f.manager.onGenerationRetired(retired::add);
        var router = mock(CapabilityExecutionRouter.class);
        var sessions = new HashSet<String>();
        when(router.execute(any(), any(), any(), any())).thenAnswer(call -> {
            var binding = ExecutionBindingScope.requireCurrent();
            assertThat(binding.generation().id()).isEqualTo(root.generationId());
            assertThat(binding.snapshot().generation("dep").id()).isEqualTo(dep.generationId());
            assertThat(binding.snapshot().generation("dep").visibility()).isEqualTo(NamespaceVisibility.PUBLIC);
            assertThat(sessions.add(binding.session().getSessionId())).isTrue();
            return "old";
        });
        var template = template(f, router);
        var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
        var first = handoff.handoff("caller-root", Map.of());
        var abandoned = handoff.handoff("caller-root", (Object) Map.of());
        try {
            f.reloader.remove("caller", root.generationId()); f.reloader.remove("dep", dep.generationId());
            publish(f, "dep", List.of(model("leaf")), NamespaceVisibility.PRIVATE);
            assertThat(retired).doesNotContain(root.generationId(), dep.generationId());
            abandoned.release(); abandoned.release();
            assertThat(abandoned.generationId()).isEqualTo(root.generationId());
            assertThat(retired).doesNotContain(root.generationId(), dep.generationId());
            assertThat(first.invoke()).isEqualTo("old");
            assertThat(retired).containsExactlyInAnyOrder(root.generationId(), dep.generationId());
            assertThat(first.generationId()).isEqualTo(root.generationId());
            assertThatThrownBy(first::invoke).isInstanceOf(SkillException.class);
        } finally { first.release(); abandoned.release(); f.lifecycle.destroy(); f.manager.destroy(); }
    }

    @Test void nestedExecutionRejectsAndReleasesWhileValidationAndHandoffPreparationStayAllowed() {
        var f = new Fixture(null);
        publish(f, "caller", List.of(model("root"), model("leaf")), NamespaceVisibility.PRIVATE);
        var router = mock(CapabilityExecutionRouter.class);
        var template = template(f, router);
        var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
        var pending = new AtomicReference<AdmittedSkillInvocation>();
        when(router.execute(any(), any(), any(), any())).thenAnswer(call -> {
            CapabilityMetadata cap = call.getArgument(0);
            if (cap.name().equals("caller-leaf")) return "leaf";
            var outer = ExecutionBindingScope.requireCurrent();
            template.validate("caller-leaf", Map.of());
            pending.set(handoff.handoff("caller-leaf", Map.of()));
            assertThatThrownBy(() -> template.invoke("caller-leaf", Map.of())).isInstanceOf(SkillException.class);
            try (var nested = owned(handoff.handoff("caller-leaf", Map.of()))) {
                assertThatThrownBy(nested::invoke).isInstanceOf(SkillException.class);
            }
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(outer);
            return "root";
        });
        try {
            assertThat(template.invoke("caller-root", Map.of())).isEqualTo("root");
            assertThat(pending.get().invoke()).isEqualTo("leaf");
            assertThat(ExecutionBindingScope.current()).isEmpty();
        } finally { if (pending.get() != null) pending.get().release(); f.lifecycle.destroy(); f.manager.destroy(); }
    }

    @Test void concurrentRootsAndQueuedAuthenticationRemainIsolated() throws Exception {
        var f = new Fixture(null);
        publish(f, "a", List.of(model("root")), NamespaceVisibility.PRIVATE);
        publish(f, "b", List.of(model("root")), NamespaceVisibility.PRIVATE);
        var router = mock(CapabilityExecutionRouter.class);
        when(router.execute(any(), any(), any(), any())).thenAnswer(call -> {
            var binding = ExecutionBindingScope.requireCurrent();
            return binding.snapshot().entry() + ":" + binding.session().getAuthentication().orElseThrow().getName();
        });
        var template = template(f, router); var handoff = new DefaultSkillInvocationHandoff(template, f.lifecycle);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new CyclicBarrier(2);
            var a = workers.submit(() -> { authenticate("alice"); start.await(5, TimeUnit.SECONDS); return template.invoke("A-root", Map.of()); });
            var b = workers.submit(() -> { authenticate("bob"); start.await(5, TimeUnit.SECONDS); return template.invoke("B-root", Map.of()); });
            assertThat(a.get(5, TimeUnit.SECONDS)).isEqualTo("a-root:alice");
            assertThat(b.get(5, TimeUnit.SECONDS)).isEqualTo("b-root:bob");
            authenticate("captured");
            try (var admitted = owned(handoff.handoff("a-root", Map.of()))) {
                authenticate("worker");
                assertThat(workers.submit(() -> admitted.invoke()).get(5, TimeUnit.SECONDS)).isEqualTo("a-root:captured");
                assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("worker");
            }
        } finally { f.lifecycle.destroy(); f.manager.destroy(); }
    }

    @Test void timeoutResponseRetainsWholeSnapshotUntilPhysicalWorkReturns() throws Exception {
        var f = new Fixture(null);
        var dep = publish(f, "dep", List.of(model("leaf")), NamespaceVisibility.PUBLIC);
        var config = new ExecutionConfiguration(MODEL.yaml() + "  session: {mission-timeout: 50ms}\n");
        var root = f.reloader.prepare("caller", List.of(calls("root", "dep-leaf")), config); f.reloader.publish(root);
        var retired = new CopyOnWriteArrayList<String>(); var retirementComplete = new CountDownLatch(2);
        f.manager.onGenerationRetired(id -> { retired.add(id); retirementComplete.countDown(); });
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        var router = mock(CapabilityExecutionRouter.class);
        var physical = Executors.newVirtualThreadPerTaskExecutor();
        var work = new ai.loomspan.internal.runtime.MissionWorkExecutor(
                new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(Clock.systemUTC()), Duration.ofSeconds(2),
                physical, new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService());
        when(router.execute(any(), any(), any(), any())).thenAnswer(call -> {
            var binding = ExecutionBindingScope.requireCurrent();
            var mission = new MissionContext(binding.session(), "caller-root", "mission", null);
            return ExecutionBindingScope.supplyWith(binding.withMission(mission), () -> work.execute(binding.session(), "caller-root", () -> {
                started.countDown();
                while (release.getCount() != 0) try { release.await(); } catch (InterruptedException ignored) { }
                return "late";
            }));
        });
        var template = template(f, router);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var response = callers.submit(() -> org.assertj.core.api.Assertions.catchThrowable(() -> template.invoke("caller-root", Map.of())));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            f.reloader.remove("caller", root.generationId()); f.reloader.remove("dep", dep.generationId());
            assertThat(response.get(5, TimeUnit.SECONDS)).isInstanceOf(SkillException.class)
                    .hasCauseInstanceOf(ai.loomspan.internal.runtime.LoomspanMissionTimeoutException.class);
            assertThat(retired).doesNotContain(root.generationId(), dep.generationId());
            release.countDown(); physical.shutdown(); assertThat(physical.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(retirementComplete.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(retired).containsExactlyInAnyOrder(root.generationId(), dep.generationId());
        } finally { release.countDown(); physical.shutdownNow(); f.lifecycle.destroy(); f.manager.destroy(); }
    }

    private static PreparedSkillUpdate publish(Fixture f, String namespace, List<SkillDocument> docs, NamespaceVisibility visibility) {
        var candidate = f.reloader.prepare(namespace, docs, MODEL, visibility); f.reloader.publish(candidate); return candidate;
    }

    private static DefaultSkillTemplate template(Fixture f, CapabilityExecutionRouter router) {
        var codecs = LoomspanJacksonCodecs.defaults();
        var runner = new LoomspanSessionRunner(4, TracePersistencePolicy.NEVER, Clock.systemUTC(),
                NoOpExecutionObservationHandleFactory.INSTANCE, ImmediateCompletionRetention.INSTANCE,
                new LoomspanProperties.Session.Quotas(), codecs.canonicalTrace(), f.lifecycle);
        return new DefaultSkillTemplate(f.manager, router, runner, codecs.applicationConversion(), new SkillInputValidator(), new SkillRoleEvaluator(null, null), null);
    }

    private static void authenticate(String principal, String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "fixture",
                Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    private static void assertAllOperationsReject(DefaultSkillTemplate template, DefaultSkillInvocationHandoff handoff, String name) {
        List<org.assertj.core.api.ThrowableAssert.ThrowingCallable> operations = List.of(
                () -> template.validate(name, Map.of()), () -> template.validate(name, (Object) Map.of()),
                () -> template.invoke(name, Map.of()), () -> template.invoke(name, (Object) Map.of()),
                () -> template.invoke(name, Map.of(), view -> { throw new AssertionError("unexpected observer"); }),
                () -> template.invoke(name, (Object) Map.of(), view -> { throw new AssertionError("unexpected observer"); }),
                () -> handoff.handoff(name, Map.of()), () -> handoff.handoff(name, (Object) Map.of()));
        operations.forEach(operation -> assertThatThrownBy(operation).isInstanceOf(SkillException.class));
    }
    private static Owned owned(AdmittedSkillInvocation admitted) { return new Owned(admitted); }
    private record Owned(AdmittedSkillInvocation admitted) implements AutoCloseable {
        public String invoke() { return admitted.invoke(); }
        public String invoke(Consumer<SkillExecutionView> observer) { return admitted.invoke(observer); }
        public String generationId() { return admitted.generationId(); }
        @Override public void close() { admitted.release(); }
    }
}
