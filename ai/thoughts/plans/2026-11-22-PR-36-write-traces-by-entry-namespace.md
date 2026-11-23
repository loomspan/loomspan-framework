# Entry namespace trace directories Implementation Plan

## Overview

Add the host setting `loomspan.execution-trace.directory` and place each automatically named execution trace in its canonical entry namespace's immediate child directory. The default root is `${java.io.tmpdir}/loomspan-traces`. The root stays owned by host infrastructure throughout admission and cross-namespace execution.

This is pipeline Step 2 under the Full 5-Step Pipeline. The developer approved that profile after Step 0. The baseline was `main` matching `origin/main`, with only the supplied ticket untracked. Research produced `ai/thoughts/research/2026-11-22-PR-36-write-traces-by-entry-namespace.md`. The existing ticket is user-supplied work to preserve.

## Current State Analysis

- `DefaultExecutionTraceHandle.java:89` selects the path once, owns all subsequent writing/reading and puts that path into trace metadata. Its `defaultPath` at line 550 currently selects a flat temporary-file path.
- `CapturedNamespaceMetadata.java:12` validates a canonical entry namespace. `SkillIdentity` excludes path separators and dot components from namespace syntax. The trace directory can use this existing authoritative fact directly.
- `LoomspanSessionRunner.java:300` selects an ordinary trace factory or a factory using generation-captured quota settings. Both must receive the host directory. `CapturedNamespaceMetadata.from(snapshot)` supplies admission-time entry ownership.
- `ExecutionTraceProperties.java:8` binds persistence today. `LoomspanAutoConfiguration.java:135` constructs the host runner using that bean.
- `ExecutionConfigurationParser.java:133` explicitly allows only `persistence` below published `execution-trace`. `PublicationFixtures` similarly copies only persistence. This existing boundary already rejects a published directory key.
- `NdjsonTraceRecordWriter.java:31` creates parent directories and appends the selected file. It sets no POSIX permissions or ACLs. Handle initialization already reports write failures. Completion retention and catalog collaborators consume the exact artifact path.
- `NamespaceObservationIntegrationTest.java:54` directly lists the temporary root. It must discover the trace in the entry namespace directory after the layout change.

## Desired End State

For root `R` and canonical entry namespace `N`, the selected file is `R/N/<sessionId>.<traceId>.execution-trace.ndjson`. A configured root is used directly. A root `/var/department-traces` produces `/var/department-traces/finance/...` for a finance entry. The default finance path is `${java.io.tmpdir}/loomspan-traces/finance/...`.

The whole run keeps that one path when it invokes skills owned by other namespaces. The path reported by trace start, snapshots and finalized artifacts identifies that file. Existing persistence and completion-grace policy apply to it. The existing writer creates absent parents and the existing error paths report filesystem failures.

## Scope Boundaries

The change covers host binding, trace directory selection, affected tests and current product guidance. The ticket authorizes outright replacement of the flat layout and explicitly omits migration, legacy lookup and compatibility shims. Filesystem permissions remain administrator-managed. Existing filename, record content, persistence policy, directory creation, error propagation, retention and transport semantics provide the implementation foundation.

## Skill-Authoring Documentation Impact

**Impact** — Affected.

The repository-local `loomspan-docs` router and source both identify `1.0.0-beta.10-SNAPSHOT`. After source/test inventory, the router led to `traces-and-debugging.md`, `execution-limits.md` and `source-verification.md`. Existing guidance about one trace across namespaces and entry-captured persistence is **aligned** with executable evidence. Storage layout is a new documented setting and behavior.

Update these documents in Phase 3.

- `README.md` under Captured execution diagnostics describes the host setting, default root, namespace child and filesystem administration. Its publishable-key list already restricts trace publication to persistence and should remain precise.
- `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md` gains a compact storage section containing the default/configured root, entry ownership, actual path metadata, directory creation, permissions and write failure behavior. Cite focused source and test anchors by name.
- `agent-skills/loomspan-docs/references/skill-authoring/execution-limits.md` identifies the host directory separately from entry-captured persistence and links to the trace storage section. Its example is host startup configuration and can include the directory with an explicit host-only explanation.
- `agent-skills/loomspan-docs/references/skill-authoring/README.md` extends the existing trace coverage row and routing to include storage configuration. This coverage update is required because the documented task boundary expands.

