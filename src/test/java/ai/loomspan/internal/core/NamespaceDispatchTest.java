package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.internal.security.*;
import ai.loomspan.internal.skill.*;
import ai.loomspan.internal.runtime.*;
import ai.loomspan.internal.runtime.state.*;
import ai.loomspan.internal.runtime.tool.*;
import ai.loomspan.internal.runtime.usage.NoOpSessionUsageService;
import ai.loomspan.internal.model.ModelInteractionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceDispatchTest {
    @Test void everyDirectAndAssembledRouteIndependentlyChecksSourceBeforeSwitching() {
        for (boolean declared : List.of(false, true)) for (var visibility : NamespaceVisibility.values()) {
            var count = new AtomicInteger();
            var target = new CapabilityMetadata("target", "tax-leaf", "leaf", SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(), input -> {
                count.incrementAndGet();
                var selected = ExecutionBindingScope.requireCurrent();
                assertThat(selected.currentNamespace()).isEqualTo("tax");
                assertThat(selected.entryNamespace()).isEqualTo("orders");
                assertThat(selected.mission().parent().orElseThrow().skillName()).isEqualTo("orders-root");
                return "ok";
            }, SkillKind.JAVA, CapabilityToolDescriptor.generic("tax-leaf", "leaf"), null);
            var manifest = new YamlSkillManifest(); manifest.setName("root");
            var definition = new YamlSkillDefinition(YamlSkillDefinition.Form.MODEL, "orders", new ByteArrayResource(new byte[0]), manifest,
                    new EffectiveSkillExecutionConfiguration("model", "connection", ai.loomspan.autoconfigure.AiDriver.OPENAI, "provider", null),
                    null, null, declared ? List.of(new AllowedSkillConstraint(target.name(), null, null, false, List.of())) : List.of());
            var root = new CapabilityMetadata("source", "orders-root", "root", SkillExecutionDescriptor.none(), SkillAccessPolicy.unrestricted(), input -> "unused",
                    SkillKind.YAML, CapabilityToolDescriptor.generic("orders-root", "root"), null);
            var emptyCatalog = new ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog(List.of(), Map.of());
            var orders = new SkillGeneration("orders", "orders-old", Map.of(root.name(), root), Map.of(root.name(), definition), null, emptyCatalog);
            var tax = new SkillGeneration("tax", "tax-old", Map.of(target.name(), target), Map.of(), null, emptyCatalog, null, visibility);
            var snapshot = new SkillAdmissionSnapshot(root.identity(), Map.of("orders", orders, "tax", tax));
            var session = TestLoomspanSessions.withId("dispatch" + declared + visibility, root.name(), 12);
            var parent = ExecutionBinding.sessionOnly(session, snapshot).withMission(new MissionContext(session, root.name(), "parent", null));
            var state = new DefaultExecutionStateService(java.time.Clock.systemUTC());
            var refs = mock(ai.loomspan.internal.vfs.RefResolver.class);
            when(refs.resolveArguments(anyMap(), eq(session), any())).thenAnswer(invocation -> invocation.getArgument(0));
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var coordinator = new ExecutionCoordinator(mock(ModelInteractionFactory.class), mock(ToolSurfaceService.class), mock(CapabilityBindingFactory.class),
                        mock(MissionExecutionEngine.class), mock(MissionExecutionEngine.class), state, new DefaultAccessGuard(), refs,
                        new ScopedAuthentication(null), new MissionWorkExecutor(state, java.time.Duration.ofSeconds(3), workers, new NoOpSessionUsageService()));
                var router = new CapabilityExecutionRouter(new StaticListableBeanFactory(Map.of("coordinator", coordinator)).getBeanProvider(ExecutionCoordinator.class), new DefaultAccessGuard());
                List<Runnable> paths = List.of(
                        () -> router.execute(target, Map.of(), session, null),
                        () -> router.executeAssembled(target, Map.of(), session, null, List.of()),
                        () -> coordinator.execute(target, "call", Map.of(), session, null),
                        () -> coordinator.execute(target.name(), "call", Map.of(), session, null));
                ExecutionBindingScope.runWith(parent, () -> {
                    for (Runnable path : paths) {
                        if (declared && visibility == NamespaceVisibility.PUBLIC) path.run();
                        else assertThatThrownBy(path::run).isInstanceOf(AccessDeniedException.class);
                        assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
                    }
                });
                assertThat(count).hasValue(declared && visibility == NamespaceVisibility.PUBLIC ? paths.size() : 0);
            }
        }
    }
}
