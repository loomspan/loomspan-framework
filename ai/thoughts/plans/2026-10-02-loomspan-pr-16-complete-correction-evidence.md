# PR 16 — Complete correction evidence implementation plan

## Overview

Remove candidate clipping from ordinary output-schema and planning step-action correction. Send the latest rejected response exactly in its existing data role/encoding while retaining original task context, validation, feedback bounds, retry allowances, and terminal failure behavior. This is Steps 2–3 of the accepted Full 5-Step Pipeline (`PROFILE: full`), following the 2026-10-02 research artifact. No implementation or provider calls belong to this planning stage.

## Current State Analysis

`OutputSchemaCallAdvisor.java:242` rebuilds retries from its schema-augmented baseline with one assistant candidate and one user correction. `candidateReplay` at line 416 clips to 8192 code points and discards whitespace-only text; the correction announces clipping. `StepActionCorrection.java:57` JSON-quotes candidate evidence but `replay` at line 64 selects at most an 8192-character head/region/tail. Parser coordinates are also useful as diagnostics, independently of that selection.

`StepLoopMissionExecutionEngine.java:793` rebuilds each request from the original assignment and available results, replacing rejection state after each failure. Parsing and action/final validation precede accepted action execution (`:858-906`). Ordinary invalid actions have one retry (`:88`); final validators retain separate allowances. `ProviderAttemptCallAdvisor.java:64-95` propagates terminal provider failures and enforces provider-attempt reservation without shortening requests. No configured full-request/context-window limit was found; existing quotas and provider-reported limitations remain the authorities.

## Desired End State

Both paths send the complete latest rejected text, including Unicode, original outer whitespace/fences, content after character/code-point 8192, and malformed tails. Schema replay remains an assistant message; step replay remains a JSON string in user evidence with framework data-only instructions. Earlier rejected candidates are absent from later corrective requests. Feedback remains separately bounded. A valid replacement is accepted only by existing validators; invalid responses and provider/resource failures remain explicit failures.

### Key Discoveries

- `OutputSchemaCallAdvisorTest.java:256` and `StepLoopMissionExecutionEngineTest.java:389` assert obsolete clipping; the ticket explicitly authorizes replacing those assertions.
- `StepActionCorrection.java:90` stores an offset solely for region selection. Remove this state with clipping, retaining available coordinates/nearby fragments in the diagnostic string.
- `ModelAttemptCallAdvisorIntegrationTest` already provides both a real localhost mock-provider seam and fake model/runtime helpers; no external model is needed.
- The neighboring suite's fictional source pack supplies a self-contained structured example: P240 asset `NB-P240-017`, event `E17`, child assessment/chronology/hypotheses/questions and comparison citations. Use these concepts for synthetic Framework fixtures; do not copy ignored live journals or couple tests to that workspace.

## What We're NOT Doing

No model-window catalog, tokenizer, new configuration/API/SPI, automatic JSON repair, summary/excerpt fallback, patch protocol, provider selection, general context management, trace schema/retention changes, Console changes, or business citation-quality policy. No paid calls, neighboring workspace edits, or alteration of the existing `ExecutionJournalProjector` exception.

## Skill-Authoring Documentation Impact

**Impact: Affected.** Authors diagnosing correction must know that complete candidates are sent and provider limits may fail explicitly; completeness does not guarantee model recovery or citation preservation.

