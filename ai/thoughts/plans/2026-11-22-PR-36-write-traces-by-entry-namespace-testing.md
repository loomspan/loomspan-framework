# Entry namespace trace directories Testing Plan

## Change Summary

The host's `loomspan.execution-trace.directory` selects a root, defaulting to `${java.io.tmpdir}/loomspan-traces`. Every generated trace path adds its canonical entry namespace and the existing session/trace filename. Namespace publication continues permitting only trace persistence. Writing, error propagation, metadata and retention operate on that selected path.

This is pipeline Step 3 under the Full 5-Step Pipeline and continues in the Step 2 context. The governing implementation plan is `ai/thoughts/plans/2026-11-22-PR-36-write-traces-by-entry-namespace.md`. The developer approved this profile. No tests were executed during planning.

## Impacted Areas

- Host binding in `ExecutionTraceProperties`, packaged Spring metadata and auto-configuration.
- `LoomspanSessionRunner` ordinary and generation-captured trace factories.
- `DefaultExecutionTraceHandle` generated path construction and existing explicit-file test seam.
- Existing writer parent creation, I/O failures, metadata, policy and completion retention.
- Cross-namespace invocation and `NamespaceObservationIntegrationTest` file discovery.
- README and routed authoring guidance for storage and the publication boundary.

## Risk Assessment

| Risk | Required evidence |
| --- | --- |
| Generation-aware factory accidentally reads published root/default instead of host root | Configured-root Spring invocation with a published entry configuration, plus ordinary runner branch coverage |
| Child namespace changes trace ownership | Real finance-to-shared call with one finance trace and target-owner records |
| Configured root is treated as a parent for another `loomspan-traces` suffix | Exact immediate parent assertion and root directory listing |
| Automatic default path remains flat on a constructor path | Default-parent test, constructor/call-site search and ordinary runner assertion |
| Recorded path differs from actual written/retained file | Trace-start metadata, snapshot and finalized descriptor path comparisons |
| Directory creation modifies existing administrator permissions | Supported POSIX/ACL attribute snapshots around actual trace creation |
| Error path writes into an alternate directory | Unusable namespace parent, expected existing I/O error and exact absence assertions |
| Retention stops applying to nested paths | Existing policy matrix plus configured nested-path retention tests |

The supported application Java surface is governed by the closed architecture allowlist. Run it after production changes. There is no supported SPI to preserve. The new host configuration is deliberate public configuration behavior. The flat persisted layout is intentionally replaced under Pipeline notes, so existing flat discovery must be updated atomically. Diagnostic record semantics, journal projection and consumed HTTP/NDJSON structure remain current-version obligations.

## Existing Test Coverage

- `LoomspanPropertiesTest` supplies binding runners and strict startup validation patterns.
- `ConfigurationMetadataTest` reads the packaged generated metadata.
- `ExecutionConfigurationParserTest` checks published allowlists and valid persistence.
- `ExecutionTraceHandleTest` covers persistence matrix, filename/unique IDs, initialization/write failures, path metadata, descriptors, completion ordering and content.
- `NdjsonTraceRecordWriterTest` checks actual NDJSON output, monotonic sequences and filename suffix.
- `LoomspanSessionRunnerTest` covers ordinary roots, trace lifecycle and failure propagation. `TestSkillGenerations.empty()` has no captured runtime and reaches the ordinary branch.
- `NamespaceObservationIntegrationTest` covers one session/trace/sequence and retained foreign target generations. Its flat file listing is the concrete discovery call to update.
- `NamespaceExecutionAuthorityIntegrationTest` covers captured entry persistence/quotas across foreign calls using real namespace publication and mock HTTP providers.
- `ScheduledCompletionGraceRetentionTest`, `DefaultExecutionObservationHandleTest` and `InMemoryFinalizedTraceCatalogTest` cover exact-path lifecycle consumers.
- `ObservabilityRestIntegrationTest` and `ObservabilityArtifactIntegrationTest` protect descriptor/HTTP acquisition behavior.
- `ConsoleTraceFixtureCorpusTest` protects canonical reader/projector semantics using deterministic explicit paths. Its content fixtures should retain their exact-path purpose.

The gaps are a host directory binding, default/configured namespace parent assertions, publication rejection for directory, both factories' use of host root, actual permission preservation and a directory failure without alternate placement.

## Bug Reproduction / Failing Test First

