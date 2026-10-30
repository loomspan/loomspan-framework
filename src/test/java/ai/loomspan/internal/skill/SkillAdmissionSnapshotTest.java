package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContract;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static ai.loomspan.internal.skill.SkillReloaderTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillAdmissionSnapshotTest {
    @Test void javaSkillsRemainLeavesAndAdmissionDoesNotConstructOrInvokeRuntime() {
        var invoked = new AtomicInteger(); var built = new AtomicInteger();
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));
        var javaSkills = mock(SkillMethodBeanPostProcessor.class);
        var leaf = new CapabilityMetadata("java:leaf", "default-leaf", "leaf", SkillExecutionDescriptor.none(),
                ai.loomspan.internal.security.SkillAccessPolicy.unrestricted(), arguments -> invoked.incrementAndGet(),
                CapabilityKind.JAVA_SKILL, CapabilityToolDescriptor.generic("default-leaf", "leaf"), new SkillSource(null, "bean", "leaf()"));
        when(javaSkills.capabilities()).thenReturn(List.of(leaf));
        var manager = new SkillGenerationManager(javaSkills, () -> new YamlSkillCatalog(properties),
                new SkillInputContractResolver(), new StaticListableBeanFactory(), properties, null,
                (p, policy) -> { built.incrementAndGet(); return runtime(p, policy); });
        manager.initializeStartup();
        int completedStartupBuilds = built.get();
        var capture = manager.capture(SkillIdentity.parse("default-leaf"));
        try {
            manager.validateAdmission(capture.snapshot());
            assertThat(capture.snapshot().generations()).containsOnlyKeys("default");
            assertThat(capture.snapshot().entryGeneration().capability("default-leaf")).isSameAs(leaf);
            assertThat(built).hasValue(completedStartupBuilds);
            assertThat(invoked).hasValue(0);
        } finally { capture.lease().close(); manager.destroy(); }
    }

    @Test void capturesEveryReachableSkillOnceAndOnlyReachedNamespaces() throws Exception {
        var f = new Fixture(null);
        publish(f, "spare", model("leaf"));
        publish(f, "c", new SkillDocument("leaf", model("leaf").yaml() + "rbac_roles: [ADMIN]\n"));
        publish(f, "b", calls("leaf", "c-leaf"));
        var root = new SkillDocument("root", model("root").yaml()
                + "planning_mode: true\nallowed_skills: [{name: a-first}, {name: a-second, required: false}]\n");
        publish(f, "a", root, calls("first", "b-leaf"), calls("second", "c-leaf"), calls("unused", "spare-leaf"));
        // Introduce a normal call cycle through an already valid graph.
        publish(f, "b", new SkillDocument("leaf", model("leaf").yaml()
                + "allowed_skills: [{name: c-leaf}, {name: a-root}]\n"));
        f.reloader.remove("spare", f.manager.active("spare").id());
        var retired = new CopyOnWriteArrayList<String>();
        f.reloader.onGenerationRetired(retired::add);
        var capture = f.manager.capture(SkillIdentity.parse("a-root"));
        try {
            assertThat(capture.snapshot().generations()).containsOnlyKeys("a", "b", "c");
            assertThat(capture.snapshot().entry()).isEqualTo(SkillIdentity.parse("a-root"));
            assertThat(capture.snapshot().entryGeneration()).isSameAs(f.manager.active("a"));
            assertThatThrownBy(() -> capture.snapshot().generations().clear()).isInstanceOf(UnsupportedOperationException.class);
            f.manager.validateAdmission(capture.snapshot());
            var ids = capture.snapshot().generations().values().stream().map(SkillGeneration::id).toList();
            for (var generation : capture.snapshot().generations().values())
                f.reloader.remove(generation.namespace(), generation.id());
            assertThat(retired).isEmpty();
            capture.lease().close();
            assertThat(retired).containsExactlyInAnyOrderElementsOf(ids);
            capture.lease().close();
            assertThat(retired).hasSize(3);
        } finally { capture.lease().close(); }
    }

    @Test void validatesEveryIncomingEdgeEvenWhenItsTargetWasAlreadyVisited() {
        var f = new Fixture(null);
        publish(f, "b", model("bridge"));
        var root = new SkillDocument("root", model("root").yaml()
                + "allowed_skills: [{name: a-target}, {name: b-bridge}]\n");
        publish(f, "a", root, model("target"));
        var pendingPrivate = f.reloader.prepare("a", List.of(root, model("target")), MODEL);
        publish(f, "b", calls("bridge", "a-target"));
        f.reloader.publish(pendingPrivate);
        var capture = f.manager.capture(SkillIdentity.parse("a-root"));
        try {
            assertThatThrownBy(() -> f.manager.validateAdmission(capture.snapshot())).hasMessageContaining("Private");
        } finally { capture.lease().close(); }
        // Private is still valid for direct entry and same-namespace access.
        var direct = f.manager.capture(SkillIdentity.parse("a-target"));
        try { f.manager.validateAdmission(direct.snapshot()); }
        finally { direct.lease().close(); }
    }

    @Test void partialCaptureFailureReleasesAllSelectedGenerations() {
        var f = new Fixture(null);
        publish(f, "early", model("leaf"));
        publish(f, "late", model("leaf"));
        publish(f, "a", new SkillDocument("root", model("root").yaml()
                + "planning_mode: true\nallowed_skills: [{name: early-leaf}, {name: late-leaf, required: false}]\n"));
        f.reloader.remove("late", f.manager.active("late").id());
        var retired = new CopyOnWriteArrayList<String>();
        f.reloader.onGenerationRetired(retired::add);
        assertThatThrownBy(() -> f.manager.capture(SkillIdentity.parse("a-root"))).hasMessageContaining("late");
        String a = f.manager.active("a").id(), early = f.manager.active("early").id();
        f.reloader.remove("a", a); f.reloader.remove("early", early);
        assertThat(retired).containsExactlyInAnyOrder(a, early);
    }

    @Test void captureRacingReplacementOrRemovalRetainsOneCoherentAssociation() throws Exception {
        var f = new Fixture(null);
        for (int iteration = 0; iteration < 12; iteration++) {
            publish(f, "dep", model("leaf"));
            publish(f, "a", calls("root", "dep-leaf"));
            String old = f.manager.active("dep").id();
            boolean remove = iteration % 2 == 0;
            var replacement = remove ? null : f.reloader.prepare("dep", List.of(model("leaf")), MODEL, NamespaceVisibility.PRIVATE);
            var retired = new CopyOnWriteArrayList<String>();
            try (var registration = f.reloader.onGenerationRetired(retired::add);
                 var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var start = new CyclicBarrier(2);
                var selection = workers.submit(() -> {
                    SkillDependencyValidatorTest.barrier(start);
                    try { return f.manager.capture(SkillIdentity.parse("a-root")); }
                    catch (SkillReloadException failure) { assertThat(remove).isTrue(); return null; }
                });
                var mutation = workers.submit(() -> {
                    SkillDependencyValidatorTest.barrier(start);
                    if (remove) f.reloader.remove("dep", old); else f.reloader.publish(replacement);
                });
                var capture = selection.get(5, TimeUnit.SECONDS); mutation.get(5, TimeUnit.SECONDS);
                if (capture == null) { assertThat(retired).contains(old); continue; }
                try {
                    var dep = capture.snapshot().generations().get("dep");
                    if (dep.id().equals(old)) {
                        assertThat(dep.visibility()).isEqualTo(NamespaceVisibility.PUBLIC);
                        assertThat(retired).doesNotContain(old);
                        f.manager.validateAdmission(capture.snapshot());
                    } else {
                        assertThat(dep.id()).isEqualTo(replacement.generationId());
                        assertThat(dep.visibility()).isEqualTo(NamespaceVisibility.PRIVATE);
                        assertThatThrownBy(() -> f.manager.validateAdmission(capture.snapshot())).hasMessageContaining("Private");
                    }
                } finally { capture.lease().close(); }
                assertThat(retired).contains(old);
            }
        }
    }

    @Test void publicationProgressesWhileAdmissionContractValidationIsBlocked() throws Exception {
        var blocked = new AtomicBoolean(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var inputs = new SkillInputContractResolver() {
            @Override public SkillInputContract resolveYamlCapability(YamlSkillDefinition definition) {
                if (blocked.get()) { entered.countDown(); await(release); }
                return super.resolveYamlCapability(definition);
            }
        };
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));
        var javaSkills = mock(SkillMethodBeanPostProcessor.class); when(javaSkills.capabilities()).thenReturn(List.of());
        var manager = new SkillGenerationManager(javaSkills, () -> new YamlSkillCatalog(properties), inputs,
                new StaticListableBeanFactory(), properties, null, SkillReloaderTest::runtime);
        manager.initializeStartup();
        var lifecycle = new FrameworkExecutionLifecycle(mock(ApplicationContext.class), Duration.ofMillis(100), Executors.newSingleThreadExecutor());
        var reloader = new DefaultSkillReloader(manager, lifecycle);
        reloader.publish(reloader.prepare("dep", List.of(model("leaf")), MODEL, NamespaceVisibility.PUBLIC));
        reloader.publish(reloader.prepare("a", List.of(calls("root", "dep-leaf")), MODEL));
        var replacement = reloader.prepare("dep", List.of(model("leaf")), MODEL);
        var capture = manager.capture(SkillIdentity.parse("a-root"));
        blocked.set(true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var validation = workers.submit(() -> manager.validateAdmission(capture.snapshot()));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                workers.submit(() -> reloader.publish(replacement)).get(5, TimeUnit.SECONDS);
                assertThat(manager.active("dep").visibility()).isEqualTo(NamespaceVisibility.PRIVATE);
                release.countDown(); validation.get(5, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        } finally { capture.lease().close(); }
    }

    static void publish(Fixture f, String namespace, SkillDocument... documents) {
        f.reloader.publish(f.reloader.prepare(namespace, List.of(documents), MODEL, NamespaceVisibility.PUBLIC));
    }
}
