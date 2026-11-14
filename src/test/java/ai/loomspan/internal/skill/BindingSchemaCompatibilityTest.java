package ai.loomspan.internal.skill;

import ai.loomspan.internal.runtime.input.ObjectFieldPath;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;

class BindingSchemaCompatibilityTest {
    private static JsonNode schema(String json) { return LoomspanJacksonCodecs.defaults().schemaTree().readTree(json); }
    private static JsonNode select(String json, String path, boolean destination) {
        return BindingSchemaCompatibility.select(schema(json), ObjectFieldPath.parse(path, !destination), destination);
    }
    private static void compatible(String from, String to) {
        BindingSchemaCompatibility.check(schema(from), schema(to), "value");
    }

    @Test void omittedAndTrueAreOpenForSourcesAndDestinationsAtEveryDepth() {
        for (boolean destination : new boolean[]{true, false}) {
            for (String openness : new String[]{"", ",\"additionalProperties\":true"}) {
                assertThat(select("{\"type\":\"object\"" + openness + "}", "/unknown/child", destination)).isNull();
                assertThat(select("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\"" + openness + "}}}",
                        "/nested/unknown", destination)).isNull();
            }
            assertThatThrownBy(() -> select("{\"type\":\"object\",\"additionalProperties\":false}", "/unknown", destination))
                    .hasMessageContaining("closed object");
            assertThatThrownBy(() -> select("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":\"object\",\"additionalProperties\":false}}}",
                    "/nested/unknown", destination)).hasMessageContaining("closed object");
        }
    }

    @Test void selectionUsesExactNamesAndObjectTokensOnly() {
        String object = "{\"type\":\"object\",\"properties\":{\"Value\":{\"type\":\"string\"},\"0\":{\"type\":\"integer\"},\"a/b~c\":{\"type\":\"boolean\"}},\"additionalProperties\":false}";
        assertThat(select(object, "/Value", true).path("type").asText()).isEqualTo("string");
        assertThat(select(object, "/0", false).path("type").asText()).isEqualTo("integer");
        assertThat(select(object, "/a~1b~0c", true).path("type").asText()).isEqualTo("boolean");
        assertThatThrownBy(() -> select(object, "/value", true)).hasMessageContaining("closed object");
        assertThatThrownBy(() -> select(object, "/Value/child", false)).hasMessageContaining("non-object");
        assertThatThrownBy(() -> select("{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}", "/a/0", false))
                .hasMessageContaining("non-object");
        assertThatThrownBy(() -> select("{\"type\":\"object\",\"properties\":{\"reserved\":false}}", "/reserved", true))
                .hasMessageContaining("prohibited");
    }

    @Test void nullableObjectsAndTypedInputExtensionsRemainTraversable() {
        assertThat(select("{\"type\":\"object\",\"properties\":{\"nested\":{\"type\":[\"null\",\"object\"],\"properties\":{\"v\":{\"type\":\"number\"}}}}}", "/nested/v", true)
                .path("type").asText()).isEqualTo("number");
        assertThat(select("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}", "/dynamic", false)
                .path("type").asText()).isEqualTo("string");
        assertThatThrownBy(() -> select("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}", "/dynamic/child", false))
                .hasMessageContaining("non-object");
        assertThat(select("{\"type\":\"object\",\"properties\":{\"unknown\":{}}}", "/unknown/anything", false)).isNull();
    }

    @Test void equivalentNumericEnumSpellingsOverlapWithoutRounding() {
        for (String from : new String[]{"1", "1.0", "1e0", "1.00"})
            for (String to : new String[]{"1", "1.0", "1e0", "1.00"})
                compatible("{\"type\":\"number\",\"enum\":[" + from + "]}", "{\"type\":\"number\",\"enum\":[" + to + "]}");
        compatible("{\"type\":\"integer\",\"enum\":[900719925474099300000000000000000001]}",
                "{\"type\":\"number\",\"enum\":[900719925474099300000000000000000001.0]}");
        assertThatThrownBy(() -> compatible("{\"type\":\"number\",\"enum\":[1.0000000000000000000000000001]}",
                "{\"type\":\"number\",\"enum\":[1.0000000000000000000000000002]}"))
                .hasMessageContaining("incompatible enums");
        assertThatThrownBy(() -> compatible("{\"type\":\"integer\",\"enum\":[1]}", "{\"type\":\"number\",\"enum\":[2]}"))
                .hasMessageContaining("incompatible enums");
    }

