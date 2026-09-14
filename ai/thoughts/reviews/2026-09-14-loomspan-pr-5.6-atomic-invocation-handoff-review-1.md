# Atomic Invocation Handoff — Review 1

## Code Review Findings

No actionable findings remain after the fixes applied in this review context.

## Findings Resolved in This Context

- **[P2] Exercise the application-owned discard boundary in both listener orders.** The original `FrameworkShutdownIntegrationTest#independentCloseListenersWorkInEitherRegistrationOrder` handed work directly to Loomspan before close and used an unreachable `if (!hostGateClosed)` branch as its only queued-work assertion. It therefore did not execute the ticket's external-dispatch-close-before-handoff boundary. The test now uses a synchronized application dispatch gate in both listener registration orders, proves a pre-close handoff remains framework-owned, and proves a post-close request is discarded before calling Loomspan (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkShutdownIntegrationTest.java:311`).
- **[P2] Add a controlled execution-first release/cutoff schedule.** The initial lifecycle tests covered sequential claim, release, and cutoff states but did not hold a concurrently claimed execution across both explicit release and framework cutoff. The new deterministic test pauses a claimed root, applies release and cutoff, verifies ownership remains active, and then completes it without a leak (`loomspan-spring-boot-starter/src/test/java/ai/loomspan/internal/core/FrameworkExecutionLifecycleTest.java:141`).
- **[P3] Complete generated documentation for the new supported API.** Release-profile Javadoc identified missing parameter and return documentation on the new supported methods. The public interfaces now document parameters, results, authorization denial, and safe invocation/admission failures (`loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/AdmittedSkillInvocation.java:14`; `loomspan-spring-boot-starter/src/main/java/ai/loomspan/api/SkillInvocationHandoff.java:15`).

## Open Questions and Assumptions

- None. The selected `full` profile remains required and sufficient because the change deliberately adds supported API and changes security, lifecycle, and concurrency behavior.

## Verification Results

- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest' test` — 30 tests passed after the deterministic test fixes.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -pl loomspan-spring-boot-starter '-Dtest=ApplicationApiValueTest,DefaultSkillTemplateTest,FrameworkExecutionLifecycleTest,FrameworkShutdownIntegrationTest,LoomspanPublicSurfaceArchitectureTest,SupportedSurfaceIntegrationTest' test` — 68 tests passed.
- PASS — `go -C loomspan-console test ./internal/buildtool`.
- PASS — `python scripts/loomspan_version.py check` — version remains `1.0.0-beta.4-SNAPSHOT`.
- PASS — `python -m unittest discover -s scripts/tests -p "test_*.py"` — 9 tests passed.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` — full reactor passed with 1,148 tests.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests '-Dgpg.skip=true' verify`.
- PASS — `.\mvnw.cmd --batch-mode --no-transfer-progress -Prelease -pl loomspan-spring-boot-starter -am -DskipTests '-Dgpg.skip=true' clean verify` — regenerated source and Javadoc artifacts; remaining warnings are pre-existing and outside the changed public interfaces.
- PASS — `git diff --check`.
- PASS — `git status --short` — only the ticket-scoped implementation, documentation, tests, pipeline artifacts, and this review artifact are present; nothing is staged.

## Requirements and Plan Conformance

- **Implemented:** The new `SkillInvocationHandoff` and `AdmittedSkillInvocation` supported API is additive and leaves all six `SkillTemplate` methods unchanged. Handoff completes preparation and authentication capture before atomically admitting one pending lifecycle root; invocation claims that root once and enters the shared runner body without reacquiring admission.
- **Implemented:** `FrameworkExecutionLifecycle` serializes claim, pending release, execution completion, admission close, and cutoff under one lifecycle monitor. Pending roots are removed at release/cutoff; executing roots stay registered through trace finalization, binding restoration, view mapping, observer delivery, and runner completion.
- **Implemented:** Direct invocation, validation, exception taxonomy, authorization, captured identity, nested work, observer behavior, and worker-context restoration remain on the existing paths and are covered by focused and supported-surface tests.
- **Implemented:** Real `TRACE_COMPLETED` appends are blocked through a test-only package seam for both completion-within-budget and cutoff-at-budget schedules. No public trace hook, new executor, queue, shutdown authority, grace period, or listener priority dependency was added.
- **Implemented:** README, release notes/readiness, AGENTS guidance, Java API knowledge set, architecture allowlist, API-shape tests, auto-configuration boundary test, supported-surface test, and Console documentation-package assertion agree on the closed fifteen-type surface and sole `RestSkillHandler` SPI.
- **Partial:** None within this repository's ticket. Snapshot reinstall, Sidecar adoption, and Sidecar verification remain explicitly separate and pending, as required.
- **Missing:** None.
- **Safe deviations:** Test fixture organization and exact helper names differ mechanically from the plan while preserving its observable schedules and assertions.
- **Compatibility review:** The affected protected surface is the closed Application API. The two new interfaces are additive; `SkillTemplate` signatures and behavior remain unchanged, so no compatibility shim is needed. `RestSkillHandler` remains the sole supported SPI, no `@ConditionalOnMissingBean` replacement surface was introduced, and public signatures expose only supported API or JDK types. Internal lifecycle and runner paths were updated atomically without retaining a second admission authority.

## Skill-Authoring Documentation Impact

- **Assessment:** No impact.
- **Rationale:** The diff changes application dispatch-to-framework ownership and does not change manifests, skill inputs or outputs, planning, capability visibility, RBAC semantics, attachments, limits, trace contents, debugging semantics, or skill tests.
- **Documents reviewed:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`; application-facing guidance under `agent-skills/loomspan-docs/references/java-api/`.
- **Evidence checked:** Public API and implementation source, lifecycle/facade/supported-surface tests, README, release notes, and Console package assertions.
- **Coverage table:** Current; no skill-authoring topic boundary or confidence changed.
- **LLM-first usability:** Not applicable to the unchanged skill-authoring set. The affected Java API guidance is routed, concise, self-contained, and aligned with executable behavior.

## Residual Risks and Optional Developer Checks

- The rebuilt snapshot has not been installed and Sidecar has not yet adopted or rerun against the handoff API. This is intentionally outside the framework ticket and is recorded as pending in release readiness; framework-internal blocked-trace evidence does not satisfy Sidecar SC5's separate public-contract criterion.
- No non-automatable developer check is needed for this framework change.

## Disposition

- **Candidate clean; fresh review required** — two P2 verification gaps and one P3 public-documentation gap were fixed in this context, and all final verification passed. A fresh independent review must validate the resulting implementation.
