package ai.loomspan.internal.skill;

import ai.loomspan.autoconfigure.LoomspanAutoConfiguration;
import ai.loomspan.autoconfigure.AiDriver;
import ai.loomspan.api.SkillMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class YamlSkillCatalogTests {

    @TempDir
    Path temporaryManifests;

    private final ApplicationContextRunner modelFreeContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                    TestYamlCatalogConfiguration.class));

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ai.loomspan.autoconfigure.LoomspanJacksonAutoConfiguration.class,
                    TestYamlCatalogConfiguration.class))
            .withInitializer(context -> {
                try {
                    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
                    for (PropertySource<?> propertySource : loader.load("application-test", new ClassPathResource("application-test.yml"))) {
                        context.getEnvironment().getPropertySources().addLast(propertySource);
                    }
                }
                catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load application-test.yml", ex);
                }
            });

    @Test
    void acceptsProviderPortablePublicSkillNames() {
        List<String> expectedNames = List.of(
                "A",
                "_",
                "expenseLookup",
                "expense_lookup",
                "_internalStyleAllowed",
                "Skill2",
                "CaseName",
                "caseName",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/public-name/valid/*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);
                    assertThat(catalog.getSkills())
                            .extracting(definition -> definition.manifest().getName())
                            .containsExactlyInAnyOrderElementsOf(expectedNames);
                    expectedNames.forEach(name -> assertThat(catalog.getSkill(name))
                            .isNotNull()
                            .extracting(definition -> definition.manifest().getName())
                            .isEqualTo(name));
                    assertThat(catalog.getSkill("expenselookup")).isNull();
                });
    }

    @TestFactory
    List<DynamicTest> rejectsNonPortablePublicSkillNames() {
        record Case(String filename, String value) {}
        return List.of(
                new Case("leading-digit.yaml", "2expenseLookup"),
                new Case("dot.yaml", "mapped" + ".method.skill"),
                new Case("dash.yaml", "expense-lookup"),
                new Case("space.yaml", "expense lookup"),
                new Case("leading-space.yaml", " expenseLookup"),
                new Case("trailing-space.yaml", "expenseLookup "),
                new Case("hash.yaml", "expenseService#getLatestExpenses"),
                new Case("slash.yaml", "expense/lookup"),
                new Case("colon.yaml", "expense:lookup"),
                new Case("unicode.yaml", "expénsèLookup"),
                new Case("too-long.yaml", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .stream()
                .map(testCase -> DynamicTest.dynamicTest(testCase.filename(), () -> contextRunner
                        .withPropertyValues("loomspan.skills.locations=classpath:/skills/public-name/invalid/" + testCase.filename())
                        .run(context -> assertThat(context.getStartupFailure())
                                .isNotNull()
                                .hasMessageContaining(testCase.filename())
                                .hasMessageContaining("field 'name'")
                                .hasMessageContaining("'" + testCase.value() + "'")
                                .hasMessageContaining("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
                                .hasMessageContaining("1-64 characters")
                                .hasMessageContaining("start with a letter or underscore")
                                .hasMessageContaining("only letters, digits, or underscores")
                                .hasMessageContaining("searchFlights"))))
                .toList();
    }

    @TestFactory
    List<DynamicTest> keepsMissingAndBlankNamesOnRequiredFieldPath() {
        return List.of("missing.yaml", "blank.yaml")
                .stream()
                .map(filename -> DynamicTest.dynamicTest(filename, () -> contextRunner
                        .withPropertyValues("loomspan.skills.locations=classpath:/skills/public-name/invalid/" + filename)
                        .run(context -> assertThat(context.getStartupFailure())
                                .isNotNull()
                                .hasMessageContaining(filename)
                                .hasMessageContaining("field 'name'")
                                .hasMessageContaining("required field is missing or blank")
                                .hasMessageNotContaining("invalid public skill name"))))
                .toList();
    }





    @Test
    void rejectsLlmSkillWhenModelCatalogIsEmpty() {
        modelFreeContextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/llm-missing-model.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("llmMissingModel")
                        .hasMessageContaining("llm-missing-model.yaml")
                        .hasMessageContaining("field 'model'")
                        .hasMessageContaining("required field is missing or blank")
                        .hasMessageContaining("declare a configured model"));
    }











    @Test
    void resolvesConcurrencyDefaultAndExplicitOptOutForPlanningSkills()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/concurrency-*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);
                    assertThat(catalog.getSkill("concurrencyOmitted").concurrencyEnabled()).isTrue();
                    assertThat(catalog.getSkill("concurrencyTrue").concurrencyEnabled()).isTrue();
                    assertThat(catalog.getSkill("concurrencyFalse").concurrencyEnabled()).isFalse();
                });
    }

    @TestFactory
    List<DynamicTest> rejectsInapplicableConcurrencyBeforeValueOrPlanningModeBinding()
    {
        return List.of(
                "concurrency-without-planning.yaml",
                "concurrency-planning-false.yaml",
                "concurrency-planning-null.yaml",
                "concurrency-planning-malformed.yaml")
                .stream()
                .map(filename -> DynamicTest.dynamicTest(filename, () -> contextRunner
                        .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/" + filename)
                        .run(context -> assertThat(context.getStartupFailure())
                                .isNotNull()
                                .hasMessageContaining(filename)
                                .hasMessageContaining("field 'concurrency'")
                                .hasMessageContaining("only when planning_mode is explicitly true")
                                .hasMessageContaining("remove concurrency or enable planning_mode")
                                .hasMessageNotContaining("Cannot deserialize"))))
                .toList();
    }

    @TestFactory
    List<DynamicTest> rejectsInvalidApplicableConcurrencyValues()
    {
        return List.of("concurrency-null-planner.yaml", "concurrency-wrong-type-planner.yaml")
                .stream()
                .map(filename -> DynamicTest.dynamicTest(filename, () -> contextRunner
                        .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/" + filename)
                        .run(context -> assertThat(context.getStartupFailure())
                                .isNotNull()
                                .hasMessageContaining(filename)
                                .hasMessageContaining("field 'concurrency'")
                                .hasMessageContaining("must be a non-null Boolean"))))
                .toList();
    }

    @TestFactory
    List<DynamicTest> rejectsEveryDeclaredConcurrencyValueForEachInapplicablePlanningMode()
    {
        record PlanningMode(String label, String yaml) {}
        record ConcurrencyValue(String label, String yaml) {}
        List<PlanningMode> planningModes = List.of(
                new PlanningMode("omitted", ""),
                new PlanningMode("false", "planning_mode: false\n"),
                new PlanningMode("null", "planning_mode: null\n"),
                new PlanningMode("malformed", "planning_mode: { enabled: true }\n"));
        List<ConcurrencyValue> values = List.of(
                new ConcurrencyValue("true", "true"),
                new ConcurrencyValue("false", "false"),
                new ConcurrencyValue("null", "null"),
                new ConcurrencyValue("wrong-type", "{ enabled: true }"));

        return planningModes.stream()
                .flatMap(mode -> values.stream().map(value -> {
                    String label = mode.label() + "-" + value.label();
                    return DynamicTest.dynamicTest(label, () -> {
                        Path manifest = writeTemporaryManifest("concurrency-" + label + ".yaml", """
                                name: concurrencyMatrixSkill
                                description: Generated concurrency applicability matrix case.
                                model: gpt-5
                                %sconcurrency: %s
                                """.formatted(mode.yaml(), value.yaml()));
                        contextRunner
                                .withPropertyValues("loomspan.skills.locations=" + manifest.toUri())
                                .run(context -> assertThat(context.getStartupFailure())
                                        .isNotNull()
                                        .hasMessageContaining("field 'concurrency'")
                                        .hasMessageContaining("only when planning_mode is explicitly true")
                                        .hasMessageNotContaining("Cannot deserialize"));
                    });
                }))
                .toList();
    }



    @Test
    void defaultsThinkingLevelToMediumWhenModelSupportsThinking() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/default-thinking-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("thinkingDefaultSkill")).isNotNull();
                    assertThat(catalog.getSkill("thinkingDefaultSkill").executionConfiguration())
                            .extracting(
                                    EffectiveSkillExecutionConfiguration::frameworkModel,
                                    EffectiveSkillExecutionConfiguration::connection,
                                    EffectiveSkillExecutionConfiguration::driver,
                                    EffectiveSkillExecutionConfiguration::providerModel,
                                    EffectiveSkillExecutionConfiguration::thinkingLevel)
                            .containsExactly("gpt-5", "openai-main", AiDriver.OPENAI, "openai/gpt-5", "medium");
                });
    }

    @Test
    void omitsThinkingLevelWhenSelectedModelHasNoThinkingSupport() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/non-thinking-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("nonThinkingSkill")).isNotNull();
                    assertThat(catalog.getSkill("nonThinkingSkill").executionConfiguration().providerModel()).isEqualTo("llama3.2");
                    assertThat(catalog.getSkill("nonThinkingSkill").executionConfiguration().thinkingLevel()).isNull();
                });
    }

    @Test
    void failsStartupWhenYamlSkillReferencesUnknownModel() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unknown-model-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("unknown-model-skill.yaml")
                            .hasMessageContaining("field 'model'")
                            .hasMessageContaining("unknown model 'missing-model'");
                });
    }

    @Test
    void failsStartupWhenThinkingLevelIsUnsupportedForModel() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unsupported-thinking-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("invalidUnsupportedThinkingSkill")
                            .hasMessageContaining("unsupported-thinking-skill.yaml")
                            .hasMessageContaining("field 'thinking_level'")
                            .hasMessageContaining("unsupported thinking_level 'high'");
                });
    }

    @Test
    void includesPublicSkillNameInPostParseLlmValidationErrors() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/negative-linter-max-retries-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("invalidNegativeLinterMaxRetriesSkill")
                        .hasMessageContaining("negative-linter-max-retries-skill.yaml")
                        .hasMessageContaining("field 'linter.max_retries'"));
    }

    @Test
    void failsStartupWhenYamlSkillsShareDuplicateName() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/duplicate-name/*.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("second-skill.yaml")
                            .hasMessageContaining("field 'name'")
                            .hasMessageContaining("duplicate skill name 'duplicateSkill'");
                });
    }

    @Test
    void loadsYamlSkillsFromClasspathSkillsPattern() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/pattern/**/*.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkills()).hasSize(2);
                    assertThat(catalog.getSkills())
                            .extracting(definition -> definition.manifest().getName())
                            .containsExactly("patternTwoSkill", "patternOneSkill");
                });
    }

    @Test
    void loadsNoSkillsWhenConfiguredClasspathRootIsMissing() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/does-not-exist/**/*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(YamlSkillCatalog.class).getSkills()).isEmpty();
                });
    }

    @Test
    void loadsNoSkillsWhenClasspathRootExistsButHasNoYamlMatches() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/empty/**/*.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(YamlSkillCatalog.class).getSkills()).isEmpty();
                });
    }

    @Test
    void loadsTypedManifestFieldsWhenPresent() {
        contextRunner
                .withUserConfiguration(TargetBeanConfiguration.class)
                .withPropertyValues(
                        "loomspan.skills.locations=classpath:/skills/valid/allowed-skills-root.yaml,classpath:/skills/valid/allowed-child-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("rootVisibleSkill")).isNotNull();
                    assertThat(catalog.getSkill("rootVisibleSkill").allowedSkills())
                            .containsExactly("allowedVisibleSkill", "disallowedVisibleSkill");
                    assertThat(catalog.getSkill("allowedVisibleSkill").rbacRoles())
                            .containsExactly("ALLOWED");
                });
    }

    @Test
    void loadsStructuredAllowedSkillEntryWithRequiredMinimum() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/allowed-skills-structured-required.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    YamlSkillDefinition definition = context.getBean(YamlSkillCatalog.class)
                            .getSkill("structuredConstraintParent");
                    assertThat(definition.allowedSkills())
                            .containsExactly("requiredChild", "boundedChild", "optionalChild");
                    assertThat(definition.allowedSkillConstraints())
                            .extracting(AllowedSkillConstraint::effectiveMinTasks)
                            .containsExactly(1, 2, 0);
                    assertThat(definition.allowedSkillConstraints().get(1).maxTasks()).isEqualTo(3);
                    assertThat(definition.allowedSkillConstraints().get(2).required()).isFalse();
                    assertThatThrownBy(() -> definition.allowedSkillConstraints()
                            .add(new AllowedSkillConstraint("other", null, null, false)))
                            .isInstanceOf(UnsupportedOperationException.class);
                });
    }

    @TestFactory
    List<DynamicTest> rejectsInvalidStructuredAllowedSkillEntries() {
        record Case(String filename, String path) {}
        return List.of(
                new Case("allowed-skills-scalar-flow.yaml", "allowed_skills[0]"),
                new Case("allowed-skills-scalar-block.yaml", "allowed_skills[0]"),
                new Case("allowed-skills-mixed.yaml", "allowed_skills[1]"),
                new Case("allowed-skills-null-entry.yaml", "allowed_skills[0]"),
                new Case("allowed-skills-missing-name.yaml", "allowed_skills[0].name"),
                new Case("allowed-skills-blank-name.yaml", "allowed_skills[0].name"),
                new Case("allowed-skills-invalid-name.yaml", "allowed_skills[0].name"),
                new Case("allowed-skills-unknown-field.yaml", "allowed_skills[0].minimum_tasks"),
                new Case("allowed-skills-duplicate.yaml", "allowed_skills[1].name"),
                new Case("allowed-skills-negative-min.yaml", "allowed_skills[0].min_tasks"),
                new Case("allowed-skills-null-min.yaml", "allowed_skills[0].min_tasks"),
                new Case("allowed-skills-decimal-max.yaml", "allowed_skills[0].max_tasks"),
                new Case("allowed-skills-negative-max.yaml", "allowed_skills[0].max_tasks"),
                new Case("allowed-skills-wrong-required.yaml", "allowed_skills[0].required"),
                new Case("allowed-skills-null-required.yaml", "allowed_skills[0].required"),
                new Case("allowed-skills-impossible-max.yaml", "allowed_skills[0].max_tasks"),
                new Case("allowed-skills-constraint-without-planning.yaml", "allowed_skills[0]"),
                new Case("allowed-skills-constraint-planning-false.yaml", "allowed_skills[0]"))
                .stream()
                .map(testCase -> DynamicTest.dynamicTest(testCase.filename(), () -> contextRunner
                        .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/" + testCase.filename())
                        .run(context -> assertThat(context.getStartupFailure())
                                .isNotNull()
                                .hasMessageContaining(testCase.filename())
                                .hasMessageContaining("field '" + testCase.path() + "'"))))
                .toList();
    }

    @Test
    void returnsDefensiveManifestCopiesFromPublicCatalog() {
        contextRunner
                .withUserConfiguration(TargetBeanConfiguration.class)
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/allowed-child-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);
                    YamlSkillDefinition definition = catalog.getSkill("allowedVisibleSkill");

                    definition.manifest().setName("mutated.name");
                    definition.manifest().setRbacRoles(java.util.List.of("ROLE_MUTATED"));

                    assertThat(catalog.getSkill("allowedVisibleSkill").manifest().getName())
                            .isEqualTo("allowedVisibleSkill");
                    assertThat(catalog.getSkill("allowedVisibleSkill").rbacRoles())
                            .containsExactly("ALLOWED");

                    assertThat(catalog.getSkill("mutated.name")).isNull();
                });
    }

    @Test
    void returnsDefensiveCopiesFromNestedManifestAccessors() {
        contextRunner
                .withPropertyValues(
                        "loomspan.skills.locations=classpath:/skills/valid/regex-linter-skill.yaml,"
                                + "classpath:/skills/valid/input-schema-skill.yaml,"
                                + "classpath:/skills/valid/output-schema-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    catalog.getSkill("lintedSkill").linter().setType("mutated");
                    catalog.getSkill("inputSchemaSkill").inputSchema().setType("string");
                    catalog.getSkill("outputSchemaSkill").outputSchema().setType("string");

                    assertThat(catalog.getSkill("lintedSkill").linter().getType()).isEqualTo("regex");
                    assertThat(catalog.getSkill("inputSchemaSkill").inputSchema().getType()).isEqualTo("object");
                    assertThat(catalog.getSkill("outputSchemaSkill").outputSchema().getType()).isEqualTo("object");
                });
    }



    @Test
    void loadsYamlSkillPromptAndNormalizesBlankPrompt() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/prompt-skill.yaml,classpath:/skills/valid/blank-prompt-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("promptSkill").prompt())
                            .contains("LONG_PROMPT_SENTINEL")
                            .contains("Follow the private skill instructions.");
                    assertThat(catalog.getSkill("blankPromptSkill").prompt()).isNull();
                });
    }

    @Test
    void rejectsUnknownPromptLikeFields() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unknown-prompt-like-field-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("unknown-prompt-like-field-skill.yaml")
                        .hasMessageContaining("field 'system_prompt'")
                        .hasMessageContaining("unknown field"));
    }



    @Test
    void defaultsTypedManifestFieldsWhenMissing() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/default-thinking-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("thinkingDefaultSkill").allowedSkills()).isEmpty();
                    assertThat(catalog.getSkill("thinkingDefaultSkill").rbacRoles()).isEmpty();
                    assertThat(catalog.getSkill("thinkingDefaultSkill").manifest().getPlanningMode()).isNull();
                });
    }

    @Test
    void loadsPlanningModeOverrideWhenPresent() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/planning-disabled-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("planningDisabledSkill")).isNotNull();
                    assertThat(catalog.getSkill("planningDisabledSkill").manifest().getPlanningMode()).isFalse();
                    assertThat(catalog.getSkill("planningDisabledSkill").planningModeEnabled(true)).isFalse();
                    assertThat(catalog.getSkill("planningDisabledSkill").planningModeEnabled(false)).isFalse();
                });
    }

    @Test
    void loadsTypedRegexLinterConfigurationWhenPresent() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/regex-linter-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("lintedSkill")).isNotNull();
                    assertThat(catalog.getSkill("lintedSkill").linter()).isNotNull();
                    assertThat(catalog.getSkill("lintedSkill").linter().getType()).isEqualTo("regex");
                    assertThat(catalog.getSkill("lintedSkill").linter().getMaxRetries()).isEqualTo(2);
                    assertThat(catalog.getSkill("lintedSkill").linter().getRegex()).isNotNull();
                    assertThat(catalog.getSkill("lintedSkill").linter().getRegex().getPattern()).isEqualTo("^```yaml[\\s\\S]*```$");
                    assertThat(catalog.getSkill("lintedSkill").linter().getRegex().getMessage()).isEqualTo("Return fenced YAML only.");
                });
    }

    @Test
    void defaultsLinterToAbsentWhenManifestDoesNotDeclareOne() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/default-thinking-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("thinkingDefaultSkill")).isNotNull();
                    assertThat(catalog.getSkill("thinkingDefaultSkill").linter()).isNull();
                });
    }

    @Test
    void defaultsOutputSchemaMaxRetriesToTwoWhenSchemaIsPresent() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/output-schema-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("outputSchemaSkill")).isNotNull();
                    assertThat(catalog.getSkill("outputSchemaSkill").manifest().getOutputSchemaMaxRetries()).isEqualTo(2);
                });
    }

    @Test
    void loadsYamlSkillInputSchemaWhenPresent() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/input-schema-skill.yaml")
                .run(context -> {
                    YamlSkillDefinition definition = context.getBean(YamlSkillCatalog.class).getSkill("inputSchemaSkill");

                    assertThat(definition).isNotNull();
                    assertThat(definition.inputSchema()).isNotNull();
                    assertThat(definition.inputSchema().getType()).isEqualTo("object");
                    assertThat(definition.inputSchema().getRequired()).containsExactly("payload");
                });
    }

    @Test
    void acceptsAttachmentOnlyInInputSchema() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/attachment-input-skill.yaml")
                .run(context -> {
                    YamlSkillDefinition definition = context.getBean(YamlSkillCatalog.class).getSkill("attachmentInputSkill");

                    assertThat(definition).isNotNull();
                    assertThat(definition.inputSchema().getProperties().get("image").getType()).isEqualTo("attachment");
                    assertThat(definition.inputSchema().getProperties().get("image").getMediaType()).isEqualTo("image");
                    assertThat(definition.inputSchema().getProperties().get("image").getAllowedContentTypes())
                            .containsExactly("image/jpeg");
                });
    }

    @Test
    void rejectsAttachmentFieldsOnOutputSchema() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/attachment-output-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("attachment-output-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.image.type'")
                        .hasMessageContaining("unsupported schema type 'attachment'"));
    }

    @Test
    void requiresAllowedContentTypesForAttachmentInputAndRejectsMediaFieldsElsewhere() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/attachment-missing-allowed-content-types-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("attachment-missing-allowed-content-types-skill.yaml")
                        .hasMessageContaining("field 'input_schema.properties.image.allowed_content_types'")
                        .hasMessageContaining("must declare at least one content type"));

        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/non-attachment-media-field-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("non-attachment-media-field-skill.yaml")
                        .hasMessageContaining("field 'input_schema.properties.payload.media_type'")
                        .hasMessageContaining("is only supported for attachment schemas"));
    }

    @Test
    void failsStartupWhenInputSchemaUsesUnsupportedKeywordOrNonObjectRoot() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/input-schema-root-array-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("input-schema-root-array-skill.yaml")
                        .hasMessageContaining("field 'input_schema.type'")
                        .hasMessageContaining("root input_schema type must be 'object'"));

        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/input-schema-unsupported-keyword-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("input-schema-unsupported-keyword-skill.yaml")
                        .hasMessageContaining("field 'input_schema.properties.payload.oneOf'")
                        .hasMessageContaining("unknown field"));
    }





    @Test
    void loadsEvidenceContractWhenManifestDeclaresOne() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/evidence-contract-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);

                    assertThat(catalog.getSkill("evidenceContractSkill")).isNotNull();
                    assertThat(catalog.getSkill("evidenceContractSkill").evidenceContract()
                            .canonicalExpressionForClaim("isDuplicate"))
                            .isEqualTo("invoiceParser and expenseLookup");
                });
    }

    @Test
    void failsStartupWhenEvidenceContractContainsBlankExpression() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-blank-evidence-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("evidence-contract-blank-evidence-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.vendorName.evidence'")
                        .hasMessageContaining("expression must be a nonblank YAML string"));
    }

    @Test
    void usesExactValidatedPublicNameInEvidenceExpression() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/evidence-contract-hash-public-name-skill.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(YamlSkillCatalog.class)
                            .getSkill("evidenceHashNameSkill")
                            .evidenceContract()
                            .canonicalExpressionForClaim("result"))
                            .isEqualTo("reviewSkill");
                });
    }

    @Test
    void rejectsListValuedEvidenceExpressionWithoutStringCoercion() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-list-expression-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("evidence-contract-list-expression-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.vendorName.evidence'"));
    }

    @Test
    void rejectsWrongCaseDirectChildWithSuggestion() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/evidence-contract-wrong-case-child-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("field 'output_schema.properties.vendorName.evidence'")
                        .hasMessageContaining("column 1")
                        .hasMessageContaining("did you mean 'invoiceParser'?"));
    }

    @Test
    void failsStartupWhenOutputSchemaMaxRetriesIsPresentWithoutSchema() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-max-retries-without-schema-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-max-retries-without-schema-skill.yaml")
                        .hasMessageContaining("field 'output_schema_max_retries'")
                        .hasMessageContaining("may only be configured when output_schema is present"));
    }

    @Test
    void failsStartupWhenOutputSchemaUsesUnsupportedKeyword() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-unsupported-keyword-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-unsupported-keyword-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.vendorName.oneOf'")
                        .hasMessageContaining("unknown field"));
    }

    @Test
    void failsStartupWhenOutputSchemaUsesEnumOnObjectSchema() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-object-enum-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-object-enum-skill.yaml")
                        .hasMessageContaining("field 'output_schema.enum'")
                        .hasMessageContaining("is only supported for string schemas in the MVP"));
    }

    @Test
    void failsStartupWhenRootSchemaIsNotObject() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-root-array-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-root-array-skill.yaml")
                        .hasMessageContaining("field 'output_schema.type'")
                        .hasMessageContaining("root output_schema type must be 'object'"));
    }

    @Test
    void failsStartupWhenRequiredFieldIsMissingFromProperties() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-missing-required-property-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-missing-required-property-skill.yaml")
                        .hasMessageContaining("field 'output_schema.required'")
                        .hasMessageContaining("references unknown property 'vendorName'"));
    }

    @Test
    void failsStartupWhenPropertiesDifferOnlyByCase() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-duplicate-properties-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-duplicate-properties-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.VendorName'")
                        .hasMessageContaining("duplicates property 'vendorName'"));
    }

    @Test
    void defaultsAdditionalPropertiesToFalseAtEveryObjectDepth() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/output-schema-complex-skill.yaml")
                .run(context -> {
                    YamlSkillCatalog catalog = context.getBean(YamlSkillCatalog.class);
                    YamlSkillManifest.OutputSchemaManifest root = catalog.getSkill("outputSchemaComplexSkill")
                            .manifest().getOutputSchema();
                    YamlSkillManifest.OutputSchemaManifest item = root.getProperties().get("lineItems").getItems();
                    YamlSkillManifest.OutputSchemaManifest details = item.getProperties().get("details");

                    assertThat(root.getAdditionalProperties()).isFalse();
                    assertThat(item.getAdditionalProperties()).isFalse();
                    assertThat(details.getAdditionalProperties()).isFalse();
                });
    }

    @Test
    void failsStartupWhenOutputSchemaContainsNestedArrayItems()
    {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/output-schema-nested-array-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("output-schema-nested-array-skill.yaml")
                        .hasMessageContaining("field 'output_schema.properties.matrix.items.type'")
                        .hasMessageContaining("nested array items are not supported"));
    }

    @Test
    void logsWarningForComplexButSupportedSchema(CapturedOutput output) {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/valid/output-schema-complex-skill.yaml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(YamlSkillCatalog.class).getSkill("outputSchemaComplexSkill")).isNotNull();
                    assertThat(output.getOut())
                            .contains("output_schema")
                            .contains("recommended");
                });
    }

    @Test
    void failsStartupWhenLinterTypeIsMissing() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/missing-linter-type-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("missing-linter-type-skill.yaml")
                            .hasMessageContaining("field 'linter.type'")
                            .hasMessageContaining("required field is missing or blank");
                });
    }

    @Test
    void failsStartupWhenLinterTypeIsUnsupported() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unsupported-linter-type-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("unsupported-linter-type-skill.yaml")
                            .hasMessageContaining("field 'linter.type'")
                            .hasMessageContaining("unsupported linter type 'external'");
                });
    }

    @Test
    void failsStartupWhenRegexBlockIsMissingForRegexType() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/missing-regex-block-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("missing-regex-block-skill.yaml")
                            .hasMessageContaining("field 'linter.regex'")
                            .hasMessageContaining("required block is missing");
                });
    }

    @Test
    void failsStartupWhenRegexPatternIsMissingOrBlank() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/missing-regex-pattern-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("missing-regex-pattern-skill.yaml")
                        .hasMessageContaining("field 'linter.regex.pattern'")
                        .hasMessageContaining("required field is missing or blank"));

        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/blank-regex-pattern-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("blank-regex-pattern-skill.yaml")
                        .hasMessageContaining("field 'linter.regex.pattern'")
                        .hasMessageContaining("required field is missing or blank"));
    }

    @Test
    void failsStartupWhenRegexPatternIsInvalid() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/invalid-regex-linter-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("invalid-regex-linter-skill.yaml")
                            .hasMessageContaining("field 'linter.regex.pattern'")
                            .hasMessageContaining("invalid regex pattern");
                });
    }

    @Test
    void failsStartupWhenLinterMaxRetriesIsMissing() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/missing-linter-max-retries-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("missing-linter-max-retries-skill.yaml")
                            .hasMessageContaining("field 'linter.max_retries'")
                            .hasMessageContaining("required field is missing");
                });
    }

    @Test
    void failsStartupWhenLinterMaxRetriesIsOutOfRange() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/negative-linter-max-retries-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("negative-linter-max-retries-skill.yaml")
                        .hasMessageContaining("field 'linter.max_retries'")
                        .hasMessageContaining("must be between 0 and 3"));

        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/excessive-linter-max-retries-skill.yaml")
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .hasMessageContaining("excessive-linter-max-retries-skill.yaml")
                        .hasMessageContaining("field 'linter.max_retries'")
                        .hasMessageContaining("must be between 0 and 3"));
    }

    @Test
    void failsStartupWhenLinterMaxRetriesHasWrongType() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/wrong-type-linter-max-retries-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("wrong-type-linter-max-retries-skill.yaml")
                            .hasMessageContaining("field 'linter.max_retries'")
                            .hasMessageContaining("Cannot deserialize value of type `java.lang.Integer`");
                });
    }

    @Test
    void failsStartupWhenLinterContainsUnknownFields() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unknown-linter-field-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("unknown-linter-field-skill.yaml")
                            .hasMessageContaining("field 'linter.regex.patterns'")
                            .hasMessageContaining("unknown field");
                });
    }

    @Test
    void failsStartupWhenManifestContainsUnknownRootFields() {
        contextRunner
                .withPropertyValues("loomspan.skills.locations=classpath:/skills/invalid/unknown-root-field-skill.yaml")
                .run(context -> {
                    assertThat(context.getStartupFailure())
                            .isNotNull()
                            .hasMessageContaining("unknown-root-field-skill.yaml")
                            .hasMessageContaining("field 'lintr'")
                            .hasMessageContaining("unknown field");
                });
    }



    private Path writeTemporaryManifest(String filename, String contents) throws IOException
    {
        Path manifest = temporaryManifests.resolve(filename);
        return Files.writeString(manifest, contents);
    }

    @Configuration(proxyBeanMethods = false)
    static class TargetBeanConfiguration {

        @Bean
        TargetBean targetBean() {
            return new TargetBean();
        }
    }

    static class TargetBean {

        @SkillMethod(description = "Deterministic target")
        String deterministicTarget(String input) {
            return "mapped:" + input;
        }
    }
}