    @Test void nonNumericEnumsRemainTypeSensitiveAndUseIntersection() {
        compatible("{\"type\":\"string\",\"enum\":[\"A\",\"B\"]}", "{\"type\":\"string\",\"enum\":[\"B\",\"Z\"]}");
        assertThatThrownBy(() -> compatible("{\"type\":\"string\",\"enum\":[\"A\"]}", "{\"type\":\"string\",\"enum\":[\"B\"]}"))
                .hasMessageContaining("incompatible enums");
        // Equal broad type deliberately isolates enum equality from type compatibility.
        assertThatThrownBy(() -> compatible("{\"type\":\"number\",\"enum\":[1]}", "{\"type\":\"number\",\"enum\":[\"1\"]}"))
                .hasMessageContaining("incompatible enums");
        assertThatThrownBy(() -> compatible("{\"type\":\"boolean\",\"enum\":[true]}", "{\"type\":\"boolean\",\"enum\":[\"true\"]}"))
                .hasMessageContaining("incompatible enums");
        compatible("{\"type\":[\"string\",\"null\"],\"enum\":[null]}", "{\"type\":[\"string\",\"null\"],\"enum\":[null]}");
    }

    @Test void typesNullabilityAndWholeArraysPreserveBoundedChecks() {
        compatible("{\"type\":\"integer\"}", "{\"type\":\"number\"}");
        compatible("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}", "{\"type\":\"array\",\"items\":{\"type\":\"number\"}}");
        compatible("{\"type\":\"string\"}", "{\"type\":[\"null\",\"string\"]}");
        assertThatThrownBy(() -> compatible("{\"type\":[\"string\",\"null\"]}", "{\"type\":\"string\"}"))
                .hasMessageContaining("nullable");
        assertThatThrownBy(() -> compatible("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}", "{\"type\":\"array\",\"items\":{\"type\":\"number\"}}"))
                .hasMessageContaining("incompatible types");
    }

    @Test void wholeObjectOpennessRetainsOnlyStaticallyProvableFailures() {
        String guaranteedExtra = "{\"type\":\"object\",\"properties\":{\"extra\":{\"type\":\"string\"}},\"required\":[\"extra\"]}";
        String requiresMissing = "{\"type\":\"object\",\"properties\":{\"needed\":{\"type\":\"string\"}},\"required\":[\"needed\"]}";
        for (String openness : new String[]{"", ",\"additionalProperties\":true"}) {
            compatible(guaranteedExtra, "{\"type\":\"object\"" + openness + "}");
            compatible("{\"type\":\"object\"" + openness + "}", requiresMissing);
        }
        assertThatThrownBy(() -> compatible(guaranteedExtra, "{\"type\":\"object\",\"additionalProperties\":false}"))
                .hasMessageContaining("required source field");
        assertThatThrownBy(() -> compatible("{\"type\":\"object\",\"additionalProperties\":false}", requiresMissing))
                .hasMessageContaining("closed source");
        compatible(guaranteedExtra.replace(",\"required\":[\"extra\"]", ""), "{\"type\":\"object\",\"additionalProperties\":false}");
        assertThatThrownBy(() -> compatible("{\"type\":\"object\",\"properties\":{\"VALUE\":{\"type\":\"string\"}},\"required\":[\"VALUE\"]}",
                "{\"type\":\"object\",\"properties\":{\"value\":{\"type\":\"string\"}},\"additionalProperties\":false}"))
                .hasMessageContaining("required source field");
    }
}
