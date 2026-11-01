package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutorGenerationExecutionTest {
    @Test void preparesExecutorLeafAsJavaAndRetainsItsYamlDefinition(@TempDir Path directory) {
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of(directory.toUri() + "*.yml"));
        var beans = new StaticListableBeanFactory();
        var discovery = new SkillMethodBeanPostProcessor();
        discovery.setBeanFactory(beans);
        var bean = new ExecutorBean();
        beans.addBean("executor", bean);
        discovery.postProcessAfterInitialization(bean, "executor");
        var manager = new SkillGenerationManager(discovery, () -> new YamlSkillCatalog(properties),
                new SkillInputContractResolver());
        manager.initializeStartup();
        String yaml = "name: leaf\ndescription: executor leaf\nexecutor: run\nconfig: {operation: echo}\n";
        var candidate = manager.prepare(manager.capturePreparation("default"),
                List.of(new SkillDocument("leaf.yml", yaml)), new ExecutionConfiguration("loomspan: {}"),
                NamespaceVisibility.PRIVATE, null);
        assertThat(candidate.capability("default-leaf").kind().toString()).contains("JAVA");
        assertThat(candidate.definition("default-leaf").config()).containsEntry("operation", "echo");
        assertThat(candidate.definition("default-leaf").source().bytes()).isEqualTo(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var detail = candidate.registeredSkillCatalog().find("default-leaf").orElseThrow();
        assertThat(detail.kind()).isEqualTo("JAVA");
        assertThat(detail.declaration()).isEqualTo("YAML");
        assertThat(detail.executorName()).isEqualTo("default-run");
        assertThat(detail.beanName()).isEqualTo("executor");
        assertThat(detail.method()).contains("ExecutorBean.run(");
        assertThat(detail.yaml()).isEqualTo(yaml);
        assertThat(detail.sourcePath()).isEqualTo("leaf.yml");
        assertThat(candidate.skillCatalog().skills()).extracting(SkillDescriptor::name).containsExactly("default-leaf");
        assertThat(candidate.registeredSkillCatalog().find("default-run")).isEmpty();
        var sameName = manager.prepare(manager.capturePreparation("default"),
                List.of(new SkillDocument("opaque label", "# authored\r\nname: run\r\ndescription: leaf\r\nexecutor: default-run\r\n")),
                new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PRIVATE, null);
        assertThat(sameName.skillCatalog().skills()).extracting(SkillDescriptor::name).containsExactly("default-run");
        assertThat(sameName.registeredSkillCatalog().registeredSkillCount()).isOne();
        var sameDetail = sameName.registeredSkillCatalog().find("default-run").orElseThrow();
        assertThat(sameDetail.executorName()).isEqualTo("default-run");
        assertThat(sameDetail.sourcePath()).isEqualTo("opaque label");
        assertThat(sameDetail.yaml()).startsWith("# authored\r\n");
        sameName.close();
        assertThat(bean.calls).isZero();
        candidate.close();
        manager.destroy();
    }
    @Test void javaRouteConstructsInvocationAfterReferenceResolution(@TempDir Path directory) {
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of(directory.toUri() + "*.yml"));
        var beans = new StaticListableBeanFactory();
        var discovery = new SkillMethodBeanPostProcessor(); discovery.setBeanFactory(beans);
        var bean = new ExecutorBean(); beans.addBean("executor", bean); discovery.postProcessAfterInitialization(bean, "executor");
        var manager = new SkillGenerationManager(discovery, () -> new YamlSkillCatalog(properties), new SkillInputContractResolver());
        manager.initializeStartup();
        var view = manager.capturePreparation("default");
        var generation = manager.prepare(view, List.of(new SkillDocument("leaf", "name: leaf\ndescription: leaf\nexecutor: run\nconfig: {operation: echo}\n")),
                new ExecutionConfiguration("loomspan: {}"), NamespaceVisibility.PRIVATE, null);
        SkillGenerationManager.dispatch(manager.publish(generation, view.baseId()));
        var captured = manager.capture(SkillIdentity.parse("default-leaf"));
        var admitted = manager.validateAdmission(captured.snapshot());
        var state = new ai.loomspan.internal.runtime.state.DefaultExecutionStateService(java.time.Clock.systemUTC());
        var modelFactory = mock(ai.loomspan.internal.model.ModelInteractionFactory.class);
        var engine = mock(ai.loomspan.internal.runtime.MissionExecutionEngine.class);
        var resource = new org.springframework.core.io.ByteArrayResource(new byte[] {4, 5, 6});
        var session = TestLoomspanSessions.withId("executor-refs", "default-leaf", 3);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var coordinator = new ExecutionCoordinator(modelFactory, mock(ai.loomspan.internal.runtime.tool.ToolSurfaceService.class),
                    mock(ai.loomspan.internal.runtime.tool.CapabilityBindingFactory.class), engine, engine, state,
                    new ai.loomspan.internal.security.DefaultAccessGuard(), (value, current) -> "ref://file".equals(value) ? resource : value,
                    new ai.loomspan.internal.security.ScopedAuthentication(null),
                    new ai.loomspan.internal.runtime.MissionWorkExecutor(state, java.time.Duration.ofSeconds(5), workers,
                            new ai.loomspan.internal.runtime.usage.NoOpSessionUsageService()));
            assertThat(ExecutionBindingScope.supplyWith(ExecutionBinding.sessionOnly(session, admitted), () ->
                    coordinator.execute("default-leaf", "resolve resource", Map.of("file", "ref://file"), session, null))).isEqualTo("ok");
            assertThat(bean.invocation.input().get("file")).isSameAs(resource);
            assertThat(bean.invocation.skillConfig()).containsEntry("operation", "echo");
            assertThat(bean.invocation.executorConfig()).isEmpty();
            assertThat(bean.invocation.skillName()).isEqualTo("default-leaf");
            assertThat(bean.invocation.executorConfigGenerationId()).isEqualTo(generation.id());
            verifyNoInteractions(modelFactory, engine);
        } finally { captured.lease().close(); manager.destroy(); }
    }
    static class ExecutorBean {
        int calls;
        SkillExecutorInvocation invocation;
        @SkillExecutor(name = "run") public String run(SkillExecutorInvocation invocation) { calls++; this.invocation = invocation; return "ok"; }
    }
}
