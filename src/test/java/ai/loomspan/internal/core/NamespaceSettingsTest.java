package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.*;
import ai.loomspan.internal.skill.*;
import ai.loomspan.internal.runtime.*;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.core.io.ByteArrayResource;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NamespaceSettingsTest {
    @Test void childMaterializationUsesItsOwnCapturedAttachmentLimit() {
        var properties = new LoomspanProperties(); properties.getSession().getAttachments().setMaxSize(DataSize.ofBytes(20));
        var smaller = new LoomspanProperties(); smaller.getSession().getAttachments().setMaxSize(DataSize.ofBytes(3));
        var orders = generation("orders", properties); var tax = generation("tax", smaller);
        var snapshot = new SkillAdmissionSnapshot(SkillIdentity.parse("orders-root"), Map.of("orders", orders, "tax", tax));
        var session = TestLoomspanSessions.withId("attachments", "orders-root", 8);
        var parent = ExecutionBinding.sessionOnly(session, snapshot).withMission(new MissionContext(session, "orders-root", "root", null));
        var child = new ExecutionBinding(session, new MissionContext(session, "tax-child", "child", parent.mission()), parent.branch(), tax, snapshot);
        var materializer = new ai.loomspan.internal.runtime.attachment.DefaultMissionInputMaterializer(mock(ai.loomspan.internal.vfs.RefResolver.class));
        var manifest = new YamlSkillManifest(); manifest.setName("child");
        var schema = new YamlSkillManifest.InputSchemaManifest(); schema.setType("object");
        var image = new YamlSkillManifest.InputSchemaManifest(); image.setType("attachment"); image.setMediaType("image"); image.setAllowedContentTypes(List.of("image/jpeg"));
        schema.setProperties(Map.of("image", image)); manifest.setInputSchema(schema);
        var definition = new YamlSkillDefinition("tax", new ByteArrayResource(new byte[0]), manifest,
                new EffectiveSkillExecutionConfiguration("model", "connection", AiDriver.OPENAI, "provider", null));
        var resource = new ByteArrayResource(new byte[]{(byte)255,(byte)216,(byte)255,1,2,3,4,5}) {
            @Override public String getFilename() { return "image.jpg"; }
        };
        var input = Map.<String,Object>of("image", resource);
        ExecutionBindingScope.runWith(parent, () -> {
            assertThat(materializer.materialize(session, definition, "input", input).attachments()).hasSize(1);
            assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(child, () -> materializer.materialize(session, definition, "input", input)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exceeding configured limit");
            assertThat(ExecutionBindingScope.requireCurrent()).isSameAs(parent);
        });
        // A large current limit is not reduced to the entry's smaller attachment setting.
        var reverse = new SkillAdmissionSnapshot(SkillIdentity.parse("tax-root"), Map.of("orders", orders, "tax", tax));
        var reverseSession = TestLoomspanSessions.withId("reverse", "tax-root", 8);
        var current = new ExecutionBinding(reverseSession, null, new PhysicalBranchContext(reverseSession), orders, reverse);
        ExecutionBindingScope.runWith(current, () -> assertThat(materializer.materialize(reverseSession, definition, "input", input).attachments()).hasSize(1));
    }

    @Test void parentDeadlineContinuesAndEachChildUsesItsCapturedMissionTimeout() throws Exception {
        for (boolean parentExpires : List.of(true, false)) {
            var parentSettings = new LoomspanProperties(); parentSettings.getSession().setMissionTimeout(Duration.ofMillis(parentExpires ? 400 : 3000));
            var childSettings = new LoomspanProperties(); childSettings.getSession().setMissionTimeout(Duration.ofMillis(parentExpires ? 3000 : 100));
            var orders = generation("orders", parentSettings); var tax = generation("tax", childSettings);
            var snapshot = new SkillAdmissionSnapshot(SkillIdentity.parse("orders-root"), Map.of("orders", orders, "tax", tax));
            var session = TestLoomspanSessions.withId("timeouts" + parentExpires, "orders-root", 8);
            var parent = ExecutionBinding.sessionOnly(session, snapshot).withMission(new MissionContext(session, "orders-root", "root", null));
            var child = new ExecutionBinding(session, new MissionContext(session, "tax-child", "child", parent.mission()), parent.branch(), tax, snapshot);
            var started = new CountDownLatch(1); var returned = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var state = mock(ExecutionStateService.class);
                when(state.recordFailure(eq(session), any(Throwable.class), anyMap())).thenReturn("failure");
                var worker = new MissionWorkExecutor(state, Duration.ofSeconds(30), executor, new NoOpSessionUsageService());
                assertThatThrownBy(() -> ExecutionBindingScope.supplyWith(parent, () -> worker.execute(session, "orders-root", () -> {
                    Thread.sleep(50); // Child starts after the parent's own timeout is already running.
                    return ExecutionBindingScope.supplyWith(child, () -> worker.execute(session, "tax-child", () -> {
                        started.countDown();
                        try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); return "unreachable"; }
                        finally { returned.countDown(); }
                    }));
                }))).isInstanceOf(LoomspanMissionTimeoutException.class)
                        .hasMessageContaining(parentExpires ? "orders-root after PT0.4S" : "tax-child after PT0.1S");
                assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(returned.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(ExecutionBindingScope.current()).isEmpty();
            }
        }
    }

    private static SkillGeneration generation(String namespace, LoomspanProperties settings) {
        var registry = new ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry(Map.of(), mock(ai.loomspan.internal.springai.SpringAiProviderIntegration.class));
        return new SkillGeneration(namespace, namespace, Map.of(), Map.of(), null, null,
                new ExecutionRuntime(settings, TracePersistencePolicy.NEVER, registry));
    }
}
