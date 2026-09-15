package ai.loomspan.autoconfigure;

import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import ai.loomspan.internal.observability.web.BoundedJsonPageWriter;
import ai.loomspan.internal.observability.web.ObservabilityAccessService;
import ai.loomspan.internal.observability.web.ObservabilityApiKeyFilter;
import ai.loomspan.internal.observability.web.ObservabilityCursorCodec;
import ai.loomspan.internal.observability.web.ObservabilityDtoMapper;
import ai.loomspan.internal.observability.web.ObservabilityJsonCodec;
import ai.loomspan.internal.observability.web.ObservabilityProblemMapper;
import ai.loomspan.internal.observability.web.ObservabilityRestController;
import ai.loomspan.internal.observability.web.ObservabilityRouteCollisionDetector;
import ai.loomspan.internal.observability.web.ObservabilityRouteRegistrar;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.EnumSet;
import java.util.List;

@AutoConfiguration
@AutoConfigureAfter({ LoomspanAutoConfiguration.class, WebMvcAutoConfiguration.class })
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ DispatcherServlet.class, Filter.class })
public class LoomspanObservabilityWebAutoConfiguration
{
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityJsonCodec observabilityJsonCodec(LoomspanJacksonCodecs codecs)
    {
        return new ObservabilityJsonCodec(codecs.strictObservability());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityProblemMapper observabilityProblemMapper() { return new ObservabilityProblemMapper(); }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityAccessService observabilityAccessService() { return new ObservabilityAccessService(); }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityDtoMapper observabilityDtoMapper() { return new ObservabilityDtoMapper(); }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityCursorCodec observabilityCursorCodec(ObservabilityJsonCodec json)
    {
        return new ObservabilityCursorCodec(json);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    BoundedJsonPageWriter boundedJsonPageWriter(ObservabilityJsonCodec json)
    {
        return new BoundedJsonPageWriter(json);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityRestController observabilityRestController(
            ObservabilityActivationCoordinator activation,
            ObservabilityAccessService access,
            ObservabilityDtoMapper mapper,
            ObservabilityCursorCodec cursors,
            BoundedJsonPageWriter pages,
            ObservabilityJsonCodec json)
    {
        return new ObservabilityRestController(activation, access, mapper, cursors, pages, json);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityRouteCollisionDetector observabilityRouteCollisionDetector(List<HandlerMapping> handlerMappings)
    {
        return new ObservabilityRouteCollisionDetector(handlerMappings);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityRouteRegistrar observabilityRouteRegistrar(
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> mappings,
            ObservabilityRestController controller,
            ObservabilityRouteCollisionDetector collisions,
            ObservabilityActivationCoordinator activation,
            LoomspanProperties properties,
            ExecutionTraceProperties traceProperties,
            YamlSkillCatalog yamlSkills,
            ai.loomspan.internal.core.CapabilityRegistry registry,
            ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar registrar,
            ObservabilityDtoMapper mapper,
            ObservabilityJsonCodec json)
    {
        return new ObservabilityRouteRegistrar(
                mappings.getIfAvailable(), controller, collisions, activation, properties, traceProperties,
                registry, yamlSkills, registrar, mapper, json);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FilterRegistrationBean<ObservabilityApiKeyFilter> observabilityApiKeyFilter(
            ObservabilityActivationCoordinator activation,
            ObservabilityJsonCodec json,
            ObservabilityProblemMapper problems)
    {
        var registration = new FilterRegistrationBean<>(
                new ObservabilityApiKeyFilter(activation, json, problems));
        registration.setName("LoomspanObservabilityApiKeyFilter");
        registration.addUrlPatterns("/_loomspan/observability/v1", "/_loomspan/observability/v1/*");
        registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        registration.setAsyncSupported(true);
        registration.setOrder(-99);
        return registration;
    }
}
