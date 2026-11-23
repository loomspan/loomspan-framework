package ai.loomspan.internal.runtime.trace;

import ai.loomspan.internal.core.TraceCompletion;
import ai.loomspan.internal.core.TraceOutcome;
import ai.loomspan.internal.core.TracePersistencePolicy;
import ai.loomspan.internal.core.TraceRecord;
import ai.loomspan.internal.core.TraceRecordType;
import ai.loomspan.internal.runtime.observation.ExecutionObservationHandle;
import ai.loomspan.internal.runtime.observation.ObservationCompletionDisposition;
import ai.loomspan.internal.runtime.usage.SessionUsageSnapshot;
import ai.loomspan.internal.release.LoomspanReleaseVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionTraceHandleTest {

    @TempDir
    Path tempDir;

    @Test
    void activeTraceDoesNotExposeCompletedFilename() throws Exception {
        var handle = namespaceTrace(tempDir, "finance", "active-session", TracePersistencePolicy.ALWAYS);
        Path completed = tempDir.resolve("finance").resolve("active-session." + handle.snapshot().traceId()
                + ".execution-trace.ndjson");
        handle.append(TraceRecordType.PLAN_CREATED, java.util.Map.of(), java.util.Map.of("planId", "active-plan"));
        assertThat(completed).doesNotExist();
        assertThat(handle.tracePath()).isEqualTo(completed.resolveSibling(completed.getFileName() + ".in-progress"));
        assertThat(handle.tracePath()).exists();
        assertThat(readRecords(handle)).hasSize(3);
        assertThat(new ExecutionJournalProjector().project(handle).getEntriesSnapshot()).singleElement()
                .satisfies(entry -> assertThat(entry.payload().path("planId").asText()).isEqualTo("active-plan"));
        assertThat(completed).doesNotExist();
    }

    @Test
    void publishesCompleteRetainedTraceOnce() throws Exception {
        for (TracePersistencePolicy policy : List.of(TracePersistencePolicy.ALWAYS, TracePersistencePolicy.ONERROR)) {
            for (TraceOutcome outcome : List.of(TraceOutcome.SUCCEEDED, TraceOutcome.FAILED)) {
                var observation = new RecordingObservationHandle();
                var handle = new DefaultExecutionTraceHandle(policy + "-" + outcome, "finance-entry",
                        namespaceMetadata("finance"), tempDir, policy, Clock.systemUTC(), observation);
                if (policy == TracePersistencePolicy.ONERROR || outcome == TraceOutcome.FAILED) handle.markErrored();
                Path active = handle.tracePath();
                Path destination = active.resolveSibling(active.getFileName().toString().replace(".in-progress", ""));
                observation.onTerminal = () -> {
                    assertThat(active).exists();
                    assertThat(destination).doesNotExist();
                };
                String payload = "\u00e9\ud83d\ude00".repeat(4000);
                handle.append(TraceRecordType.MODEL_RESPONSE_RECEIVED, java.util.Map.of(), payload);
                var before = readRecords(handle);
                var artifact = handle.finalizeTrace(completion(outcome)).orElseThrow();
                Path published = artifact.artifactPath();
                assertThat(active).doesNotExist();
                byte[] bytes = Files.readAllBytes(published);
                assertThat(artifact.sizeBytes()).isEqualTo(bytes.length);
                var after = readRecords(handle);
                assertThat(after.subList(0, before.size())).isEqualTo(before);
                assertThat(after.getLast().recordType()).isEqualTo(TraceRecordType.TRACE_COMPLETED);
                assertThat(after.stream().filter(r -> r.recordType() == TraceRecordType.TRACE_COMPLETED)).hasSize(1);
                assertThat(after.stream().filter(r -> r.recordType() == TraceRecordType.MODEL_RESPONSE_RECEIVED)
                        .findFirst().orElseThrow().data().asText()).isEqualTo(payload);
                assertThat(after.getFirst().metadata()).containsEntry("tracePath", active.toString());
                assertThat(handle.snapshot().filePath()).isEqualTo(published.toString());
                assertThat(handle.finalizeTrace(completion(outcome))).contains(artifact);
                assertThat(Files.readAllBytes(published)).isEqualTo(bytes);
            }
        }
    }

    @Test
    void failedRenameCachesFailureAndPreservesTerminalEvidence() throws Exception {
        var handle = namespaceTrace(tempDir, "finance", "failed-rename", TracePersistencePolicy.ALWAYS);
        Path active = handle.tracePath();
        Path destination = active.resolveSibling(active.getFileName().toString().replace(".in-progress", ""));
        Files.createDirectory(destination);
        Path marker = Files.writeString(destination.resolve("marker"), "administrator-owned");
        IOException failure = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED)));
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
                () -> handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED)))).isSameAs(failure);
        assertThat(active).exists();
        assertThat(Files.readString(marker)).isEqualTo("administrator-owned");
        assertThat(readRecords(handle).stream().filter(r -> r.recordType() == TraceRecordType.TRACE_COMPLETED)).hasSize(1);
    }

    @Test
    void immediateDiscardNeverPublishesCompletedName() throws Exception {
        for (TracePersistencePolicy policy : List.of(TracePersistencePolicy.NEVER, TracePersistencePolicy.ONERROR)) {
            for (TraceOutcome outcome : List.of(TraceOutcome.SUCCEEDED, TraceOutcome.FAILED)) {
                var handle = namespaceTrace(tempDir, "finance", "discard-" + policy + outcome, policy);
                Path active = handle.tracePath();
                Path destination = active.resolveSibling(active.getFileName().toString().replace(".in-progress", ""));
                assertThat(destination).doesNotExist();
                assertThat(handle.finalizeTrace(completion(outcome))).isEmpty();
                assertThat(handle.finalizeTrace(completion(outcome))).isEmpty();
                assertThat(active).doesNotExist();
                assertThat(destination).doesNotExist();
            }
        }
    }

    @Test
    void configuredTraceRootUsesCanonicalEntryNamespaceAndReportsActualPath() throws Exception {
        Path root = tempDir.resolve("host root with spaces");
        DefaultExecutionTraceHandle finance = namespaceTrace(root, "finance", "finance-session", TracePersistencePolicy.ALWAYS);
        assertThat(finance.tracePath().getParent()).isEqualTo(root.resolve("finance"));
        assertThat(finance.tracePath()).exists();
        assertThat(finance.tracePath().getFileName().toString())
                .isEqualTo("finance-session." + finance.snapshot().traceId() + ".execution-trace.ndjson.in-progress");
        assertThat(readRecords(finance).getFirst().metadata()).containsEntry("tracePath", finance.tracePath().toString());
        assertThat(finance.snapshot().filePath()).isEqualTo(finance.tracePath().toString());
        assertThat(finance.finalizeTrace(completion(TraceOutcome.SUCCEEDED)).orElseThrow().artifactPath())
                .isEqualTo(finance.tracePath().toAbsolutePath().normalize());
        DefaultExecutionTraceHandle shared = namespaceTrace(root, "shared", "shared-session", TracePersistencePolicy.ALWAYS);
        shared.finalizeTrace(completion(TraceOutcome.SUCCEEDED));
        assertThat(shared.tracePath().getParent()).isEqualTo(root.resolve("shared"));
        try (var children = Files.list(root)) {
            assertThat(children).containsExactlyInAnyOrder(root.resolve("finance"), root.resolve("shared"));
        }
    }

    @Test
    void writingIntoExistingNamespaceDirectoryPreservesPermissions() throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("finance"));
        var acl = Files.getFileAttributeView(directory, java.nio.file.attribute.AclFileAttributeView.class);
        var posix = Files.getFileAttributeView(directory, java.nio.file.attribute.PosixFileAttributeView.class);
        var originalAcl = acl == null ? null : acl.getAcl();
        var originalOwner = Files.getOwner(directory);
        var originalPosix = posix == null ? null : posix.readAttributes();
        try {
            if (posix != null) {
                posix.setPermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            }
            var expectedPermissions = posix == null ? null : posix.readAttributes().permissions();
            DefaultExecutionTraceHandle handle = namespaceTrace(tempDir, "finance", "permissions", TracePersistencePolicy.ALWAYS);
            handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED));
            assertThat(handle.tracePath()).exists();
            assertThat(Files.getOwner(directory)).isEqualTo(originalOwner);
            if (acl != null) {
                assertThat(acl.getAcl()).isEqualTo(originalAcl);
                System.out.println("Trace directory permission verification exercised ACL attributes");
            }
            if (posix != null) {
                assertThat(posix.readAttributes().permissions()).isEqualTo(expectedPermissions);
                assertThat(posix.readAttributes().group()).isEqualTo(originalPosix.group());
                System.out.println("Trace directory permission verification exercised POSIX attributes");
            }
            assertThat(acl != null || posix != null).as("filesystem supplies ACL or POSIX permission evidence").isTrue();
        } finally {
            if (posix != null) posix.setPermissions(originalPosix.permissions());
        }
    }

    @Test
    void unusableNamespaceDirectoryUsesExistingWriteFailureAndCreatesNoFallback() throws Exception {
        Files.writeString(tempDir.resolve("finance"), "administrator-owned blocker");
        String sessionId = "unusable-" + java.util.UUID.randomUUID();
        assertThatThrownBy(() -> namespaceTrace(tempDir, "finance", sessionId, TracePersistencePolicy.ALWAYS))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IOException.class);
        assertThat(Files.readString(tempDir.resolve("finance"))).isEqualTo("administrator-owned blocker");
        try (var files = Files.walk(tempDir)) {
            assertThat(files.filter(Files::isRegularFile)).containsExactly(tempDir.resolve("finance"));
        }
        for (Path candidate : List.of(Path.of(System.getProperty("java.io.tmpdir")),
                DefaultExecutionTraceHandle.defaultDirectory().resolve("finance"))) {
            if (Files.exists(candidate)) {
                try (var files = Files.list(candidate)) {
                    assertThat(files).noneMatch(path -> path.getFileName().toString().startsWith(sessionId + "."));
                }
            }
        }
    }

    @Test
    void namespacePathsFollowExistingPersistenceAndCompletionRetention() throws Exception {
        for (TracePersistencePolicy policy : TracePersistencePolicy.values()) {
            for (TraceOutcome outcome : List.of(TraceOutcome.SUCCEEDED, TraceOutcome.FAILED)) {
                var handle = namespaceTrace(tempDir, "finance", policy + "-" + outcome, policy);
                if (outcome == TraceOutcome.FAILED) handle.markErrored();
                handle.finalizeTrace(completion(outcome));
                assertThat(Files.exists(handle.tracePath())).isEqualTo(policy == TracePersistencePolicy.ALWAYS
                        || policy == TracePersistencePolicy.ONERROR && outcome == TraceOutcome.FAILED);
            }
        }
        var retainedPath = new java.util.concurrent.atomic.AtomicReference<Path>();
        CompletionGraceRetention retention = new CompletionGraceRetention() {
            public java.util.Optional<RetainedArtifact> retainOrDelete(Path path, Path completedPath, Instant finalizedAt, String traceId, String sessionId)
                    throws IOException {
                TraceFilePublication.publish(path, completedPath);
                retainedPath.set(completedPath);
                return java.util.Optional.of(new RetainedArtifact(finalizedAt.plusSeconds(60), Files.size(completedPath)));
            }
            public java.util.Optional<ArtifactLease> acquire(ai.loomspan.internal.core.FinalizedTraceArtifact artifact) {
                return java.util.Optional.empty();
            }
            public void close() {}
        };
        var handle = new DefaultExecutionTraceHandle("grace", "finance-entry", namespaceMetadata("finance"), tempDir,
                TracePersistencePolicy.NEVER, Clock.systemUTC(),
                ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle.INSTANCE, retention);
        var artifact = handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED)).orElseThrow();
        assertThat(retainedPath.get()).isEqualTo(tempDir.resolve("finance").resolve(handle.tracePath().getFileName()));
        assertThat(artifact.artifactPath()).isEqualTo(handle.tracePath().toAbsolutePath().normalize());
        assertThat(handle.tracePath()).exists();
    }

    private DefaultExecutionTraceHandle namespaceTrace(Path root, String namespace, String sessionId, TracePersistencePolicy policy) {
        return new DefaultExecutionTraceHandle(sessionId, namespace + "-entry", namespaceMetadata(namespace), root, policy,
                Clock.systemUTC(), ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle.INSTANCE);
    }

    private static ai.loomspan.internal.core.CapturedNamespaceMetadata namespaceMetadata(String namespace) {
        return new ai.loomspan.internal.core.CapturedNamespaceMetadata(namespace, "generation", java.util.Map.of(namespace, "generation"));
    }

    @Test
    void defaultTracePathUsesEntryNamespaceDirectory() throws Exception {
        String sessionId = "default-layout-" + java.util.UUID.randomUUID();
        DefaultExecutionTraceHandle handle = new DefaultExecutionTraceHandle(
                sessionId, "finance-entry",
                new ai.loomspan.internal.core.CapturedNamespaceMetadata("finance", "generation", java.util.Map.of("finance", "generation")),
                DefaultExecutionTraceHandle.defaultDirectory(), TracePersistencePolicy.ALWAYS, Clock.systemUTC(), ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle.INSTANCE);
        try {
            assertThat(handle.tracePath().getParent())
                    .isEqualTo(Path.of(System.getProperty("java.io.tmpdir"), "loomspan-traces", "finance"));
            assertThat(handle.tracePath().getFileName().toString())
                    .isEqualTo(sessionId + "." + handle.snapshot().traceId() + ".execution-trace.ndjson.in-progress");
            assertThat(handle.tracePath()).exists();
        } finally {
            Files.deleteIfExists(handle.tracePath());
        }
    }

    @Test
    void writesAuthoritativeConsoleCompatibilityVersionOnTraceStarted() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "marked-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);

        TraceRecord started = readRecords(handle).getFirst();

        assertThat(started.recordType()).isEqualTo(TraceRecordType.TRACE_STARTED);
        assertThat((String) started.metadata().get("consoleCompatibilityVersion"))
                .isEqualTo(LoomspanReleaseVersion.load())
                .isNotBlank();
        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void writesEntrySkillOnTraceStarted() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "entry-skill-trace", "incident.primary", TracePersistencePolicy.ALWAYS, clock);

        TraceRecord started = readRecords(handle).getFirst();

        assertThat(started.recordType()).isEqualTo(TraceRecordType.TRACE_STARTED);
        assertThat(started.metadata()).containsEntry("entrySkill", "incident.primary");
        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void appliesNeverOnErrorAndAlwaysPersistencePolicies() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);

        DefaultExecutionTraceHandle never = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("never-trace", "default-test_entry", TracePersistencePolicy.NEVER, clock);
        never.finalizeTrace(completion(TraceOutcome.SUCCEEDED));
        assertThat(Files.exists(never.tracePath())).isFalse();

        DefaultExecutionTraceHandle onError = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("onerror-trace", "default-test_entry", TracePersistencePolicy.ONERROR, clock);
        onError.markErrored();
        onError.append(TraceRecordType.ERROR_RECORDED, java.util.Map.of("kind", "runtime"), java.util.Map.of("message", "boom"));
        onError.finalizeTrace(completion(TraceOutcome.FAILED));
        assertThat(Files.exists(onError.tracePath())).isTrue();

        DefaultExecutionTraceHandle always = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("always-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);
        always.finalizeTrace(completion(TraceOutcome.SUCCEEDED));
        assertThat(Files.exists(always.tracePath())).isTrue();
    }

    @Test
    void honorsExplicitTimestampOverridesInTraceEnvelope() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("override-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);

        handle.append(
                TraceRecordType.MODEL_REQUEST_SENT,
                java.util.Map.of("timestampOverride", "2026-03-20T05:06:07Z"),
                java.util.Map.of("objective", "after"));

        List<TraceRecord> records = readRecords(handle);

        assertThat(records.getLast().recordType()).isEqualTo(TraceRecordType.MODEL_REQUEST_SENT);
        assertThat(records.getLast().timestamp()).isEqualTo(Instant.parse("2026-03-20T05:06:07Z"));
        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void usesSessionNamedTempFiles() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("shared-session", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);

        assertThat(handle.tracePath().getFileName().toString())
                .startsWith("shared-session.")
                .endsWith(".execution-trace.ndjson.in-progress");

        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void usesDistinctTraceFilesForRepeatedRunsOfTheSameSessionId() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle first = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("shared-session", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);
        DefaultExecutionTraceHandle second = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("shared-session", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);

        assertThat(first.tracePath()).isNotEqualTo(second.tracePath());
        assertThat(first.tracePath().getFileName().toString()).startsWith("shared-session.");
        assertThat(second.tracePath().getFileName().toString()).startsWith("shared-session.");

        Files.deleteIfExists(first.tracePath());
        Files.deleteIfExists(second.tracePath());
    }

    @Test
    void rejectsAppendsAfterTraceFinalization() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create("completed-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock);

        handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED));

        assertThatThrownBy(() -> handle.append(TraceRecordType.MODEL_REQUEST_SENT, java.util.Map.of(), java.util.Map.of("objective", "late")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already completed");

        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void constructsDescriptorFromSizeCapturedBeforeAsynchronousDeletion() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        CompletionGraceRetention deletionDuringRetention = new CompletionGraceRetention() {
            @Override
            public java.util.Optional<RetainedArtifact> retainOrDelete(
                    Path artifactPath,
                    Path completedPath,
                    Instant finalizedAt,
                    String traceId,
                    String sessionId) throws IOException {
                long sizeBytes = Files.size(artifactPath);
                TraceFilePublication.publish(artifactPath, completedPath);
                Files.delete(completedPath);
                return java.util.Optional.of(new RetainedArtifact(
                        finalizedAt.plusSeconds(1),
                        sizeBytes));
            }

            @Override
            public java.util.Optional<ArtifactLease> acquire(
                    ai.loomspan.internal.core.FinalizedTraceArtifact artifact)
            {
                return java.util.Optional.empty();
            }

            @Override
            public void close() {
            }
        };
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "short-grace-trace",
                "default-test_entry",
                TracePersistencePolicy.NEVER,
                clock,
                ai.loomspan.internal.runtime.observation.NoOpExecutionObservationHandle.INSTANCE,
                deletionDuringRetention);

        var artifact = handle.finalizeTrace(completion(TraceOutcome.SUCCEEDED)).orElseThrow();

        assertThat(artifact.sizeBytes()).isPositive();
        assertThat(handle.tracePath()).doesNotExist();
    }

    @Test
    void publishesTraceStartedAndCapturePolicyExactlyOnce() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        RecordingObservationHandle observation = new RecordingObservationHandle();
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "observed-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock, observation);

        assertThat(observation.records)
                .extracting(TraceRecord::recordType)
                .containsExactly(
                        TraceRecordType.TRACE_STARTED,
                        TraceRecordType.TRACE_CAPTURE_POLICY_RECORDED);
        assertThat(observation.records).extracting(TraceRecord::sequence).containsExactly(1L, 2L);
        assertThat(observation.records).extracting(TraceRecord::traceId).containsOnly(handle.snapshot().traceId());
        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void publishesCompleteLogicalChunkedRecordOnlyAfterAllChunksSucceed() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        RecordingObservationHandle observation = new RecordingObservationHandle();
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "chunked-trace", "default-test_entry", TracePersistencePolicy.ALWAYS, clock, observation);
        String payload = "x".repeat(4_097);

        TraceRecord persistedEnvelope = handle.append(
                TraceRecordType.MODEL_RESPONSE_RECEIVED,
                java.util.Map.of("attemptNumber", 1),
                payload);

        assertThat(persistedEnvelope.data()).isNull();
        assertThat(observation.records.getLast().recordType())
                .isEqualTo(TraceRecordType.MODEL_RESPONSE_RECEIVED);
        assertThat(observation.records.getLast().data().asText()).isEqualTo(payload);
        assertThat(observation.records)
                .noneMatch(record -> record.recordType() == TraceRecordType.PAYLOAD_CHUNK_APPENDED);

        List<TraceRecord> physical = readRecords(handle);
        assertThat(physical)
                .extracting(TraceRecord::recordType)
                .contains(TraceRecordType.PAYLOAD_CHUNK_APPENDED);
        Files.deleteIfExists(handle.tracePath());
    }

    @Test
    void doesNotPublishWhenEnvelopeWriteFails() {
        assertChunkWriteFailurePublishesNothing(3, 9_000);
    }

    @Test
    void doesNotPublishWhenMiddleChunkWriteFails() {
        assertChunkWriteFailurePublishesNothing(5, 9_000);
    }

    @Test
    void doesNotPublishWhenFinalChunkWriteFails() {
        assertChunkWriteFailurePublishesNothing(6, 9_000);
    }

    private void assertChunkWriteFailurePublishesNothing(int failingWrite, int payloadLength) {
        Clock clock = Clock.fixed(Instant.parse("2026-03-24T12:00:00Z"), ZoneOffset.UTC);
        RecordingObservationHandle observation = new RecordingObservationHandle();
        Path tracePath = tempDir.resolve("failed-" + failingWrite + ".ndjson");
        ControllableWriter writer = new ControllableWriter(tracePath.resolveSibling(tracePath.getFileName() + ".in-progress"), failingWrite);
        DefaultExecutionTraceHandle handle = ai.loomspan.internal.runtime.trace.TestExecutionTraceHandles.create(
                "trace-" + failingWrite,
                "session-" + failingWrite,
                "default-test_entry",
                tracePath,
                TracePersistencePolicy.ALWAYS,
                clock,
                () -> "payload",
                "thread",
                "trace.ndjson",
                observation,
                writer);

        assertThatThrownBy(() -> handle.append(
                TraceRecordType.MODEL_RESPONSE_RECEIVED,
                java.util.Map.of(),
                "x".repeat(payloadLength)))
                .isInstanceOf(IOException.class);
        assertThat(observation.records)
                .extracting(TraceRecord::recordType)
                .containsExactly(
                        TraceRecordType.TRACE_STARTED,
                        TraceRecordType.TRACE_CAPTURE_POLICY_RECORDED);
    }

    private static final class ControllableWriter implements TraceRecordWriter {
        private final NdjsonTraceRecordWriter delegate;
        private final int failingWrite;
        private int writes;

        private ControllableWriter(Path tracePath, int failingWrite) {
            this.delegate = new NdjsonTraceRecordWriter(tracePath);
            this.failingWrite = failingWrite;
        }

        @Override
        public void append(TraceRecord record) throws IOException {
            writes++;
            if (writes == failingWrite) {
                throw new IOException("selected write failure");
            }
            delegate.append(record);
        }
    }

    private static final class RecordingObservationHandle implements ExecutionObservationHandle {
        private final List<TraceRecord> records = new ArrayList<>();
        private Runnable onTerminal = () -> {};

        @Override
        public void recordAppended(TraceRecord record) {
            records.add(record);
            if (record.recordType() == TraceRecordType.TRACE_COMPLETED) onTerminal.run();
        }

        @Override
        public void close(ObservationCompletionDisposition disposition) {
        }
    }

    private static List<TraceRecord> readRecords(DefaultExecutionTraceHandle handle) throws Exception {
        List<TraceRecord> records = new ArrayList<>();
        handle.readRecords(records::add);
        return records;
    }

    private static TraceCompletion completion(TraceOutcome outcome) {
        return new TraceCompletion(
                outcome,
                SessionUsageSnapshot.empty(),
                outcome == TraceOutcome.SUCCEEDED ? null : "failure",
                java.util.Map.of("status", outcome == TraceOutcome.SUCCEEDED ? "ok" : "error"));
    }
}
