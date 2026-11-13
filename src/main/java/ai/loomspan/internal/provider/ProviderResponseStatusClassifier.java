package ai.loomspan.internal.provider;

import ai.loomspan.autoconfigure.AiDriver;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.responses.OpenAiResponsesMetadata;

import java.util.Set;

/** Reads explicit, pinned adapter metadata only. Missing/unknown values and prose are not policy. */
public final class ProviderResponseStatusClassifier
{
    private ProviderResponseStatusClassifier() { }

    public static String rejection(AiDriver driver, ChatResponse response)
    {
        if (response == null) return null;
        if (driver == AiDriver.OPENAI && response.getMetadata() != null
                && "incomplete".equals(response.getMetadata().get(OpenAiResponsesMetadata.STATUS)))
            return "incomplete";
        for (var generation : response.getResults())
        {
            if (generation == null) continue;
            var metadata = generation.getMetadata();
            String finish = metadata == null ? "" : java.util.Objects.toString(metadata.getFinishReason(), "");
            if (driver == AiDriver.OPENAI)
            {
                if (metadata != null)
                {
                    Object refusal = metadata.get(OpenAiResponsesMetadata.REFUSAL);
                    if (refusal instanceof String text && !text.isEmpty()) return "refusal";
                    if ("incomplete".equals(metadata.get(OpenAiResponsesMetadata.STATUS))) return "incomplete";
                }
                if (Set.of("LENGTH", "CONTENT_FILTER", "INCOMPLETE").contains(finish)) return finish;
                if (generation.getOutput() != null && generation.getOutput().getParts().stream()
                        .anyMatch(part -> "incomplete".equals(part.attributes().get(OpenAiResponsesMetadata.ITEM_STATUS_ATTRIBUTE))))
                    return "incomplete output item";
            }
            else if (driver == AiDriver.ANTHROPIC
                    && Set.of("max_tokens", "model_context_window_exceeded", "pause_turn", "refusal").contains(finish))
                return finish;
            // Pinned FinishReason.Known documents these as generation cutoffs/content filtering.
            // OTHER, unspecified, and malformed function calls do not establish a status policy.
            else if (driver == AiDriver.GEMINI && Set.of("MAX_TOKENS", "SAFETY", "RECITATION", "LANGUAGE",
                    "BLOCKLIST", "PROHIBITED_CONTENT", "SPII").contains(finish))
                return finish;
        }
        return null;
    }

    public static void requireAccepted(AiDriver driver, ChatResponse response)
    {
        String reason = rejection(driver, response);
        if (reason != null) throw new ProviderResponseRejectedException(reason);
    }
}
