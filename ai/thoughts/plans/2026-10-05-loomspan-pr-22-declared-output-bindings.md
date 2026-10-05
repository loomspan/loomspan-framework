# PR22 Declared Output Bindings Implementation Plan

## Overview

Implement top-level destination-keyed `output_bindings` on model-backed YAML skills. Framework supplies exact values from the owning invocation's validated input and accepted direct-child results; the model supplies only unbound output. Entirely bound output completes without a final model request. This is Steps 2–3 of the approved Full 5-Step Pipeline.

Developer authorization: **development; destructive changes welcome; no compatibility shims**. Apply internal changes atomically. Preserve the ticket's explicit `output_from`, ordinary execution, authorization, limits, isolation and diagnostic fidelity requirements. No paid model runs, sidecar-suite changes, commits, pushes or release work are authorized.

## Current State Analysis

PR21 already provides descriptor parsing, object JSON pointers, model-owned input projection, accepted-result decoding, detached insertion, immutable input capture and provenance. Output contracts have native nullable semantics and case-insensitive property validation absent from input schemas. Ordinary schema correction lives in an advisor, while explicit planning final correction lives in the step engine. Forwarding already demonstrates exact task selection, full-work completion and N-task admission without a synthesis slot.

### Key discoveries

- `src/main/java/ai/loomspan/internal/skill/YamlSkillCatalog.java:574`: raw descriptor checks; strict YAML codec rejects duplicate keys.
- `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingProjection.java:14`: removal of owned subtrees and optional-ancestor/required-sibling rules.
- `src/main/java/ai/loomspan/internal/runtime/input/ChildInputBindingAssembler.java:16`: detached values, null versus absence and provenance.
- `src/main/java/ai/loomspan/internal/core/MissionContext.java:69`: immutable captured input; task-keyed invocation-local results at `:129`.
- `src/main/java/ai/loomspan/internal/skill/SkillGenerationManager.java:294`: complete-generation checks; effective producer output metadata resolves after this point.
- `src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java:154`: model interaction is created inside the owning mission binding after access checking.
- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:487`: synthesis begins after accepted work joins; forwarding verifies complete work at `:490`.
- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaCallAdvisor.java:86`: ordinary initial/corrective schema ownership.
- `src/main/java/ai/loomspan/internal/outputschema/OutputSchemaValidator.java:37`: exact native output validation; nullable and case-insensitive property rules.
- `src/main/java/ai/loomspan/internal/core/DefaultExecutionTraceRecorder.java:73`: owner-frame authoritative completion event pattern.

## Desired End State

Authors choose ordinary synthesis, whole forwarding, fully bound assembly or mixed assembly. Every assembly resolves every declared binding, prohibits model overrides, validates the complete original object contract and publishes only after all accepted work succeeds. The model sees projected constraints without losing input or completed-result evidence needed for reasoning. Sources and generation remain stable across correction, nested execution, parallel execution and reload.

## What We're NOT Doing

- No new Java application API, supported SPI, bean override contract or inferred Java/REST producer schema.
- No destination-list syntax, `ref://`, runtime model-authored bindings, transforms, array-element mapping, aggregation, cross-parent or grandchild selection.
- No provider-native schema feature, stronger format validation, historical trace readers or compatibility shims.
- No comparison-suite tuning, live paid reliability claims or unrelated cleanup.

## Skill-Authoring Documentation Impact

**Impact: Affected.** This adds manifest syntax and changes final-output ownership, planning requirements, correction, step costs and diagnostic interpretation.

