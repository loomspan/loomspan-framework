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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class NamespaceStartupIntegrationTest {
    static final ExecutionConfiguration EMPTY=new ExecutionConfiguration("loomspan: {}");
    static final ExecutionConfiguration MODEL=new ExecutionConfiguration("""
            loomspan:
              connections: {local: {driver: ollama, base-url: 'http://localhost:11434'}}
              models: {model: {connection: local, provider-model: fixture}}
            """);
    static ApplicationContextRunner runner(){return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,LoomspanJacksonAutoConfiguration.class,LoomspanAutoConfiguration.class,LoomspanAiAutoConfiguration.class))
            .withUserConfiguration(App.class).withPropertyValues("loomspan.skills.locations=classpath:/namespace-startup-no-files/*.yaml");}

    @Test void managedWithoutLocalFilesDirectJavaRemovalRecreationAndRestart(){
        var ids=new ArrayList<String>();
        for(int restart=0;restart<2;restart++)runner().run(context->{
            assertThat(context).hasNotFailed();var reloader=context.getBean(SkillReloader.class);var template=context.getBean(SkillTemplate.class);
            var bean=context.getBean(Methods.class);var startup=reloader.snapshot("DEPT");ids.add(startup.generationId());
            assertThat(startup.skills()).extracting(SkillDescriptor::name).containsExactly("dept-fixed");
            assertThat(template.invoke("dept-fixed",Map.of("value","initial"))).contains("initial");
            assertThat(reloader.validate("caller",List.of(caller("dept-fixed")),MODEL).valid()).isFalse();
            var publicJava=reloader.prepare("dept",List.of(),EMPTY,NamespaceVisibility.PUBLIC);reloader.publish(publicJava);
            var callers=reloader.prepare("caller",List.of(caller("dept-fixed")),MODEL);reloader.publish(callers);
            assertThat(reloader.snapshot("caller").skills()).hasSize(1);
            var supplied=new SkillDocument("departmental","name: supplied\ndescription: supplied\nrest: true\n");
            reloader.publish(reloader.prepare("dept",List.of(supplied),EMPTY));
            assertThat(reloader.snapshot("dept").skills()).extracting(SkillDescriptor::name).containsExactly("dept-fixed","dept-supplied");
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
            assertThat(bean.calls).hasValue(3);
            String defaultId=reloader.snapshot("default").generationId();reloader.remove("default",defaultId);
            assertThatThrownBy(()->reloader.snapshot("default")).isInstanceOf(SkillReloadException.class);
            assertThatThrownBy(()->template.invoke("default-local",Map.of())).isInstanceOf(RuntimeException.class);
            reloader.publish(reloader.prepare("default",List.of(),EMPTY));
            assertThat(template.invoke("default-local",Map.of())).contains("local");
        });
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test void embeddedSameNamespaceStartupCompositionIsValidAndForeignPrivateStartupFails(@TempDir Path directory)throws Exception{
        var file=directory.resolve("parent.yaml");Files.writeString(file,caller("default-local").yaml());
        var configured=runner().withPropertyValues("loomspan.skills.locations="+directory.toUri()+"*.yaml",
                "loomspan.connections.local.driver=ollama","loomspan.connections.local.base-url=http://localhost:11434",
                "loomspan.models.model.connection=local","loomspan.models.model.provider-model=fixture");
        configured.run(context->{assertThat(context).hasNotFailed();assertThat(context.getBean(SkillReloader.class).snapshot("default").skills()).extracting(SkillDescriptor::name).contains("default-parent","default-local");});
        Files.writeString(file,caller("dept-fixed").yaml());
        configured.run(context->assertThat(context).hasFailed());
    }

    static SkillDocument caller(String target){return new SkillDocument("caller","name: parent\ndescription: parent\nmodel: model\nallowed_skills: [{name: "+target+"}]\n");}
    @Configuration(proxyBeanMethods=false)static class App{
        @Bean Methods methods(){return new Methods();}
        @Bean RestSkillHandler rest(){return invocation->"rest";}
    }
    static class Methods{
        final AtomicInteger calls=new AtomicInteger(),destroyed=new AtomicInteger();
        @SkillMethod(namespace="Dept",name="fixed",description="fixed Java")public String fixed(@SkillParam(description="value")String value){calls.incrementAndGet();return value;}
        @SkillMethod(name="local",description="local Java")public String local(){return "local";}
        @PreDestroy public void destroy(){destroyed.incrementAndGet();}
    }
}
