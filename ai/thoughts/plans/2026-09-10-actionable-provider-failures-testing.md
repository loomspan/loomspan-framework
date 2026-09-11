# Actionable Provider Failures Testing Plan

## Change Summary

- Recognize typed OpenAI SDK HTTP failures using the existing HTTP classification/retry path.
- Generate one conservative, identity-rich framework explanation; attach it to every existing failed-attempt diagnostic payload and log it at WARN only for the terminal attempt.
- Make safe client-construction failures name the relevant connection property without exposing configured values or unsafe causes.
- Let Console follow the canonical terminal failure's final-attempt link to the existing attempt diagnostic content; do not add a Console classifier or duplicate failure record.
- Update the same-version Java/Go fixture contract and author-facing connection/trace guidance.

## Impacted Areas

- Provider exception translation and diagnostic capture: `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java`.
- Framework guidance and terminal WARN ownership: new internal `ProviderFailureGuidance` plus `ProviderAttemptCallAdvisor.java`.
- Safe startup construction message: `NamedAiConnectionRegistry.java`.
- Attempt trace ordering, retry decisions, usage accounting, and terminal linkage: `DefaultExecutionTraceRecorder`, `ProviderRetryDecider`, `LoomspanSession`, and their existing tests (behavior preserved, not parallel implementations).
- Console terminal-attempt validation, derived indexes, query/browser/MCP DTOs, and exact content references: `loomspan-console/internal/traceanalysis`, `internal/browserapi`, and `internal/mcpadapter`.
- Failure panel and diagnostic renderer: `TraceExplorer.tsx`, `TraceFailureFocus.tsx`, `TraceAttemptDiagnostics.tsx`, and `api/contracts.ts`.
- Cross-language corpus: `ConsoleTraceFixtureCorpusTest`, `loomspan-console-fixtures`, Go fixture tests, and browser/MCP semantic fixtures.
- Root and skill-authoring documentation: `README.md`, `model-selection-and-connections.md`, and `traces-and-debugging.md`.

## Risk Assessment

- **High — retry regression from OpenAI recognition**: OpenAI 429/503 currently fall through to `UNKNOWN`/no retry; after recognition they must use the intentionally existing transient policy. Conversely, 401/403/404 must stay permanent. Parameterize status coverage around the exact status set.
- **High — warning cardinality**: logging before the retry branch would emit one WARN per failed attempt; logging recovered attempts would create false terminal alerts. Capture output for recovery, permanent failure, and exhaustion.
- **High — misleading diagnosis**: 401 must not say “missing key,” connectivity must not say “bad URL,” and 404 must not say “model does not exist.” Assert prohibited as well as required wording.
- **High — canonical linkage drift**: Console must use `ERROR_RECORDED.attemptId`/`retrySequenceId` validated against the final failed attempt and `TRACE_COMPLETED.terminalFailureId`; it must reject partial, retrying, or non-final links as before.
- **Medium — diagnostic ordering/bounds**: stack must remain first, framework guidance second, provider evidence afterward, with fixed UTF-8 bounds and no loss of provider content/truncation facts.
- **Medium — sensitive output**: framework-created WARN/startup text must contain identity and property names, never configured API keys, headers, base URL values, credential paths, raw provider bodies, or unsafe construction causes. Raw provider trace evidence remains governed by existing bounds/sensitivity guidance; general scrubbing is explicitly out of scope.
- **Medium — current-version projection coherence**: Java, Go, browser, MCP, TypeScript, and fixtures must agree on the optional linked-attempt content reference. No old/new dual behavior is required.
- **Low — performance**: guidance is bounded and constant-time; Console should fetch the attempt payload only for a selected failure and continue using bounded range traversal.
- **Protected compatibility paths**: closed `ai.loomspan.api` signatures; documented connection/model property shapes/defaults; exact retry ownership/status semantics; successful/recovered execution accounting; canonical terminal failure identity/linkage; exact unequal-release rejection.
- **Intentionally changed internal/current-run paths**: OpenAI HTTP failures become recognized normalized failures; failed-attempt diagnostics gain a guidance element; the derived failure projection gains an optional attempt content reference. These replace no supported old behavior and require no shim.
- **Authoring claims requiring evidence**: terminal WARN timing/cardinality; identity/property names; ambiguity rules; trace diagnostic order; direct Console failure-to-attempt access; existing sensitivity limitations.

