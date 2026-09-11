## Code Review Findings

No actionable findings remain after the review/fix loop in this context.

## Findings Resolved in This Context

- **[P2] Name exact Gemini Vertex AI credential settings.** `ProviderFailureGuidance.credentialProperty` produced the malformed text `loomspan.connections.<name>.gemini Vertex AI credential settings`, so a Gemini Vertex authentication failure did not point developers to usable configuration keys. The guidance now names `gemini.project-id`, `gemini.location`, and `gemini.credentials-uri`, and `ProviderFailureGuidanceTest` protects the exact paths.
- **[P2] Exercise the OpenAI authentication path end to end.** The planned central acceptance test was absent: translator status tests and generic advisor warning tests did not prove that a typed OpenAI 401 becomes a permanent one-attempt failure whose exact safe guidance is shared by WARN and the failed-attempt trace. `terminalOpenAiAuthenticationFailureIsActionableInWarningAndAttemptTrace` now runs the real translator through the advisor and verifies classification, retry decision, diagnostic order, identity/property wording, provider evidence, one warning, and omission of placeholder/body values from guidance.
- **[P3] Do not label linked attempt evidence with the error record sequence.** `TraceFailureFocus` passed the canonical `ERROR_RECORDED` sequence into `TraceAttemptDiagnostics`, yielding an inaccurate accessible label for content loaded from the linked provider-attempt payload. The reusable component now accepts an explicit accessible label, and the failure panel identifies the section as linked provider-attempt diagnostics without inventing an attempt sequence.

## Open Questions and Assumptions

- None. The ticket authorizes an atomic current-version diagnostic projection and no compatibility shim.

## Verification Results

- PASS — `.\\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=SpringAiProviderIntegrationTest,ProviderFailureGuidanceTest,ModelAttemptCallAdvisorIntegrationTest,NamedAiConnectionRegistryTests,SensitiveConnectionDataRedactionTest,LoomspanSessionTest,LoomspanPublicSurfaceArchitectureTest" -DfailIfNoTests=false` (58 tests after fixes).
- PASS — `go test ./internal/traceanalysis ./internal/browserapi ./internal/mcpadapter`.
- PASS — `npm test -- TraceExplorer.test.tsx TraceAttemptDiagnostics.test.tsx` (78 tests after fixes).
- PASS — `npm run typecheck`.
- PASS — `go run ./internal/buildtool verify` (46 web test files / 497 tests, coverage gates, web build, and Go verification).
- PASS — `go test ./...` after the build tool restored the repository-managed web dependencies.
- PASS — `.\\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=ConsoleTraceFixtureCorpusTest" "-Dloomspan.console.fixtures.regenerate=true" "-DfailIfNoTests=false"`.
- PASS — `.\\mvnw.cmd -pl loomspan-spring-boot-starter test "-Dtest=ConsoleTraceFixtureCorpusTest" "-DfailIfNoTests=false"` (clean non-regenerating corpus check).
- PASS — `.\\mvnw.cmd --batch-mode --no-transfer-progress clean verify` (1,055 tests).
- PASS — `git diff --check`.
- FAIL, then recovered — initial `go test ./...` encountered a missing optional file below `web/node_modules`; `go run ./internal/buildtool verify` performed the repository-managed dependency installation, and the exact Go command then passed.
- FAIL, invocation-only — initial unquoted `.\\mvnw.cmd -pl loomspan-spring-boot-starter test -Dtest=ConsoleTraceFixtureCorpusTest -Dloomspan.console.fixtures.regenerate=true -DfailIfNoTests=false` was parsed by PowerShell as an invalid lifecycle phase; the quoted equivalent above passed.

## Requirements and Plan Conformance

- **Implemented:** Typed OpenAI 401/403/404/429/503 failures reuse the existing HTTP classification and retry policy, retain bounded provider evidence and Retry-After, and preserve optional provider type/code metadata.
- **Implemented:** Every failed physical attempt records stack, framework guidance, then provider diagnostics. Recovered retries produce no terminal warning; permanent and exhausted outcomes produce one warning using the same guidance text.
- **Implemented:** Guidance names framework model, connection, driver, provider model, and conservative property-oriented actions without treating authentication, connectivity, or HTTP 404 as a proven root cause. Generic construction failures safely name the connection base-url property without retaining unsafe causes or configured values.
- **Implemented:** Console derives the optional attempt content reference only after validating the canonical terminal failure/final-attempt link, preserves it through browser/MCP/TypeScript contracts, and reuses bounded inert attempt-diagnostic rendering in failure details.
- **Implemented:** The Java-generated terminal-provider fixture round-trips through Go with the unchanged exact compatibility release marker. The writer, analyzer, adapters, UI, fixtures, and documentation move atomically.
- **Implemented:** The targeted future-scrubbing TODO is at the provider diagnostic capture boundary; no scrubber or parallel classification system was introduced.
- **Missing/partial:** None.
- **Safe deviations:** The tests use focused methods and existing harnesses rather than reproducing every testing-plan name verbatim. Behavior and risk coverage are equivalent or stronger after the added end-to-end OpenAI authentication test.
- **Compatibility review:** The closed `ai.loomspan.api` allowlist and documented configuration shapes/defaults are unchanged. The new technically-public type remains below `ai.loomspan.internal` and is explicitly classified as internal by `LoomspanPublicSurfaceArchitectureTest`; no SPI or conditional bean was added. The trace diagnostic and optional failure reference are ephemeral current-version additions, so the no-shim decision and unchanged exact compatibility marker are consistent with repository policy.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Skill authors and operators need the new warning timing, conservative property guidance, attempt diagnostic ordering, sensitivity limitation, and failure-to-attempt Console path.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/README.md`; `mental-model.md`; `source-verification.md`; `model-selection-and-connections.md`; `traces-and-debugging.md`.
- **Evidence checked:** `ProviderFailureGuidance`, `ProviderAttemptCallAdvisor`, `SpringAiProviderIntegration`, startup redaction tests, Java fixture generation, Go terminal-link/query/adapters, and React range/inert-rendering tests.
- **Drift classification:** **aligned** for the final checked-out source, focused tests, fixture corpus, and checked-in authoring guidance. The installed `loomspan-docs` package advertises `0.1.0-SNAPSHOT` while the checkout is `1.0.0-beta.3-SNAPSHOT`; version-sensitive conclusions therefore use the matching checked-in knowledge base and executable evidence, not the installed copy.
- **Coverage table:** Current; the existing model-selection and traces/debugging routes already own this material.
- **LLM-first usability:** Pass. The changed topics are self-contained, conservative, and distinguish framework behavior from sensitive provider evidence limitations.

## Residual Risks and Optional Developer Checks

- None required. A manual travel-demo run with a placeholder key would be illustrative only; the typed end-to-end test supplies deterministic acceptance evidence.

## Disposition

- **Candidate clean; fresh review required** — all findings found in this context were fixed, and this context changed implementation artifacts.
