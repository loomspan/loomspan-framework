package ai.loomspan.internal.serialization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredYamlVariablesTest {
    @Test void discoversAuthoredHeaderKeyAndPairedValueBeforeMapConstruction() {
        var document = AuthoredYamlVariables.execution("loomspan:\n  connections:\n    primary:\n      headers:\n        ? { $var: header.name }\n        : { $var: app.region }\n", "loomspan/execution.yaml");
        assertEquals(List.of(
                new AuthoredYamlVariables.Occurrence("header.name", "loomspan/execution.yaml", "loomspan.connections.primary.headers{key#0}"),
                new AuthoredYamlVariables.Occurrence("app.region", "loomspan/execution.yaml", "loomspan.connections.primary.headers{value#0}")), document.discover());
    }

    private AuthoredYamlVariables.Document config(String yaml) {
        return AuthoredYamlVariables.skill("executor: http\nconfig:\n" + yaml.indent(2), "skill.yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "null", "[]", "{}", "''", "' '", "' padded'", "'padded '", "'\u00a0'", "'\u2003'", "'\u00a0name'", "\"name\\n\""})
    void rejectsInvalidNamesBeforeAnyLookup(String name) {
        var document = config("first: { $var: good }\nvalue: { $var: " + name + " }");
        var calls = new AtomicInteger();
        var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class,
                () -> document.substitute(ignored -> { calls.incrementAndGet(); return "value"; }));
        assertEquals(List.of(new AuthoredYamlVariables.Location("skill.yaml", "config.value")), failure.locations());
        assertEquals(0, calls.get());
    }

    @Test void grammarUsesExactOpaqueNamesAndWalksOrdinaryMultiKeyMaps() {
        var document = config("""
                block:
                  $var: app.region
                flow: { $var: app.region }
                number: { $var: "123" }
                opaque: { $var: "name:default x" }
                upper: { $var: APP.REGION }
                ordinary:
                  $var: literal
                  nested: { $var: child }
                case: { $VAR: literal }
                text: '{ $var: text } ${region}'
                """);
        assertEquals(List.of("app.region", "app.region", "123", "name:default x", "APP.REGION", "child"),
                document.discover().stream().map(AuthoredYamlVariables.Occurrence::name).toList());
        var calls = new ArrayList<String>();
        Map<?, ?> result = (Map<?, ?>)((Map<?, ?>)document.substitute(name -> { calls.add(name); return name; })).get("config");
        assertEquals(document.discover().stream().map(AuthoredYamlVariables.Occurrence::name).toList(), calls);
        assertEquals(Map.of("$var", "literal", "nested", "child"), result.get("ordinary"));
        assertEquals("{ $var: text } ${region}", result.get("text"));
    }

    @Test void selectsOnlyActualExecutionValuesAndRetainsAuthoredOrder() {
        var document = AuthoredYamlVariables.execution("""
                input: { $var: excluded }
                loomspan:
                  models: { $var: excluded }
                  session-policy: { $var: excluded }
                  executor-config:
                    ? { $var: selector }
                    :
                      prompt: { $var: first }
                  connections:
                    primary:
                      unknown: [{ $var: second }]
                    ? { $var: selector }
                    : { $var: third }
                """, "execution.yaml");
        assertEquals(List.of("first", "second", "third"), document.discover().stream().map(AuthoredYamlVariables.Occurrence::name).toList());
        var calls = new ArrayList<String>();
        document.substitute(name -> { calls.add(name); return "resolved"; });
        assertEquals(List.of("first", "second", "third"), calls);
    }

    @Test void skillSelectionRequiresExecutorAndActualTopLevelConfigMap() {
        for (String yaml : List.of("model: primary\nconfig: { x: { $var: excluded } }",
                "executor: http\nconfig: [{ $var: excluded }]",
                "executor: { $var: excluded }\nprompt: { $var: excluded }\nroles: [{ $var: excluded }]\nschema: { config: { $var: excluded } }\ninput: { $var: excluded }\noutput: { $var: excluded }")) {
            var document = AuthoredYamlVariables.skill(yaml, "skill.yaml");
            assertEquals(List.of(), document.discover());
            document.substitute(name -> fail("unexpected lookup " + name));
        }
        assertEquals(List.of("root"), AuthoredYamlVariables.skill("executor: http\nconfig: { $var: root }", "s").discover()
                .stream().map(AuthoredYamlVariables.Occurrence::name).toList());
    }

    @Test void pathsUseAuthoredIndicesAndJsonEscapes() {
        var document = config("""
                api-key: { $var: a }
                "X.Region": [{ $var: b }]
                "": { $var: c }
                "123": { $var: d }
                "é": { $var: e }
                "a\\\"b\\\\c\\t\\n": { $var: f }
                /: { $var: g }
                ? { inner: [{ $var: key }] }
                : { $var: value }
                """);
        assertEquals(List.of("config.api-key", "config[\"X.Region\"][0]", "config[\"\"]", "config[\"123\"]", "config[\"é\"]",
                        "config[\"a\\\"b\\\\c\\t\\n\"]", "config[\"/\"]", "config{key#7}.inner[0]", "config{value#7}"),
                document.discover().stream().map(AuthoredYamlVariables.Occurrence::path).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"  nonblank \n", "{ $var: other }", "${region}", "[1, true]", "a: b\n---\n", "x ! : # & *"})
    void substitutionsAreExactTerminalStrings(String supplied) {
        var document = config("value: { $var: first }");
        var calls = new AtomicInteger();
        var resolved = (Map<?, ?>)document.substitute(name -> { calls.incrementAndGet(); return supplied; });
        assertEquals(supplied, ((Map<?, ?>)resolved.get("config")).get("value"));
        assertEquals(1, calls.get());
    }

    @Test void retainsOrdinaryTypesAndMakesIndependentOutputs() {
        String yaml = "integer: 3\nlarge: 123456789012345678901234567890\ndecimal: 1.25\nnonfinite: .nan\nboolean: true\nnull: null\ntimestamp: !!timestamp 2026-11-13\nquoted: '123'\nmap: {}\nlist: []\nset: !!set { a: null }";
        var document = config(yaml);
        Object expected = YamlValues.read(yaml);
        var first = (Map<?, ?>)document.substitute(name -> fail());
        var second = (Map<?, ?>)document.substitute(name -> fail());
        assertEquals(expected, first.get("config"));
        assertEquals(first, second);
        assertNotSame(first.get("config"), second.get("config"));
        var variables = config("value: { $var: name }");
        assertEquals("first", ((Map<?, ?>)((Map<?, ?>)variables.substitute(name -> "first")).get("config")).get("value"));
        assertEquals("second", ((Map<?, ?>)((Map<?, ?>)variables.substitute(name -> "second")).get("config")).get("value"));
        assertEquals("name", variables.discover().getFirst().name());
    }

    @Test void rejectsMissingBlankAndThrowingLookupWithoutSuppliedContents() {
        for (String supplied : new String[] { null, "", " \n\t", "\u00a0" }) {
            var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class,
                    () -> config("value: { $var: name }").substitute(name -> supplied));
            assertEquals(List.of(new AuthoredYamlVariables.Location("skill.yaml", "config.value")), failure.locations());
            assertEquals(supplied == null ? "missing-value" : "blank-value", failure.reason());
        }
        var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class,
                () -> config("value: { $var: name }").substitute(name -> { throw new IllegalStateException("SUPPLIED_SENTINEL"); }));
        assertEquals("lookup-failed", failure.reason());
        assertFalse(failure.toString().contains("SUPPLIED_SENTINEL"));
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test void reportsBothOriginalLocationsForAllCollisionOrders() {
        for (String yaml : List.of("? { $var: name }\n: value\nX-Region: literal",
                "X-Region: literal\n? { $var: name }\n: value",
                "? { $var: a }\n: value\n? { $var: b }\n: other",
                "? { $var: a }\n: value\n? { $var: a }\n: other")) {
            var document = config(yaml);
            var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class, () -> document.substitute(name -> "X-Region"));
            assertEquals("key-collision", failure.reason());
            assertEquals(2, failure.locations().size());
            assertTrue(failure.locations().stream().anyMatch(location -> location.path().contains("{key#")));
        }
        var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class,
                () -> config("? [{ $var: a }]\n: value\n? [literal]\n: other").substitute(name -> "literal"));
        assertEquals(List.of(new AuthoredYamlVariables.Location("skill.yaml", "config{key#0}"),
                new AuthoredYamlVariables.Location("skill.yaml", "config{key#1}")), failure.locations());
        var duplicate = config("a: 1\na: 2");
        assertThrows(AuthoredYamlVariables.InvalidVariablesException.class, () -> duplicate.substitute(name -> fail()));
        assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> YamlValues.read("a: 1\na: 2"));
    }

    @Test void collisionDiagnosticsOmitResolvedStrings() {
        var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class,
                () -> config("? { $var: a }\n: x\n? { $var: b }\n: y").substitute(name -> "SUPPLIED_SENTINEL\n"));
        assertFalse(failure.toString().contains("SUPPLIED_SENTINEL"));
        assertNull(failure.getCause());
    }

    @Test void malformedMarkersRetainKeyValueAndSequenceLocations() {
        var examples = Map.of(
                "items: [{ $var: 3 }]", "config.items[0]",
                "? { $var: 3 }\n: literal", "config{key#0}",
                "? { $var: header }\n: { $var: 3 }", "config{value#0}");
        examples.forEach((yaml, path) -> {
            var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class, () -> config(yaml).discover());
            assertEquals(List.of(new AuthoredYamlVariables.Location("skill.yaml", path)), failure.locations());
        });
    }

    @Test void aliasesInsideKeysRetainAuthoredPathsAndFiles() {
        var document = config("template: &marker { $var: name }\n? [*marker]\n: *marker");
        assertEquals(List.of("config.template", "config{key#1}[0]", "config{value#1}"),
                document.discover().stream().map(AuthoredYamlVariables.Occurrence::path).toList());
        var other = AuthoredYamlVariables.skill("executor: http\nconfig: { template: { $var: name } }", "other.yaml");
        assertNotEquals(document.discover().getFirst(), other.discover().getFirst());
        assertEquals(3, document.discover().size());
    }

    @Test void aliasOccurrencesUseEachAuthoredPathAndCannotEscapeLimits() {
        var document = config("a: &a [{ $var: name }]\nb: *a");
        assertEquals(List.of("config.a[0]", "config.b[0]"), document.discover().stream().map(AuthoredYamlVariables.Occurrence::path).toList());
        assertEquals(Map.of("a", List.of("v"), "b", List.of("v")), ((Map<?, ?>)document.substitute(name -> "v")).get("config"));
        for (String yaml : List.of("a: &a [*a]", "a: &a { x: *a }"))
            assertTrue(assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> config(yaml)).getMessage().contains("recursive"));
        StringBuilder bomb = new StringBuilder("a0: &a0 [x]\n");
        for (int i = 1; i < 20; i++) bomb.append("a").append(i).append(": &a").append(i).append(" [*a").append(i - 1).append(", *a").append(i - 1).append("]\n");
        assertTrue(assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class, () -> config(bomb.toString())).getMessage().contains("expanded value count exceeds limit 1000000"));
    }

    @Test void streamFactoriesRetainBomDecodingAndRejectMultipleDocuments() {
        String yaml = "executor: http\nconfig: { value: { $var: name } }";
        byte[] bytes = yaml.getBytes(StandardCharsets.UTF_16);
        assertEquals("name", AuthoredYamlVariables.skill(new ByteArrayInputStream(bytes), "utf16.yaml").discover().getFirst().name());
        String execution = "loomspan: { connections: { primary: { $var: name } } }";
        assertEquals("name", AuthoredYamlVariables.execution(new ByteArrayInputStream(execution.getBytes(StandardCharsets.UTF_8)), "e").discover().getFirst().name());
        assertThrows(org.snakeyaml.engine.v2.exceptions.YamlEngineException.class,
                () -> AuthoredYamlVariables.execution("a: 1\n---\nb: 2", "e"));
    }
}