## Existing Test Coverage

- `SpringAiProviderIntegrationTest` covers Google HTTP/transport failures, Anthropic 503/`Retry-After`, Ollama 429, OpenRouter completion errors, timeout/cancellation distinctions, body bounds, and response closure, but not OpenAI SDK HTTP exceptions (`SpringAiProviderIntegrationTest.java:49-245`).
- `ModelAttemptCallAdvisorIntegrationTest` covers exact physical attempts, retry identity/order, provider diagnostic preservation, recovery without `ERROR_RECORDED`, exhaustion, usage, and final registration (`ModelAttemptCallAdvisorIntegrationTest.java:335-411,413-486,488-573,716-771`). It does not cover provider WARN output or framework guidance.
- `LoomspanSessionTest#linksTheCanonicalTerminalErrorToTheRegisteredFinalProviderAttempt` protects wrapped-cause linkage (`LoomspanSessionTest.java:125-141`).
- `SensitiveConnectionDataRedactionTest` protects omission of configured secrets/transport values and safe identity (`SensitiveConnectionDataRedactionTest.java:35-103`), but does not require a `base-url` property path in the construction failure.
- Go `failures.go` tests reject incomplete/invalid terminal links and the query/range suites protect opaque references. They do not expose the final attempt payload from a failure query.
- `TraceAttemptDiagnostics` and `TraceExplorer` tests protect exact ranged loading, inert rendering, failure focus, and no implicit raw reads. The failure focus currently cannot reach attempt diagnostics.
- The recovered-provider fixture protects attempt-local stack/provider evidence and exact Java-to-Go range behavior, but no terminal provider failure fixture carries framework guidance.

## Bug Reproduction / Failing Test First

- **Name**: `terminalOpenAiAuthenticationFailureIsActionableInWarningAndAttemptTrace`
- **Type**: integration-style unit test around the real advisor/translator with in-memory session state
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`
- **Arrange**: Create an OpenAI `ProviderConnectionRuntime` whose model throws a mocked/SDK-built `OpenAIServiceException` with HTTP 401, bounded provider evidence, and retry policy enabled. Use a `ModelTraceContext` with framework model `support-model`, connection `primary-openai`, driver `OPENAI`, and provider model `gpt-example`; capture logging output.
- **Act**: Execute one chat call through `ProviderAttemptCallAdvisor` and collect `MODEL_ATTEMPT_FAILED` records plus captured WARN output.
- **Assert**: One provider call; permanent `AUTHENTICATION`; `DO_NOT_RETRY`; exactly one WARN naming the four identities and `loomspan.connections.primary-openai.api-key`; no claim that the key is missing; diagnostics ordered `JAVA_STACK_TRACE`, `LOOMSPAN_PROVIDER_GUIDANCE`, `PROVIDER_ERROR`; the guidance diagnostic text exactly equals the actionable WARN message; provider evidence remains available.
- **Expected failure (pre-fix)**: OpenAI SDK HTTP exceptions translate to `UNKNOWN`, no WARN is emitted, and the failed-attempt payload contains only the Java stack (no framework guidance).

## Tests to Add/Update

### 1) `translatesOpenAiServiceExceptionsThroughExistingHttpPolicy`

- **Type**: parameterized unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/springai/SpringAiProviderIntegrationTest.java`
- **What it proves**: OpenAI 401→permanent/authentication, 403→permanent/authorization, 404→permanent/invalid-request, 429→transient/rate-limited, and 503→transient/server-error; `Retry-After`, type, code, bounded body, and truncation are preserved.
- **Fixtures/data**: Mocked `OpenAIServiceException` instances with SDK `Headers`, `JsonValue` bodies, optional type/code, and representative statuses; include an over-limit body boundary case if the SDK value can be built cheaply.
- **Mocks**: Mock only the external SDK exception accessors; exercise the real translator returned by `SpringAiProviderIntegration#create`.
- **Affected surface**: Internal implementation and ephemeral diagnostics.
- **Compatibility expectation**: Intentional correction authorized by the ticket; retryable statuses adopt the existing retry semantics, not a new policy.

