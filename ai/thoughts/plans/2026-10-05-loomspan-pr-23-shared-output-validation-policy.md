# PR 23 Shared Output-Validation Policy Implementation Plan

## Overview

Extract the overlapping final-output validation mechanics used by ordinary advisors, planning synthesis, binding contribution rejection, and fully bound completion. Preserve the existing validators, output-composition authorities, execution loops, and every observable success/correction/exhaustion behavior.

### Execution authorization and scope attribution

The developer approved the **Full 5-Step Pipeline** (`full`) with “proceed”; this artifact covers Steps 2 and 3 in pipeline mode. Implementation and independent review belong to subsequent fresh stages. Baseline is HEAD `35cded1dfe30ff467869139f5f69ee872cbe41a9`, which implements prerequisite PR22. The developer's expanded ticket was the only preexisting dirty file at triage; preserve its diff and do not reset or rewrite it. Step 1's research artifact is pipeline work. PR23 remote contents/status could not be established; the local ticket governs scope, with no assumed remote implementation or merge relationship.

Planning checklist: source/contract inventory complete; documentation alignment complete; abstraction selected; phase structure complete; implementation plan complete; dedicated testing plan follows in this same context.

## Current State Analysis

Ordinary schema, evidence, and linter advisors contain their own retry loops and feedback request construction. Planning has one final-step loop, three independent validation counters, short-circuit schema/evidence/linter checks, and separate action-envelope retries. Their validators are already shared; duplication remains in status arithmetic, retry-count/outcome construction, regex matching, and feedback utilities.

PR22 extends these seams. Ordinary fully bound completion records schema success then evaluates evidence and regex without a model request. Planning fully bound completion composes, revalidates complete schema through its normal final validators, and immediately rejects any failure even when its recorded status permits retry. Mixed planning contribution failures repeat schema accounting. These are explicitly in scope. Composition/projection algorithms and repeated validation calls remain unchanged.

### Key discoveries

- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:96`: baseline/latest-only schema correction; ordinary outcomes/trace issues capped at four; schema correction bounded to 2,048 code points.
- `src/main/java/ai/loomspan/internal/linter/LinterCallAdvisor.java:74` and `src/main/java/ai/loomspan/internal/runtime/evidence/EvidenceContractCallAdvisor.java:50`: accumulating system hints and nested downstream-chain retries; unbound linter returns on exhaustion.
- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:208`: binding schema advisor order changes from precedence minus 90 to minus 70, enabling complete assembly before evidence/lint checks; linter is minus 100 and evidence minus 80.
- `src/main/java/ai/loomspan/internal/runtime/DefaultMissionExecutionEngine.java:85` and `:165`: immutable completion, forced exhausted linter at attempt 1, distinct evidence metadata and assembled-output details; `:128` rejects mixed binding linter exhaustion.
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:555`, `:561`, `:1207`: full-bound checking, contribution rejection, and planning final validators share source rules but duplicate policy arithmetic. Planning schema/linter failures advance their counter even on exhaustion; exhausted evidence does not.
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1401`: planning summary uses three issues, canonicalField before path, and its own empty fallback.
- `src/main/java/ai/loomspan/internal/outputschema/OutputBindingComposition.java:18` and `:65`: invocation-local source capture, ownership/projection, complete composition, and complete-schema validation are existing authorities.
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:504`: output_from bypass returns the exact retained producer result with no final synthesis/policy checks.

## Desired End State

Both execution paths, including binding branches, call a small shared policy implementation for reusable decisions, outcome construction, and regex checking. Feedback formatting has explicit internal named entry points for intentionally different formats. Integration code owns candidate extraction, actual validator invocation, issue limits, detail/prefix selection, counters, requests, recording, exceptions, and lifecycle. No duplicate policy algorithm or generic retry engine remains.

The dedicated testing plan establishes behavioral equivalence through exact request/outcome/event assertions and baseline characterization. Existing invocation results, public signatures, YAML behavior, transport, and trace formats remain unchanged.

## What We're NOT Doing

- No new validator, composition/projection/source-selection algorithm, universal retry loop, service SPI, Spring replacement contract, configuration key, or application API.
- No normalized exhaustion behavior, retry budget, next-attempt counter, issue limit, prompt text, exception, metadata, or event timing.
- No removal of repeated within-path schema validation, planning task replay, parent validation of forwarding results, or final model requests for fully bound completion.
- No lifecycle/concurrency/authorization/transport change, trace migration, Console change, compatibility-marker change, or unrelated cleanup.
- No prompt/data classification or content rewriting. Retain explicit resource bounds and the existing ExecutionJournalProjector exception outside this refactor.

## Skill-Authoring Documentation Impact

**Impact: No impact on author-facing semantics.**

- **Rationale:** syntax, guidance, retries, returned data, full/mixed assembly, evidence truth sets, final-step boundaries, and forwarding remain identical.
- **Documents to update:** no semantic rewrite. In Phase 4, verify and repair only named implementation anchors in `agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md` and any routed document referencing moved helpers. Preserve `StepLoopMissionExecutionEngine#validateOutputSchema` as an integration method so its current anchor remains valid.
- **Supporting evidence:** schema/linter advisor tests, EvidenceContractAdvisorAdditionalTest, StepLoopMissionExecutionEngineTest, DeclaredOutputBindingsOrdinaryIntegrationTest, DeclaredOutputBindingsPlanningIntegrationTest, OutputBindingCompositionTest, OutputBindingIsolationTest, DesignatedChildResultIntegrationTest.
- **Coverage table update:** not required; topic scope and confidence remain unchanged. Anchor maintenance does not add coverage.
- **LLM-first usability:** preserve routing, self-contained author guidance, terminology, and explicit limitations; use stable named source/test anchors without duplicating internal design details.
- **Drift:** **aligned** after reading matching-checkout loomspan-docs SKILL.md, index, output-contracts.md, evidence-contracts.md, and source-verification.md following the executable inventory. Skill/POM version is `1.0.0-beta.8-SNAPSHOT`. Undocumented exact diagnostic strings and advisor reset details are established by source/tests, not inferred from prose.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | No signatures/types change; SkillTemplate invocation results retain exact required behavior. Closed classification comes from LoomspanPublicSurfaceArchitectureTest and AGENTS.md. | Preserve source, binary and behavioral compatibility; unchanged API allowlist. |
| Supported SPI | RestSkillHandler is the sole SPI; no handler or extension changes. | Preserve; no additional SPI/bean override contract. |
| Configuration and manifest contracts | Existing output_schema, output_schema_max_retries, evidence, linter, output_bindings, output_from and planning behaviors are exercised by source/fixtures and authoring docs. | Preserve keys, defaults, normalized values, budgets and semantics. |
| Persisted or serialized contracts | No durable-format change. Invoked text/JSON and source-value fidelity remain protected at return boundaries; current trace artifact encoding is unchanged. | Preserve serialized output and current exact-version trace behavior; no migration/legacy readers. |
| Ephemeral diagnostic formats | Existing schema/linter/evidence outcomes, advisor/step events, exceptions, assembled/forwarded events, payloads and metadata. | Preserve current writer/reader/projector coherence, ordering, counts, limits and full candidate fidelity. |
| Internal or accidentally exposed implementation | Advisors, engine helpers, validators and wiring are internal even when Java-public. New cross-package helper types need technical public access. | Atomic caller/test updates; remove displaced internal helper algorithms without compatibility wrappers. |

- **Evidence of supported contracts:** AGENTS.md, architecture test closed allowlists, ticket's explicit preservation requirements, README supported surface, routed output/evidence authoring guidance.
- **Intentional compatibility changes:** none. Internal decomposition changes only.
- **In-repository consumers to update:** three advisors; ordinary/step mission engines; focused tests; architecture internal-public allowlist; moved documentation anchors if any. Composition, projection, validator, recorder/projector and transport algorithms stay as authorities.
- **Public-surface delta:** no supported surface delta. Add only `ai.loomspan.internal.outputvalidation.OutputValidationPolicy` and `OutputValidationFeedback` as public final internal utility classes for cross-package use; keep incidental types private/package-private or nested. Add their explicit internal-public reasons to the architecture test; do not change API/SPI/integration allowlists or Spring bean definitions.
- **Shim decision: No shim.** The relocated helpers are internal and all callers can update atomically; no protected Java signature requires an adapter, overload or deprecated bridge.
- **Java-to-Go boundary coordination: Not required.** REST/SSE/acquisition/problem/consumed NDJSON fields and authoritative events remain identical; use ConsoleTraceFixtureCorpusTest as current-run evidence. consoleCompatibilityVersion remains unchanged.
- **Pipeline notes alignment: No notes.** The requirements explicitly require internal refactoring and behavior preservation; any observable change requires scope escalation.

## Implementation Approach

