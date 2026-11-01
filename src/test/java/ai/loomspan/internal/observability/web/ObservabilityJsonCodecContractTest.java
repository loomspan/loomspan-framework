package ai.loomspan.internal.observability.web;

import tools.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retained REST/SSE diagnostic contract rows exercised through the production codec. */
class ObservabilityJsonCodecContractTest
{
    private final ObservabilityJsonCodec codec = new ObservabilityJsonCodec();

    @Test
    void observabilityRolePreservesRecordTimeNullOmissionNumericKindsAndInsertionOrder() throws Exception
    {
        Map<String, Number> values = new LinkedHashMap<>();
        values.put("integer", 7);
        values.put("decimal", 7.25d);
        Probe value = new Probe("probe-1", Instant.parse("2026-07-24T12:00:00Z"), null, values);

        String encoded = new String(codec.write(value), StandardCharsets.UTF_8);

        assertThat(encoded).isEqualTo("""
                {"id":"probe-1","observedAt":"2026-07-24T12:00:00Z","optional":null,"values":{"integer":7,"decimal":7.25}}""");
        assertThat(codec.read(encoded.getBytes(StandardCharsets.UTF_8), Probe.class)).isEqualTo(value);
    }

    @Test
    void observabilityRoleRejectsUnknownFields()
    {
        byte[] input = """
                {"id":"probe-1","observedAt":"2026-07-24T12:00:00Z","optional":null,"values":{},"future":true}"""
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> codec.read(input, Probe.class))
                .isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("future");
    }

    @Test
    void skillDetailsRoundTripWithOnlyApplicableSourceFields() throws Exception
    {
        var java = new ai.loomspan.internal.observability.web.dto.ObservabilityDtos.SkillDetail(
                "Lookup", "JAVA", "SKILL_METHOD", null, null, "lookupBean", "example.Lookup.find(java.lang.String)", null, null);
        String encoded = new String(codec.write(java), StandardCharsets.UTF_8);
        assertThat(encoded).contains("\"kind\":\"JAVA\"", "\"declaration\":\"SKILL_METHOD\"", "\"beanName\":\"lookupBean\"")
                .doesNotContain("sourcePath", "yaml");
        assertThat(codec.read(encoded.getBytes(StandardCharsets.UTF_8), java.getClass())).isEqualTo(java);
    }

    @Test
    void rejectsMissingUnknownAndInconsistentSkillProvenance()
    {
        for (String value : new String[] {
                "{\"registeredName\":\"Lookup\",\"sourcePath\":\"a.yaml\",\"yaml\":\"name: Lookup\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"OTHER\",\"declaration\":\"YAML\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"REST\",\"declaration\":\"YAML\",\"sourcePath\":\"a.yaml\",\"yaml\":\"x\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"JAVA\",\"declaration\":\"SKILL_METHOD\",\"beanName\":\"bean\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"JAVA\",\"declaration\":\"SKILL_METHOD\",\"beanName\":\"bean\",\"method\":\"lookup()\",\"yaml\":\"invalid\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"YAML\",\"declaration\":\"YAML\",\"sourcePath\":\"a.yaml\",\"beanName\":\"bean\",\"yaml\":\"x\"}",
                "{\"registeredName\":\"Lookup\",\"kind\":\"JAVA\",\"declaration\":\"YAML\",\"sourcePath\":\"a.yaml\",\"beanName\":\"bean\",\"method\":\"lookup()\",\"yaml\":\"executor: run\"}" })
        {
            assertThatThrownBy(() -> codec.read(value.getBytes(StandardCharsets.UTF_8),
                    ai.loomspan.internal.observability.web.dto.ObservabilityDtos.SkillDetail.class))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private record Probe(String id, Instant observedAt, String optional, Map<String, Number> values)
    {
    }
}
