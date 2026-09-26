package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.autoconfigure.NamedAiConnectionRegistry;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Prepared provider resources and effective settings owned by one generation. */
public final class ExecutionRuntime implements AutoCloseable
{
    private final LoomspanProperties properties;
    private final TracePersistencePolicy tracePersistence;
    private final NamedAiConnectionRegistry registry;
    private final AtomicBoolean closed = new AtomicBoolean();

    public ExecutionRuntime(LoomspanProperties properties, TracePersistencePolicy tracePersistence,
            NamedAiConnectionRegistry registry)
    {
        this.properties = copy(Objects.requireNonNull(properties));
        this.tracePersistence = Objects.requireNonNull(tracePersistence);
        this.registry = Objects.requireNonNull(registry);
    }

    public LoomspanProperties properties() { return properties; }
    public TracePersistencePolicy tracePersistence() { return tracePersistence; }
    public NamedAiConnectionRegistry registry() { return registry; }
    public ProviderConnectionRuntime connection(String name) { return registry.asMap().get(name); }

    private static LoomspanProperties copy(LoomspanProperties source)
    {
        LoomspanProperties target = new LoomspanProperties();
        LoomspanProperties.Session originalSession = source.getSession();
        LoomspanProperties.Session session = new LoomspanProperties.Session();
        session.setMaxDepth(originalSession.getMaxDepth());
        session.setMissionTimeout(originalSession.getMissionTimeout());
        LoomspanProperties.Session.Quotas originalQuotas = originalSession.getQuotas();
        LoomspanProperties.Session.Quotas quotas = new LoomspanProperties.Session.Quotas();
        quotas.setMaxSkillInvocations(originalQuotas.getMaxSkillInvocations());
        quotas.setMaxToolInvocations(originalQuotas.getMaxToolInvocations());
        quotas.setMaxLinterRetries(originalQuotas.getMaxLinterRetries());
        quotas.setMaxModelCalls(originalQuotas.getMaxModelCalls());
        quotas.setMaxProviderAttempts(originalQuotas.getMaxProviderAttempts());
        quotas.setMaxUsageUnits(originalQuotas.getMaxUsageUnits());
        session.setQuotas(quotas);
        LoomspanProperties.Session.Attachments attachments = new LoomspanProperties.Session.Attachments();
        attachments.setMaxSize(originalSession.getAttachments().getMaxSize());
        session.setAttachments(attachments);
        target.setSession(session);
        LoomspanProperties.Skills skills = new LoomspanProperties.Skills();
        skills.setLocations(source.getSkills().getLocations());
        target.setSkills(skills);
        var copiedConnections = new LinkedHashMap<String, LoomspanProperties.ConnectionProperties>();
        source.getConnections().forEach((name, original) -> {
            LoomspanProperties.ConnectionProperties connection = new LoomspanProperties.ConnectionProperties();
            connection.setDriver(original.getDriver());
            connection.setBaseUrl(original.getBaseUrl());
            connection.setApiKey(original.getApiKey());
            connection.setHeaders(original.getHeaders());
            if (original.getOpenai() != null)
            {
                LoomspanProperties.OpenAiOptions options = new LoomspanProperties.OpenAiOptions();
                options.setCompatibilityProfile(original.getOpenai().getCompatibilityProfile());
                options.setOrganizationId(original.getOpenai().getOrganizationId());
                options.setProjectId(original.getOpenai().getProjectId());
                connection.setOpenai(options);
            }
            if (original.getGemini() != null)
            {
                LoomspanProperties.GeminiOptions options = new LoomspanProperties.GeminiOptions();
                options.setVertexAi(original.getGemini().getVertexAi());
                options.setProjectId(original.getGemini().getProjectId());
                options.setLocation(original.getGemini().getLocation());
                options.setCredentialsUri(original.getGemini().getCredentialsUri());
                connection.setGemini(options);
            }
            LoomspanProperties.ProviderRetryProperties retry = new LoomspanProperties.ProviderRetryProperties();
            retry.setEnabled(original.getProviderRetry().isEnabled());
            retry.setMaxAttempts(original.getProviderRetry().getMaxAttempts());
            retry.setInitialBackoff(original.getProviderRetry().getInitialBackoff());
            retry.setMultiplier(original.getProviderRetry().getMultiplier());
            retry.setMaxBackoff(original.getProviderRetry().getMaxBackoff());
            retry.setJitter(original.getProviderRetry().getJitter());
            connection.setProviderRetry(retry);
            copiedConnections.put(name, connection);
        });
        target.setConnections(copiedConnections);
        var copiedModels = new LinkedHashMap<String, LoomspanProperties.ModelCatalogEntry>();
        source.getModels().forEach((name, original) -> {
            LoomspanProperties.ModelCatalogEntry model = new LoomspanProperties.ModelCatalogEntry();
            model.setConnection(original.getConnection());
            model.setProviderModel(original.getProviderModel());
            model.setThinkingLevels(original.getThinkingLevels());
            copiedModels.put(name, model);
        });
        target.setModels(copiedModels);
        return target;
    }

    @Override public void close()
    {
        if (!closed.compareAndSet(false, true)) return;
        try { registry.destroy(); }
        catch (Exception ex) { throw new IllegalStateException("Failed to close generation provider resources"); }
    }
}
