package ai.loomspan.internal.typesafe;

import ai.loomspan.internal.core.*;
import ai.loomspan.internal.provider.*;
import ai.loomspan.internal.runtime.state.ExecutionStateService;
import ai.loomspan.internal.runtime.usage.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillDefinition;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.concurrent.CancellationException;

/** Native evaluation attempts within the captured mission and existing quota/write authority. */
public final class TypeSafeExecutor {
    private final ExecutionStateService state;
    private final SessionUsageService usage;
    private final ProviderRetryDecider retry = new ProviderRetryDecider();

    public TypeSafeExecutor(ExecutionStateService state, SessionUsageService usage) {
        this.state = Objects.requireNonNull(state);
        this.usage = Objects.requireNonNull(usage);
    }

    public String execute(
            YamlSkillDefinition definition, Map<String, Object> input, LoomspanSession session) {
        var binding = ExecutionBindingScope.requireCurrent();
        var lifecycle = binding.requireMission().lifecycle();
        var configuration = definition.requireExecutionConfiguration();
        var runtime = binding.generation().runtime().connection(configuration.connection());
        TypeSafeClient client =
                Objects.requireNonNull(
                        runtime.nativeClient(), "TypeSafe connection resource is required");
        ObjectNode request = (ObjectNode) definition.typeSafeDeclaration().resolve(input).json();
        request.put("model", configuration.providerModel());
        var codec = LoomspanJacksonCodecs.defaults().schemaTree();
        String body = codec.writeValueAsString(request);
        var context =
                new ModelTraceContext(
                        ModelExecutionIdentity.from(configuration),
                        definition.manifest().getName(),
                        "evaluation");
        var frame = binding.branch().requireLeaf();
        var requestEvidence = evidence(client, configuration.providerModel());
        requestEvidence.put("request", request);
        for (int number = 1; number <= runtime.retryPolicy().maxAttempts(); number++) {
            requireNotInterrupted();
            lifecycle.requireOpenForNewWork(binding);
            usage.reserveProviderAttempt(session, definition.manifest().getName());
            var attempt = context.nextAttempt(number);
            state.recordModelRequestSent(session, frame, context, attempt, requestEvidence);
            String raw;
            try {
                raw = client.send(body, lifecycle);
            } catch (RuntimeException failure) {
                requireNotInterrupted();
                lifecycle.requireOpenForNewWork(binding);
                var details = runtime.failureTranslator().translate(failure);
                var outcome = retry.decide(runtime.retryPolicy(), details, number);
                var metadata = failureMetadata(details, outcome);
                usage.recordProviderAttemptOutcome(
                        session,
                        definition.manifest().getName(),
                        context.identity(),
                        "failed",
                        details.category(),
                        outcome.decision());
                state.recordModelAttemptFailed(
                        session, frame, context, attempt, metadata, failure, details.diagnostics());
                if (outcome.decision() != ProviderRetryDecision.RETRY) {
                    state.registerProviderFailure(session, failure, attempt);
                    throw failure;
                }
                try {
                    Thread.sleep(outcome.delay().toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("TypeSafe retry interrupted");
                }
                continue;
            }
            requireNotInterrupted();
            lifecycle.requireOpenForNewWork(binding);
            JsonNode envelope = null;
            RuntimeException decoding = null;
            try {
                envelope = codec.readTree(raw);
            } catch (RuntimeException invalid) {
                decoding = invalid;
            }
            ModelUsageRecord counts = nativeUsage(envelope == null ? null : envelope.get("usage"));
            var responseEvidence = evidence(client, configuration.providerModel());
            if (decoding != null || envelope == null) responseEvidence.put("rawBody", raw);
            else {
                responseEvidence.put("envelope", envelope);
                JsonNode served = envelope.get("model");
                if (served != null && served.isString())
                    responseEvidence.put("servedModel", served.stringValue());
            }
            usage.recordProviderAttemptOutcome(
                    session,
                    definition.manifest().getName(),
                    context.identity(),
                    "succeeded",
                    null,
                    null);
            // Successful HTTP usage belongs to the response, even when minimal envelope extraction
            // fails.
            state.recordModelResponseReceived(
                    session, frame, context, attempt, counts, responseEvidence);
            usage.recordModelResponse(
                    session, definition.manifest().getName(), context.identity(), counts);
            if (decoding != null)
                throw new IllegalStateException("System One returned unreadable JSON", decoding);
            JsonNode answers = envelope == null ? null : envelope.get("answers");
            if (answers == null || !answers.isObject())
                throw new IllegalStateException(
                        "System One response requires an object-valued answers field");
            return codec.writeValueAsString(answers);
        }
        throw new IllegalStateException("System One retry loop has no terminal result");
    }

    static ModelUsageRecord nativeUsage(JsonNode value) {
        if (value != null && value.isObject()) {
            JsonNode input = value.get("input_tokens"), output = value.get("output_tokens");
            if (usableCount(input) && usableCount(output)) {
                long total = input.longValue() + output.longValue();
                if (total <= Integer.MAX_VALUE)
                    return new ModelUsageRecord(
                            input.intValue(),
                            output.intValue(),
                            (int) total,
                            UsagePrecision.EXACT,
                            value.deepCopy());
            }
        }
        return new ModelUsageRecord(
                0, 0, 0, UsagePrecision.UNAVAILABLE, value == null ? null : value.deepCopy());
    }

    private static boolean usableCount(JsonNode value) {
        return value != null
                && value.isIntegralNumber()
                && value.canConvertToInt()
                && value.intValue() >= 0;
    }

    private static LinkedHashMap<String, Object> evidence(TypeSafeClient client, String model) {
        var result = new LinkedHashMap<String, Object>();
        result.put("payloadType", "evaluation");
        result.put("endpoint", client.endpoint().toString());
        result.put("requestedModel", model);
        return result;
    }

    private static Map<String, Object> failureMetadata(
            ProviderFailureDetails details, ProviderRetryOutcome outcome) {
        var result = new LinkedHashMap<String, Object>();
        result.put("failureClassification", details.classification().name());
        result.put("failureCategory", details.category().name());
        result.put("retryDecision", outcome.decision().name());
        result.put("retryDelayMillis", outcome.delay().toMillis());
        result.put("retryDelaySource", outcome.delaySource().name());
        if (details.httpStatus() != null) result.put("httpStatus", details.httpStatus());
        if (details.summary() != null) result.put("summary", details.summary());
        return result;
    }

    private static void requireNotInterrupted() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("TypeSafe execution interrupted");
    }
}