Evidence will come from binding/metadata tests, parser publication rejection, handle filesystem/lifecycle tests and namespace integration. Preserve progressive disclosure, concise current-system prose and stable named source/test anchors. Product guidance must be usable without this ticket or delivery history.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned treatment |
| --- | --- | --- |
| Application API | Closed allowlist in `LoomspanPublicSurfaceArchitectureTest`; existing `SkillTemplate` and publication calls reach the feature | Existing signatures remain sufficient. Run the architecture test after production changes |
| Supported SPI | Repository policy establishes no supported Java SPI | Keep the new dataflow internal |
| Configuration and manifest contracts | New host `loomspan.execution-trace.directory`; existing strict published allowlist permits persistence | Add default/binding/metadata guidance and rejection evidence. Keep directory out of published execution configuration |
| Persisted or serialized contracts | Administrator-visible filesystem layout changes under explicit ticket requirements | Replace the flat layout atomically and update repository discovery code |
| Ephemeral diagnostics | Existing path-valued metadata identifies the newly selected file | Preserve current writer/reader/projector fidelity, ordering, path accuracy and retention |
| Internal implementation | Runner, factory, handle constructors and test construction seams | Update callers atomically. Internal Java visibility creates no compatibility obligation |

**Evidence of supported contracts** — AGENTS.md, the architecture allowlist, README's published configuration list and this ticket establish the boundaries. In-process readers, finalized catalogs, retention/acquisition and matching Console consume the current descriptor and bytes.

**Intentional compatibility changes** — The flat temporary-directory storage layout is replaced as expressly authorized by Pipeline notes.

**In-repository consumers to update** — Auto-configuration, trace construction sites, synthetic trace helper calls affected by signatures, namespace trace discovery, focused binding/parser/path tests and the documentation above. Deterministic corpus fixtures with explicit exact paths retain their fixture purpose.

**Public-surface delta** — Add a property accessor pair to Spring integration machinery. Internal constructors may accept a `Path`, and an internal default-directory helper may be technically public to cross packages. Add no type or signature to the supported application API allowlist and create no bean replacement contract.

**Shim decision** — No shim. The ticket authorizes the layout break, and the changed constructors are internal. Useful constructors that intentionally supply defaults can continue serving internal callers, but signature changes must not add obsolete overloads solely for compatibility.

**Java-to-Go boundary coordination** — Not required. Record fields, artifact HTTP/SSE behavior and consumed NDJSON schema retain their current semantics. The existing `tracePath` value reports the selected path. Keep the current compatibility marker.

**Pipeline notes alignment** — Aligned. The authorized break is confined to output layout.

## Implementation Approach

Use `Path` for the bound root and carry it as a host-owned immutable runner value. Add a small internal default-directory function on `DefaultExecutionTraceHandle` returning `Path.of(System.getProperty("java.io.tmpdir"), "loomspan-traces")`. Reuse it for the property default and internal convenience construction so the default has one definition. This helper is implementation detail.

Extend the trace handle's generated-path selection to resolve the root, `namespaceMetadata.entryNamespace()` and its existing filename expression. Add the root parameter to production construction paths and update their callers atomically. Keep the package-private exact-file-path seam used by deterministic fixtures. Its automatic/null-path branch must use the same namespace layout.

Pass the configured host root through the full runner construction from auto-configuration and retain it for both its ordinary factory and generation-captured factory. Generation properties continue providing their existing persistence/limits, while the runner supplies the directory. This avoids giving publication properties storage authority. Existing default convenience runner construction supplies the default root through the same generated-path logic.

Alternative placement in per-generation execution settings would duplicate ownership and expose a host boundary to publication. Selecting the directory in the NDJSON writer would leave handle metadata/retention paths inconsistent. The handle already owns filename/path, making it the narrowest complete placement.

No dead or unreachable production logic requiring separate cleanup was established during this investigation. Constructor plumbing is confined to the directory feature.

## Phase 1 — Host configuration and directory selection

### Changes Required

1. In `ExecutionTraceProperties`, add a non-null `Path directory` with the default-directory helper and normal getter/setter binding. The setter rejects null rather than allowing an unconfigured root to reach execution. Spring path conversion handles configured filesystem paths, including spaces. Preserve ordinary relative-path semantics.
2. In `META-INF/additional-spring-configuration-metadata.json`, describe the property as the host trace root with canonical entry namespace subdirectories and the symbolic temporary-directory default.
3. In `DefaultExecutionTraceHandle`, route root and captured entry namespace into generated path selection while preserving the exact filename expression, reset/initialize sequence, writer, metadata and error flow.
4. In `LoomspanSessionRunner` and `LoomspanAutoConfiguration`, carry the root to both ordinary and captured-runtime trace creation. Capture a host value at runner construction and keep it independent from generation properties.
5. Update affected internal callers and synthetic test helper wiring for constructor changes. Retain explicit-file corpus construction and use the new default layout for synthetic automatic paths.
6. Add/update focused binding, generated metadata, publication rejection and default/configured path tests as specified in the testing plan. Establish the failing default-parent test before production edits.

