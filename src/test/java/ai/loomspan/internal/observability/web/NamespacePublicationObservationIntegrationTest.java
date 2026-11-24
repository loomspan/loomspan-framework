package ai.loomspan.internal.observability.web;

import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.NamespaceVisibility;
import ai.loomspan.api.SkillReloader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespacePublicationObservationIntegrationTest
{
    private static final String KEY = "0123456789abcdef0123456789abcdef";
    private static final ExecutionConfiguration EMPTY = new ExecutionConfiguration("loomspan: {}");

    @Test
    void instanceReportsEffectiveNamespacePoliciesBeforeInvocation() throws Exception
    {
        try (var context = engine()) {
            var reloader = context.getBean(SkillReloader.class);
            var installed = reloader.prepare("empty", "P1", List.of(), policy("always"), NamespaceVisibility.PRIVATE);
            reloader.publish(installed);
            var snapshot = read(context);
            assertPublication(snapshot, "default", reloader.snapshot("default").generationId(), null);
            assertPublication(snapshot, "empty", installed.generationId(), "P1");
            assertPolicy(snapshot, "default", "ONERROR");
            assertPolicy(snapshot, "empty", "ALWAYS");
            assertThat(snapshot.path("tracePersistencePolicy").asString()).isEqualTo("ONERROR");
            assertThat(snapshot.path("activeExecutionCount").asInt()).isZero();
            assertThat(snapshot.path("catalogedTraceCount").asInt()).isZero();

            var replacement = reloader.prepare("empty", "P2", List.of(), policy("never"), NamespaceVisibility.PRIVATE);
            assertPublication(read(context), "empty", installed.generationId(), "P1");
            assertPolicy(read(context), "empty", "ALWAYS");
            reloader.publish(replacement);
            assertPublication(read(context), "empty", replacement.generationId(), "P2");
            assertPolicy(read(context), "empty", "NEVER");

            var unlabeled = reloader.prepare("unlabeled", List.of(), policy("never"));
            reloader.publish(unlabeled);
            assertPublication(read(context), "unlabeled", unlabeled.generationId(), null);
            assertPolicy(read(context), "unlabeled", "NEVER");

            var defaultReplacement = reloader.prepare("default", List.of(), policy("never"));
            reloader.publish(defaultReplacement);
            assertThat(read(context).path("tracePersistencePolicy").asString()).isEqualTo("NEVER");
            var always = reloader.prepare("always", List.of(), policy("always"));
            reloader.publish(always);
            reloader.remove("default", defaultReplacement.generationId());
            var withoutDefault = read(context);
            assertThat(withoutDefault.path("tracePersistencePolicy").asString()).isEqualTo("ONERROR");
            assertPolicy(withoutDefault, "always", "ALWAYS");
        }
    }

    private static ExecutionConfiguration policy(String value)
    {
        return new ExecutionConfiguration("loomspan: {execution-trace: {persistence: " + value + "}}");
    }

    private static void assertPolicy(JsonNode instance, String namespace, String policy)
    {
        for (var row : instance.path("namespaces")) {
            if (row.path("namespace").asString().equals(namespace)) {
                assertThat(row.has("tracePersistencePolicy")).isTrue();
                assertThat(row.path("tracePersistencePolicy").asString()).isEqualTo(policy);
                return;
            }
        }
        throw new AssertionError("Missing namespace " + namespace);
    }

    @Test
    void independentEnginesExposeOnlySuccessfulPublicationsAndRestartInstallation() throws Exception
    {
        String eastGeneration;
        String eastInstance;
        try (var west = engine()) {
            var westReloader = west.getBean(SkillReloader.class);
            var westP7 = westReloader.prepare("empty", "P7", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
            westReloader.publish(westP7);
            try (var east = engine()) {
                var reloader = east.getBean(SkillReloader.class);
                var p7 = reloader.prepare("empty", "P7", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                reloader.publish(p7);
                var before = read(east);
                eastInstance = before.path("instanceId").asString();
                eastGeneration = p7.generationId();
                assertPublication(before, "empty", p7.generationId(), "P7");
                assertPublication(read(west), "empty", westP7.generationId(), "P7");
                assertThat(p7.generationId()).isNotEqualTo(westP7.generationId());
                assertThat(before.path("activeExecutionCount").asInt()).isZero();
                assertThat(before.path("catalogedTraceCount").asInt()).isZero();

                var p8 = reloader.prepare("empty", "P8", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                assertThat(p8.snapshot().publicationVersion()).contains("P8");
                assertThat(p8.snapshot().generationId()).isNotEqualTo(p7.generationId());
                assertThat(reloader.snapshot("empty").publicationVersion()).contains("P7");
                assertPublication(read(east), "empty", p7.generationId(), "P7");
                var abandoned = reloader.prepare("empty", "abandoned", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                abandoned.close();
                assertThatThrownBy(() -> reloader.publish(abandoned)).hasMessageContaining("closed");
                assertPublication(read(east), "empty", p7.generationId(), "P7");
                var stale = reloader.prepare("empty", "stale", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                reloader.publish(p8);
                assertThatThrownBy(() -> reloader.publish(stale)).hasMessageContaining("stale");
                assertPublication(read(east), "empty", p8.generationId(), "P8");
                assertPublication(read(west), "empty", westP7.generationId(), "P7");

                var replacement = reloader.prepare("empty", List.of(), EMPTY);
                reloader.publish(replacement);
                assertPublication(read(east), "empty", replacement.generationId(), null);
                for (String label : List.of("", " \t\n", "<P7>\"☃")) {
                    var exact = reloader.prepare("empty", label, List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                    reloader.publish(exact);
                    assertPublication(read(east), "empty", exact.generationId(), label);
                }
                for (String namespace : List.of("default", "empty")) {
                    reloader.remove(namespace, reloader.snapshot(namespace).generationId());
                }
                assertThat(read(east).path("namespaces").size()).isZero();
            }
            try (var restarted = engine()) {
                var reloader = restarted.getBean(SkillReloader.class);
                var reinstalled = reloader.prepare("empty", "P7", List.of(), EMPTY, NamespaceVisibility.PRIVATE);
                reloader.publish(reinstalled);
                var current = read(restarted);
                assertThat(current.path("instanceId").asString()).isNotEqualTo(eastInstance);
                assertThat(reinstalled.generationId()).isNotEqualTo(eastGeneration);
                assertPublication(current, "empty", reinstalled.generationId(), "P7");
                assertPublication(read(west), "empty", westP7.generationId(), "P7");
            }
        }
    }

    private static ConfigurableApplicationContext engine()
    {
        return new SpringApplicationBuilder(ObservabilityRestIntegrationTest.TestApplication.class)
                .web(WebApplicationType.SERVLET).properties(
                        "server.port=0", "spring.main.banner-mode=off",
                        "loomspan.observability.enabled=true", "loomspan.observability.auth.api-key=" + KEY,
                        "loomspan.skills.locations=classpath:/publication-no-files/*.yaml")
                .run();
    }

    private static JsonNode read(ConfigurableApplicationContext context) throws Exception
    {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + ObservabilityApiPaths.INSTANCE))
                .header(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            var result = JsonMapper.builder().build().readTree(response.body());
            assertThat(response.headers().firstValue(ObservabilityApiKeyFilter.INSTANCE_HEADER))
                    .contains(result.path("instanceId").asString());
            return result;
        }
    }

    private static void assertPublication(JsonNode instance, String namespace, String id, String label)
    {
        JsonNode row = null;
        for (var candidate : instance.path("namespaces")) {
            if (candidate.path("namespace").asString().equals(namespace)) row = candidate;
        }
        assertThat(row).isNotNull();
        assertThat(row.path("generationId").asString()).isEqualTo(id);
        assertThat(row.has("publicationVersion")).isTrue();
        if (label == null) assertThat(row.path("publicationVersion").isNull()).isTrue();
        else assertThat(row.path("publicationVersion").asString()).isEqualTo(label);
    }
}
