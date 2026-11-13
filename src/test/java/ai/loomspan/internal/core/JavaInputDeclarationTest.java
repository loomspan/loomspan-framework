package ai.loomspan.internal.core;

import ai.loomspan.api.*;
import ai.loomspan.internal.schema.*;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

/** Registration -> canonical JSON validation -> registered invoker.
 * Direct runtime handles exercise binding only, not the still-unmigrated ingress adapter. */
class JavaInputDeclarationTest {
    final ObjectMapper mapper = LoomspanJacksonCodecs.defaults().applicationConversion();
    final StaticListableBeanFactory factory = new StaticListableBeanFactory();
    final SkillMethodBeanPostProcessor processor = SkillMethodBeanPostProcessor.create(mapper,
            LoomspanJacksonCodecs.defaults().schemaTree());
    JavaInputDeclarationTest() { processor.setBeanFactory(factory); }
    CapabilityMetadata register(Object bean) { return register(bean, processor); }
    CapabilityMetadata register(Object bean, SkillMethodBeanPostProcessor target) {
        factory.addBean("bean", bean); target.setBeanFactory(factory);
        target.postProcessAfterInitialization(bean, "bean");
        return target.capabilities().getFirst();
    }
    Object validated(CapabilityMetadata capability, Map<String,Object> input) {
        assertThat(capability.inputContract().schema().validate(mapper.valueToTree(input))).isEmpty();
        return capability.invoker().invoke(input);
    }
    JsonNode property(CapabilityMetadata capability, String name) {
        assertThat(mapper.readTree(capability.tool().inputSchema())).isEqualTo(capability.inputContract().schema().tree());
        return capability.inputContract().schema().tree().path("properties").path(name);
    }

