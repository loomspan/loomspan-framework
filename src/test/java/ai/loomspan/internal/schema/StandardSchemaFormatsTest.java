package ai.loomspan.internal.schema;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ai.loomspan.internal.schema.StandardSchemaCompiler.*;
import static ai.loomspan.internal.schema.StandardSchemaCompilerTest.*;

class StandardSchemaFormatsTest {
    @Test void pinnedStandardFormatAssertionsRunForAllRoles() {
        String[][] cases={
            {"date","2026-11-13","11/13/2026"},{"date-time","2026-11-13T12:00:00Z","2026-11-13"},
            {"duration","P1D","tomorrow"},{"email","a@example.com","not-email"},{"hostname","example.com","bad host"},
            {"idn-email","a@example.com","not-email"},{"idn-hostname","example.com","bad host"},{"ipv4","192.0.2.1","999.1.1.1"},
            {"ipv6","2001:db8::1","gggg::1"},{"iri","https://example.com/é","bad iri"},{"iri-reference","/é","bad iri"},
            {"json-pointer","/a~1b","/~2"},{"regex","[a-z]+","["},{"relative-json-pointer","0#","00#"},
            {"time","12:30:00Z","25:00:00Z"},{"uri","https://example.com/a","bad uri"},{"uri-reference","/a","bad uri"},
            {"uri-template","https://example.com/{id}","https://example.com/{id"},{"uuid","123e4567-e89b-12d3-a456-426614174000","not-a-uuid"}};
        assertEquals(FORMATS.size(),cases.length);
        for(Role role:Role.values()) for(var item:cases){var schema=compile(root("{\"type\":\"string\",\"format\":\""+item[0]+"\"}"),role);for(int n=1;n<=2;n++){var node=json("{}");((tools.jackson.databind.node.ObjectNode)node).put("x",item[n]);assertEquals(n==1,schema.validate(node).isEmpty(),role+" "+Arrays.toString(item)+" "+schema.validate(node));}}
        check(compile(root("{\"type\":\"array\",\"items\":{\"type\":\"string\",\"format\":\"date\"}}"),Role.INPUT),"{\"x\":[\"bad\"]}",false);
        check(compile(root("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\",\"format\":\"date\"}}"),Role.INPUT),"{\"x\":{\"k\":\"bad\"}}",false);
        for(String bad:List.of("DATE","unknown","alpha","alphanumeric","color","ip-address","phone","style","utc-millisec")) assertThrows(DeclarationException.class,()->compile(root("{\"type\":\"string\",\"format\":\""+bad+"\"}"),Role.INPUT));
        check(compile(root("{\"type\":\"string\"}"),Role.INPUT),"{\"x\":\"invalid date text\"}",true);
    }
}
