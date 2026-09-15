package ai.loomspan.internal.observability.web;

import ai.loomspan.internal.observability.web.dto.ObservabilityDtos;
import ai.loomspan.internal.runtime.observation.ExecutionActivityKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleSseFixtureCorpusTest
{
    private static final String INSTANCE = "11111111-1111-4111-8111-111111111111";
    private static final Instant OBSERVED = Instant.parse("2026-07-25T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedSseCorpusMatchesCommittedFixturesByteForByte() throws Exception
    {
        ObservabilityJsonCodec json = new ObservabilityJsonCodec();
        Map<String, byte[]> fixtures = new LinkedHashMap<>();
        byte[] handshake = ObservabilityActivityStream.handshakeFrame(
                json, new ObservabilityDtos.ActivityHandshake(INSTANCE, OBSERVED, "0"));
        byte[] completed = ObservabilityActivityStream.activityFrame(
                json, activity("7", ExecutionActivityKind.TRACE_COMPLETED, "COMPLETED",
                        "Execution completed", Map.of("applicationTraceAvailability", "AVAILABLE")));
        byte[] failed = ObservabilityActivityStream.activityFrame(
                json, activity("8", ExecutionActivityKind.EXECUTION_OBSERVATION_ENDED, "COMPLETED",
                        "Trace finalization failed",
                        Map.of("applicationTraceAvailability", "CORE_FINALIZATION_FAILED")));
        fixtures.put("handshake.sse", handshake);
        fixtures.put("activity-trace-completed.sse", completed);
        fixtures.put("activity-core-finalization-failed.sse", failed);
        fixtures.put("replay.sse", concat(handshake, completed, failed));

        Path generated = temporaryDirectory.resolve("application-sse");
        Files.createDirectories(generated);
        fixtures.forEach((name, bytes) -> write(generated.resolve(name), bytes));
        compareOrRegenerate(generated, fixtureRoot().resolve("application-sse"));
    }

    private static ObservabilityDtos.ActivityEnvelope activity(
            String cursor,
            ExecutionActivityKind kind,
            String status,
            String summary,
            Map<String, Object> details)
    {
        return new ObservabilityDtos.ActivityEnvelope(
                INSTANCE, cursor, "session-1", "trace-1", Long.valueOf(cursor), OBSERVED,
                kind, status, null, null, null, null, summary, details);
    }

    private static byte[] concat(byte[]... values)
    {
        int length = java.util.Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values)
        {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static void write(Path path, byte[] bytes)
    {
        try
        {
            Files.write(path, bytes);
        }
        catch (java.io.IOException failure)
        {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    static void compareOrRegenerate(Path generated, Path committed) throws Exception
    {
        if (Boolean.getBoolean("loomspan.console.fixtures.regenerate"))
        {
            Files.createDirectories(committed);
            for (Path source : Files.list(generated).toList())
            {
                Files.copy(source, committed.resolve(source.getFileName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        List<String> names = Files.list(generated).map(path -> path.getFileName().toString()).sorted().toList();
        assertThat(Files.list(committed).map(path -> path.getFileName().toString()).sorted().toList())
                .containsExactlyElementsOf(names);
        for (String name : names)
        {
            assertThat(Files.readAllBytes(committed.resolve(name)))
                    .as(name)
                    .isEqualTo(Files.readAllBytes(generated.resolve(name)));
        }
    }

    static Path fixtureRoot()
    {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = cwd.resolve("loomspan-console-fixtures");
        return Files.isDirectory(direct) ? direct : cwd.getParent().resolve("loomspan-console-fixtures");
    }
}
