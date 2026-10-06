# PR 24 Direct Dispatch Implementation Plan

## Overview

Automatically remove the parent dispatch-only model interaction when a ready accepted assignment has no possible model-authored input. Keep planning, child reasoning, validation, binding assembly, authorization, lifecycle, accounting and scheduling on their current paths. Pipeline profile: `full` (approved). The developer explicitly authorized development-stage breaking changes and **no compatibility shims**.

Planning checklist completed: receiving/projection semantics, unsupported schema preservation, nested presence, execution entry, model hooks, diagnostics/Console, offline tests and documentation. This stage changes planning artifacts only.

## Current State Analysis

- `StepLoopMissionExecutionEngine.java:839` opens a normal step frame, then every assignment calls the parent model at line 903. `StepActionValidator.java:64` already requires the accepted exact task/capability and IN_PROGRESS status. `executeToolAction` at line 1096 reaches `BoundCapability.invokeAssigned` and the ordinary invoker.
- `ChildInputBindingProjection.java:14` is the existing shared authority for projected argument shape and prohibited binding overrides. `SkillInputContract.java:32` checks empty-input requiredness only; that does not prove contribution absence.
- `SkillInputContractResolver.java:117` discards unsupported raw JSON Schema keywords. Proof cannot infer support merely from the remaining parsed node. Explicit closed empty input differs from absent/generic input.
- `DefaultCapabilityInvoker.java:81` resolves bindings from owning mission input and accepted pre-unit results, validates exact full input, records provenance, then enters the router. `CapabilityExecutionRouter.java:53`, `ExecutionCoordinator.java:84` and `MissionWorkExecutor.java:36` provide ordinary access/generation/lifecycle enforcement.
- `ProviderAttemptCallAdvisor.java:48` owns real sends, interruption checks, reservations and accounting. Removing a send intentionally removes model-request-specific hooks. There is no supported arbitrary advisor or internal bean-replacement contract.
- `loomspan-console/web/src/observability/TraceRecords.tsx:1182` incorrectly assumes STEP_STARTED precedes task selection. Update this existing raw-record presentation for authoritative assigned dispatch metadata. Go already reads the existing record vocabulary and exact raw records.

## Desired End State

Accepted fully bound closed assignments and explicit closed zero-input assignments execute without parent dispatch calls. Remaining assignments use today's model generation/correction. Binding or invocation failure on the direct path terminates through normal task failure; it never requests model repair. Controlled offline workflows establish exact physical call reductions, complete results, honest records and retained controls.

### Key Discoveries

`ChildInputBindingAssembler.java:15` distinguishes absence/null and creates nested ancestors. Model-created ancestor presence can be meaningful; no general schema solver is warranted. The input binding integration example already contains a top-level fully bound producer and a consumer with optional model reasoning, making it a useful mixed-path fixture. Its YAML extraction test expects three complete declarations; preserve that complete example and explain the new rule within it.

## What We're NOT Doing

No new YAML execution flag, rollout switch, supported API/SPI, task selection/conditional execution, approval mechanism, retries of business side effects, concurrency expansion, provider routing, paid evaluations, sibling reference-suite edits, historical fixture migration, commit/push or release. Do not turn the historical eight-call sample into a required tool allowlist or savings promise.

## Skill-Authoring Documentation Impact

**Impact: Affected.** Authors must understand automatic dispatch, conservative exclusions, unchanged child reasoning and the absence of eliminated model hooks/usage.

- **Documents to update:** root `README.md`; `agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md`, `planning-concurrency.md`, `traces-and-debugging.md`, and its `README.md` routing/coverage table. Add a short clarification to `execution-limits.md` where provider-call counting is explained. Update Java observation guidance only where it claims model lifecycle for every assigned task.
- **Supporting evidence:** projection/resolver tests; step engine tests; `DeclaredChildInputBindingsIntegrationTest`; canonical trace and observer tests; Console step/action view tests.
- **Coverage table update:** required for declared bindings, planning and debugging; existing topics gain verified automatic-dispatch behavior.
- **LLM-first usability:** put the rule and exclusions in bindings; link planning/debugging instead of duplicating schema details. Reuse the existing three-file example: producer `/caseId` is fully bound; consumer's optional `candidateReasoning` keeps model dispatch. Explicitly state that a model-backed producer still reasons and validates its output.
- **Drift:** inspected present binding/planning/trace mechanics are aligned with current source. New dispatch prose is prospective behavior. Console's no-selected-task message is existing presentation drift against accepted assigned identity and will be corrected in scope.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | `SkillTemplate` and immutable observer values remain supported; task/tool/skill start and terminal observations must remain coherent. | Keep signatures and observation obligations; actual model observations only when a model runs. |
| Supported SPI | `RestSkillHandler` remains the sole Java SPI; handler authorization/business approval must run normally. | No SPI delta. |
| Configuration and manifest | Existing `input_schema`, `input_bindings`, planning and limits determine automatic behavior. | Intentional call-count/hook change authorized by ticket; no new keys. |
| Persisted or serialized | No business persistence change; same-version portable canonical traces retain existing marker policy. | No migration/readers or historical rewrite. |
| Ephemeral diagnostics | Existing step records gain explicit dispatch source and eligibility reason; missing model frames become expected. | Atomic current-version Framework/Console/docs/test update, full canonical fidelity. Preserve journal-only existing redaction. |
| Internal implementation | Schema node/resolver/projection, bound capability and step engine evolve. | Update constructors/callers atomically, remove obsolete assumptions, no internal compatibility surface. |

