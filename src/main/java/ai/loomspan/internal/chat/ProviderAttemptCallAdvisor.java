package ai.loomspan.internal.chat;

import ai.loomspan.internal.core.ExecutionFrame;
import ai.loomspan.internal.core.ExecutionBinding;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.provider.ProviderConnectionRuntime;
import ai.loomspan.internal.provider.ProviderFailureDetails;
import ai.loomspan.internal.provider.ProviderFailureGuidance;
import ai.loomspan.internal.provider.ProviderRetryDecider;
import ai.loomspan.internal.provider.ProviderRetryOutcome;
import ai.loomspan.internal.provider.ProviderResponseEvidence;
import ai.loomspan.internal.provider.ProviderResponseStatusClassifier;
import ai.loomspan.internal.provider.ProviderResponseRejectedException;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.ModelUsageExtractor;
import ai.loomspan.internal.runtime.usage.ModelUsageRecord;
import ai.loomspan.internal.runtime.usage.SessionUsageService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.part.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public final class ProviderAttemptCallAdvisor implements CallAdvisor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderAttemptCallAdvisor.class);
    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 1;
    private final ProviderConnectionRuntime runtime;
    private final ExecutionStateService executionStateService;
    private final ModelUsageExtractor modelUsageExtractor;
    private final SessionUsageService sessionUsageService;
    private final ProviderRetryDecider retryDecider = new ProviderRetryDecider();

    public ProviderAttemptCallAdvisor(ProviderConnectionRuntime runtime, ExecutionStateService executionStateService,
            ModelUsageExtractor modelUsageExtractor, SessionUsageService sessionUsageService)
    {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.executionStateService = Objects.requireNonNull(executionStateService, "executionStateService must not be null");
        this.modelUsageExtractor = Objects.requireNonNull(modelUsageExtractor, "modelUsageExtractor must not be null");
        this.sessionUsageService = Objects.requireNonNull(sessionUsageService, "sessionUsageService must not be null");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain)
    {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(chain, "chain must not be null");
        Object rawContext = request.context().get(ModelTraceContext.REQUEST_CONTEXT_KEY);
        if (!(rawContext instanceof ModelTraceContext context))
            throw new IllegalStateException("loomspan model call is missing its call-local trace context");

        request = ai.loomspan.internal.springai.NativeOutputContract.prepare(request, runtime.driver(), context);

        ExecutionBinding binding = ExecutionBindingScope.requireCurrent();
        LoomspanSession session = binding.session();
        ExecutionFrame frame = binding.branch().requireLeaf();
        Map<String, Object> requestPayload = requestPayload(request);
        for (int providerAttempt = 1; providerAttempt <= runtime.retryPolicy().maxAttempts(); providerAttempt++)
        {
            requireNotInterrupted();
            sessionUsageService.reserveProviderAttempt(session, context.skillName());
            Map<String, Object> attempt = context.nextAttempt(providerAttempt);
            executionStateService.recordModelRequestSent(session, frame, context, attempt, requestPayload);

            ChatClientResponse response;
            try
            {
                response = chain.copy(this).nextCall(request);
            }
            catch (RuntimeException failure)
            {
                ProviderResponseEvidence evidence = ProviderResponseEvidence.find(failure);
                if (evidence != null && !evidence.responseEvidence().isEmpty())
                {
                    ModelUsageRecord usage = evidence.responseUsage() == null
                            ? new ModelUsageRecord(0, 0, 0, ai.loomspan.internal.runtime.usage.UsagePrecision.UNAVAILABLE, null)
                            : evidence.responseUsage();
                    executionStateService.recordModelResponseReceived(session, frame, context, attempt, usage,
                            evidence.responseEvidence());
                    try
                    {
                        sessionUsageService.recordModelResponse(session, context.skillName(), context.identity(), usage);
                    }
                    catch (RuntimeException accountingFailure)
                    {
                        recordTerminalFailure(session, frame, context, attempt, accountingFailure);
                        throw accountingFailure;
                    }
                }
                ProviderFailureDetails details = runtime.failureTranslator().translate(failure);
                ProviderRetryOutcome outcome = retryDecider.decide(runtime.retryPolicy(), details, providerAttempt);
                ProviderFailureGuidance.Guidance guidance = ProviderFailureGuidance.explain(context.identity(), details);
                List<Map<String, Object>> diagnostics = new java.util.ArrayList<>(details.diagnostics().size() + 1);
                diagnostics.add(guidance.diagnostic());
                diagnostics.addAll(details.diagnostics());
                sessionUsageService.recordProviderAttemptOutcome(session, context.skillName(), context.identity(), "failed",
                        details.category(), outcome.decision());
                executionStateService.recordModelAttemptFailed(session, frame, context, attempt,
                        failureMetadata(details, outcome), failure, List.copyOf(diagnostics));
                if (outcome.decision() != ai.loomspan.internal.provider.ProviderRetryDecision.RETRY)
                {
                    LOGGER.warn("{}", guidance.text());
                    executionStateService.registerProviderFailure(session, failure, attempt);
                    throw failure;
                }
                waitInterruptibly(outcome.delay());
                continue;
            }

            ChatClientResponse linkedResponse = (response == null ? ChatClientResponse.builder() : response.mutate())
                    .context(ModelTraceContext.RESPONSE_ATTEMPT_CONTEXT_KEY, attempt).build();
            String rejection = ProviderResponseStatusClassifier.rejection(runtime.driver(), linkedResponse.chatResponse());
            String responseText = responseText(linkedResponse);
            ModelUsageRecord usage = modelUsageExtractor.extract(linkedResponse.chatResponse(),
                    request.prompt().getInstructions(), responseText);
            executionStateService.recordModelResponseReceived(session, frame, context, attempt, usage,
                    responsePayload(linkedResponse.chatResponse()));
            try
            {
                sessionUsageService.recordModelResponse(session, context.skillName(), context.identity(), usage);
            }
            catch (RuntimeException accountingFailure)
            {
                recordTerminalFailure(session, frame, context, attempt, accountingFailure);
                throw accountingFailure;
            }
            if (rejection != null)
            {
                var failure = new ProviderResponseRejectedException(rejection);
                recordTerminalFailure(session, frame, context, attempt, failure);
                throw failure;
            }
            sessionUsageService.recordProviderAttemptOutcome(session, context.skillName(), context.identity(), "succeeded", null, null);
            return linkedResponse;
        }
        throw new IllegalStateException("provider retry loop exhausted without a terminal result");
    }

    private void recordTerminalFailure(LoomspanSession session, ExecutionFrame frame, ModelTraceContext context,
            Map<String, Object> attempt, RuntimeException failure)
    {
        var details = new ProviderFailureDetails(ai.loomspan.internal.provider.ProviderFailureClassification.PERMANENT,
                ai.loomspan.internal.provider.ProviderFailureCategory.UNKNOWN,
                null, null, null, null, failure.getMessage(), List.of());
        var outcome = ProviderRetryOutcome.doNotRetry();
        sessionUsageService.recordProviderAttemptOutcome(session, context.skillName(), context.identity(), "failed",
                details.category(), outcome.decision());
        executionStateService.recordModelAttemptFailed(session, frame, context, attempt,
                failureMetadata(details, outcome), failure, List.of());
        executionStateService.registerProviderFailure(session, failure, attempt);
    }

    private static Map<String, Object> failureMetadata(ProviderFailureDetails details, ProviderRetryOutcome outcome)
    {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("failureClassification", details.classification().name());
        metadata.put("failureCategory", details.category().name());
        metadata.put("retryDecision", outcome.decision().name());
        metadata.put("retryDelayMillis", outcome.delay().toMillis());
        metadata.put("retryDelaySource", outcome.delaySource().name());
        if (details.httpStatus() != null) metadata.put("httpStatus", details.httpStatus());
        if (details.providerErrorType() != null) metadata.put("providerErrorType", details.providerErrorType());
        if (details.providerErrorCode() != null) metadata.put("providerErrorCode", details.providerErrorCode());
        if (details.summary() != null) metadata.put("summary", details.summary());
        return Map.copyOf(metadata);
    }

    private static void waitInterruptibly(Duration delay)
    {
        try
        {
            if (!delay.isZero()) Thread.sleep(delay.toMillis());
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new CancellationException("Provider retry interrupted");
        }
        requireNotInterrupted();
    }

    private static void requireNotInterrupted()
    {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider retry interrupted");
    }

    @Override public String getName() { return "LoomspanProviderAttemptCallAdvisor"; }
    @Override public int getOrder() { return ORDER; }

    private Map<String, Object> requestPayload(ChatClientRequest request)
    {
        List<Map<String, Object>> messages = request.prompt().getInstructions().stream().map(this::messagePayload).toList();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("messages", messages);
        Object contract = request.context().get(ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor.OUTPUT_CONTRACT_KEY);
        if (contract instanceof ai.loomspan.internal.schema.CanonicalSchema canonical)
        {
            payload.put("canonicalGenerationSchema", canonical.tree());
            // This is the prepared native option, not a claim about SDK wire fidelity (PR30-B01/B04).
            payload.put("preparedNativeSchema", request.context().get(ai.loomspan.internal.springai.NativeOutputContract.PREPARED_SCHEMA_KEY));
        }
        return Collections.unmodifiableMap(payload);
    }

    private Map<String, Object> messagePayload(Message message)
    {
        if (message == null) return null;
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", message.getMessageType().name());
        payload.put("text", message.getText());
        payload.put("metadata", message.getMetadata());
        payload.put("parts", message instanceof AbstractMessage rich
                ? rich.getParts().stream().map(this::partPayload).toList() : List.of());
        if (message instanceof AssistantMessage assistant)
        {
            payload.put("toolCalls", assistant.getToolCalls().stream().map(call -> {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("id", call.id());
                entry.put("name", call.name());
                entry.put("arguments", call.arguments());
                return Collections.unmodifiableMap(entry);
            }).toList());
        }
        else if (message instanceof ToolResponseMessage tool)
        {
            payload.put("toolResponses", tool.getResponses().stream().map(response -> {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("id", response.id());
                entry.put("name", response.name());
                entry.put("content", response.responseData());
                return Collections.unmodifiableMap(entry);
            }).toList());
        }
        return Collections.unmodifiableMap(payload);
    }

    private Map<String, Object> partPayload(MessagePart part)
    {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("attributes", part.attributes());
        payload.put("payload", part.payload());
        switch (part)
        {
            case TextPart text -> { payload.put("type", "text"); payload.put("text", text.text()); }
            case ReasoningPart reasoning -> {
                payload.put("type", "reasoning"); payload.put("text", reasoning.text());
                payload.put("summary", reasoning.summary());
            }
            case ToolCallPart call -> {
                payload.put("type", "tool_call"); payload.put("toolCall", call.toolCall());
            }
            case ToolResultPart result -> {
                payload.put("type", "tool_result"); payload.put("id", result.id());
                payload.put("name", result.name()); payload.put("isError", result.isError());
                payload.put("content", result.content().stream().map(this::partPayload).toList());
            }
            case MediaPart media -> {
                payload.put("type", "media");
                payload.put("media", new MediaPart.ToDto().convert(media.media()));
            }
            case UnknownPart unknown -> {
                payload.put("type", "unknown"); payload.put("provider", unknown.provider());
                payload.put("kind", unknown.kind()); payload.put("rawJson", unknown.rawJson());
            }
        }
        return Collections.unmodifiableMap(payload);
    }

    private Map<String, Object> responsePayload(ChatResponse response)
    {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("generations", response == null ? List.of() : response.getResults().stream()
                .map(generation -> messagePayload(generation == null ? null : generation.getOutput())).toList());
        payload.put("generationMetadata", response == null ? List.of() : response.getResults().stream().map(generation -> {
            if (generation == null || generation.getMetadata() == null) return null;
            var metadata = new LinkedHashMap<String, Object>();
            generation.getMetadata().entrySet().forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));
            metadata.put("finishReason", generation.getMetadata().getFinishReason());
            metadata.put("contentFilters", generation.getMetadata().getContentFilters());
            return Collections.unmodifiableMap(metadata);
        }).toList());
        var metadata = new LinkedHashMap<String, Object>();
        if (response != null && response.getMetadata() != null)
        {
            var source = response.getMetadata();
            source.entrySet().forEach(entry -> metadata.put(entry.getKey(), entry.getValue()));
            metadata.put("id", source.getId());
            metadata.put("model", source.getModel());
        }
        payload.put("metadata", Collections.unmodifiableMap(metadata));
        return Collections.unmodifiableMap(payload);
    }

    private String responseText(ChatClientResponse response)
    {
        if (response.chatResponse() == null || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null
                || response.chatResponse().getResult().getOutput().getText() == null) return "";
        return response.chatResponse().getResult().getOutput().getText();
    }
}
