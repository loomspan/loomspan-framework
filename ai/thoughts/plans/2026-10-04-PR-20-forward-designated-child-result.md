# PR 20 — Designated Child Result Implementation Plan

## Overview

Add opt-in `output_from: {skill: exactDirectChildName}` to model-backed YAML planning skills. A forwarding parent still plans and executes meaningful orchestration, then returns its designated task's existing Framework String result unchanged. It makes no parent final-synthesis request and applies no parent final-output validation or correction. Existing synthesis skills retain their behavior.

Execution mode: pipeline; selected profile: `full` (Full 5-Step Pipeline). This plan and its testing companion complete Steps 2 and 3 only. The user authorized necessary Java–Go boundary changes; Step 4 owns implementation and verification. Additional user direction: “Let's try to fully meet our goal while incurring as little technical debt as we can.” Explicit compatibility decision: “we are in development, destructive changes welcome, so no compatibility shims”. This authorizes necessary coherent API/protocol breaks, not unrelated destructive filesystem or Git operations.

Planning checklist: ticket/research/policy read; production and test paths cross-checked; authoring router consulted; design and compatibility decisions settled; implementation phases defined; dedicated testing plan completed. No unresolved product decisions remain.

## Current State Analysis

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:311` loads normalized per-document definitions; `:395` checks raw trees and field applicability. Manifest field presence includes explicit null. Complete-set checks at `SkillGenerationManager.java:243` already resolve local children across Java, REST and model YAML, and startup, supplied validation and reload share that path.
- `src/main/java/ai/loomspan/internal/runtime/planning/DefaultPlanningService.java:270` preflights required-child visibility, validates structure/counts/evidence, and permits one combined corrective attempt before plan storage. `PlanTaskConstraintValidator.java:20` already enforces one matching task for `required: true, max_tasks: 1`.
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:327` executes every accepted ordered unit, joins groups, folds complete results and propagates failure. `:374` reserves a synthesis slot; `:471` always performs final synthesis. `MissionContext.java:115` owns complete direct results keyed by task ID, independently of progress summaries and nested missions.
- `src/main/java/ai/loomspan/internal/skill/YamlSkillDefinition.java:93` exposes authored schema to actual validators and prompts. Replacing it with inherited metadata would incorrectly rerun validation and reinterpret child-local evidence at the parent.
- `src/main/java/ai/loomspan/internal/skill/SkillGeneration.java:18` freezes declarations and capability identity. Existing execution bindings retain this generation across publication and concurrent/nested work.
- `src/main/java/ai/loomspan/api/SkillDescriptor.java:6` and `internal/core/CapabilityToolDescriptor.java:5` expose input metadata only. Console skill details preserve original declaration text; resolved output metadata currently has no usable catalog/planner path.
- Java return values are Jackson JSON text (`SkillMethodBeanPostProcessor.java:505`), including quoted Java Strings. REST handlers return non-null text unchanged. `SkillTemplate.invoke()` returns String; forwarding must preserve those boundaries.
- Trace record types and live activity kinds are closed Java/Go vocabularies (`internal/core/TraceRecordType.java`, `loomspan-console/internal/traceanalysis/enums.go:8`). Console must receive the authoritative forwarding decision rather than infer it from an absent model response.

## Desired End State

Validated forwarding declarations are bound to an immutable complete generation. Each accepted plan has exactly one designated direct-child task. The coordinator binds its task ID before execution, traverses all accepted work through existing lifecycle/authorization/limits, checks successful complete state and the exact task's retained result, records forwarding provenance, and returns that String without parsing or rewriting it. A forwarding chain follows the same path at each boundary.

Available effective output schema is normalized, generation-bound metadata derived through forwarding edges. An unavailable schema stays unspecified. Authored schema remains the sole validator input on ordinary model skills; evidence and linting remain owned by the actual producing child. Callers, Console tooling and planners can inspect effective schema metadata, with an explicit distinction between metadata and validation guarantees.

