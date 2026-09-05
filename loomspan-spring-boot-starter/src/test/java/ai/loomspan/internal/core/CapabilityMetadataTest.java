package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMetadataTest
{
    @Test
    void exposesOnlyYamlSkillCapabilityKind()
    {
        assertThat(CapabilityKind.values()).containsExactly(CapabilityKind.YAML_SKILL, CapabilityKind.JAVA_SKILL);
    }

    @Test
    void rejectsNullCapabilityKind()
    {
        assertThatThrownBy(() -> new CapabilityMetadata(
                "id", "name", "description", SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), arguments -> "ok", null, CapabilityToolDescriptor.generic("name", "description"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind must not be null");
    }

    @Test
    void rejectsProviderToolNameThatDoesNotMatchPublicYamlName()
    {
        assertThatThrownBy(() -> new CapabilityMetadata(
                "id", "public.skill", "description", SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), arguments -> "ok", CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("bean#method", "description"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool.name")
                .hasMessageContaining("registered skill name");
    }


}
