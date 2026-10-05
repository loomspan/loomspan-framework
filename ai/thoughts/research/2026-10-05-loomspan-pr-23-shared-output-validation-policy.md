---
date: 2026-10-05T12:45:05-07:00
researcher: Codex (GPT-6)
git_commit: 35cded1dfe30ff467869139f5f69ee872cbe41a9
branch: main
repository: loomspan-framework
topic: "PR 23 shared output-validation policy, including declared output bindings"
tags: [research, codebase, output-validation, output-bindings, planning]
status: complete
last_updated: 2026-10-05
last_updated_by: Codex
---

# Research: shared output-validation policy

## Research question and execution scope

Map the existing ordinary/planning final-output policy and PR22 binding paths for the behavior-preserving internal extraction requested by `ai/thoughts/tickets/loomspan-pr-23-shared-output-validation-policy.md`.

The developer approved the **Full 5-Step Pipeline** with “proceed”. This is Step 1 in pipeline mode, profile `full`. The only preexisting dirty file at triage was the developer's expanded ticket; preserve that diff. Research changes only this artifact, without implementing or changing the ticket. Prerequisite PR22 is present as HEAD `35cded1dfe30ff467869139f5f69ee872cbe41a9` (“PR 22 — Assemble skill outputs through declared bindings”).

## Summary

Ordinary execution uses three nested Spring AI advisors. Planning final validation lives in `StepLoopMissionExecutionEngine`, not `DefaultPlanningService`, and uses one final-step loop with independent schema/evidence/linter counters. Schema, evidence, output projection, source capture, and binding composition already have shared authorities. Retry/status arithmetic, schema/linter outcome construction, issue rendering, and regex evaluation appear separately in the integrations.

The expanded binding scope is implemented at HEAD: ordinary full assembly performs schema recording plus `validateBoundPolicies`; planning full assembly composes then calls `requireCompleteOutputPolicies`, which repeats complete-schema validation through its normal final validators. Planning mixed contribution failure has separate schema accounting. These paths intentionally differ in recorded details, failure prefixes, retry statuses, and issue limits. The extraction must retain those differences and existing repeated validation calls.

PR23 remote contents/status remain unavailable: unauthenticated GitHub REST returned 404 and remote pull refs were absent. This does not establish that the PR never existed. The ticket is the operative requirement; no remote implementation or merge relationship is assumed. Current origin/main is `1b5c1a63bf0beb538119be05a8b5b361c96f8e72`, a verified ancestor exactly one commit behind HEAD.

## Investigation checklist

- [x] Read ticket and pipeline research/shared protocols.
- [x] Establish checkout, dirty attribution, prerequisite, remote PR availability.
- [x] Locate ordinary advisors, wiring, and policy ordering.
- [x] Trace planning final-step validation and binding completion.
- [x] Inventory source/projection authorities and forwarding bypass.
- [x] Inventory focused tests, contracts, diagnostic consumers and docs.
- [x] Gather metadata and persist findings for planning.

## Detailed findings

### Ordinary execution and advisor ordering

`DefaultSkillAdvisorResolver.java:100` creates schema/evidence/linter advisors with shared validators. It records schema/linter through `ExecutionStateService`; evidence uses callbacks with distinct success/failure metadata. The resolver's infrastructure bean is `LoomspanAiAutoConfiguration.java:50`, without a supported bean-replacement contract.

Advisor order values determine nesting. Unbound linter is outermost (precedence minus 100), schema next (minus 90), evidence innermost (minus 80). Responses therefore encounter evidence, schema, then linter; a correction by an outer advisor re-enters its downstream advisors. With bindings, schema moves to minus 70, so complete assembly occurs before evidence and linting see response text. The numerical order and reset behavior matter more than resolver insertion order.

`OutputSchemaCallAdvisor.java:96` skips marked planning calls, creates baseline schema guidance, and validates attempt 1. Binding mode captures the mission's single `OutputBindingComposition` once; schema guidance uses its projection and adds ownership instructions. Every candidate is composed, and `assembled.validation()` supplies schema validity. On success, the advisor replaces generation text with assembled output while preserving generation/output/chat metadata, tool calls, and media. On failure, it retains the original contribution as rejected-candidate evidence.

Schema failures are retryable while `attempt <= maxRetries`; attempt N+1 is exhausted. Outcomes use `retryCount = attempt - 1`. Passed outcomes contain no failure mode/issues; failed ordinary outcomes and advisor trace issues are limited to four. Exhaustion records on the session and throws `LoomspanOutputSchemaValidationException` with the complete candidate and all issues. Retry creates a fresh downstream chain and rebuilds from the baseline with only the latest assistant candidate and one correction user message; older corrections are not accumulated.

