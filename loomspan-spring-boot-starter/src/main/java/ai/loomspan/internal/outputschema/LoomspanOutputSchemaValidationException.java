package ai.loomspan.internal.outputschema;

import java.util.List;
import java.util.Objects;

final class LoomspanOutputSchemaValidationException extends RuntimeException
{
    private final String skillName;
    private final String rawOutput;
    private final List<OutputSchemaValidationIssue> validationIssues;
    private final int attemptCount;
    private final int maxRetries;
    private final OutputSchemaFailureMode failureMode;

    public LoomspanOutputSchemaValidationException(String skillName,
            String rawOutput,
            List<OutputSchemaValidationIssue> validationIssues,
            int attemptCount,
            int maxRetries,
            OutputSchemaFailureMode failureMode)
    {
        super(buildMessage(skillName, failureMode, attemptCount, maxRetries, validationIssues));
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.rawOutput = rawOutput;
        this.validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
        this.attemptCount = attemptCount;
        this.maxRetries = maxRetries;
        this.failureMode = Objects.requireNonNull(failureMode, "failureMode must not be null");
    }

    public String getSkillName()
    {
        return skillName;
    }

    public String getRawOutput()
    {
        return rawOutput;
    }

    public List<OutputSchemaValidationIssue> getValidationIssues()
    {
        return validationIssues;
    }

    public int getAttemptCount()
    {
        return attemptCount;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    public OutputSchemaFailureMode getFailureMode()
    {
        return failureMode;
    }

    private static String buildMessage(String skillName,
            OutputSchemaFailureMode failureMode,
            int attemptCount,
            int maxRetries,
            List<OutputSchemaValidationIssue> validationIssues)
    {
        String summary = validationIssues == null || validationIssues.isEmpty()
                ? "No validation issues recorded."
                : validationIssues.getFirst().message();
        return "Output schema validation failed for skill '%s' after %d attempt(s) with maxRetries=%d (%s): %s"
                .formatted(skillName, attemptCount, maxRetries, failureMode, summary);
    }
}
