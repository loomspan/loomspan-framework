package ai.loomspan.internal.autoconfigure;

import ai.loomspan.internal.skill.ExecutorConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/** Fixed early binding integration, not a consumer customization contract. */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfigurationBinding {
    @Bean
    @ConfigurationPropertiesBinding
    static Converter<ExecutorYamlPropertySourceLoader.Raw, ExecutorConfiguration> executorConfigurationConverter() {
        return new RawConverter();
    }
    private static final class RawConverter implements Converter<ExecutorYamlPropertySourceLoader.Raw, ExecutorConfiguration> {
        @Override public ExecutorConfiguration convert(ExecutorYamlPropertySourceLoader.Raw source) {
            return ExecutorConfiguration.from(source.value());
        }
    }
}
