package ai.loomspan.autoconfigure;

import ai.loomspan.internal.core.LoomspanExceptionTransformer;
import ai.loomspan.internal.core.CapabilityRegistry;
import ai.loomspan.internal.core.LoomspanSessionRunner;
import ai.loomspan.internal.core.CapabilityExecutionRouter;
import ai.loomspan.internal.core.DefaultLoomspanExceptionTransformer;
import ai.loomspan.internal.core.ExecutionCoordinator;
import ai.loomspan.internal.core.InMemoryCapabilityRegistry;
import ai.loomspan.internal.core.SkillMethodBeanPostProcessor;
import ai.loomspan.internal.runtime.DefaultMissionExecutionEngine;
import ai.loomspan.internal.runtime.MissionExecutionEngine;
import ai.loomspan.internal.runtime.attachment.DefaultMissionInputMaterializer;
import ai.loomspan.internal.runtime.attachment.MissionInputMaterializer;
import ai.loomspan.internal.runtime.planning.DefaultPlanningService;
import ai.loomspan.internal.runtime.planning.PlanningService;
import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.runtime.input.SkillInputValidator;
import ai.loomspan.internal.runtime.state.DefaultExecutionStateService;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.tool.DefaultCapabilityInvoker;
import ai.loomspan.internal.runtime.tool.DefaultToolSurfaceService;
import ai.loomspan.internal.runtime.tool.ToolSurfaceService;
import ai.loomspan.internal.runtime.usage.DefaultSessionUsageService;
import ai.loomspan.internal.runtime.usage.MicrometerUsageMetricsRecorder;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.NoOpUsageMetricsRecorder;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import ai.loomspan.internal.runtime.usage.UsageMetricsRecorder;
import ai.loomspan.internal.security.AccessGuard;
import ai.loomspan.internal.security.DefaultAccessGuard;
import ai.loomspan.internal.skill.DefaultSkillVisibilityResolver;
import ai.loomspan.internal.skill.SkillVisibilityResolver;
import ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skillapi.DefaultSkillTemplate;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.internal.vfs.DefaultRefResolver;
import ai.loomspan.internal.vfs.RefResolver;
import ai.loomspan.internal.vfs.SessionLocalVirtualFileSystem;
import ai.loomspan.internal.vfs.VirtualFileSystem;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AutoConfiguration
@EnableConfigurationProperties({
        ExecutionTraceProperties.class,
        LoomspanProperties.class
})
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class LoomspanAutoConfiguration
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LoomspanAutoConfiguration.class);
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CapabilityRegistry capabilityRegistry()
    {
        return new InMemoryCapabilityRegistry();
    }


    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    LoomspanExceptionTransformer LoomspanExceptionTransformer()
    {
        return new DefaultLoomspanExceptionTransformer();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static SkillMethodBeanPostProcessor skillMethodBeanPostProcessor(
            CapabilityRegistry capabilityRegistry,
            LoomspanJacksonCodecs codecs,
            LoomspanExceptionTransformer LoomspanExceptionTransformer,
            SkillInputContractResolver skillInputContractResolver)
    {
        return SkillMethodBeanPostProcessor.create(
                capabilityRegistry,
                codecs.applicationConversion(),
                codecs.schemaTree(),
                LoomspanExceptionTransformer,
                skillInputContractResolver);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ObservabilityActivationCoordinator observabilityActivationCoordinator()
    {
        return new ObservabilityActivationCoordinator();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnNotWebApplication
    SmartInitializingSingleton observabilityNonWebActivation(
            LoomspanProperties properties,
            ObservabilityActivationCoordinator observabilityActivationCoordinator)
    {
        return unsupportedObservabilityActivation(properties, observabilityActivationCoordinator);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    SmartInitializingSingleton observabilityReactiveWebActivation(
            LoomspanProperties properties,
            ObservabilityActivationCoordinator observabilityActivationCoordinator)
    {
        return unsupportedObservabilityActivation(properties, observabilityActivationCoordinator);
    }

    private static SmartInitializingSingleton unsupportedObservabilityActivation(
            LoomspanProperties properties,
            ObservabilityActivationCoordinator observabilityActivationCoordinator)
    {
        return () ->
        {
            if (properties.getObservability().isEnabled())
            {
                LOGGER.warn("loomspan observability disabled: a servlet web application is required");
            }
            observabilityActivationCoordinator.disable();
        };
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    LoomspanSessionRunner LoomspanSessionRunner(LoomspanProperties properties,
            ExecutionTraceProperties executionTraceProperties,
            ObservabilityActivationCoordinator observabilityActivationCoordinator,
            LoomspanJacksonCodecs codecs)
    {
        return new LoomspanSessionRunner(
                properties.getSession().getMaxDepth(),
                executionTraceProperties.getPersistence(),
                Clock.systemUTC(),
                observabilityActivationCoordinator.observationFactory(),
                observabilityActivationCoordinator.completionRetention(),
                properties.getSession().getQuotas(),
                // The session factory carries the canonical role into every trace reader/writer it creates.
                codecs.canonicalTrace());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    YamlSkillCatalog yamlSkillCatalog(LoomspanProperties properties, LoomspanJacksonCodecs codecs)
    {
        // The catalog is the YAML discovery/loading boundary that downstream runtime beans build on.
        return new YamlSkillCatalog(properties, new org.springframework.core.io.support.PathMatchingResourcePatternResolver(),
                codecs.skillYaml());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    YamlSkillCapabilityRegistrar yamlSkillCapabilityRegistrar(CapabilityRegistry capabilityRegistry,
            SkillMethodBeanPostProcessor skillMethodBeanPostProcessor,
            YamlSkillCatalog yamlSkillCatalog,
            SkillInputContractResolver skillInputContractResolver)
    {
        return new YamlSkillCapabilityRegistrar(
                capabilityRegistry,
                skillMethodBeanPostProcessor,
                yamlSkillCatalog,
                skillInputContractResolver);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SkillInputContractResolver skillInputContractResolver(LoomspanJacksonCodecs codecs)
    {
        return new SkillInputContractResolver(codecs.applicationConversion());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SkillInputValidator skillInputValidator()
    {
        return new SkillInputValidator();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AccessGuard accessGuard(
            ObjectProvider<org.springframework.security.config.core.GrantedAuthorityDefaults> defaults,
            ObjectProvider<org.springframework.security.access.hierarchicalroles.RoleHierarchy> hierarchy)
    {
        return new DefaultAccessGuard(new ai.loomspan.internal.security.SkillRoleEvaluator(
                defaults.getIfAvailable(), hierarchy.getIfAvailable()));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SkillVisibilityResolver skillVisibilityResolver(YamlSkillCatalog yamlSkillCatalog,
            CapabilityRegistry capabilityRegistry,
            AccessGuard accessGuard)
    {
        return new DefaultSkillVisibilityResolver(yamlSkillCatalog, capabilityRegistry, accessGuard);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    VirtualFileSystem virtualFileSystem()
    {
        return new SessionLocalVirtualFileSystem(Paths.get(System.getProperty("java.io.tmpdir"), "loomspan-vfs"));
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    RefResolver refResolver(VirtualFileSystem virtualFileSystem)
    {
        return new DefaultRefResolver(virtualFileSystem);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    MissionInputMaterializer missionInputMaterializer(RefResolver refResolver,
            SkillInputContractResolver skillInputContractResolver,
            LoomspanProperties properties)
    {
        return new DefaultMissionInputMaterializer(
                refResolver,
                skillInputContractResolver,
                properties.getSession().getAttachments().getMaxSize());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    CapabilityExecutionRouter capabilityExecutionRouter(org.springframework.beans.factory.ObjectProvider<ExecutionCoordinator> executionCoordinatorProvider,
            AccessGuard accessGuard,
            SkillInputValidator skillInputValidator)
    {
        return new CapabilityExecutionRouter(
                executionCoordinatorProvider,
                accessGuard,
                skillInputValidator);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SkillTemplate skillTemplate(CapabilityRegistry capabilityRegistry,
            CapabilityExecutionRouter capabilityExecutionRouter,
            LoomspanSessionRunner LoomspanSessionRunner,
            LoomspanJacksonCodecs codecs,
            SkillInputValidator skillInputValidator,
            ObjectProvider<org.springframework.security.core.context.SecurityContextHolderStrategy> securityContextStrategy)
    {
        return new DefaultSkillTemplate(
                capabilityRegistry,
                capabilityExecutionRouter,
                LoomspanSessionRunner,
                codecs.applicationConversion(),
                skillInputValidator,
                securityContextStrategy.getIfAvailable());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ModelUsageExtractor modelUsageExtractor()
    {
        return new ModelUsageExtractor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    UsageMetricsRecorder usageMetricsRecorder(ObjectProvider<MeterRegistry> meterRegistryProvider)
    {
        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        return meterRegistry == null
                ? new NoOpUsageMetricsRecorder()
                : new MicrometerUsageMetricsRecorder(meterRegistry);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SessionUsageService sessionUsageService(LoomspanProperties properties,
            UsageMetricsRecorder usageMetricsRecorder)
    {
        return new DefaultSessionUsageService(properties.getSession().getQuotas(), usageMetricsRecorder);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ExecutionStateService executionStateService(SessionUsageService sessionUsageService)
    {
        return new DefaultExecutionStateService(Clock.systemUTC(), sessionUsageService);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PlanningService planningService(ExecutionStateService executionStateService,
            LoomspanJacksonCodecs codecs)
    {
        return new DefaultPlanningService(executionStateService,
                codecs.planningJson(), codecs.planningYaml());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ToolSurfaceService toolSurfaceService(SkillVisibilityResolver skillVisibilityResolver)
    {
        return new DefaultToolSurfaceService(skillVisibilityResolver);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    DefaultCapabilityInvoker capabilityInvoker(CapabilityExecutionRouter capabilityExecutionRouter,
            PlanningService planningService,
            ExecutionStateService executionStateService,
            SessionUsageService sessionUsageService,
            UsageMetricsRecorder usageMetricsRecorder)
    {
        return new DefaultCapabilityInvoker(
                capabilityExecutionRouter,
                planningService,
                executionStateService,
                sessionUsageService,
                usageMetricsRecorder);
    }

    @Bean(name = "LoomspanMissionExecutor", destroyMethod = "close")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ExecutorService LoomspanMissionExecutor()
    {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ai.loomspan.internal.runtime.MissionWorkExecutor missionWorkExecutor(
            ExecutionStateService executionStateService, LoomspanProperties properties,
            SessionUsageService sessionUsageService,
            @Qualifier("LoomspanMissionExecutor") ExecutorService missionExecutor)
    {
        return new ai.loomspan.internal.runtime.MissionWorkExecutor(
                executionStateService, properties.getSession().getMissionTimeout(), missionExecutor, sessionUsageService);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    MissionExecutionEngine missionExecutionEngine(PlanningService planningService,
            ExecutionStateService executionStateService,
            ai.loomspan.internal.runtime.MissionWorkExecutor missionWorkExecutor,
            MissionInputMaterializer missionInputMaterializer)
    {
        return new DefaultMissionExecutionEngine(
                planningService, executionStateService, missionWorkExecutor, missionInputMaterializer);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ai.loomspan.internal.runtime.step.StepLoopMissionExecutionEngine stepLoopMissionExecutionEngine(
            PlanningService planningService,
            ExecutionStateService executionStateService,
            CapabilityRegistry capabilityRegistry,
            YamlSkillCatalog yamlSkillCatalog,
            LoomspanProperties properties,
            SessionUsageService sessionUsageService,
            MissionInputMaterializer missionInputMaterializer,
            LoomspanJacksonCodecs codecs,
            @Qualifier("LoomspanMissionExecutor") ExecutorService LoomspanMissionExecutor)
    {
        return new ai.loomspan.internal.runtime.step.StepLoopMissionExecutionEngine(
                planningService,
                executionStateService,
                capabilityRegistry,
                yamlSkillCatalog,
                properties.getSession().getMissionTimeout(),
                LoomspanMissionExecutor,
                sessionUsageService,
                missionInputMaterializer,
                codecs.schemaTree());
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    ExecutionCoordinator executionCoordinator(YamlSkillCatalog yamlSkillCatalog,
            CapabilityRegistry capabilityRegistry,
            ai.loomspan.internal.model.ModelInteractionFactory modelInteractionFactory,
            ToolSurfaceService toolSurfaceService,
            ai.loomspan.internal.runtime.tool.CapabilityBindingFactory capabilityBindingFactory,
            MissionExecutionEngine missionExecutionEngine,
            ai.loomspan.internal.runtime.step.StepLoopMissionExecutionEngine stepLoopMissionExecutionEngine,
            ExecutionStateService executionStateService,
            AccessGuard accessGuard, RefResolver refResolver,
            ai.loomspan.internal.runtime.MissionWorkExecutor missionWorkExecutor,
            ObjectProvider<org.springframework.security.core.context.SecurityContextHolderStrategy> strategy)
    {
        return new ExecutionCoordinator(
                yamlSkillCatalog,
                capabilityRegistry,
                modelInteractionFactory,
                toolSurfaceService,
                capabilityBindingFactory,
                missionExecutionEngine,
                stepLoopMissionExecutionEngine,
                executionStateService,
                accessGuard, refResolver, new ai.loomspan.internal.security.ScopedAuthentication(strategy.getIfAvailable()), missionWorkExecutor);
    }

}
