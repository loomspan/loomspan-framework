# PR 11.1 Complete Execution Configuration Testing Plan

## Change Summary

- Add a supported `SkillReloader.prepare(documents, configuration, credentialValues)` path that resolves every credential reference from a transient host map, without reading deployment provider credentials.
- Preserve existing file startup and environment-reference preparation, while adding in-memory Gemini Vertex credential preparation and ensuring one captured generation supplies all execution settings and clients.
- Keep preparation non-billable, atomic on failure, and free of secret readback.

## Impacted Areas

- API and publication: `src/main/java/ai/loomspan/api/SkillReloader.java`, `src/main/java/ai/loomspan/internal/skill/DefaultSkillReloader.java`, `SkillGenerationManager.java`.
- Candidate parsing, property defaults and provider construction: `ExecutionConfigurationParser.java`, `LoomspanProperties.java`, `ExecutionRuntime.java`, `NamedAiConnectionRegistry.java`, `SpringAiProviderIntegration.java`.
- Bound runtime consumers: handoff, session runner, mission executor, attachment materializer, usage service, step engine, chat client assembler, provider attempt advisor, trace recorder, and any further production reads found by the Phase 2 audit.
- Documentation: `README.md`, model-selection and Java reload topics, relevant limit/trace topics and the authoring coverage table.
- Companion acceptance: Sidecar snapshot preparation, execution correlation and encrypted credential source, owned by PR 7.1.

## Risk Assessment

- A missing map entry might accidentally fall back to `Environment`, violating Console override isolation. A reused reference name might resolve inconsistently. A mutable caller map might change mid-preparation.
- Secret values might enter record/string representations, Binder or SDK error text, validation feedback, catalogs, traces, logs, or Sidecar read/export payloads.
- Gemini Vertex JSON parsing might defer failure until first request, leak source text in exceptions, or issue an external request during preparation.
- Bound work might consult a startup bean fallback after publication; delayed, nested or parallel work and retry attempts could mix settings and credentials.
- Candidate construction or close failure might leak partly constructed clients, affect the active generation, or violate the one shutdown budget.
- Protected paths: `ExecutionConfiguration(String)`, existing `SkillReloader` overloads, file-based environment placeholders, strict candidate syntax/defaults, captured root correlation and sole `RestSkillHandler` SPI. Evidence: architecture allowlist, README and the tests below.
- Intentionally removed paths: internal mutable-global execution reads discovered during audit. No supported API path is planned for removal. Direct-secret YAML and process fields remain rejected.

## Existing Test Coverage

- `src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java` tests reference resolution, default values, direct-secret and process-field rejection, and duplicate keys, but has no host-map mode or in-memory Vertex source.
- `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java` tests complete validation and provider resource retirement; `SkillReloaderTest.java` covers publication, stale/abandoned candidate cleanup and concurrency.
- `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` covers atomic handoff and retained generation selection, but needs full credential/setting contrast across physical descendants.
- `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java` protects the closed API and signature boundary.
- `../loomspan-sidecar/src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java` and `.../execution/AuthenticatedExecutionApiIntegrationTest.java` cover existing reference-only publication and durable snapshot correlation; companion PR 7.1 must add the host-supplied credential case.

## Bug Reproduction / Failing Test First

