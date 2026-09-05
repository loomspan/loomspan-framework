package ai.loomspan.internal.outputschema;

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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class OutputSchemaCallAdvisor implements CallAdvisor
{
    private static final Logger log = LoggerFactory.getLogger(OutputSchemaCallAdvisor.class);

    public static final String CONTEXT_KEY = "loomspan.output-schema.outcome";
    public static final String PLANNING_CALL_KEY = "loomspan.advisor.planning-call";

    private static final int MAX_ISSUES_IN_HINT = 4;
    private static final int MAX_ISSUES_IN_OUTCOME = 4;
    static final int MAX_CORRECTION_CODE_POINTS = 2_048;
    static final int MAX_CANDIDATE_CODE_POINTS = 8_192;

    private final String skillName;
    private final YamlSkillManifest.OutputSchemaManifest schema;
    private final OutputSchemaValidator validator;
    private final OutputSchemaPromptAugmentor promptAugmentor;
    private final int maxRetries;
    private final OutputSchemaOutcomeRecorder outcomeRecorder;
    private final AdvisorTraceRecorder advisorTraceRecorder;

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

        ChatClientRequest baselineRequest = chatClientRequest.mutate()
                .prompt(promptAugmentor.augment(chatClientRequest.prompt(), schema))
                .build();
        ChatClientRequest currentRequest = baselineRequest;

        CallAdvisorChain downstreamChain = callAdvisorChain.copy(this);
        int attempt = 1;

        while (true)
        {
            ChatClientResponse response = downstreamChain.nextCall(currentRequest);
            String candidate = extractAssistantText(response);
            OutputSchemaValidationResult result = validator.validate(candidate, schema);

            if (result.valid())
            {
                advisorTraceRecorder.record(AdvisorTraceFact.passed(
                        new AdvisorTraceContext(getName(), skillName, attempt, "passed",
                                ModelTraceContext.attemptFrom(response.context())),
                        candidate));

                return record(response, outcome(attempt, OutputSchemaOutcomeStatus.PASSED, null, List.of()));
            }

            if (attempt > maxRetries)
            {
                log.warn("Output schema validation exhausted for skill '{}' after attempt {} of {} (failureMode={}): {}",
                        skillName,
                        attempt,
                        maxRetries + 1,
                        result.failureMode(),
                        summarizeIssues(result.issues(), MAX_ISSUES_IN_HINT));

                OutputSchemaOutcome exhaustedOutcome = outcome(
                        attempt,
                        OutputSchemaOutcomeStatus.EXHAUSTED,
                        result.failureMode(),
                        result.issues());

                advisorTraceRecorder.record(AdvisorTraceFact.exhausted(
                        new AdvisorTraceContext(getName(), skillName, attempt, "exhausted",
                                ModelTraceContext.attemptFrom(response.context())),
                        truncateIssues(result.issues(), MAX_ISSUES_IN_OUTCOME)));

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
                    summarizeIssues(result.issues(), MAX_ISSUES_IN_HINT));

            record(response, outcome(attempt, OutputSchemaOutcomeStatus.RETRYING, result.failureMode(), result.issues()));

            advisorTraceRecorder.record(AdvisorTraceFact.retryRequested(
                    new AdvisorTraceContext(getName(), skillName, attempt, "retrying",
                            ModelTraceContext.attemptFrom(response.context())),
                    truncateIssues(result.issues(), MAX_ISSUES_IN_OUTCOME)));

            currentRequest = baselineRequest.mutate()
                    .prompt(buildRetryPrompt(baselineRequest.prompt(), candidate, result))
                    .build();

            downstreamChain = callAdvisorChain.copy(this);
            attempt++;
        }
    }

    @Override
    public String getName()
    {
        return "OutputSchemaCallAdvisor[" + skillName + "]";
    }

    @Override
    public int getOrder()
    {
        return DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 90;
    }

    private OutputSchemaOutcome outcome(int attempt,
            OutputSchemaOutcomeStatus status,
            OutputSchemaFailureMode failureMode,
            List<OutputSchemaValidationIssue> issues)
    {
        return new OutputSchemaOutcome(
                skillName,
                failureMode,
                attempt,
                attempt - 1,
                maxRetries,
                status,
                truncateIssues(issues, MAX_ISSUES_IN_OUTCOME));
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
        CandidateReplay replay = candidateReplay(candidate);
        if (StringUtils.hasText(replay.text()))
        {
            messages.add(new AssistantMessage(replay.text()));
        }
        messages.add(new UserMessage(correctionMessage(result, replay.truncated())));
        return new Prompt(messages, baselinePrompt.getOptions());
    }

    private String issueMessage(OutputSchemaValidationIssue issue)
    {
        String path = StringUtils.hasText(issue.path()) ? issue.path() : "$";
        if (OutputSchemaValidator.INVALID_JSON.equals(issue.code()))
        {
            StringBuilder message = new StringBuilder("Path ").append(quoteFact(path)).append(": invalid JSON");
            if (StringUtils.hasText(issue.reason()))
            {
                message.append(". Parser reason: ").append(quoteFact(issue.reason()));
            }
            if (issue.line() != null || issue.column() != null || issue.characterOffset() != null)
            {
                message.append(". Location:");
                if (issue.line() != null) message.append(" line ").append(issue.line());
                if (issue.column() != null) message.append(", column ").append(issue.column());
                if (issue.characterOffset() != null) message.append(", character offset ").append(issue.characterOffset());
            }
            if (StringUtils.hasText(issue.fragment()))
            {
                message.append(". Nearby fragment (escaped JSON string): ").append(quoteFact(issue.fragment()));
            }
            return message.append('.').toString();
        }
        if (StringUtils.hasText(issue.expected()) && StringUtils.hasText(issue.actual()))
        {
            return "Path " + quoteFact(path) + ": expected " + quoteFact(issue.expected())
                    + ", received " + quoteFact(issue.actual()) + ".";
        }
        return "Path " + quoteFact(path) + ": " + quoteFact(sentence(issue.message()));
    }

    private List<OutputSchemaValidationIssue> truncateIssues(List<OutputSchemaValidationIssue> issues, int maxIssues)
    {
        if (issues == null || issues.isEmpty())
        {
            return List.of();
        }

        return issues.stream()
                .limit(maxIssues)
                .toList();
    }

    private String summarizeIssues(List<OutputSchemaValidationIssue> issues, int maxIssues)
    {
        if (issues == null || issues.isEmpty())
        {
            return "no validation issues recorded";
        }

        List<String> summarized = issues.stream()
                .limit(maxIssues)
                .map(this::logIssueMessage)
                .toList();

        if (issues.size() > maxIssues)
        {
            summarized = new ArrayList<>(summarized);
            summarized.add("+" + (issues.size() - maxIssues) + " more issue(s)");
        }

        return String.join("; ", summarized);
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

    private String correctionMessage(OutputSchemaValidationResult result, boolean candidateTruncated)
    {
        String heading = result.failureMode() == OutputSchemaFailureMode.INVALID_JSON
                ? "The previous response could not be parsed as JSON."
                : "The previous response is valid JSON but does not satisfy the configured output_schema.";
        String prefix = heading + "\nIssues:\n";
        List<String> bullets = result.issues().stream()
                .limit(MAX_ISSUES_IN_HINT)
                .map(issue -> "- " + issueMessage(issue) + "\n")
                .toList();

        for (int displayed = bullets.size(); displayed >= 0; displayed--)
        {
            int omitted = result.issues().size() - displayed;
            String tail = correctionTail(omitted, candidateTruncated);
            String renderedBullets = String.join("", bullets.subList(0, displayed)).stripTrailing();
            String correction = prefix + renderedBullets + "\n" + tail;
            if (codePointCount(correction) <= MAX_CORRECTION_CODE_POINTS)
            {
                return correction;
            }
        }

        throw new IllegalStateException("Required output-schema correction text exceeds its configured bound");
    }

    private String correctionTail(int omitted, boolean candidateTruncated)
    {
        StringBuilder tail = new StringBuilder();
        if (omitted > 0)
        {
            tail.append(omitted).append(" additional issue(s) omitted.\n");
        }
        if (candidateTruncated)
        {
            tail.append("The previous assistant candidate was truncated to ")
                    .append(MAX_CANDIDATE_CODE_POINTS).append(" Unicode code points.\n");
        }
        return tail.append("Preserve all already-valid structure and values visible in the previous assistant response.\n")
                .append("Do NOT call any tools again; use the data already returned by completed tool calls.\n")
                .append("Return one complete corrected JSON object only, with no explanation, markdown, or code fences.")
                .toString();
    }

    private String quoteFact(String value)
    {
        StringBuilder quoted = new StringBuilder("\"");
        for (int index = 0; index < value.length();)
        {
            int codePoint = value.codePointAt(index);
            switch (codePoint)
            {
                case '\"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\b' -> quoted.append("\\b");
                case '\f' -> quoted.append("\\f");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (codePoint < 0x20)
                    {
                        quoted.append(String.format("\\u%04x", codePoint));
                    }
                    else
                    {
                        quoted.appendCodePoint(codePoint);
                    }
                }
            }
            index += Character.charCount(codePoint);
        }
        return quoted.append('\"').toString();
    }

    private CandidateReplay candidateReplay(String candidate)
    {
        if (!StringUtils.hasText(candidate))
        {
            return new CandidateReplay("", false);
        }
        int count = codePointCount(candidate);
        return count <= MAX_CANDIDATE_CODE_POINTS
                ? new CandidateReplay(candidate, false)
                : new CandidateReplay(truncateCodePoints(candidate, MAX_CANDIDATE_CODE_POINTS), true);
    }

    private String logIssueMessage(OutputSchemaValidationIssue issue)
    {
        String path = StringUtils.hasText(issue.path()) ? issue.path() : "$";
        if (OutputSchemaValidator.INVALID_JSON.equals(issue.code()))
        {
            return path + ": invalid JSON";
        }
        if (OutputSchemaValidator.UNKNOWN_PROPERTY.equals(issue.code()))
        {
            return "output object: validation issue code " + issue.code();
        }
        if (StringUtils.hasText(issue.expected()) && StringUtils.hasText(issue.actual()))
        {
            return path + ": expected " + issue.expected() + ", received " + issue.actual();
        }
        return path + ": validation issue code " + issue.code();
    }

    private String sentence(String message)
    {
        if (!StringUtils.hasText(message)) return "validation failed.";
        return message.endsWith(".") ? message : message + ".";
    }

    private int codePointCount(String value)
    {
        return value.codePointCount(0, value.length());
    }

    private String truncateCodePoints(String value, int maxCodePoints)
    {
        if (maxCodePoints <= 0) return "";
        if (codePointCount(value) <= maxCodePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private record CandidateReplay(String text, boolean truncated)
    {
    }
}
