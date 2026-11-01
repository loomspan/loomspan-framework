package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutorDependencyResolutionTest {
    static SkillExecutorRegistration executor(String name, String path) {
        return new SkillExecutorRegistration(SkillIdentity.parse(name), path, SkillAccessPolicy.unrestricted(),
                new SkillSource(null, "bean", "run"), invocation -> "unused");
    }
    static SkillGenerationManager manager(List<SkillExecutorRegistration> registrations, Map<String, Object> startup) {
        return manager(registrations, startup, new SkillInputContractResolver());
    }
    static SkillGenerationManager manager(List<SkillExecutorRegistration> registrations, Map<String, Object> startup,
            SkillInputContractResolver inputs) {
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));
        properties.setExecutorConfig(ExecutorConfiguration.from(startup));
        var discovery = mock(SkillMethodBeanPostProcessor.class);
        when(discovery.capabilities()).thenReturn(List.of());
        when(discovery.executors()).thenReturn(registrations);
        var manager = new SkillGenerationManager(discovery, () -> new YamlSkillCatalog(properties), inputs, properties, null, null);
        manager.initializeStartup();
        return manager;
    }
    static SkillDocument leaf(String name, String executor) {
        return new SkillDocument(name, "name: " + name + "\ndescription: leaf\nexecutor: " + executor + "\nconfig: {operation: echo}\n");
    }
    static SkillGeneration prepare(SkillGenerationManager manager, String namespace, List<SkillDocument> docs,
            String config, NamespaceVisibility visibility) {
        return manager.prepare(manager.capturePreparation(namespace), docs, new ExecutionConfiguration(config), visibility, null);
    }
    static SkillGeneration publish(SkillGenerationManager manager, String namespace, List<SkillDocument> docs,
            String config, NamespaceVisibility visibility) {
        var view = manager.capturePreparation(namespace);
        var generation = manager.prepare(view, docs, new ExecutionConfiguration(config), visibility, null);
        SkillGenerationManager.dispatch(manager.publish(generation, view.baseId()));
        return generation;
    }
    static SkillAdmissionSnapshot admit(SkillGenerationManager manager, String name) {
        var capture = manager.capture(SkillIdentity.parse(name));
        try { return manager.validateAdmission(capture.snapshot()); }
        finally { capture.lease().close(); }
    }

    @Test void exactPathSelectionRejectsMalformedMissingNullScalarAndListButAcceptsEmptyMap() {
        for (String path : List.of(" ", " x", "x ", ".x", "x.", "x..y"))
            assertThatThrownBy(() -> manager(List.of(executor("default-run", path)), Map.of()))
                    .hasMessageContaining("Invalid executor config path");
        for (String selected : List.of("null", "3", "[]", "text")) {
            try (var cleanup = new ManagerOwner(manager(List.of(executor("default-run", "a.b")),
                    Map.of("default", Map.of("a", Map.of("b", Map.of())))))) {
                var manager = cleanup.manager;
                var result = manager.validate(manager.capturePreparation("default"), List.of(leaf("leaf", "run")),
                        new ExecutionConfiguration("loomspan: {executor-config: {default: {a: {b: " + selected + "}}}}"), NamespaceVisibility.PRIVATE);
                assertThat(result.valid()).as(selected).isFalse();
            }
        }
        var manager = manager(List.of(executor("default-run", "a.b")), Map.of("default", Map.of("a", Map.of("b", Map.of()))));
        try {
            assertThat(manager.validate(manager.capturePreparation("default"), List.of(leaf("leaf", "run")),
                    new ExecutionConfiguration("loomspan: {executor-config: {default: {'a.b': {}}}}"), NamespaceVisibility.PRIVATE).valid()).isFalse();
            publish(manager, "default", List.of(leaf("leaf", "run")),
                    "loomspan: {executor-config: {default: {a: {b: {'literal.dot': value, Case: [null, {}, []]}}}}}", NamespaceVisibility.PRIVATE);
            var binding = admit(manager, "default-leaf").executorBinding("default-leaf");
            assertThat(binding.configuration()).containsEntry("literal.dot", "value").containsKey("Case");
            assertThatThrownBy(() -> binding.configuration().put("other", "x")).isInstanceOf(UnsupportedOperationException.class);
        } finally { manager.destroy(); }
    }

    @Test void localAndQualifiedRolesRemainSeparateAndRespectVisibility() {
        var manager = manager(List.of(executor("alpha-run", ""), executor("beta-run", ""), executor("default-run", "")), Map.of());
        try {
            assertThat(manager.active("alpha").capabilities()).isEmpty();
            assertThat(manager.active("alpha").executors()).containsOnlyKeys("alpha-run");
            publish(manager, "alpha", List.of(leaf("run", "run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
            assertThat(admit(manager, "alpha-run").executorBinding("alpha-run").registration().name()).isEqualTo("alpha-run");
            assertThatThrownBy(() -> prepare(manager, "caller", List.of(leaf("leaf", "beta-run")), "loomspan: {}", NamespaceVisibility.PRIVATE))
                    .hasMessageContaining("Private");
            assertThatThrownBy(() -> prepare(manager, "caller", List.of(leaf("leaf", "default-run")), "loomspan: {}", NamespaceVisibility.PRIVATE))
                    .hasMessageContaining("Private");
            publish(manager, "beta", List.of(), "loomspan: {}", NamespaceVisibility.PUBLIC);
            publish(manager, "caller", List.of(leaf("leaf", "beta-run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
            assertThat(admit(manager, "caller-leaf").executorBinding("caller-leaf").registration().name()).isEqualTo("beta-run");
            assertThatThrownBy(() -> prepare(manager, "caller", List.of(leaf("leaf", "run")), "loomspan: {}", NamespaceVisibility.PRIVATE))
                    .hasMessageContaining("Unknown executor 'caller-run'");
            assertThatThrownBy(() -> manager.capture(SkillIdentity.parse("beta-run"))).hasMessageContaining("Unknown skill");
            var model = new SkillDocument("root", "name: root\ndescription: root\nmodel: model\nallowed_skills: [{name: beta-run}]\n");
            assertThatThrownBy(() -> prepare(manager, "caller", List.of(model),
                    SkillReloaderTest.MODEL.yaml(), NamespaceVisibility.PRIVATE)).hasMessageContaining("Unknown child");
        } finally { manager.destroy(); }
    }

    @Test void retainedBindingAndTargetBasePublicationRemainCoherentAcrossForeignReplacementRemovalRecreation() {
        var manager = manager(List.of(executor("infra-run", "settings")), Map.of("infra", Map.of("settings", Map.of("version", 0))));
        var retired = new ArrayList<String>();
        manager.onGenerationRetired(retired::add);
        try {
            var old = publish(manager, "infra", List.of(), "loomspan: {executor-config: {infra: {settings: {version: 1}}}}", NamespaceVisibility.PUBLIC);
            publish(manager, "caller", List.of(leaf("leaf", "infra-run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
            var capture = manager.capture(SkillIdentity.parse("caller-leaf"));
            var retained = manager.validateAdmission(capture.snapshot());
            var view = manager.capturePreparation("caller");
            var candidate = manager.prepare(view, List.of(leaf("leaf", "infra-run")), new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PRIVATE, null);
            var replacement = publish(manager, "infra", List.of(), "loomspan: {executor-config: {infra: {settings: {version: 2}}}}", NamespaceVisibility.PRIVATE);
            assertThat(retired).doesNotContain(old.id());
            SkillGenerationManager.dispatch(manager.publish(candidate, view.baseId()));
            assertThatThrownBy(() -> admit(manager, "caller-leaf")).hasMessageContaining("Private");
            assertThat(retained.executorBinding("caller-leaf").configuration()).containsEntry("version", 1);
            assertThat(retained.executorBinding("caller-leaf").generationId()).isEqualTo(old.id());
            SkillGenerationManager.dispatch(manager.remove("infra", replacement.id()));
            assertThatThrownBy(() -> admit(manager, "caller-leaf")).hasMessageContaining("No active namespace");
            var recreated = publish(manager, "infra", List.of(), "loomspan: {executor-config: {infra: {settings: {version: 3}}}}", NamespaceVisibility.PUBLIC);
            var fresh = admit(manager, "caller-leaf").executorBinding("caller-leaf");
            assertThat(fresh.generationId()).isEqualTo(recreated.id());
            assertThat(fresh.configuration()).containsEntry("version", 3);
            assertThat(fresh.registration()).isSameAs(retained.executorBinding("caller-leaf").registration());
            capture.lease().close();
            assertThat(retired).containsOnlyOnce(old.id());
            assertThatThrownBy(() -> manager.publish(candidate, view.baseId())).hasMessageContaining("stale");
        } finally { manager.destroy(); }
    }

    @Test void executorRetentionDoesNotTraverseUnrelatedDeclarationsOrGrantCallableAuthority() {
        var manager = manager(List.of(executor("infra-run", ""), executor("third-run", "")), Map.of());
        try {
            publish(manager, "third", List.of(), "loomspan: {}", NamespaceVisibility.PUBLIC);
            publish(manager, "infra", List.of(leaf("unrelated", "third-run")), "loomspan: {}", NamespaceVisibility.PUBLIC);
            publish(manager, "caller", List.of(leaf("leaf", "infra-run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
            SkillGenerationManager.dispatch(manager.remove("third", manager.active("third").id()));
            var admitted = admit(manager, "caller-leaf");
            assertThat(admitted.generations()).containsOnlyKeys("caller", "infra");
            assertThat(admitted.executorBindings()).containsOnlyKeys("caller-leaf");
            assertThat(admitted.entryGeneration().definition("caller-leaf").allowedSkills()).isEmpty();
            var session = TestLoomspanSessions.withId("executor-authority", "caller-leaf", 3);
            var binding = ExecutionBinding.sessionOnly(session, admitted);
            var unrelated = admitted.resolve(SkillIdentity.parse("infra-unrelated"));
            var guard = new ai.loomspan.internal.security.DefaultAccessGuard();
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(binding, () -> {
                guard.checkAccess(unrelated, session, null); return "unexpected";
            })).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            var mission = new MissionContext(session, "caller-leaf", "frame", null);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(
                    binding.withTarget(admitted.resolve(SkillIdentity.parse("caller-leaf")), mission), () -> {
                        guard.checkAccess(unrelated, session, null); return "unexpected";
                    })).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> admit(manager, "infra-unrelated")).hasMessageContaining("third");
        } finally { manager.destroy(); }
    }

    @Test void bothReferenceRolesSelectOneGenerationAndCandidateOverlayChecksReturnEdges() {
        var manager = manager(List.of(executor("infra-run", ""), executor("target-run", "")), Map.of());
        try {
            publish(manager, "target", List.of(), "loomspan: {}", NamespaceVisibility.PUBLIC);
            var old = publish(manager, "infra", List.of(leaf("child", "run"), leaf("bridge", "target-run")),
                    "loomspan: {}", NamespaceVisibility.PUBLIC);
            var root = new SkillDocument("root", "name: root\ndescription: root\nmodel: model\nallowed_skills: [{name: caller-leaf}, {name: infra-child}]\n");
            publish(manager, "caller", List.of(root, leaf("leaf", "infra-run")), SkillReloaderTest.MODEL.yaml(), NamespaceVisibility.PRIVATE);
            var capture = manager.capture(SkillIdentity.parse("caller-root"));
            try {
                publish(manager, "infra", List.of(leaf("child", "run"), leaf("bridge", "target-run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
                var admitted = manager.validateAdmission(capture.snapshot());
                assertThat(admitted.generations()).containsOnlyKeys("caller", "infra");
                assertThat(admitted.executorBinding("caller-leaf").generationId()).isEqualTo(old.id());
                assertThat(admitted.executorBinding("infra-child").generationId()).isEqualTo(old.id());
            } finally { capture.lease().close(); }
            publish(manager, "infra", List.of(leaf("bridge", "target-run")), "loomspan: {}", NamespaceVisibility.PUBLIC);
            var returning = new SkillDocument("root", "name: root\ndescription: root\nmodel: model\nallowed_skills: [{name: infra-bridge}]\n");
            assertThatThrownBy(() -> prepare(manager, "target", List.of(returning), SkillReloaderTest.MODEL.yaml(), NamespaceVisibility.PRIVATE))
                    .hasMessageContaining("Private").hasMessageContaining("executor 'target-run'");
            prepare(manager, "target", List.of(returning), SkillReloaderTest.MODEL.yaml(), NamespaceVisibility.PUBLIC).close();
        } finally { manager.destroy(); }
    }

    @Test void expensiveAdmissionValidationAllowsConcurrentPublicationWithSameCapturedExecutor() throws Exception {
        var blocked = new AtomicBoolean(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var inputs = new SkillInputContractResolver() {
            @Override public ai.loomspan.internal.runtime.input.SkillInputContract resolveYamlCapability(YamlSkillDefinition definition) {
                if (blocked.get()) { entered.countDown(); try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException e) { throw new AssertionError(e); } }
                return super.resolveYamlCapability(definition);
            }
        };
        var manager = manager(List.of(executor("infra-run", "")), Map.of(), inputs);
        try {
            var old = publish(manager, "infra", List.of(), "loomspan: {}", NamespaceVisibility.PUBLIC);
            publish(manager, "caller", List.of(leaf("leaf", "infra-run")), "loomspan: {}", NamespaceVisibility.PRIVATE);
            var replacement = prepare(manager, "infra", List.of(), "loomspan: {}", NamespaceVisibility.PRIVATE);
            var capture = manager.capture(SkillIdentity.parse("caller-leaf"));
            blocked.set(true);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var pending = workers.submit(() -> manager.validateAdmission(capture.snapshot()));
                try {
                    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                    workers.submit(() -> SkillGenerationManager.dispatch(manager.publish(replacement, old.id()))).get(5, TimeUnit.SECONDS);
                    release.countDown();
                    assertThat(pending.get(5, TimeUnit.SECONDS).executorBinding("caller-leaf").generationId()).isEqualTo(old.id());
                } finally { release.countDown(); capture.lease().close(); }
            }
        } finally { manager.destroy(); }
    }
    private record ManagerOwner(SkillGenerationManager manager) implements AutoCloseable {
        @Override public void close() { manager.destroy(); }
    }
}
