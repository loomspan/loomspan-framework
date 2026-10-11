package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.springai.SpringAiProviderIntegration;
import ai.loomspan.internal.typesafe.TypeSafeClient;
import org.springframework.beans.factory.DisposableBean;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NamedAiConnectionRegistry
{
    private final Map<String, ProviderConnectionRuntime> connections;
    private final AtomicBoolean destroyed = new AtomicBoolean();

    public NamedAiConnectionRegistry(Map<String, LoomspanProperties.ConnectionProperties> connections,
            SpringAiProviderIntegration integration)
    {
        Objects.requireNonNull(connections, "connections must not be null");
        Objects.requireNonNull(integration, "integration must not be null");
        Map<String, ProviderConnectionRuntime> built = new LinkedHashMap<>();
        for (Map.Entry<String, LoomspanProperties.ConnectionProperties> entry : connections.entrySet())
        {
            String name = entry.getKey();
            LoomspanProperties.ConnectionProperties properties = entry.getValue();
            ProviderConnectionRuntime runtime = null;
            TypeSafeClient nativeClient = null;
            try
            {
                if (properties.getDriver() == ai.loomspan.autoconfigure.AiDriver.TYPESAFE)
                {
                    var retryPolicy = ai.loomspan.internal.provider.ProviderRetryPolicy.from(properties.getProviderRetry());
                    nativeClient = new TypeSafeClient(properties);
                    runtime = new ProviderConnectionRuntime(null, nativeClient, properties.getDriver(),
                            ai.loomspan.internal.provider.AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP,
                            retryPolicy, TypeSafeClient::translate);
                }
                else runtime = integration.create(name, properties);
                if (runtime.attemptOwnership() != ai.loomspan.internal.provider.AttemptOwnership.EXACT_ATTEMPT_OWNERSHIP
                        && properties.getProviderRetry().isEnabled())
                {
                    throw new SafeAiConnectionConfigurationException("loomspan.connections." + name
                            + ".provider-retry.enabled must be false because the configured client has opaque retries");
                }
                built.put(name, runtime);
            }
            catch (SafeAiConnectionConfigurationException ex)
            {
                closeUnregistered(runtime, nativeClient, ex);
                cleanupAfterConstructionFailure(built, ex);
                throw ex;
            }
            catch (RuntimeException ex)
            {
                IllegalStateException failure = new IllegalStateException(
                        "Failed to construct AI connection '" + name + "' for driver " + properties.getDriver()
                                + "; check " + constructionSettings(name, properties), ex);
                closeUnregistered(runtime, nativeClient, failure);
                cleanupAfterConstructionFailure(built, failure);
                throw failure;
            }
        }
        this.connections = Map.copyOf(built);
    }

    private static String constructionSettings(String name, LoomspanProperties.ConnectionProperties properties)
    {
        String connection = "loomspan.connections." + name;
        return properties.getDriver() == ai.loomspan.autoconfigure.AiDriver.GEMINI
                ? connection + ".api-key or " + connection + ".gemini.vertex-ai, "
                        + connection + ".gemini.project-id, "
                        + connection + ".gemini.location, and " + connection + ".gemini.credentials-uri"
                : connection + ".base-url";
    }

    ProviderConnectionRuntime get(String connectionName)
    {
        return connections.get(connectionName);
    }

    public Map<String, ProviderConnectionRuntime> asMap()
    {
        return connections;
    }

    public void destroy() throws Exception
    {
        if (!destroyed.compareAndSet(false, true)) return;
        Exception failure = destroyModels(connections);
        if (failure != null) throw failure;
    }

    private static void cleanupAfterConstructionFailure(Map<String, ProviderConnectionRuntime> built, RuntimeException failure)
    {
        Exception cleanup = destroyModels(built);
        if (cleanup != null && cleanup != failure) failure.addSuppressed(cleanup);
    }

    private static void closeUnregistered(ProviderConnectionRuntime runtime, TypeSafeClient nativeClient,
            RuntimeException failure)
    {
        if (runtime != null)
        {
            Exception cleanup = destroyModels(Map.of("unregistered", runtime));
            if (cleanup != null && cleanup != failure) failure.addSuppressed(cleanup);
        }
        else if (nativeClient != null)
        {
            try { nativeClient.close(); }
            catch (Exception cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
    }

    private static Exception destroyModels(Map<String, ProviderConnectionRuntime> models)
    {
        Exception failure = null;
        for (ProviderConnectionRuntime runtime : models.values())
        {
            Object model = runtime.nativeClient() != null ? runtime.nativeClient() : runtime.chatModel();
            try
            {
                if (model instanceof DisposableBean disposableBean) disposableBean.destroy();
                else if (model instanceof AutoCloseable closeable) closeable.close();
            }
            catch (Exception ex)
            {
                if (failure == null) failure = ex; else if (failure != ex) failure.addSuppressed(ex);
            }
        }
        return failure;
    }
}