- **Type**: integration with fake provider factory; a narrow API compilation failure first establishes the missing supported overload.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java` or a new public-only publication integration test alongside `PublicSkillReloadIntegrationTest`.
- **Arrange/Act/Assert**: Construct a candidate YAML with `openai` connection `api-key-ref: sidecar.openai.key` and a model-backed skill. Keep that property absent from `Environment`. Call the new public `prepare(documents, configuration, Map.of("sidecar.openai.key", "key-A"))`, publish, and assert the fake provider runtime received `key-A`; then publish `key-B` and assert the old captured root still calls A while a new root calls B. Verify no model send during either preparation.
- **Expected failure pre-fix**: The overload does not compile. Using the existing overload fails because the reference is unavailable from `Environment`. This is the smallest proof that Sidecar cannot currently use its encrypted credential store through supported API.

## Tests to Add/Update

### 1) HostMapReferencesAreCompleteAndIsolated

- **Type**: unit.
- **Location**: `src/test/java/ai/loomspan/internal/skill/ExecutionConfigurationParserTest.java`.
- **What it proves**: API keys, sensitive headers and Gemini references resolve only from a copied supplied map; missing, null, blank or unused entries fail with field paths but no secret text. A same-named `Environment` property never fills a missing map entry. Repeated reference names use the same value. Caller map mutation after method entry cannot alter the candidate.
- **Fixtures/data**: Small YAML documents for each credential form and a mutable map; distinct sentinel secrets.
- **Mocks**: `MockEnvironment`; no provider client.
- **Affected surface**: Application API and configuration behavior.
- **Compatibility expectation**: New protected host-map operation; existing environment overload retained.

### 2) StrictCandidateAndDefaultParity

- **Type**: unit/integration.
- **Location**: `ExecutionConfigurationParserTest.java`, `src/test/java/ai/loomspan/autoconfigure/LoomspanPropertiesTest.java`.
- **What it proves**: Equivalent file and explicit candidates have matching effective driver/options/retry/session/trace defaults. Explicit candidate does not inherit deployment connection/model fields. Direct API keys, headers and Gemini credential contents in YAML, process fields, unknown fields and duplicate keys fail. Skill-only preparation copies the active complete configuration.
- **Fixtures/data**: Paired file-bound and explicit YAML configurations with partial optional fields; startup properties deliberately containing different provider values.
- **Mocks**: Environment/property binder only.
- **Affected surface**: Configuration and manifest behavior.
- **Compatibility expectation**: File and existing reference paths protected; direct-secret/process candidate rejection protected.

### 3) InMemoryVertexCredentialsPrepareLocally

- **Type**: unit/integration.
- **Location**: `src/test/java/ai/loomspan/internal/springai/SpringAiProviderIntegrationTest.java` and parser test.
- **What it proves**: `gemini.credentials-json-ref` uses supplied JSON, rejects malformed content and mutually exclusive URI/JSON sources before publication, sanitizes errors, closes partial resources, and makes no model request. File `credentials-uri` and existing `credentials-ref` still work.
- **Fixtures/data**: Synthetic service-account JSON suitable for local SDK parsing; malformed JSON sentinel.
- **Mocks**: Fake provider transport/client construction counters; no network.
- **Affected surface**: Configuration behavior and internal provider preparation.
- **Compatibility expectation**: Existing URI path protected; new in-memory path added.

### 4) PreparedCandidateOwnsFullGeneration

- **Type**: integration.
- **Location**: `src/test/java/ai/loomspan/internal/skill/SkillGenerationManagerTest.java`, `SkillReloaderTest.java`.
- **What it proves**: A and B candidates differ in credentials, alias, retry, timeout, depth, quotas, attachments and trace persistence. Invalid B leaves A active; abandoned B closes new clients; publication makes B visible at handoff; A clients remain live while A owners exist; final release closes A; shutdown follows the existing budget and closes as specified.
- **Fixtures/data**: Two complete candidate configurations and close-tracking fake clients; barriers for overlap.
- **Mocks**: Runtime factory/provider transports, not publication state.
- **Affected surface**: Application API and internal lifecycle.
- **Compatibility expectation**: Captured generation and cleanup contracts protected.

### 5) RootAndDescendantsNeverMixVersions

- **Type**: integration.
- **Location**: `src/test/java/ai/loomspan/internal/skillapi/SkillGenerationExecutionIntegrationTest.java` plus the closest mission/parallel/retry integration fixtures.
- **What it proves**: Capture before conversion and validation; queued work selects at actual handoff; admitted delayed root, nested call, parallel branch, provider retry and later provider call retain A's full settings and client after B publication. New root uses B. `AdmittedSkillInvocation.generationId()` remains accurate for Sidecar mapping.
- **Fixtures/data**: Deterministic latches, fake responses and distinct A/B settings; no billable provider calls.
- **Mocks**: Fake provider transport and clock/latches where existing patterns support them.
- **Affected surface**: Application API, internal execution, ephemeral diagnostics.
- **Compatibility expectation**: Existing handoff and physical lifetime protected.

### 6) NoSecretReadbackOrDiagnosticDisclosure

- **Type**: unit/integration.
- **Location**: parser test, public publication integration and trace contract tests (`ExecutionTraceContractTest` or closest current trace test).
- **What it proves**: Authored `yaml()`, `toString()`, candidate catalog/snapshot, validation issues, preparation failures, structured trace events and ordinary logs contain no known credential sentinel. Current-run trace still records accurate generation and limits. Do not assert generic scrubbing of untrusted provider response bodies; the test targets framework-controlled material.
- **Fixtures/data**: Unique sentinel for API key, header and JSON; captured log/trace sink.
- **Mocks**: Fake provider and throwing credential/client constructor whose message could carry an input sentinel.
- **Affected surface**: Application API and ephemeral diagnostics.
- **Compatibility expectation**: Protected security boundary and current-run diagnostic coherence.

### 7) PublicSurfaceAndOnlyOneSpi

- **Type**: architecture.
- **Location**: `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`.
- **What it proves**: The one new `SkillReloader` signature is explicitly allowlisted; it exposes only JDK and `ai.loomspan.api` types; no new top-level API type or supported SPI appears.
- **Fixtures/data**: Updated exact signature allowlist.
- **Mocks**: None.
- **Affected surface**: Application API and Supported SPI.
- **Compatibility expectation**: Existing signatures preserved, boundary remains closed.

### 8) SidecarInstalledSnapshotAcceptance

- **Type**: cross-repository integration, owned by Sidecar PR 7.1.
- **Location**: `../loomspan-sidecar/src/test/java/ai/loomspan/sidecar/configuration/RuntimeConfigurationIntegrationTest.java` and execution integration tests.
- **What it proves**: With deployment provider config absent, Sidecar decrypts a retained credential and calls only the public framework overload; published A/B roots correlate with the right durable snapshot and credential. Override-off file source, override-on no-fallback, restart and rollback are Sidecar-owned cases. Tests resolve the installed framework snapshot rather than rebuilding framework as part of Sidecar.
- **Fixtures/data**: Test encryption key, isolated database, two credential versions and distinct snapshot IDs.
- **Mocks**: Fake provider; real supported framework API.
- **Affected surface**: Application API and Sidecar persisted behavior.
- **Compatibility expectation**: Companion PR owns intentional durable schema changes and development-data reset documentation.

## Authoring Claims Requiring Evidence

- A candidate may use host-supplied reference values without changing Spring Environment: tests 1, 4 and 8.
- Authored/readback YAML and ordinary diagnostics never include resolved secrets: test 6.
- Validation checks syntax and skill/model relationships; preparation resolves secrets and constructs clients without model invocation: tests 1, 3 and 4.
- New roots select the new generation at handoff; descendants and retries keep old clients and settings: test 5.
- File startup, environment references and defaults remain supported; environment changes need restart: test 2 plus startup binding tests.
- The documentation's process/execution table matches the production-read audit and parser rejection tests. Rotation order and provider-side revocation are operational recommendations/limits, not framework-enforced guarantees.

## How to Run

From the framework repository on Windows:

```powershell
.\mvnw.cmd -B -ntp -Dtest=ExecutionConfigurationParserTest,SkillGenerationManagerTest,SkillReloaderTest,SkillGenerationExecutionIntegrationTest,LoomspanPublicSurfaceArchitectureTest test
.\mvnw.cmd -B -ntp test
.\mvnw.cmd -B -ntp install
```

Run focused provider/trace integration classes added or modified by implementation as part of the first command or separately if Maven test selection requires it. No live provider credentials or billable calls are required. After the snapshot install, run Sidecar's focused configuration/execution integration tests and `..\loomspan-sidecar\mvnw.cmd -B -ntp test` from the Sidecar directory, recording exact commands and dependency version. Per its ticket, Maven Central release publication and the later Sidecar released-artifact pin are separate release steps; do not claim those checks passed before they occur.

## Exit Criteria

- [ ] The public-only red test fails before implementation for the missing operation, then passes.
- [ ] All focused and full framework tests pass; `LoomspanPublicSurfaceArchitectureTest` passes after production type changes.
- [ ] Map mode has no Environment fallback and never mixes credential versions; file-only and reference-only behavior remain protected.
- [ ] All execution-affecting production reads are inventoried and either captured or explicitly classified as process infrastructure; complete-settings overlap tests pass.
- [ ] Invalid or abandoned candidates leave active generation and provider resources correct; no validation/preparation model send occurs.
- [ ] Framework-controlled public readback, diagnostics and errors exclude secret sentinels; current-run trace correlation stays useful and accurate.
- [ ] Updated authoring guidance is supported by focused executable evidence, with coverage table updated where scope changed.
- [ ] Framework snapshot is installed and Sidecar supplies its own supported-API integration evidence before final framework release checks.
- [ ] No optional non-automatable check is a completion gate; no such check is currently identified.
