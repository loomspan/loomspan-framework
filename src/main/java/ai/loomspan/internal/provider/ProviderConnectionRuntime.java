package ai.loomspan.internal.provider;

import ai.loomspan.autoconfigure.AiDriver;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Objects;

public record ProviderConnectionRuntime(ChatModel chatModel, ai.loomspan.internal.typesafe.TypeSafeClient nativeClient, AiDriver driver,
        AttemptOwnership attemptOwnership, ProviderRetryPolicy retryPolicy,
        ProviderFailureTranslator failureTranslator)
{
    public ProviderConnectionRuntime
    {
        if ((chatModel == null) == (nativeClient == null))
            throw new IllegalArgumentException("Exactly one concrete provider resource is required");
        if ((driver == AiDriver.TYPESAFE) != (nativeClient != null))
            throw new IllegalArgumentException("Provider resource does not match driver");
        Objects.requireNonNull(driver, "driver must not be null");
        Objects.requireNonNull(attemptOwnership, "attemptOwnership must not be null");
        Objects.requireNonNull(retryPolicy, "retryPolicy must not be null");
        Objects.requireNonNull(failureTranslator, "failureTranslator must not be null");
    }

    public static ProviderConnectionRuntime chat(ChatModel model, AiDriver driver, AttemptOwnership ownership,
            ProviderRetryPolicy retry, ProviderFailureTranslator translator)
    {
        return new ProviderConnectionRuntime(model, null, driver, ownership, retry, translator);
    }

    public ChatModel requireChatModel()
    {
        if (chatModel == null) throw new IllegalStateException("Native evaluation connection cannot execute chat");
        return chatModel;
    }
}