Schema correction formatting at `OutputSchemaCallAdvisor.java:350` uses failure-mode headings, JSON-quoted issue facts, detailed parser locations/fragments, at most four whole issue bullets, and a total 2,048-code-point bound. It removes whole bullets to fit and reports omitted count; it preserves the candidate independently. The tail differs for whole output versus model contribution. Logging uses its own compact issue rendering (including special unknown-property behavior). These are explicit resource/format controls, not content classification.

`EvidenceContractCallAdvisor.java:50` validates against mission successful direct-child names. It invokes pass/fail callbacks on every evaluation. Failures use the schema retry budget; exhaustion throws `LoomspanEvidenceValidationException` with full candidate/issues. Its system hint includes four issue messages, already-completed-work instructions, and raw-JSON correction instructions. Unlike schema, evidence hints accumulate through `currentRequest`.

`LinterCallAdvisor.java:74` uses precompiled `Pattern.matcher(candidate).matches()`. It records every schema-independent attempt/status, and accumulates its system hints. Unbound exhaustion returns the response with EXHAUSTED outcome; it does not throw. Ordinary schema/linter recorder wrappers tolerate absent managed session but propagate managed recorder failures. Evidence callbacks require the bound mission/session.

### Ordinary bound completion

`DefaultMissionExecutionEngine.java:85` captures composition after planning initialization (where applicable) and input materialization. Full assembly calls `composeEmpty()`, rejects invalid schema immediately with `binding_output_validation`, records schema PASSED attempt 1 using the configured budget, and invokes `validateBoundPolicies` before the single `RESULT_ASSEMBLED` event. It makes no model request or correction.

`validateBoundPolicies` (`:165`) evaluates evidence against successful direct children. Nonempty contracts record metadata containing only skillName; invalid evidence throws `binding_evidence_validation`. Regex uses the authored pattern, records PASSED or forced EXHAUSTED at attempt 1 irrespective of remaining budget, and preserves the assembled-specific detail strings. Failure throws `binding_linter_validation: Immutable assembled output failed configured regex linter.`

Mixed completion uses the ordinary advisors and, after the model interaction returns, inspects the last linter outcome (`:128`). EXHAUSTED throws `binding_linter_validation: Assembled output exhausted configured regex linter correction.` before assembly publication. Unbound ordinary completion still returns the exhausted linter text. Lifecycle checks and assembly publication timing belong to the engine.

### Planning execution and counter behavior

`SpringAiChatClientAssembler.java:113` strips all three final-output advisors when building step-mode interactions. `DefaultPlanningService` owns plan generation/acceptance, not final-output validation.

`StepLoopMissionExecutionEngine.java:850` runs assigned action steps and the final step. Each final step initializes schema, evidence and linter attempts to 1. Parsing/action validation has its own invalid-action allowance; final-output failures do not consume it. `:940` first validates the action envelope. Only an accepted FINAL_RESPONSE reaches composition/final validation, against extracted payload rather than envelope JSON. Strings are used as text, object payloads are serialized, null becomes empty text (`serializeFinalResponse`, `:1380`).

`validateFinalResponseForSkill` (`:1207`) runs schema, then evidence, then linter and short-circuits on failure. Successful checks keep their counters unchanged. Schema and linter failures return nextAttempt = attempt + 1, including exhaustion. Evidence failures advance only when retryable; exhausted evidence keeps its current attempt. Other counters remain unchanged. Planning schema outcomes retain all issues; evidence records attempt, maxRetries, claims, and skillName. Schema rejection text summarizes at most three issues using canonicalField when nonblank, otherwise path. Evidence rejection uses `EvidenceCoverageResult.retryFeedback()` (all messages joined with newlines). Linter uses authored failure text or a final-response fallback, plus its own passed detail.

`StepLoopMissionExecutionEngine.java:958` rejects exhausted final validation with `IllegalStateException("Final response validation exhausted at step ...")`, recording STEP_ACTION_REJECTED before the existing failure/frame flow. Retry recreates the final request, adding `StepActionCorrection` request text and JSON-quoted complete latest rejected action plus a separately bounded diagnostic. It stays inside the same final step and does not replay accepted tasks. `StepActionCorrection.java:40` owns envelope-specific correction text; `:50` limits the failure diagnostic to 2,048 characters.

### Planning binding completion and contribution failures

After all accepted work is joined and folded, `StepLoopMissionExecutionEngine.java:491` verifies accepted-plan identity, unchanged tasks, successful completion, and retained results. Full assembly captures composition, calls `composeEmpty`, rejects invalid result with `binding_output_contract`, then calls `requireCompleteOutputPolicies` (`:555`). That helper invokes ordinary planning final validators at counters 1/1/1. Thus it deliberately validates complete schema again after composition, records planning-specific outcomes/evidence, and rejects any failure immediately with `IllegalArgumentException("binding_output_contract: ...")`, even if the recorded validator status is RETRYING. There is no final model request or correction attempt. The assembly event follows all checks.

