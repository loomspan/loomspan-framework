package ai.loomspan.internal.observability.web;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import ai.loomspan.internal.core.TraceFrameType;
import ai.loomspan.internal.runtime.observation.ActiveExecutionSnapshot;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.observability.web.dto.ObservabilityDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleRestFixtureCorpusTest
{
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();
    private static final Instant OBSERVED = Instant.parse("2026-07-25T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedCorpusMatchesCommittedFixturesByteForByte() throws Exception
    {
        Path generated = temporaryDirectory.resolve("application-rest");
        Files.createDirectories(generated);
        for (Map.Entry<String, Object> fixture : fixtures().entrySet())
        {
            Path target = generated.resolve(fixture.getKey());
            Files.createDirectories(target.getParent());
            Files.writeString(target,
                    JSON.writeValueAsString(fixture.getValue()) + "\n");
        }
        Path committed = fixtureRoot().resolve("application-rest");
        if (Boolean.getBoolean("loomspan.console.fixtures.regenerate"))
        {
            Files.createDirectories(committed);
            for (Path source : Files.walk(generated).filter(Files::isRegularFile).toList())
            {
                Path destination = committed.resolve(generated.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        List<String> names = Files.walk(generated).filter(Files::isRegularFile)
                .map(path -> generated.relativize(path).toString().replace('\\', '/')).sorted().toList();
        assertThat(Files.walk(committed).filter(Files::isRegularFile)
                .map(path -> committed.relativize(path).toString().replace('\\', '/')).sorted().toList())
                .containsExactlyElementsOf(names);
        for (String name : names)
        {
            assertThat(Files.readAllBytes(committed.resolve(name)))
                    .as(name)
                    .isEqualTo(Files.readAllBytes(generated.resolve(name)));
        }
    }

    @Test
    void sharedActiveExecutionCorpusDefinesCanonicalValidAndInvalidShapes() throws Exception
    {
        Path root = fixtureRoot().resolve("application-rest/active-executions");
        for (Path fixture : Files.list(root.resolve("valid")).sorted().toList())
        {
            assertThatCode(() -> validateSharedActiveExecution(fixture)).as(fixture.getFileName().toString())
                    .doesNotThrowAnyException();
        }
        for (Path fixture : Files.list(root.resolve("invalid")).sorted().toList())
        {
            assertThatThrownBy(() -> validateSharedActiveExecution(fixture)).as(fixture.getFileName().toString())
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static void validateSharedActiveExecution(Path fixture) throws Exception
    {
        ObjectNode execution = (ObjectNode) JSON.readTree(Files.readAllBytes(fixture));
        ArrayNode rawBranches = (ArrayNode) execution.get("activeBranches");
        if (rawBranches == null) throw new IllegalArgumentException("activeBranches is required");
        List<ActiveExecutionSnapshot.ActiveBranch> branches = new java.util.ArrayList<>();
        for (int branchIndex = 0; branchIndex < rawBranches.size(); branchIndex++)
        {
            ObjectNode rawBranch = (ObjectNode) rawBranches.get(branchIndex);
            for (String field : List.of("planId", "taskId", "stepNumber", "parallelGroup", "effectiveConcurrency"))
            {
                if (!rawBranch.has(field)) throw new IllegalArgumentException(field + " is required");
            }
            ArrayNode rawPath = (ArrayNode) rawBranch.get("path");
            List<ActiveExecutionSnapshot.FramePathEntry> path = new java.util.ArrayList<>();
            if (rawPath != null)
            {
                for (int pathIndex = 0; pathIndex < rawPath.size(); pathIndex++)
                {
                    ObjectNode rawEntry = (ObjectNode) rawPath.get(pathIndex);
                    if (rawEntry.size() != 3) throw new IllegalArgumentException("path entry shape is not canonical");
                    path.add(new ActiveExecutionSnapshot.FramePathEntry(
                            rawEntry.get("frameId").textValue(),
                            TraceFrameType.valueOf(rawEntry.get("frameType").textValue()),
                            rawEntry.get("route").textValue()));
                }
            }
            branches.add(new ActiveExecutionSnapshot.ActiveBranch(
                    nullableText(rawBranch, "planId"), nullableText(rawBranch, "taskId"),
                    rawBranch.get("stepNumber").isNull() ? null : rawBranch.get("stepNumber").intValue(),
                    nullableText(rawBranch, "parallelGroup"),
                    rawBranch.get("effectiveConcurrency").isNull() ? null : rawBranch.get("effectiveConcurrency").booleanValue(),
                    path));
        }
        new ActiveExecutionSnapshot("session-live", "trace-live", 1, 7,
                Instant.parse("2026-07-25T11:59:55Z"), Instant.parse("2026-07-25T11:59:59Z"),
                "planner", "RUNNING", "Executing", branches, SessionUsageSnapshot.empty(), null);
    }

    private static String nullableText(ObjectNode node, String field)
    {
        return node.get(field).isNull() ? null : node.get(field).textValue();
    }

    private static Map<String, Object> fixtures()
            throws Exception
    {
        Map<String, Object> result = new LinkedHashMap<>();
        var skill = new ObservabilityDtos.SkillSummary(
                "CheckDns", "YAML", "classpath:/skills/check-dns.yaml", null, null, "skills/CheckDns");
        var usage = new ObservabilityDtos.Usage(1, 0, 0, 1, 2, 10, 5, 15, 1, 0, 0);
        var limits = new ObservabilityDtos.QuotaLimits(64, 128, 32, 64, 192, 200000);
        var active = new ObservabilityDtos.ActiveExecution(
                "session-1", "trace-1", 7, Instant.parse("2026-07-25T11:59:55Z"),
                Instant.parse("2026-07-25T11:59:59Z"), 5000, "CheckDns", "ACTIVE",
                "RUNNING", "Checking DNS", List.of(), usage, limits);
        var trace = new ObservabilityDtos.Trace(
                "trace-1", "session-1", "CheckDns", TraceOutcome.SUCCEEDED, OBSERVED, 128,
                TracePersistencePolicy.ONERROR, Instant.parse("2026-07-25T12:15:00Z"));

        result.put("instance-status.json", new ObservabilityDtos.InstanceStatus(
                "11111111-1111-4111-8111-111111111111", "1.0.0-beta.2", OBSERVED, true,
                1, 1, 1, TracePersistencePolicy.ONERROR, Duration.ofMinutes(15), Duration.ofHours(24)));
        result.put("skills-page.json", new ObservabilityDtos.Page<>(List.of(skill, new ObservabilityDtos.SkillSummary(
                "LookupDns", "JAVA", null, "dnsSkills", "example.DnsSkills.lookup(java.lang.String)", "skills/LookupDns")), false, null, OBSERVED));
        result.put("skill-detail.json", new ObservabilityDtos.SkillDetail(
                "CheckDns", "YAML", "classpath:/skills/check-dns.yaml", null, null, "# DNS check\r\nname: CheckDns\r\n"));
        result.put("skill-java-detail.json", new ObservabilityDtos.SkillDetail(
                "LookupDns", "JAVA", null, "dnsSkills", "example.DnsSkills.lookup(java.lang.String)", null));
        result.put("active-executions-page.json",
                new ObservabilityDtos.ActivePage(List.of(active), false, null, OBSERVED, "9"));
        result.put("active-execution-detail.json", active);
        result.put("traces-page.json", new ObservabilityDtos.Page<>(List.of(trace), false, null, OBSERVED));
        result.put("trace-detail.json", trace);
        result.put("empty-page.json", new ObservabilityDtos.Page<>(List.of(), false, null, OBSERVED));
        result.put("continuation-page.json", new ObservabilityDtos.Page<>(
                List.of(skill), true, "eyJ2ZXJzaW9uIjoxfQ", OBSERVED.plusSeconds(1)));
        problems(result);
        activeExecutionContracts(result, usage, limits);
        return result;
    }

    private static void activeExecutionContracts(Map<String, Object> result,
            ObservabilityDtos.Usage usage, ObservabilityDtos.QuotaLimits limits) throws Exception
    {
        var root = new ObservabilityDtos.FramePathEntry("root", TraceFrameType.ROOT_MISSION, "planner");
        var stepA = new ObservabilityDtos.FramePathEntry("step-a", TraceFrameType.STEP_EXECUTION, "planner#step-1");
        var stepB = new ObservabilityDtos.FramePathEntry("step-b", TraceFrameType.STEP_EXECUTION, "planner#step-2");
        var nested = new ObservabilityDtos.FramePathEntry("nested", TraceFrameType.MODEL_CALL, "nested-model");
        var serial = new ObservabilityDtos.ActiveBranch("plan-1", "task-a", 1, null, false, List.of(root, stepA));
        var disabled = new ObservabilityDtos.ActiveBranch("plan-1", "task-a", 1, "batch", false, List.of(root, stepA));
        var concurrentA = new ObservabilityDtos.ActiveBranch("plan-1", "task-a", 1, "batch", true, List.of(root, stepA));
        var concurrentB = new ObservabilityDtos.ActiveBranch("plan-1", "task-b", 2, "batch", true, List.of(root, stepB));
        var inherited = new ObservabilityDtos.ActiveBranch("plan-1", "task-a", 1, "batch", true, List.of(root, stepA, nested));

        result.put("active-executions/valid/empty.json", live(List.of(), usage, limits));
        result.put("active-executions/valid/serial-assigned.json", live(List.of(serial), usage, limits));
        result.put("active-executions/valid/grouped-disabled.json", live(List.of(disabled), usage, limits));
        result.put("active-executions/valid/concurrent-siblings.json", live(List.of(concurrentA, concurrentB), usage, limits));
        result.put("active-executions/valid/nested-inheritance.json", live(List.of(inherited), usage, limits));

        ObjectNode base = (ObjectNode) JSON.valueToTree(live(List.of(concurrentA, concurrentB), usage, limits));
        result.put("active-executions/invalid/empty-path.json", mutate(base, node -> branch(node, 0).set("path", JSON.createArrayNode())));
        result.put("active-executions/invalid/duplicate-leaf.json", mutate(base, node ->
                branch(node, 1).set("path", branch(node, 0).get("path").deepCopy())));
        result.put("active-executions/invalid/non-root-first-entry.json", mutate(base, node ->
                pathEntry(node, 0, 0).put("frameType", "STEP_EXECUTION")));
        result.put("active-executions/invalid/repeated-frame-id.json", mutate(base, node ->
                pathEntry(node, 0, 1).put("frameId", "root")));
        result.put("active-executions/invalid/conflicting-shared-prefix.json", mutate(base, node ->
        {
            pathEntry(node, 1, 1).put("frameId", "step-a");
            pathEntry(node, 1, 1).put("route", "conflicting-route");
        }));
        result.put("active-executions/invalid/missing-assignment-member.json", mutate(base, node ->
                branch(node, 0).remove("planId")));
        result.put("active-executions/invalid/concurrent-without-group.json", mutate(base, node ->
                branch(node, 0).putNull("parallelGroup")));
        result.put("active-executions/invalid/null-task-metadata-leak.json", mutate(base, node ->
                branch(node, 0).putNull("taskId")));
        result.put("active-executions/invalid/multiple-roots.json", mutate(base, node ->
        {
            pathEntry(node, 1, 0).put("frameId", "other-root");
            pathEntry(node, 1, 0).put("route", "other");
        }));
    }

    private static ObservabilityDtos.ActiveExecution live(List<ObservabilityDtos.ActiveBranch> branches,
            ObservabilityDtos.Usage usage, ObservabilityDtos.QuotaLimits limits)
    {
        return new ObservabilityDtos.ActiveExecution("session-live", "trace-live", 7,
                Instant.parse("2026-07-25T11:59:55Z"), Instant.parse("2026-07-25T11:59:59Z"),
                5000, "planner", "ACTIVE", "RUNNING", "Executing", branches, usage, limits);
    }

    private static ObjectNode mutate(ObjectNode base, java.util.function.Consumer<ObjectNode> mutation)
    {
        ObjectNode copy = base.deepCopy();
        mutation.accept(copy);
        return copy;
    }

    private static ObjectNode branch(ObjectNode root, int index)
    {
        return (ObjectNode) ((ArrayNode) root.get("activeBranches")).get(index);
    }

    private static ObjectNode pathEntry(ObjectNode root, int branch, int index)
    {
        return (ObjectNode) ((ArrayNode) branch(root, branch).get("path")).get(index);
    }

    private static void problems(Map<String, Object> result)
    {
        result.put("problem-loomspan-api-key-rejected.json", new ObservabilityProblem(
                401, ObservabilityProblem.Code.LOOMSPAN_API_KEY_REJECTED, "loomspan API key was rejected"));
        result.put("problem-invalid-request.json", new ObservabilityProblem(
                400, ObservabilityProblem.Code.INVALID_REQUEST, "The request is invalid"));
        result.put("problem-invalid-cursor.json", new ObservabilityProblem(
                400, ObservabilityProblem.Code.INVALID_CURSOR, "The continuation is invalid"));
        result.put("problem-stale-cursor.json", new ObservabilityProblem(
                410, ObservabilityProblem.Code.STALE_CURSOR,
                "The continuation belongs to another application instance"));
        result.put("problem-not-found.json", new ObservabilityProblem(
                404, ObservabilityProblem.Code.NOT_FOUND, "The requested observability resource was not found"));
        result.put("problem-live-monitoring-unavailable.json", new ObservabilityProblem(
                503, ObservabilityProblem.Code.LIVE_MONITORING_UNAVAILABLE,
                "Live execution monitoring is unavailable"));
        result.put("problem-limit-exceeded.json", new ObservabilityProblem(
                429, ObservabilityProblem.Code.LIMIT_EXCEEDED,
                "The observability response exceeds the configured limit"));
        result.put("problem-application-error.json", new ObservabilityProblem(
                500, ObservabilityProblem.Code.APPLICATION_ERROR,
                "The observability request could not be completed"));
    }

    private static Path fixtureRoot()
    {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path direct = cwd.resolve("loomspan-console-fixtures");
        return Files.isDirectory(direct) ? direct : cwd.getParent().resolve("loomspan-console-fixtures");
    }
}
