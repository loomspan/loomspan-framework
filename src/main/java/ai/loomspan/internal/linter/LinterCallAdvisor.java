package ai.loomspan.internal.linter;

import ai.loomspan.internal.core.AdvisorTraceContext;
import ai.loomspan.internal.core.AdvisorTraceFact;
import ai.loomspan.internal.core.AdvisorTraceRecorder;
import ai.loomspan.internal.core.ModelTraceContext;
import ai.loomspan.internal.outputschema.OutputSchemaCallAdvisor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.regex.Pattern;

public final class LinterCallAdvisor implements CallAdvisor
{
    public static final String CONTEXT_KEY = "loomspan.linter.outcome";

    private static final String HINT_TEMPLATE = """

            Linter validation failed for the previous response.
            Return a corrected answer that satisfies this requirement:
            %s
            """;

    private final String skillName;
    private final String linterType;
    private final Pattern pattern;
    private final String failureMessage;
    private final int maxRetries;
    private final LinterOutcomeRecorder outcomeRecorder;
    private final AdvisorTraceRecorder advisorTraceRecorder;

    public LinterCallAdvisor(String skillName,
            String linterType,
            Pattern pattern,
            String failureMessage,
            int maxRetries,
            LinterOutcomeRecorder outcomeRecorder)
    {
        this(skillName, linterType, pattern, failureMessage, maxRetries, outcomeRecorder, AdvisorTraceRecorder.noOp());
    }

    public LinterCallAdvisor(String skillName,
            String linterType,
            Pattern pattern,
            String failureMessage,
            int maxRetries,
            LinterOutcomeRecorder outcomeRecorder,
            AdvisorTraceRecorder advisorTraceRecorder)
    {
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.linterType = Objects.requireNonNull(linterType, "linterType must not be null");
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.failureMessage = Objects.requireNonNull(failureMessage, "failureMessage must not be null");

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

        if (Boolean.TRUE.equals(chatClientRequest.context().get(OutputSchemaCallAdvisor.PLANNING_CALL_KEY)))
        {
            return callAdvisorChain.nextCall(chatClientRequest);
        }

        ChatClientRequest currentRequest = chatClientRequest;
        CallAdvisorChain downstreamChain = callAdvisorChain.copy(this);
        int attempt = 1;
        while (true)
        {
            ChatClientResponse response = downstreamChain.nextCall(currentRequest);
            String candidate = extractAssistantText(response);
            if (pattern.matcher(candidate).matches())
            {
                advisorTraceRecorder.record(AdvisorTraceFact.passed(
                        new AdvisorTraceContext(getName(), skillName, attempt, "passed",
                                ModelTraceContext.attemptFrom(response.context())),
                        candidate));
                return record(response, outcome(attempt, LinterOutcomeStatus.PASSED, failureMessage));
            }

            if (attempt > maxRetries)
            {
                advisorTraceRecorder.record(AdvisorTraceFact.exhausted(
                        new AdvisorTraceContext(getName(), skillName, attempt, "exhausted",
                                ModelTraceContext.attemptFrom(response.context())),
                        candidate,
                        failureMessage));
                return record(response, outcome(attempt, LinterOutcomeStatus.EXHAUSTED, failureMessage));
            }

            record(response, outcome(attempt, LinterOutcomeStatus.RETRYING, failureMessage));

            advisorTraceRecorder.record(AdvisorTraceFact.retryRequested(
                    new AdvisorTraceContext(getName(), skillName, attempt, "retrying",
                            ModelTraceContext.attemptFrom(response.context())),
                    failureMessage));

            currentRequest = currentRequest.mutate()
                    .prompt(appendHint(currentRequest.prompt(), failureMessage))
                    .build();

            downstreamChain = callAdvisorChain.copy(this);
            attempt++;
        }
    }

    @Override
    public String getName()
    {
        return "LinterCallAdvisor[" + skillName + "]";
    }

    @Override
    public int getOrder()
    {
        return DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 100;
    }

    private LinterOutcome outcome(int attempt, LinterOutcomeStatus status, String detail)
    {
        return new LinterOutcome(skillName, linterType, attempt, attempt - 1, maxRetries, status, detail);
    }

    private ChatClientResponse record(ChatClientResponse response, LinterOutcome outcome)
    {
        recordOnSession(outcome);
        return response.mutate().context(CONTEXT_KEY, outcome).build();
    }

    private void recordOnSession(LinterOutcome outcome)
    {
        try
        {
            outcomeRecorder.record(outcome);
        }
        catch (IllegalStateException ex)
        {
            if (!isManagedSessionBound())
            {
                // Advisor usage outside a managed Loomspan session still exposes the outcome via response context.
                return;
            }
            throw ex;
        }
    }

    private boolean isManagedSessionBound()
    {
        try
        {
            ai.loomspan.internal.core.LoomspanSession.getCurrentSession();
            return true;
        }
        catch (IllegalStateException ignored)
        {
            return false;
        }
    }

    private Prompt appendHint(Prompt prompt, String detail)
    {
        String hint = HINT_TEMPLATE.formatted(detail).stripTrailing();
        return prompt.augmentSystemMessage(systemMessage -> systemMessage.mutate()
                .text(joinSystemText(systemMessage.getText(), hint))
                .build());
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

    private String joinSystemText(String original, String hint)
    {
        if (!StringUtils.hasText(original))
        {
            return hint;
        }
        return original + "\n\n" + hint;
    }

}