Mixed planning (`:946`) composes the serialized model contribution. Invalid composition enters `validateContributionFailure` (`:561`): record the full schema issue list using the normal schema budget/status, increment schemaAttempt, retain linter/evidence counters, and render the binding-specific rejection prefix. Valid composition enters full final validators, including the repeated schema check. Complete assembled text/node are used for evidence and regex validation. Publication occurs only after final-step success and accepted-plan recheck (`:532`).

### Existing composition/projection authorities

`OutputBindingComposition.java:18` captures native output schema, projection, and selected immutable sources. It validates bound-source native destination contracts before model work, validates bound evidence, verifies accepted producer work, retains the owning mission and captured plan, and fences changed plan/closed mission use.

`compose` (`:65`) decodes contribution once, enforces object shape and bound-destination ownership (including aliases, null/equal values, blocking ancestors), validates projected contribution, copies/inserts selected values, serializes complete output, then validates the original schema. `OutputBindingProjection` owns projection, contribution-required detection, reserved destination checks and guidance. `ChildInputBindingAssembler`, `AcceptedResultDecoder`, and `DeepInputValues` already own strict source selection/value fidelity. Neither extraction requires a second schema/evidence validator or new composition algorithm.

### Forwarding and protected boundaries

`StepLoopMissionExecutionEngine.java:504` returns the retained selected child's String directly after whole-work checks, records RESULT_FORWARDED, and bypasses final synthesis/policy checks. Forwarding catalog restrictions exclude parent output schema/retry/linter. The producer owns its validation. These branches and assigned/tool action guidance remain outside final-output policy integration.

## Executable regression inventory

These are source-inspected tests, not claimed test runs in this research step.

| Area | Existing focused evidence |
| --- | --- |
| Ordinary schema | `OutputSchemaCallAdvisorTest`: success, invalid/blank/canonical/nested failure, baseline/latest-only replay, code-point feedback bounds, full candidate fidelity, exhaustion/full exception candidate, bounded recorded issues, logs, advisor traces and recorder failure propagation |
| Ordinary linter | `LinterCallAdvisorTest`: success, hint preservation, retry/exhaustion return, each recorded outcome, bound session, advisor mutations and recorder failures |
| Ordinary evidence | `EvidenceContractAdvisorAdditionalTest#unsupportedRequiredClaimFailsAfterToolFreeRetriesAndPreservesExpressionDiagnostics`; `SkillAdvisorResolverEvidenceTraceTest`; `PlannerEvidenceFlowIntegrationTest` |
| Planning schema/evidence | `StepLoopMissionExecutionEngineTest:973` success after schema correction; `:1010` latest-candidate/full completed evidence and exhaustion; `:1080` independent evidence/schema budgets; `EvidencePlanningIntegrationTest` expression/trace semantics |
| Planning linter | `StepLoopMissionExecutionEngineTest:1584` linter correction success; `:1621` configured multi-retry budget |
| Planning bindings | `StepLoopMissionExecutionEngineTest:106` override correction/projected guidance; `:327` invalid retained results; `:348` joined producer/unrelated work; `:385` producer failure; `:414` full/mixed policies on exact decimals |
| Ordinary bindings | `DeclaredOutputBindingsOrdinaryIntegrationTest`: no-provider full input assembly; override correction; concurrent roots; bound linter terminal failure; source snapshot through reload; open/optional fields; blocking aliases; invalid immutable type/evidence with no fallback/event |
| Facade planning bindings | `DeclaredOutputBindingsPlanningIntegrationTest#assemblesUniqueProducersAfterAllWorkAndCorrectsOnlyModelFields`: full/mixed model, Java and REST producers; task/final counts, projected contribution and provenance |
| Source algorithms | `OutputBindingCompositionTest`, `OutputBindingIsolationTest`, `OutputBindingProjection` usage through composition tests, binding source fixtures |
| Forwarding | `StepLoopMissionExecutionEngineTest:145` through `:306`; `DesignatedChildResultIntegrationTest` producer-owned policies, exact model/Java/REST text, complete work and reload isolation |
| API classification | `LoomspanPublicSurfaceArchitectureTest`: closed API/integration lists, every public internal type with reason, no extra SPI and safe API signature types |

Planning/testing must determine the additional evidence needed for cross-validator nested ordering, all exhausted planning validators, mixed-bound linter terminal behavior and exact per-path recording/correction equivalence. These are coverage questions, not findings of changed behavior.

## Contract and exposure inventory

