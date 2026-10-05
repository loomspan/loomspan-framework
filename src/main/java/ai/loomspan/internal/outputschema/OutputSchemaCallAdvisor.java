package ai.loomspan.internal.outputschema;

import ai.loomspan.internal.outputvalidation.OutputValidationPolicy;
import ai.loomspan.internal.outputvalidation.OutputValidationFeedback;
import ai.loomspan.internal.core.AdvisorTraceContext;
import ai.loomspan.internal.core.AdvisorTraceFact;
import ai.loomspan.internal.core.AdvisorTraceRecorder;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.skill.YamlSkillManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OutputSchemaCallAdvisor implements CallAdvisor
{
    private static final Logger log = LoggerFactory.getLogger(OutputSchemaCallAdvisor.class);

    public static final String CONTEXT_KEY = "loomspan.output-schema.outcome";
    public static final String PLANNING_CALL_KEY = "loomspan.advisor.planning-call";

    private static final int MAX_ISSUES_IN_OUTCOME = 4;
    static final int MAX_CORRECTION_CODE_POINTS = OutputValidationFeedback.MAX_CORRECTION_CODE_POINTS;

    private final String skillName;
    private final YamlSkillManifest.OutputSchemaManifest schema;
    private final OutputSchemaValidator validator;
    private final OutputSchemaPromptAugmentor promptAugmentor;
    private final int maxRetries;
    private final OutputSchemaOutcomeRecorder outcomeRecorder;
    private final AdvisorTraceRecorder advisorTraceRecorder;
    private ai.loomspan.internal.skill.YamlSkillDefinition compositionDefinition;
    private OutputBindingComposition invocationComposition;
    private ai.loomspan.internal.core.MissionContext compositionMission;

    public OutputSchemaCallAdvisor withOutputBindings(ai.loomspan.internal.skill.YamlSkillDefinition definition) {
        compositionDefinition = Objects.requireNonNull(definition);
        return this;
    }

    public OutputSchemaCallAdvisor(String skillName,
            YamlSkillManifest.OutputSchemaManifest schema,
            OutputSchemaValidator validator,
            OutputSchemaPromptAugmentor promptAugmentor,
            int maxRetries,
            OutputSchemaOutcomeRecorder outcomeRecorder)
    {
        this(skillName, schema, validator, promptAugmentor, maxRetries, outcomeRecorder, AdvisorTraceRecorder.noOp());
    }

    public OutputSchemaCallAdvisor(String skillName,
            YamlSkillManifest.OutputSchemaManifest schema,
            OutputSchemaValidator validator,
            OutputSchemaPromptAugmentor promptAugmentor,
            int maxRetries,
            OutputSchemaOutcomeRecorder outcomeRecorder,
            AdvisorTraceRecorder advisorTraceRecorder)
    {
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.promptAugmentor = Objects.requireNonNull(promptAugmentor, "promptAugmentor must not be null");

        if (maxRetries < 0)
        {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }

        this.maxRetries = maxRetries;
        this.outcomeRecorder = Objects.requireNonNull(outcomeRecorder, "outcomeRecorder must not be null");
        this.advisorTraceRecorder = Objects.requireNonNull(advisorTraceRecorder, "advisorTraceRecorder must not be null");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain)
    {
        Objects.requireNonNull(chatClientRequest, "chatClientRequest must not be null");
        Objects.requireNonNull(callAdvisorChain, "callAdvisorChain must not be null");

        if (Boolean.TRUE.equals(chatClientRequest.context().get(PLANNING_CALL_KEY)))
        {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        OutputBindingComposition composition = compositionDefinition == null ? null : invocationComposition();
        Prompt initialPrompt = promptAugmentor.augment(chatClientRequest.prompt(), composition == null ? schema : composition.projection().schema());
        if (composition != null) initialPrompt = initialPrompt.augmentSystemMessage(message -> message.mutate().text(message.getText() + "\n" + composition.guidance()).build());
        ChatClientRequest baselineRequest = chatClientRequest.mutate()
                .prompt(initialPrompt)
                .build();
        ChatClientRequest currentRequest = baselineRequest;

        CallAdvisorChain downstreamChain = callAdvisorChain.copy(this);
        int attempt = 1;

        while (true)
        {
            ChatClientResponse response = downstreamChain.nextCall(currentRequest);
            String candidate = extractAssistantText(response);
            OutputBindingComposition.Result assembled = composition == null ? null : composition.compose(candidate);
            OutputSchemaValidationResult result = assembled == null ? validator.validate(candidate, schema) : assembled.validation();

            if (result.valid())
            {
                advisorTraceRecorder.record(AdvisorTraceFact.passed(
                        new AdvisorTraceContext(getName(), skillName, attempt, "passed",
                                ModelTraceContext.attemptFrom(response.context())),
                        candidate));

                if (assembled != null) {
                    var originalResponse = response.chatResponse();
                    var generation = originalResponse.getResult();
                    var output = generation.getOutput();
                    var replacement = new org.springframework.ai.chat.model.Generation(AssistantMessage.builder().content(assembled.content()).properties(output.getMetadata()).toolCalls(output.getToolCalls()).media(output.getMedia()).build(), generation.getMetadata());
                    response = response.mutate().chatResponse(new org.springframework.ai.chat.model.ChatResponse(List.of(replacement), originalResponse.getMetadata())).build();
                }
                return record(response, OutputValidationPolicy.schemaOutcome(skillName, attempt, maxRetries, true, null, List.of(), MAX_ISSUES_IN_OUTCOME));
            }

            if (OutputValidationPolicy.exhausted(attempt, maxRetries))
            {
                log.warn("Output schema validation exhausted for skill '{}' after attempt {} of {} (failureMode={}): {}",
                        skillName,
                        attempt,
                        maxRetries + 1,
                        result.failureMode(),
                        OutputValidationFeedback.ordinarySchemaSummary(result.issues(), 4));

                OutputSchemaOutcome exhaustedOutcome = OutputValidationPolicy.schemaOutcome(skillName, attempt, maxRetries, false, result.failureMode(), result.issues(), MAX_ISSUES_IN_OUTCOME);

                advisorTraceRecorder.record(AdvisorTraceFact.exhausted(
                        new AdvisorTraceContext(getName(), skillName, attempt, "exhausted",
                                ModelTraceContext.attemptFrom(response.context())),
                        OutputValidationPolicy.limitIssues(result.issues(), MAX_ISSUES_IN_OUTCOME)));

                recordOnSession(exhaustedOutcome);

                throw new LoomspanOutputSchemaValidationException(
                        skillName,
                        candidate,
                        result.issues(),
                        attempt,
                        maxRetries,
                        result.failureMode());
            }

            log.warn("Output schema validation retry for skill '{}' after attempt {} of {} (failureMode={}): {}",
                    skillName,
                    attempt,
                    maxRetries + 1,
                    result.failureMode(),
                    OutputValidationFeedback.ordinarySchemaSummary(result.issues(), 4));

            record(response, OutputValidationPolicy.schemaOutcome(skillName, attempt, maxRetries, false, result.failureMode(), result.issues(), MAX_ISSUES_IN_OUTCOME));

            advisorTraceRecorder.record(AdvisorTraceFact.retryRequested(
                    new AdvisorTraceContext(getName(), skillName, attempt, "retrying",
                            ModelTraceContext.attemptFrom(response.context())),
                    OutputValidationPolicy.limitIssues(result.issues(), MAX_ISSUES_IN_OUTCOME)));

            currentRequest = baselineRequest.mutate()
                    .prompt(buildRetryPrompt(baselineRequest.prompt(), candidate, result))
                    .build();

            downstreamChain = callAdvisorChain.copy(this);
            attempt++;
        }
    }

    private synchronized OutputBindingComposition invocationComposition() {
        var mission = ai.loomspan.internal.core.ExecutionBindingScope.requireCurrent().requireMission();
        if (invocationComposition == null) {
            compositionMission = mission;
            invocationComposition = mission.getOrCreateOutputComposition(() -> new OutputBindingComposition(compositionDefinition, mission,
                    ai.loomspan.internal.serialization.LoomspanJacksonCodecs.defaults().schemaTree()));
        }
        if (compositionMission != mission) throw new IllegalStateException("Output binding advisor reused across owning invocations.");
        return invocationComposition;
    }

    @Override
    public String getName()
    {
        return "OutputSchemaCallAdvisor[" + skillName + "]";
    }

    @Override
    public int getOrder()
    {
        return DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - (compositionDefinition == null ? 90 : 70);
    }

    private ChatClientResponse record(ChatClientResponse response, OutputSchemaOutcome outcome)
    {
        recordOnSession(outcome);

        return response.mutate()
                .context(CONTEXT_KEY, outcome)
                .build();
    }

    private void recordOnSession(OutputSchemaOutcome outcome)
    {
        try
        {
            outcomeRecorder.record(outcome);
        }
        catch (IllegalStateException ex)
        {
            if (!isManagedSessionBound())
            {
                // Advisor usage outside a managed Loomspan session still exposes outcome via response context.
                return;
            }
            throw ex;
        }
    }

    private boolean isManagedSessionBound()
    {
        try
        {
            LoomspanSession.getCurrentSession();
            return true;
        }
        catch (IllegalStateException ignored)
        {
            return false;
        }
    }

    private Prompt buildRetryPrompt(Prompt baselinePrompt,
            String candidate,
            OutputSchemaValidationResult result)
    {
        List<Message> messages = new ArrayList<>(baselinePrompt.getInstructions());
        if (candidate != null && !candidate.isEmpty())
        {
            messages.add(new AssistantMessage(candidate));
        }
        messages.add(new UserMessage(OutputValidationFeedback.ordinarySchemaCorrection(result, compositionDefinition != null)));
        return new Prompt(messages, baselinePrompt.getOptions());
    }

    private String extractAssistantText(ChatClientResponse response)
    {
        if (response == null || response.chatResponse() == null || response.chatResponse().getResult() == null)
        {
            return "";
        }

        AssistantMessage message = response.chatResponse().getResult().getOutput();
        return message == null || message.getText() == null ? "" : message.getText();
    }

}