- Update `agent-skills/loomspan-docs/references/skill-authoring/output-contracts.md` with a four-mode decision table and complete full/mixed schema examples; explain mutual exclusion, exact sources, required object schema, projection, failure/correction and no-synthesis cases.
- Update `input-bindings.md` with the destination distinction and link to output assembly without duplicating the grammar.
- Update `planning-task-constraints.md` and `planning-concurrency.md` for unconditional output producer counts, no artificial dependencies and full joins; state N versus N+1 step cost.
- Update `traces-and-debugging.md` for authoritative assembly/provenance and raw-model versus assembled output.
- Update the knowledge-base `README.md` routing and coverage rows, and repository `README.md` feature summary.
- Supporting evidence: new focused declaration/projection/assembly tests; ordinary and planning `SkillTemplate` integrations; isolation/reload tests; Java/Console canonical fixture tests.
- **Coverage table update: Required.** Output modes and planning/diagnostic coverage expand.
- **LLM-first usability:** keep output guidance self-contained, distinguish enforced behavior from recommendation, use the PR21 vocabulary, link adjacent topics, and cite stable implemented classes/test names.
- **Drift classification: aligned** for investigated current input/forwarding/output behavior. Assembly is new coverage, not correction of an existing documented runtime promise.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | `SkillTemplate` invocation and existing schema metadata expose assembled results; closed allowlist remains authoritative | No supported type/signature additions; existing unbound/forwarding behavior retained |
| Supported SPI | REST child text remains a source through existing `RestSkillHandler` | Sole SPI unchanged; no inferred producer schema |
| Configuration and manifest | New `output_bindings`; projected contribution schema; unconditional producer plan count; explicit `output_from` conflict | Additive syntax; intentional internal updates shipped atomically; explicit null conflicts rejected |
| Persisted or serialized | Current canonical portable diagnostic vocabulary changes | Same-version/current-development coherence; no historical readers or alternate encodings |
| Ephemeral diagnostics | Authoritative assembly outcome and exact provenance; Console enum/DTO/MCP/live consumers | Atomic writer/reader/fixture changes; preserve content and existing size controls |
| Internal or accidental exposure | Manifest descriptors, reusable binding helpers, advisors, engines and wiring | Rename/remove/update callers atomically; public modifier is not supported API |

- **Evidence of supported contracts:** ticket requirements, authoring docs, `LoomspanPublicSurfaceArchitectureTest` closed application allowlist and `RestSkillHandler` classification.
- **Intentional compatibility changes:** current internal decomposition and current diagnostic vocabulary may change. No old constructor, type alias, legacy trace reader or duplicate behavior will be retained. The developer explicitly authorizes destructive changes and forbids shims.
- **In-repository consumers:** Java callers/tests/architecture inventory; YAML fixtures; both engines and model advisor assembly; trace journal, current Java-to-Go NDJSON, Console live/browser/MCP schemas/fixtures and documentation.
- **Public-surface delta:** zero supported API/SPI additions. Add necessary internal types to the architecture test's implementation inventory, without expanding the application allowlist or leaking internals through public signatures.
- **Shim decision: No shim.** Explicit developer decision; coherent atomic implementation is required.
- **Java-to-Go boundary coordination: Required.** Add `RESULT_ASSEMBLED` to canonical Java and Go record vocabularies, live activity types, analysis projection, browser/MCP record DTOs/schemas, generated contract fixtures, and focused Java/Go tests in the same change. Existing exact resolved release-string rejection and development validation behavior remain unchanged; no compatibility marker/version bump is part of this development feature. No REST/SSE route or acquisition protocol is added.
- **Pipeline notes alignment: Aligned with developer authorization.** Ticket has no `Pipeline notes`; the subsequent explicit no-shim/development instruction is persisted here and in research.

## Implementation Approach

Keep one descriptor vocabulary and one low-level source-selection/copy/insertion authority. Reuse or atomically rename the existing PR21 internal binding descriptor to a destination-neutral internal name; reuse object pointers, `DeepInputValues`, `AcceptedResultDecoder` and insertion/provenance logic. Avoid duplicating descriptor checks for inputs and outputs. An output-native projection/validation adapter is necessary because input schemas cannot represent output nullability or output property matching rules; do not convert output schemas into input contracts and lose semantics.

Compile immutable output declaration/projection information from the captured complete generation. At invocation runtime resolve immutable authoritative sources once, before any final model attempt. A shared internal output composition helper accepts model contribution, detects conflicts, inserts detached bound values and validates complete output. Both ordinary advisor correction and planning final-response correction call this authority. Failure categories separate declaration, producer plan/selection, model override and complete output contract issues. Source and bound-only contract errors terminate without model fallback; only model-owned defects consume the existing correction budget.