- **Evidence of supported contracts:** AGENTS policy, `LoomspanPublicSurfaceArchitectureTest`, README, approved ticket R2/R4/R5 and research source inventory.
- **Intentional compatibility changes:** eliminated parent model events, request-specific hooks and physical usage; tests asserting redundant call counts must change. Internal signatures may change in place.
- **In-repository consumers:** Java source constructors/tests, fake response queues/count expectations, live projection tests, Console raw step/action detail views/tests, current fixture corpus and documentation.
- **Public-surface delta:** none; no new public API or Spring override contract. Run the architecture test after production changes.
- **Shim decision: No shim.** Explicit developer decision; one coherent development implementation. No overloads/aliases added to preserve changed internal constructors.
- **Java-to-Go boundary coordination: Required verification, no planned wire enum delta.** Use existing STEP_STARTED/STEP_ACTION records and metadata. Exact raw record transport already provides the fields; keep Go enums/DTOs unchanged unless executable verification exposes a concrete required consumer update. Add a current fixture case and verify Java/Go and browser handling together. Preserve exact release-string rejection and development validation policy; no compatibility-marker change is needed for existing record metadata.
- **Pipeline notes alignment: Aligned.** Parent model event/usage removal is specifically intended; developer additionally rejects shims.

## Implementation Approach

Reuse the existing projection and assigned execution boundary. Proof is immutable schema/binding analysis of the pinned BoundCapability, never source-value assembly or a name/model-specific heuristic. Real values are assembled only at ordinary invocation time. Model parsing/correction remains confined to model-authored actions. Keep the existing scheduler untouched.

## Phase 1: Conservative eligibility beside the contract authority

### Changes Required

1. Add internal proof-support provenance to `SkillInputSchemaNode` (a boolean or equivalent compact marker). Populate it in `SkillInputContractResolver` while raw keywords are still available; propagate it through projection. Update affected internal constructors atomically without overload shims.
2. Supported proof vocabulary is the resolver/manifest's established typed shape plus explicitly harmless annotations. Unknown/composition/reference/default/constant/conditional/property-count keywords, nullable/union types and malformed keyword shapes must mark proof unsupported instead of disappearing into an apparently eligible node. Scan recursively, including bound subtrees. Do not expand ordinary validation into a general JSON Schema interpreter in this ticket.
3. `ChildInputBindingProjection` exposes one immutable internal eligibility result, delegated by `BoundCapability`. Use bounded reasons `eligible`, `unbound_input_remains`, `open_or_unknown_contract`, `unsupported_or_ambiguous_shape`. Requiredness alone is never sufficient.
4. Prove the initial subset: explicit supported root object, closed additional properties, no root runtime-reference/attachment ambiguity, only single-token binding destinations, every declared root property wholly bound, no projected properties/requiredness/extension channel, and empty arguments accepted by the same projected validation semantics. A closed supported zero-property root with no bindings also qualifies. Whole bound object/array values are allowed; their contents come entirely from their source.
5. Every nested destination (multi-token pointer) conservatively yields unsupported/ambiguous. This deliberate narrow subset prevents absent/empty/null ancestor choices from being erased. It is within R1's permitted conservative fallback and must be documented/tested. No synthesized defaults/constants/nulls/empty ancestors are used to qualify a task.

### Automated Verification

- [x] Resolver/projection matrix covers explicit zero input versus generic, optional unbound versus wholly bound optional properties, typed/open extension channels, raw unsupported keywords and all nested destination fallback cases.
- [x] Existing model schema/prohibited-override and exact input validation tests still pass.

## Phase 2: Join direct actions to normal assigned execution

### Changes Required