### 2) `formatsConservativeGuidanceForNormalizedFailureCategories`

- **Type**: parameterized unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/provider/ProviderFailureGuidanceTest.java`
- **What it proves**: Every relevant category produces bounded text with framework model, connection, driver, provider model, and appropriate literal property paths. Authentication says rejected/check credentials, not missing; 404/invalid request says endpoint/model/access may be involved, not that a model is nonexistent; connectivity says reachability/configuration should be checked, not that the URL is proven wrong.
- **Fixtures/data**: `ModelExecutionIdentity` values for OpenAI, Anthropic, Gemini, and Ollama plus normalized failure details for authentication, authorization, connectivity, timeout/server unavailable, invalid request/404, rate limit, and unknown.
- **Mocks**: None.
- **Affected surface**: Internal implementation and ephemeral diagnostics.
- **Compatibility expectation**: Current-run diagnostic coherence.

### 3) `terminalProviderWarningOccursOnceAndUsesRecordedGuidance`

- **Type**: integration-style unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`
- **What it proves**: A permanent failure logs one WARN immediately; three transient failures log only once on `ATTEMPTS_EXHAUSTED`; a transient failure followed by success logs no terminal WARN. Every failed attempt still receives guidance, while only the final failure is registered for canonical linkage. WARN text and final-attempt guidance text are identical.
- **Fixtures/data**: Existing in-memory model/session/builders with zero backoff, normalized 401 and 503 failures, and `CapturedOutput`.
- **Mocks**: Lambda `ChatModel` and translator fixtures; real retry decider, state service, trace recorder, and usage service.
- **Affected surface**: Internal implementation and ephemeral diagnostics.
- **Compatibility expectation**: Preserve retry/linkage/accounting; add terminal visibility without per-attempt WARN duplication.

### 4) `failedAttemptDiagnosticsRemainStackGuidanceProviderOrderedAndBounded`

- **Type**: integration-style unit
- **Location**: `loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/chat/ModelAttemptCallAdvisorIntegrationTest.java`
- **What it proves**: The existing record remains singular; descriptor order is Java stack, guidance, then exact provider diagnostics; each descriptor reports valid content type, truncation, and capture bound; recovered failures keep their attempt evidence and create no canonical error.
- **Fixtures/data**: Extend the existing multi-provider-diagnostic recovery fixture at lines 351-405 rather than create a parallel harness.
- **Mocks**: Existing lambda model and fixed translator.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: Preserve stack-first and exact provider evidence while adding current-run guidance.

### 5) `constructionFailureNamesSafeBaseUrlSettingWithoutLeakingValuesOrCause`

- **Type**: unit/context integration
- **Location**: `NamedAiConnectionRegistryTests.java` and `SensitiveConnectionDataRedactionTest.java`
- **What it proves**: Generic construction failure names connection, driver, and `loomspan.connections.sensitive.base-url`; the configured URL/key/header/credential URI and unsafe original exception/cause remain absent. Existing exact Gemini credential and retry-ownership errors remain unchanged.
- **Fixtures/data**: Existing sentinel properties and mocked integration construction failure; optionally a real malformed URI case when deterministic across the pinned clients.
- **Mocks**: `SpringAiProviderIntegration` for the deterministic unsafe-cause test.
- **Affected surface**: Configuration behavior and internal implementation.
- **Compatibility expectation**: Protected configuration shape; improved startup diagnostic only.

