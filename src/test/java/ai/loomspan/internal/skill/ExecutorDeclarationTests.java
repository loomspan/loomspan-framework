package ai.loomspan.internal.skill;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.autoconfigure.LoomspanProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExecutorDeclarationTests {
    private static final String BASE = "name: lookup\ndescription: SQL lookup\nexecutor: infrastructure-sql\n";
    private YamlSkillCatalog.CheckedDocuments check(String yaml) {
        return new YamlSkillCatalog(new LoomspanProperties()).checkedSupplied(List.of(new SkillDocument("executor-fixture.yml", yaml)), false);
    }
    @Test void executorInputTransformLoads() {
        var checked = check(BASE + """
                input_transform: {language: jslt, expression: '{"answer": .value}'}
                """);
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).singleElement().satisfies(definition ->
                assertThat(definition.executorInput(Map.of("value", "case-17"))).isEqualTo(Map.of("answer", "case-17")));
    }

    @Test void executorOutputTransformLoads() {
        var checked = check(BASE + "output_transform: {language: jslt, expression: '.'}");
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).hasSize(1);
    }

    @Test void acceptsExecutorOutputSchemaWithoutRetries() {
        var checked = check(BASE + """
                output_schema:
                  type: object
                  properties:
                    repeatContactProbability: {type: number, minimum: 0, maximum: 1}
                  required: [repeatContactProbability]
                  additionalProperties: false
                """);
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).singleElement().satisfies(definition -> {
            assertThat(definition.outputSchema()).isNotNull();
            assertThat(definition.outputSchemaMaxRetries()).isZero();
            definition.manifest().setOutputSchema(null);
            assertThat(definition.outputSchema()).isNotNull();
        });
    }

    @Test void executorOutputSchemaUsesTheAuthoredObjectProfile() {
        for (String schema : List.of("{type: string}", "{type: array, items: {type: string}}",
                "{type: [object, 'null']}", "{}", "{type: object, unknown: true}",
                "{type: object, properties: {value: {type: number, minimum: wrong}}}")) {
            var checked = check(BASE + "output_schema: " + schema);
            assertThat(checked.definitions()).isEmpty();
            assertThat(checked.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.fieldPath()).startsWith("output_schema");
                assertThat(issue.toString()).contains("executor-fixture.yml", "lookup");
            });
        }
    }

    @Test void executorOutputSchemaRejectsEvidenceAndExplicitRetries() {
        for (String schema : List.of(
                "{type: object, properties: {value: {type: number, x-loomspan-evidence: child}}}",
                "{type: object, properties: {value: {type: number, x-loomspan-evidence: '['}}}",
                "{type: object, properties: {nested: {type: object, properties: {value: {type: number, x-loomspan-evidence: child}}}}}")) {
            assertThat(check(BASE + "output_schema: " + schema).issues()).singleElement()
                    .satisfies(issue -> assertThat(issue.toString()).contains("output_schema", "x-loomspan-evidence"));
        }
        for (String schema : List.of("", "output_schema: {type: object}\n"))
            for (String retries : List.of("null", "0", "1"))
                assertTransformIssue(BASE + schema + "output_schema_max_retries: " + retries,
                        "output_schema_max_retries", "not allowed for executor");
        assertThat(check(BASE + "output_schema: {type: object, properties: {x-loomspan-evidence: {type: string}}}")
                .issues()).isEmpty();
    }

    @Test void executorInputAndOutputTransformsPrepareIndependently() {
        String yaml = BASE + """
                input_transform: {language: jslt, expression: '{"answer": .value}'}
                output_transform:
                  language: jslt
                  expression: |
                    import "executor-transform/module.jslt" as fixture
                    fixture:value(.result)
                """;
        var checked = check(yaml);
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).singleElement().satisfies(definition -> {
            assertThat(definition.executorInput(Map.of("value", 42))).isEqualTo(Map.of("answer", 42));
            assertThat(definition.executorOutput("{\"result\":17}")).isEqualTo("\"17\"");
            assertThat(definition.source().bytes()).isEqualTo(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        });
        assertThat(check(BASE).definitions()).singleElement().satisfies(definition -> {
            assertThat(definition.hasInputTransform()).isFalse();
            assertThat(definition.hasOutputTransform()).isFalse();
        });
    }

    @ParameterizedTest @ValueSource(strings = {"input_transform", "output_transform"})
    void rejectsInvalidTransformDeclarationAndCompilation(String field) {
        String executor = BASE + field + ": ";
        for (String value : List.of("", "null", "1", "true", "literal", "[]", "{}",
                "{language: jslt, expression: '.'}")) {
            assertTransformIssue("name: lookup\ndescription: lookup\nmodel: chat\n" + field + ": " + value,
                    field, "only on executor leaves");
        }
        for (String value : List.of("", "null", "1", "true", "literal", "[]")) {
            assertTransformIssue(executor + value, field, "must be an object");
        }
        for (String value : List.of("null", "1", "true", "{}", "[]", "''", "JSLT", "'jslt '")) {
            assertTransformIssue(executor + "{language: " + value + ", expression: '.'}",
                    field + ".language", "must be the string 'jslt'");
        }
        assertTransformIssue(executor + "{}", field + ".language", "must be the string 'jslt'");
        assertTransformIssue(executor + "{expression: '.'}", field + ".language", "must be the string 'jslt'");
        assertTransformIssue(executor + "{language: jslt}", field + ".expression", "must be a nonblank string");
        for (String value : List.of("null", "3", "true", "{}", "[]", "''", "' '")) {
            assertTransformIssue(executor + "{language: jslt, expression: " + value + "}",
                    field + ".expression", "must be a nonblank string");
        }
        assertTransformIssue(executor + "{language: jslt, expression: '.', extra: 1}",
                field + ".extra", "unknown field");
        assertTransformIssue(executor + "{language: jslt, expression: '{'}",
                field + ".expression", "Parse error");
        assertTransformIssue(executor + "{language: jslt, expression: '9999999999999999999999999'}",
                field + ".expression", "9999999999999999999999999");
        assertTransformIssue(BASE + field + ":\n" + """
                  language: jslt
                  expression: |
                    import "missing-executor-transform-module.jslt" as missing
                    {"value": missing:value(.)}
                """.indent(2), field + ".expression", "missing-executor-transform-module.jslt");
    }

    @Test void executorInputTransformSupportsBlockSyntaxAndClasspathImports() {
        var checked = check(BASE + """
                input_transform:
                  language: jslt
                  expression: |
                    import "executor-transform/module.jslt" as fixture
                    {"answer": fixture:value(.value)}
                """);
        assertThat(checked.issues()).isEmpty();
        assertThat(checked.definitions()).singleElement().satisfies(definition ->
                assertThat(definition.executorInput(Map.of("value", 17))).containsExactlyEntriesOf(Map.of("answer", "17")));
        assertThat(check(BASE).definitions().getFirst().manifest().isDeclared(YamlSkillManifest.Field.INPUT_TRANSFORM)).isFalse();
    }

    private void assertTransformIssue(String yaml, String path, String message) {
        var checked = check(yaml);
        assertThat(checked.definitions()).isEmpty();
        assertThat(checked.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.fieldPath()).isEqualTo(path);
            assertThat(issue.sourceName()).isEqualTo("executor-fixture.yml");
            assertThat(issue.skillName()).isEqualTo("default-lookup");
            assertThat(issue.message()).contains(message);
        });
    }
    @Test void nativeDeclarationIsUnknownForEveryValueAndForm() {
        for (String value : List.of("null", "{}", "{state: fixed, questions: {q: {type: noul, instructions: decide}}}"))
            for (String form : List.of("", "model: chat\n", "executor: sql\n")) {
                var checked = check("name: lookup\ndescription: lookup\n" + form + "typesafe: " + value);
                assertThat(checked.definitions()).isEmpty();
                assertThat(checked.issues()).singleElement().satisfies(issue ->
                        assertThat(issue.toString()).contains("typesafe", "unknown field"));
            }
        assertThat(check(BASE + "config: {typesafe: {state: literal}, model: provider}").issues()).isEmpty();
    }
    @Test void executorLeafHasExplicitFormAndImmutableData() {
        var checked = check(BASE + """
                rbac_roles: [reader]
                input_schema: {type: object, properties: {id: {type: integer}}}
                config:
                  Nil: null
                  List: [null, {}, [], 7, 2.5, true, "7"]
                  Upper: A
                  upper: B
                  literal.dot: kept
                  literal: {dot: separate}
                """);
        assertThat(checked.issues()).isEmpty();
        var definition = checked.definitions().getFirst();
        assertThat(definition.form()).isEqualTo(YamlSkillDefinition.Form.EXECUTOR);
        assertThat(definition.executor()).isEqualTo("infrastructure-sql");
        assertThat(definition.executionConfiguration()).isNull();
        assertThat(definition.rbacRoles()).containsExactly("reader");
        assertThat(definition.inputSchema().tree().path("properties").has("id")).isTrue();
        assertThat(definition.config()).containsEntry("Nil", null).containsEntry("Upper", "A").containsEntry("upper", "B").containsEntry("literal.dot", "kept");
        assertThat(definition.config().get("List")).isEqualTo(java.util.Arrays.asList(null, Map.of(), List.of(), 7, 2.5, true, "7"));
        assertThatThrownBy(() -> definition.config().put("new", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<Object>) definition.config().get("List")).add(1)).isInstanceOf(UnsupportedOperationException.class);
        definition.manifest().setConfig(Map.of("replaced", true));
        assertThat(definition.config()).doesNotContainKey("replaced");
        assertThat(definition.manifest().getConfig()).isEqualTo(definition.config());
    }
    @Test void executorRejectsForbiddenPresenceWithoutFormPrecedence() {
        for (String field : List.of("model", "prompt", "thinking_level", "planning_mode", "concurrency", "max_steps", "allowed_skills", "linter", "output_schema_max_retries", "output_from", "output_bindings"))
            for (String value : List.of("null", "{}", "[]", "true", "''")) {
                var checked = check(BASE + field + ": " + value);
                assertThat(checked.definitions()).as(field + "=" + value).isEmpty();
                assertThat(checked.issues()).singleElement().satisfies(issue -> {
                    assertThat(issue.fieldPath()).isEqualTo(field);
                    assertThat(issue.toString()).contains("executor-fixture.yml", "lookup", "not allowed");
                });
            }
    }
    @Test void yamlNullsAndAliasedContainersSurviveManifestIngress() {
        var checked = check(BASE + "config: {template: &shared {Nil: ~, Values: [null, {}, []]}, copied: *shared, literal: '~'}");
        assertThat(checked.issues()).isEmpty();
        var config = checked.definitions().getFirst().config();
        assertThat(config.get("copied")).isEqualTo(config.get("template")).isInstanceOf(Map.class);
        assertThat((Map<String, Object>)config.get("copied")).containsEntry("Nil", null);
        assertThat(config).containsEntry("literal", "~");
        assertThat(check(BASE + "config: &cycle {self: *cycle}").issues()).singleElement()
                .satisfies(issue -> assertThat(issue.toString()).contains("executor-fixture.yml", "recursive"));
    }
    @Test void configRequiresExecutorAndMapValue() {
        for (String value : List.of("null", "", "a", "1", "true", "[]", "[{}]")) {
            assertThat(check(BASE + "config: " + value).issues()).singleElement().satisfies(issue -> assertThat(issue.fieldPath()).isEqualTo("config"));
        }
        for (String value : List.of("null", "{}"))
            for (String form : List.of("model: chat\n"))
                assertThat(check("name: lookup\ndescription: lookup\n" + form + "config: " + value).issues()).singleElement()
                        .satisfies(issue -> assertThat(issue.fieldPath()).isEqualTo("config"));
        for (String suffix : List.of("", "config: {}")) {
            var checked = check(BASE + suffix);
            assertThat(checked.issues()).isEmpty();
            assertThat(checked.definitions().getFirst().config()).isEmpty();
        }
    }
    @Test void restIsUnsupportedAndExecutorReferencesAreStrict() {
        for (String value : List.of("true", "false", "null", "", "{}", "[]"))
            assertThat(check("name: lookup\ndescription: lookup\nrest: " + value).issues()).singleElement()
                    .satisfies(issue -> assertThat(issue.toString()).contains("rest", "unknown field"));
        for (String value : List.of("null", "", "' '", "' sql'", "'sql '", "{}", "[]", "true", "1", "a-b-c"))
            assertThat(check("name: lookup\ndescription: lookup\nexecutor: " + value).issues()).singleElement()
                    .satisfies(issue -> assertThat(issue.fieldPath()).isEqualTo("executor"));
        assertThat(check(BASE.replace("infrastructure-sql", "sql")).definitions().getFirst().executor()).isEqualTo("default-sql");
    }
}
