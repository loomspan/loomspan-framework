package ai.loomspan.integration;

import ai.loomspan.api.*;
import ai.loomspan.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import jakarta.annotation.PreDestroy;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class NamespaceStartupIntegrationTest {
    static final ExecutionConfiguration EMPTY=new ExecutionConfiguration("loomspan: {}");
    static ExecutionConfiguration modelConfiguration(MockWebServer server) {
        return new ExecutionConfiguration("""
                loomspan:
                  connections:
                    local: {driver: openai, base-url: '%s', api-key-ref: fixture-key}
                  models: {model: {connection: local, provider-model: fixture}}
                """.formatted(server.url("/v1")));
    }
    static ApplicationContextRunner runner(){return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,LoomspanJacksonAutoConfiguration.class,LoomspanAutoConfiguration.class,LoomspanAiAutoConfiguration.class))
            .withUserConfiguration(App.class).withPropertyValues("loomspan.skills.locations=classpath:/namespace-startup-no-files/*.yaml");}

    @Test void suppliedLocalDocumentsValidateAndPrepareInEachOwnerBeforePublicationWithoutFallback() throws Exception {
        try (var server = new MockWebServer()) {
            var configuration = modelConfiguration(server);
            var documents = List.of(caller("leaf"), new SkillDocument("leaf", "name: leaf\ndescription: REST leaf\nrest: true\n"));
            runner().withPropertyValues("fixture-key=test-key").run(context -> {
                assertThat(context).hasNotFailed();
                var reloader = context.getBean(SkillReloader.class);
                var template = context.getBean(SkillTemplate.class);
                for (String owner : List.of("default", "sales", "other")) {
                    assertThat(reloader.validate(owner, documents, configuration).valid()).isTrue();
                    try (var candidate = reloader.prepare(owner, documents, configuration)) {
                        assertThat(candidate.snapshot().skills()).extracting(SkillDescriptor::name)
                                .contains(owner + "-parent", owner + "-leaf");
                        reloader.publish(candidate);
                        server.enqueue(tool(owner + "-leaf", "{}"));
                        server.enqueue(text("done"));
                        var views = new ArrayList<SkillExecutionView>();
                        assertThat(template.invoke(owner + "-parent", Map.of(), views::add)).contains("done");
                        assertThat(views.getFirst().events()).extracting(SkillExecutionEvent::route).contains(owner + "-leaf");
                    }
                }
                reloader.publish(reloader.prepare("shared", documents, configuration, NamespaceVisibility.PUBLIC));
                var missing = List.of(caller("leaf"));
                var feedback = reloader.validate("sales", missing, configuration);
                assertThat(feedback.valid()).isFalse();
                assertThat(feedback.issues()).anySatisfy(issue -> assertThat(issue.message()).contains("sales-leaf"));
                assertThatThrownBy(() -> reloader.prepare("sales", missing, configuration)).hasMessageContaining("sales-leaf");
                // Owner-assigned Java remains available privately in the same namespace.
                assertThat(reloader.validate("dept", List.of(caller("fixed")), configuration).valid()).isTrue();
                reloader.publish(reloader.prepare("dept", List.of(caller("fixed")), configuration));
                server.enqueue(tool("dept-fixed", "{\"value\":\"same owner\"}"));
                server.enqueue(text("Java done"));
                assertThat(template.invoke("dept-parent", Map.of())).contains("Java done");
                assertThat(context.getBean(Methods.class).calls).hasValue(1);
            });
            assertThat(server.getRequestCount()).isEqualTo(8);
        }
    }

    @Test void managedWithoutLocalFilesDirectJavaRemovalRecreationAndRestart() throws Exception {
        var ids=new ArrayList<String>();
        for(int restart=0;restart<2;restart++) try (var server = new MockWebServer()) {
            server.enqueue(tool("dept-fixed", "{\"value\":\"managed\"}"));
            server.enqueue(text("managed caller complete"));
            var model = modelConfiguration(server);
            runner().withPropertyValues("fixture-key=test-key").run(context->{
            assertThat(context).hasNotFailed();var reloader=context.getBean(SkillReloader.class);var template=context.getBean(SkillTemplate.class);
            var bean=context.getBean(Methods.class);var startup=reloader.snapshot("DEPT");ids.add(startup.generationId());
            assertThat(startup.skills()).extracting(SkillDescriptor::name).containsExactly("dept-fixed");
            assertThat(template.invoke("dept-fixed",Map.of("value","initial"))).contains("initial");
            assertThat(reloader.validate("caller",List.of(caller("dept-fixed")),model).valid()).isFalse();
            var publicJava=reloader.prepare("dept",List.of(),EMPTY,NamespaceVisibility.PUBLIC);reloader.publish(publicJava);
            var callers=reloader.prepare("caller",List.of(caller("dept-fixed")),model);reloader.publish(callers);
            assertThat(reloader.snapshot("caller").skills()).hasSize(1);
            template.validate("caller-parent", Map.of());
            var pendingCaller = context.getBean(SkillInvocationHandoff.class).handoff("caller-parent", Map.of());
            assertThat(pendingCaller.generationId()).isEqualTo(callers.generationId());
            var supplied=new SkillDocument("departmental","name: supplied\ndescription: supplied\nrest: true\n");
            reloader.publish(reloader.prepare("dept",List.of(supplied),EMPTY));
            assertThat(reloader.snapshot("dept").skills()).extracting(SkillDescriptor::name).containsExactly("dept-fixed","dept-supplied");
            // Omitted visibility is Private on replacement; fresh admission fails, captured work still runs.
            assertThatThrownBy(() -> template.invoke("caller-parent", Map.of())).isInstanceOf(SkillException.class);
            var observed = new ArrayList<SkillExecutionView>();
            assertThat(pendingCaller.invoke(observed::add)).contains("managed");
            assertThat(observed).hasSize(1);
            assertThat(observed.getFirst().events()).extracting(SkillExecutionEvent::route)
                    .contains("caller-parent", "dept-fixed");
            reloader.publish(reloader.prepare("dept", List.of(), EMPTY));
            assertThat(reloader.snapshot("dept").skills()).extracting(SkillDescriptor::name).containsExactly("dept-fixed");
            assertThat(reloader.validate("dept",List.of(new SkillDocument("collision","name: fixed\ndescription: collision\nrest: true\n")),EMPTY).valid()).isFalse();
            var admitted=context.getBean(SkillInvocationHandoff.class).handoff("dept-fixed",Map.of("value","retained"));
            String current=reloader.snapshot("dept").generationId();reloader.remove("dept",current);
            assertThat(bean.destroyed).hasValue(0);
            assertThatThrownBy(()->reloader.snapshot("dept")).isInstanceOf(SkillReloadException.class);
            assertThatThrownBy(()->template.invoke("dept-fixed",Map.of("value","withdrawn"))).isInstanceOf(RuntimeException.class);
            assertThat(admitted.invoke()).contains("retained");assertThat(bean.destroyed).hasValue(0);
            var recreated=reloader.prepare("DEPT",List.of(),EMPTY);reloader.publish(recreated);
            assertThat(recreated.generationId()).isNotEqualTo(current).isNotEqualTo(startup.generationId());
            assertThat(template.invoke("dept-fixed",Map.of("value","recreated"))).contains("recreated");
            assertThat(bean.calls).hasValue(4);
            String defaultId=reloader.snapshot("default").generationId();reloader.remove("default",defaultId);
            assertThatThrownBy(()->reloader.snapshot("default")).isInstanceOf(SkillReloadException.class);
            assertThatThrownBy(()->template.invoke("default-local",Map.of())).isInstanceOf(RuntimeException.class);
            reloader.publish(reloader.prepare("default",List.of(),EMPTY));
            assertThat(template.invoke("default-local",Map.of())).contains("local");
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test void embeddedSameNamespaceStartupCompositionIsValidAndForeignPrivateStartupFails(@TempDir Path directory)throws Exception{
        var file=directory.resolve("parent.yaml");Files.writeString(file,caller("local").yaml());
        try (var server = new MockWebServer()) {
            server.enqueue(tool("default-local", "{}"));
            server.enqueue(text("local caller complete"));
            var configured=runner().withPropertyValues("loomspan.skills.locations="+directory.toUri()+"*.yaml",
                    "loomspan.connections.local.driver=openai", "loomspan.connections.local.base-url="+server.url("/v1"),
                    "loomspan.connections.local.api-key=test-key",
                    "loomspan.models.model.connection=local","loomspan.models.model.provider-model=fixture");
            configured.run(context->{
                assertThat(context).hasNotFailed();
                var reloader = context.getBean(SkillReloader.class);
                var startup = reloader.snapshot("default");
                assertThat(startup.skills()).extracting(SkillDescriptor::name).contains("default-parent","default-local");
                assertThat(context.getBean(SkillTemplate.class).invoke("parent", Map.of())).contains("local");
                assertThat(context.getBean(Methods.class).localCalls).hasValue(1);
                assertThat(reloader.snapshot("default").generationId()).isEqualTo(startup.generationId());
            });
            assertThat(server.getRequestCount()).isEqualTo(2);
            Files.writeString(file,caller("dept-fixed").yaml());
            configured.run(context->assertThat(context).hasFailed());
            assertThat(server.getRequestCount()).isEqualTo(2);
        }
    }

    static SkillDocument caller(String target){return new SkillDocument("caller","name: parent\ndescription: parent\nmodel: model\nallowed_skills: [{name: "+target+"}]\n");}
    static MockResponse tool(String name, String arguments) {
        String escaped = arguments.replace("\\", "\\\\").replace("\"", "\\\"");
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"fixture","object":"chat.completion","created":1,"model":"fixture",
                 "choices":[{"index":0,"message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"call","type":"function","function":{"name":"%s","arguments":"%s"}}]},
                 "finish_reason":"tool_calls"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(name, escaped));
    }
    static MockResponse text(String value) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"id":"fixture","object":"chat.completion","created":1,"model":"fixture",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},
                 "finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(value));
    }
    @Configuration(proxyBeanMethods=false)static class App{
        @Bean Methods methods(){return new Methods();}
        @Bean RestSkillHandler rest(){return invocation->"rest";}
    }
    static class Methods{
        final AtomicInteger calls=new AtomicInteger(),localCalls=new AtomicInteger(),destroyed=new AtomicInteger();
        @SkillMethod(namespace="Dept",name="fixed",description="fixed Java")public String fixed(@SkillParam(description="value")String value){calls.incrementAndGet();return value;}
        @SkillMethod(name="local",description="local Java")public String local(){localCalls.incrementAndGet();return "local";}
        @PreDestroy public void destroy(){destroyed.incrementAndGet();}
    }
}
