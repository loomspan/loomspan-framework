package ai.loomspan.internal.springai;

import ai.loomspan.internal.core.SkillIdentity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates model selections against the request surface without changing provider evidence. */
final class CanonicalToolCallingManager implements ToolCallingManager {
    private final ToolCallingManager delegate;
    CanonicalToolCallingManager(ToolCallingManager delegate) { this.delegate = Objects.requireNonNull(delegate); }

    @Override public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        var definitions = delegate.resolveToolDefinitions(options);
        definitions.forEach(definition -> requireCanonical(definition.name()));
        return definitions;
    }

    @Override public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options))
            throw new IllegalArgumentException("Tool calls require a bound callback surface");
        Set<String> bound = options.getToolCallbacks().stream().map(callback -> callback.getToolDefinition().name())
                .peek(CanonicalToolCallingManager::requireCanonical).collect(Collectors.toUnmodifiableSet());
        // Complete validation before delegating any side effect, including multi-call responses.
        var generations = response.getResults().stream().map(generation -> {
            var original = generation.getOutput();
            var calls = original.getToolCalls().stream().map(call -> {
                String canonical = SkillIdentity.parse(call.name()).toString();
                if (!bound.contains(canonical)) throw new IllegalArgumentException("Unbound tool selection '" + call.name() + "'");
                return new AssistantMessage.ToolCall(call.id(), call.type(), canonical, call.arguments());
            }).toList();
            return new Generation(AssistantMessage.builder().content(original.getText()).properties(original.getMetadata())
                    .media(original.getMedia()).toolCalls(calls).build(), generation.getMetadata());
        }).toList();
        return delegate.executeToolCalls(prompt, new ChatResponse(generations, response.getMetadata()));
    }

    private static void requireCanonical(String name) {
        if (!SkillIdentity.parse(name).toString().equals(name))
            throw new IllegalArgumentException("Bound tool name must be canonical: '" + name + "'");
    }
}
