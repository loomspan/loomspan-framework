package ai.loomspan.internal.runtime.prompt;

import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public record SkillPromptComposition(
        String systemPrompt,
        boolean skillPromptPresent,
        @Nullable String skillPrompt,
        String promptComposition)
{
    public Map<String, Object> traceMetadata()
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("skillPromptPresent", skillPromptPresent);
        if (skillPromptPresent)
        {
            metadata.put("skillPrompt", skillPrompt);
        }
        metadata.put("promptComposition", promptComposition);
        return Map.copyOf(metadata);
    }
}
