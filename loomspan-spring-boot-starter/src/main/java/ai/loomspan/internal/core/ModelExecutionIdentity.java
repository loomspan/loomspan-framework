package ai.loomspan.internal.core;

import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.internal.skill.EffectiveSkillExecutionConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ModelExecutionIdentity(
        String frameworkModel,
        String connection,
        AiDriver driver,
        String providerModel)
{
    public ModelExecutionIdentity
    {
        frameworkModel = requireNonBlank(frameworkModel, "frameworkModel");
        connection = requireNonBlank(connection, "connection");
        driver = Objects.requireNonNull(driver, "driver must not be null");
        providerModel = requireNonBlank(providerModel, "providerModel");
    }

    public static ModelExecutionIdentity from(EffectiveSkillExecutionConfiguration configuration)
    {
        Objects.requireNonNull(configuration, "configuration must not be null");
        return new ModelExecutionIdentity(configuration.frameworkModel(), configuration.connection(),
                configuration.driver(), configuration.providerModel());
    }

    public Map<String, Object> metadata()
    {
        return Map.of(
                "frameworkModel", frameworkModel,
                "connection", connection,
                "driver", driver.name(),
                "providerModel", providerModel);
    }

    public Map<String, Object> metadata(String contextKey, Object contextValue)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(metadata());
        metadata.put(contextKey, contextValue);
        return Map.copyOf(metadata);
    }

    private static String requireNonBlank(String value, String field)
    {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
