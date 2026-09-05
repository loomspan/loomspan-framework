package ai.loomspan.internal.runtime.evidence;

import java.util.List;
import java.util.Objects;

final class LoomspanEvidenceValidationException extends RuntimeException
{
    private final String skillName;
    private final String rawOutput;
    private final List<EvidenceCoverageIssue> issues;
    private final int attemptCount;
    private final int maxRetries;

    public LoomspanEvidenceValidationException(String skillName,
            String rawOutput,
            List<EvidenceCoverageIssue> issues,
            int attemptCount,
            int maxRetries)
    {
        super(buildMessage(skillName, issues, attemptCount, maxRetries));
        this.skillName = Objects.requireNonNull(skillName, "skillName must not be null");
        this.rawOutput = rawOutput;
        this.issues = issues == null ? List.of() : List.copyOf(issues);
        this.attemptCount = attemptCount;
        this.maxRetries = maxRetries;
    }

    public String getSkillName()
    {
        return skillName;
    }

    public String getRawOutput()
    {
        return rawOutput;
    }

    public List<EvidenceCoverageIssue> getIssues()
    {
        return issues;
    }

    public int getAttemptCount()
    {
        return attemptCount;
    }

    public int getMaxRetries()
    {
        return maxRetries;
    }

    private static String buildMessage(String skillName,
            List<EvidenceCoverageIssue> issues,
            int attemptCount,
            int maxRetries)
    {
        String summary = issues == null || issues.isEmpty()
                ? "No evidence validation issues recorded."
                : issues.getFirst().message();
        return "Evidence validation failed for skill '%s' after %d attempt(s) with maxRetries=%d: %s"
                .formatted(skillName, attemptCount, maxRetries, summary);
    }
}