### Key Discoveries

- Reuse accepted task identity and `CompletedTaskResult`, not global capability names, result indexes, completion order or model references.
- Reuse complete-set checking and generation capture for graph validation and schema derivation; no runtime traversal of the active catalog.
- A forwarding parent needs N assignment slots for N tasks; synthesis still needs N+1. Planning and existing correction/provider budgets remain independently applicable.
- The existing journal projection exception remains confined to `ExecutionJournalProjector`. Canonical traces, prompts, results and Console preserve content fidelity and explicit access boundaries.

## What We're NOT Doing

No multiple-invocation selectors, field extraction, result transformation, aggregation, automatic forwarding detection, fallback synthesis, reference-suite prompt tuning/replay refresh, application migration, provider-specific advice or new Java/REST output validation. No new SPI, bean replacement contract, historical trace reader or cross-version migration. No early return when the selected task finishes.

## Skill-Authoring Documentation Impact

**Impact: Affected.** Syntax, declaration applicability, planning completion, step budgets, schema metadata and diagnostic interpretation change.

- **Documents to update:** `agent-skills/loomspan-docs/references/skill-authoring/README.md`, `mental-model.md`, `output-contracts.md`, `planning-task-constraints.md`, `planning-concurrency.md`, `rest-skills.md`, `traces-and-debugging.md`, `checklists/evaluate-a-skill-design.md`; Java API `catalog-and-validation.md`, `invocation.md` and `compatibility-and-boundaries.md`; repository `README.md` and `docs/architecture.md` where completion/metadata are summarized.
- **Supporting evidence:** catalog/definition/generation tests; planning cardinality/visibility tests; forwarding engine and supported-facade HTTP integration tests; child schema integration; generation/reload isolation; Java-written trace/REST/SSE corpora and Go/browser projections. The testing companion maps the specific claims to executable cases.
- **Coverage table update:** Required; output contracts gains forwarding, derived metadata and missing-schema coverage, planning gains alternate completion and step accounting, diagnostics gains authoritative forwarding.
- **LLM-first usability:** Extend the existing output-contracts topic and route forwarding there, with a decision table, minimal general model/Java/REST examples, precise required/prohibited fields, isolated validation semantics, named tests, and links to planning constraints/debugging. Do not create a parallel reference authority. Label illustrative fragments and retain model-independent limitations.
- **Drift classification:** Aligned for current executable behavior: mandatory synthesis and N+1 are accurate today. New forwarding is absent because it is a new feature. Rewrite unconditional synthesis/budget claims atomically once implementation establishes the alternate path. No existing defect or unresolved semantic conflict is being inferred from documentation.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | `SkillTemplate` String return remains; `SkillDescriptor` is allowlisted supported metadata. Callers need the newly requested effective output metadata. | Add nullable String `outputSchema` component to existing descriptor in place; update every constructor consumer to five arguments. The four-argument constructor and record-pattern arity intentionally change under explicit user authorization. Existing accessors/invocation/catalog methods remain. Record equality/toString/serialized shape now include output metadata. |
| Supported SPI | `RestSkillHandler` is the sole SPI and already returns String. | Preserve its signature, handling, authentication and generation identity. No new validation policy or SPI. |
| Configuration and manifest contracts | New opt-in `output_from` on explicit model planners; existing synthesis declarations protected by ticket. | Add strict validation. Forwarding disallows authored `output_schema`, `output_schema_max_retries` and `linter`, including explicit null declarations. Existing definitions without `output_from` keep current rules. |
| Persisted or serialized contracts | Original YAML is unchanged inspection data. Portable canonical trace files support only exact-version transfer; Console indexes are ephemeral. | Current trace vocabulary changes atomically. No historical readers, durable-index migration or old/new dual formats. No cross-version promise. |
| Ephemeral diagnostic formats | New authoritative forwarding record/activity/journal details and effective schema on Console skill detail. Closed readers and fixtures are consumers. | Coordinate Java/Go/TypeScript, adapters, schemas, corpus and diagnostic guidance together; maintain ordering/failure/access/fidelity. Preserve existing journal exception only. |
| Internal or accidentally exposed implementation | Manifest/definition/tool metadata, complete-set result, runtime completion, catalog DTOs and Spring wiring. | Change internal signatures and callers atomically; no internal compatibility overloads introduced for this feature. No bean override contract. |

