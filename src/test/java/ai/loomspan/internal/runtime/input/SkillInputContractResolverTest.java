package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.skill.YamlSkillDefinition;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual resolver tests; root compilation currently blocked by downstream catalog/output consumers. */
class SkillInputContractResolverTest {
    final SkillInputContractResolver resolver=new SkillInputContractResolver();
    @Test void yamlUsesItsCompiledCanonicalContractWithoutRewriting() {
        var schema=new StandardSchemaCompiler().compile("{\"type\":\"object\",\"properties\":{\"file\":{\"type\":\"string\",\"x-loomspan-attachment\":{\"media_type\":\"file\",\"allowed_content_types\":[\"text/plain\"]}}}}",StandardSchemaCompiler.Role.INPUT,StandardSchemaCompiler.Origin.AUTHORED);
        var definition=mock(YamlSkillDefinition.class);when(definition.hasDeclaredInputSchema()).thenReturn(true);when(definition.inputSchema()).thenReturn(schema);
        var contract=resolver.resolveYamlCapability(definition);
        assertThat(contract.schema()).isSameAs(schema);assertThat(contract.kind()).isEqualTo(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT);
        assertThat(schema.tree().at("/properties/file/x-loomspan-attachment/media_type").asText()).isEqualTo("file");
        assertThat(LoomspanJacksonCodecs.defaults().schemaTree().readTree(resolver.toJsonSchema(contract)).at("/properties/file").has("x-loomspan-attachment")).isFalse();
    }
    @Test void preservesExactStandardTreesAndStripsOnlyProviderAnnotationsAtSchemaNodes() {
        var schema=resolver.fromJsonSchema("""
                {"type":"object","properties":{
                  "value":{"description":" exact "},
                  "x-loomspan-runtime-ref-capable":{"type":"string"},
                  "handle":{"type":["string","null"],"description":"reference","minLength":4,"x-loomspan-runtime-ref-capable":true},
                  "map":{"type":"object","additionalProperties":{}},
                  "items":{"type":"array","minItems":1,"items":{"type":"number","minimum":0.12345678901234567890123456789,"enum":[null,1,true]}}
                },"required":["value"],"additionalProperties":false}
                """);
        var before=schema.tree();var contract=new SkillInputContract(SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,schema);
        var provider=LoomspanJacksonCodecs.defaults().schemaTree().readTree(resolver.toJsonSchema(contract));
        assertThat(schema.tree()).isEqualTo(before);assertThat(provider).isEqualTo(schema.providerTree());
        assertThat(provider.at("/properties/handle").has("x-loomspan-runtime-ref-capable")).isFalse();
        assertThat(provider.at("/properties/x-loomspan-runtime-ref-capable/type").asText()).isEqualTo("string");
        assertThat(provider.at("/properties/handle/description").asText()).isEqualTo("reference");
        assertThat(provider.at("/properties/value").has("type")).isFalse();
    }
    @Test void distinguishesAbsentGenericShapeAndExplicitContracts() {
        for(String schema:java.util.Arrays.asList(null,"","{\"type\":\"object\"}","{\"type\":\"object\",\"additionalProperties\":true}")) assertThat(resolver.resolveFromToolSchema(schema).isGeneric()).isTrue();
        for(String schema:java.util.List.of("{\"type\":\"object\",\"additionalProperties\":false}","{\"type\":\"object\",\"additionalProperties\":{}}", "{\"type\":\"object\",\"properties\":{\"value\":{}}}")) assertThat(resolver.resolveFromToolSchema(schema).isGeneric()).isFalse();
        assertThatThrownBy(()->resolver.resolveFromToolSchema("{}")).isInstanceOf(StandardSchemaCompiler.DeclarationException.class);
        assertThat(resolver.resolveJavaCapability("{\"type\":\"object\"}").isGeneric()).isFalse();
        var absent=mock(YamlSkillDefinition.class);assertThat(resolver.resolveYamlCapability(absent).isGeneric()).isTrue();
    }
}
