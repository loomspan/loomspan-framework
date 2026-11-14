package ai.loomspan.internal.vfs;

import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real selection traversal; DefaultRefResolver's session/VFS integration is separately retained. */
class InputReferenceTraversalTest {
    static final String MARK="{\"type\":\"string\",\"x-loomspan-runtime-ref-capable\":true}";
    static Object resolve(Object value,String schema,boolean generic,java.util.function.UnaryOperator<Object> resolver) {
        return InputReferenceTraversal.resolve(value,new StandardSchemaCompiler().compile(schema,StandardSchemaCompiler.Role.INPUT,StandardSchemaCompiler.Origin.GENERATED).tree(),generic,resolver);
    }
    @Test void explicitUnconstrainedAndOpenRefsStayLiteralWhileEveryMarkedLocationResolves() {
        String schema="{\"type\":\"object\",\"properties\":{\"plain\":{\"type\":\"string\"},\"any\":{},\"described\":{\"description\":\"literal\"},\"marked\":"+MARK+",\"array\":{\"type\":\"array\",\"items\":"+MARK+"},\"map\":{\"type\":\"object\",\"additionalProperties\":"+MARK+"},\"nested\":{\"type\":\"object\",\"properties\":{\"file\":"+MARK+"}}}}";
        var input=new LinkedHashMap<String,Object>();input.put("plain","ref://plain");input.put("any",Map.of("nested",List.of("ref://any")));input.put("described","ref://described");input.put("marked","ref://marked");input.put("array",List.of("ref://item"));input.put("map",Map.of("name","ref://map"));input.put("nested",Map.of("file","ref://nested"));input.put("open",Map.of("deep","ref://open"));
        var count=new AtomicInteger();
        var result=(Map<?,?>)resolve(input,schema,false,value->{count.incrementAndGet();return "resolved:"+value;});
        assertThat(result.get("plain")).isEqualTo("ref://plain");assertThat(result.get("any")).isEqualTo(input.get("any"));assertThat(result.get("described")).isEqualTo("ref://described");assertThat(result.get("open")).isEqualTo(input.get("open"));
        assertThat(result.get("marked")).isEqualTo("resolved:ref://marked");assertThat(result.get("array")).isEqualTo(List.of("resolved:ref://item"));assertThat(result.get("map")).isEqualTo(Map.of("name","resolved:ref://map"));assertThat(result.get("nested")).isEqualTo(Map.of("file","resolved:ref://nested"));assertThat(count).hasValue(4);
        assertThat(input.get("marked")).isEqualTo("ref://marked");
    }
    @Test void genericResolutionRemainsRecursiveThroughMapsListsAndJavaArrays() {
        var values=Map.of("items",List.of("ref://one",Map.of("nested","ref://two")),"array",new Object[]{"ref://three",true});
        var result=(Map<?,?>)resolve(values,"{\"type\":\"object\"}",true,value->value instanceof String text && text.startsWith("ref://") ? "resolved:"+text : value);
        assertThat(result.get("items")).isEqualTo(List.of("resolved:ref://one",Map.of("nested","resolved:ref://two")));assertThat(result.get("array")).isEqualTo(List.of("resolved:ref://three",true));
    }
    @Test void directHandlesStayIdenticalAndResolutionFailuresPropagate() {
        var resource=new AbstractResource(){public String getDescription(){return "unread";}public InputStream getInputStream(){throw new AssertionError("do not open");}};
        var bytes=new byte[]{1};
        var values=Map.of("resource",resource,"bytes",bytes);
        var result=(Map<?,?>)resolve(values,"{\"type\":\"object\",\"additionalProperties\":"+MARK+"}",false,value->value);
        assertThat(result.get("resource")).isSameAs(resource);assertThat(result.get("bytes")).isSameAs(bytes);
        assertThatThrownBy(()->resolve(Map.of("file","ref://denied"),"{\"type\":\"object\",\"properties\":{\"file\":"+MARK+"}}",false,value->{throw new SecurityException("not authorized");})).isInstanceOf(SecurityException.class).hasMessage("not authorized");
    }
    @Test void attachmentMetadataRemainsASeparateReferenceSelection() {
        String schema="{\"type\":\"object\",\"properties\":{\"file\":{\"type\":\"string\",\"x-loomspan-attachment\":{\"media_type\":\"file\",\"allowed_content_types\":[\"text/plain\"]}},\"falseMarker\":{\"type\":\"string\",\"x-loomspan-runtime-ref-capable\":false}}}";
        var result=(Map<?,?>)resolve(Map.of("file","ref://file","falseMarker","ref://literal"),schema,false,value->"resolved:"+value);
        assertThat(result.get("file")).isEqualTo("resolved:ref://file");assertThat(result.get("falseMarker")).isEqualTo("ref://literal");
    }
}