    interface Swapped {
        @SkillMethod(description="fixture") String swapped(@SkillParam(description="left text") String left,
                @SkillParam(description="right count",required=false) Integer right);
    }
    static class SwappedBean implements Swapped {
        public String swapped(String right, Integer left) { return right + ":" + left; }
    }
    @Test void interfaceNameCollisionsPreservePositionalMetadataAndBinding() {
        var capability=register(new SwappedBean());
        assertThat(property(capability,"left").path("type").asText()).isEqualTo("string");
        assertThat(property(capability,"right").path("type").asText()).isEqualTo("integer");
        assertThat(property(capability,"left").path("description").asText()).isEqualTo("left text");
        assertThat(property(capability,"right").path("description").asText()).isEqualTo("right count");
        assertThat(capability.inputContract().schema().tree().path("required").toString()).isEqualTo("[\"left\"]");
        assertThat(validated(capability,Map.of("left","alpha","right",4))).isEqualTo("alpha:4");
        assertThat(validated(capability,Map.of("left","alpha"))).isEqualTo("alpha:null");
    }
    static class Base<T> { int calls; T received; @SkillMethod(description="fixture") public String inherited(T value) { calls++; received=value; return value.getClass().getSimpleName()+":"+value; } }
    static class IntegerBean extends Base<Integer> { }
    static class ResourceBean extends Base<Resource> { }
    @Test void inheritedGenericUsesSameResolvedTypeInSchemaAndBinder() {
        var bean=new IntegerBean(); var capability=register(bean);
        assertThat(property(capability,"value").path("type").asText()).isEqualTo("integer");
        assertThat(validated(capability,Map.of("value",4))).isEqualTo("Integer:4");
        assertThat(capability.inputContract().schema().validate(mapper.valueToTree(Map.of("value","4")))).isNotEmpty();
        assertThat(bean.calls).isEqualTo(1);
    }
    @Test void inheritedRuntimeLeafIsNotExpanded() {
        var bean=new ResourceBean();var capability=register(bean);
        var handle=new org.springframework.core.io.ByteArrayResource(new byte[]{1});
        capability.invoker().invoke(Map.of("value",handle));assertThat(bean.received).isSameAs(handle);
        assertThat(property(capability,"value").path("x-loomspan-runtime-ref-capable").asBoolean()).isTrue();
        assertThat(property(capability,"value").path("type").asText()).isEqualTo("string");
    }
    record Name(String firstName) { }
    record NamedRuntime(Resource fileContent) { }
    static class NamingBean { @SkillMethod(description="fixture") String name(Name value) {return value.firstName();} }
    @Test void productionTwoMapperFactoryUsesApplicationPropertyNames() {
        var application=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        var target=SkillMethodBeanPostProcessor.create(application,LoomspanJacksonCodecs.defaults().schemaTree());
        var capability=register(new NamingBean(),target);
        assertThat(property(capability,"value").path("properties").has("first_name")).isTrue();
        assertThat(validated(capability,Map.of("value",Map.of("first_name","Ada")))).isEqualTo("Ada");
        assertThat(capability.inputContract().schema().validate(mapper.valueToTree(Map.of("value",Map.of("firstName","Ada"))))).isNotEmpty();
    }
    enum Choice { @JsonProperty("wire-ready") READY; @Override public String toString(){return "wrong";} }
    static class EnumBean { @SkillMethod(description="fixture") String choose(Choice value) {return value.name();} }
    @Test void annotatedEnumUsesBindingNameInsteadOfToString() {
        var capability=register(new EnumBean());
        assertThat(property(capability,"value").path("enum").toString()).isEqualTo("[\"wire-ready\"]");
        assertThat(validated(capability,Map.of("value","wire-ready"))).isEqualTo("READY");
        assertThat(capability.inputContract().schema().validate(mapper.valueToTree(Map.of("value","READY")))).isNotEmpty();
    }
    record Uuids(UUID id, List<UUID> ids, Map<String,UUID> byName) { }
    static class UuidBean { int calls; @SkillMethod(description="fixture") String uuid(UUID id, Uuids nested) {calls++; return id+":"+nested.id()+":"+nested.ids().getFirst()+":"+nested.byName().get("a");} }
    @Test void uuidAllShapesAdvertiseStringAndBindWithoutFormatAssertion() {
        var bean=new UuidBean();var capability=register(bean);String id=UUID.randomUUID().toString();
        assertThat(property(capability,"id").path("type").asText()).isEqualTo("string");
        assertThat(property(capability,"id").has("format")).isFalse();
        var input=Map.<String,Object>of("id",id,"nested",Map.of("id",id,"ids",List.of(id),"byName",Map.of("a",id)));
        assertThat(validated(capability,input)).isEqualTo(String.join(":",id,id,id,id));
        var bad=new LinkedHashMap<>(input);bad.put("id","malformed");
        assertThat(capability.inputContract().schema().validate(mapper.valueToTree(bad))).isEmpty();
        assertThatThrownBy(()->capability.invoker().invoke(bad)).isInstanceOf(tools.jackson.databind.exc.InvalidFormatException.class);
        assertThat(bean.calls).isEqualTo(1);
    }