Use two stateless internal utilities, with existing result/outcome types. `OutputValidationPolicy` owns pass/retry/exhaustion calculation (failure retries while attempt <= maxRetries), retryCount = attempt - 1, schema/linter outcome factories, bounded issue-list selection, and full-string regex matching. Expose an explicit immutable-completion decision for ordinary fully bound linter failure so attempt 1 can be EXHAUSTED with a positive configured budget. Do not hide that path difference behind guessed retryability.

The policy may use a nested decision enum/value with exhausted and existing-schema/linter-status mappings; it must not carry session, candidate, counter state, recorder, model request, or executable loop. Factories receive the existing failure mode/issues/detail and the caller's issue-limit choice: four ordinary, complete planning. Avoid changing existing validation/constructor failure timing by adding speculative preconditions to the arithmetic utility.

`OutputValidationFeedback` owns existing pure rendering algorithms, using explicit methods for ordinary schema correction, ordinary schema log summary, and planning schema summary. Ordinary correction accepts the model-contribution/whole-output choice and preserves exact tails, JSON quoting, parser locations/fragments, four whole bullets and code-point limit. Planning retains its three-issue canonical-field summary. Share the existing `EvidenceCoverageResult.retryFeedback()` for planning; ordinary evidence's four-message system hint remains a named ordinary renderer or local request integration, with unchanged text. Linter/evidence prompt joining can reuse a narrow text helper when it removes actual duplication, but no generalized format/profile configuration object is needed.

This removes duplicate runtime mechanics while preserving the owning boundary for every semantic decision. No framework change would leave the ticket's duplication unresolved. A combined execution service would add callbacks, state and policy switches and could change nested/reset semantics. Replacing existing validators/composition would create second authorities. The chosen pure utilities provide reuse with no new mutable state, trust/dataflow changes, model costs, or concepts for skill authors or application callers. Duplicate arithmetic/renderers removed within this scope are the identified technical debt; unrelated apparently unused engine methods are outside this ticket.

## Phase 1: Characterize integration differences

### Changes Required

Extend the existing advisor, evidence, step-engine and declared-binding tests as specified in the dedicated testing plan. Add a deterministic ordinary combined-advisor regression using real resolver/chain ordering, plus planning combined-validator and terminal tests. Capture exact outcomes, correction requests, exceptions and event order for paths currently protected mainly by source inspection.

Run characterization on the unchanged production baseline before extraction. These tests should pass: the ticket describes a refactor, not a defect. Do not create a test requiring new helper structure merely to produce a red baseline.

### Automated Verification

- [x] Baseline characterization passes with the appropriate existing suites/new test classes.
- [x] Assertions explicitly distinguish unbound ordinary, bound ordinary, and planning terminal behavior.
- [x] Fully bound status/details and contribution-failure accounting are covered without provider fallback.

## Phase 2: Extract policy and feedback; integrate ordinary advisors

### Changes Required

1. Add `src/main/java/ai/loomspan/internal/outputvalidation/OutputValidationPolicy.java` and `OutputValidationFeedback.java` with the mechanics above; add focused utility tests for budget boundaries, issue-limit choice, regex full matching and exact render output.
2. Update `OutputSchemaCallAdvisor`, `EvidenceContractCallAdvisor`, and `LinterCallAdvisor` to delegate their pure mechanics. Keep order values, invocation-composition capture, candidate extraction, validation calls, response metadata/context mutation, recorder handling, exception construction, traces and chain-copy/request loops local.
3. Delete replaced private arithmetic/rendering algorithms; preserve constants needed by existing tests or update internal test references atomically. Do not retain old algorithms as compatibility routes.
4. Add only new internal-public type entries in `src/test/java/ai/loomspan/architecture/LoomspanPublicSurfaceArchitectureTest.java`, each explaining cross-package collaboration.

### Automated Verification

- [x] Policy/feedback tests and OutputSchemaCallAdvisorTest, LinterCallAdvisorTest, EvidenceContractAdvisorAdditionalTest, SkillAdvisorResolverEvidenceTraceTest and combined-advisor characterization pass.
- [x] Exact ordinary corrections, logs, resource bounds, metadata, recorder failures and full exceptions remain covered.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes after production-type changes.

## Phase 3: Integrate planning and all binding policy paths

### Changes Required

