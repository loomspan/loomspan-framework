package ai.loomspan.sample;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SampleSecurityConfiguration
{
    private static final String OBSERVABILITY_NAMESPACE = "/_loomspan/observability/v1/**";

    @Bean
    SecurityFilterChain sampleSecurity(HttpSecurity http) throws Exception
    {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers(OBSERVABILITY_NAMESPACE))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(OBSERVABILITY_NAMESPACE).permitAll()
                        .anyRequest().permitAll())
                .build();
    }
}
