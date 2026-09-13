package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;

public enum CapabilityKind
{
    YAML_SKILL(SkillKind.YAML), REST_SKILL(SkillKind.REST), JAVA_SKILL(SkillKind.JAVA);

    private final SkillKind publicKind;

    CapabilityKind(SkillKind publicKind)
    {
        this.publicKind = publicKind;
    }

    public SkillKind publicKind()
    {
        return publicKind;
    }
}
