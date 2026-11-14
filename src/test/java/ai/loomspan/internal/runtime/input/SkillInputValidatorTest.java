package ai.loomspan.internal.runtime.input;

import ai.loomspan.internal.schema.StandardSchemaCompiler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import java.io.*;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SkillInputValidatorTest
{
    final SkillInputValidator validator = new SkillInputValidator();
    static final StandardSchemaCompiler compiler = new StandardSchemaCompiler();
    static SkillInputContract contract(String schema) { return contract(schema, StandardSchemaCompiler.Origin.AUTHORED); }
    static SkillInputContract contract(String schema, StandardSchemaCompiler.Origin origin) {
        return new SkillInputContract(origin == StandardSchemaCompiler.Origin.AUTHORED
                ? SkillInputContract.SkillInputContractKind.YAML_EXPLICIT : SkillInputContract.SkillInputContractKind.JAVA_REFLECTED,
                compiler.compile(schema, StandardSchemaCompiler.Role.INPUT, origin));
    }
    static String root(String leaf) { return "{\"type\":\"object\",\"properties\":{\"value\":"+leaf+"}}"; }
    static Map<String,Object> input(Object value) { var map=new LinkedHashMap<String,Object>();map.put("value",value);return map; }
    static final String MARK = "\"x-loomspan-runtime-ref-capable\":true";
    static String marked(String assertions) { return "{\"type\":\"string\","+MARK+assertions+"}"; }
    static class NoReadResource extends AbstractResource {
        int opens;
        public String getDescription(){return "untouched";}
        public InputStream getInputStream(){opens++;throw new AssertionError("must not open");}
    }
    static class NoReadStream extends InputStream {
        int reads;
        public int read(){reads++;throw new AssertionError("must not read");}
    }
    static class NoSerialization { public String getValue(){throw new AssertionError("must not serialize");} }

    @Test void acceptsEquivalentAuthoredGeneratedJsonValuesAndPresenceWithoutCoercion() {
        for (var origin:StandardSchemaCompiler.Origin.values()) {
            var c=contract("{\"type\":\"object\",\"required\":[\"value\"],\"properties\":{\"value\":{\"description\":\" exact \"}},\"additionalProperties\":false}",origin);
            for(Object value:Arrays.asList(null,"ref://literal",true,4,5L,new BigInteger("123456789012345678901234567890"),new BigDecimal("0.12345678901234567890123456789"),1.5f,2.5d,Map.of("nested",List.of("x",true)),Arrays.asList(null,Map.of("a","ref://literal")))) {
                var values=input(value);var before=new LinkedHashMap<>(values);
                var result=validator.validate(values,c);
                assertThat(result.valid()).as("%s => %s",value,result.issues()).isTrue();
                assertThat(result.normalizedInput()).isEqualTo(before);assertThat(values).isEqualTo(before);
                if(!(value instanceof Map || value instanceof List)) assertThat(result.normalizedInput().get("value")).isSameAs(value);
            }
            assertThat(validator.validate(Map.of(),c).valid()).isFalse();
            var optional=contract(root("{}"),origin);
            assertThat(validator.validate(Map.of(),optional).normalizedInput()).doesNotContainKey("value");
            assertThat(validator.validate(input(null),optional).normalizedInput()).containsEntry("value",null);
            assertThat(c.schema().tree().at("/properties/value/description").asText()).isEqualTo(" exact ");
        }
    }

    @Test void standardTypesFormatsEnumsAndBoundsAreExact() {
        for(String leaf:List.of("{\"type\":\"integer\"}","{\"type\":\"number\"}","{\"type\":\"boolean\"}"))
            assertThat(validator.validate(input("1"),contract(root(leaf))).valid()).isFalse();
        var date=contract(root("{\"type\":\"string\",\"format\":\"date\"}"));
        assertThat(validator.validate(input("2026-11-13"),date).valid()).isTrue();
        for(String text:List.of("11/13/2026","2026-02-30","2026-1-1")) assertThat(validator.validate(input(text),date).valid()).isFalse();
        var numeric=contract(root("{\"type\":\"number\",\"maximum\":1.00000000000000000001}"));
        assertThat(validator.validate(input(new BigDecimal("1.00000000000000000002")),numeric).valid()).isFalse();
        var text=contract(root("{\"type\":\"string\",\"minLength\":2,\"maxLength\":3,\"enum\":[\"yes\"]}"));
        for(String value:List.of("y","yes!","YES")) assertThat(validator.validate(input(value),text).valid()).isFalse();
        assertThat(validator.validate(input("yes"),text).valid()).isTrue();
    }

    @Test void rejectsNonJsonValuesRecursivelyAtAllOrdinaryOpenLocationsWithoutReading() {
        var resource=new NoReadResource();var stream=new NoReadStream();
        var invalid=List.of(new NoSerialization(),resource,stream,new byte[]{1},Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,new java.util.concurrent.atomic.AtomicInteger(1));
        for(var origin:StandardSchemaCompiler.Origin.values()) for(String openness:List.of("",",\"additionalProperties\":true",",\"additionalProperties\":{}")) {
            var open=contract("{\"type\":\"object\""+openness+"}",origin);
            for(Object value:invalid) for(Object nested:List.of(value,Map.of("deep",value),List.of(Map.of("deep",value)))) {
                var values=input(nested); var before=new LinkedHashMap<>(values);
                var result=validator.validate(values,open);
                assertThat(result.valid()).as("%s %s",openness,value.getClass()).isFalse();
                assertThat(result.issues()).anySatisfy(issue->{assertThat(issue.code()).isEqualTo("non_json_value");assertThat(issue.path()).startsWith("/value");assertThat(issue.rejectedValue()).isSameAs(value);});
                assertThat(values).isEqualTo(before);
            }
        }
        for(Object value:invalid) for(String leaf:List.of("{}","{\"type\":\"string\"}","{\"type\":\"string\",\"x-loomspan-runtime-ref-capable\":false}"))
            assertThat(validator.validate(input(value),contract(root(leaf),StandardSchemaCompiler.Origin.GENERATED)).valid()).isFalse();
        assertThat(resource.opens).isZero();assertThat(stream.reads).isZero();
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void rejectsRuntimeMapKeysAndCollisionsBeforeCopyOrConversion() {
        for(Object key:Arrays.asList(1,true,null)) {
            var bad=new LinkedHashMap<Object,Object>();bad.put(key,"numeric");bad.put("1","string");
            for(var c:List.of(contract(root("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}")),contract(root("{}")),SkillInputContract.genericObject())) {
                for(Map<String,Object> values:List.of(input(bad),input(List.of(Map.of("deep",bad))),(Map)bad)) {
                    var result=validator.validate(values,c);
                    assertThat(result.valid()).isFalse();assertThat(result.issues()).anyMatch(issue->issue.code().equals("non_string_key"));
                    assertThat(bad).hasSize(2);assertThat(bad.get(key)).isEqualTo("numeric");assertThat(bad.get("1")).isEqualTo("string");
                }
            }
        }
        var valid=contract(root("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"));
        assertThat(validator.validate(input(Map.of("a/b~c","ok"," name ","text")),valid).valid()).isTrue();
    }

    @Test void runtimeLeavesAcceptOnlyMarkedDirectTypesAndKeepIdentityAndSchemas() {
        var resource=new NoReadResource();var stream=new NoReadStream();var bytes=new byte[]{1,2};
        var c=contract(root(marked(",\"minLength\":5,\"description\":\"handle\"")),StandardSchemaCompiler.Origin.GENERATED);
        var canonical=c.schema().tree();var provider=c.schema().providerTree();
        for(Object handle:List.of(resource,stream,bytes)) {
            var values=input(handle);var result=validator.validate(values,c);
            assertThat(result.valid()).as("%s",result.issues()).isTrue();
            assertThat(result.normalizedInput().get("value")).isSameAs(handle);assertThat(values.get("value")).isSameAs(handle);
        }
        assertThat(validator.validate(input("short"),c).valid()).isTrue();
        assertThat(validator.validate(input("bad"),c).valid()).isFalse();
        assertThat(validator.validate(input(null),c).valid()).isFalse();
        assertThat(validator.validate(input(4),c).valid()).isFalse();
        assertThat(c.schema().tree()).isEqualTo(canonical);assertThat(c.schema().providerTree()).isEqualTo(provider);
        assertThat(provider.at("/properties/value").has("x-loomspan-runtime-ref-capable")).isFalse();
        assertThat(provider.at("/properties/value/type").asText()).isEqualTo("string");
        assertThat(resource.opens).isZero();assertThat(stream.reads).isZero();
    }

    @Test void checksEveryMarkedArrayAndMapOccurrenceBeforeWeakeningSharedSchemas() {
        var resource=new NoReadResource();String leaf=marked(",\"minLength\":5,\"maxLength\":20,\"enum\":[\"ref://valid\"]");
        var array=contract(root("{\"type\":\"array\",\"minItems\":1,\"maxItems\":3,\"items\":"+leaf+"}"),StandardSchemaCompiler.Origin.GENERATED);
        assertThat(validator.validate(input(List.of(resource,"ref://valid")),array).valid()).isTrue();
        for(Object bad:Arrays.asList("bad","ref://other",null,1,Map.of("x",true))) {
            var result=validator.validate(input(Arrays.asList(resource,"ref://valid",bad)),array);
            assertThat(result.valid()).isFalse();assertThat(result.issues()).anyMatch(issue->issue.path().equals("/value/2") && Objects.equals(issue.rejectedValue(),bad));
        }
        assertThat(validator.validate(input(List.of(resource,resource,resource,resource)),array).valid()).isFalse();
        assertThat(validator.validate(input(List.of()),array).valid()).isFalse();
        var map=contract(root("{\"type\":\"object\",\"additionalProperties\":"+leaf+"}"),StandardSchemaCompiler.Origin.GENERATED);
        assertThat(validator.validate(input(Map.of("first",resource,"second","bad")),map).valid()).isFalse();
        assertThat(resource.opens).isZero();
    }

    @Test void runtimeAdaptationRetainsNestedParentsSiblingsFalsePropertiesAndOriginalDiagnostics() {
        var resource=new NoReadResource();
        String schema="{\"type\":\"object\",\"required\":[\"value\"],\"additionalProperties\":false,\"properties\":{\"value\":{\"type\":\"object\",\"required\":[\"handle\",\"count\"],\"additionalProperties\":false,\"properties\":{\"handle\":"+marked("")+",\"count\":{\"type\":\"integer\"},\"reserved\":false}}}}";
        var c=contract(schema,StandardSchemaCompiler.Origin.GENERATED);
        assertThat(validator.validate(input(Map.of("handle",resource,"count",1)),c).valid()).isTrue();
        for(Map<String,Object> bad:List.<Map<String,Object>>of(Map.of("handle",resource),Map.of("handle",resource,"count","1"),Map.of("handle",resource,"count",1,"extra",true),Map.of("handle",resource,"count",1,"reserved",true))) {
            var values=input(bad);var result=validator.validate(values,c);
            assertThat(result.valid()).isFalse();assertThat(result.issues()).allSatisfy(issue->{
                assertThat(issue.message()).doesNotContain("/value/value","placeholder","x-loomspan");
                if(issue.path().equals("/value")) assertThat(issue.rejectedValue()).isSameAs(bad);
            });
            assertThat(values.get("value")).isSameAs(bad);
        }
        assertThat(validator.validate(Map.of(),c).valid()).isFalse();assertThat(resource.opens).isZero();
    }

    @Test void nullableMarkedPresenceIsIndependentFromRuntimePlaceholders() {
        var resource=new NoReadResource();String leaf="{\"type\":[\"string\",\"null\"],"+MARK+"}";
        var c=contract("{\"type\":\"object\",\"required\":[\"value\"],\"properties\":{\"value\":"+leaf+",\"optional\":"+leaf+",\"items\":{\"type\":\"array\",\"items\":"+leaf+"},\"map\":{\"type\":\"object\",\"additionalProperties\":"+leaf+"}}}",StandardSchemaCompiler.Origin.GENERATED);
        var values=input(null);values.put("items",Arrays.asList(resource,null));values.put("map",input(null));
        var result=validator.validate(values,c);assertThat(result.valid()).as("%s",result.issues()).isTrue();
        assertThat(result.normalizedInput()).containsEntry("value",null).doesNotContainKey("optional");
        assertThat(validator.validate(Map.of("items",List.of(resource)),c).valid()).isFalse();
        values.put("optional",null);assertThat(validator.validate(values,c).valid()).isTrue();
    }

    @Test void invalidMarkedContainersNeverSerializeAdmittedNestedRuntimeHandles() {
        class NoInspectionResource extends NoReadResource {
            int inspections;
            @Override public java.net.URI getURI() {
                inspections++;
                throw new AssertionError("must not inspect resource metadata");
            }
        }
        var resource = new NoInspectionResource();
        // Standard object/array keywords are inapplicable to a string instance, but may
        // still be present in a valid declaration. Invalid container inputs must remain safe.
        for (boolean array : List.of(false, true)) {
            String nested = array ? ",\"items\":" + marked("")
                    : ",\"properties\":{\"nested\":" + marked("") + "}";
            var c = contract(root(marked(nested)), StandardSchemaCompiler.Origin.GENERATED);
            Object container = array ? List.of(resource) : Map.of("nested", resource);
            var canonical = c.schema().tree();
            var result = validator.validate(input(container), c);
            assertThat(result.valid()).isFalse();
            assertThat(result.issues()).anySatisfy(issue -> {
                assertThat(issue.path()).isEqualTo("/value");
                assertThat(issue.code()).isEqualTo("type");
                assertThat(issue.rejectedValue()).isSameAs(container);
            });
            assertThat(c.schema().tree()).isEqualTo(canonical);
            assertThat(resource.opens).isZero();
            assertThat(resource.inspections).isZero();
        }
    }

    @Test void ordinaryUnconstrainedLeavesDoNotBypassParentChecks() {
        var c=contract("{\"type\":\"object\",\"required\":[\"value\"],\"additionalProperties\":false,\"properties\":{\"value\":{},\"count\":{\"type\":\"integer\"},\"items\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":2,\"items\":{}}}}");
        assertThat(validator.validate(input(null),c).valid()).isTrue();
        for(Map<String,Object> bad:List.of(Map.<String,Object>of(),Map.<String,Object>of("value",true,"count","1"),Map.<String,Object>of("value",true,"extra",1),Map.<String,Object>of("value",true,"items",List.of()),Map.<String,Object>of("value",true,"items",List.of(1,2,3)))) assertThat(validator.validate(bad,c).valid()).isFalse();
    }

    @Test void attachmentFirstPassRemainsSeparateAndNonConsuming() {
        var resource=new NoReadResource();var stream=new NoReadStream();
        String leaf="{\"type\":[\"string\",\"null\"],\"x-loomspan-attachment\":{\"media_type\":\"file\",\"allowed_content_types\":[\"text/plain\"]}}";
        var c=contract(root("{\"type\":\"array\",\"items\":"+leaf+",\"maxItems\":2}"));
        assertThat(validator.validate(input(Arrays.asList(resource,null)),c).valid()).isTrue();
        assertThat(validator.validate(input(List.of("ref://files/a")),c).valid()).isTrue();
        for(Object value:List.of(stream,new byte[]{1},"ordinary","ref://bad trailing")) assertThat(validator.validate(input(List.of(resource,value)),c).valid()).isFalse();
        assertThat(validator.validate(input(List.of(resource,resource,resource)),c).valid()).isFalse();
        assertThat(resource.opens).isZero();assertThat(stream.reads).isZero();
    }

    @Test void genericContractsPreserveSeparateRuntimePermissionsAndExactEntryChecks() {
        var handle=new NoReadResource();var stream=new NoReadStream();var bytes=new byte[]{1};
        var values=input(List.of(handle,stream,bytes,"ref://generic"));
        var result=validator.validate(values,SkillInputContract.genericObject());
        assertThat(result.valid()).isTrue();assertThat(((List<?>)result.normalizedInput().get("value")).get(2)).isSameAs(bytes);
        assertThat(handle.opens).isZero();assertThat(stream.reads).isZero();
    }
}
