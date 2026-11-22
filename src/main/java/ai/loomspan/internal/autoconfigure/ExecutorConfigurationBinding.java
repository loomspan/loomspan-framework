package ai.loomspan.internal.autoconfigure;

import ai.loomspan.autoconfigure.LoomspanProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindHandlerAdvisor;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Fixed early binding integration, not a consumer customization contract. */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfigurationBinding {
    @Bean
    static StartupVariableResolution startupVariableResolution(Environment environment) {
        return new StartupVariableResolution(environment);
    }

    @Bean
    static ConfigurationPropertiesBindHandlerAdvisor startupSettingsBinding(StartupVariableResolution resolution, Environment environment) {
        return parent -> new AbstractBindHandler(parent) {
            private boolean properties(ConfigurationPropertyName name, Bindable<?> target) {
                return name.toString().equals("loomspan") && target.getType().resolve() == LoomspanProperties.class;
            }
            @Override public <T> Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target, BindContext context) {
                if (!properties(name, target)) return super.onStart(name, target, context);
                var instance = (LoomspanProperties) target.getValue().get();
                resolution.select(instance, context.getSources());
                var ordinary = new java.util.ArrayList<org.springframework.boot.context.properties.source.ConfigurationPropertySource>();
                for (var source : context.getSources()) ordinary.add(source.filter(key ->
                        !ExecutorYamlPropertySourceLoader.selected(key.toString(), ExecutorYamlPropertySourceLoader.CONNECTIONS)
                                && !ExecutorYamlPropertySourceLoader.selected(key.toString(), ExecutorYamlPropertySourceLoader.EXECUTORS)));
                new Binder(ordinary, new PropertySourcesPlaceholdersResolver(environment)).bind(name, target, parent);
                return null;
            }
            @Override public void onFinish(ConfigurationPropertyName name, Bindable<?> target, BindContext context, Object result) throws Exception {
                if (!properties(name, target)) super.onFinish(name, target, context, result);
            }
        };
    }
}