- **Documents to update:** `agent-skills/loomspan-docs/references/skill-authoring/traces-and-debugging.md`, sections “Output-schema semantic retries” and “Step-action correction evidence”; `agent-skills/loomspan-docs/references/skill-authoring/README.md`, Traces and debugging coverage row.
- **Supporting evidence:** revised advisor/helper/engine tests, context-limit integration test, `ProviderAttemptCallAdvisor`, existing output-schema/action validators and quota tests.
- **Coverage table update:** Required; replace “Bounded rejected step-action evidence” with complete latest-candidate replay and separately bounded diagnostics.
- **LLM-first usability:** Keep existing routing and focused sections; explain complete candidate versus bounded feedback/trace previews, exact roles/encoding, separate retry allowances, explicit failure, and model-quality limitations. Update named test anchors atomically. General output-contract vocabulary/retry keys are unchanged.
- **Drift classification:** Aligned before implementation; existing prose accurately describes clipping. Update prose with production changes to remain aligned. Bundled skill and `pom.xml` both identify beta.8-SNAPSHOT.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | Closed architecture allowlist; helpers are internal, no supported signatures change | Preserve; run public-surface architecture test |
| Supported SPI | `RestSkillHandler` unaffected; advisors are not replacement contracts | Preserve; add no extension points |
| Configuration and manifest contracts | Schema vocabulary, retry keys, provider policy/quotas unchanged; candidate-size behavior intentionally changes | Preserve keys/defaults and retry behavior; document complete replay |
| Persisted or serialized contracts | No durable candidate interchange contract; internal evidence string encoding retained | No migrations or dual formats |
| Ephemeral diagnostics | Actual request text grows; current-run request/exception fidelity retained; rejection previews remain separate | Preserve record shapes and failure visibility; no content rewriting |
| Internal implementation | Clipping constants/records/helpers and offset-selection state become obsolete | Remove atomically with callers/tests; no shims |

- **Evidence of supported contracts:** `AGENTS.md`, `LoomspanPublicSurfaceArchitectureTest`, design lens, documented manifest/quotas, explicit ticket requirements.
- **Intentional compatibility changes:** Remove only the internal candidate-size guarantee and omission behavior for nonempty whitespace-only schema candidates. Whitespace is rejected response data and the ticket requires exact preservation. Empty/missing schema responses may omit the synthetic message because there is no candidate text; existing empty-response feedback remains.
- **In-repository consumers:** Both correction helpers, step-engine `Failure` construction/parsing caller, focused tests, authoring topic and coverage row.
- **Public-surface delta:** None; no allowlist additions, constructors, Spring replacement points, or supported signature changes.
- **Shim decision: No shim.** Internal clipping machinery has no protected consumer contract; old and new behavior should not coexist.
- **Java-to-Go boundary coordination: Not required.** No REST/SSE/NDJSON record shape or consumed semantics changes; requests continue to carry ordinary text. No Console compatibility-marker bump.
- **Pipeline notes alignment: Aligned.** Removal of internal size bounds is explicit; unrelated contracts stay preserved.

## Implementation Approach

Use direct replay in existing composition paths. This fixes an information-loss defect owned by Framework even with capable models, avoids speculative abstractions, and removes the clipping state rather than retaining dead branches. Larger requests are an intentional cost of correctness; bounded retries limit history to baseline plus one latest candidate. Making no change or raising the cutoff would leave the defect; excerpt/summary approaches violate the ticket. Existing provider/resource failures supply explicit failure without introducing estimates.

## Phase 1: Preserve complete replay and replace obsolete regressions

### Changes Required

1. In `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java`, append the exact nonempty candidate as `AssistantMessage`; preserve nonempty whitespace. Remove `MAX_CANDIDATE_CODE_POINTS`, `CandidateReplay`, clipping helper and candidate-truncated booleans/notice. Retain code-point counting for the 2048 feedback bound, issue quoting, original prompt/options/context, and retry loop.
2. In `src/main/java/ai/loomspan/internal/runtime/step/StepActionCorrection.java`, JSON-quote the complete original candidate (retain existing null-to-empty convention). Remove candidate/head/region constants, replay selection, and `Failure.characterOffset`; narrow parsing helper parameters if the original candidate parameter becomes unused. Keep parser reason bound, available parsed-candidate coordinates, nearby fragment, diagnostic bound, and data-only correction instructions.
3. Update `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java` callers for that internal simplification. Do not change parsing normalization, validator ordering, retry counters, execution gates, or success/failure publication.
4. Replace clipping assertions in advisor/helper/engine tests with exact equality, decoding JSON-string evidence for step requests. Add Unicode after the old boundary and malformed tail coverage, plus whitespace/fenced preservation. Use deterministic structured equipment/comparison output with child data and ordered citations; fixture builders/resources must live in Framework tests and document synthetic provenance.

### Automated Success Criteria

- [x] First add replay-equality assertions and record their pre-fix failure; then implement direct replay.
- [x] Focused advisor/helper/engine tests pass; no clipped candidate, truncation notice, or offset-selection state remains.
- [x] Large and small candidates both retain original task/schema/action constraints and candidate data placement.
- [x] Full-text equality proves Unicode and tail fidelity, independently of generated-model quality.