### Automated Success Criteria

- [x] Default and configured root binding pass, including a configured root with spaces.
- [x] Packaged configuration metadata includes the new setting and host ownership.
- [x] Published configuration rejects `loomspan.execution-trace.directory` with its field path.
- [x] Both runner trace construction branches select the host root.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes.

## Phase 2 — Namespace, filesystem and lifecycle verification

### Changes Required

1. Extend `ExecutionTraceHandleTest` with exact default and custom namespace-parent assertions, existing filename/trace-ID behavior, actual metadata and finalized-path assertions.
2. Exercise policy and completion retention with a custom namespace path using existing collaborators. Verify the supplied selected file is retained/deleted under the existing rules.
3. Add a portable filesystem failure test using a regular file occupying the namespace directory. Existing initialization must fail with an I/O cause and produce no alternate trace. Check absence in the configured root's other locations and the known automatic default candidates for the test's unique session ID.
4. Verify missing namespace directories are created. For a pre-created directory, compare the filesystem's supported permission representation before and after writing. Use `AclFileAttributeView` on Windows and `PosixFileAttributeView` on POSIX. A POSIX fixture can set a restrictive administrator-style mode and restore it during cleanup. This comparison exercises actual writer behavior without relying on an effective user being unable to bypass a deny rule.
5. Extend namespace integration with configured root and independent finance/shared entries. Finance invokes a shared target, remains one trace under finance and carries shared execution-owner evidence. A shared root produces its own immediate shared child path.
6. Update the existing namespace observation test's flat discovery to its entry namespace directory and preserve its retained-generation, one-sequence and content assertions.

### Automated Success Criteria

- [x] Every acceptance criterion has executable coverage under the accompanying testing plan.
- [x] New tests validate host root selection through normal application/runner entry points and direct path mechanics.
- [x] Existing focused retention, observation, catalog and semantic fixture tests pass.
- [x] Permission checks report which filesystem attribute view was exercised. Unsupported views are reported accurately.

## Phase 3 — Product guidance and final verification

Apply the documentation changes in the impact section with implemented test names. Check each claim against the implementation and focused tests. Run the combined suite in the testing plan after final source/test changes, then `git diff --check`. Record exact commands and results in the Step 4 report and update phase checkboxes with actual evidence.

### Automated Success Criteria

- [x] README, routed trace guidance, execution limits and coverage table describe the implemented host root and entry ownership coherently.
- [x] Documentation classification is aligned after the change.
- [x] The combined targeted Maven suite and architecture test pass with no unresolved required verification.
- [x] The fresh independent reviewer can reconstruct the complete scope from ticket, research, these plans and current checkout.

## Testing Strategy

Use binding/parser unit tests to establish host/publication boundaries, direct trace tests for path/metadata/filesystem/lifecycle behavior, runner tests for both factory branches, and Spring namespace integration for real cross-namespace invocation. Existing writer, reader, retention, observation/catalog and corpus tests protect the path-dependent lifecycle and diagnostic fidelity. Detailed cases and exact commands are in `2026-11-22-PR-36-write-traces-by-entry-namespace-testing.md`.

## Performance and Migration

Directory selection adds two `Path.resolve` operations per trace. Existing parent creation remains on the writer's path. The ticket authorizes immediate replacement of the flat layout with no migration or lookup machinery.

## Optional Developer Checks

None. Automated checks cover the ticket's filesystem semantics on available filesystem attribute views. Deployment-specific department ACL provisioning remains administrator configuration and is outside this implementation's deliverables.

## References

- Ticket `ai/thoughts/tickets/loomspan-pr-36-write-traces-by-entry-namespace.md`
- Research `ai/thoughts/research/2026-11-22-PR-36-write-traces-by-entry-namespace.md`
- Design lens `ai/thoughts/framework-feature-design-lens.md`
- Verification environment evidence `ai/thoughts/reviews/2026-11-22-PR-35.4-startup-variable-resolution-review-3.md`

## Step 4 implementation and verification results

Pipeline Step 4 completed on 2026-11-22 under the developer-approved full profile. The implementation follows the planned host-owned Path dataflow and reuses the existing writer, initialization/error handling, filename, content, persistence and retention. All internal constructor callers, including lifecycle and synthetic session tests, were updated atomically. No application API, bean replacement contract or Java-to-Go protocol surface was introduced.

