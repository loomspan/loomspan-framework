package ai.loomspan.internal.model;

import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.runtime.attachment.RenderedMissionInput;
import ai.loomspan.internal.runtime.tool.BoundCapability;

import java.util.List;
import java.util.Objects;

public record ModelInteractionRequest(
        String systemPrompt,
        RenderedMissionInput input,
        ModelTraceContext traceContext,
        List<BoundCapability> capabilities,
        boolean planning)
{
    public ModelInteractionRequest
    {
        systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        input = Objects.requireNonNull(input, "input must not be null");
        traceContext = Objects.requireNonNull(traceContext, "traceContext must not be null");
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