No-framework-change and prompt-only alternatives leave the model responsible for copying exact framework-held evidence. Whole forwarding remains useful but cannot express several producers. A separate destination dialect or new general dataflow engine adds concepts without improving this ticket's outcome. Reuse PR21 mechanics and native output schema support instead. No out-of-scope dead code has been established; remove only obsolete internals made redundant by this integration.

## Phase 1: Declaration and complete-generation validation

### Changes required

1. Extend `YamlSkillManifest`, declaration presence tracking and defensive copying in `YamlSkillDefinition` for top-level `output_bindings`. Share the PR21 descriptor and raw validation. Require a nonempty destination map on a model-backed skill with declared object `output_schema`; reject REST use, null/malformed/unknown descriptor fields and simultaneous `output_from`/`output_bindings`, naming both fields. Input-only declarations permit nonplanning model skills; any child source requires explicit planning and an allowed direct producer.
2. Keep pointer semantics exact: nonempty destination, empty source root, escaped object keys, literal numeric-looking object keys, no array traversal. Reject destination overlap; also reject case aliases that collide under the existing output validator's property matching. Known schema destination names should resolve to the schema's canonical declared spelling; source lookup retains exact PR21 object-key semantics.
3. Extend `SkillGenerationManager` after effective output metadata resolution to validate destination paths, current-input source paths and actual declared producer shapes. Unknown Java/REST/model shapes and open branches defer to runtime. Reject statically provable incompatible types/array items/object boundaries; permit integer-to-number, reject known incompatible enum/nullability combinations. Optional source presence remains a runtime obligation rather than a fabricated guarantee. Never infer schemas from Java signatures or REST text.
4. Reject unknown/disallowed/self producers and impossible counts (`max_tasks: 0`, effective minimum >1). A producer need not declare `required: true,max_tasks: 1`; the output binding itself makes exactly one task unconditional at accepted-plan validation. Multiple bindings to one producer use the same unique task.

### Automated success criteria

- [x] Catalog/generation tests accept input-only and direct-child maps, preserve explicit declaration presence and reject invalid/conflicting declarations.
- [x] Static closed-source/destination/type tests and unknown/open runtime-deferral tests pass.
- [x] Existing PR21 and forwarding configuration tests pass after atomic internal changes.

## Phase 2: Ownership projection and exact assembly

### Changes required

1. Add an output-native immutable projection under `internal/outputschema` (or a narrow internal binding package). Retain nullable, openness, enum/items/description/format/evidence metadata as appropriate. Remove bound subtrees from model-required properties, forbid reserved fields in guidance and runtime checks, preserve every unbound sibling constraint, and require originally optional ancestors when binding insertion makes required unbound siblings mandatory. A bound ancestor owns its entire subtree.
2. **Full-bound decision:** skip a final model request only when no model-owned declared optional or required field and no open/additional field space remains. Open root or any reachable unbound open object means model contribution is possible. A wholly bound subtree's internal openness does not require a model. Closed empty residual object means assemble from `{}`. An empty binding declaration is invalid, rather than a second ordinary-output dialect.
3. Capture resolved source selections using the mission input and exact accepted completed task identities. Check accepted plan identity, producer skill/task count/status, retained result identity and owning mission. Resolve every binding, including optional destinations. Decode only through the existing once-decoded result; retain exact scalars/large integers/decimals, strings, null and ordered arrays. Copy containers for each output.
4. Reject model presence at reserved paths even for equal/null values; reject blocking scalar/array/null ancestors and case aliases that would collide with owned output fields. Permit ancestor objects containing valid unbound siblings. Never silently discard a model field or overwrite a conflict.
5. Validate bound-only values/source paths before final model correction where possible. Validate projected contribution and complete assembly against the original output schema; retain exact output semantics with no scalar/date normalization. Full validation issues caused by bound values are terminal; model-owned issues remain correctable. Complete evidence/linter policies continue to apply to complete assembled output; immutable bound-policy failures cannot be repaired by requesting copied values.

### Automated success criteria

- [x] Projection tests cover optional ancestors, required siblings, open objects, nullable/blocking ancestors and full-bound classification.
- [x] Assembly tests prove exact values, null/absence, override detection, detached containers and actionable source/output failure categories.
- [x] No new supported surface or internal-type leakage; architecture inventory remains coherent.