## Phase 2: Verify retry/execution/resource boundaries and synchronize guidance

### Changes Required

1. Strengthen advisor latest-only/exhaustion cases with distinct large first/second candidates; assert third request contains only the full second candidate and baseline/options/context.
2. In engine tests, cover malformed and action-invalid corrections exhausting with zero rejected tool executions, valid correction executing once, final schema-invalid correction publishing no success, and multi-retry final-schema correction replacing previous candidate while retaining completed task evidence. Reuse existing final-schema allowance fixtures rather than increasing invalid-action allowance.
3. In `ModelAttemptCallAdvisorIntegrationTest`, use localhost `MockWebServer`/`SpringAiProviderIntegration`: initial large invalid response, then HTTP 400 context-limit response. Decode the sent correction request and assert candidate equality, two physical sends, preserved original provider failure, no third/fallback request, and no passed schema outcome. The existing real-provider timeout test is the wiring pattern. Also inject a terminal context-limit failure on the second engine model call to prove zero tool execution/no final success and full request before failure. Run existing provider-attempt quota tests to preserve explicit limits.
4. Update the two authoring locations above with implemented facts and focused anchors. Clearly separate full model-request evidence from bounded diagnostic feedback/rejection previews and provider limits from model-quality claims.

### Automated Success Criteria

- [x] New deterministic resource-failure tests and existing quota/retry tests pass; no lossy fallback or fabricated success.
- [x] Existing validator, coordinator, tool-reuse, and supported-surface tests pass.
- [x] Authoring guidance and coverage row accurately describe the verified final behavior.
- [x] `git diff --check` passes.
- [ ] Perform independent Step 5 review after implementation verification.

## Testing Strategy

The companion testing plan specifies regressions and commands. Use low-cost request-capture tests for exact text, engine tests for execution/publication gates, and one localhost provider integration for wire fidelity and real failure translation. No live-provider verification is needed or authorized. Existing architecture/validator tests protect unchanged boundaries.

## Performance Considerations

Correction requests and captured diagnostics may be larger, including JSON escaping overhead in step evidence. Preserve one latest candidate and current bounded feedback; do not optimize by discarding evidence. Real provider/serializer/resource exceptions must propagate without a shortened fallback. No speculative limit-setting machinery is justified.

## Migration Notes

