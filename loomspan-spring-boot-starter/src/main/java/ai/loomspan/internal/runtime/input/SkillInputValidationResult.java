package ai.loomspan.internal.runtime.input;

import java.util.List;
import java.util.Map;

public record SkillInputValidationResult(
                boolean valid,
                Map<String, Object> normalizedInput,
                List<SkillInputValidationIssue> issues)
{
}
