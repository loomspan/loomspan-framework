package ai.loomspan.internal.skill;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.core.*;
import ai.loomspan.internal.runtime.input.SkillInputContractResolver;
import ai.loomspan.internal.security.SkillAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparedCandidateVariablesTest {
    private static final ExecutionConfiguration CONFIG = new ExecutionConfiguration("""
            loomspan:
              connections:
                api:
                  driver: openai
                  api-key: {$var: shared}
                  headers: {X-Region: {$var: shared}, Accept: application/json}
              executor-config:
                sales:
                  targets: {value: {$var: shared}, literal: '${region}'}
            """);
    private static final SkillDocument LEAF = new SkillDocument("leaf.yaml", """
            name: leaf
            description: Fixture leaf
            executor: http
            config: {value: {$var: shared}, literal: '${region}', native: [2.5, 7, true]}
            """);

    @Test void allOverloadsResolveOnceAndPublishOnlyCapturedValues() {
        try (var f = new Fixture()) {
            f.environment.withProperty("region", "west").withProperty("shared", "${region}");
            for (int mode = 0; mode < 6; mode++) {
                f.environment.lookups.clear();
                var values = new LinkedHashMap<>(Map.of("shared", "${region}"));
                var candidate = switch (mode) {
                    case 0 -> f.reloader.prepare("sales", List.of(LEAF), CONFIG);
                    case 1 -> f.reloader.prepare("sales", List.of(LEAF), CONFIG, NamespaceVisibility.PRIVATE);
                    case 2 -> f.reloader.prepare("sales", "same-version", List.of(LEAF), CONFIG, NamespaceVisibility.PRIVATE);
                    case 3 -> f.reloader.prepare("sales", List.of(LEAF), CONFIG, values);
                    case 4 -> f.reloader.prepare("sales", List.of(LEAF), CONFIG, NamespaceVisibility.PRIVATE, values);
                    default -> f.reloader.prepare("sales", "same-version", List.of(LEAF), CONFIG, NamespaceVisibility.PRIVATE, values);
                };
                String expected = mode < 3 ? "west" : "${region}";
                values.put("shared", "changed");
                int lookups = f.environment.lookups.getOrDefault("shared", 0);
                assertThat(lookups).isEqualTo(mode < 3 ? 1 : 0);
                f.reloader.publish(candidate);
                assertThat(f.environment.lookups.getOrDefault("shared", 0)).isEqualTo(lookups);
                var generation = f.manager.active("sales");
                assertThat(generation.runtime().properties().getConnections().get("api").getApiKey()).isEqualTo(expected);
                assertThat(generation.executorConfiguration().get("targets")).isEqualTo(Map.of("value", expected, "literal", "${region}"));
                assertThat(generation.definition("sales:leaf").manifest().getConfig()).containsEntry("value", expected)
                        .containsEntry("literal", "${region}");
                assertThat(new String(generation.definition("sales:leaf").source().bytes(), java.nio.charset.StandardCharsets.UTF_8)).contains("{$var: shared}");
                candidate.close();
            }
        }
    }

    @Test void stagingDoesNoLookupAndChecksDecidableLiteralsAndDependencies() {
        try (var f = new Fixture()) {
            int builds = f.builds.get();
            assertThat(f.reloader.validate("sales", List.of(LEAF), CONFIG).valid()).isTrue();
            assertThat(f.environment.lookups).isEmpty();
            assertThat(f.builds).hasValue(builds);
            for (String fields : List.of("request-timeout: 0ms", "provider-retry: {max-attempts: 0}",
                    "headers: {'Bad Header': {$var: shared}}", "headers: {X-Null: null}",
                    "gemini: {vertex-ai: true}", "driver: absent")) {
                String yaml = "loomspan:\n  connections:\n    api:\n      driver: openai\n      api-key: {$var: shared}\n" + fields.indent(6);
                if (fields.startsWith("driver:")) yaml = yaml.replace("      driver: openai\n", "");
                assertThat(f.reloader.validate("sales", List.of(), new ExecutionConfiguration(yaml)).valid()).as(fields).isFalse();
            }
            var unknownExecutor = new SkillDocument("unknown.yaml", LEAF.yaml().replace("executor: http", "executor: absent"));
            assertThat(f.reloader.validate("sales", List.of(unknownExecutor), CONFIG).valid()).isFalse();
            assertThat(f.environment.lookups).isEmpty();
            assertThat(f.builds).hasValue(builds);
        }
    }

    @Test void dynamicSelectorDefersOnlyMissingEffectiveKeys() {
        try (var f = new Fixture()) {
            var dynamic = new ExecutionConfiguration("loomspan: {executor-config: {sales: {{$var: selector}: {value: literal}}}}");
            assertThat(f.reloader.validate("sales", List.of(LEAF), dynamic).valid()).isTrue();
            var invalidOwner = new ExecutionConfiguration(dynamic.yaml().replace("sales:", "Sales:"));
            assertThat(f.reloader.validate("sales", List.of(LEAF), invalidOwner).valid()).isFalse();
            assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(LEAF), invalidOwner,
                    Map.of("selector", "targets", "shared", "value")))
                    .isInstanceOf(SkillReloadException.class).hasMessageContaining("Sales");
            var staged = new PreparedCandidateVariables(dynamic, List.of(LEAF));
            assertThat(staged.unknownSelector("sales", "targets")).isTrue();
            assertThat(staged.unknownSelector("Sales", "targets")).isFalse();
            assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(LEAF), dynamic,
                    Map.of("selector", "other", "shared", "value"))).hasMessageContaining("must select a map");
            try (var candidate = f.reloader.prepare("sales", List.of(LEAF), dynamic,
                    Map.of("selector", "targets", "shared", "value"))) {
                assertThat(candidate).isNotNull();
            }
            for (String data : List.of("{targets: {$var: map}}", "{targets: []}", "{other: {}}",
                    "{{$var: selector}: scalar}", "{{$var: selector}: {$var: value}}"))
                assertThat(f.reloader.validate("sales", List.of(LEAF), new ExecutionConfiguration(
                        "loomspan: {executor-config: {sales: " + data + "}}" )).valid()).as(data).isFalse();
        }
    }

    @Test void exactMapsCountSkillOnlyNamesAndRejectMissingBlankUnusedAndNull() {
        try (var f = new Fixture()) {
            f.environment.withProperty("shared", "host");
            var empty = new ExecutionConfiguration("loomspan: {executor-config: {sales: {targets: {}}}}");
            try (var candidate = f.reloader.prepare("sales", List.of(LEAF), empty, Map.of("shared", "leaf-only"))) {
                f.reloader.publish(candidate);
                assertThat(f.manager.active("sales").definition("sales:leaf").manifest().getConfig()).containsEntry("value", "leaf-only");
            }
            for (Map<String, String> map : List.of(Map.<String, String>of(), Map.of("Shared", "different-case"),
                    Map.of("shared", "\u00a0"), Map.of("shared", "value", "extra", "private-supplied-value"))) {
                assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(LEAF), empty, map))
                        .isInstanceOf(SkillReloadException.class).hasMessageNotContaining("private-supplied-value");
            }
            assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(), empty, (Map<String, String>) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(), empty, NamespaceVisibility.PRIVATE, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> f.reloader.prepare("sales", "version", List.of(), empty, NamespaceVisibility.PRIVATE, null)).isInstanceOf(NullPointerException.class);
            assertThat(f.environment.lookups).isEmpty();
        }
    }

    @Test void editedCandidateRequiresItsOwnNewVariablesAndFailureKeepsOldGeneration() {
        try (var f = new Fixture()) {
            var first = f.reloader.prepare("sales", "version", List.of(LEAF), CONFIG, NamespaceVisibility.PRIVATE, Map.of("shared", "first"));
            f.reloader.publish(first);
            assertThat(f.reloader.validate("sales", List.of(LEAF), CONFIG).valid()).isTrue();
            var edited = new SkillDocument(LEAF.sourceName(), LEAF.yaml().replace("value: {$var: shared}", "value: {$var: added}"));
            int builds = f.builds.get();
            assertThatThrownBy(() -> f.reloader.prepare("sales", "version", List.of(edited), CONFIG, NamespaceVisibility.PRIVATE, Map.of("shared", "first")))
                    .hasMessageContaining("leaf.yaml").hasMessageContaining("config.value").hasMessageContaining("missing-value");
            assertThat(f.builds).hasValue(builds);
            assertThat(f.reloader.snapshot("sales").generationId()).isEqualTo(first.generationId());
            var next = f.reloader.prepare("sales", "version", List.of(edited), CONFIG, NamespaceVisibility.PRIVATE,
                    Map.of("shared", "second", "added", "a: b\n- c\n"));
            f.reloader.publish(next);
            assertThat(f.manager.active("sales").definition("sales:leaf").manifest().getConfig()).containsEntry("value", "a: b\n- c\n");
            assertThat(next.generationId()).isNotEqualTo(first.generationId());
        }
    }

    @Test void collisionDiagnosticsUseBothAuthoredPathsAndNoRuntime() {
        try (var f = new Fixture()) {
            var config = new ExecutionConfiguration(CONFIG.yaml().replace("X-Region: {$var: shared}", "{$var: header}: {$var: shared}, X-Region: literal"));
            int builds = f.builds.get();
            assertThatThrownBy(() -> f.reloader.prepare("sales", List.of(LEAF), config, Map.of("shared", "private-supplied-value", "header", "X-Region")))
                    .hasMessageContaining("key-collision").hasMessageContaining("headers{key#0}").hasMessageContaining("headers.X-Region")
                    .hasMessageNotContaining("private-supplied-value");
            assertThat(f.builds).hasValue(builds);
            try (var candidate = f.reloader.prepare("sales", List.of(LEAF), config, Map.of("shared", "exact", "header", "X!Token"))) {
                f.reloader.publish(candidate);
                assertThat(f.manager.active("sales").runtime().properties().getConnections().get("api").getHeaders())
                        .containsEntry("X!Token", "exact").containsEntry("X-Region", "literal").containsEntry("Accept", "application/json");
            }
        }
    }

    @Test void concurrentNamespacesUseTheirOwnValuesWhileHostHasAThird() throws Exception {
        try (var f = new Fixture(); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            f.environment.withProperty("shared", "host-third");
            var config = new ExecutionConfiguration("loomspan: {connections: {api: {driver: openai, api-key: {$var: shared}}}}");
            var start = new CountDownLatch(1);
            var a = workers.submit(() -> { SkillReloaderTest.await(start); return f.reloader.prepare("a", List.of(), config, Map.of("shared", "first")); });
            var b = workers.submit(() -> { SkillReloaderTest.await(start); return f.reloader.prepare("b", List.of(), config, Map.of("shared", "second")); });
            start.countDown();
            try (var first = a.get(5, TimeUnit.SECONDS); var second = b.get(5, TimeUnit.SECONDS)) {
                f.reloader.publish(first); f.reloader.publish(second);
                assertThat(f.manager.active("a").runtime().properties().getConnections().get("api").getApiKey()).isEqualTo("first");
                assertThat(f.manager.active("b").runtime().properties().getConnections().get("api").getApiKey()).isEqualTo("second");
                assertThat(f.environment.lookups).isEmpty();
            }
        }
    }

    @Test void hostValuesAreFreshPerPreparationAndReturnedPlaceholderTextIsTerminal() {
        try (var f = new Fixture()) {
            f.environment.withProperty("region", "west").withProperty("report.label", "${region}");
            var configuration = new ExecutionConfiguration(CONFIG.yaml().replace("$var: shared", "$var: report.label"));
            var leaf = new SkillDocument(LEAF.sourceName(), LEAF.yaml().replace("$var: shared", "$var: report.label"));
            var first = f.reloader.prepare("sales", "same", List.of(leaf), configuration, NamespaceVisibility.PRIVATE);
            f.environment.withProperty("report.label", "east");
            f.reloader.publish(first);
            assertThat(f.manager.active("sales").definition("sales:leaf").manifest().getConfig()).containsEntry("value", "west");
            assertThat(f.environment.lookups).containsEntry("report.label", 1);
            var second = f.reloader.prepare("sales", "same", List.of(leaf), configuration, NamespaceVisibility.PRIVATE);
            f.reloader.publish(second);
            assertThat(f.manager.active("sales").definition("sales:leaf").manifest().getConfig()).containsEntry("value", "east");
            assertThat(f.environment.lookups).containsEntry("report.label", 2);
            f.environment.direct.put("report.label", "${still.literal}");
            var third = f.reloader.prepare("sales", "same", List.of(leaf), configuration, NamespaceVisibility.PRIVATE);
            f.reloader.publish(third);
            var generation = f.manager.active("sales");
            assertThat(generation.runtime().properties().getConnections().get("api").getApiKey()).isEqualTo("${still.literal}");
            assertThat(generation.definition("sales:leaf").manifest().getConfig()).containsEntry("value", "${still.literal}");
            assertThat(f.environment.lookups).containsEntry("report.label", 3).doesNotContainKey("still.literal");
        }
    }

    @Test void resolvedConfigKeepsDeclarationPrecisionAndOriginalProvenance() {
        try (var f = new Fixture()) {
            String decimal = "0.12345678901234567890123456789";
            var document = new SkillDocument("precise.yaml", LEAF.yaml() + "input_schema:\n  type: object\n  properties:\n    value: {type: number, minimum: "
                    + decimal + ", maximum: " + decimal + "}\n");
            try (var candidate = f.reloader.prepare("sales", List.of(document), CONFIG, Map.of("shared", "a: b\n- c\n"))) {
                f.reloader.publish(candidate);
                var definition = f.manager.active("sales").definition("sales:leaf");
                assertThat(new String(definition.source().bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(document.yaml());
                assertThat(definition.source().diagnosticName()).isEqualTo("precise.yaml");
                assertThat(definition.manifest().getInputSchema().toString()).contains(decimal);
                assertThat(definition.manifest().getConfig()).containsEntry("value", "a: b\n- c\n")
                        .containsEntry("native", List.of(2.5d, 7, true));
                assertThatThrownBy(() -> definition.manifest().getConfig().put("added", "value")).isInstanceOf(UnsupportedOperationException.class);
            }
        }
    }

    @Test void resolvedConfigOverrideDoesNotLeakIntoCatalogReuse() {
        var catalog = new YamlSkillCatalog(new LoomspanProperties());
        var overridden = catalog.checkedSupplied("sales", List.of(LEAF), false,
                Map.of(LEAF.sourceName(), Map.of("config", Map.of("value", "override"))));
        assertThat(overridden.definitions().getFirst().manifest().getConfig()).containsEntry("value", "override");
        var next = new SkillDocument(LEAF.sourceName(), LEAF.yaml().replace("{$var: shared}", "literal-next"));
        var ordinary = catalog.checkedSupplied("sales", List.of(next), false);
        assertThat(ordinary.definitions().getFirst().manifest().getConfig()).containsEntry("value", "literal-next");
    }

    static final class CountingEnvironment extends MockEnvironment {
        final Map<String, Integer> lookups = new ConcurrentHashMap<>();
        final Map<String, String> direct = new ConcurrentHashMap<>();
        @Override public String getProperty(String name) {
            lookups.merge(name, 1, Integer::sum);
            if (direct.containsKey(name)) return direct.get(name);
            return super.getProperty(name);
        }
    }

    static final class Fixture implements AutoCloseable {
        final CountingEnvironment environment = new CountingEnvironment();
        final AtomicInteger builds = new AtomicInteger();
        final SkillGenerationManager manager;
        final FrameworkExecutionLifecycle lifecycle;
        final DefaultSkillReloader reloader;
        Fixture() {
            var properties = new LoomspanProperties();
            properties.getSkills().setLocations(List.of("classpath:/publication-no-files/*.yaml"));
            properties.setExecutorConfig(ExecutorConfiguration.from(Map.of("sales", Map.of("targets", Map.of()))));
            var methods = mock(SkillMethodBeanPostProcessor.class);
            when(methods.capabilities()).thenReturn(List.of());
            when(methods.executors()).thenReturn(List.of(new SkillExecutorRegistration(new SkillIdentity("sales", "http"), "targets",
                    SkillAccessPolicy.unrestricted(), new SkillSource(null, "fixture", "execute", null), invocation -> "result")));
            manager = new SkillGenerationManager(methods, () -> new YamlSkillCatalog(properties), new SkillInputContractResolver(), properties, environment,
                    (config, policy) -> { builds.incrementAndGet(); return SkillReloaderTest.runtime(config, policy); });
            manager.initializeStartup();
            lifecycle = new FrameworkExecutionLifecycle(mock(ApplicationContext.class), Duration.ofMillis(100), Executors.newSingleThreadExecutor());
            reloader = new DefaultSkillReloader(manager, lifecycle);
        }
        @Override public void close() { manager.destroy(); lifecycle.destroy(); }
    }
}