### 6) `terminalFailureProjectsOnlyItsValidatedFinalAttemptContentReference`

- **Type**: Go unit/integration
- **Location**: `loomspan-console/internal/traceanalysis/failures_test.go`, `query_facts_test.go`, and related index/record-facts tests
- **What it proves**: A valid terminal error linked to the last non-retrying failed attempt gains `providerAttemptContentRef`; ordinary/non-provider failures omit it. Partial links, links to `RETRY`, wrong retry sequence, missing attempts, and non-final attempts remain rejected. Record-local failure facts do not invent a cross-record reference.
- **Fixtures/data**: Minimal current-development traces covering each link shape and an attempt payload ID.
- **Mocks**: In-memory artifact processor/store only; no network.
- **Affected surface**: Ephemeral diagnostics and internal implementation.
- **Compatibility expectation**: Additive current-run projection based only on an authoritative protected link.

### 7) `failureAdaptersPreserveOptionalProviderAttemptContentReference`

- **Type**: Go adapter contract
- **Location**: `loomspan-console/internal/browserapi/trace_analysis_test.go`; `loomspan-console/internal/mcpadapter/traces_test.go` and semantic contract tests
- **What it proves**: Browser and MCP failure DTOs expose the opaque linked-attempt reference when present, omit it when absent, and do not add classification or copy raw diagnostic text.
- **Fixtures/data**: `FailureSummary` values with and without the optional reference.
- **Mocks**: Existing fake trace-analysis service.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: Atomic Java-to-Go/browser/MCP update; no fallback.

### 8) `terminalProviderFailurePanelLoadsLinkedAttemptGuidance`

- **Type**: React component integration
- **Location**: `loomspan-console/web/src/observability/TraceExplorer.test.tsx` and `loomspan-console/web/src/observability/TraceAttemptDiagnostics.test.tsx`
- **What it proves**: Selecting the canonical terminal failure displays the linked attempt diagnostics in the failure panel, recognizes the guidance label, renders the exact framework text inertly, traverses contiguous ranges, and works for target/imported evidence. Non-provider failures retain current presentation; artifact expiry invokes the existing unavailable path; raw records are never fetched.
- **Fixtures/data**: Failure with `providerAttemptContentRef`, ranged JSON containing stack/guidance/provider descriptors, absent-reference failure, and rejected/missing artifact responses.
- **Mocks**: Existing browser API mocks (`getTraceFailures`, `getContentRange`, scope verifier); explicitly assert `getRawRecordRange` is not called.
- **Affected surface**: Ephemeral diagnostics.
- **Compatibility expectation**: Current-run diagnostic coherence; Console presents framework-owned content and performs no classification.

### 9) `terminalProviderFailureActionableFixtureRoundTrips`

- **Type**: cross-language fixture integration
- **Location**: `ConsoleTraceFixtureCorpusTest.java`; `loomspan-console/internal/traceanalysis/fixture_corpus_test.go`; relevant browser/MCP semantic fixture tests; generated `loomspan-console-fixtures/traces/terminal-provider-failure-actionable.ndjson`
- **What it proves**: Java writes one failed attempt with complete identity, ordered guidance/provider evidence, one linked canonical error, and terminal completion; Go accepts it, projects the linked reference, and returns byte-exact diagnostic ranges. Exact unequal-release rejection still passes with the unchanged repository release marker.
- **Fixtures/data**: One generated terminal authentication or ambiguous 404 case; expected summaries/ranges committed with LF endings.
- **Mocks**: Existing fixture generators/readers only.
- **Affected surface**: Ephemeral diagnostics and Java-to-Go boundary.
- **Compatibility expectation**: Atomic current-version update; no historical fixture, fallback reader, or compatibility shim.