Add `ExecutionTraceHandleTest#defaultTracePathUsesEntryNamespaceDirectory` before production edits. Construct a real `DefaultExecutionTraceHandle` using existing production construction with `CapturedNamespaceMetadata("finance", generationId, Map.of("finance", generationId))`, unique session ID, ALWAYS and the existing no-op observation collaborator. Assert the exact parent equals `Path.of(System.getProperty("java.io.tmpdir"), "loomspan-traces", "finance")`, the existing filename shape, and actual file existence. Delete the created file in a finally block.

Run that single method against the pre-fix checkout. It should fail because the actual parent is `java.io.tmpdir`. Preserve the failure result in Step 4's report. After constructor plumbing changes, adapt its construction to the new explicit root parameter while continuing to use the default-root helper. Its assertion remains independent and states the exact required path.

## Tests to Add or Update

### 1. Default and host root binding

**Type** — Unit with Spring binding.

**Location** — `src/test/java/ai/loomspan/autoconfigure/LoomspanPropertiesTest.java` and `ConfigurationMetadataTest.java`.

Add `executionTraceDirectoryDefaultsAndBindsAsHostRoot`. Use the existing binding runner to assert the omitted default and a supplied temporary path containing spaces. Assert the bound value equals the configured root itself. Also assert a plain `ExecutionTraceProperties` default is correct. Test explicit null setter rejection if the plan's setter uses it, with the property object's normal validation conventions.

Add a metadata assertion for the new name, Path type and description establishing host root/entry child behavior. The symbolic temporary-directory default must agree with production initialization.

Fixtures are local temporary paths. No mocks are needed. The affected surface is configuration behavior and the compatibility expectation is the newly specified host setting.

### 2. Published execution configuration rejects directory

**Type** — Parser unit test.

**Location** — `src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java`.

Add `publishedTraceConfigurationRejectsHostDirectory`. Parse a minimal otherwise-valid configuration containing `loomspan.execution-trace.directory`. Require rejection naming the full field path. Keep existing persistence acceptance tests. Include persistence alongside directory to establish that the nested scope stays restricted even when a valid key is present.

Use the existing parser fixture and literal YAML. No mocks are needed. This covers the configuration/access boundary. Production parser changes are unnecessary if its current allowlist already supplies the required error.

### 3. Generated path, metadata and directory creation

**Type** — Filesystem unit test.

**Location** — `src/test/java/ai/loomspan/internal/runtime/trace/ExecutionTraceHandleTest.java`.

Keep the failing-default test and add `configuredTraceRootUsesCanonicalEntryNamespaceAndReportsActualPath`. Use a temporary root containing spaces and canonical finance metadata. Start with no finance directory. Construct the initialized handle and assert its exact parent is `<root>/finance`, filename is `<sessionId>.<actual traceId>.execution-trace.ndjson`, and the file exists there. Assert TRACE_STARTED metadata `tracePath`, snapshot `filePath`, and finalized artifact path all identify that file. Root children contain the namespace directly. A second handle for shared uses `<root>/shared` and a distinct filename. Use ALWAYS for retained-file assertions.

Reuse fixed clocks and existing observation/canonical mapper collaborators. No writer mock is used for placement. This covers persisted layout and current-run diagnostic coherence.

### 4. Existing permissions survive writing

**Type** — Filesystem unit test.

**Location** — `ExecutionTraceHandleTest.java` alongside the path cases.

Add `writingIntoExistingNamespaceDirectoryPreservesPermissions`. Pre-create `<root>/finance`, record its supported permissions, create and finalize a real trace there, then compare the attributes. Use `AclFileAttributeView#getAcl` on Windows and `PosixFileAttributeView#readAttributes().permissions()` on POSIX. For POSIX, set an owner-only writable mode before the snapshot and restore original permissions during cleanup. Compare owner/group too where the selected view exposes them. When both views exist, checking both is inexpensive and gives stronger evidence.

Keep the placement/write assertions unconditional. Attribute-view assertions depend on the filesystem's actual supported views and the report must state which view ran. An unsupported view does not become a pass. Avoid assumptions that a test user or administrator cannot bypass access denial.

This covers the filesystem permission acceptance criterion through the unchanged directory-creation writer. The test does not install a framework permission policy.

### 5. Unusable namespace path fails without alternate output

**Type** — Filesystem unit test, supplemented by existing runner error tests.