No supported API/configuration or durable-data migration. Update internal helper callers/tests and authoring descriptions atomically. Old clipping behavior is intentionally removed.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-16-preserve-complete-correction-evidence.md`
- Research: `ai/thoughts/research/2026-10-02-loomspan-pr-16-complete-correction-evidence.md`
- Testing: `ai/thoughts/plans/2026-10-02-loomspan-pr-16-complete-correction-evidence-testing.md`
- Design lens: `ai/thoughts/framework-feature-design-lens.md`
- Synthetic domain provenance: neighboring `loomspan-sidecar-test-suite/docs/equipment-service-source-pack.md` and `tests/test_full_correction.py` (read-only context; no runtime test dependency).

## Planning Checklist

- [x] Read requirements, research, protocols, and design lens.
- [x] Verify source/test composition and provider seam.
- [x] Settle exact whitespace replay and obsolete internal-state removal.
- [x] Classify compatibility and documentation impact.
- [x] Define implementation phases, test evidence, and companion testing plan.


## Step 4 implementation and verification — 2026-10-02

Implementation is complete and ready for fresh Step 5 review. Both replay paths now send the exact complete latest candidate. Nonempty whitespace is preserved; schema retries retain assistant data placement, and step retries JSON-quote original text independently of parser normalization. Removed internal clipping constants/helpers/record state atomically; parser coordinates, diagnostic bounds, validators, retry allowances, and execution gates remain.

A self-contained `CorrectionEvidenceFixtures` builder supplies fictional P240/NB-P240-017/E17/MAN-2.3 assessments, chronology, hypotheses, questions, comparison options and ordered citations with Unicode and a unique tail. It requires no neighboring workspace or live capture. Exact decoded-text comparisons cover both replay paths; scripted valid replacements pass real validators. Large final-schema retries first execute the tool once to obtain authoritative evidence, then retain it through latest-only retries, including exhaustion without an accepted final response. Wrong task/tool corrections and malformed exhaustion have zero tool effects. Localhost wire capture proves complete assistant text is sent before terminal HTTP 400, without a third or shortened send; the step engine propagates the exact injected context exception.

Material implementation details / ordinary adaptations:

- The existing provider translator classifies HTTP 400 as `PERMANENT` / `INVALID_REQUEST`; the context-limit code and message remain intact in provider diagnostics. The test asserts that existing taxonomy and preserved diagnostic meaning instead of introducing a `CONTEXT_LIMIT` mapping.
- The corrected final-schema fixture constructs a new definition after changing its copied manifest, because `YamlSkillDefinition.manifest()` returns a defensive copy.
- Bare `StepActionValidatorTest` selection inside the combined command did not execute its nested-only cases. A dedicated wildcard selector executed all 30 cases. The testing instructions are updated accordingly.
- Documentation assessment remains **Affected**, with the two planned topic/index edits only. Final drift classification: **aligned** with executable behavior. No supported API/SPI signatures, Spring bean replacement points, configuration keys/defaults, Console boundary, journal policy, or neighboring workspace files changed.

Verification receipt:

1. Expected red reproduction before production changes: `./mvnw.cmd '-Dtest=OutputSchemaCallAdvisorTest,StepActionCorrectionTest,StepLoopMissionExecutionEngineTest' test > target/pr16-red.log 2>&1` — 78 tests, five assertion failures, zero errors. Failing names: `preservesWhitespaceOnlyCandidateExactly`, `replaysCompleteLargeUnicodeCandidateAndBoundsCorrectionOnly`, `missingAndUnmappableLocationsRetainHeadAndTailWithoutInventingCoordinates`, `reliableLocationRetainsMiddleFailureAndReportsOnlyAvailableCoordinates`, and `syntheticTrailingBraceRecoveryReplaysShortAndLongCandidatesWithoutRejectedToolCalls`. These show candidate clipping/omission, not compilation failures.
2. Intermediate runs of the same focused command into `target/pr16-green.log` failed while building new fixtures: an incorrect test helper name, then undeclared fixture schema fields/copied retry settings, then missing property-rendering/completed-evidence setup. `./mvnw.cmd '-Dtest=StepLoopMissionExecutionEngineTest#largeFinalSchemaRetriesReplaceCandidateAndRetainCompletedEvidence' test > target/pr16-engine.log 2>&1` also exposed that copied-manifest fixture setup. All were test setup/assertion mistakes, corrected before the final passing runs; no production failure is unresolved.
3. PASS: `./mvnw.cmd '-Dtest=OutputSchemaCallAdvisorTest,StepActionCorrectionTest,StepLoopMissionExecutionEngineTest' test > target/pr16-green.log 2>&1` — 82 tests, zero failures/errors/skips.
4. PASS: `./mvnw.cmd '-Dtest=OutputSchemaValidatorTest,StepActionValidatorTest,ExecutionCoordinatorOutputSchemaIntegrationTest,ModelAttemptCallAdvisorIntegrationTest,SpringAiProviderIntegrationTest,LoomspanPublicSurfaceArchitectureTest,StepLoopMissionExecutionEngineTest#invalidCorrectedToolIdentityStillExhaustsWithoutExecution' test > target/pr16-boundary.log 2>&1` — 48 tests, zero failures/errors/skips, including all eight mandatory supported-surface architecture checks. Nested action validators are verified separately below.
5. PASS: `./mvnw.cmd '-Dtest=StepActionValidatorTest*' test > target/pr16-action-validator.log 2>&1` — all 30 nested validator tests, zero failures/errors/skips.
6. PASS: `git diff --check`.
7. NOT RUN: `./mvnw.cmd test` — the two focused groups plus nested validators provide proportionate acceptance coverage; no unresolved evidence requires the unrelated full suite. No paid-provider/live-model calls; model-quality/citation experiments are outside scope. Optional developer checks: none.

All 160 final selected tests passed across the three commands. Public-surface/Spring-extension checks across the production diff confirm only internal changes and no compatibility machinery. Remaining assurance: fresh Step 5 review; implementation completion does not claim pipeline completion.
