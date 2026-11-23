---
date: 2026-11-22T19:00:52-08:00
researcher: Codex
model: GPT-6.1-sol
git_commit: 03e7be1822102aed81f9187e797a1c9e285c89af
branch: main
repository: loomspan-framework
topic: "PR 36 — Write execution traces under the entry skill's namespace"
tags: [research, codebase, execution-traces, namespaces, configuration]
status: complete
last_updated: 2026-11-22
last_updated_by: Codex
---

# Research — Trace directory ownership

## Research question

Map the existing host configuration, namespace capture, trace construction, filesystem writing, metadata, retention and verification paths for `ai/thoughts/tickets/loomspan-pr-36-write-traces-by-entry-namespace.md`.

The selected execution profile is Full 5-Step Pipeline. The developer approved this profile after Step 0. This is pipeline Step 1. At research start, `main` matched `origin/main` and the supplied ticket was the only untracked file. No production changes or tests were made during research.

## Summary

The current runtime chooses a flat path in `java.io.tmpdir`. `DefaultExecutionTraceHandle` owns the UUID trace ID, filename, actual path, trace-start path metadata, writer and reader. Its constructors already receive `CapturedNamespaceMetadata`, whose entry namespace is canonical and validated against the captured entry-generation map. `LoomspanSessionRunner` constructs one session and handle per admitted root, including a distinct construction branch for generation-captured execution settings. Nested namespace calls retain that session and trace.

Host trace configuration currently contains only persistence. Published execution configuration uses a strict allowlist that admits only `execution-trace.persistence`. The writer creates the selected path's parent directories and appends NDJSON. It does not set filesystem permissions or select a fallback path. Finalization and completion-grace retention carry the exact artifact path throughout their lifecycle.

## Detailed findings

### Host configuration and wiring

- `src/main/java/ai/loomspan/autoconfigure/ExecutionTraceProperties.java:8` is the Spring integration binding type. It has one `@NotNull` persistence field defaulting to `ONERROR`, getter and setter. A null setter value restores that default. No directory property exists.
- `src/main/java/ai/loomspan/autoconfigure/LoomspanProperties.java:27` binds `loomspan` with `ignoreUnknownFields = false`. Its nested `executionTrace` instance is created eagerly and is exposed through getters/setters at lines 62–65.
- `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:78` exposes the nested trace properties as an infrastructure bean. Its session-runner bean at line 135 supplies host max depth, trace persistence, clock, observation factory, completion retention, quotas, canonical mapper and framework lifecycle.
- The runner has internal convenience constructors for test and subsystem use. The full lifecycle constructor starts at `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:183` and stores the generation-retention collaborator after constructing a trace factory.
- `src/main/resources/META-INF/additional-spring-configuration-metadata.json` supplies curated configuration descriptions and hints. `ConfigurationMetadataTest` parses packaged metadata and checks several configuration entries. `LoomspanPropertiesTest` covers Spring binding/defaults in the same autoconfigure test package.

### Published execution configuration boundary

- `src/main/java/ai/loomspan/internal/skill/ExecutionConfigurationParser.java:23` allows connections, models, session, execution-trace and executor-config under `loomspan`.
- At line 133, execution-trace is converted to an object and checked with the exact nested allowlist `Set.of("persistence")`. Only that property is flattened into the Binder input. The parser creates fresh `LoomspanProperties`, validates and returns the parsed persistence separately at line 154.
- `SkillGenerationManager` captures properties and persistence in `ExecutionRuntime`. Those generation properties are namespace execution configuration, while the auto-configured runner is host infrastructure.
- `src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java:297` already tests rejection of host shutdown settings and duplicate YAML keys. Its full valid configuration includes trace persistence at line 252. A directory rejection case does not currently appear.
- `src/test/java/ai/loomspan/testkit/PublicationFixtures.java:37` deliberately publishes only persistence from host trace properties, reflecting the current publishable surface.

### Entry namespace and one-trace execution flow

- `src/main/java/ai/loomspan/internal/core/SkillIdentity.java` validates namespace components with `[A-Za-z_][A-Za-z0-9_]*`, limits namespace length to 32, and normalizes namespace case using `Locale.ROOT`. Canonical namespaces cannot contain separators or dot components.
- `src/main/java/ai/loomspan/internal/core/CapturedNamespaceMetadata.java:12` requires its supplied entry namespace to equal its normalized form. It copies the generation map into an unmodifiable sorted map and checks that the entry generation matches the entry namespace mapping.
- `CapturedNamespaceMetadata.from(snapshot)` obtains the entry namespace from `snapshot.entry().namespace()` and the entry generation from `snapshot.entryGeneration()`.
- `src/main/java/ai/loomspan/internal/core/LoomspanSessionRunner.java:284` begins the admitted-root path. At line 300, it selects between the runner's existing factory and a generation-aware factory. The latter uses the captured entry quotas and canonical mapper and the host generation-retention collaborator. Both produce `DefaultExecutionTraceHandle`.
- The same method creates one `LoomspanSession`, supplies `CapturedNamespaceMetadata.from(snapshot)`, and selects the captured entry persistence. The root is bound with `ExecutionBinding.sessionOnly` before the action executes.
- `src/main/java/ai/loomspan/internal/core/LoomspanSession.java:112` creates its observation handle and invokes its trace factory once. If trace creation throws, it closes observation with `CORE_FINALIZATION_FAILED` and rethrows. `InternalExecutionTraceHandleFactory` is package-private and receives session ID, entry skill, captured namespace metadata, persistence, clock and observation handle.
- `src/main/java/ai/loomspan/internal/core/ExecutionBinding.java:57` changes current generation and mission for a nested target while retaining the session, branch and captured snapshot. `forkBranch()` similarly retains the session. Entry namespace remains accessible separately from current namespace.