**Location** — `ExecutionTraceHandleTest.java` and existing `LoomspanSessionRunnerTest` failure cases.

Add `unusableNamespaceDirectoryUsesExistingWriteFailureAndCreatesNoFallback`. Create a regular file at `<root>/finance`, then try a finance trace with a unique session ID. Require the existing initialization exception and an I/O cause. Assert the blocker content is unchanged, no trace file exists in other configured-root locations, and no matching unique-session file exists in the default finance directory or the old flat temporary root. The occupied path supplies a deterministic designated-directory write failure on Windows and POSIX.

Use actual filesystem operations, the real writer and no-op observation. Existing controlled writer/runner failures continue protecting exception containment and observation cleanup. This covers persisted layout, error visibility and the absence of alternate placement.

### 6. Nested-path persistence and completion grace

**Type** — Filesystem/lifecycle unit tests.

**Location** — `ExecutionTraceHandleTest.java`, with `ScheduledCompletionGraceRetentionTest` reused for its retention engine.

Exercise NEVER, successful ONERROR, failed ONERROR and ALWAYS using a configured root and namespace metadata. Successful NEVER/ONERROR follow existing deletion semantics. Failed ONERROR and ALWAYS retain the selected namespace file. A capturing `CompletionGraceRetention` collaborator asserts it receives exactly that path. Use an existing deterministic scheduled retention clock/executor fixture to check the lease/deletion path where a new regression assertion is needed. Prefer extending current tests to introducing a second retention harness.

This covers current policy compatibility and metadata accuracy. The tested matrix is behavioral rather than a mirror of path implementation.

### 7. Both runner factories use the host root

**Type** — Runner unit test and Spring integration.

**Location** — `LoomspanSessionRunnerTest.java` and the namespace storage integration case below.

Add `hostTraceDirectoryAppliesWithoutCapturedRuntime` using the normally configured runner construction, a custom root and `TestSkillGenerations.empty()` snapshot. The action's `session.getExecutionTrace().filePath()` points to `<root>/default`, and finalization keeps normal ALWAYS behavior. This reaches the ordinary factory through a real session.

The namespace integration test reaches the captured-runtime branch using a published execution configuration. Its host root must win over the built-in directory field present in fresh generation properties. This proves the two branches without using a root supplied only through a mock factory.

### 8. Finance entry owns a foreign shared call

**Type** — Spring namespace integration through `SkillTemplate` and `SkillReloader`.

**Location** — A focused test in `NamespaceObservationIntegrationTest.java`, reusing `NamespaceStartupIntegrationTest` runner, provider-response helpers and fixed Java skill pattern. A separate `NamespaceTraceDirectoryIntegrationTest.java` is acceptable if the existing file would become difficult to navigate, and its class must be included in the combined command.

Configure host `loomspan.execution-trace.directory` to an isolated temporary root and host persistence NEVER. Publish a finance model-backed caller with published persistence ALWAYS, plus a Public shared target. Reuse existing Java methods or executor fixtures so the foreign target runs without another real provider. Supply local MockWebServer responses for the model tool call and final result.

Invoke finance with mixed-case namespace shorthand only where the existing public invocation contract supports normalization. The required directory assertion uses canonical `finance`. Assert one finance trace file, no shared trace for that nested call, one trace/session/sequence, actual tracePath metadata, entry namespace finance, and executing namespace shared records. Then invoke a shared root with its own ALWAYS publication and assert its single independent trace resides directly under `<root>/shared`.

Attempt to prepare a publication with a `directory` key through `SkillReloader` and require rejection. An attempted path is an isolated sibling under the test temporary directory. Verify the publication failure creates no trace output there and later normal invocations retain the configured host placement.

Update `capturedMapAndTargetOwnerSurviveRemovalAndRecreation` to find the trace under the selected root/`caller` directory, retaining its current captured-generation, content and ordering assertions.

Mocks are limited to deterministic local provider HTTP responses. Host binding, publication, session creation, path selection, writing and invocation remain real. This covers the ticket's complete cross-namespace outcome and the configuration authority boundary.

## Authoring Claims and Evidence

