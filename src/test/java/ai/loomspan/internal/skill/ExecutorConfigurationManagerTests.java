package ai.loomspan.internal.skill;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.env.StandardEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutorConfigurationManagerTests {
    @Test void validationAndBothPreparationParsePathsUsePublicationTargetWithoutApplicationCalls(@TempDir Path directory) {
        var properties = new LoomspanProperties();
        properties.getSkills().setLocations(List.of(directory.toUri() + "*.yml"));
        var javaSkills = mock(SkillMethodBeanPostProcessor.class);
        when(javaSkills.capabilities()).thenReturn(List.of());
        var invocations = new AtomicInteger();
        var beans = new StaticListableBeanFactory();
        beans.addBean("handler", (ai.loomspan.api.RestSkillHandler) invocation -> { invocations.incrementAndGet(); return "unused"; });
        var captured = new ArrayList<ExecutorConfiguration>();
        var manager = new SkillGenerationManager(javaSkills, () -> new YamlSkillCatalog(properties),
                new SkillInputContractResolver(), beans, properties, new StandardEnvironment(), (config, policy) -> {
                    captured.add(config.getExecutorConfig()); return null;
                });
        manager.afterSingletonsInstantiated();
        int startupCalls = captured.size();
        var view = manager.capturePreparation("sales");
        var config = new ExecutionConfiguration("loomspan: {executor-config: {sales: {sql: arbitrary, api-key-ref: unknown}}}");
        assertThat(manager.validate(view, List.of(), config, NamespaceVisibility.PRIVATE).valid()).isTrue();
        assertThat(captured).hasSize(startupCalls);
        manager.prepare(view, List.of(), config, NamespaceVisibility.PRIVATE, null).close();
        manager.prepare(view, List.of(), config, NamespaceVisibility.PRIVATE, Map.of()).close();
        assertThat(captured.subList(startupCalls, captured.size())).allSatisfy(value ->
                assertThat(value.namespaces().get("sales")).containsEntry("sql", "arbitrary").containsEntry("api-key-ref", "unknown"));
        var foreign = new ExecutionConfiguration("loomspan: {executor-config: {foreign: {}}}");
        assertThat(manager.validate(view, List.of(), foreign, NamespaceVisibility.PRIVATE).valid()).isFalse();
        assertThatThrownBy(() -> manager.prepare(view, List.of(), foreign, NamespaceVisibility.PRIVATE, null)).hasMessageContaining("target");
        assertThatThrownBy(() -> manager.prepare(view, List.of(), foreign, NamespaceVisibility.PRIVATE, Map.of())).hasMessageContaining("target");
        assertThat(invocations).hasValue(0);
    }
}