| Lens category | Current evidence and affected boundary |
| --- | --- |
| Application API | `ai.loomspan.api` closed allowlist (`LoomspanPublicSurfaceArchitectureTest.java:29`), SkillTemplate invocation/catalog observations. Ticket adds no supported API or signature changes. |
| Supported SPI | RestSkillHandler only; advisors, validators and engines are not supported extension contracts. |
| Configuration and manifest contracts | Existing output_schema, output_schema_max_retries, linter, output_bindings, output_from, evidence and planning semantics. Behavior must remain unchanged; no new keys/declaration shapes. |
| Persisted or serialized contracts | No new persisted contract. Returned JSON/text and complete binding value behavior are protected at invocation boundaries. |
| Ephemeral diagnostic formats | Schema/linter/evidence outcomes, advisor events, step events, assembly/forwarding events, exception text and candidate evidence. Preserve event timing/count, issue limits, distinct metadata/details. Canonical trace writers/readers and Console consume these. |
| Internal or accidentally exposed implementation | Public internal advisor classes/constructors/outcomes, runtime engines, validator seams and Spring infrastructure beans. Current cross-package call sites explain technical public access; they are internal and do not require compatibility shims. New public internal types require a reason in the architecture test's closed internal list (`:344`). |

No requested REST/SSE/acquisition/problem/NDJSON schema change or consoleCompatibilityVersion change exists. Console consumers remain protected through unchanged Java trace facts/events; `ConsoleTraceFixtureCorpusTest` covers assembly authority. Internal extraction must preserve those facts rather than introduce a coordinated Java/Go protocol change. `ExecutionJournalProjector` remains outside scope, including its existing derived-journal redaction exception.

## Documentation alignment and authoring impact

Read the checkout's `agent-skills/loomspan-docs/SKILL.md`, routed `skill-authoring/README.md`, `output-contracts.md`, `evidence-contracts.md`, and `source-verification.md`. Skill metadata and POM both identify `1.0.0-beta.8-SNAPSHOT`; source/docs come from the same checkout.

**Drift classification: aligned** for full/mixed assembly, source snapshots/ownership, projected model guidance, extracted planning final validation, supportability truth sets, final corrections using completed work, and exact forwarding with producer-owned policies. The topic documents do not fully specify every per-path diagnostic string or nested advisor reset; those details are established by live source/tests above. This lack of exact-detail coverage does not contradict documented semantics.

Authoring impact assessment: the ticket requests no changed author-visible syntax, guidance, validation, corrections, budgets, returned data or forwarding/assembly semantics. Documentation behavior can remain unchanged if the extraction preserves them. If named implementation anchors move, planning should account for focused anchor maintenance without introducing new authoring instructions or replacing intentionally different paths.

## Metadata, historical context and verification environment

`bash ai/scripts/spec_metadata.sh` succeeded: 2026-10-05 12:45:05 PDT, main, HEAD 35cded1dfe30ff467869139f5f69ee872cbe41a9. Researcher identified as Codex; no thoughts-status researcher was supplied by the script. `mvn -version` reports Maven 3.9.6 and JetBrains Java 21.0.2 on Windows. POM requires Java >=21 and Maven >=3.9.0. Research ran no tests or build; later stages own verification.

Remote relationship checks: `gh pr view` unavailable because gh is not on PATH; GitHub REST `GET https://api.github.com/repos/loomspan/loomspan-framework/pulls/23` returned 404; `git ls-remote origin refs/pull/23/head refs/pull/23/merge` succeeded with no matching refs. `git ls-remote origin HEAD refs/heads/main` returned 1b5c1a6; `git merge-base --is-ancestor 1b5c1a6 HEAD` succeeded and `git rev-list --count 1b5c1a6..HEAD` returned 1. The unavailable PR metadata does not alter the fully specified local ticket scope.

Historical supplement: `ai/thoughts/research/2026-10-05-loomspan-pr-22-declared-output-bindings.md` describes the pre-PR22 state, and its corresponding implementation/testing plans describe that prerequisite. Claims there that assembly was absent are historical; current source above is authoritative. PR23's developer-expanded ticket explicitly adds contribution failures and full-bound policies to its original ordinary/planning scope.

## Open questions for planning

1. Choose the cohesive shared mechanics boundary and its internal visibility, retaining orchestration, recording adapters and per-path formatting responsibilities as established above.
2. Define how ordinary four-issue outcomes versus planning full issue lists, forced terminal immutable-bound linter status versus planning retry arithmetic, and evidence's exhaustion next-attempt difference remain explicit.
3. Select focused characterization tests for presently less-explicit combined ordering/exhaustion/binding-policy diagnostics. Existing source/test inventory establishes the required behavior; no developer semantic decision remains.
4. Remote PR23 status/content is unavailable; do not infer it or use it as implementation authority. Continue from the local ticket and verified PR22 prerequisite.
