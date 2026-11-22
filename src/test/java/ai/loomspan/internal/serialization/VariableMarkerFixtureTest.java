package ai.loomspan.internal.serialization;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VariableMarkerFixtureTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @TestFactory Stream<DynamicTest> allPortableCases() throws IOException {
        try (var cases = Files.list(Path.of("loomspan-variable-fixtures"))) {
            List<Path> directories = cases.filter(Files::isDirectory).sorted().toList();
            assertFalse(directories.isEmpty());
            return directories.stream().map(directory -> DynamicTest.dynamicTest(directory.getFileName().toString(),
                    () -> verify(directory)));
        }
    }

    private void verify(Path directory) throws IOException {
        JsonNode expected = mapper.readTree(Files.readString(directory.resolve("expected.json")));
        var documents = new ArrayList<AuthoredYamlVariables.Document>();
        for (JsonNode document : expected.get("documents")) {
            String source = document.get("source").asString();
            String yaml = Files.readString(directory.resolve(source));
            String kind = document.get("kind").asString();
            assertTrue(kind.equals("execution") || kind.equals("skill"));
            documents.add(kind.equals("execution") ? AuthoredYamlVariables.execution(yaml, source) : AuthoredYamlVariables.skill(yaml, source));
        }
        JsonNode failure = expected.get("failure");
        if (failure != null && failure.get("phase").asString().equals("discovery")) {
            verifyFailure(failure, () -> documents.forEach(AuthoredYamlVariables.Document::discover));
            return;
        }
        var occurrences = new ArrayList<AuthoredYamlVariables.Occurrence>();
        documents.forEach(document -> occurrences.addAll(document.discover()));
        assertEquals(expected.get("occurrences"), mapper.valueToTree(occurrences));
        var names = new LinkedHashSet<String>();
        occurrences.forEach(occurrence -> names.add(occurrence.name()));
        assertEquals(expected.get("names"), mapper.valueToTree(names));
        JsonNode values = expected.get("values");
        java.util.function.Function<String, String> lookup = name -> {
            assertTrue(values.has(name), "unexpected lookup " + name);
            return values.get(name).asString();
        };
        if (failure != null) {
            assertEquals("substitution", failure.get("phase").asString());
            verifyFailure(failure, () -> documents.forEach(document -> document.substitute(lookup)));
            return;
        }
        var resolved = new ArrayList<Object>();
        documents.forEach(document -> resolved.add(document.substitute(lookup)));
        if (expected.has("resolved")) assertEquals(expected.get("resolved"), mapper.valueToTree(resolved));
        if (directory.getFileName().toString().equals("worked")) {
            assertEquals(4, names.size());
            assertEquals(6, occurrences.size());
        }
    }

    private void verifyFailure(JsonNode expected, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(AuthoredYamlVariables.InvalidVariablesException.class, operation);
        assertEquals(expected.get("reason").asString(), failure.reason());
        assertEquals(expected.get("locations"), mapper.valueToTree(failure.locations()));
    }
}
