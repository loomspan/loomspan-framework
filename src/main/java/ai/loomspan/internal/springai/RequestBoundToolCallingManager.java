package ai.loomspan.internal.springai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates model selections against the request surface without changing provider evidence. */
final class RequestBoundToolCallingManager implements ToolCallingManager {
    private final ToolCallingManager delegate;
    RequestBoundToolCallingManager(ToolCallingManager delegate) { this.delegate = Objects.requireNonNull(delegate); }

    @Override public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        boundCallbacks(options);
        return delegate.resolveToolDefinitions(options);
    }

    @Override public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        var repair = OrdinaryFinalOutputRepair.from(prompt);
        if (repair != null) repair.requireDispatchAllowed();
        var driver = prompt.getOptions() instanceof org.springframework.ai.anthropic.AnthropicChatOptions
                ? ai.loomspan.autoconfigure.AiDriver.ANTHROPIC
                : prompt.getOptions() instanceof org.springframework.ai.google.genai.GoogleGenAiChatOptions
                ? ai.loomspan.autoconfigure.AiDriver.GEMINI : ai.loomspan.autoconfigure.AiDriver.OPENAI;
        ai.loomspan.internal.provider.ProviderResponseStatusClassifier.requireAccepted(driver, response);
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options))
            throw new IllegalArgumentException("Tool calls require a bound callback surface");
        List<ToolCallback> callbacks = boundCallbacks(options);
        boolean controlBound = callbacks.stream().anyMatch(PlannerInabilityTool.class::isInstance);
        Set<String> bound = callbacks.stream().filter(callback -> !(callback instanceof PlannerInabilityTool))
                .map(callback -> callback.getToolDefinition().name())
                .collect(Collectors.toUnmodifiableSet());
        // Complete validation before delegating any side effect, including multi-call responses.
        AssistantMessage.ToolCall control = null;
        int selectionCount = 0;
        for (Generation generation : response.getResults()) {
            var original = generation == null ? null : generation.getOutput();
            if (original == null) throw new IllegalArgumentException("Missing assistant output in tool selection response");
            for (var part : original.getParts()) {
                if (!(part instanceof ToolCallPart tool) || StreamingParts.isPartial(part)) {
                    continue;
                }
                var call = tool.toolCall();
                selectionCount++;
                if (PlannerInabilityTool.NAME.equals(call.name())) {
                    if (!controlBound) throw new IllegalArgumentException("Unbound tool selection '" + call.name() + "'");
                    control = call;
                    continue;
                }
                if (call.name() == null || !bound.contains(call.name())) throw new IllegalArgumentException("Unbound tool selection '" + call.name() + "'");
                // Spring's delegate replaces blank arguments with {}; reject before the original root is lost.
                if (call.arguments() == null || call.arguments().isBlank())
                    throw new IllegalArgumentException("Tool arguments must contain exactly one JSON object");
            }
        }
        if (control != null) {
            if (selectionCount != 1) throw new IllegalArgumentException("Planner inability must be the only tool selection");
            throw PlannerInabilityTool.accept(control);
        }
        var result = delegate.executeToolCalls(prompt, response);
        if (repair != null) repair.completed(prompt, result);
        return result;
    }

    private static List<ToolCallback> boundCallbacks(ToolCallingChatOptions options) {
        List<ToolCallback> callbacks = Objects.requireNonNullElse(options.getToolCallbacks(), List.of());
        for (ToolCallback callback : callbacks) {
            if (PlannerInabilityTool.NAME.equals(callback.getToolDefinition().name())
                    && !(callback instanceof PlannerInabilityTool)) {
                throw new IllegalArgumentException("Reserved Framework control name is bound to an application callback");
            }
        }
        return callbacks;
    }
}