### Path, filename, writing and error behavior

- `src/main/java/ai/loomspan/internal/runtime/trace/DefaultExecutionTraceHandle.java:64` is the package-private full constructor used by internal fixture helpers. A supplied exact `tracePath` is retained. Null selects `defaultPath(sessionId, traceId)` at line 89.
- Its public constructors at lines 110, 125, 141 and 161 create a UUID trace ID, delegate to the full constructor, reset the path and initialize the trace. They differ in retention, configured-limit and mapper collaborators.
- `defaultPath` at line 550 currently returns `Path.of(System.getProperty("java.io.tmpdir"), sessionId + "." + traceId + ".execution-trace.ndjson")`.
- The writer at `src/main/java/ai/loomspan/internal/runtime/trace/NdjsonTraceRecordWriter.java:31` invokes `Files.createDirectories(tracePath.getParent())` on append, then opens the selected file with CREATE, WRITE and APPEND. Serialization uses the existing canonical mapper and UTF-8 output followed by a newline.
- No explicit attributes, chmod, ACL changes, alternate directory selection or fallback writer exists in this path. Existing directories are passed to `Files.createDirectories` and file creation uses the current process/filesystem defaults.
- `resetTraceFile` at `DefaultExecutionTraceHandle.java:325` calls `Files.deleteIfExists(tracePath)` and wraps IOException in `IllegalStateException` naming the session. Initialization at line 179 wraps append IOException in `IllegalStateException("Failed to initialize execution trace for session ...")`. Later append and finalization methods expose IOException through existing callers.
- The handle records against one final path throughout writing, reading and finalization. No current per-record or per-target directory decision exists.

### Metadata and retention

- `DefaultExecutionTraceHandle.java:103` sets `tracePathMetadata` to the actual selected path unless a test supplies an explicit metadata override. `initialize` emits it as `TRACE_STARTED.metadata.tracePath` at line 186, alongside entry skill, entry namespace and generation facts.
- `snapshot()` at line 239 returns `ExecutionTrace` with `visibleTracePath()`. The visible path becomes null only after completion when deletion policy applies and the artifact is absent.
- `finalizeTrace()` at line 257 writes completion and applies the existing policy. NEVER deletes or temporarily retains through completion grace, ONERROR does so after success, and ALWAYS retains. Errored ONERROR executions retain normally.
- The finalized descriptor at line 302 carries the same actual path and namespace facts. `ImmediateCompletionRetention.java:16` deletes the supplied path. `ScheduledCompletionGraceRetention.java:57` converts the supplied path to an absolute normalized exact path, registers retention state and later deletes that same path.
- `src/main/java/ai/loomspan/internal/runtime/observation/catalog/InMemoryFinalizedTraceCatalog.java:66` checks that the supplied artifact path is a readable regular file and carries it into its catalog entry. Observation activation delegates retention using the supplied artifact path.

## Existing tests and fixtures

| Area | Current executable evidence |
| --- | --- |
| Filename, sequences, UTF-8 NDJSON | `NdjsonTraceRecordWriterTest#writesMonotonicSequenceAndSessionNamedTempFile` checks the existing filename suffix, monotonic sequence, newline and records. |
| Handle lifecycle | `ExecutionTraceHandleTest` covers NEVER/ONERROR/ALWAYS, unique trace IDs for the same session ID, terminal records, metadata, content, chunks, observation ordering and controlled writer failure. Most fixture helpers supply default namespace metadata. |
| Root lifecycle and persistence | `LoomspanSessionRunnerTest` covers successful ALWAYS, failed ONERROR, dropped successful ONERROR/NEVER, finalization and trace initialization failure. `LoomspanSessionPropertiesTest` protects persistence configuration. |
| Cross-namespace execution | `NamespaceObservationIntegrationTest#capturedMapAndTargetOwnerSurviveRemovalAndRecreation` invokes caller into dept, checks one session/trace/sequence, exact entry/target metadata and content. At line 54 it discovers the trace by listing `java.io.tmpdir` directly. |
| Captured settings | `NamespaceExecutionAuthorityIntegrationTest` checks entry persistence and quotas across namespace calls. `NamespaceStartupIntegrationTest` supplies reusable Spring runner/model mock setup. |
| Retention and observation | `ScheduledCompletionGraceRetentionTest`, `DefaultExecutionObservationHandleTest`, catalog tests and `ObservabilityRestIntegrationTest` protect the exact-path artifact lifecycle and existing HTTP acquisition. |
| Shared Console corpus | `ConsoleTraceFixtureCorpusTest` constructs deterministic explicit paths and metadata for semantic NDJSON fixtures through the handle's internal full constructor. These fixtures describe diagnostic content and are independent of automatic host directory selection. |
| Supported Java surface | `LoomspanPublicSurfaceArchitectureTest` classifies `ExecutionTraceProperties` as framework integration machinery at line 67 and `LoomspanSessionRunner` and `DefaultExecutionTraceHandle` as technically public internal collaborators at lines 145 and 256. |