The default-layout regression was run before production edits and failed at its exact parent assertion. The observed parent was `java.io.tmpdir` and the expected parent was `java.io.tmpdir/loomspan-traces/finance`. Initial new-test accessor and internal-constructor compilation errors were corrected before the successful final run.

### Acceptance evidence

| Ticket criterion | Actual verification |
| --- | --- |
| Default finance path, existing filename and actual metadata | `ExecutionTraceHandleTest#defaultTracePathUsesEntryNamespaceDirectory` and `configuredTraceRootUsesCanonicalEntryNamespaceAndReportsActualPath` passed. The latter compares trace-start metadata, snapshot and finalized descriptor with the written file. |
| Existing retention on namespace paths | `namespacePathsFollowExistingPersistenceAndCompletionRetention` passed for all persistence policies and successful/failed outcomes, and confirms the completion-retention collaborator receives the selected path. Existing scheduled-retention, observation/catalog and artifact tests passed. |
| Configured root used directly for finance/shared, with foreign call in finance trace | `NamespaceObservationIntegrationTest#hostRootAndEntryNamespaceOwnCrossNamespaceTrace` passed through host binding, public publication and invocation. It verifies mixed-case namespace admission, one finance file and sequence containing shared-owner records, and a separate shared root trace in the immediate shared directory. `LoomspanSessionRunnerTest#hostTraceDirectoryAppliesWithoutCapturedRuntime` passed for the other factory branch. |
| Published configuration cannot redirect output | The parser rejection and public `SkillReloader.prepare` rejection passed with the full directory key. The integration test verifies no redirected output exists and later ordinary invocations use the host root. |
| Missing namespace directory is created | Configured-root direct and integration tests started without the namespace directories and observed real files in those directories. |
| Existing administrator permissions survive | `writingIntoExistingNamespaceDirectoryPreservesPermissions` passed with Windows ACL and owner comparisons around real trace writing. POSIX attributes are unavailable on this Windows filesystem, so the POSIX-specific branch was not exercised. |
| Unusable destination follows existing errors without alternate output | `unusableNamespaceDirectoryUsesExistingWriteFailureAndCreatesNoFallback` passed with a regular file occupying the designated namespace directory, the existing initialization exception/I/O cause, unchanged blocker content, and no matching trace in configured/default/old flat candidates. |

### Executed verification

Pre-fix reproduction command, expected failure with one failed parent-path assertion after correcting the test accessor.

```powershell
./mvnw.cmd '-Dmaven.compiler.useIncrementalCompilation=false' '-DlastModGranularityMs=-1000000000' '-Dtest=ExecutionTraceHandleTest#defaultTracePathUsesEntryNamespaceDirectory' test
```

Final combined command, PASS with 231 tests, zero failures, errors or skips. Javac compiled all 350 production and 275 test sources. The eight supported-surface architecture checks passed. Additional lifecycle/template suites cover the internal constructor updates.

```powershell
./mvnw.cmd '-Dmaven.compiler.useIncrementalCompilation=false' '-DlastModGranularityMs=-1000000000' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar' '-Dtest=LoomspanPropertiesTest,ConfigurationMetadataTest,ExecutionConfigurationParserTest,ExecutionTraceHandleTest,NdjsonTraceRecordWriterTest,LoomspanSessionRunnerTest,LoomspanSessionPropertiesTest,EntrySkillIdentityTest,NamespaceObservationIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,NamespaceStartupIntegrationTest,ScheduledCompletionGraceRetentionTest,DefaultExecutionObservationHandleTest,InMemoryFinalizedTraceCatalogTest,ObservabilityRestIntegrationTest,ObservabilityArtifactIntegrationTest,ConsoleTraceFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,SkillReachableAdmissionTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest' test
```

`git diff --check` passed. Call-site and flat-discovery searches found the six updated production construction sites and the updated caller-namespace discovery. All automatic path selection resolves the captured canonical namespace. Exact-file corpus seams retain their fixture purpose. The full diff introduces no additional supported application-facing types or Spring extension points.

Documentation classification is **aligned**. README, routed trace storage guidance, execution-limit startup example and routing/coverage entries describe the implemented behavior and cite focused source/test anchors. The routed guidance identifies host authority, published-key rejection, paths, permissions, errors and retention without depending on delivery history. There are no optional developer checks. Independent Step 5 review remains the next pipeline stage.
