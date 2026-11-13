package ai.loomspan.internal.schema;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.serialization.SkillDocumentValues;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual authored fixtures through production YAML decoding and declaration compilation only. */
class SchemaDeclarationFixturesTest {
    private final StandardSchemaCompiler compiler = new StandardSchemaCompiler();

    private JsonNode document(String directory, String name) throws IOException {
        String resource = "/skills/" + directory + "/" + name + ".yaml";
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return SkillDocumentValues.decode(yaml, resource, LoomspanJacksonCodecs.defaults().skillYaml()).root();
        }
    }

    private CanonicalSchema output(String directory, String name) throws IOException {
        return compiler.compile(document(directory, name).path("output_schema"),
                StandardSchemaCompiler.Role.OUTPUT, StandardSchemaCompiler.Origin.AUTHORED);
    }

    private JsonNode value(String json) {
        return LoomspanJacksonCodecs.defaults().schemaTree().readTree(json);
    }

    @Test void migratedAcceptedDeclarationsUseCanonicalSyntax() throws IOException {
        for (String name : List.of("evidence-contract-hash-public-name-skill", "evidence-contract-skill",
                "evidence-operator-substring-skill", "output-schema-property-evidence-skill",
                "output-schema-property-evidence-types-skill")) {
            assertNotNull(output("valid", name), name);
        }
        var attachment = compiler.compile(document("valid", "attachment-input-skill").path("input_schema"),
                StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.AUTHORED);
        assertEquals("image", attachment.tree().at("/properties/image/x-loomspan-attachment/media_type").textValue());
        assertEquals("string", attachment.providerTree().at("/properties/image/type").textValue());
        assertFalse(attachment.providerTree().at("/properties/image").has("x-loomspan-attachment"));
    }

    @Test void formerlyRestrictedStandardDeclarationsHaveMatchingInstanceVerdicts() throws IOException {
        var required = output("valid", "output-schema-missing-required-property-skill");
        assertTrue(required.validate(value("{\"vendorName\":\"Supplier\"}")).isEmpty());
        assertFalse(required.validate(value("{}")).isEmpty());
        var cased = output("valid", "output-schema-duplicate-properties-skill");
        assertEquals(2, cased.tree().path("properties").size());
        assertTrue(cased.validate(value("{\"vendorName\":\"lower\",\"VendorName\":\"upper\"}")).isEmpty());
        assertFalse(cased.validate(value("{\"VendorName\":1}")).isEmpty());
        var nested = output("valid", "output-schema-nested-array-skill");
        assertTrue(nested.validate(value("{\"matrix\":[[\"one\"],[]]}")).isEmpty());
        assertFalse(nested.validate(value("{\"matrix\":[[1]]}")).isEmpty());
        var scalarEnum = output("valid", "output-schema-object-enum-skill");
        assertFalse(scalarEnum.validate(value("{}")).isEmpty());
        assertFalse(scalarEnum.validate(value("\"invalid\"")).isEmpty());
    }

    @Test void malformedAnnotationsRemainDeclarationFailuresAtTheirActualSchemaLocations() throws IOException {
        Map<String, String> invalid = Map.ofEntries(
                Map.entry("evidence-contract-blank-evidence-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-boolean-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-decimal-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-list-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-null-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-number-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("evidence-contract-object-expression-skill", "/properties/vendorName/x-loomspan-evidence"),
                Map.entry("output-schema-root-evidence-skill", "/x-loomspan-evidence"),
                Map.entry("output-schema-item-evidence-skill", "/properties/results/items/x-loomspan-evidence"),
                Map.entry("output-schema-item-property-evidence-skill", "/properties/results/items/properties/detail/x-loomspan-evidence"),
                Map.entry("output-schema-nested-property-evidence-skill", "/properties/result/properties/detail/x-loomspan-evidence"),
                Map.entry("attachment-output-skill", "/properties/image/x-loomspan-attachment"));
        for (var entry : invalid.entrySet()) {
            var failure = assertThrows(StandardSchemaCompiler.DeclarationException.class,
                    () -> output("invalid", entry.getKey()), entry.getKey());
            assertEquals(entry.getValue(), failure.path(), entry.getKey());
        }
        var document = document("invalid", "attachment-missing-allowed-content-types-skill");
        var failure = assertThrows(StandardSchemaCompiler.DeclarationException.class, () -> compiler.compile(
                document.path("input_schema"), StandardSchemaCompiler.Role.INPUT, StandardSchemaCompiler.Origin.AUTHORED));
        assertEquals("/properties/image/x-loomspan-attachment/allowed_content_types", failure.path());
    }

    @Test void expressionGrammarAndChildResolutionAreNotDeclarationCompilerResponsibilities() throws IOException {
        for (String name : List.of("evidence-contract-ambiguous-case-child-skill",
                "evidence-contract-malformed-expression-skill", "evidence-contract-nondirect-child-skill",
                "evidence-contract-reserved-child-skill", "evidence-contract-trailing-token-skill",
                "evidence-contract-wrong-case-child-skill")) {
            // These remain invalid catalog/runtime cases; the compiler only checks string shape/placement.
            assertNotNull(output("invalid", name), name);
        }
    }
}
