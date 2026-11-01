package ai.loomspan.internal.core;

import ai.loomspan.api.SkillKind;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMetadataTest
{
    @Test
    void exposesExactlyThreeExecutionKinds()
    {
        assertThat(SkillKind.values()).containsExactly(
                SkillKind.YAML, SkillKind.JAVA, SkillKind.TYPESAFE);
    }

    @Test
    void rejectsNullSkillKind()
    {
        assertThatThrownBy(() -> new CapabilityMetadata(
                "id", "default-name", "description", SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), arguments -> "ok", null, CapabilityToolDescriptor.generic("default-name", "description"), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("kind must not be null");
    }

    @Test
    void rejectsProviderToolNameThatDoesNotMatchPublicYamlName()
    {
        assertThatThrownBy(() -> new CapabilityMetadata(
                "id", "default-public_skill", "description", SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), arguments -> "ok", SkillKind.YAML,
                CapabilityToolDescriptor.generic("bean#method", "description"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool.name")
                .hasMessageContaining("registered skill name");
    }


}