## Phase 3: Ordinary and planning completion/correction

### Changes required

1. `DefaultMissionExecutionEngine`: for fully bound input-only output, assemble and validate inside the mission lifecycle without opening a model frame or making a provider request. Keep access and invocation admission intact. Mixed ordinary calls retain complete input evidence.
2. `DefaultSkillAdvisorResolver`/`OutputSchemaCallAdvisor`: use the projected contract in all initial/retry instructions and examples; share output composition checking within the existing schema retry loop. Capture the invocation's explicit existing `ExecutionBinding`/mission rather than global result-name state. Preserve original provider candidates; return/publish assembled content only after complete validation. Ensure downstream evidence/linter checks inspect complete output and their retries cannot expose complete bound fields as model-owned. An invocation-local helper can be passed through existing internal model construction; do not add a new supported SPI or parallel model execution path.
3. `DefaultPlanningService`: make output producers visible/count-exact in initial guidance, combined plan correction and acceptance checks. Introduce a final-output producer count validator alongside input dependency checks; output requirements add no `dependsOn` edge or earlier-unit constraint. Keep existing input-binding dependencies when a child really consumes another child's result.
4. `StepLoopMissionExecutionEngine`: retain accepted producer task IDs, execute/join every accepted task, and verify all task success before resolving outputs. Share forwarding's full-work identity/status checks rather than maintaining contradictory completion authorities. Fully bound planning output uses N task slots and no synthetic final step. Mixed output retains N+1 admission and existing correction limits; final prompt/action validation uses only projected contribution, keeps full accepted result evidence and never reruns children.
5. Preserve captured generation and selected source snapshots through correction/reload. Failed/aborted unrelated accepted work, stale plan/task/result or lifecycle cutoff prevents assembly success and late event writes.

### Automated success criteria

- [x] Ordinary full/mixed `SkillTemplate` tests assert provider call counts and projected initial/corrective prompts.
- [x] Planning tests assert exactly-one producer correction, parallel joins, N-slot fast path, unrelated-task completion/failure and no child replay during output correction.
- [x] Existing unbound synthesis, forwarding, RBAC and execution-limit suites pass.

## Phase 4: Diagnostics, Console and authoring guidance

### Changes required

1. Add owner-frame `RESULT_ASSEMBLED` only after complete validation succeeds. Metadata identifies skill, owning mission, optional accepted plan ID, `modelContributionRequired`, and `outputBindings` provenance entries (`destination`, `sourceKind`, `sourcePath`, owning mission frame, exact source task/skill when applicable). Store assembled JSON once in the normal payload channel, enabling existing descriptor/byte-range access. Provenance never duplicates selected payloads. Raw model records and retained child records remain unchanged.
2. Extend execution state/trace recorder and derived journal coherently, preserving the existing journal-only field-name redaction exception. Distinguish failure codes/stages without changing original diagnostic content. Absence of a final model call is observable from the emitted authoritative `modelContributionRequired: false`, not inferred by Console from timing or adjacency.
3. Atomically update Go closed enums, live DTO/title mapping, strict assembly metadata decoder, record facts/index projections, browser record facts, MCP query filters/output schemas, generated discovery fixtures and focused current-run tests. Treat `resultAssembly` as an authoritative fact with provenance and ordinary assembled payload descriptors; verify referenced source tasks belong to the recorded owner where canonical evidence permits. Use existing byte/lifetime/access boundaries.
4. Update all documents listed in the documentation impact section using the implemented test fixtures as complete full/mixed examples. Preserve the existing forwarding examples and ordinary output guarantees. Do not promise format enforcement, producer schema inference, improved business reasoning or historical trace interchange.

### Automated success criteria

- [x] Canonical Java fixture exports and Console strict readers/projections accept assembly and preserve raw/source/assembled separation.
- [x] Go/MCP/live/browser tests cover vocabulary, provenance, payload access, byte limits and existing exact release matching.
- [x] Routed authoring docs and README coverage table accurately describe executable behavior.

## Testing Strategy

