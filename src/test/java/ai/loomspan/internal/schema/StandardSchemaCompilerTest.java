package ai.loomspan.internal.schema;

import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ai.loomspan.internal.schema.StandardSchemaCompiler.*;

class StandardSchemaCompilerTest {
    static final StandardSchemaCompiler COMPILER = new StandardSchemaCompiler();
    static JsonNode json(String value) { return LoomspanJacksonCodecs.defaults().schemaTree().readTree(value); }
    static CanonicalSchema compile(String schema, Role role) { return COMPILER.compile(schema, role, Origin.AUTHORED); }
    static String root(String leaf) { return "{\"type\":\"object\",\"properties\":{\"x\":" + leaf + "}}"; }
    static void check(CanonicalSchema schema, String instance, boolean valid) {
        var node = json(instance); var before = node.deepCopy();
        assertEquals(valid, schema.validate(node).isEmpty(), instance + " => " + schema.validate(node));
        assertEquals(before, node);
    }
    @Test void equivalentRolesUseExactPresenceNamesNullAndOpenness() {
        for (Role role : Role.values()) {
            var schema = compile("{\"type\":\"object\",\"required\":[\"Name\"],\"properties\":{\"Name\":{\"type\":[\"string\",\"null\"]},\"name\":{\"type\":\"integer\"},\" name \":{\"type\":\"boolean\"},\"\":{\"type\":\"string\"}}}", role);
            check(schema, "{\"Name\":null,\"name\":1.0,\" name \":true,\"\":\"ok\",\"extra\":4}", true);
            check(schema, "{\"name\":1}", false); check(schema,"{\"Name\":false}",false);
            assertFalse(schema.tree().has("additionalProperties"));
            check(compile("{\"type\":\"object\",\"required\":[\"undeclared\"]}",role), "{\"undeclared\":1}",true);
            check(compile("{\"type\":\"object\",\"additionalProperties\":false}",role), "{\"extra\":1}",false);
            check(compile(root("{\"type\":[\"null\",\"string\"]}"),role), "{\"x\":null}",true);
            check(compile(root("{\"type\":\"string\"}"),role), "{}",true);
            check(compile(root("{\"type\":\"string\"}"),role), "{\"x\":null}",false);
        }
    }
    @Test void typesEnumsNestedArraysAndBoundsAreStandardAndExact() {
        for (Role role : Role.values()) {
            for (String type : List.of("number","integer","boolean")) {
                var schema=compile(root("{\"type\":\""+type+"\"}"),role);
                check(schema,"{\"x\":\"1\"}",false); check(schema,"{\"x\":\"true\"}",false);
            }
            var integer=compile(root("{\"type\":\"integer\"}"),role);
            check(integer,"{\"x\":1.0}",true); check(integer,"{\"x\":1.1}",false);
            for (String value : List.of("1","1.0","true","null","\"Text\"")) check(compile(root("{\"type\":[\"number\",\"null\"],\"enum\":[1,null]}"),role),"{\"x\":"+value+"}", value.equals("1")||value.equals("1.0")||value.equals("null"));
            check(compile(root("{\"type\":\"string\",\"enum\":[\"Text\"]}"),role),"{\"x\":\"text\"}",false);
            check(compile(root("{\"type\":\"boolean\",\"enum\":[true]}"),role),"{\"x\":true}",true);
            check(compile(root("{\"type\":\"boolean\",\"enum\":[true]}"),role),"{\"x\":false}",false);
            check(compile(root("{\"type\":\"number\",\"enum\":[0.12345678901234567890123456789]}"),role),"{\"x\":0.12345678901234567890123456789}",true);
            check(compile(root("{\"type\":\"number\",\"enum\":[0.12345678901234567890123456789]}"),role),"{\"x\":0.12345678901234567890123456788}",false);
            var arrays=compile(root("{\"type\":\"array\",\"minItems\":1,\"maxItems\":2,\"items\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}"),role);
            check(arrays,"{\"x\":[[1],[2,3]]}",true); check(arrays,"{\"x\":[[\"2\"]]}",false);check(arrays,"{\"x\":[]}",false);check(arrays,"{\"x\":[[],[],[]]}",false);
            var exact=compile(root("{\"type\":\"number\",\"minimum\":9007199254740993.0,\"maximum\":9007199254740993.000000000000000001}"),role);
            check(exact,"{\"x\":9007199254740992.999999999999999999}",false);check(exact,"{\"x\":9007199254740993.0}",true);check(exact,"{\"x\":9007199254740993.000000000000000002}",false);
            var exponent=compile(root("{\"type\":\"number\",\"minimum\":1.234567890123456789e-30,\"maximum\":1.234567890123456789e-30}"),role);
            check(exponent,"{\"x\":1.234567890123456789e-30}",true);check(exponent,"{\"x\":1.234567890123456788e-30}",false);
            var text=compile(root("{\"type\":\"string\",\"minLength\":1,\"maxLength\":1}"),role);
            check(text,"{\"x\":\"😀\"}",true);check(text,"{\"x\":\"\"}",false);check(text,"{\"x\":\"aa\"}",false);
        }
    }
    @Test void unsupportedDeclarationsFailAtSchemaLocations() {
        for(Role role:Role.values()) {
            for(String bad:List.of("{}","true","{\"type\":\"array\"}","{\"type\":[\"object\",\"null\"]}","{\"type\":\"OBJECT\"}")) assertThrows(DeclarationException.class,()->compile(bad,role),bad);
            for(String keyword:List.of("nullable","evidence","default","pattern","$ref","$defs","$anchor","allOf","anyOf","oneOf","if","then","prefixItems","multipleOf","x-arbitrary","input_attachments","media_type")) {
                var error=assertThrows(DeclarationException.class,()->compile(root("{\"type\":\"string\",\""+keyword+"\":true}"),role));
                assertEquals("/properties/x/"+keyword,error.path());
            }
            for(String bad:List.of("{\"type\":[\"string\",\"integer\"]}","{\"type\":\"null\"}","{\"type\":\"attachment\"}","{\"type\":\"array\",\"items\":[{}]}","{\"type\":\"string\",\"enum\":[{}]}")) assertThrows(DeclarationException.class,()->compile(root(bad),role));
            for(String keyword:List.of("default","$ref","nullable","evidence","x-loomspan-evidence","x-loomspan-attachment","x-loomspan-runtime-ref-capable")) check(compile("{\"type\":\"object\",\"properties\":{\""+keyword+"\":{\"type\":\"string\"}}}",role),"{\""+keyword+"\":\"data\"}",true);
        }
        assertEquals("/properties/a~1b~0c/default",assertThrows(DeclarationException.class,()->compile("{\"type\":\"object\",\"properties\":{\"a/b~c\":{\"type\":\"string\",\"default\":1}}}",Role.INPUT)).path());
    }
    @Test void bundledMetaSchemaChecksKeywordShapesWithoutFetchingReferences() {
        assertNotNull(compile("{\"$schema\":\""+DIALECT+"\",\"type\":\"object\"}",Role.INPUT));
        for(String bad:List.of("\"properties\":[]","\"required\":[1]","\"required\":[\"x\",\"x\"]","\"additionalProperties\":1","\"description\":1","\"minimum\":\"1\"","\"maximum\":false","\"minLength\":-1","\"maxLength\":0.5","\"minItems\":-1","\"maxItems\":\"2\"","\"enum\":true")) {
            var error=assertThrows(DeclarationException.class,()->compile("{\"type\":\"object\","+bad+"}",Role.INPUT),bad);
            assertFalse(error.getMessage().isBlank()); assertTrue(error.path().startsWith("/"));
        }
        check(compile(root("{\"type\":\"string\",\"enum\":[]}"),Role.INPUT),"{\"x\":\"anything\"}",false);
        assertThrows(DeclarationException.class,()->compile("{\"$schema\":\"https://example.test/dialect\",\"type\":\"object\"}",Role.INPUT));
        for(String ref:List.of("#/$defs/x","https://127.0.0.1:1/no","file:///no")) assertThrows(DeclarationException.class,()->compile("{\"type\":\"object\",\"$ref\":\""+ref+"\"}",Role.INPUT));
    }
    @Test void roleSpecificShapeFreeMapsAndGeneratedOwnership() {
        var input=compile(root("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"integer\"}}"),Role.INPUT);
        check(input,"{\"x\":{\"a\":1}}",true);check(input,"{\"x\":{\"a\":\"1\"}}",false);
        check(compile(root("{}"),Role.INPUT),"{\"x\":[null,{},true]}",true);
        for(Role role:List.of(Role.OUTPUT,Role.PLANNING)) {assertThrows(DeclarationException.class,()->compile(root("{}"),role));assertThrows(DeclarationException.class,()->compile(root("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"),role));}
        for(Role role:Role.values()) {
            var generated=COMPILER.compile(root("false"),role,Origin.GENERATED);check(generated,"{}",true);check(generated,"{\"x\":null}",false);
            assertThrows(DeclarationException.class,()->compile(root("false"),role));assertThrows(DeclarationException.class,()->COMPILER.compile(root("true"),role,Origin.GENERATED));
            assertThrows(DeclarationException.class,()->COMPILER.compile(root("{\"type\":\"array\",\"items\":false}"),role,Origin.GENERATED));
        }
    }
    @Test void describedUnconstrainedInputLeavesKeepPresenceAndParentConstraints() {
        for(Origin origin:Origin.values()) {
            var schema=COMPILER.compile("{\"type\":\"object\",\"properties\":{\"x\":{\"description\":\" exact \"}},\"required\":[\"x\"],\"additionalProperties\":false}",Role.INPUT,origin);
            for(String value:List.of("null","true","4","\"ref://literal\"","{}","[null,1]")) check(schema,"{\"x\":"+value+"}",true);
            check(schema,"{}",false);check(schema,"{\"x\":null,\"extra\":1}",false);
            assertEquals(" exact ",schema.tree().at("/properties/x/description").asText());
            for(Role role:List.of(Role.OUTPUT,Role.PLANNING)) assertThrows(DeclarationException.class,()->COMPILER.compile(root("{\"description\":\"text\"}"),role,origin));
            assertThrows(DeclarationException.class,()->COMPILER.compile("{\"description\":\"text\"}",Role.INPUT,origin));
            assertThrows(DeclarationException.class,()->COMPILER.compile(root("{\"description\":4}"),Role.INPUT,origin));
            assertThrows(DeclarationException.class,()->COMPILER.compile(root("{\"description\":\"text\",\"default\":null}"),Role.INPUT,origin));
        }
        var bounded=compile(root("{\"type\":\"array\",\"minItems\":1,\"maxItems\":2,\"items\":{\"description\":\"anything\"}}"),Role.INPUT);
        check(bounded,"{\"x\":[]}",false);check(bounded,"{\"x\":[null,true,{}]}",false);check(bounded,"{\"x\":[null]}",true);
    }

}
