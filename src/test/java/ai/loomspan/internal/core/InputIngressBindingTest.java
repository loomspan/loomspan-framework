package ai.loomspan.internal.core;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import ai.loomspan.internal.runtime.input.*;
import ai.loomspan.internal.schema.StandardSchemaCompiler;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real generated/authored contracts -> ingress validator -> registered Java binder.
 * This direct composition does not exercise the blocked public/coordinator dispatch graph. */
class InputIngressBindingTest {
    final SkillInputValidator validator=new SkillInputValidator();
    CapabilityMetadata register(Object bean) {
        var codecs=LoomspanJacksonCodecs.defaults();
        var processor=SkillMethodBeanPostProcessor.create(codecs.applicationConversion(),codecs.schemaTree());
        var factory=new StaticListableBeanFactory();factory.addBean("bean",bean);processor.setBeanFactory(factory);
        processor.postProcessAfterInitialization(bean,"bean");return processor.capabilities().getFirst();
    }
    static class AnyBean {
        int calls;Object received;
        @SkillMethod(description="Any JSON") String accept(@SkillParam(description=" exact ") Object value){calls++;received=value;return "ok";}
    }
    record Values(Object field,List<Object> items,Map<String,Object> byName){}
    static class ValuesBean { int calls;Values received; @SkillMethod(description="JSON positions") String accept(Values value){calls++;received=value;return "ok";} }
    Object execute(CapabilityMetadata c,SkillInputContract contract,Map<String,Object> input) {
        var result=validator.validate(input,contract);
        if(!result.valid()) return result;
        return c.invoker().invoke(result.normalizedInput());
    }
    @Test void generatedAndAuthoredObjectInputsReachJavaUnchangedIncludingPresentNull() {
        var bean=new AnyBean();var c=register(bean);
        var authored=new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                new StandardSchemaCompiler().compile(c.inputContract().schema().providerTree(),StandardSchemaCompiler.Role.INPUT,StandardSchemaCompiler.Origin.AUTHORED));
        for(var contract:List.of(c.inputContract(),authored)) for(Object value:Arrays.asList(null,"ref://literal",true,new BigDecimal("0.12345678901234567890123456789"),Map.of("x","ref://nested"),Arrays.asList(null,1,true))) {
            var input=new LinkedHashMap<String,Object>();input.put("value",value);
            assertThat(execute(c,contract,input)).isEqualTo("ok");assertThat(bean.received).isEqualTo(value);
            assertThat(input).containsEntry("value",value);
        }
        int before=bean.calls;
        assertThat(execute(c,c.inputContract(),Map.of())).isInstanceOf(SkillInputValidationResult.class);
        assertThat(bean.calls).isEqualTo(before);
        assertThat(c.inputContract().schema().tree().at("/properties/value/description").asText()).isEqualTo(" exact ");
    }
    @Test void dtoFieldsCollectionItemsAndMapValuesRemainUnconstrainedThroughRealBinding() {
        var bean=new ValuesBean();var c=register(bean);
        var nested=new LinkedHashMap<String,Object>();nested.put("field",null);nested.put("items",Arrays.asList("ref://literal",true,null,new BigDecimal("1.12345678901234567890")));nested.put("byName",Map.of("mixed",Map.of("a",List.of(1,"x"))));
        assertThat(execute(c,c.inputContract(),Map.of("value",nested))).isEqualTo("ok");
        assertThat(bean.received.field()).isNull();assertThat(bean.received.items()).isEqualTo(nested.get("items"));assertThat(bean.received.byName()).isEqualTo(nested.get("byName"));
        assertThat(bean.calls).isEqualTo(1);
    }
    static class Untouched extends AbstractResource {
        int reads;public String getDescription(){return "untouched";}
        public InputStream getInputStream(){reads++;throw new AssertionError("do not read");}
    }
    static class UntouchedStream extends InputStream { int reads;public int read(){reads++;throw new AssertionError("do not read");} }
    @Test void invalidOrdinaryValuesAndOpenPropertiesNeverReachJava() {
        var bean=new AnyBean();var c=register(bean);var resource=new Untouched();var stream=new UntouchedStream();
        for(Object value:List.of(resource,stream,new byte[]{1},new Object(),Double.NaN,Float.POSITIVE_INFINITY)) {
            for(Map<String,Object> input:List.<Map<String,Object>>of(Map.of("value",value),Map.of("value",List.of(Map.of("nested",value))))) {
                var result=(SkillInputValidationResult)execute(c,c.inputContract(),input);assertThat(result.valid()).isFalse();assertThat(bean.calls).isZero();
            }
            for(String open:List.of("",",\"additionalProperties\":true",",\"additionalProperties\":{}")) {
                var contract=new SkillInputContract(SkillInputContract.SkillInputContractKind.YAML_EXPLICIT,
                        new StandardSchemaCompiler().compile("{\"type\":\"object\",\"properties\":{\"value\":{}}"+open+"}",StandardSchemaCompiler.Role.INPUT,StandardSchemaCompiler.Origin.AUTHORED));
                var result=(SkillInputValidationResult)execute(c,contract,Map.of("value","okay","extra",List.of(value)));
                assertThat(result.valid()).isFalse();assertThat(bean.calls).isZero();
            }
        }
        assertThat(resource.reads).isZero();assertThat(stream.reads).isZero();
    }
    static class HandlesBean {
        int calls;Resource resource;InputStream stream;byte[] bytes;
        @SkillMethod(description="Handles") String accept(Resource resource,InputStream stream,byte[] bytes){calls++;this.resource=resource;this.stream=stream;this.bytes=bytes;return "ok";}
    }
    @Test void acceptedMarkedHandlesReachExistingBinderWithOriginalIdentity() {
        var bean=new HandlesBean();var c=register(bean);var resource=new Untouched();var stream=new UntouchedStream();var bytes=new byte[]{1};
        var input=Map.<String,Object>of("resource",resource,"stream",stream,"bytes",bytes);
        var canonical=c.inputContract().schema().tree();
        assertThat(execute(c,c.inputContract(),input)).isEqualTo("ok");assertThat(bean.calls).isEqualTo(1);
        assertThat(bean.resource).isSameAs(resource);assertThat(bean.stream).isSameAs(stream);assertThat(bean.bytes).isSameAs(bytes);
        assertThat(resource.reads).isZero();assertThat(stream.reads).isZero();assertThat(c.inputContract().schema().tree()).isEqualTo(canonical);
    }
    static class NeedsBytes { int calls; @SkillMethod(description="Bytes") String accept(byte[] value){calls++;return "ok";} }
    @Test void runtimeAcceptanceDoesNotPromiseConversionAndBinderStillOwnsFailureAndClosure() {
        var bean=new NeedsBytes();var c=register(bean);
        class FailsReading extends AbstractResource {
            boolean closed;
            public String getDescription(){return "fail on binding read";}
            public InputStream getInputStream(){return new InputStream(){public int read() throws IOException {throw new IOException("binding read failure");} public void close(){closed=true;}};}
        }
        var resource=new FailsReading();var result=validator.validate(Map.of("value",resource),c.inputContract());
        assertThat(result.valid()).isTrue();assertThat(resource.closed).isFalse();
        assertThatThrownBy(()->c.invoker().invoke(result.normalizedInput())).isInstanceOf(IllegalStateException.class).hasMessageContaining("Failed to read binary payload");
        assertThat(resource.closed).isTrue();assertThat(bean.calls).isZero();
    }
}