1. In `StepLoopMissionExecutionEngine`, delegate `validateOutputSchema`, `validateEvidence`, `validateLinter`, and `validateContributionFailure` arithmetic/outcomes/rendering/regex to shared utilities. Keep schema/evidence/linter ordering and counters local. In particular schema/linter failure returns attempt+1 even when exhausted; exhausted evidence returns attempt unchanged. Successful validators retain counters, action retries stay independent, and rejected final actions remain in the same step.
2. Keep `requireCompleteOutputPolicies` calling the planning final validators at 1/1/1 after `composeEmpty()`; do not eliminate the repeated complete-schema validation. Its immediate rejection and binding_output_contract prefix stay local, regardless of recorded RETRYING.
3. In `DefaultMissionExecutionEngine`, use shared outcome factories for full schema success and shared regex/outcome mechanics in `validateBoundPolicies`. Preserve actual evidence invocation and sparse metadata, assembled-specific details, forced terminal linter status at attempt 1, immediate exception prefixes and publication timing. Preserve mixed bound exhausted-linter rejection before RESULT_ASSEMBLED.
4. Retain OutputBindingComposition/Projection, successful-child truth-set selection, capture fences, final action extraction, and output_from branch unchanged. Remove the displaced duplicate helper algorithms only.

### Automated Verification

- [x] StepLoopMissionExecutionEngineTest and new planning/binding characterization pass.
- [x] DeclaredOutputBindingsOrdinaryIntegrationTest, DeclaredOutputBindingsPlanningIntegrationTest, OutputBindingCompositionTest and OutputBindingIsolationTest pass.
- [x] EvidencePlanningIntegrationTest, PlannerEvidenceFlowIntegrationTest and DesignatedChildResultIntegrationTest pass.
- [x] Full-bound no-final-call, immutable failure, mixed complete-output checks, snapshots, projected guidance, task counts, provenance and event counts remain covered.
- [x] Architecture check passes; no supported type/signature/configuration change appears in diff.

## Phase 4: Verify complete change and documentation anchors

### Changes Required

Inspect displaced helper call sites to confirm both integrations and binding branches use the shared mechanics, leaving only execution/diagnostic-specific logic. Verify named authoring anchors still resolve; apply focused stable-anchor edits only if relocation invalidated them. Keep semantic docs and README coverage unchanged. Review the ticket acceptance checklist against executable assertions, not helper test counts.

### Automated Verification

- [x] Focused commands and complete `test` suite in the dedicated testing plan pass.
- [x] ConsoleTraceFixtureCorpusTest and LoomspanPublicSurfaceArchitectureTest pass.
- [x] `git diff --check` passes; preexisting expanded ticket changes remain preserved.
- [x] Source search confirms no duplicate retry/outcome/regex algorithm survives in the migrated integrations.
- [x] Documentation anchors are valid and remain aligned with the LLM-First Authoring Standard.

## Testing Strategy

Use existing deterministic chains/model sequences, fixed clocks, bound missions, mock HTTP providers and trace record readers. Add only missing cross-validator/terminal/full-bound characterization; retain broad existing source/projection, fidelity, forwarding, lifecycle and concurrency regression suites. See `2026-10-05-loomspan-pr-23-shared-output-validation-policy-testing.md` for exact cases, locations, commands and exit criteria. Planning has not run tests; implementation owns baseline and post-change verification.

## Performance Considerations

Pure utility calls add no provider request or work replay. Ordinary advisor keeps its precompiled regex; planning and full-bound paths retain current compile timing unless a separate justified local implementation detail can preserve failure timing. No global regex cache, session state or new allocation-heavy orchestration object is needed. Preserve all validation calls, quotas and explicit diagnostic bounds.

## Migration Notes