1. In `StepLoopMissionExecutionEngine#executeOneStep`, find the exact accepted assignment's pinned visible BoundCapability and compute eligibility before emitting STEP_STARTED. Final synthesis is separate and always follows its current completion contract.
2. Record trusted `dispatchOrigin` (`framework` or `model`) and `dispatchReason` plus assigned task/capability in STEP_STARTED metadata. Final synthesis need not pretend to be an assignment. Do not infer origin downstream from absent model frames.
3. For eligible assignments create typed CALL_TOOL using trusted task ID, exact capability and `Map.of()` arguments. Validate with `StepActionValidator.validateAssigned`, record ordinary action validation and enter the same `executeToolAction`. Do not serialize a fake response, emit model-proposed/raw-response events, create a model frame or enter an action correction loop for a Framework action. A rejected constructed action fails normally with attributable records.
4. Share lifecycle/frame/failure handling and invocation with the model path. Before invoking either assigned action, enforce current owning lifecycle/new-work permission and interruption at the invocation boundary, preserving existing router/coordinator access/generation checks. Revalidate executable task identity with the assigned validator. No side-effect retry or cached source result is added.
5. Retain model parsing, correction and request hooks for noneligible assignments; retain child model reasoning, schema/evidence/linter checks and corrections. The eliminated provider attempt consumes no provider/model quota; task/tool/mission/depth limits remain independent.

### Automated Verification

- [x] Minimal red test demonstrates fewer calls; equivalent arguments/results and ordinary assigned validation prove reuse.
- [x] Missing/ambiguous/incompatible binding sources produce normal failure and zero child invocation or parent repair calls.
- [x] Latch-based cancellation/interruption/access tests after admission prevent side effects; handler/proxy approval denial stays effective. Use existing business authorization boundaries, not a newly invented approval feature.
- [x] Mixed grouped/sequential tests preserve joins, task-ordered outcomes, pinning, quotas and duplicate-execution behavior.

## Phase 3: Honest observations and current Console presentation

### Changes Required

1. Assert canonical STEP_STARTED origin/reason, normal validated/action/tool completion/failure, full effective input/provenance and retained actual child model usage. Existing live and journal projectors must retain coherent task/tool lifecycle; update their descriptions only if they assume a parent send. Keep derived journal redaction as-is.
2. Update `TraceRecords.tsx` step detail decoder/view to display exact trusted assigned identity and Framework/model dispatch reason from the existing raw record. Remove the unconditional no-selected-task statement. Action details must not call a Framework action model-proposed; failed direct dispatch must remain visible before a tool frame exists.
3. Extend current Java-written trace fixtures and Go/raw/browser tests to cover an assigned Framework-dispatched step without a model frame. Keep complete/raw content inert and explicit. No new canonical enum or broad trace redesign.

### Automated Verification

- [x] Trace/provider/observer tests prove no invented request/response/token/cost/correction facts and actual child usage remains.
- [x] Console corpus, Go tests and browser verification pass; changed step/action detail claims use emitted fields, not inference.

## Phase 4: Documentation and complete verification

Update the documents listed above with tested rule, narrow nested limitation, existing fully bound producer example, optional-input counterexample and model-child distinction. Explain hooks, provider accounting, unchanged step costs, task selection and business authorization. Keep historical captures untouched and avoid timing/dollar/reliability extrapolation.

### Automated Verification

- [x] Focused tests in the dedicated testing plan pass, then `./mvnw.cmd test` passes.
- [x] `./mvnw.cmd test "-Dtest=LoomspanPublicSurfaceArchitectureTest"` passes after production changes.
- [x] In `loomspan-console`, `go test ./...` and `go run ./internal/buildtool verify` pass.
- [x] Knowledge-base routing/coverage is updated and exact guidance cites executable evidence; the complete three-declaration authoring example still loads.
- [ ] Independent fresh Step 5 review verifies the entire change and required checks.

## Testing Strategy

See `2026-10-05-loomspan-pr-24-direct-dispatch-testing.md` for named cases and commands. Unit tests establish schema proof and no-model dispatch first; facade/local-provider integration covers Java/REST/model combinations, controls, usage and exact transfer; trace corpus/browser tests establish current consumer coherence.

## Performance Considerations

Proof is compiled once per bound capability from immutable contracts/bindings. No model contribution means one removed parent interaction per eligible accepted task; child requests and final completion remain governed by existing behavior. Tests assert exact calls, not speed/cost/reliability improvements.

## Migration Notes

No migration or compatibility shims. Update in-repository construction sites, current fixtures and call-count assumptions atomically. Preserve unrelated work, ticket and captured evidence.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-24-direct-dispatch-for-fully-bound-tasks.md`
- Research: `ai/thoughts/research/2026-10-05-loomspan-pr-24-direct-dispatch.md`
- Policy: `ai/thoughts/framework-feature-design-lens.md`, `ai/commands/shared/loomspan-docs-protocol.md`, root and Console AGENTS guidance.

## Implementation verification receipt

Step 4 implementation and verification completed on 2026-10-05. See [implementation receipt](2026-10-05-loomspan-pr-24-direct-dispatch-implementation.md) for exact acceptance mapping, commands/results, resolved test failures and bounded implementation decisions. Full Maven: 1476 tests, 0 failures/errors, 2 existing skips. Console standard verification: 525 browser tests plus build/type checks and Go packages. Independent Step 5 review remains pending.
