package ai.loomspan.internal.serialization;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillDocumentValuesTest {
    private SkillDocumentValues.Decoded decode(String yaml) {
        return SkillDocumentValues.decode(yaml, "literal-skill.yaml", LoomspanJacksonCodecs.defaults().skillYaml());
    }

    @Test void literalYamlBoundsReachProductionCompilerWithoutRounding() {
        var schema = decode("""
                input_schema:
                  type: object
                  properties:
                    large: {type: number, minimum: 9007199254740993.0, maximum: 9007199254740993.0}
                    fraction: {type: number, minimum: 0.12345678901234567890123456789, maximum: 0.12345678901234567890123456789}
                    exponent: {type: number, minimum: 1.234567890123456789e-30, maximum: 1.234567890123456789e-30}
                  required: [large, fraction, exponent]
                """).root().path("input_schema");
        var json = LoomspanJacksonCodecs.defaults().schemaTree();
        var exact = json.readTree("{\"large\":9007199254740993.0,\"fraction\":0.12345678901234567890123456789,\"exponent\":1.234567890123456789e-30}");
        var yamlExact = decode("large: 9007199254740993.0\nfraction: 0.12345678901234567890123456789\nexponent: 1.234567890123456789e-30").root();
        assertEquals(exact, yamlExact);
        var compiler = new ai.loomspan.internal.schema.StandardSchemaCompiler();
        for (var role : ai.loomspan.internal.schema.StandardSchemaCompiler.Role.values()) {
            var compiled = compiler.compile(schema, role, ai.loomspan.internal.schema.StandardSchemaCompiler.Origin.AUTHORED);
            assertTrue(compiled.validate(exact).isEmpty());
            assertFalse(compiled.validate(json.readTree("{\"large\":9007199254740992,\"fraction\":0.12345678901234567890123456789,\"exponent\":1.234567890123456789e-30}")).isEmpty());
            assertFalse(compiled.validate(json.readTree("{\"large\":9007199254740993,\"fraction\":0.12345678901234567890123456788,\"exponent\":1.234567890123456789e-30}")).isEmpty());
            assertFalse(compiled.validate(json.readTree("{\"large\":9007199254740993,\"fraction\":0.12345678901234567890123456789,\"exponent\":1.234567890123456790e-30}")).isEmpty());
        }
    }

    @Test void preservesLiteralDecimalAndExponentPrecision() {
        JsonNode root = decode("""
                typesafe:
                  data:
                    - large: 9007199254740993.0
                      fraction: 0.123456789012345678901234567890123456789
                      negativeExponent: 1.234567890123456789e-30
                      positiveExponent: 1.234567890123456789e+30
                      integer: 123456789012345678901234567890123456789
                      quoted: '9007199254740993.0'
                      bool: true
                      nil: null
                """).root().path("typesafe").path("data").path(0);
        assertEquals(0, new BigDecimal("9007199254740993.0").compareTo(root.path("large").decimalValue()));
        assertEquals(new BigDecimal("0.123456789012345678901234567890123456789"), root.path("fraction").decimalValue());
        assertEquals(0, new BigDecimal("1.234567890123456789e-30").compareTo(root.path("negativeExponent").decimalValue()));
        assertEquals(0, new BigDecimal("1.234567890123456789e+30").compareTo(root.path("positiveExponent").decimalValue()));
        assertEquals(new BigInteger("123456789012345678901234567890123456789"), root.path("integer").bigIntegerValue());
        assertEquals("9007199254740993.0", root.path("quoted").asText());
        assertTrue(root.path("bool").booleanValue());
        assertTrue(root.path("nil").isNull());
    }

    @Test void rejectsNonStringKeysBeforeTreeConversion() {
        for (String yaml : List.of("1: value\n'1': other", "true: value", "null: value", "input_schema: {properties: {1: {}, '1': {}}}",
                "typesafe: {data: [{false: value}]}", "typesafe: {data: {config: {1: value}}}")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> decode(yaml));
            assertTrue(failure.getMessage().contains("literal-skill.yaml"), failure.getMessage());
            assertTrue(failure.getMessage().contains("map keys must be textual"), failure.getMessage());
        }
        var failure = assertThrows(SkillDocumentValues.InvalidDocumentException.class, () -> decode("typesafe: {data: [{false: value}]}"));
        assertTrue(failure.getMessage().contains("/typesafe/data/0"));
        assertEquals("/typesafe/data/0", failure.path());
        assertEquals("map keys must be textual; found false", failure.detail());
        JsonNode root = decode("'1': one\n'01': zeroOne\n'false': string\nName: upper\nname: lower\n' a/b~c ': exact").root();
        assertEquals(6, root.size());
        assertEquals("exact", root.path(" a/b~c ").asText());
    }

    @Test void rejectsNonfiniteValuesOnlyAtJsonBoundaryWithPaths() {
        for (String scalar : List.of(".nan", ".inf", "-.inf", "+.Inf")) {
            for (String template : List.of("input_schema: {properties: {x: {enum: [%s]}}}",
                    "input_schema: {properties: {x: {minimum: %s}}}", "typesafe: {data: [%s]}")) {
                var failure = assertThrows(IllegalArgumentException.class, () -> decode(template.formatted(scalar)));
                assertTrue(failure.getMessage().contains("literal-skill.yaml at /"));
                assertTrue(failure.getMessage().contains("not JSON-compatible"));
            }
        }
        JsonNode values = decode("data: ['NaN', 'Infinity', '-Infinity']").root().path("data");
        assertEquals("NaN", values.path(0).asText());
        assertEquals("Infinity", values.path(1).asText());
        assertEquals("-Infinity", values.path(2).asText());
    }

    @Test void configKeepsOrdinaryYamlKindsAndDomainData() {
        var decoded = decode("""
                executor: acme.run
                config:
                  fraction: 9007199254740993.0
                  nested: &domain {1: value, infinity: .inf}
                  alias: *domain
                input_schema: {type: object, properties: {config: {type: number, minimum: 9007199254740993.0}}}
                """);
        assertTrue(decoded.configPresent());
        Map<?, ?> config = (Map<?, ?>) decoded.executorConfig();
        assertInstanceOf(Double.class, config.get("fraction"));
        assertEquals(9007199254740992.0, config.get("fraction"));
        assertSame(config.get("nested"), config.get("alias"));
        assertEquals("value", ((Map<?, ?>) config.get("nested")).get(1));
        assertEquals(Double.POSITIVE_INFINITY, ((Map<?, ?>) config.get("nested")).get("infinity"));
        assertTrue(decoded.root().path("config").isObject());
        assertEquals(0, new BigDecimal("9007199254740993.0").compareTo(decoded.root().at("/input_schema/properties/config/minimum").decimalValue()));
        assertFalse(decode("name: no-config").configPresent());
        assertTrue(decode("config: null").root().path("config").isNull());
    }

    @Test void configPreservesExponentsOutsideBigDecimalRange() {
        for (String literal : List.of("1e9999999999", "1e-9999999999")) {
            var decoded = decode("executor: acme.run\nconfig: {value: " + literal + "}");
            assertEquals(((Map<?, ?>) YamlValues.read("value: " + literal)).get("value"),
                    ((Map<?, ?>) decoded.executorConfig()).get("value"));
            var failure = assertThrows(SkillDocumentValues.InvalidDocumentException.class,
                    () -> decode("typesafe: {state: {value: " + literal + "}}"));
            assertEquals("/typesafe/state/value", failure.path());
            assertTrue(failure.getMessage().contains(literal));
        }
    }

    @Test void byteIngressRetainsBomEncodingAndRejectsMalformedUtf8() throws Exception {
        String yaml = "name: café\nconfig: {value: 1.25}\ntypesafe: {state: 0.1234567890123456789}";
        for (String encoding : List.of("UTF-8", "UTF-16", "UTF-32")) {
            byte[] bytes = yaml.getBytes(java.nio.charset.Charset.forName(encoding));
            if (encoding.equals("UTF-32")) {
                byte[] marked = new byte[bytes.length + 4];
                marked[2] = (byte) 0xfe; marked[3] = (byte) 0xff;
                System.arraycopy(bytes, 0, marked, 4, bytes.length); bytes = marked;
            }
            var decoded = SkillDocumentValues.decode(new java.io.ByteArrayInputStream(bytes),
                    "encoded.yaml", LoomspanJacksonCodecs.defaults().skillYaml());
            assertEquals(decode(yaml).root(), decoded.root(), encoding);
            assertEquals(decode(yaml).executorConfig(), decoded.executorConfig(), encoding);
        }
        assertThrows(java.nio.charset.MalformedInputException.class, () -> SkillDocumentValues.decode(
                new java.io.ByteArrayInputStream(new byte[] {(byte) 0xc3, 0x28}), "bad.yaml",
                LoomspanJacksonCodecs.defaults().skillYaml()));
    }

    @Test void retainsDuplicateCycleAliasExpansionAndTimestampRules() {
        assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> decode("a: 1\na: 2"));
        assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> decode("a: &cycle [*cycle]"));
        StringBuilder yaml = new StringBuilder("v0: &v0 [leaf]\n");
        for (int level = 1; level <= 24; level++)
            yaml.append("v").append(level).append(": &v").append(level).append(" [*v").append(level-1).append(", *v").append(level-1).append("]\n");
        var failure = assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> decode(yaml.toString()));
        assertTrue(failure.getMessage().contains("1000000"));
        JsonNode root = decode("date: !!timestamp 2026-11-13\na: &shared [1.234567890123456789]\nb: *shared").root();
        assertEquals("2026-11-13", root.path("date").asText());
        assertEquals(root.path("a"), root.path("b"));
    }
}
