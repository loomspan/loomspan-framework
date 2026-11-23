package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.internal.core.CapturedNamespaceMetadata;
import ai.loomspan.internal.skill.SkillGenerationManager;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class NamespaceObservationIntegrationTest
{
    @Test
    void hostRootAndEntryNamespaceOwnCrossNamespaceTrace(@org.junit.jupiter.api.io.TempDir Path directory) throws Exception
    {
        Path root = directory.resolve("host root");
        Path redirected = directory.resolve("published redirect");
        try (var server = new MockWebServer())
        {
            server.enqueue(NamespaceStartupIntegrationTest.tool("shared-leaf", "{}"));
            server.enqueue(NamespaceStartupIntegrationTest.text("finance complete"));
            var configuration = new ExecutionConfiguration(NamespaceStartupIntegrationTest.modelConfiguration(server).yaml()
                    + "  execution-trace: {persistence: always}\n");
            NamespaceStartupIntegrationTest.runner().withPropertyValues("fixture-key=test-key",
                    "loomspan.execution-trace.directory=" + root, "loomspan.execution-trace.persistence=never")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var reloader = context.getBean(SkillReloader.class);
                        var template = context.getBean(SkillTemplate.class);
                        var shared = reloader.prepare("shared", List.of(new SkillDocument("shared leaf", """
                                name: leaf
                                description: shared executor
                                executor: fixture
                                """)), new ExecutionConfiguration("loomspan: {execution-trace: {persistence: always}}"),
                                NamespaceVisibility.PUBLIC);
                        reloader.publish(shared);
                        reloader.publish(reloader.prepare("finance", List.of(new SkillDocument("finance caller", """
                                name: entry
                                description: finance caller
                                model: model
                                allowed_skills: [{name: shared-leaf}]
                                """)), configuration));
                        assertThatThrownBy(() -> reloader.prepare("finance", List.of(), new ExecutionConfiguration(
                                "loomspan: {execution-trace: {persistence: always, directory: '" + redirected + "'}}")))
                                .isInstanceOf(SkillReloadException.class).hasMessageContaining("loomspan.execution-trace.directory");
                        var observations = new ArrayList<SkillExecutionView>();
                        assertThat(template.invoke("FiNaNcE-entry", Map.of(), observations::add)).contains("finance complete");
                        try {
                            assertThat(root.resolve("shared")).doesNotExist();
                            Path financeTrace;
                            try (var paths = Files.list(root.resolve("finance"))) {
                                var files = paths.toList();
                                assertThat(files).hasSize(1);
                                financeTrace = files.getFirst();
                            }
                            var mapper = JsonMapper.builder().build();
                            var records = Files.readAllLines(financeTrace).stream().map(mapper::readTree).toList();
                            var metadata = records.getFirst().path("metadata");
                            assertThat(metadata.path("entryNamespace").asText()).isEqualTo("finance");
                            assertThat(metadata.path("tracePath").asText()).isEqualTo(financeTrace + ".in-progress");
                            assertThat(records.stream().map(record -> record.path("traceId").asText()).distinct().count()).isOne();
                            assertThat(records.stream().map(record -> record.path("sessionId").asText()).distinct())
                                    .containsExactly(observations.getFirst().sessionId());
                            for (int index = 0; index < records.size(); index++)
                                assertThat(records.get(index).path("sequence").asLong()).isEqualTo(index + 1L);
                            assertThat(records).anySatisfy(record -> {
                                assertThat(record.path("route").asText()).isEqualTo("shared-leaf");
                                assertThat(record.path("metadata").path("executingNamespace").asText()).isEqualTo("shared");
                            });
                            assertThat(template.invoke("shared-leaf", Map.of())).contains("executor");
                            try (var paths = Files.list(root.resolve("shared"))) {
                                assertThat(paths.toList()).hasSize(1).allSatisfy(path -> assertThat(path).isRegularFile());
                            }
                            try (var paths = Files.list(root)) {
                                assertThat(paths.toList()).containsExactlyInAnyOrder(root.resolve("finance"), root.resolve("shared"));
                            }
                            assertThat(redirected).doesNotExist();
                        } catch (Exception failure) { throw new AssertionError(failure); }
                    });
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    void capturedMapAndTargetOwnerSurviveRemovalAndRecreation() throws Exception
    {
        try (var server = new MockWebServer())
        {
            server.enqueue(NamespaceStartupIntegrationTest.tool("dept-fixed", "{\"value\":\"payload\"}"));
            server.enqueue(NamespaceStartupIntegrationTest.text("unchanged result"));
            var configuration = new ExecutionConfiguration(
                    NamespaceStartupIntegrationTest.modelConfiguration(server).yaml()
                            + "  execution-trace: {persistence: always}\n");
            NamespaceStartupIntegrationTest.runner().withPropertyValues("fixture-key=test-key").run(context -> {
                assertThat(context).hasNotFailed();
                var reloader = context.getBean(SkillReloader.class);
                var template = context.getBean(SkillTemplate.class);
                var shared = reloader.prepare("dept", List.of(), NamespaceStartupIntegrationTest.EMPTY, NamespaceVisibility.PUBLIC);
                reloader.publish(shared);
                var caller = reloader.prepare("caller", List.of(new SkillDocument("caller", """
                        name: fixed
                        description: calls the other fixed
                        model: model
                        allowed_skills: [{name: dept-fixed}]
                        """)), configuration);
                reloader.publish(caller);
                var pending = context.getBean(SkillInvocationHandoff.class).handoff("caller-fixed", Map.of());
                reloader.remove("dept", shared.generationId());
                assertThatThrownBy(() -> template.invoke("caller-fixed", Map.of())).isInstanceOf(SkillException.class);
                var replacement = reloader.prepare("dept", List.of(), NamespaceStartupIntegrationTest.EMPTY, NamespaceVisibility.PUBLIC);
                reloader.publish(replacement);
                assertThat(replacement.generationId()).isNotEqualTo(shared.generationId());
                var observed = new ArrayList<SkillExecutionView>();
                assertThat(pending.invoke(observed::add)).contains("unchanged result");
                assertThat(observed).hasSize(1);
                String sessionId = observed.getFirst().sessionId();
                try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir"), "loomspan-traces", "caller")))
                {
                    Path trace = paths.filter(path -> path.getFileName().toString().startsWith(sessionId + "."))
                            .filter(path -> path.toString().endsWith(".execution-trace.ndjson")).findFirst().orElseThrow();
                    var mapper = JsonMapper.builder().build();
                    var records = Files.readAllLines(trace).stream().map(mapper::readTree).toList();
                    var start = records.getFirst().path("metadata");
                    assertThat(start.path("entrySkill").asText()).isEqualTo("caller-fixed");
                    assertThat(start.path("entryNamespace").asText()).isEqualTo("caller");
                    assertThat(start.path("generationId").asText()).isEqualTo(caller.generationId());
                    assertThat(start.path("namespaceGenerationIds").path("caller").asText()).isEqualTo(caller.generationId());
                    assertThat(start.path("namespaceGenerationIds").path("dept").asText()).isEqualTo(shared.generationId());
                    assertThat(records.stream().map(node -> node.path("sessionId").asText()).distinct()).containsExactly(sessionId);
                    assertThat(records.stream().map(node -> node.path("traceId").asText()).distinct().count()).isOne();
                    for (int index = 0; index < records.size(); index++)
                        assertThat(records.get(index).path("sequence").asLong()).isEqualTo(index + 1L);
                    var targets = records.stream().filter(node -> node.path("route").asText().equals("dept-fixed")).toList();
                    assertThat(targets).isNotEmpty();
                    assertThat(targets).allSatisfy(node -> {
                        assertThat(node.path("metadata").path("executingNamespace").asText()).isEqualTo("dept");
                        assertThat(node.path("metadata").path("executingGenerationId").asText()).isEqualTo(shared.generationId());
                    });
                    assertThat(Files.readString(trace)).contains("payload").contains("unchanged result");
                    Files.delete(trace);
                }
                catch (Exception failure) { throw new AssertionError(failure); }

                // Existing host inspection includes Private namespaces and works after default withdrawal.
                var manager = context.getBean(SkillGenerationManager.class);
                assertThat(manager.registeredSkillCatalog().listAfter(null, 100)).extracting(entry -> entry.registeredName())
                        .contains("caller-fixed", "dept-fixed", "default-local");
                reloader.remove("default", reloader.snapshot("default").generationId());
                assertThat(manager.registeredSkillCatalog().find("caller-fixed")).isPresent();
                assertThat(manager.registeredSkillCatalog().find("dept-fixed")).isPresent();
                reloader.remove("caller", caller.generationId());
                reloader.remove("dept", replacement.generationId());
                assertThat(manager.registeredSkillCatalog().registeredSkillCount()).isZero();
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    @Test
    void primitiveMetadataCopiesAndValidatesEntryAssociation()
    {
        var map = new LinkedHashMap<>(Map.of("orders", "entry-old", "tax", "target-old"));
        var metadata = new CapturedNamespaceMetadata("orders", "entry-old", map);
        map.put("tax", "target-new");
        assertThat(metadata.namespaceGenerationIds()).containsEntry("tax", "target-old");
        assertThatThrownBy(() -> metadata.namespaceGenerationIds().put("tax", "mutated"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new CapturedNamespaceMetadata("orders", "wrong", map))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
