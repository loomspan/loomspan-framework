## Code Review Findings

### [P3] Document the intentional Java API migration in the compatibility guide

- **Location:** `agent-skills/loomspan-docs/references/java-api/compatibility-and-boundaries.md:20`
- **Evidence:** The supported-surface table called `SkillCatalog` only an immutable startup snapshot, although `PreparedSkillUpdate.snapshot()` and `SkillReloader.snapshot()` now return candidate/current catalogs. Its “Compatibility Changes in This Revision” section omitted the required `SkillCatalog.generationId()` and the three-component `RestSkillInvocation` break.
- **Trigger:** An application developer consults the routed compatibility guide while updating a catalog implementation or direct REST invocation construction.
- **Impact:** The guide leaves the required source migration and absence of old-signature shims unclear, and understates where a catalog value may come from.
- **Recommendation:** Describe all three catalog origins and state the two intentional API breaks and required recompilation. Add reload coverage to the Java API index.

## Findings Resolved in This Context

- [P3] Updated the compatibility table and migration section, including the no-shim decision, and added the reload topic to the Java API coverage table. Re-read the changed text against `SkillCatalog`, `RestSkillInvocation`, `SkillReloader`, `PreparedSkillUpdate`, the architecture allowlist, and focused integration tests; `git diff --check` is clean.

## Open Questions and Assumptions

- None. The ticket explicitly authorizes the development-time public API break and requires no legacy constructor or method.

## Verification Results

- PASS — `.\mvnw.cmd '-Dtest=LoomspanPublicSurfaceArchitectureTest,ApplicationApiValueTest,SkillReloaderTest,PublicSkillReloadIntegrationTest,FrameworkExecutionLifecycleTest' test` (45 tests).
- PASS — `.\mvnw.cmd test` (1,144 tests).
- PASS — `go test ./...` from `loomspan-console`.
- PASS — `npm run typecheck` from `loomspan-console/web`.
- FAIL — `npm test -- --run` from `loomspan-console/web`: one unchanged `TraceExplorer.test.tsx` call-count/timing assertion failed while 520 tests passed. This run overlapped the full Maven and Go suites.
- PASS — `npm test -- src/observability/TraceExplorer.test.tsx` from `loomspan-console/web` (73 tests).
- PASS — `npm test` from `loomspan-console/web` (521 tests).
- PASS — `npm run build:web` from `loomspan-console/web`.
- PASS — `git diff --check` after the documentation correction.

## Requirements and Plan Conformance

- **Implemented:** `SkillReloader` and internal candidate enforce owner, base, one-shot, and lifecycle cutoff; manager prepares complete immutable generations with fresh IDs; the catalog and REST invocation expose captured IDs. `DefaultSkillTemplate`/handoff/binding retain the generation through execution. Observability reads the active catalog per operation, while trace/live/finalized and Java/Go/TypeScript consumers carry the execution ID. The README and knowledge base cover application staging and retention. Focused and full suites pass.
- **Partial:** The testing plan proposed additional named nested/parallel reload-ID integration cases. The checked-out implementation has a per-generation REST invoker closure, PR 6.1 binding-based child inheritance, existing nested/parallel and authorization tests, a delayed public handoff test, and the full suite passes, but this exact composition is not tested as a single new scenario. This is a residual coverage limitation, not a demonstrated implementation defect.
- **Missing:** None of the ticket's runtime or public-surface requirements was found missing.
- **Safe deviations:** Generation IDs use a random process namespace plus monotonic ordinal instead of independent UUIDs. This guarantees non-reuse within the manager without implying content or chronology semantics to callers.
- **Compatibility review:** The closed `ai.loomspan.api` allowlist is expanded by three types. `SkillCatalog.generationId()` and the third `RestSkillInvocation` component are intentionally source/binary sensitive under the ticket's Pipeline notes; no old constructor/shim remains. `RestSkillHandler` stays the only supported SPI, with no internal signature leakage or bean-replacement contract. `autoconfigure` and `internal` are implementation; diagnostic formats are current-run only. The Java/Go fixture and exact-version checks pass.

## Skill-Authoring Documentation Impact

- **Assessment:** Affected.
- **Rationale:** Author-facing execution consistency, fixed REST handler selection, and current diagnostic identity change without new YAML syntax.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/{README,mental-model,rest-skills,traces-and-debugging,source-verification}.md`; `agent-skills/loomspan-docs/references/java-api/{README,compatibility-and-boundaries,skill-reload,catalog-and-validation,rest-skills}.md`; root `README.md`.
- **Evidence checked:** Public API and manager/runner/trace/observability source, focused reload and fixture tests, Java architecture tests, and Go/web consumer suites. The bundled skill version and checkout both identify `1.0.0-beta.4-SNAPSHOT`.
- **Coverage table:** Current after the Java API index correction; skill-authoring routing and coverage are current.
- **LLM-first usability:** Pass. The Java API index routes lifecycle/staging to the reload topic; authoring topics keep local execution and trace semantics concise. Drift classification: documentation drift found in the Java compatibility guide and resolved; remaining changed claims align with executable evidence.

## Residual Risks and Optional Developer Checks

- The exact nested/parallel REST A/B reload composition is not newly asserted end-to-end, although its component invariants and broader nested/parallel paths are tested. A production-like deployment may optionally inspect its external artifact retention and traffic-readiness ordering; Loomspan cannot verify an application's storage policy.
- Browser E2E tests were not run; the changed UI fields are covered by component tests, typecheck, and production build. The initial broad web timing failure did not reproduce in isolated or repeat-full runs.

## Disposition

- **Candidate clean; fresh review required.** One P3 documentation finding was fixed in this context. No actionable issue remained after re-review, but this context changed implementation documentation and therefore cannot certify its own fix.