No application, manifest, data, or transport migration. Internal helper/caller/test changes ship atomically; rollback is the cohesive code change. No compatibility shim or duplicate policy route.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-23-shared-output-validation-policy.md`.
- Research: `ai/thoughts/research/2026-10-05-loomspan-pr-23-shared-output-validation-policy.md`.
- Policy: `AGENTS.md`, `ai/thoughts/framework-feature-design-lens.md`.
- Documentation protocol: `ai/commands/shared/loomspan-docs-protocol.md`.
- Matching source/test anchors cited above and in the dedicated testing plan.

## Step 4 implementation evidence

Baseline production remained unchanged through characterization. The main baseline command passed 157 tests with zero failures/errors/skips:

```powershell
.\mvnw.cmd '-Dtest=OutputValidationAdvisorOrderingTest,OutputSchemaCallAdvisorTest,LinterCallAdvisorTest,EvidenceContractAdvisorAdditionalTest,StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest' test
```

A final bounded contribution-policy characterization was added and unchanged-production planning tests passed 101 tests with zero failures/errors/skips:

```powershell
.\mvnw.cmd '-Dtest=StepLoopMissionExecutionEngineTest' test
```

Fixture corrections during characterization: use object FINAL_RESPONSE payloads for object-claim evidence; retain the copied manifest before altering it; inspect system plus user messages for completed-work evidence. Initial runs failed these new fixture assertions or incorrectly named trace enum constants; production was unchanged. The final baseline runs establish nested ordinary resets, independent planning counters, zero/positive budgets, full-bound retry-status differences, contribution overrides and complete-output mixed linting.

The extraction follows the planned boundary: schema/linter outcomes, retry decisions, issue-list selection and full regex matching live in OutputValidationPolicy; named ordinary schema/log/evidence and planning schema renderers live in OutputValidationFeedback. Existing EvidenceCoverageResult.retryFeedback remains the planning evidence formatter. Next-attempt state, recording, requests, validation timing, lifecycle and composition/projection remain with their existing owners.

Routine internal visibility adaptation: OutputSchemaValidator's existing INVALID_JSON and UNKNOWN_PROPERTY constants are public for the moved renderer, avoiding a second issue-code authority. The validator remains internal. Initial post-extraction compilation caught the original package-private visibility and was corrected before verification. Planning retains its null short-circuit before compiling regex to preserve failure timing.

The focused command passed 54 tests with zero failures/errors/skips, including architecture:

```powershell
.\mvnw.cmd '-Dtest=OutputValidationPolicyTest,OutputValidationFeedbackTest,OutputValidationAdvisorOrderingTest,OutputSchemaCallAdvisorTest,LinterCallAdvisorTest,EvidenceContractAdvisorAdditionalTest,SkillAdvisorResolverEvidenceTraceTest,LoomspanPublicSurfaceArchitectureTest' test
```

Documentation classification remains **aligned**, with no authoring semantic impact. Matching-checkout loomspan-docs routes, output/evidence topics and source-verification protocol were read after executable inventory. Named OutputSchemaCallAdvisor, StepPromptBuilder and StepLoopMissionExecutionEngine#validateOutputSchema anchors still resolve. No knowledge-base or README edit is necessary.

The supported API/SPI/integration allowlists, Spring beans, manifest/configuration keys, lifecycle/concurrency and transport remain unchanged. Only the two planned utility classes were added to the internal-public allowlist. The expanded preexisting ticket diff remains intact; no commit was requested or made.

The planning/binding/forwarding/evidence/Console regression command passed 175 tests with zero failures/errors/skips, including architecture and the Console fixture corpus:

```powershell
.\mvnw.cmd '-Dtest=StepLoopMissionExecutionEngineTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,OutputBindingCompositionTest,OutputBindingIsolationTest,EvidencePlanningIntegrationTest,PlannerEvidenceFlowIntegrationTest,DesignatedChildResultIntegrationTest,ConsoleTraceFixtureCorpusTest,LoomspanPublicSurfaceArchitectureTest' test
```

The ordinary fully bound linter failure characterization was then strengthened to assert EXHAUSTED at attempt 1/retryCount 0 with maxRetries 2 and the exact assembled-output detail, complementing the planning test's RETRYING-at-attempt-1 assertion. This assertion is included in final full-suite verification.


Final broad verification passed:

```powershell
.\mvnw.cmd test
```

Maven reported 1,464 tests, zero failures/errors and two skipped tests. The skipped tests are the preexisting Pr18RecordedHandoffDiagnosticTest property-gated diagnostics (`pr18.diagnostic` absent); no PR23 required suite was skipped. Production and tests compiled. The strengthened ordinary immutable lint-recording assertion passed in this run.

`git diff --check` passed. Targeted source search found no remaining `attempt > maxRetries`/`attempt <= maxRetries`, `attempt - 1`, direct schema/linter construction or `.matcher(...)` mechanics in the five migrated integrations; those mechanics now live in the pure policy. Request creation, status-specific trace events, issue limits, exceptions, caller-owned next counters and terminal publication choices deliberately remain path-specific.

All ticket acceptance criteria map to the combined executed coverage in the testing plan. No required correctness checks remain unrun. No new API/SPI/configuration or transport contract appeared in the full diff; composition/projection, forwarding and journal projection were unchanged. Independent Step 5 remains the final pipeline assurance stage.