| Claim in changed guidance | Executable evidence |
| --- | --- |
| Host root defaults to temporary `loomspan-traces`, and configured value is the root itself | Binding/metadata and default/configured path tests |
| Canonical entry namespace owns the whole trace | Captured namespace validation, real finance/shared integration and existing retained namespace observation |
| Published execution configuration permits persistence and rejects directory | Parser rejection plus public preparation rejection |
| Actual metadata names the selected path and retention still applies | Path metadata/finalized descriptor assertions and policy/retention matrix |
| Missing directories are created, existing permissions survive, failure has no alternate destination | Real writer path tests, permission view comparison and occupied-parent failure |

Documentation comparison is currently **aligned** for existing semantics. Update guidance in the same implementation and classify it aligned against the completed executable evidence. README's trace coverage/routing expansion is required. Explicit-file semantic fixtures establish trace coherence without asserting automatic directory layout.

## How to Run

Use Java 21 and the repository Maven wrapper from the checkout root. `mvn -v` already confirmed the available Java 21 installation during orchestration. The previous PR 35.4 review records successful Windows compiler timestamp flags and the installed Mockito agent. Reuse these flags to force current source compilation and avoid dynamic attach failures.

Pre-fix focused path reproduction, after adding the test and before production edits.

```powershell
./mvnw.cmd '-Dmaven.compiler.useIncrementalCompilation=false' '-DlastModGranularityMs=-1000000000' '-Dtest=ExecutionTraceHandleTest#defaultTracePathUsesEntryNamespaceDirectory' test
```

Combined post-fix verification, with exact new class names incorporated if the implementation creates the separate integration test.

```powershell
./mvnw.cmd '-Dmaven.compiler.useIncrementalCompilation=false' '-DlastModGranularityMs=-1000000000' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar' '-Dtest=LoomspanPropertiesTest,ConfigurationMetadataTest,ExecutionConfigurationParserTest,ExecutionTraceHandleTest,NdjsonTraceRecordWriterTest,LoomspanSessionRunnerTest,LoomspanSessionPropertiesTest,EntrySkillIdentityTest,NamespaceObservationIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,NamespaceStartupIntegrationTest,ScheduledCompletionGraceRetentionTest,DefaultExecutionObservationHandleTest,InMemoryFinalizedTraceCatalogTest,ObservabilityRestIntegrationTest,ObservabilityArtifactIntegrationTest,ConsoleTraceFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillReachableAdmissionTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest' test
```

```powershell
git diff --check
```

Also inspect all trace-construction call sites and searches for flat temporary-root discovery after the change. This source inspection validates that production path ownership remains confined to the handle and both factories receive the root. No full repository or Console suite is required by the bounded path/value change unless a failure or expanded diff creates a concrete need.

Record actual test counts, errors, failures, skips and supported permission view from the current run. Historical review results provide environment setup only.

## Exit Criteria

- [x] The default-parent test demonstrably fails on current flat placement before the production change.
- [x] Default and configured roots, exact immediate namespace placement and the existing filename are proven.
- [x] Both runner trace construction branches use host storage authority.
- [x] Public publication rejects directory and normal cross-namespace calls stay in the entry trace.
- [x] Actual path metadata, descriptor and retention behavior are verified.
- [x] Missing directories are created and real pre-created directory permission attributes are preserved on available views.
- [x] A designated directory write failure follows existing error handling and produces no alternate trace.
- [x] The combined targeted suite and supported Java surface architecture checks pass with no unexplained required failure.
- [x] Existing diagnostic sequence/content and semantic fixture checks remain coherent.
- [x] Documentation claims match the tests/source and the routed coverage table is updated.
- [x] Every ticket acceptance criterion is mapped to sufficient actual verification in Step 4's report.
- [ ] A fresh independent Step 5 review returns clean with sufficient verification.

## Optional Developer Checks

None. Any filesystem attribute view unavailable on the verification host must be disclosed in the implementation/review report with the exercised view and residual platform coverage, without converting unsupported checks into claimed passes.

## Step 4 verification record

The pre-fix default-path regression produced its required failing parent-path assertion. The final combined command above passed 231 tests with zero failures, errors or skips after compiling all production and test sources. It includes the eight architecture checks and the additional lifecycle/template tests affected by internal constructor updates. Windows ACL and owner preservation ran successfully. POSIX attributes were unavailable on this filesystem and that branch remains platform-specific unexecuted coverage. `git diff --check` passed. The implementation plan contains the complete acceptance-criterion mapping and exact verification commands. All Step 4 exit criteria are satisfied. Fresh Step 5 review remains pending.
