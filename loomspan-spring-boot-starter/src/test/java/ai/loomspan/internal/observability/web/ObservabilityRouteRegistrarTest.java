package ai.loomspan.internal.observability.web;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.ExecutionTraceProperties;
import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityRouteRegistrarTest
{
    @Test
    void rejectsCompletionGraceThatCannotBeScheduled()
    {
        var configuration = new LoomspanProperties.Observability();
        configuration.setEnabled(true);
        configuration.getAuth().setApiKey("0123456789abcdef0123456789abcdef");
        configuration.setCompletionGraceTtl(Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(ObservabilityRouteRegistrar.validate(configuration))
                .isEqualTo("completion grace TTL is too large");
    }

    @Test
    void rejectsTraceCatalogMetadataTtlThatCannotBeScheduled()
    {
        var configuration = new LoomspanProperties.Observability();
        configuration.setEnabled(true);
        configuration.getAuth().setApiKey("0123456789abcdef0123456789abcdef");
        configuration.setTraceCatalogMetadataTtl(Duration.ofSeconds(Long.MAX_VALUE));

        assertThat(ObservabilityRouteRegistrar.validate(configuration))
                .isEqualTo("trace catalog metadata TTL is too large");
    }

    @Test
    void validatesInspectionCatalogBeforeStartingSchedulers()
    {
        LoomspanProperties properties = new LoomspanProperties();
        LoomspanProperties.Observability configuration = properties.getObservability();
        configuration.setEnabled(true);
        configuration.getAuth().setApiKey("0123456789abcdef0123456789abcdef");
        YamlSkillCatalog yamlSkills = mock(YamlSkillCatalog.class);
        CapabilityRegistry registry = mock(CapabilityRegistry.class);
        when(registry.getAllCapabilities()).thenThrow(new IllegalStateException("inspection projection failed"));
        var registrar = new ObservabilityRouteRegistrar(
                null, null, null, new ObservabilityActivationCoordinator(),
                properties, new ExecutionTraceProperties(), registry, yamlSkills, mock(YamlSkillCapabilityRegistrar.class),
                new ObservabilityDtoMapper(), new ObservabilityJsonCodec());
        long before = traceCatalogThreadCount();

        assertThatThrownBy(() -> registrar.createRuntime(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inspection projection failed");

        assertThat(traceCatalogThreadCount()).isEqualTo(before);
    }

    @Test
    void registersRoutesWithHostPathMatchingConfiguration()
    {
        PathPatternParser parser = new PathPatternParser();
        parser.setCaseSensitive(false);
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(parser);
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);
        when(mappings.getBuilderConfiguration()).thenReturn(options);
        LoomspanProperties properties = new LoomspanProperties();
        properties.getObservability().setEnabled(true);
        properties.getObservability().getAuth()
                .setApiKey("0123456789abcdef0123456789abcdef");
        YamlSkillCatalog yamlSkills = mock(YamlSkillCatalog.class);
        when(yamlSkills.getSkills()).thenReturn(List.of());
        ObservabilityActivationCoordinator activation = new ObservabilityActivationCoordinator();
        ObservabilityRouteRegistrar registrar = new ObservabilityRouteRegistrar(
                mappings,
                mock(ObservabilityRestController.class),
                mock(ObservabilityRouteCollisionDetector.class),
                activation,
                properties,
                new ExecutionTraceProperties(),
                mock(CapabilityRegistry.class), yamlSkills, mock(YamlSkillCapabilityRegistrar.class),
                new ObservabilityDtoMapper(),
                new ObservabilityJsonCodec());

        try
        {
            registrar.afterSingletonsInstantiated();

            ArgumentCaptor<RequestMappingInfo> registered = ArgumentCaptor.forClass(RequestMappingInfo.class);
            verify(mappings, atLeastOnce()).registerMapping(
                    registered.capture(), any(), any());
            assertThat(registered.getAllValues())
                    .anySatisfy(info -> assertThat(info.getPathPatternsCondition().getPatterns())
                            .anySatisfy(pattern -> assertThat(pattern.matches(
                                    org.springframework.http.server.PathContainer.parsePath(
                                            "/_loomspan/OBSERVABILITY/V1/INSTANCE"))).isTrue()));
        }
        finally
        {
            registrar.destroy();
        }
    }

    private static long traceCatalogThreadCount()
    {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().equals("loomspan-trace-catalog"))
                .count();
    }
}