Use deterministic unit tests for declaration/projection/source/assembly mechanics and MockWebServer-backed `SkillTemplate` integrations for both runtime paths, direct model/Java/REST results, correction, full-work completion, authorization, nested/concurrent isolation and reload. Add current Java/Go diagnostic fixtures with exact assertions; no paid evaluations. Detailed fixtures, red tests, commands and gates are in `2026-10-05-loomspan-pr-22-declared-output-bindings-testing.md`.

## Performance Considerations

Decode accepted results once, resolve bound sources once per invocation, and copy only output containers at assembly. Fully bound output saves the final provider call; mixed output still presents complete reasoning evidence and carries its context cost. Provenance contains identifiers and paths, not duplicated payloads. Do not serialize source snapshots repeatedly inside correction.

## Migration Notes

No compatibility shim or legacy behavior. Internal renames and current trace vocabulary changes update all repository consumers atomically. Existing manifests without output bindings and existing `output_from` remain executable. No durable application data migration or compatibility marker change is needed.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-22-declared-output-bindings.md`.
- Research: `ai/thoughts/research/2026-10-05-loomspan-pr-22-declared-output-bindings.md`.
- Design lens: `ai/thoughts/framework-feature-design-lens.md`.
- Documentation protocol: `ai/commands/shared/loomspan-docs-protocol.md`.
- Source/test anchors above and companion testing plan.

## Implementation execution notes (2026-10-05)

- Implemented the existing `ChildInputBinding` descriptor vocabulary for final output declarations without renaming it or introducing a second descriptor dialect. Raw manifest descriptor validation is shared with child input bindings; output-native projection retains native nullable/property semantics.
- `OutputBindingComposition` is the shared composition authority. `MissionContext` memoizes exactly one invocation-local immutable source snapshot, reused by the ordinary engine/advisor and planning engine through every correction. No generic session data bag or supported extension point was added.
- Final output producers are unconditional exactly-one direct tasks, including optional allowed entries. `PlanOutputBindingProducerValidator` adds no dependency edges; the step loop shares complete accepted-work identity/result checks with forwarding.
- Fully bound ordinary output validates schema/evidence/regex policies without a model frame. Mixed ordinary assembly runs inside schema correction so outer evidence/linter advisors see complete output; terminal regex exhaustion prevents binding success publication. Immutable bound evidence is checked before model correction. All successful assembly records are emitted once from the owning engine after complete validation.
- `RESULT_ASSEMBLED` uses the ordinary payload capture channel. Metadata includes `skillName`, `owningMissionFrameId`, optional `planId`, `modelContributionRequired`, and `outputBindings`; each source entry retains PR21's `parentMissionFrameId`, with exact child task/skill where applicable. Original provider/child records remain intact and provenance contains no selected payload values.
- Final test names are `OutputBindingCompositionTest` (projection plus exact assembly), `OutputBindingIsolationTest`, `DeclaredOutputBindingsOrdinaryIntegrationTest`, `DeclaredOutputBindingsPlanningIntegrationTest`, and the existing expanded planning/step/live/canonical fixture suites. These replace the testing plan's provisional separate projection/assembler/integration/trace class names while preserving their behavioral coverage.
- The minimal declaration red test was authored before production declaration support, but concurrent incomplete compilation/shared Maven target contention prevented a meaningful pre-fix assertion result. Compilation failures are not behavioral red evidence. The orchestrator directed proportionate final behavior verification rather than reconstructing a baseline checkout solely for red evidence; the limitation remains explicit. The same declaration test passes post-fix.
- Early verification suffered overlapping child Maven writes to `target`; implementation centralized all later Maven commands and used a clean build. Subsequent fixture-only compile/encoding mistakes and test fixture declaration mistakes were corrected before final checks. No such intermediate failure is claimed as passing evidence.
- Preserve unrelated untracked `ai/thoughts/tickets/loomspan-pr-23-shared-output-validation-policy.md`. No paid provider evaluation, sidecar change, commit, push, release, API/SPI expansion, compatibility shim, or compatibility marker change is part of this implementation.
## Acceptance and final verification evidence

Documentation comparison: **aligned**. The authoring guides describe the implemented runtime and cite the executable classes below. Supported API/SPI delta remains zero; the architecture allowlist adds only internal collaboration types.

| Ticket acceptance | Implementation and executable evidence |
| --- | --- |
| Shared destination/from/skill/path grammar; whole/subtree current-input and model/Java/REST sources | Shared raw binding checks in `YamlSkillCatalog`, captured generation checks in `SkillGenerationManager`, catalog/generation tests, exact composition tests, six public-facade planning integration variants |
| Two direct children plus input identifier, no final synthesis, all accepted work completes | `DeclaredOutputBindingsPlanningIntegrationTest` full modes and `StepLoopMissionExecutionEngineTest#outputAssemblyJoinsWholeProducerUnitAndUnrelatedWork` with both producer outputs and a latch-controlled sibling; N task slots and exact arrays/large integers |
| Mixed input evidence/reasoning, projected initial/corrective contracts, nested required siblings | Thirteen `DeclaredOutputBindingsOrdinaryIntegrationTest` cases and `OutputBindingCompositionTest`; optional/open residual space retains a provider call and complete input evidence remains in requests |
| Mutual exclusion and protected existing modes | Catalog declaration conflicts; full regression includes `DesignatedChildResultIntegrationTest`, existing ordinary synthesis, PR21 input bindings and forwarding tests |
| Invalid declaration/static types and dynamic source failures cannot publish/fallback | Catalog/generation/producer tests; composition missing/ambiguous/null/type tests; `OutputBindingIsolationTest`; step failure/stale retained result/invisible producer tests; bound type/evidence integration failures use zero model fallback |
| Equal/null/alias/blocking overrides, bounded correction, stable sources and no child replay | Composition override cases; ordinary alias and ancestor correction; mixed planning summary correction with child counters; shared memoized composition and complete original schema validation |
| Parallel/nested/concurrent/reload ownership and authorization | Two-producer step unit joins with latches under concurrency enabled/disabled; nested identical producer/task-ID isolation; concurrent ordinary roots and held correction reload tests; invisible producer gate; full existing RBAC/invocation-limit/lifecycle regression |
| Exact provenance, original model/child evidence, skipped synthesis facts | Single owner-frame `RESULT_ASSEMBLED`; full-input and mixed-child Java→Go corpora, raw contribution/source/assembled payload separation, strict exact-source ownership rejection, live/browser/MCP facts and frontend assembly details |
| Complete authoring modes/examples and deterministic verification | Updated output/input/planning/concurrency/trace references, routing/coverage README and repository README; all new providers are local deterministic fixtures |

