package ai.loomspan.internal.skill;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.autoconfigure.LoomspanProperties;
import org.junit.jupiter.api.Test;
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
    @Test void executorInputTransformRejectsInvalidDeclarationAndCompilation() {
        for (String value : List.of("", "null", "1", "true", "literal", "[]", "{}",
                "{language: jslt, expression: '.'}")) {
            assertTransformIssue("name: lookup\ndescription: lookup\nmodel: chat\ninput_transform: " + value,
                    "input_transform", "only on executor leaves");
        }
        for (String value : List.of("", "null", "1", "true", "literal", "[]")) {
            assertTransformIssue(BASE + "input_transform: " + value, "input_transform", "must be an object");
        }
        for (String value : List.of("null", "1", "true", "{}", "[]", "''", "JSLT", "'jslt '")) {
            assertTransformIssue(BASE + "input_transform: {language: " + value + ", expression: '.'}",
                    "input_transform.language", "must be the string 'jslt'");
        }
        assertTransformIssue(BASE + "input_transform: {}", "input_transform.language", "must be the string 'jslt'");
        assertTransformIssue(BASE + "input_transform: {expression: '.'}", "input_transform.language", "must be the string 'jslt'");
        assertTransformIssue(BASE + "input_transform: {language: jslt}", "input_transform.expression", "must be a nonblank string");
        for (String value : List.of("null", "3", "true", "{}", "[]", "''", "' '")) {
            assertTransformIssue(BASE + "input_transform: {language: jslt, expression: " + value + "}",
                    "input_transform.expression", "must be a nonblank string");
        }
        assertTransformIssue(BASE + "input_transform: {language: jslt, expression: '.', extra: 1}",
                "input_transform.extra", "unknown field");
        assertTransformIssue(BASE + "input_transform: {language: jslt, expression: '{'}",
                "input_transform.expression", "Parse error");
        assertTransformIssue(BASE + "input_transform: {language: jslt, expression: '9999999999999999999999999'}",
                "input_transform.expression", "9999999999999999999999999");
        assertTransformIssue(BASE + """
                input_transform:
                  language: jslt
                  expression: |
                    import "missing-executor-transform-module.jslt" as missing
                    {"value": missing:value(.)}
                """, "input_transform.expression", "missing-executor-transform-module.jslt");
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
        for (String field : List.of("model", "prompt", "thinking_level", "planning_mode", "concurrency", "max_steps", "allowed_skills", "linter", "output_schema", "output_schema_max_retries", "output_from", "output_bindings"))
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
