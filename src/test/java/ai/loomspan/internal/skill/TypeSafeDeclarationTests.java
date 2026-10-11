package ai.loomspan.internal.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.autoconfigure.LoomspanProperties;
import ai.loomspan.internal.serialization.LoomspanJacksonCodecs;
import ai.loomspan.internal.typesafe.TypeSafeDeclaration;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class TypeSafeDeclarationTests {
    @Test
    void acceptsDeclarationAsNativeLeaf() {
        var props = new LoomspanProperties();
        var connection = new LoomspanProperties.ConnectionProperties();
        connection.setDriver(ai.loomspan.autoconfigure.AiDriver.valueOf("TYPESAFE"));
        connection.setApiKey("fixture");
        props.getConnections().put("native", connection);
        var model = new LoomspanProperties.ModelCatalogEntry();
        model.setConnection("native");
        model.setProviderModel("jev");
        props.getModels().put("judge", model);
        var result =
                new YamlSkillCatalog(props)
                        .checkedSupplied(
                                List.of(
                                        new SkillDocument(
                                                "test",
                                                """
name: judgment
description: native
model: judge
input_schema: {type: object}
typesafe:
  state: literal
  questions:
    yes: {type: noul, instructions: decide}
""")),
                                false);
        assertThat(result.issues()).isEmpty();
        var definition = result.definitions().getFirst();
        assertThat(definition.typesafe()).isTrue();
        assertThat(definition.typeSafeDeclaration()).isSameAs(definition.typeSafeDeclaration());
        var copied = definition.manifest();
        assertThat(copied.compiledTypesafe()).isSameAs(definition.typeSafeDeclaration());
        copied.setTypesafe(
                LoomspanJacksonCodecs.defaults()
                        .skillYaml()
                        .readTree(
                                "state: changed\n"
                                    + "questions: {q: {type: noul, instructions: changed}}"));
        assertThat(
                        definition
                                .typeSafeDeclaration()
                                .resolve(Map.of())
                                .json()
                                .get("state")
                                .stringValue())
                .isEqualTo("literal");
    }

    @Test
    void incompatibleDeclarationsAreRejectedEvenWhenNullFalseOrEmpty() {
        for (String field :
                List.of(
                        "rest: false",
                        "prompt: null",
                        "thinking_level: null",
                        "planning_mode: false",
                        "concurrency: false",
                        "max_steps: null",
                        "allowed_skills: []",
                        "linter: null",
                        "output_schema: null",
                        "output_schema_max_retries: null",
                        "output_from: null",
                        "output_bindings: {}")) {
            var result =
                    new YamlSkillCatalog(new LoomspanProperties())
                            .checkedSupplied(
                                    List.of(
                                            new SkillDocument(
                                                    "test",
                                                    "name: judge\n"
                                                        + "description: native\n"
                                                        + "model: judge\n"
                                                        + "input_schema: {type: object}\n"
                                                            + field
                                                            + "\n"
                                                            + "typesafe: {state: literal,"
                                                            + " questions: {q: {type: noul,"
                                                            + " instructions: decide}}}\n")),
                                    false);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().fieldPath())
                    .isEqualTo(field.substring(0, field.indexOf(':')));
        }
        for (String block :
                List.of(
                        "null",
                        "text",
                        "[]",
                        "{state: literal, questions: {}}",
                        "{state: literal, questions: {q: {type: noul, instructions: decide,"
                            + " unknown: 1}}}",
                        "{state: literal, questions: {q: {type: noul, instructions: decide}},"
                            + " unknown: 1}")) {
            var result =
                    new YamlSkillCatalog(new LoomspanProperties())
                            .checkedSupplied(
                                    List.of(
                                            new SkillDocument(
                                                    "test",
                                                    "name: judge\n"
                                                        + "description: native\n"
                                                        + "model: judge\n"
                                                        + "input_schema: {type: object}\n"
                                                        + "typesafe: "
                                                            + block
                                                            + "\n")),
                                    false);
            assertThat(result.issues()).hasSize(1);
            assertThat(result.issues().getFirst().fieldPath()).startsWith("typesafe");
        }
    }

    @Test
    void typeSafeRequiresObjectInputAndRejectsAttachmentNodes() {
        for (String schema :
                List.of(
                        "",
                        "input_schema: {type: string}\n",
                        "input_schema: {type: object, properties: {file: {type: attachment,"
                            + " media_type: image, allowed_content_types: [image/png]}}}\n")) {
            var checked =
                    new YamlSkillCatalog(new LoomspanProperties())
                            .checkedSupplied(
                                    List.of(
                                            new SkillDocument(
                                                    "input-contract",
                                                    "name: judge\n"
                                                        + "description: native\n"
                                                        + "model: judge\n"
                                                            + schema
                                                            + "typesafe: {state: text, questions:"
                                                            + " {q: {type: noul, instructions:"
                                                            + " decide}}}\n")),
                                    false);
            assertThat(checked.issues()).hasSize(1);
            assertThat(checked.issues().getFirst().fieldPath()).startsWith("input_schema");
        }
    }

    private TypeSafeDeclaration declaration(String yaml) {
        return TypeSafeDeclaration.compile(
                LoomspanJacksonCodecs.defaults().skillYaml().readTree(yaml));
    }

    @Test
    void structuredSubstitutionPreservesTypesAndDoesNotRescanInsertedData() {
        var d =
                declaration(
                        """
state:
  entire: ${/input}
  escaped: $${/input/name}
  literal_key_${/input/name}: [null, 2, true]
  message: "pré ${/input/name} ${/input/flag} ${/input/count}  "
questions:
  Chosen:
    type: choice
    criteria: ${/input/candidates}
  Severity:
    type: score
    criteria: [null]
  Yes:
    type: noul
    criteria: {'true': {reason: 'yes'}}
""");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("name", "${/input/never}");
        input.put("flag", true);
        input.put("count", 3);
        input.put("nil", null);
        input.put("candidates", Map.of("One", List.of("a")));
        var resolved = d.resolve(input).json();
        assertThat(resolved.at("/state/entire/nil").isNull()).isTrue();
        assertThat(resolved.at("/state/escaped").stringValue()).isEqualTo("${/input/name}");
        assertThat(resolved.at("/state/message").stringValue())
                .isEqualTo("pré ${/input/never} true 3  ");
        assertThat(resolved.at("/state/literal_key_${~1input~1name}/1").intValue()).isEqualTo(2);
        assertThat(resolved.at("/questions/Severity/criteria/0").isNull()).isTrue();
        assertThat(resolved.at("/questions/Chosen/criteria/One").isArray()).isTrue();
        input.put("name", "changed");
        assertThat(resolved.at("/state/entire/name").stringValue()).isEqualTo("${/input/never}");
    }

    @Test
    void pointerLookupUsesExactObjectKeysAndCanonicalArrayIndices() {
        var d =
                declaration(
                        """
state: [ '${/input/a~1b/~0/}', '${/input/0}', '${/input/list/0}' ]
questions: {yes: {type: noul, instructions: decide}}
""");
        var resolved =
                d.resolve(
                                Map.of(
                                        "a/b",
                                        Map.of("~", Map.of("", "empty")),
                                        "0",
                                        "numeric object key",
                                        "list",
                                        List.of("first")))
                        .json();
        assertThat(resolved.get("state").toString())
                .isEqualTo("[\"empty\",\"numeric object key\",\"first\"]");
        for (String pointer :
                List.of(
                        "/input/list/00",
                        "/input/list/-",
                        "/input/list/2",
                        "/input/list/0/x",
                        "/input/missing")) {
            var invalid =
                    declaration(
                            "state: '${"
                                    + pointer
                                    + "}'\nquestions: {yes: {type: noul, instructions: decide}}\n");
            assertThatThrownBy(() -> invalid.resolve(Map.of("list", List.of("scalar"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void malformedExpressionsFailRegistrationAndEscapesRemainLiteral() {
        for (String value :
                List.of(
                        "${/input",
                        "${ENV}",
                        "${ /input}",
                        "${/input/a~2}",
                        "${/input/${/input/a}}"))
            assertThatThrownBy(
                            () ->
                                    declaration(
                                            "state: '"
                                                    + value
                                                    + "'\n"
                                                    + "questions: {yes: {type: noul, instructions:"
                                                    + " decide}}\n"))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                        declaration(
                                        "state: '$${ENV}'\n"
                                            + "questions: {yes: {type: noul, instructions:"
                                            + " decide}}\n")
                                .resolve(Map.of())
                                .json()
                                .get("state")
                                .stringValue())
                .isEqualTo("${ENV}");
    }

    @Test
    void resolvedShapesRejectWithoutCoercionOrInterpolationOfStructuredValues() {
        for (Object value : Arrays.asList(null, List.of("item"), Map.of("key", "value"))) {
            var input = new LinkedHashMap<String, Object>();
            input.put("value", value);
            var d =
                    declaration(
                            "state: ' ${/input/value}'\n"
                                + "questions: {yes: {type: noul, instructions: decide}}\n");
            assertThatThrownBy(() -> d.resolve(input)).isInstanceOf(IllegalArgumentException.class);
        }
        var d =
                declaration(
                        "state: '${/input/state}'\n"
                            + "questions: {choice: {type: choice, criteria:"
                            + " '${/input/candidates}'}}\n");
        assertThatThrownBy(() -> d.resolve(Map.of("state", 7, "candidates", Map.of("A", "yes"))))
                .hasMessageContaining("typesafe.state");
        assertThatThrownBy(
                        () ->
                                d.resolve(
                                        Map.of(
                                                "state",
                                                "ok",
                                                "candidates",
                                                Map.of("A", "yes", "a", "no"))))
                .hasMessageContaining("collides");
        assertThatThrownBy(() -> d.resolve(Map.of("state", "ok", "candidates", Map.of("A", 42))))
                .hasMessageContaining("criteria.A");
        assertThatThrownBy(() -> d.resolve(Map.of("state", "ok", "candidates", Map.of())))
                .hasMessageContaining("1 to 255");
    }

    @Test
    void literalConstraintsAreCheckedDespiteOtherDeferredValues() {
        for (String questions :
                List.of(
                        "{q: {type: choice, instructions: '${/input/x}', criteria: {a: 42}}}",
                        "{q: {type: score, criteria: []}}",
                        "{q: {type: noul}}",
                        "{q: {type: noul, criteria: {'true': null}}}",
                        "{q: {type: noul, criteria: {other: null}, instructions: text}}",
                        "{q: {type: choice, criteria: {A: null, a: null}}}",
                        "{Q: {type: noul, instructions: yes}, q: {type: noul, instructions: yes}}"))
            assertThatThrownBy(
                            () ->
                                    declaration(
                                            "state: '${/input/state}'\nquestions: "
                                                    + questions
                                                    + "\n"))
                    .isInstanceOf(IllegalArgumentException.class);
        var singleton =
                declaration(
                        "state: {}\n"
                            + "questions: {choice: {type: choice, criteria: {' A ': null}}, score:"
                            + " {type: score, criteria: [null]}, noul: {type: noul, criteria:"
                            + " {'false': []}}}\n");
        assertThat(singleton.resolve(Map.of()).json().at("/questions/choice/criteria").has(" A "))
                .isTrue();
    }

    @Test
    void cardinalityBoundariesAndDynamicNoulUseTheSameValidator() {
        var yaml = LoomspanJacksonCodecs.defaults().schemaTree();
        for (int count : List.of(0, 1, 255, 256)) {
            var block = yaml.createObjectNode();
            block.put("state", "text");
            var question = yaml.createObjectNode();
            question.put("type", "choice");
            var criteria = yaml.createObjectNode();
            for (int n = 0; n < count; n++) criteria.putNull("c" + n);
            question.set("criteria", criteria);
            block.set("questions", yaml.createObjectNode().set("q", question));
            if (count == 0 || count == 256)
                assertThatThrownBy(() -> TypeSafeDeclaration.compile(block))
                        .hasMessageContaining("1 to 255");
            else
                assertThat(
                                TypeSafeDeclaration.compile(block)
                                        .resolve(Map.of())
                                        .json()
                                        .at("/questions/q/criteria")
                                        .size())
                        .isEqualTo(count);
        }
        for (int count : List.of(0, 1, 10, 11)) {
            var levels = new java.util.ArrayList<Object>();
            for (int n = 0; n < count; n++) levels.add(null);
            var d =
                    declaration(
                            "state: text\n"
                                + "questions: {q: {type: score, criteria: '${/input/levels}'}}\n");
            if (count == 0 || count == 11)
                assertThatThrownBy(() -> d.resolve(Map.of("levels", levels)))
                        .hasMessageContaining("1 to 10");
            else
                assertThat(
                                d.resolve(Map.of("levels", levels))
                                        .json()
                                        .at("/questions/q/criteria")
                                        .size())
                        .isEqualTo(count);
        }
        var d =
                declaration(
                        "state: text\n"
                            + "questions: {q: {type: noul, instructions: null, criteria:"
                            + " '${/input/criteria}'}}\n");
        assertThat(
                        d.resolve(Map.of("criteria", Map.of("true", List.of("yes"))))
                                .json()
                                .at("/questions/q/instructions")
                                .isNull())
                .isTrue();
        assertThatThrownBy(() -> d.resolve(Map.of("criteria", Map.of())))
                .hasMessageContaining("requires instructions");
    }

    @Test
    void nonJsonInputsAreRejectedBeforeSerialization() {
        var d =
                declaration(
                        "state: '${/input}'\nquestions: {q: {type: noul, instructions: decide}}\n");
        for (Object input :
                List.of(
                        Map.of("number", Double.NaN),
                        Map.of("number", Double.POSITIVE_INFINITY),
                        Map.of("value", new Object()),
                        Map.of(3, "value")))
            assertThatThrownBy(() -> d.resolve(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptiveSchemasStayFixedAcrossDynamicInvocationsAndDefensiveCopies() {
        var d =
                declaration(
                        """
state: ${/input}
questions:
  fixed: {type: choice, criteria: {One: null, Two: text}}
  dynamic: {type: choice, criteria: '${/input/candidates}'}
  score: {type: score, criteria: [null, {reason: text}]}
  dynamicScore: {type: score, criteria: '${/input/levels}'}
  noul: {type: noul, instructions: decide}
""");
        var schema = d.outputSchema();
        assertThat(schema.getRequired())
                .containsExactly("fixed", "dynamic", "score", "dynamicScore", "noul");
        assertThat(schema.getAdditionalProperties()).isFalse();
        assertThat(
                        schema.getProperties()
                                .get("fixed")
                                .getProperties()
                                .get("choice")
                                .getEnumValues())
                .containsExactly("One", "Two");
        assertThat(
                        schema.getProperties()
                                .get("fixed")
                                .getProperties()
                                .get("probabilities")
                                .getRequired())
                .containsExactly("One", "Two");
        assertThat(
                        schema.getProperties()
                                .get("dynamic")
                                .getProperties()
                                .get("probabilities")
                                .getAdditionalProperties())
                .isTrue();
        assertThat(
                        schema.getProperties()
                                .get("score")
                                .getProperties()
                                .get("probabilities")
                                .getRequired())
                .containsExactly("0", "1");
        assertThat(
                        schema.getProperties()
                                .get("score")
                                .getProperties()
                                .get("legend")
                                .getAdditionalProperties())
                .isTrue();
        d.resolve(Map.of("candidates", Map.of("X", "x"), "levels", List.of("low")));
        d.resolve(Map.of("candidates", Map.of("Y", "y"), "levels", List.of("low", "high")));
        schema.setRequired(List.of());
        assertThat(d.outputSchema().getRequired()).hasSize(5);
        assertThat(
                        d.outputSchema()
                                .getProperties()
                                .get("dynamic")
                                .getProperties()
                                .get("choice")
                                .getEnumValues())
                .isEmpty();
    }
}