    record Box<T>(T value) { }
    record Pair(Name left, Name right) { }
    static class Finite { @SkillMethod(description="fixture") String finite(Box<Box<String>> value, Pair pair, Name separate) {return value.value().value()+pair.left().firstName()+pair.right().firstName()+separate.firstName();} }
    @Test void finiteGenericNestingAndIndependentRepeatedDtosRetainConstraints() {
        var c=register(new Finite());
        assertThat(property(c,"value").at("/properties/value/properties/value/type").asText()).isEqualTo("string");
        var input=Map.<String,Object>of("value",Map.of("value",Map.of("value","x")),"pair",Map.of("left",Map.of("firstName","a"),"right",Map.of("firstName","b")),"separate",Map.of("firstName","c"));
        assertThat(validated(c,input)).isEqualTo("xabc");
    }
    record Recursive(Recursive child) { }
    record MutualA(MutualB child) { }
    record MutualB(MutualA child) { }
    record ArrayCycle(ArrayCycle[] children) { }
    record ListCycle(List<ListCycle> children) { }
    record MapCycle(Map<String,List<MapCycle>> children) { }
    record GenericCycle<T>(GenericCycle<T> child) { }
    record Growing<T>(Growing<List<T>> child) { }
    record WithResource(Resource resource, Recursive child) { }
    static class BadDirect { @SkillMethod(description="fixture") void bad(Recursive value) {} }
    static class BadMutual { @SkillMethod(description="fixture") void bad(MutualA value) {} }
    static class BadArray { @SkillMethod(description="fixture") void bad(ArrayCycle value) {} }
    static class BadList { @SkillMethod(description="fixture") void bad(ListCycle value) {} }
    static class BadMapCycle { @SkillMethod(description="fixture") void bad(MapCycle value) {} }
    static class BadGeneric { @SkillMethod(description="fixture") void bad(GenericCycle<String> value) {} }
    static class BadGrowing { @SkillMethod(description="fixture") void bad(Growing<String> value) {} }
    static class BadWithResource { @SkillMethod(description="fixture") void bad(WithResource value) {} }
    static Stream<Object> recursiveBeans(){return Stream.of(new BadDirect(),new BadMutual(),new BadArray(),new BadList(),new BadMapCycle(),new BadGeneric(),new BadGrowing(),new BadWithResource());}
    @ParameterizedTest @MethodSource("recursiveBeans") void recursiveDeclarationsRejectBeforePublication(Object bean) {
        factory.addBean("bean",bean);
        assertThatThrownBy(()->processor.postProcessAfterInitialization(bean,"bean"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bad(").hasMessageContaining("value.").hasMessageContaining("cycle");
        assertThat(processor.capabilities()).isEmpty();
    }
    enum Key { A }
    record CustomKey(String value) { }
    record BadNested(Map<Integer,String> wrong) { }
    static class BadKey { @SkillMethod(description="fixture") void bad(Map<Integer,String> value) {} }
    static class BadLongKey { @SkillMethod(description="fixture") void bad(List<Map<Long,String>> value) {} }
    static class BadEnumKey { @SkillMethod(description="fixture") void bad(Map<String,Map<Key,String>> value) {} }
    static class BadCustomKey { @SkillMethod(description="fixture") void bad(Map<CustomKey,String> value) {} }
    static class BadNestedKey { @SkillMethod(description="fixture") void bad(BadNested value) {} }
    static class KeyBase<K> { @SkillMethod(description="fixture") public void bad(Map<K,String> value) {} }
    static class InheritedKey extends KeyBase<Integer> { }
    static Stream<Object> badKeys(){return Stream.of(new BadKey(),new BadLongKey(),new BadEnumKey(),new BadCustomKey(),new BadNestedKey(),new InheritedKey());}
    @ParameterizedTest @MethodSource("badKeys") void unsupportedKeysRejectBeforePublication(Object bean) {
        factory.addBean("bean",bean);
        assertThatThrownBy(()->processor.postProcessAfterInitialization(bean,"bean"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bad(").hasMessageContaining("value").hasMessageContaining("map key type");
        assertThat(processor.capabilities()).isEmpty();
    }
    record Unwrapped(@JsonUnwrapped Name name) { }
    record Disabled(@JsonUnwrapped(enabled=false) Name name) { }
    static class GetterUnwrapped { @JsonUnwrapped(prefix="x",suffix="y") public Name getName(){return null;} public void setName(Name name){} }
    static class BadUnwrapped { @SkillMethod(description="fixture") void bad(Unwrapped value){} }
    static class BadGetterUnwrapped { @SkillMethod(description="fixture") void bad(GetterUnwrapped value){} }
    static class BadListUnwrapped { @SkillMethod(description="fixture") void bad(List<Unwrapped> value){} }
    static class BadMapUnwrapped { @SkillMethod(description="fixture") void bad(Map<String,Unwrapped> value){} }
    static Stream<Object> unwrapped(){return Stream.of(new BadUnwrapped(),new BadGetterUnwrapped(),new BadListUnwrapped(),new BadMapUnwrapped());}
    @ParameterizedTest @MethodSource("unwrapped") void enabledUnwrappedRejectsEffectiveMember(Object bean) {
        factory.addBean("bean",bean);
        assertThatThrownBy(()->processor.postProcessAfterInitialization(bean,"bean"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bad(").hasMessageContaining("value").hasMessageContaining("name").hasMessageContaining("JsonUnwrapped");
        assertThat(processor.capabilities()).isEmpty();
    }
    static class DisabledBean { @SkillMethod(description="fixture") String disabled(Disabled value){return value.name().firstName();} }
    @Test void disabledUnwrappedBindsNestedDto(){var c=register(new DisabledBean());assertThat(validated(c,Map.of("value",Map.of("name",Map.of("firstName","Ada"))))).isEqualTo("Ada");}

    static class Descriptions {
        @SkillMethod(description="fixture") void described(@SkillParam(description="  authored\nresource  ") Resource resource,
                @SkillParam(description="authored stream",required=false) InputStream stream,
                @SkillParam(description="authored bytes") byte[] bytes,
                @SkillParam(description="  ") Resource fallback) {}
    }
    @Test void authoredRuntimeDescriptionsAndCanonicalMetadataAreExact() {
        var c=register(new Descriptions());
        for(var entry:Map.of("resource","  authored\nresource  ","stream","authored stream","bytes","authored bytes").entrySet()) {
            var node=property(c,entry.getKey());assertThat(node.path("description").asText()).isEqualTo(entry.getValue());
            assertThat(node.path("type").asText()).isEqualTo("string");assertThat(node.path("x-loomspan-runtime-ref-capable").asBoolean()).isTrue();
            assertThat(c.inputContract().schema().providerTree().path("properties").path(entry.getKey()).has("x-loomspan-runtime-ref-capable")).isFalse();
        }
        assertThat(property(c,"fallback").path("description").asText()).isEqualTo("Provide a ref:// URI for the resource content.");
        assertThat(c.inputContract().schema().tree().path("required").toString()).isEqualTo("[\"resource\",\"bytes\",\"fallback\"]");
    }
    record RuntimeShapes(Resource direct, List<Resource> items, Map<String,Resource> values, byte[] bytes, InputStream stream) { }
    static class RuntimeBean { @SkillMethod(description="fixture") void runtime(Box<Box<String>> finite, RuntimeShapes resources){} }
    @Test void runtimeLeavesPrecedeBeanExpansionAtEveryLocation() {
        var c=register(new RuntimeBean());var props=property(c,"resources").path("properties");
        for(var n:List.of(props.path("direct"),props.path("items").path("items"),props.path("values").path("additionalProperties"),props.path("bytes"),props.path("stream")))
            assertThat(n.path("x-loomspan-runtime-ref-capable").asBoolean()).isTrue();
    }
    static class UntouchableResource extends AbstractResource {
        int reads;
        public String getDescription(){reads++;throw new AssertionError("resource introspection");}
        public InputStream getInputStream(){reads++;throw new AssertionError("resource read");}
    }
    static class Handles {
        Object received;
        @SkillMethod(description="fixture") String handles(List<Resource> value){received=value;return "ok";}
    }
    @Test void resourceListBindingPreservesIdentityWithoutSerialization() {
        var bean=new Handles();var c=register(bean);var handle=new UntouchableResource();
        assertThat(c.invoker().invoke(Map.of("value",List.of(handle)))).isEqualTo("ok");
        assertThat(((List<?>)bean.received).getFirst()).isSameAs(handle);assertThat(handle.reads).isZero();
    }
    static class MapHandles { Object received; @SkillMethod(description="fixture") String handles(Map<String,Resource> value){received=value;return "ok";} }
    @Test void resourceMapBindingPreservesIdentityWithoutSerialization() {
        var bean=new MapHandles();var c=register(bean);var handle=new UntouchableResource();
        assertThat(c.invoker().invoke(Map.of("value",Map.of("a",handle)))).isEqualTo("ok");
        assertThat(((Map<?,?>)bean.received).get("a")).isSameAs(handle);assertThat(handle.reads).isZero();
    }
    static class ArrayHandles { Object received; @SkillMethod(description="fixture") String handles(Resource[] value){received=value;return "ok";} }
    @Test void resourceArrayBindingPreservesIdentityWithoutSerialization() {
        var bean=new ArrayHandles();var c=register(bean);var handle=new UntouchableResource();
        assertThat(c.invoker().invoke(Map.of("value",List.of(handle)))).isEqualTo("ok");
        assertThat(((Resource[])bean.received)[0]).isSameAs(handle);assertThat(handle.reads).isZero();
    }
    static class ObjectsBean {
        Map<String,Object> received;
        @SkillMethod(description="objects") String objects(@SkillParam(description="any JSON") Object value,
                @SkillParam(required=false) Object optional, List<Object> items, Map<String,Object> values, AnyDto dto) {
            received=new LinkedHashMap<>();received.put("value",value);received.put("optional",optional);
            received.put("items",items);received.put("values",values);received.put("dto",dto);return "ok";
        }
    }
    record AnyDto(Object value) { }
    @Test void unconstrainedGeneratedAndAuthoredSchemasAgreeForJsonKindsPresenceAndBinding() {
        var bean=new ObjectsBean();var c=register(bean);
        var authored=new StandardSchemaCompiler().compile(c.inputContract().schema().tree(),StandardSchemaCompiler.Role.INPUT,StandardSchemaCompiler.Origin.AUTHORED);
        for(Object value:Arrays.asList("ref://literal",true,3,2.5,Map.of("nested",List.of(1,"two")),List.of(false,"ref://literal"),null)) {
            var input=new LinkedHashMap<String,Object>();input.put("value",value);
            input.put("items",Arrays.asList(value,null));
            var values=new LinkedHashMap<String,Object>();values.put("a",value);input.put("values",values);
            var dto=new LinkedHashMap<String,Object>();dto.put("value",value);input.put("dto",dto);
            var json=mapper.valueToTree(input);var before=json.deepCopy();
            assertThat(authored.validate(json)).isEmpty();assertThat(validated(c,input)).isEqualTo("ok");
            assertThat(json).isEqualTo(before);assertThat(bean.received.get("value")).isEqualTo(value);
            assertThat(((AnyDto)bean.received.get("dto")).value()).isEqualTo(value);
            input.put("optional",null);assertThat(validated(c,input)).isEqualTo("ok");
            input.remove("value");assertThat(c.inputContract().schema().validate(mapper.valueToTree(input))).isNotEmpty();
        }
    }
    static class MapsBean {
        @SkillMethod(description="maps") String maps(Map<String,Integer> integers, Map<Object,Object> objects, Map raw) {
            return integers.get("a")+":"+objects.size()+":"+raw.size();
        }
    }
    @Test void supportedMapKeysAndTypedValuesBindWithoutKeyCollapse() {
        var c=register(new MapsBean());
        var input=Map.<String,Object>of("integers",Map.of("a",7),"objects",Map.of("1","a","01","b"),"raw",Map.of("1","a","01","b"));
        assertThat(validated(c,input)).isEqualTo("7:2:2");
        var bad=new LinkedHashMap<>(input);bad.put("integers",Map.of("a","7"));
        assertThat(c.inputContract().schema().validate(mapper.valueToTree(bad))).isNotEmpty();
    }
    static class Mixable { public Name name; }
    abstract static class UnwrappedMixin { @JsonUnwrapped(prefix="pre",suffix="post") public Name name; }
    static class MixBean { @SkillMethod(description="mixin") void mix(Mixable value){} }
    @Test void enabledUnwrappedFromApplicationMixinRejectsBeforePublication() {
        var app=JsonMapper.builder().addMixIn(Mixable.class,UnwrappedMixin.class).build();
        var target=SkillMethodBeanPostProcessor.create(app,LoomspanJacksonCodecs.defaults().schemaTree());
        assertThatThrownBy(()->register(new MixBean(),target)).hasMessageContaining("mix(").hasMessageContaining("value.name").hasMessageContaining("JsonUnwrapped");
        assertThat(target.capabilities()).isEmpty();
    }
    @Test void customKeyDeserializerCannotMakeForbiddenDeclarationPublishable() {
        var module=new tools.jackson.databind.module.SimpleModule();
        module.addKeyDeserializer(CustomKey.class,new KeyDeserializer(){
            @Override public Object deserializeKey(String key,DeserializationContext context){return new CustomKey(key);}
        });
        var app=JsonMapper.builder().addModule(module).build();
        var target=SkillMethodBeanPostProcessor.create(app,LoomspanJacksonCodecs.defaults().schemaTree());
        assertThatThrownBy(()->register(new BadCustomKey(),target)).hasMessageContaining("map key type").hasMessageContaining("CustomKey");
        assertThat(target.capabilities()).isEmpty();
    }
    record Wire(@JsonProperty("wire_name") String javaName) { }
    static class WireBean { @SkillMethod(description="wire") String wire(Wire value){return value.javaName();} }
    @Test void explicitPropertyNameMatchesBinding(){var c=register(new WireBean());assertThat(validated(c,Map.of("value",Map.of("wire_name","ok")))).isEqualTo("ok");}

    public interface Proxied { @SkillMethod(description="proxy") String proxy(@SkillParam(description="contract") String value); }
    public static class ProxiedBean implements Proxied { public String proxy(String value){return value;} }
    @Test void registeredInvokerRetainsJdkProxyAdvice() {
        var proxies=new org.springframework.aop.framework.ProxyFactory(new ProxiedBean());proxies.setInterfaces(Proxied.class);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        proxies.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation->{calls.incrementAndGet();return invocation.proceed();});
        var c=register(proxies.getProxy());
        assertThat(validated(c,Map.of("value","ok"))).isEqualTo("ok");assertThat(calls.get()).isEqualTo(1);
    }
    static class ExecutorBean {
        Recursive internal; SkillExecutorInvocation received;
        @SkillExecutor String execute(SkillExecutorInvocation invocation){received=invocation;return "done";}
    }
    @Test void executorRemainsReferenceOnlyAndDoesNotReflectInternalStructures() {
        var bean=new ExecutorBean();factory.addBean("executor",bean);processor.postProcessAfterInitialization(bean,"executor");
        assertThat(processor.capabilities()).isEmpty();assertThat(processor.executors()).hasSize(1);
        var input=new SkillExecutorInvocation(Map.of("a",1),Map.of("skill",true),Map.of("executor",true),"default-leaf","generation");
        assertThat(processor.executors().getFirst().invoker().apply(input)).isEqualTo("done");assertThat(bean.received).isSameAs(input);
    }
    static class ClosingStream extends ByteArrayInputStream {
        int closes; ClosingStream(){super(new byte[]{1});}
        @Override public void close(){closes++;}
    }
    static class StreamBean {
        @SkillMethod(description="streams") String streams(InputStream first, UUID second){return "ok";}
    }
    static class ThrowingStreamBean { @SkillMethod(description="streams") String streams(InputStream value){throw new IllegalStateException("body");} }
    @Test void managedStreamsCloseOnLaterConversionFailureAndMethodFailure() {
        var c=register(new StreamBean());var stream=new ClosingStream();
        assertThatThrownBy(()->c.invoker().invoke(Map.of("first",stream,"second","bad"))).isInstanceOf(tools.jackson.databind.exc.InvalidFormatException.class);
        assertThat(stream.closes).isEqualTo(1);
        var target=SkillMethodBeanPostProcessor.create(mapper,LoomspanJacksonCodecs.defaults().schemaTree());
        var throwing=register(new ThrowingStreamBean(),target);var other=new ClosingStream();
        assertThatThrownBy(()->throwing.invoker().invoke(Map.of("value",other))).hasMessage("body");assertThat(other.closes).isEqualTo(1);
    }
    static class NestedHandles { Object received; @SkillMethod(description="nested") String nested(List<Map<String,List<Resource>>> value){received=value;return "ok";} }
    @Test void nestedRuntimeContainersRetainHandlesAndConcreteContainerAssignability() {
        var bean=new NestedHandles();var c=register(bean);var handle=new UntouchableResource();
        assertThat(c.invoker().invoke(Map.of("value",List.of(Map.of("a",List.of(handle)))))).isEqualTo("ok");
        assertThat(((List<?>)((Map<?,?>)((List<?>)bean.received).getFirst()).get("a")).getFirst()).isSameAs(handle);
        assertThat(handle.reads).isZero();
    }

    static class RuntimeNamingBean { @SkillMethod(description="naming") void runtime(NamedRuntime value){} }
    @Test void effectivePropertyNamesAlsoOwnRuntimeMarkers() {
        var app=JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        var c=register(new RuntimeNamingBean(),SkillMethodBeanPostProcessor.create(app,LoomspanJacksonCodecs.defaults().schemaTree()));
        assertThat(property(c,"value").at("/properties/file_content/x-loomspan-runtime-ref-capable").asBoolean()).isTrue();
        assertThat(property(c,"value").path("properties").has("fileContent")).isFalse();
    }
    static class DefaultDescriptions {
        @SkillMethod(description="default descriptions") void defaults(Resource resource, @SkillParam(description=" ") InputStream stream,
                @SkillParam(description="\n") byte[] bytes){}
    }
    @Test void absentAndBlankRuntimeDescriptionsRetainFallbackGuidance() {
        var c=register(new DefaultDescriptions());
        assertThat(property(c,"resource").path("description").asText()).isEqualTo("Provide a ref:// URI for the resource content.");
        assertThat(property(c,"stream").path("description").asText()).isEqualTo("Provide a ref:// URI for the stream content.");
        assertThat(property(c,"bytes").path("description").asText()).isEqualTo("Provide a ref:// URI for binary content or an inline string value when appropriate.");
    }
    static class ConcreteHandles { Object received; @SkillMethod(description="concrete") String handles(ArrayList<Resource> value){received=value;return "ok";} }
    @Test void concreteResourceContainerRemainsAssignableWithoutSerializingChildren() {
        var bean=new ConcreteHandles();var c=register(bean);var handle=new UntouchableResource();
        assertThat(c.invoker().invoke(Map.of("value",List.of(handle)))).isEqualTo("ok");
        assertThat(bean.received).isInstanceOf(ArrayList.class);assertThat(((List<?>)bean.received).getFirst()).isSameAs(handle);assertThat(handle.reads).isZero();
    }

    static class RecursiveList extends ArrayList<RecursiveList> { }
    static class RecursiveMap extends LinkedHashMap<String,RecursiveMap> { }
    static class BadRecursiveList { @SkillMethod(description="recursive list") void bad(RecursiveList value){} }
    static class BadRecursiveMap { @SkillMethod(description="recursive map") void bad(RecursiveMap value){} }
    @Test void selfRecursiveContainerDeclarationsRejectWithoutFallbackOrStackOverflow() {
        for(Object bean:List.of(new BadRecursiveList(),new BadRecursiveMap())) {
            var target=SkillMethodBeanPostProcessor.create(mapper,LoomspanJacksonCodecs.defaults().schemaTree());
            assertThatThrownBy(()->register(bean,target)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bad(").hasMessageContaining("value").hasMessageContaining("cycle");
            assertThat(target.capabilities()).isEmpty();
        }
    }

}