- **Evidence of supported contracts:** ticket acceptance criteria; `AGENTS.md`; `LoomspanPublicSurfaceArchitectureTest`; README supported API; current authoring/Java API guidance; Console AGENTS and corpus consumers.
- **Intentional compatibility changes:** supported descriptor constructor/record shape changes atomically, with source/binary/record-pattern impact accepted by the user's no-shim decision; strict applicability only for new forwarding declarations; current-version trace/live and skill-detail boundary expansion. No existing ordinary YAML contract is intentionally removed.
- **In-repository consumers:** descriptor construction/tests; capability descriptor construction/tests; bound tools and planning prompts; registered catalogs/web DTO mapping; Console observability/applicationclient, traceanalysis/live, browserapi/MCP schemas and skill views; TS contracts/presentation/tests; fixture corpora and knowledge base.
- **Public-surface delta:** one nullable String record component/accessor and five-argument canonical constructor on existing `ai.loomspan.api.SkillDescriptor`; remove the four-argument constructor rather than preserving an obsolete shape. No new top-level API type, leaked internal signature or Spring extension point. Update README and supported-surface assertions deliberately.
- **Shim decision: No shim.** The user's explicit development-stage decision overrides conditional compatibility-preservation guidance. Change the existing record and every in-repository consumer/test/document atomically; do not add compatibility constructors, aliases, adapters, fallbacks or dual metadata representations. External consumers constructing or destructuring the record must rebuild/update to the five-component shape. No unrelated supported entry point is changed.
- **Java-to-Go boundary coordination: Required.** `RESULT_FORWARDED` NDJSON/live vocabulary, task identity details, Console skill-detail schema metadata, fixtures, outward browser/MCP schemas and TypeScript consumers ship together. REST/SSE acquisition/error/access behavior stays protected.
- **Compatibility marker decision:** Keep the coordinated unreleased `1.0.0-beta.8-SNAPSHOT` product version. The marker derives from the project version rather than an independent schema counter; `docs/upgrades.md` explicitly says snapshots are not immutable. This feature does not create a release or bump all product/package versions. Exact release-string mismatch/missing rejection and development complete-validation behavior remain required; new corpora describe this checkout only. A future release uses the normal coordinated version workflow, never a compatibility range.
- **Pipeline notes alignment: Aligned with recorded authorization.** Ticket has no dedicated notes, but Step 1 records the user's explicit authorization for necessary Java–Go changes, and this plan records the subsequent explicit no-shim decision. The plan stays within that scope and the requested metadata/result semantics.

## Implementation Approach

Use one new local manifest concept and one authoritative completion event. Derive metadata once in complete-set preparation; reuse generation ownership, task constraints, accepted assignments, result retention, unit join/failure ordering and write fencing. Do not create a second executor or result store.

Alternatives rejected: prompting a parent to copy leaves the copying opportunity and model cost; global name lookup loses task/run identity; substituting inherited schema into `YamlSkillDefinition.outputSchema()` adds duplicate validation; dynamic active-catalog lookup breaks captured-generation consistency; interpreting Java/REST business fields changes existing result semantics; an extra public metadata type/SPI is unnecessary when the existing catalog descriptor can carry one nullable value.

## Phase 1: Validate Declarations and Resolve Generation Metadata

### Overview

Make the declaration strict, resolve chains/cycles in every complete-set path, and expose metadata without changing validation ownership.

### Changes Required

