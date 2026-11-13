package ai.loomspan.internal.springai;

import ai.loomspan.internal.core.SkillIdentity;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates model selections against the request surface without changing provider evidence. */
final class CanonicalToolCallingManager implements ToolCallingManager {
    private final ToolCallingManager delegate;
    CanonicalToolCallingManager(ToolCallingManager delegate) { this.delegate = Objects.requireNonNull(delegate); }

    @Override public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions options) {
        List<ToolCallback> callbacks = boundCallbacks(options);
        callbacks.stream().filter(callback -> !(callback instanceof PlannerInabilityTool))
                .forEach(callback -> requireCanonical(callback.getToolDefinition().name()));
        var definitions = delegate.resolveToolDefinitions(options);
        boolean controlBound = callbacks.stream().anyMatch(PlannerInabilityTool.class::isInstance);
        definitions.forEach(definition -> {
            if (!(controlBound && PlannerInabilityTool.NAME.equals(definition.name()))) requireCanonical(definition.name());
        });
        return definitions;
    }

    @Override public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse response) {
        if (!(prompt.getOptions() instanceof ToolCallingChatOptions options))
            throw new IllegalArgumentException("Tool calls require a bound callback surface");
        List<ToolCallback> callbacks = boundCallbacks(options);
        boolean controlBound = callbacks.stream().anyMatch(PlannerInabilityTool.class::isInstance);
        Set<String> bound = callbacks.stream().filter(callback -> !(callback instanceof PlannerInabilityTool))
                .map(callback -> callback.getToolDefinition().name())
                .peek(CanonicalToolCallingManager::requireCanonical).collect(Collectors.toUnmodifiableSet());
        // Complete validation before delegating any side effect, including multi-call responses.
        List<Generation> generations = new ArrayList<>(response.getResults().size());
        boolean changedResponse = false;
        AssistantMessage.ToolCall control = null;
        int selectionCount = 0;
        for (Generation generation : response.getResults()) {
            var original = generation == null ? null : generation.getOutput();
            if (original == null) throw new IllegalArgumentException("Missing assistant output in tool selection response");
            List<MessagePart> parts = new ArrayList<>(original.getParts().size());
            boolean changed = false;
            for (var part : original.getParts()) {
                if (!(part instanceof ToolCallPart tool) || StreamingParts.isPartial(part)) {
                    parts.add(part);
                    continue;
                }
                var call = tool.toolCall();
                selectionCount++;
                if (PlannerInabilityTool.NAME.equals(call.name())) {
                    if (!controlBound) throw new IllegalArgumentException("Unbound tool selection '" + call.name() + "'");
                    control = call;
                    continue;
                }
                String canonical = SkillIdentity.parse(call.name()).toString();
                if (!bound.contains(canonical)) throw new IllegalArgumentException("Unbound tool selection '" + call.name() + "'");
                if (canonical.equals(call.name())) parts.add(part);
                else {
                    parts.add(new ToolCallPart(new AssistantMessage.ToolCall(call.id(), call.type(), canonical,
                            call.arguments()), tool.payload(), tool.attributes()));
                    changed = true;
                }
            }
            generations.add(changed ? new Generation(AssistantMessage.builder().parts(parts)
                    .properties(original.getMetadata()).build(), generation.getMetadata()) : generation);
            changedResponse |= changed;
        }
        if (control != null) {
            if (selectionCount != 1) throw new IllegalArgumentException("Planner inability must be the only tool selection");
            throw PlannerInabilityTool.accept(control);
        }
        return delegate.executeToolCalls(prompt, changedResponse ? new ChatResponse(generations, response.getMetadata()) : response);
    }

    private static List<ToolCallback> boundCallbacks(ToolCallingChatOptions options) {
        return Objects.requireNonNullElse(options.getToolCallbacks(), List.of());
    }

    private static void requireCanonical(String name) {
        if (!SkillIdentity.parse(name).toString().equals(name))
            throw new IllegalArgumentException("Bound tool name must be canonical: '" + name + "'");
    }
}
