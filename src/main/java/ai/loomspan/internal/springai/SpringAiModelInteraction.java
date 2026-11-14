package ai.loomspan.internal.springai;

import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.model.ModelInteraction;
import ai.loomspan.internal.model.ModelInteractionMode;
import ai.loomspan.internal.model.ModelInteractionRequest;
import ai.loomspan.internal.model.ModelInteractionResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.util.MimeTypeUtils;

import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;

public final class SpringAiModelInteraction implements ModelInteraction
{
    private final ChatClient chatClient;
    private final ModelInteractionMode mode;

    public SpringAiModelInteraction(ChatClient chatClient, ModelInteractionMode mode)
    {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    @Override
    public ModelInteractionResult call(ModelInteractionRequest interactionRequest)
    {
        try
        {
            return doCall(interactionRequest);
        }
        catch (org.springframework.ai.tool.execution.ToolExecutionException ex)
        {
            if (ex.getCause() instanceof RuntimeException runtimeException) throw runtimeException;
            throw ex;
        }
    }

    private ModelInteractionResult doCall(ModelInteractionRequest interactionRequest)
    {
        boolean exposeControl = mode == ModelInteractionMode.STANDARD && !interactionRequest.planning();
        String system = interactionRequest.systemPrompt();
        ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                .system(exposeControl ? system + PlannerInabilityTool.CALLER_GUIDANCE : system);
        if (interactionRequest.input().attachments().isEmpty())
        {
            request = request.user(interactionRequest.input().userText());
        }
        else
        {
            request = request.user(user ->
            {
                user.text(interactionRequest.input().userText());
                interactionRequest.input().attachments().forEach(attachment -> user.media(
                        MimeTypeUtils.parseMimeType(attachment.contentType()), attachment.resource()));
            });
        }
        if (exposeControl || !interactionRequest.capabilities().isEmpty())
        {
            var callbacks = new ArrayList<>(SpringAiToolCallbackAdapter.adapt(interactionRequest.capabilities()));
            if (exposeControl) callbacks.add(new PlannerInabilityTool());
            request = request.tools(callbacks);
        }
        ChatClientResponse response = request
                .advisors(spec ->
                {
                    spec.param(ModelTraceContext.REQUEST_CONTEXT_KEY, interactionRequest.traceContext());
                    if (interactionRequest.outputContract() != null)
                    {
                        spec.param(ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY,
                                interactionRequest.outputContract());
                    }
                    if (interactionRequest.planning())
                    {
                        spec.param(ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor.PLANNING_CALL_KEY, true);
                    }
                })
                .call()
                .chatClientResponse();
        String content = response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null
                ? ""
                : response.chatResponse().getResult().getOutput().getText();
        Map<String, Object> context = response.context() == null ? Map.of() : response.context();
        return new ModelInteractionResult(content, context);
    }
}
