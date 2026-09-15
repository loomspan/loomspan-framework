package ai.loomspan.internal.runtime.evidence;

import java.util.List;

public record EvidenceRequirement(String mode, String expression, List<String> skills, List<EvidenceRequirement> children)
{
    public EvidenceRequirement
    {
        skills = skills == null ? List.of() : List.copyOf(skills);
        children = children == null ? List.of() : List.copyOf(children);
    }
}
