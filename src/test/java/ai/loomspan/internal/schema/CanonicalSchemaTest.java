package ai.loomspan.internal.schema;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ai.loomspan.internal.schema.StandardSchemaCompiler.*;
import static ai.loomspan.internal.schema.StandardSchemaCompilerTest.*;

class CanonicalSchemaTest {
    static final String ATTACHMENT="{\"media_type\":\"image\",\"allowed_content_types\":[\"image/jpeg\"]}";
    static String attached(String type) { return "{\"type\":"+type+",\"description\":\"keep\",\"minLength\":1,\"x-loomspan-attachment\":"+ATTACHMENT+"}"; }
    @Test void attachmentShapesAndProviderCopiesVisitOnlySchemaNodes() {
        for (String media:List.of("image","pdf","audio","video","file")) assertNotNull(compile(root(attached("\"string\"").replace("\"image\"","\""+media+"\"")),Role.INPUT));
        var tree=json("{\"type\":\"object\",\"required\":[\"single\"],\"properties\":{\"single\":"+attached("[\"null\",\"string\"]")+",\"nested\":{\"type\":\"object\",\"properties\":{\"x-loomspan-attachment\":"+attached("\"string\"")+"}},\"strings\":{\"type\":\"array\",\"items\":"+attached("\"string\"")+"},\"objects\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"x\":"+attached("\"string\"")+"}}},\"map\":{\"type\":\"object\",\"additionalProperties\":"+attached("\"string\"")+"}}}");
        var schema=COMPILER.compile(tree,Role.INPUT,Origin.AUTHORED);var before=schema.tree();var provider=schema.providerTree();
        for(String location:List.of("/properties/single","/properties/nested/properties/x-loomspan-attachment","/properties/strings/items","/properties/objects/items/properties/x","/properties/map/additionalProperties")) {
            assertTrue(before.at(location).has("x-loomspan-attachment"));assertFalse(provider.at(location).has("x-loomspan-attachment"));assertEquals("keep",provider.at(location).path("description").asText());assertEquals(1,provider.at(location).path("minLength").asInt());
        }
        assertTrue(provider.path("properties").path("nested").path("properties").has("x-loomspan-attachment"));
        assertEquals(before,schema.tree());check(schema,"{\"single\":null}",true);check(schema,"{}",false);
    }
    @Test void evidenceExactNamesAndAnnotationNamedDataPropertiesSurviveProviderExtraction() {
        var tree = (ObjectNode) json("{\"type\":\"object\",\"properties\":{}}");
        var properties = (ObjectNode) tree.get("properties");
        for (String name : List.of("Name", "name", " name ", "x-loomspan-evidence", "x-loomspan-attachment", "x-loomspan-runtime-ref-capable")) {
            properties.set(name, json("{\"type\":\"string\",\"description\":\"data property\",\"x-loomspan-evidence\":\"  child  \"}"));
        }
        var schema = COMPILER.compile(tree, Role.OUTPUT, Origin.AUTHORED);
        var provider = schema.providerTree();
        for (String name : properties.propertyNames()) {
            assertTrue(provider.path("properties").has(name));
            assertEquals("data property", provider.path("properties").path(name).path("description").asText());
            assertFalse(provider.path("properties").path(name).has("x-loomspan-evidence"));
            assertEquals("  child  ", schema.tree().path("properties").path(name).path("x-loomspan-evidence").asText());
        }
    }
    @Test void malformedAttachmentMetadataAndRolePlacementsFailLocally() {
        for(String metadata:List.of("null","false","[]","{}","{\"media_type\":\"IMAGE\",\"allowed_content_types\":[\"image/jpeg\"]}","{\"media_type\":\"image\",\"allowed_content_types\":[]}","{\"media_type\":\"image\",\"allowed_content_types\":[\" \"]}","{\"media_type\":\"image\",\"allowed_content_types\":[1]}","{\"media_type\":\"image\",\"allowed_content_types\":null}","{\"media_type\":\"image\",\"allowed_content_types\":[\"x\"],\"extra\":true}")) assertThrows(DeclarationException.class,()->compile(root("{\"type\":\"string\",\"x-loomspan-attachment\":"+metadata+"}"),Role.INPUT),metadata);
        for(Role role:List.of(Role.OUTPUT,Role.PLANNING)) assertThrows(DeclarationException.class,()->compile(root(attached("\"string\"")),role));
        for(String type:List.of("object","array","integer","boolean")) assertThrows(DeclarationException.class,()->compile(root(attached("\""+type+"\"")),Role.INPUT));
    }
    @Test void generatedRuntimeMarkerAndEvidencePlacementPreserveOriginalText() {
        String marker=root("{\"type\":[\"string\",\"null\"],\"x-loomspan-runtime-ref-capable\":true}");
        var generated=COMPILER.compile(marker,Role.INPUT,Origin.GENERATED);assertFalse(generated.providerTree().at("/properties/x").has("x-loomspan-runtime-ref-capable"));
        assertThrows(DeclarationException.class,()->compile(marker,Role.INPUT));
        for(Role role:List.of(Role.OUTPUT,Role.PLANNING)) assertThrows(DeclarationException.class,()->COMPILER.compile(marker,role,Origin.GENERATED));
        assertThrows(DeclarationException.class,()->COMPILER.compile(marker.replace(":true",":1"),Role.INPUT,Origin.GENERATED));
        String expression="  child and (other)\n ";
        var node=json(root("{\"type\":\"string\"}"));((ObjectNode)node.at("/properties/x")).put("x-loomspan-evidence",expression);
        var evidence=COMPILER.compile(node,Role.OUTPUT,Origin.AUTHORED);assertEquals(expression,evidence.tree().at("/properties/x/x-loomspan-evidence").asText());assertFalse(evidence.providerTree().at("/properties/x").has("x-loomspan-evidence"));
        for(Role role:List.of(Role.INPUT,Role.PLANNING)) assertThrows(DeclarationException.class,()->COMPILER.compile(node,role,Origin.AUTHORED));
        for(String value:List.of("null","true","1","[]","{}","\"\"","\" \"")) assertThrows(DeclarationException.class,()->compile(root("{\"type\":\"string\",\"x-loomspan-evidence\":"+value+"}"),Role.OUTPUT));
        String leaf="{\"type\":\"string\",\"x-loomspan-evidence\":\"child\"}";
        for(String declaration:List.of("{\"type\":\"object\",\"x-loomspan-evidence\":\"child\"}",root("{\"type\":\"array\",\"items\":"+leaf+"}"),root("{\"type\":\"object\",\"properties\":{\"p\":"+leaf+"}}"),root("{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"p\":"+leaf+"}}}"))) assertThrows(DeclarationException.class,()->compile(declaration,Role.OUTPUT));
    }
    @Test void snapshotsAndIndependentConcurrentContextsAreImmutable() throws Exception {
        var original=(ObjectNode)json(root("{\"type\":\"integer\"}"));var schema=COMPILER.compile(original,Role.INPUT,Origin.AUTHORED);var expected=schema.tree();
        original.removeAll();((ObjectNode)schema.tree()).removeAll();((ObjectNode)schema.providerTree()).removeAll();assertEquals(expected,schema.tree());
        try(var pool=Executors.newFixedThreadPool(4)) {
            var tasks=new ArrayList<Callable<Boolean>>();for(int n=0;n<64;n++){int value=n;tasks.add(()->schema.validate(json(value%2==0?"{\"x\":1}":"{\"x\":\"1\"}")).isEmpty()==(value%2==0));}
            for(var result:pool.invokeAll(tasks)) assertTrue(result.get());
        }
        assertThrows(NullPointerException.class,()->schema.validate(null));check(schema,"{\"x\":1}",true);
    }
    @Test void operationalValidationFailurePropagatesRatherThanReturningPartialSuccess() {
        var schema = compile(root("{\"type\":\"integer\"}"), Role.INPUT);
        var failedInstance = org.mockito.Mockito.mock(tools.jackson.databind.JsonNode.class, invocation -> {
            throw new IllegalStateException("operational node failure");
        });
        var failure = assertThrows(IllegalStateException.class, () -> schema.validate(failedInstance));
        assertEquals("operational node failure", failure.getMessage());
        check(schema, "{\"x\":1}", true);
    }
    @Test void originalDiagnosticsAndUnlimitedModerateWorkloadsAreRetained() {
        var schema=compile("{\"type\":\"object\",\"properties\":{\"a/b~c\":{\"type\":\"integer\"}}}",Role.INPUT);
        var errors=schema.validate(json("{\"a/b~c\":\" token=key \"}"));assertEquals(1,errors.size());var error=errors.getFirst();
        assertEquals("/a~1b~0c",error.getInstanceLocation().toString());assertTrue(error.getSchemaLocation().toString().contains("/properties/a~1b~0c/type"));assertTrue(error.toString().contains("integer"));
        String leaf="{\"type\":\"integer\"}", value="1";
        for(int n=0;n<64;n++){leaf="{\"type\":\"array\",\"items\":"+leaf+"}";value="["+value+"]";}check(compile(root(leaf),Role.INPUT),"{\"x\":"+value+"}",true);
        var wide=(ObjectNode)json("{\"type\":\"object\",\"properties\":{}}");var instance=(ObjectNode)json("{}");
        for(int n=0;n<300;n++){((ObjectNode)wide.get("properties")).set("p"+n,json("{\"type\":\"integer\"}"));instance.put("p"+n,n);}assertTrue(COMPILER.compile(wide,Role.INPUT,Origin.AUTHORED).validate(instance).isEmpty());
    }
}
