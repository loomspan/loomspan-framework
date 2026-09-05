package ai.loomspan.internal.observability.web;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.autoconfigure.ExecutionTraceProperties;
import ai.loomspan.internal.observability.ObservabilityActivationCoordinator;
import ai.loomspan.internal.observability.ObservabilityRuntime;
import ai.loomspan.internal.runtime.observation.DefaultExecutionObservationHandleFactory;
import ai.loomspan.internal.runtime.observation.InMemoryActiveExecutionRegistry;
import ai.loomspan.internal.runtime.observation.InMemoryActivityReplayBuffer;
import ai.loomspan.internal.runtime.observation.LiveActivityProjector;
import ai.loomspan.internal.runtime.observation.LiveMonitoringAvailability;
import ai.loomspan.internal.runtime.observation.catalog.DefaultRegisteredSkillCatalog;
import ai.loomspan.internal.runtime.observation.catalog.InMemoryFinalizedTraceCatalog;
import ai.loomspan.internal.runtime.trace.ScheduledCompletionGraceRetention;
import ai.loomspan.internal.skill.YamlSkillCatalog;
import ai.loomspan.internal.skill.YamlSkillCapabilityRegistrar;
import ai.loomspan.internal.core.CapabilityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ObservabilityRouteRegistrar implements SmartInitializingSingleton, DisposableBean
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityRouteRegistrar.class);
    private final RequestMappingHandlerMapping mappings;
    private final ObservabilityRestController controller;
    private final ObservabilityRouteCollisionDetector collisionDetector;
    private final ObservabilityActivationCoordinator activation;
    private final LoomspanProperties properties;
    private final ExecutionTraceProperties traceProperties;
    private final YamlSkillCatalog yamlSkills;
    private final CapabilityRegistry registry;
    private final YamlSkillCapabilityRegistrar registrar;
    private final ObservabilityDtoMapper dtoMapper;
    private final ObservabilityJsonCodec json;
    private final List<RequestMappingInfo> registered = new ArrayList<>();

    public ObservabilityRouteRegistrar(
            RequestMappingHandlerMapping mappings,
            ObservabilityRestController controller,
            ObservabilityRouteCollisionDetector collisionDetector,
            ObservabilityActivationCoordinator activation,
            LoomspanProperties properties,
            ExecutionTraceProperties traceProperties,
            CapabilityRegistry registry,
            YamlSkillCatalog yamlSkills,
            YamlSkillCapabilityRegistrar registrar,
            ObservabilityDtoMapper dtoMapper,
            ObservabilityJsonCodec json)
    {
        this.mappings = mappings;
        this.controller = controller;
        this.collisionDetector = collisionDetector;
        this.activation = activation;
        this.properties = properties;
        this.traceProperties = traceProperties;
        this.yamlSkills = yamlSkills;
        this.registry = registry;
        this.registrar = registrar;
        this.dtoMapper = dtoMapper;
        this.json = json;
    }

    @Override
    public void afterSingletonsInstantiated()
    {
        LoomspanProperties.Observability configuration = properties.getObservability();
        String invalid = validate(configuration);
        if (!configuration.isEnabled())
        {
            activation.disable();
            return;
        }
        if (mappings == null)
        {
            LOGGER.warn("loomspan observability disabled: Spring MVC infrastructure is required");
            activation.disable();
            return;
        }
        if (invalid != null)
        {
            LOGGER.warn("loomspan observability disabled: {}", invalid);
            activation.disable();
            return;
        }
        if (collisionDetector.hasCollision())
        {
            LOGGER.warn("loomspan observability disabled: reserved route namespace collides with an application mapping");
            activation.disable();
            return;
        }

        ObservabilityRuntime runtime = null;
        try
        {
            runtime = createRuntime(configuration);
            registerGet(ObservabilityApiPaths.INSTANCE, "instance",
                    jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.SKILLS, "skills", jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.SKILLS + "/{registeredName}", "skill",
                    String.class, jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.ACTIVE, "active", jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.ACTIVE + "/{sessionId}", "active",
                    String.class, jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.ACTIVITY, "activity",
                    jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class);
            registerGet(ObservabilityApiPaths.TRACES, "traces", jakarta.servlet.http.HttpServletRequest.class);
            registerGet(ObservabilityApiPaths.TRACE_ARTIFACT, "artifact",
                    String.class, jakarta.servlet.http.HttpServletRequest.class,
                    jakarta.servlet.http.HttpServletResponse.class);
            registerGet(ObservabilityApiPaths.TRACES + "/{traceId}", "trace",
                    String.class, jakarta.servlet.http.HttpServletRequest.class);
            registerFallback();
            activation.enable(runtime);
        }
        catch (RuntimeException ex)
        {
            rollback();
            if (runtime != null) runtime.close();
            activation.disable();
            LOGGER.warn("loomspan observability disabled: route activation failed");
        }
    }

    ObservabilityRuntime createRuntime(LoomspanProperties.Observability configuration)
    {
        Clock clock = Clock.systemUTC();
        var active = new InMemoryActiveExecutionRegistry();
        var replay = new InMemoryActivityReplayBuffer();
        var live = new LiveMonitoringAvailability();
        var skills = new DefaultRegisteredSkillCatalog(registry, yamlSkills, registrar);
        UUID instanceId = UUID.randomUUID();
        InMemoryFinalizedTraceCatalog traces = null;
        ScheduledCompletionGraceRetention grace = null;
        ObservabilityActivityDelivery delivery = null;
        ObservabilityArtifactDelivery artifactDelivery = null;
        try
        {
            grace = new ScheduledCompletionGraceRetention(configuration.getCompletionGraceTtl());
            traces = new InMemoryFinalizedTraceCatalog(
                    configuration.getTraceCatalogMetadataTtl(), clock, grace);
            delivery = new ObservabilityActivityDelivery(instanceId.toString(), replay, live, dtoMapper, json);
            artifactDelivery = new ObservabilityArtifactDelivery();
            var observation = new DefaultExecutionObservationHandleFactory(
                    new LiveActivityProjector(), active, replay, live, traces, delivery);
            return new ObservabilityRuntime(
                    instanceId, clock, observation, delivery, artifactDelivery, grace,
                    active, replay, live, skills, traces,
                    configuration, properties.getSession().getQuotas(), traceProperties.getPersistence());
        }
        catch (RuntimeException | Error failure)
        {
            closeAfterFailure(delivery, failure);
            closeAfterFailure(artifactDelivery, failure);
            closeAfterFailure(grace, failure);
            closeAfterFailure(traces, failure);
            throw failure;
        }
    }

    private static void closeAfterFailure(AutoCloseable resource, Throwable failure)
    {
        if (resource == null) return;
        try
        {
            resource.close();
        }
        catch (Exception closeFailure)
        {
            failure.addSuppressed(closeFailure);
        }
    }

    private void registerGet(String path, String methodName, Class<?>... parameterTypes)
    {
        try
        {
            Method method = ObservabilityRestController.class.getMethod(methodName, parameterTypes);
            RequestMappingInfo info = RequestMappingInfo.paths(path)
                    .methods(RequestMethod.GET)
                    .options(mappings.getBuilderConfiguration())
                    .build();
            mappings.registerMapping(info, controller, method);
            registered.add(info);
        }
        catch (NoSuchMethodException ex)
        {
            throw new IllegalStateException("Observability handler definition is invalid", ex);
        }
    }

    private void registerFallback()
    {
        try
        {
            Method method = ObservabilityRestController.class.getMethod(
                    "fallback", jakarta.servlet.http.HttpServletRequest.class);
            RequestMappingInfo info = RequestMappingInfo.paths(
                            ObservabilityApiPaths.ROOT, ObservabilityApiPaths.ROOT + "/**")
                    .options(mappings.getBuilderConfiguration())
                    .build();
            mappings.registerMapping(info, controller, method);
            registered.add(info);
        }
        catch (NoSuchMethodException ex)
        {
            throw new IllegalStateException("Observability fallback definition is invalid", ex);
        }
    }

    static String validate(LoomspanProperties.Observability configuration)
    {
        String key = configuration.getAuth().getApiKey();
        if (key == null || key.length() < 32 || key.length() > 512) return "API key must contain 32 to 512 characters";
        for (int i = 0; i < key.length(); i++)
        {
            char value = key.charAt(i);
            if (value < 0x21 || value > 0x7e) return "API key must use printable non-whitespace ASCII";
        }
        Duration grace = configuration.getCompletionGraceTtl();
        Duration metadata = configuration.getTraceCatalogMetadataTtl();
        if (grace == null || grace.isNegative()) return "completion grace TTL must not be negative";
        try
        {
            grace.toNanos();
        }
        catch (ArithmeticException ex)
        {
            return "completion grace TTL is too large";
        }
        if (metadata == null || metadata.isZero() || metadata.isNegative())
            return "trace catalog metadata TTL must be positive";
        try
        {
            metadata.toNanos();
        }
        catch (ArithmeticException ex)
        {
            return "trace catalog metadata TTL is too large";
        }
        return null;
    }

    private void rollback()
    {
        if (mappings == null) return;
        for (int i = registered.size() - 1; i >= 0; i--)
        {
            mappings.unregisterMapping(registered.get(i));
        }
        registered.clear();
    }

    @Override
    public void destroy()
    {
        rollback();
        activation.close();
    }
}