### 10) `publicSurfaceAndDocumentationEvidenceRemainCoherent`

- **Type**: architecture plus documentation/source review
- **Location**: `LoomspanPublicSurfaceArchitectureTest.java`; updated README and checked-in skill-authoring topics
- **What it proves**: No new `ai.loomspan.api` type/signature, SPI, or conditional bean; documented WARN, retry, trace, Console, ambiguity, and sensitivity claims are supported by tests 1–9.
- **Fixtures/data**: Existing architecture allowlists and updated focused topic text.
- **Mocks**: None.
- **Affected surface**: Application API, Supported SPI, configuration behavior, and documentation.
- **Compatibility expectation**: Protected public/configuration paths unchanged.

## How to Run

No provider credentials, live network access, external Console target, or special Spring profile should be required. Use mocked SDK exceptions, in-memory trace storage, deterministic zero backoff, and committed fixtures.

From the repository root on Windows:

```powershell
.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=SpringAiProviderIntegrationTest,ProviderFailureGuidanceTest,ModelAttemptCallAdvisorIntegrationTest,NamedAiConnectionRegistryTests,SensitiveConnectionDataRedactionTest,LoomspanSessionTest,LoomspanPublicSurfaceArchitectureTest" -DfailIfNoTests=false
.\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false
.\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -DfailIfNoTests=false
git diff --check
```

From `loomspan-console`:

```powershell
go test ./internal/traceanalysis ./internal/browserapi ./internal/mcpadapter
go test ./...
go run ./internal/buildtool verify
```

For focused web feedback from `loomspan-console/web` (after dependencies are installed by the repository workflow):

```powershell
npm test -- TraceExplorer.test.tsx TraceAttemptDiagnostics.test.tsx
npm run typecheck
```

Final repository verification from the root:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

## Exit Criteria

- [x] The failing behavior test is observed red before implementation and green afterward.
- [x] OpenAI 401/403/404/429/503 translate through the existing status/category/retry policy with bounded evidence and `Retry-After` preservation.
- [x] Rejected credentials, access denial, connectivity, endpoint/model ambiguity, and unknown failures produce accurate property-oriented wording without prohibited root-cause claims.
- [x] Recovered retries emit zero terminal WARNs; immediate permanent failure and exhausted retries each emit exactly one terminal WARN; attempt records remain one per failed physical send.
- [x] The exact WARN guidance is present on the final `MODEL_ATTEMPT_FAILED`; all failed attempts retain stack-guidance-provider ordering, bounds, identity, and original provider evidence.
- [x] Existing response-only model-call/usage accounting, quota accounting, retry backoff/attempt ownership, canonical failure identity, and final-attempt linkage tests pass.
- [x] Startup malformed construction diagnostics name `loomspan.connections.<name>.base-url` while sentinel values and unsafe causes remain absent.
- [x] The future-scrubbing TODO exists at the provider diagnostic capture/emission boundary, and no scrubber/content-classification framework is introduced.
- [x] Go accepts and projects only a validated final-attempt content reference; browser/MCP/TypeScript contracts agree; Console failure details render framework guidance as inert linked evidence with existing range/security behavior.
- [x] The regenerated fixture corpus is clean on a second non-regenerating run, uses LF, and exact unequal-release rejection still passes. `consoleCompatibilityVersion` remains the repository release string and no legacy reader exists.
- [x] `LoomspanPublicSurfaceArchitectureTest` confirms no public API/SPI delta; configuration shapes/defaults and manifest invocation contracts are unchanged.
- [x] Updated README and skill-authoring claims are established by the cited source/tests/fixture, remain self-contained and conservative, and require no routing/coverage-table change.
- [x] Full Maven, Go, web typecheck/test/build verification and `git diff --check` pass.
- [x] No non-automatable developer check is required for completion; an optional manual run of the travel demo with the placeholder key may be reported as a convenience only, not a gate.
