# PR 9 — Admitted invocation generation ID, independent review 1

## Code Review Findings

No actionable findings.

## Findings Resolved in This Context

- None. No implementation artifact was changed during review.

## Open Questions and Assumptions

- None affecting correctness. The ticket expressly authorizes the source and binary compatibility impact of adding an abstract method to `AdmittedSkillInvocation`.

## Verification Results

- PASS — `mvn -q '-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,DefaultSkillTemplateTest,SkillGenerationExecutionIntegrationTest,PublicSkillReloadIntegrationTest,SupportedSurfaceIntegrationTest,FrameworkShutdownIntegrationTest' test` (PowerShell; focused public API, handoff, reload, nested REST, concurrency, retirement, and shutdown coverage).
- PASS — `mvn -q test` (161 Surefire reports; 1,168 tests, zero failures, errors, or skips).
- PASS — `git diff --check` (no whitespace errors; Git emitted only a line-ending normalization warning for `ApplicationApiValueTest.java`).

## Requirements and Plan Conformance

- Implemented: Both handoff overloads use the existing pre-conversion/pre-validation capture and build the same admitted handle. The new `final String` copies `PreparedInput.generation().id()` independently of the clearable payload; `generationId()` is a plain read with no lifecycle call, ownership change, lookup, or new registry. `SkillGeneration` enforces a nonblank ID, its default catalog uses that ID, and prepared REST invokers close over the same ID.
- Implemented: Focused tests verify A remains on old pending handles after B publication, B is returned for new handles, old and new definitions execute appropriately, REST root and nested handler IDs match the handle, concurrent reads remain stable, and the ID survives success, failure, release, retirement, and cutoff. Existing authorization, single-use, nesting, admission, and shutdown behavior remains exercised by the full suite.
- Implemented: Public Javadoc, README, and version-aligned Java API invocation/reload guidance explain process-local identity, capture timing, lifetime stability, application-owned mapping retention, and missing-mapping handling. Both correlation examples record before invocation and release in `finally`, including lookup or recording failures.
- Partial: None.
- Missing: None.
- Safe deviations: None. The unrelated, pre-existing `pom.xml` formatting diff was excluded from ticket scope and preserved.
- Compatibility review: `AdmittedSkillInvocation` is an allowlisted Application API type, and this change adds one `String generationId()` method to it without exposing internal types or adding a new type or Spring replacement point. Handwritten implementations and fakes must implement the new abstract method and recompile; this deliberate development-stage break is authorized by the ticket's Pipeline notes and documented. `RestSkillHandler` remains the sole supported SPI. Internal handoff implementation changes need no shim; no configuration, manifest, serialization, persistence, or external protocol contract was changed.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact.
- **Rationale:** The new read belongs to application code holding a root admission. It changes no skill manifest, validation rule, execution input/output contract, author-visible nesting rule, trace, or authoring test guidance.
- **Documents reviewed:** `agent-skills/loomspan-docs/SKILL.md`, `references/skill-authoring/README.md`, and the changed `references/java-api/{README,compatibility-and-boundaries,invocation,skill-reload}.md` under that skill.
- **Evidence checked:** Public API and internal handoff source, generation capture and REST routing source, and focused supported-surface, reload, and lifecycle tests. The skill metadata and Maven artifact both identify `1.0.0-beta.5-SNAPSHOT`; the bundled Java API guidance is version aligned. Drift classification: **aligned**.
- **Coverage table:** Skill-authoring table not applicable; Java API invocation entry current.
- **LLM-first usability:** Pass for the routed Java API guidance; not applicable to skill-authoring topics.

## Residual Risks and Optional Developer Checks

- None identified. The review used local framework fixtures and the complete automated suite; no external Sidecar or application mapping service was required for this framework-only change.

## Disposition

- **Approve** — no actionable findings and sufficient independent verification.
