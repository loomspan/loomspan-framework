package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Parser tests isolate manifest validation; shared reference completion has integration coverage. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoomspanProperties.class)
class TestYamlCatalogConfiguration {
    @Bean YamlSkillCatalog yamlSkillCatalog(LoomspanProperties properties, LoomspanJacksonCodecs codecs) {
        return new YamlSkillCatalog(properties,
                new org.springframework.core.io.support.PathMatchingResourcePatternResolver(), codecs.skillYaml());
    }
}
