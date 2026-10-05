package ai.loomspan.internal.runtime.evidence;

import ai.loomspan.internal.outputvalidation.OutputValidationPolicy;
import ai.loomspan.internal.outputvalidation.OutputValidationFeedback;
import ai.loomspan.internal.core.AdvisorTraceContext;
import ai.loomspan.internal.core.AdvisorTraceFact;
import ai.loomspan.internal.core.AdvisorTraceRecorder;
import ai.loomspan.internal.core.LoomspanSession;
import ai.loomspan.internal.core.ExecutionBindingScope;
import ai.loomspan.internal.core.ModelTraceContext;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Objects;
import java.util.function.Consumer;

public final class EvidenceContractCallAdvisor implements CallAdvisor
{

    private final String skillName;
    private final EvidenceContract contract;
    private final EvidenceBackedOutputValidator validator;
    private final int maxRetries;
    private final Consumer<EvidenceCoverageResult> passRecorder;
    private final Consumer<EvidenceCoverageResult> failRecorder;
    private final AdvisorTraceRecorder advisorTraceRecorder;

    public EvidenceContractCallAdvisor(String skillName,
            EvidenceContract contract,
            EvidenceBackedOutputValidator validator,
            int maxRetries,
            Consumer<EvidenceCoverageResult> passRecorder,
            Consumer<EvidenceCoverageResult> failRecorder,
            AdvisorTraceRecorder advisorTraceRecorder)
    {
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.contract = Objects.requireNonNull(contract, "contract must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.maxRetries = maxRetries;
        this.passRecorder = Objects.requireNonNull(passRecorder, "passRecorder must not be null");
        this.failRecorder = Objects.requireNonNull(failRecorder, "failRecorder must not be null");
        this.advisorTraceRecorder = Objects.requireNonNull(advisorTraceRecorder, "advisorTraceRecorder must not be null");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain)
    {
        Objects.requireNonNull(chatClientRequest, "chatClientRequest must not be null");
        Objects.requireNonNull(callAdvisorChain, "callAdvisorChain must not be null");

        ChatClientRequest currentRequest = chatClientRequest;
        CallAdvisorChain downstreamChain = callAdvisorChain.copy(this);
        int attempt = 1;

        while (true)
        {
            ChatClientResponse response = downstreamChain.nextCall(currentRequest);
            String candidate = extractAssistantText(response);
            EvidenceCoverageResult result = validator.validate(
                    candidate,
                    contract,
                    ExecutionBindingScope.requireCurrent().requireMission().successfulDirectSkills());

            if (result.complete())
            {
                passRecorder.accept(result);
                advisorTraceRecorder.record(AdvisorTraceFact.passed(
                        new AdvisorTraceContext(getName(), skillName, attempt, "passed",
                                ModelTraceContext.attemptFrom(response.context())),
                        candidate));
                return response;
            }

            failRecorder.accept(result);
            if (OutputValidationPolicy.exhausted(attempt, maxRetries))
            {
                advisorTraceRecorder.record(AdvisorTraceFact.exhausted(
                        new AdvisorTraceContext(getName(), skillName, attempt, "exhausted",
                                ModelTraceContext.attemptFrom(response.context())),
                        result.issues()));

                throw new LoomspanEvidenceValidationException(skillName, candidate, result.issues(), attempt, maxRetries);
            }

            advisorTraceRecorder.record(AdvisorTraceFact.retryRequested(
                    new AdvisorTraceContext(getName(), skillName, attempt, "retrying",
                            ModelTraceContext.attemptFrom(response.context())),
                    result.issues()));

            currentRequest = currentRequest.mutate()
                    .prompt(appendHint(currentRequest.prompt(), result))
                    .build();

            downstreamChain = callAdvisorChain.copy(this);
            attempt++;
        }
    }

    @Override
    public String getName()
    {
        return "EvidenceContractCallAdvisor[" + skillName + "]";
    }

    @Override
    public int getOrder()
    {
        return DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER - 80;
    }

    private Prompt appendHint(Prompt prompt, EvidenceCoverageResult result)
    {
        String hint = OutputValidationFeedback.ordinaryEvidenceHint(result);

        return prompt.augmentSystemMessage(systemMessage -> systemMessage.mutate()
                .text(OutputValidationFeedback.joinSystemText(systemMessage.getText(), hint))
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

}