Final required checks:

- PASS — `.\mvnw.cmd -q clean '-Dloomspan.console.fixtures.regenerate=true' test`: 181 suites, 1,433 reported cases, 1,431 executed; zero failures/errors. The two skipped cases are existing opt-in `Pr18RecordedHandoffDiagnosticTest` tests requiring `pr18.diagnostic`, unrelated to this ticket. Log: ignored `target-pr22-full.log`.
- PASS — `.\mvnw.cmd -q '-Dtest=StepLoopMissionExecutionEngineTest,OutputBindingIsolationTest,LoomspanPublicSurfaceArchitectureTest' test`: 95 cases, zero failures/errors/skips, after the last test-only parallel-producer refinement. Architecture independently visible: 8 tests. Log: ignored `target-pr22-final-focused.log`.
- PASS — `go run ./internal/buildtool verify` from `loomspan-console/`: final regenerated corpus, declared toolchain/locked frontend validation, TypeScript typecheck, 524 frontend tests, production assets and all Go packages.
- PASS — `go test ./internal/traceanalysis ./internal/live ./internal/browserapi ./internal/mcpadapter` from `loomspan-console/`: all four current packages pass; log `target/pr22-console-focused.log`.
- PASS — `git diff --check`: no whitespace errors; Git reports existing CRLF normalization notices.
- Historical limitation — meaningful pre-fix behavioral red evidence was not established (see execution notes). This is not reported as PASS; post-fix declaration behavior is covered by catalog and complete regression.
- No optional developer observations are required. Independent Step 5 review remains the next pipeline stage; this implementation-stage receipt is not final review approval.