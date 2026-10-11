package ai.loomspan.internal.observability.web;

import ai.loomspan.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TypeSafeRegisteredInspectionIntegrationTest.App.class, properties = {
    "loomspan.observability.enabled=true",
    "loomspan.observability.auth.api-key=0123456789abcdef0123456789abcdef",
    "loomspan.skills.locations=classpath:/typesafe-inspection-test/*.yaml",
    "loomspan.connections.native.driver=typesafe", "loomspan.connections.native.api-key=fixture",
    "loomspan.models.native.connection=native", "loomspan.models.native.provider-model=jev"
})
@AutoConfigureMockMvc
class TypeSafeRegisteredInspectionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SkillReloader reloader;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    @Test
    void discoveredAndSuppliedNativeMetadataTraverseAuthenticatedRoutes() throws Exception {
        String discovered = Files.readString(Path.of("src/test/resources/typesafe-inspection-test/judgment.yaml"));
        mvc.perform(get(ObservabilityApiPaths.SKILLS)).andExpect(status().isUnauthorized());
        inspect("judgment.yaml", discovered);
        String supplied = "# Original supplied document\n" + discovered.replace("{A: null}", "{B: null, C: null}") + "\n";
        String label = " opaque ../ Native: <label> ";
        String previous = reloader.snapshot().generationId();
        reloader.publish(reloader.prepare(List.of(new SkillDocument(label, supplied))));
        assertThat(reloader.snapshot().generationId()).isNotEqualTo(previous);
        inspect(label, supplied);
    }

    private void inspect(String location, String yaml) throws Exception {
        var summary = JSON.readTree(mvc.perform(get(ObservabilityApiPaths.SKILLS)
                .header(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("items");
        assertThat(summary.size()).isEqualTo(1);
        var item = summary.get(0);
        assertThat(item.path("registeredName").asText()).isEqualTo("judgment");
        assertThat(item.path("source").asText()).isEqualTo("TYPESAFE");
        assertThat(item.path("sourcePath").asText()).isEqualTo(location).isNotBlank();
        for (String omitted : List.of("yaml", "outputSchema", "beanName", "method")) assertThat(item.has(omitted)).isFalse();
        var detail = JSON.readTree(mvc.perform(get(ObservabilityApiPaths.SKILLS + "/judgment")
                .header(ObservabilityApiKeyFilter.API_KEY_HEADER, KEY)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(detail.path("source").asText()).isEqualTo("TYPESAFE");
        assertThat(detail.path("sourcePath").asText()).isEqualTo(location);
        assertThat(detail.path("yaml").asText()).isEqualTo(yaml);
        assertThat(detail.path("outputSchema").asText()).isEqualTo(reloader.snapshot().skill("judgment").orElseThrow().outputSchema());
        assertThat(detail.has("beanName")).isFalse();
        assertThat(detail.has("method")).isFalse();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class},
        excludeName = "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration")
    static class App {}
}
