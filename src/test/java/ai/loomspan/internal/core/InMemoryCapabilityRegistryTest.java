package ai.loomspan.internal.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryCapabilityRegistryTest {

    @Test
    void returnsNullWhenCapabilityIsMissing() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();

        assertThat(registry.getCapability("missing-capability")).isNull();
    }

    @Test
    void registersAndRetrievesCapabilityByName() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityMetadata metadata = metadata("calculatorAdd", args ->
                ((Number) args.get("left")).intValue() + ((Number) args.get("right")).intValue());

        registry.register(metadata.name(), metadata);

        CapabilityMetadata stored = registry.getCapability(metadata.name());
        assertThat(stored).isNotNull();
        assertThat(stored.id()).isEqualTo("calculatorBean#add");
        assertThat(stored.description()).isEqualTo("Adds two integers.");
        assertThat(stored.accessPolicy().roles()).containsExactly("math-user");
        assertThat(stored.invoker().invoke(Map.of("left", 2, "right", 3))).isEqualTo(5);
        assertThat(registry.getAllCapabilities()).containsExactly(metadata);
    }

    @Test
    void throwsCollisionExceptionForDuplicateCapabilityName() {
        CapabilityRegistry registry = new InMemoryCapabilityRegistry();
        CapabilityMetadata first = metadata("calculatorAdd", args -> 1);
        CapabilityMetadata duplicate = new CapabilityMetadata(
                "calculatorBean#sum",
                "calculatorAdd",
                "Duplicate add operation.",
                SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of("math-admin")),
                args -> 2,
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic("calculatorAdd", "Duplicate add operation."),
                null);

        registry.register(first.name(), first);

        assertThatThrownBy(() -> registry.register(duplicate.name(), duplicate))
                .isInstanceOf(CapabilityCollisionException.class)
                .hasMessageContaining("calculatorAdd");
    }

    @Test
    void rejectsInvalidExactNamesBeforeMutation() {
        for (String name : List.of(" ", " padded", "padded ", "a-b", "a.b", "1start", "é", "a".repeat(65))) {
            var registry = new InMemoryCapabilityRegistry();
            if (name.isBlank()) continue; // Metadata rejects blank names before registration.
            assertThatThrownBy(() -> registry.register(name, metadata(name, args -> "ok")))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Invalid skill name");
            assertThat(registry.getAllCapabilities()).isEmpty();
        }
    }
    @Test
    void supportsPortableBoundaryNamesAndExactCase() {
        var registry = new InMemoryCapabilityRegistry();
        for (String name : List.of("_", "a".repeat(64), "Exact", "exact"))
            registry.register(name, metadata(name, args -> "ok"));
        assertThat(registry.getAllCapabilities()).hasSize(4);
        assertThat(registry.getCapability("EXACT")).isNull();
    }

    @Test
    void supportsConcurrentRegistrationAndReads() throws Exception {
        InMemoryCapabilityRegistry registry = new InMemoryCapabilityRegistry();
        int capabilityCount = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Void>> registrationTasks = new ArrayList<>();
        for (int i = 0; i < capabilityCount; i++) {
            int index = i;
            registrationTasks.add(() -> {
                start.await();
                CapabilityMetadata metadata = new CapabilityMetadata(
                        "bean" + index + "#method" + index,
                        "capability_" + index,
                        "Capability number " + index,
                        SkillExecutionDescriptor.none(), ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of("role-" + index)),
                        args -> index,
                        CapabilityKind.YAML_SKILL,
                        CapabilityToolDescriptor.generic("capability_" + index, "Capability number " + index),
                        null);
                registry.register(metadata.name(), metadata);
                assertThat(registry.getCapability(metadata.name())).isNotNull();
                return null;
            });
        }

        Callable<Void> readTask = () -> {
            start.await();
            for (int i = 0; i < capabilityCount; i++) {
                registry.getAllCapabilities();
            }
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        registrationTasks.forEach(task -> futures.add(executor.submit(task)));
        futures.add(executor.submit(readTask));

        start.countDown();

        for (Future<Void> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.getAllCapabilities()).hasSize(capabilityCount);
    }

    private static CapabilityMetadata metadata(String name, CapabilityInvoker invoker) {
        return new CapabilityMetadata(
                "calculatorBean#add",
                name,
                "Adds two integers.",
                null, ai.loomspan.internal.security.SkillAccessPolicy.yamlRoles(Set.of("math-user")),
                invoker,
                CapabilityKind.YAML_SKILL,
                CapabilityToolDescriptor.generic(name, "Adds two integers."),
                null);
    }
}
