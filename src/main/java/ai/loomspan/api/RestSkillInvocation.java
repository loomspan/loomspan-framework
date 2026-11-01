package ai.loomspan.api;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable application handoff for one REST skill invocation.
 * {@code skillName} is the canonical qualified target identity ({@code namespace-localName}).
 * {@code input} preserves the validated, reference-resolved business values.
 * {@code generationId} identifies the captured executing target generation, which may differ
 * from the admitted root's entry generation. These identities are captured during preparation,
 * independent of current publication and business input.
 */
public record RestSkillInvocation(String skillName, Map<String, Object> input, String generationId)
{
    public RestSkillInvocation
    {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(generationId, "generationId must not be null");
        if (generationId.isBlank()) throw new IllegalArgumentException("generationId must not be blank");
        input = ai.loomspan.internal.util.ImmutableContainers.copyMap(input);
    }

}
