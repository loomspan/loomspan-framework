package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class YamlSkillEvidenceExpressionCatalogAdditionalTest
{
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                    TestYamlCatalogConfiguration.class))
            .withInitializer(context ->
            {
                try
                {
                    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
                    for (PropertySource<?> propertySource : loader.load(
                            "application-test", new ClassPathResource("application-test.yml")))
                    {
                        context.getEnvironment().getPropertySources().addLast(propertySource);
                    }
                }
                catch (java.io.IOException ex)
                {
                    throw new IllegalStateException("Failed to load application-test.yml", ex);
                }
            });

    @ParameterizedTest
    @ValueSource(strings = {
            "evidence-contract-list-expression-skill.yaml",
            "evidence-contract-object-expression-skill.yaml",
            "evidence-contract-number-expression-skill.yaml",
            "evidence-contract-decimal-expression-skill.yaml",
            "evidence-contract-boolean-expression-skill.yaml",
            "evidence-contract-null-expression-skill.yaml"
    })
    void rejectsEveryNonStringExpressionShapeWithoutCoercion(String filename)
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/" + filename)
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining(filename)
                        .hasMessageContaining("field 'output_schema/properties/vendorName/x-loomspan-evidence'"));
    }

    @Test
    void rejectsNondirectChildAtItsExactColumn()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-nondirect-child-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'output_schema.properties.likelyCause.x-loomspan-evidence'")
                        .hasMessageContaining("column 1")
                        .hasMessageContaining("skill 'default:check-dns' is not a direct allowed child"));
    }

    @Test
    void rejectsReservedOperatorAsAChildReference()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-reserved-child-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'output_schema.properties.result.x-loomspan-evidence'")
                        .hasMessageContaining("column 1")
                        .hasMessageContaining("reserved operator 'and'"));
    }

    @Test
    void rejectsUppercaseEvidenceComponentBeforeChildLookup()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-ambiguous-case-child-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("invalid local name 'INVOICE-PARSER'")
                        .hasMessageNotContaining("did you mean"));
    }

    @Test
    void rejectsRemovedTopLevelEvidenceContractAsAnUnknownField()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-missing-output-schema-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'evidence_contract'")
                        .hasMessageContaining("unknown field"));
    }

    @Test
    void retainsMalformedExpressionDiagnosticsAtPropertyPath()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-malformed-expression-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'output_schema.properties.vendorName.x-loomspan-evidence'")
                        .hasMessageContaining("column 27")
                        .hasMessageContaining("expected a skill name or '('"));
    }

    @Test
    void rejectsTrailingTokensAtPropertyPath()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-trailing-token-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'output_schema.properties.vendorName.x-loomspan-evidence'")
                        .hasMessageContaining("column 24")
                        .hasMessageContaining("expected 'and', 'or', or end of expression"));
    }

    @Test
    void acceptsOperatorSubstringsInExactDirectChildNames()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/evidence-operator-substring-skill.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(YamlSkillCatalog.class)
                            .getSkill("default:evidence-operator-substring-skill")
                            .evidenceContract()
                            .canonicalExpressionForClaim("result"))
                            .isEqualTo("default:android-check and (default:order-lookup or default:candy-parser)");
                });
    }
}
