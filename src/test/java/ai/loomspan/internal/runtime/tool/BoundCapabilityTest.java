package ai.loomspan.internal.runtime.tool;

import ai.loomspan.internal.core.CapabilityKind;
import ai.loomspan.internal.core.CapabilityMetadata;
import ai.loomspan.internal.core.CapabilityToolDescriptor;
import ai.loomspan.internal.core.SkillExecutionDescriptor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundCapabilityTest
{
    @Test
    void preservesExplicitNullArgumentsInAnImmutableDefensiveCopy()
    {
        AtomicReference<Map<String, Object>> observed = new AtomicReference<>();
        BoundCapability capability = new BoundCapability(metadata(), (arguments, linkedTaskId) -> {
            observed.set(arguments);
            return null;
        });
        LinkedHashMap<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("optional", null);

        capability.invoke(supplied, "task-1");
        supplied.put("later", "mutation");

        assertThat(observed.get()).containsEntry("optional", null).doesNotContainKey("later");
        assertThatThrownBy(() -> observed.get().put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static CapabilityMetadata metadata()
    {
        return new CapabilityMetadata("test:null", "nullableTool", "Nullable tool",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of()), ignored -> null, CapabilityKind.YAML_SKILL,
                new CapabilityToolDescriptor("nullableTool", "Nullable tool",
                        "{\"type\":\"object\",\"properties\":{\"optional\":{\"type\":\"string\"}}}"),
                null, null);
    }
}