1. **Manifest and catalog:** `src/main/java/ai/loomspan/internal/skill/YamlSkillManifest.java`, `YamlSkillCatalog.java`, `YamlSkillDefinition.java`.
   - Add presence-tracked `OUTPUT_FROM` and typed `{skill: String}` block. Check raw declaration before binding: only an object with exactly `skill`; reject null/scalar/list/unknown fields, null/blank/nonexact/invalid names. Explicit `planning_mode: true` is mandatory; `rest: true` forbids it regardless of value. Keep configured model mandatory because orchestration still uses a model.
   - Reject co-declaration with `output_schema`, `output_schema_max_retries`, or `linter` by presence, rather than silently dropping authored policies. A final-output linter/correction belongs on the actual producing child or an ordinary synthesis parent. This restricts new declarations, not existing synthesis skills.
   - Require the selected child to be in this parent's structured allowlist with explicit `required: true` and `max_tasks: 1`; `min_tasks: 1` alone is not a substitute. Existing bounds validation still applies. Report structured `output_from.skill` or conflicting-field errors through current paths.
   - Keep `outputSchema()` explicitly authored/validation-owned; use an exact nullable selected-child accessor. Maintain defensive copying and field-presence invariants.
2. **Complete generation:** `SkillGenerationManager.java`, `SkillGeneration.java` and an internal resolver colocated in `internal/skill` if a focused helper improves readability.
   - During `check()`, resolve forwarding edges against the complete candidate set including fixed Java skills. Validate unavailable direct targets and reject self/multinode cycles with source/path-associated issues. Suppress cascading unknown-target errors for already failed documents as current child validation does.
   - Use memoized DFS with visiting/resolved states over forwarding edges, not arbitrary allowed-child cycles. Ordinary recursive child graphs are outside this rejection rule. Resolve terminal authored model schema, or unspecified for Java/REST/no-schema model leaves. Carry resolved metadata through checked preparation; validation-only and reload run the same resolution and reject invalid candidates before activation.
   - Store immutable normalized schema JSON on generation-created capability/tool metadata. Omit child-local `evidence` orchestration annotations from schema metadata; preserve actual shape/constraints/descriptions, with provider-neutral nullable semantics clearly documented. Do not compile inherited evidence at the forwarding parent. Do not infer Java/REST schemas from return types or transport text.
3. **Metadata consumers:** `internal/core/CapabilityToolDescriptor.java`, `internal/runtime/tool/BoundCapability.java`, `internal/skillapi/DefaultSkillCatalog.java`, `api/SkillDescriptor.java`, `internal/runtime/planning/DefaultPlanningService.java`.
   - Add optional effective output-schema String to the existing internal tool descriptor, update all internal construction sites, and expose it through bound metadata. Java/REST/no-schema default is null. Generation resolution is its only authority.
   - Add nullable `SkillDescriptor.outputSchema` in place and update all construction sites to the five-argument canonical constructor. Catalog snapshots expose effective metadata for ordinary and forwarding YAML. Validate non-null metadata as nonblank JSON-object text on internal production boundaries; unspecified is null, never fabricated `{}`.
   - Render available effective output metadata in visible tool descriptions for planners, with clear output-vs-input labels; preserve source/local authorization boundaries. Do not send output schema as provider tool input parameters or a new validator policy. Planning prompt should state the designated exact child and that runtime completion forwards its result; preserve all orchestration objectives and task count guidance.

### Success Criteria

- [x] Focused manifest, definition, generation, catalog and planning tests cover accepted declarations, precise invalid paths, chains/cycles, absent schemas and authored validation separation.
- [x] Startup, public validation and prepared reload agree; invalid reload leaves active generation unchanged.
- [x] `./mvnw.cmd -Dtest=LoomspanPublicSurfaceArchitectureTest test` passes with deliberate descriptor boundary assertions and no new SPI/internal leaks.
- [x] Existing ordinary schema/evidence/linter tests remain valid; descriptor tests exercise the new canonical shape and absence of a compatibility constructor.

## Phase 2: Complete by the Exact Mission Task Result

### Overview

