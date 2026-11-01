package ai.loomspan.internal.security;

import ai.loomspan.api.SkillKind;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.skill.*;
import java.util.Map;

record ExecutorPolicyFixture(CapabilityMetadata capability, SkillAdmissionSnapshot snapshot) {
    static ExecutorPolicyFixture create(SkillAccessPolicy yaml, SkillAccessPolicy java) {
        var manifest = new YamlSkillManifest();
        manifest.setName("leaf"); manifest.setDescription("leaf"); manifest.setExecutor("infra-run");
        var definition = new YamlSkillDefinition(YamlSkillDefinition.Form.EXECUTOR, "caller",
                new org.springframework.core.io.FileSystemResource("leaf.yaml"), manifest, null);
        var capability = new CapabilityMetadata("yaml:caller-leaf", "caller-leaf", "leaf", null, yaml,
                arguments -> { throw new AssertionError("Metadata checks must not invoke business code"); },
                SkillKind.JAVA, CapabilityToolDescriptor.generic("caller-leaf", "leaf"),
                new SkillSource("leaf.yaml", "bean", "run", "infra-run"));
        var registration = new SkillExecutorRegistration(SkillIdentity.parse("infra-run"), "", java,
                new SkillSource(null, "bean", "run", null), invocation -> { throw new AssertionError("Unexpected invocation"); });
        var caller = new SkillGeneration("caller", "caller-generation", Map.of(capability.name(), capability),
                Map.of(capability.name(), definition));
        var owner = new SkillGeneration("infra", "owner-generation", Map.of(), Map.of(), null, null, null,
                ai.loomspan.api.NamespaceVisibility.PUBLIC, Map.of(registration.name(), registration), Map.of());
        return new ExecutorPolicyFixture(capability, new SkillAdmissionSnapshot(capability.identity(),
                Map.of("caller", caller, "infra", owner), Map.of(capability.name(),
                new SkillAdmissionSnapshot.ExecutorBinding(registration, Map.of(), owner.id()))));
    }
}

