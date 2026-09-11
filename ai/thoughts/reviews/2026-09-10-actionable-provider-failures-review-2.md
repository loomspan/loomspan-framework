## Code Review Findings

No remaining actionable findings after the fixes described below.

## Findings Resolved in This Context

### [P1] Return a readable reference for inline failed-attempt diagnostics

- **Location:** `loomspan-console/internal/traceanalysis/query_facts.go:450`
- **Evidence:** Terminal-attempt validation originally retained only the attempt's payload ID. Loomspan assigns that ID only when record data is chunked; the ordinary, bounded `MODEL_ATTEMPT_FAILED` diagnostic remains inline and therefore has no payload ID. The resulting failure summary omitted `providerAttemptContentRef` even though the validated final attempt contained the provider guidance and evidence.
- **Trigger:** Query a terminal provider failure whose attempt diagnostic fits in the normal inline record-data limit, including the committed `terminal-provider-failure-actionable.ndjson` fixture.
- **Impact:** Console failure details could not load the guidance for the common failure shape, so the ticket's end-to-end actionable-diagnostics path was incomplete.
- **Recommendation and fix:** Retain the validated attempt's owner sequence as well as any payload ID, use the existing record-data content-reference format for inline diagnostics, and keep the envelope reference for chunked diagnostics. `failures.go:149`, `model.go:235`, and `query_facts.go:450` now implement that split without weakening canonical attempt-link validation. `service_test.go:1040` proves that the committed fixture projects a nonempty reference and that the referenced inline content can be read.

### [P2] Serialize OpenAI structured error bodies as actual JSON

- **Location:** `loomspan-spring-boot-starter/src/main/java/ai/loomspan/internal/springai/SpringAiProviderIntegration.java:297`
- **Evidence:** The capture path used `String.valueOf(JsonValue)` while labeling the diagnostic `application/json`. The pinned OpenAI SDK's `JsonValue.toString()` is a debug representation (for example, map-style `key=value` rendering), not a JSON serializer.
- **Trigger:** An OpenAI provider returns a structured response body, such as `JsonValue.from(Map.of("error", "provider detail"))`.
- **Impact:** Stored provider evidence could be syntactically invalid JSON despite its content type, making downstream inspection misleading and brittle.
- **Recommendation and fix:** Convert `JsonValue` to its structured Java value and serialize it through Loomspan's configured Jackson mapper. If conversion itself fails, serialize the debug rendering as a JSON string so the declared content type remains truthful. The focused Spring AI and advisor integration tests now use real `JsonValue` instances and assert exact JSON bytes.

The complete corrected repository state was re-reviewed after these changes. No additional correctness, compatibility, security/privacy, lifecycle, performance, observability, documentation, or maintainability findings remain.

## Open Questions and Assumptions

- None.

## Verification Results

- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=SpringAiProviderIntegrationTest,ProviderFailureGuidanceTest,ModelAttemptCallAdvisorIntegrationTest,NamedAiConnectionRegistryTests,SensitiveConnectionDataRedactionTest,LoomspanSessionTest,LoomspanPublicSurfaceArchitectureTest" -DfailIfNoTests=false` (58 tests)
- PASS — `npm test -- TraceExplorer.test.tsx TraceAttemptDiagnostics.test.tsx` (78 tests)
- PASS — `npm run typecheck`
- PASS — `go test ./internal/traceanalysis ./internal/browserapi ./internal/mcpadapter`
- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter clean test "-Dtest=SpringAiProviderIntegrationTest,ModelAttemptCallAdvisorIntegrationTest" -DfailIfNoTests=false` (21 tests)
- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=ConsoleTraceFixtureCorpusTest" "-Dloomspan.console.fixtures.regenerate=true" "-DfailIfNoTests=false"` (15 tests)
- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=ConsoleTraceFixtureCorpusTest" "-DfailIfNoTests=false"` (15 tests)
- PASS — `go run ./internal/buildtool verify` (web typecheck, 497 web tests with coverage, production build, and all Go packages)
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` (1,055 tests)
- PASS — `go test ./...`
- PASS — `.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=ProviderFailureGuidanceTest,SpringAiProviderIntegrationTest,ModelAttemptCallAdvisorIntegrationTest" "-DfailIfNoTests=false"` (25 tests after final test strengthening)
- PASS — `git diff --check`
- FAIL — `.\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false`: PowerShell passed the dotted property as a Maven lifecycle phase; quoting the `-D` arguments produced the passing regeneration run above.
- FAIL — `go test ./...`: the initial run found an incomplete pre-existing `web/node_modules` tree (`react-aria/filterDOMProps` missing). `go run ./internal/buildtool verify` restored dependencies, and the exact command then passed.
- FAIL — `.\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=SpringAiProviderIntegrationTest,ModelAttemptCallAdvisorIntegrationTest" -DfailIfNoTests=false`: the first serialization edit used an invalid multi-catch relationship (`JacksonException` is a `RuntimeException` subtype). The catch was simplified; the clean focused run and all later verification passed.

## Requirements and Plan Conformance

- **Implemented:** Startup failures name the framework model, connection, driver/provider, provider model, and safe property names without exposing configured secret values or raw causes. Runtime terminal failures emit one bounded framework-owned WARN, classify common provider failure families into actionable guidance, preserve provider diagnostics in the failed attempt, and expose those diagnostics from the Console failure-details path. Retryable intermediate attempts remain free of terminal WARN noise.
- **Implemented:** The Java trace writer/projector, fixture corpus, Go analyzer, browser and MCP adapters, TypeScript contract, React failure focus, and diagnostics viewer form a coherent end-to-end path for both inline and chunked attempt data. Tests cover authorization, timeout, rate limit, unavailable/overload/server, unknown classification, UTF-8-safe bounds, retry behavior, linking, imported traces, unavailable payloads, and inert rendering.
- **Partial:** None.
- **Missing:** None.
- **Safe deviations:** None material. The implementation reuses existing trace content-reference formats and attempt-diagnostics UI instead of introducing a parallel payload transport.
- **Compatibility review:** No supported application-facing Java API or SPI was added or changed. The new Java helper remains internal and is classified by `LoomspanPublicSurfaceArchitectureTest`; Console DTO additions are optional current-run diagnostic fields. No compatibility shim is warranted under the repository's closed public-surface policy and ephemeral diagnostic-format policy.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected
- **Rationale:** Skill authors and operators need the new safe startup/runtime guidance, retry-warning semantics, Console attempt-details workflow, and limitation that provider bodies may contain sensitive material.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/model-selection-and-connections.md`; `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md`
- **Evidence checked:** `NamedAiConnectionRegistry`, `ProviderAttemptCallAdvisor`, `ProviderFailureGuidance`, `SpringAiProviderIntegration`, `ExecutionJournalProjector`, the terminal-provider fixture and expected projection, focused Java/Go/React tests, and full repository verification.
- **Coverage table:** Current
- **LLM-first usability:** Pass
- **Drift classification:** The checked-in authoring documentation matches the checked-out `1.0.0-beta.3-SNAPSHOT` executable behavior. The separately installed `loomspan-docs` skill reports `0.1.0-SNAPSHOT`, so it was used for workflow guidance but not as evidence for version-sensitive behavioral claims.

## Residual Risks and Optional Developer Checks

- No live provider call was run because credentials and network behavior are not required for this ticket's deterministic acceptance criteria. SDK-native exception objects, committed traces, and end-to-end Console tests cover the relevant local contracts.
- Optional developer checks: None.

## Disposition

- **Candidate clean; fresh review required** — this context fixed one P1 and one P2 finding, then completed a clean internal re-review with repository-wide verification. A separate fresh step-5 context must validate the corrected candidate.