Add a narrow completion branch to the existing coordinator after ordinary execution; retain lifecycle gates and full work completion.

### Changes Required

1. **Step loop:** `internal/runtime/step/StepLoopMissionExecutionEngine.java`.
   - At accepted-plan traversal, resolve exactly one task whose exact capability name matches the validated declaration and retain its accepted ID. Missing/multiple matches fail closed even if an internal test bypassed planning. Reuse plan count validation for authored/model errors rather than introducing a separate correction budget.
   - Compute `finalSynthesisSlots = forwarding ? 0 : 1`. Before admitting an entire unit, require assignments consumed plus complete width plus that reservation to fit `max_steps`; preserve group atomic admission and messages that explain the actual mode. No synthetic forwarding step is charged.
   - Run every accepted unit unchanged. After traversal, use current binding/lifecycle gates and ensure the same plan and all accepted task IDs/capability bindings are successfully COMPLETED, with no stale/failed work. Do not make designated-child placement a new ordering constraint; it can complete before other required work, but cannot finish the parent early.
   - In forwarding mode, retrieve the retained result by accepted task ID, validate exact expected skill identity and unique presence, record forwarding provenance under current writable permissions, and return its String. Empty/whitespace, JSON string quoting, long values and structured array order remain intact. No parse/reserialize, field selection, citation changes, parent final prompt, schema/evidence/linter check or correction path.
   - Preserve ordinary `executeOneStep(... assignment=null)` and all final validators for nonforwarding skills. Failures in child execution, selection, limit admission or cancellation use existing terminal recording and exception paths; no fallback.
2. **Mission result access:** `internal/core/MissionContext.java` only if an exact-ID accessor avoids copying/scanning the retained collection. Keep one result store, defensive state, duplicate/null prohibitions and existing write fences. No active catalog lookups.
3. **Supported-facade integration:** Add deterministic HTTP-model integration fixtures alongside `src/test/java/ai/loomspan/integration/PlannerEvidenceFlowIntegrationTest.java`; exercise actual Java annotation and REST handler boundaries, model correction, nested forwarding and no final parent request.

### Success Criteria

- [x] N forwarding tasks succeed at `max_steps: N`; N+1 ordinary synthesis tests still pass; insufficient full-group budget admits no members.
- [x] All accepted work, normal joins, failures, timeouts, quota/depth failures and ancestor cutoff remain enforced.
- [x] Actual `SkillTemplate` returns exact existing model/Java/REST text; schema correction occurs at the producing model only, and no parent final model/validation request occurs.
- [x] Parallel roots and midexecution generation publication retain their own selected results and metadata.

## Phase 3: Emit and Consume Authoritative Forwarding Diagnostics

### Overview

Record the decision where completion occurs and keep Framework/Console tooling coherent.

### Changes Required