## Contract inventory

| Category | Current exposure and supporting evidence |
| --- | --- |
| Application API | Public SkillTemplate invocation and SkillReloader/ExecutionConfiguration reach this behavior. The ticket requests no new Java application type or signature. The closed architecture allowlist is authoritative. |
| Supported SPI | Repository policy establishes none. Public internal constructors and infrastructure beans serve Java collaboration and Spring wiring. |
| Configuration and manifest contracts | `loomspan.execution-trace.persistence` is host-bindable and publishable. The directory setting requested by this ticket is absent. Strict parser allowlists own publication eligibility. |
| Persisted or serialized contracts | Persisted files exist on the filesystem. The ticket explicitly establishes the new administrator-visible layout and authorizes replacing the flat layout with no migration or compatibility shims. |
| Ephemeral diagnostic formats | Canonical NDJSON, trace path metadata and finalized artifacts describe the current execution. Matching Console readers consume those bytes, including the framework-owned release compatibility marker. |
| Internal or accidentally exposed implementation | Trace handle, writer, factory, runner, captured metadata, retention and catalog are internal. Configuration beans are Spring integration machinery. Technical public exposure establishes no additional API/SPI. |

No consumed REST/SSE or NDJSON field/type change is specified by this ticket. The existing path field carries the actual path as a value. Protected consumers are the in-process readers, journal projection, artifact retention/catalog/acquisition, and same-version Console import/inspection. The inspected Console boundary uses artifact bytes or the exact artifact descriptor supplied by the framework. The current ticket concerns filesystem placement and does not describe a protocol-marker change.

## Documentation assessment

The repository-local `agent-skills/loomspan-docs/SKILL.md` and Maven project both identify `1.0.0-beta.10-SNAPSHOT`. The documentation router was consulted after the executable inventory. Relevant topics are the skill-authoring index, `execution-limits.md`, `traces-and-debugging.md` and source-verification protocol.

**Drift classification — aligned.** Current trace guidance states one session/trace/sequence across namespaces, entry-captured persistence and limits, and separate target owner facts. Those statements agree with the production paths and focused tests. The current documentation does not specify the filesystem root/layout, which is absent as a configurable feature today.

**Skill-authoring impact — affected.** The requested setting changes how a host stores and administrators locate diagnostic traces. Relevant current guidance is README's Captured execution diagnostics section, trace debugging guidance and the execution configuration boundary. `README.md:250` explicitly lists only persistence as publishable. The authoring coverage table lists traces and execution limits as source-verified. The existing published configuration example in `execution-limits.md` includes only persistence.

## Architecture and historical context

The framework already separates captured entry execution settings from current target provider/mission settings. Namespace metadata is a primitive immutable observation fact captured at admission. One session owns canonical writing and completion, and storage consumers receive its exact descriptor path.

`ai/thoughts/framework-feature-design-lens.md` classifies current traces as diagnostic artifacts, requires coherent current writers/readers and content fidelity, and describes the pre-1.0 compatibility posture. The PR 36 ticket explicitly authorizes the layout replacement. `ai/thoughts/future/skill-namespaces-followup.md` concerns deferred namespace resource measurement and creates no additional acceptance gate for this work.

The previous PR 35.4 final review records Windows verification using `./mvnw.cmd`, Java 21, forced compilation flags `-Dmaven.compiler.useIncrementalCompilation=false` and `-DlastModGranularityMs=-1000000000`, and explicit Mockito agent `-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar`. That is historical environment evidence, not verification of PR 36.

## Metadata collection and verification scope

The required `bash ai/scripts/spec_metadata.sh` invocation was attempted. Windows system Bash returned access denied. The installed Git Bash executable also failed on a sandbox-denied MSYS named-object creation. Equivalent read-only PowerShell/Git commands supplied the actual timestamp, commit, branch and repository metadata above. `git config user.name` reports `matt-loomspan-ai`, and Codex is the researcher. No tests were run in this research step.

## Open questions for planning

No product decision blocks planning. Planning still needs to choose the internal directory parameter flow across the runner's ordinary and generation-aware handle creation paths, and the portable executable permission/error checks appropriate to Windows. Those are implementation and verification details bounded by the ticket. Existing explicit-path fixtures and the flat-directory discovery in `NamespaceObservationIntegrationTest` are concrete caller differences to account for.
