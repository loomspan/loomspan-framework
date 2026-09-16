# PR 6.2 Public Skill Reload — Independent Code Review 1

## Code Review Findings

### [P2] Preserve the execution generation in cached Console trace responses

- **Location:** `loomspan-console/internal/artifact/acquire.go:213`
- **Evidence:** The trace processor extracts the validated `TRACE_STARTED.metadata.generationId`, but target acquisition retained the catalog metadata without copying this new fact. `browserapi.cachedTrace` also projected cached metadata to an observability trace without `GenerationID`.
- **Trigger:** Acquire a target trace, then request its trace detail or list while the target is unavailable and Console uses its installed-artifact fallback.
- **Impact:** The fallback returned an empty generation ID, so the same execution had an ID in live/finalized target diagnostics but lost it in cached Console diagnostics and the browser UI.
- **Recommendation:** Store the processor-validated ID for both target acquisitions and imports, project it through the cached trace response, and assert both paths.

No actionable findings remain after the fix and internal re-review.

## Findings Resolved in This Context

- **P2 — Cached trace generation loss:** `installStream` now stores the processor-validated generation ID for target and imported artifacts. `cachedTrace` copies it into the browser response. Added acquisition/lookup and offline detail/list fallback regression assertions. Focused and full Go suites pass.

## Open Questions and Assumptions

- None requiring developer direction. The ticket's Pipeline notes expressly authorize direct pre-1.0 API changes with no compatibility constructor or historical diagnostic reader.

## Verification Results

- PASS — `.\mvnw.cmd '-Dtest=SkillReloaderTest,PublicSkillReloadIntegrationTest,LoomspanPublicSurfaceArchitectureTest' test` (13 tests).
- PASS — `.\mvnw.cmd test` (1,142 tests).
- PASS — `go test ./internal/artifact ./internal/browserapi ./internal/traceanalysis ./internal/observability` (from `loomspan-console`).
- PASS — `go test ./...` (from `loomspan-console`).
- PASS — `npm run typecheck` (from `loomspan-console/web`).
- PASS — `npm test` (47 files, 521 tests; from `loomspan-console/web`).
- PASS — `npm run build:web` (from `loomspan-console/web`; existing chunk-size warning only).
- PASS — `git diff --check` (line-ending warnings only).
- FAIL — `.\mvnw.cmd -Dtest=SkillReloaderTest,PublicSkillReloadIntegrationTest,LoomspanPublicSurfaceArchitectureTest test`: PowerShell parsed the unquoted comma argument as syntax. The quoted invocation above passed.
- NOT RUN — `npm run test:e2e`: no changed browser interaction requires browser-level coverage beyond the exercised component and Go route tests; browser dependency availability was not established.

## Requirements and Plan Conformance

- **Implemented:** The closed allowlist contains `SkillReloader`, `PreparedSkillUpdate`, and `SkillReloadException`; the sole SPI remains `RestSkillHandler`. Catalogs and REST invocations carry IDs without internal signature leakage. The Spring bean is framework-owned. `SkillGenerationManager` prepares a complete frozen generation against fixed dependencies; the service captures a base ID, checks ownership/one-shot/staleness/shutdown, and publishes through the lifecycle gate. Current catalog and observability discovery read the active generation while old catalog objects remain immutable.
- **Implemented:** Root execution passes its captured generation ID through session creation to `TRACE_STARTED`, live snapshots, finalized trace DTOs, the Go processor, browser/MCP active views, and current fixtures. The REST handler receives a per-generation closure-supplied ID, not model input or a current-active lookup. Existing generation-capture, handoff, authorization, and full-suite regression tests pass. The review fix closes the cached-trace fallback path.
- **Implemented:** README and version-aligned Java API/skill-authoring guidance cover startup readiness, prepare-stage-publish, fixed handler configuration, fresh IDs, stale/repeated/shutdown errors, file stability, empty deletion, snapshots, and application-owned old-configuration retention.
- **Partial:** New tests directly cover delayed handoff, competing publication, preparation/publication overlap, and shutdown during preparation. The dedicated new reload tests do not themselves combine nested/parallel REST calls with publication or instrument the exact publish-before-shutdown ordering; pre-existing generation-binding/nested execution and lifecycle tests plus the reviewed one-authority implementation provide compositional coverage. This is residual test granularity, not an observed behavioral defect.
- **Missing:** None identified in implemented behavior.
- **Safe deviations:** The implementation retains existing internal trace/session test constructors. Production `LoomspanSessionRunner` uses the captured-ID constructor; the retained constructors have only test call sites and are not supported API shims. The diagnostic marker remains project-derived `1.0.0-beta.4-SNAPSHOT` for an unreleased coordinated checkout, as recorded in the plan.
- **Compatibility review:** `SkillCatalog` and `RestSkillInvocation` are supported Application API by the architecture allowlist; their direct ID additions are ticket-authorized breaks with no shim. `RestSkillHandler` remains the only Supported SPI. YAML/configuration syntax is unchanged. Candidate IDs are process-local and not serialized. Trace/REST/SSE/Console shapes are current-run diagnostic contracts updated atomically; exact-version rejection and no-legacy-reader policy remain intact. Internal and autoconfigure types are not new application extension points.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Authors need to know public generation activation preserves tree-local definitions/policy and a fixed REST handler receives a trusted captured ID. Trace guidance must explain the new diagnostic ID; YAML syntax does not change.
- **Documents reviewed:** `README.md`; `agent-skills/loomspan-docs/references/java-api/{README.md,catalog-and-validation.md,compatibility-and-boundaries.md,rest-skills.md,skill-reload.md}`; `agent-skills/loomspan-docs/references/skill-authoring/{README.md,mental-model.md,rest-skills.md,traces-and-debugging.md}`.
- **Evidence checked:** Production generation manager/reloader, REST invocation construction, session/trace/observation path, Java integration and architecture tests, Java/Go fixture corpus, Go validation and browser fallback tests.
- **Coverage table:** Current for the changed mental-model and REST topics; the Java API index routes to the new reload topic.
- **LLM-first usability:** Pass. Application staging is concentrated in the Java API topic, while authoring topics link to it and distinguish runtime guarantees from application-owned readiness/retention. Drift classification: **aligned** after the code and guidance update.

## Residual Risks and Optional Developer Checks

- An application with production-like external configuration storage may optionally exercise its own startup traffic gate and old-generation retention/cleanup policy. Loomspan intentionally provides no external safe-deletion signal, so repository tests cannot prove that application policy.
- Browser end-to-end automation was not run; route, component, type, and build checks passed. The new reload-specific concurrency tests are narrower than the complete matrix in the testing plan, as noted above.

## Disposition

**Candidate clean; fresh review required.** One P2 finding was fixed in this context, with no remaining actionable finding after re-review. Because implementation artifacts changed, another independent Step 5 context must validate the result.