1. **Canonical owner:** Extend `internal/core/TraceRecordType.java` with `RESULT_FORWARDED` and the existing execution state/trace recorder abstraction with a narrow forwarding recorder operation. Emit once at the parent mission frame after successful full work and exact selection, before successful mission frame closure. Include `skillName` (parent), `planId`, `linkedTaskId` (accepted selected ID), and `capabilityName` (selected direct child), with captured generation established by the trace/binding. No STEP_EXECUTION/MODEL_CALL frame, FINAL_RESPONSE action, provider usage or additional model response is invented. Existing canonical tool result already retains the result; provenance need not duplicate the entire output.
2. **Projections:** `internal/runtime/observation/LiveActivityProjector.java`, activity kind, `internal/runtime/trace/ExecutionJournalProjector.java`, `internal/core/JournalEntryType.java`. Project `RESULT_FORWARDED` as an accurately named forwarding activity/journal event with scalar task/plan/skill details and useful summary. Preserve existing journal transformation rules; canonical data and returned result remain unchanged. Remove the projector's unused `previousRecord` argument/local plumbing while editing it (`appendRecord` currently never uses it); no compatibility preservation is needed for this private dead parameter.
3. **Skill inspection:** `internal/runtime/observation/catalog/RegisteredSkillEntry.java`, `DefaultRegisteredSkillCatalog.java`, `internal/observability/web/dto/ObservabilityDtos.java`, `ObservabilityDtoMapper.java`. Add nullable `outputSchema` only to skill detail; retain exact original YAML/source location and list pagination. Use the same generation metadata authority as the public catalog.
4. **Console:** Update `loomspan-console/internal/traceanalysis/enums.go` and affected parse/index/record-detail mapping; `internal/live/dto.go`, observability DTO validation and skill adapter; `internal/mcpadapter` record/activity vocabulary and skill output schemas; `internal/browserapi`; `web/src/api/contracts.ts`, forwarding activity presentation, trace record details and skill metadata presentation. Show designated task identity and schema as metadata, not an invented final model response or validation guarantee. Generic record rows may remain generic where already sufficient; typed views must retain authoritative fields.
5. **Executable boundary corpus:** Add successful single/nested forwarding trace cases with child result and posttask completion provenance, corresponding expected projections, live SSE activity and skill-detail metadata cases to Java corpus writers and Go readers. Keep LF fixtures and regenerate through existing test switches. Include malformed forwarding identity/metadata cases where new typed projections enforce them. Update corpus registries/hashes used by build verification if touched. Keep acquisition/access/error/exact marker rejection unchanged; no legacy fixtures or fallback reader.

### Success Criteria

- [x] Canonical event, observer journal and live activity identify the exact task after required success, without phantom provider usage; cancellation/failure produces no successful forwarding event.
- [x] Java corpus tests verify committed trace/REST/SSE fixtures; Go reads and projects the same cases.
- [x] Browser/MCP schema validation and frontend presentation tests accept forwarding vocabulary and nullable effective metadata.
- [x] Exact resolved-version mismatch and missing marker rejection continue for adapter and portable traces; development follows normal validation.
- [x] `go test ./...` and `go run ./internal/buildtool verify` pass from `loomspan-console`; Java architecture test passes after production type changes.

## Phase 4: Teach the Feature and Complete Verification

### Changes Required

- Update every document enumerated in Skill-Authoring Documentation Impact with implemented syntax and named executable anchors. In output-contracts, distinguish synthesis (combine/interpret; authored schema) from forwarding (one child already supplies full answer; meaningful orchestration retained). Recommend reconsidering wrappers that add no responsibility.
- Include three minimal, general, accepted parent/child examples: a model child with authored schema; an annotation-only Java result with unspecified schema and existing Jackson text semantics; a REST leaf returning handler text with unspecified schema. Include a short forwarding chain and limitations. Validate representative YAML through production loader tests rather than presenting unexecuted examples as shipped guarantees.
- State direct-child scope, explicit required uniqueness, all-work completion/failure/cancellation, no fallback, exact result guarantee, derived metadata/no duplicate validation, no new Java/REST guarantees, and parent linter/retry inapplicability. Remove instructions to reproduce final child output from forwarding prompts while retaining input preparation, explicit child arguments and genuine orchestration duties.
- Update unconditional final-synthesis and N+1 language in mental model/concurrency/architecture, public catalog reference and README descriptor signature, Console trace/debug guidance. Retain ordinary synthesis examples and accuracy limitations. Update authoring routing and coverage tables.
- Execute the dedicated testing plan's focused, full Java, Console verification and version/package consistency commands. Record actual outcomes and any environmental limitations in Step 4, not claims of passes from this planning stage.

### Success Criteria

- [x] Guidance loaded only through output-contracts and routed planning/debug documents suffices to author the supported feature without source inspection.
- [x] All three child examples and chain behavior have executable evidence; no application-specific worked answers or provider claims enter reusable docs.
- [x] Full automated exit criteria in the companion testing plan pass; unchanged ordinary synthesis and supported APIs/SPI remain covered.

## Testing Strategy

Use strict manifest/generation unit tests first, coordinator/lifecycle tests for exact task selection and admission, actual HTTP-model supported-facade integration for type fidelity and absent parent final requests, and synchronized Java-written/Go-read diagnostics fixtures. Use deterministic latches for concurrency and cancellation; avoid sleeps and live-provider dependence. See `2026-10-04-PR-20-forward-designated-child-result-testing.md` for named tests, red-first evidence and exact commands.

## Performance Considerations

Memoized forwarding-graph resolution is linear in definitions plus forwarding edges per candidate generation. Completion does not copy/parse result text or call a model. Complete prior-unit result delivery to assigned steps remains as today. Schema metadata adds planner/catalog payload size only when available; do not silently clip constraints. Existing resource limits remain explicit.

## Migration Notes

Existing skills require no changes. Authors opt in, remove parent schema/linter/retry declarations and exact-copy instructions, and ensure the unique required direct child produces the complete intended result. Java/REST results retain their original representation. Applications constructing/destructuring `SkillDescriptor` update to its five-component shape and rebuild; the explicitly authorized development change supplies no compatibility constructor. Deploy matching Framework/Console from the coordinated checkout; exact-version portable diagnostic transfer remains the only supported transfer contract. No application rollout or version release is performed by this ticket.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-20-forward-designated-child-result.md`.
- Research: `ai/thoughts/research/2026-10-04-PR-20-forward-designated-child-result.md`.
- Policy: `AGENTS.md`, `loomspan-console/AGENTS.md`, `ai/thoughts/framework-feature-design-lens.md`.
- Commands: `ai/commands/2_create_plan.md`, `3_testing_plan.md`, `shared/automation-protocol.md`, `shared/loomspan-docs-protocol.md`.
- Similar execution evidence: `StepLoopMissionExecutionEngineTest`, `PlannerEvidenceFlowIntegrationTest`, `SkillGenerationExecutionIntegrationTest`, `ConsoleTraceFixtureCorpusTest`, `ConsoleRestFixtureCorpusTest`, `ConsoleSseFixtureCorpusTest`.


## Step 4 Execution Evidence

The approved no-shim decision is implemented: `SkillDescriptor` has five canonical components and one constructor. Internal metadata/skill-detail constructors and all repository consumers changed atomically. The candidate-generation checker resolves normalized schema metadata once, omits child-local evidence, and retains authored definition schemas for actual producer validation. No new API type, SPI, bean override path, synthesis fallback or alternate result store was introduced.

Runtime binds the designated accepted task ID before traversal, reserves zero synthesis slots, executes every accepted unit, and checks the unchanged valid plan plus complete task/result bindings under the existing writable fence. Provenance uses the current mission frame ID. The journal's unused previous-record plumbing was removed while preserving its existing transformation rules.

Completed verification:

- Declaration red command failed on the pre-feature unknown `output_from`, as intended.
- Runtime red command failed before completion implementation because one assignment still reserved a synthesis slot. An earlier compile-only attempt exposed one missed constructor caller, corrected before obtaining the behavioral red.
- Runtime/API/supported-facade focused run passed 90 tests (72 engine, 10 API, 8 integration), zero failures/errors. Earlier focused runs exposed the new unit fixture's mismatched root-frame identity and an obsolete four-component API assertion; both were corrected without production fallback.
- Java trace/REST/SSE corpus regeneration passed 19 tests; the read-only corpus/journal/live suite passed 49 tests. The initial new trace fixtures failed strict Go plan-lineage validation; the writer was corrected to use owned PLANNING frames and actual accepted attempts, and corrected single/nested fixtures now pass strict Go projections. Forwarding adds no model attempts or usage beyond ordinary planning/assignment/producer work.
- Version check passed at `1.0.0-beta.8-SNAPSHOT`; Python script unittest discovery passed all 13 tests.
- Final `go test ./...` and `go run ./internal/buildtool verify` passed against corrected Java-written corpora; verify includes frontend typecheck, 522 tests/coverage, Vite build and exact fixture/evaluation checks. No fixture registry hash changes were needed. Earlier full Java runs exposed stale catalog mocks (unstubbed tool metadata, then an unfinished Mockito stub), corrected and narrowly verified at 3/3. A subsequent full run executed 1,317 tests (2 skipped) with one failure: the unchanged invalid-action exhaustion case hit its 5-second mission deadline during a 31.01-second gap in its second immediate queued model response. Its trace still rejects both invalid actions and joins the failed task; the timeout won cancellation arbitration. The unchanged isolated case passed 1/1 in 1.029 seconds. This suggests an environmental scheduling/I/O pause but does not establish its JVM cause; the subsequent unchanged full verification passed all 1,317 tests (zero failures/errors, 2 existing skips) in 3:01. No production/test invariant was changed for this transient failure. Task-owned Java logs live under ignored `target/pr20-verification/`, not the repository root.

Acceptance evidence anchors:

| Ticket criterion | Executable evidence |
| --- | --- |
| Exact model/Java/REST result, prerequisites and chains; no parent synthesis | `DesignatedChildResultIntegrationTest#forwardsModelJavaAndRestResultsThroughSkillTemplate`, `#forwardingChainRetainsProducerValidationAndExactResult`; `StepLoopMissionExecutionEngineTest#forwardingRunsPrerequisiteBeforeSelectedTask`, `#forwardingPreservesEveryDirectResultString` |
| Complete work, failures, cancellation and no fallback | Engine `#forwardingWaitsForWholeSelectedUnitAndLaterWork`, `#forwardingFailureNeverProducesParentSuccess`, `#forwardingRejectsInvalidRetainedCompletion`, parameterized `#timeoutCutoffSuppressesLateWorkerWritesAfterCallerReturns` |
| Strict declaration/selection, cycles, visibility and isolated captured execution | `YamlSkillCatalogTests#rejectsInvalidForwardingDeclarations`; `SkillGenerationManagerTest#rejectsForwardingCyclesAndMissingTargetsWithoutActivating`; existing planning cardinality/visibility tests; integration `#parallelRootsForwardOnlyTheirOwnTaskResults`, `#runningForwardingTreeKeepsCapturedSchemaAndTargetAfterReload` |
| Available/unspecified metadata; producer-only validation | Generation `#resolvesForwardingMetadataThroughChainsWithoutInheritingEvidence`; corrected producing-model and chain integration cases |
| Ordinary synthesis and closed supported API/SPI | Integration `#ordinarySynthesisRemainsAvailable`, existing engine synthesis/budget/validation tests; `ApplicationApiValueTest#publicCatalogAndValidationExposeOnlyTheTicketedShape`; `LoomspanPublicSurfaceArchitectureTest` |
| Canonical/journal/live/Console provenance | `ConsoleTraceFixtureCorpusTest#forwardingCorpusRecordsExactDecisionAfterAllWorkAndBeforeMissionClosure`; engine journal assertion; `LiveActivityProjectorTest#projectsForwardingWithoutParentModelAttribution`; Java corpora and Go forwarding identity/skill-detail/activity projection cases |
| Author guidance for all child kinds and completion choices | Updated output-contracts routing/topic and every planned reference; examples match executed loader/facade paths. Drift classification: aligned with shipped behavior. |

No nonautomatable developer check is required. Full-profile final completion remains subject to fresh Step 5 review.

Final receipts: `./mvnw.cmd test` exited 0 with 1,317 tests, 0 failures, 0 errors and 2 skipped, including `LoomspanPublicSurfaceArchitectureTest` (8 passed), the engine suite (76 passed) and facade integration (8 passed). `git diff --check` passed. Standard Go/buildtool and Java corpus checks above passed against the final implementation. No new Console navigation path or shared-state ownership was introduced, so conditional Playwright navigation and Go race checks were not run; standard verification covers affected presentation and boundaries. No commit was made.
