# PR 21 Declared Child Input Bindings Implementation Plan

## Overview

Implement opt-in author-declared input bindings for planning parents. Framework
copies validated parent input and accepted direct-child results into receiving
inputs; the model generates only unbound arguments. Receiving schemas, access
checks, invocation limits, captured generations and joined execution units remain
authoritative.

Execution profile: `full`, confirmed by the developer. The developer additionally
directed: **destructive changes welcome and no compatibility shims**. Update
internal records, constructors and callers atomically; do not add legacy overloads,
aliases, adapters or dual execution paths. This direction does not expand this
ticket's API, result lifetime or authorization scope. The initial checkout had
only the untracked ticket; research and these plans are pipeline-owned work.

## Current State Analysis

- `YamlSkillManifest.java:174` and `AllowedSkillConstraint.java:8` carry child
  cardinality only. `YamlSkillCatalog.java:493` rejects binding entry fields.
- `SkillGenerationManager.java:282` resolves the complete child set and input
  contracts; its output-forwarding graph is distinct from input dependencies.
- `DefaultPlanningService.java:721`, `StepPromptBuilder.java:232`, and
  `StepActionValidator.java:167` currently publish/use complete receiving schemas.
- `CapabilityExecutionRouter.java:49` validates complete receiving input after
  access checks. `SkillInputValidator.java:196` coerces numeric strings and its
  date validator normalizes strings. Generic/open copying is not always recursive.
- `ExecutionCoordinator.java:110` owns a mission-local context before binding
  tools at line 170. It already receives the validated invocation input.
- `MissionContext.java:112` retains complete successful direct results by task
  ID. `StepLoopMissionExecutionEngine.java:407` supplies pre-unit snapshots and
  line 730 folds accepted results only after join. Rejected actions are not results.
- `DefaultCapabilityInvoker.java:85` opens a tool frame with actual arguments;
  raw model actions already exist in canonical model/step evidence.

## Desired End State

An author can bind a child field from a differently named parent field or from
one accepted direct producer. A large nested payload reaches model, Java and REST
children exactly when their action arguments are `{}` or contain only unbound
reasoning. Invalid declarations, overrides, unavailable/ambiguous sources, and
receiving validation failures are attributable and occur before child dispatch.
Independent producer groups retain existing concurrency and join semantics.

### Key Discoveries

Existing mission results, immutable generation metadata, shared contract nodes,
planning correction lifecycle and canonical tool frames provide the necessary
authorities. New state is limited to compiled declaration/projection information
and invocation-local source snapshots; no global reference store is needed.

## What We're NOT Doing

No public API or SPI additions, planner-authored bindings, cross-parent/session
selection, transformations, array-element traversal/destinations, wildcards,
multi-result aggregation, inference by field name, business reasoning fixes,
paid calls, replay refresh, publishing, release, commit or push. Do not change the
sibling suite's frozen artifacts or uncommitted work.

## Settled Design Decisions

### Declaration and paths

Use `input_bindings` under a structured `allowed_skills` entry, applicable only
to explicit `planning_mode: true`. Preserve `from`, `path` and `skill` spellings.
Use object-only JSON Pointer syntax for destination keys and source paths:

```yaml
allowed_skills:
  - name: assess
    required: true
    max_tasks: 1
    input_bindings:
      /caseId: {from: input, path: /requestId}
  - name: compare
    required: true
    max_tasks: 1
    input_bindings:
      /caseId: {from: input, path: /requestId}
      /context/sourceRecords: {from: input, path: /records}
      /context/assessment: {from: child_result, skill: assess, path: /data}
output_from: {skill: compare}
```

Destination pointers must be nonempty and select named object fields. Source
`path: ""` selects the entire source value. `~0` and `~1` encode literal tilde
and slash; dots have no special meaning. Reject invalid escapes and non-pointer
strings. Tokens address exact object keys, including numeric-looking keys; never
interpret a token as an array index. Selected values may be arrays. Missing keys
are distinct from null. Reject duplicate raw YAML keys before tree/map collapse,
duplicate normalized destinations, and ancestor/descendant destination overlap.
Source records require exact keys: `input` permits `from,path`; `child_result`
permits/requires `from,path,skill`. Reject null/unknown fields and malformed kinds.

Compile normalized immutable bindings into existing child declarations. During
complete-generation validation, resolve the receiving contract, validate known
destination ancestors as objects, honor closed and typed-additional boundaries,
and validate known parent-input source paths. Generic/open unknown branches defer
value checks to runtime. Check declared producer names, self references, cycles
and manifest-impossible producer cardinalities (`max_tasks: 0` or minimum > 1
when that producer is required by a potentially planned consumer). Do not infer
producer output schema from its Java return type or REST text; undeclared output
schema remains unknown. Compatible shape is ultimately proved at runtime.

### Two contracts and forbidden overrides

Preserve original `CapabilityMetadata` identity and full input contract. Add a
separate internal model argument contract/projection to `BoundCapability`; use it
for `inputSchema()`, planning descriptions, assigned compact/verbose examples,
ordinary action validation and corrective requests. Do not clone generation
metadata into a capability that fails generation ownership checks.

Project recursively: remove bound properties from model-authored requirements
and examples while retaining all unbound types, enums, formats, descriptions,
additional-property constraints and required siblings. Framework-created ancestor
containers may be omitted when their projected required descendants are empty.
If a bound descendant forces an originally optional ancestor into existence,
its unbound required siblings become required model contributions. Compute this
bottom-up; checking only original top-level requiredness is insufficient.

Open/generic objects must still structurally forbid bound property names (for
example JSON Schema false property schemas in the serialized model contract),
and the internal action guard checks bound paths independently of generic-schema
shortcuts. A model may supply an ancestor object with unbound siblings. It may
not supply the bound destination, any value underneath it, or a scalar/null/array
ancestor preventing assembly. Equal-value copies are overrides and are rejected.
No conflict is silently discarded. Keep reserved-path knowledge in one compiled
projection so rendering, action checks and assembly agree.

### Source ownership, decoding and assembly

Capture a recursively immutable snapshot of the validated input in its owning
mission before child binding, retaining actual input rather than materialized
prompt/attachment descriptions. Tools resolve parent input from that exact
mission. Result sources come only from the worker's immutable pre-unit accepted
direct-result snapshot and exact accepted plan task ID. Never query session-global
skill names or mutable sibling state. Corrections retain the same assignment and
snapshot. Nested missions own their own source input and results.

At accepted result retention, decode a serialized result once using a dedicated
lossless JSON reader (BigInteger/BigDecimal as needed, complete-document parsing).
Valid JSON objects/arrays/scalars/null decode directly. JSON-quoted Java strings
decode once to String. Non-JSON plain text, including malformed JSON-looking text,
remains the unchanged text value; a nested object-field selection then fails
because it is not an object. Do not add braces or guess transport envelopes.
Never recursively decode strings inside the decoded value. Keep the original
String for evidence and `output_from`; extend the existing retained-result entry
with its immutable decoded representation rather than creating a second result
store. Decode only accepted successful outcomes, never rejected candidates.

Assembly deep-copies permitted model arguments and each selected source value,
creates only destination ancestor object containers and rejects any conflict.
Resolve every binding even when its receiving property is optional. Validate the
assembled complete input before dispatch through the common router.

Extend internal validation with provenance-aware exact subtrees: below each bound
destination perform unchanged type/required/closed/enum/format checks without
numeric/Boolean string coercion or date rewriting. Valid date spellings retain
their exact String; invalid dates still fail. Preserve Number values losslessly;
reject nonfinite values. Unbound fields retain current coercion/normalization.
Carry exact-path policy to receiving validation so a second normalization cannot
undo fidelity. Do not weaken or replace the full receiving schema. JSON/open/
generic and arrays-without-items branches need recursively detached containers.
Existing attachment/reference resolution remains its normal owning boundary.

### Dependencies and failures

Derive direct consumer-to-producer dependencies from declarations. Whenever an
accepted plan includes a bound consumer, it must contain exactly one task for
each named producer, and that producer must occupy an earlier execution unit.
The consumer's `dependsOn` must explicitly include that producer task ID. Do not
inject or repair model plan edges. Render these constraints in planning guidance
and feed failures into the existing one-total-correction validation, composing
with structure, count and evidence issues before any plan is stored.

No additional explicit `max_tasks: 1` syntax is required: plan validation provides
conditional uniqueness. Authors should use required/max constraints when every
run needs the producer; counts remain their own unconditional contract. At runtime
preflight rechecks unique accepted producer selection, earlier successful joined
completion and result availability. Reject same-unit dependencies with either
concurrency setting; unrelated producers remain eligible to share a valid group.

Use distinct binding issue categories/messages for declaration, plan dependency,
missing/ambiguous source, prohibited override, and assembled-input validation.
Model argument overrides use existing action correction allowance and projected
guidance. Source/receiving failures use existing tool/runtime failure lifecycle;
feedback may describe the failed source/path/contract but must never ask the model
to recreate bound evidence or silently fall back to model data.

### Evidence

Use existing `TOOL_INVOCATION` frame payload parameters. Keep `arguments` meaning
actual assembled delivered input. Raw model action evidence remains the model
argument authority. Add an `inputBindings` provenance list only for bound calls:
destination, source kind, source path, owning parent mission frame ID and, for
results, exact source task ID and skill name. Include the consuming linked task
ID through existing fields. Do not duplicate selected payloads or original
results inside provenance. Failed assembly evidence must identify the binding
and retain the raw model argument evidence even when no child dispatch occurs.

`DefaultExecutionTraceRecorder.java:29` passes arbitrary frame parameters as
payload; `NdjsonExecutionTraceReader.java:101` reads existing `TraceRecord` maps.
No new record type, top-level NDJSON field, REST/SSE/acquisition/problem shape or
semantic classifier is introduced. This extensible payload addition therefore
does not require a Console compatibility-marker change or Go implementation
change. Verify with current writer/reader and corpus evidence; update Java
fixture generation/current corpus if adding a representative binding fixture.
If implementation instead changes a closed cross-component envelope, reassess
and coordinate it rather than silently treating it as an arbitrary payload.
Preserve canonical fidelity and existing derived-journal redaction exception.

## Skill-Authoring Documentation Impact

**Impact: Affected.** New manifest syntax, separate model contracts, mandatory
declaration dependencies, exact values and failure semantics change authoring.
Baseline drift classification is **aligned**: current guides correctly describe
no binding capability. Replace those claims for opted-in declarations.

- Add routed `agent-skills/loomspan-docs/references/skill-authoring/input-bindings.md`
  with applicability, exact grammar, source root rules, two contracts, a complete
  parent/producer/consumer example, generated `{}`/reasoning-only arguments,
  dependencies, exact-value/null/missing rules, failures and `output_from` use.
- Update the same directory's `README.md` routing and coverage tables,
  `planning-task-constraints.md`, `planning-concurrency.md`, `input-contracts.md`,
  `input-contract-design.md`, `output-contracts.md` and `traces-and-debugging.md`
  where existing broad statements need qualification or cross-links.
- Update repository `README.md` with the opt-in capability and a routed link.
- Evidence: catalog/generation tests, projection/action/prompt tests, dependency
  validation tests, exact assembly/validation tests and public offline integration
  described in the dedicated testing plan.
- Coverage table update: required for the new topic and planning/input/evidence
  changes. Use named source/test anchors, no volatile lines in distributed docs.
- LLM-first usability: focused topic plus short cross-links, enforced versus
  recommended semantics, self-contained complete example, and explicit limits.
  Distinguish bindings from `ref://` attachment handling and JSON Schema `$ref`.

## Contract and Compatibility Impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | Existing closed `ai.loomspan.api` allowlist and `SkillTemplate` invocation remain; descriptors retain complete receiving contracts | No additions or changed public signatures; architecture test mandatory |
| Supported SPI | Existing `RestSkillHandler` receives effective inputs through common dispatch | Preserve sole supported SPI, no bean replacement contract |
| Configuration and manifest | Opt-in `allowed_skills.input_bindings`; existing unbound/count/output/reference semantics protected by ticket | New coherent syntax, atomic fixtures/docs; no aliases |
| Persisted or serialized | No durable binding store; raw accepted results remain unchanged; current trace maps remain extensible | No legacy readers or cross-version migration |
| Ephemeral diagnostics | Raw model action versus effective tool arguments, attributable source provenance | Current writer/reader/projector coherence and content fidelity |
| Internal implementation | Child constraints, bound tools, validation policy, retained entries, planner validation and wiring | Atomic change/removal and all callers/tests updated |

- Supported-contract evidence: repository AGENTS policy, architecture allowlist,
  README, ticket required behavior and matching source/tests cited in research.
- Intentional compatibility changes: internal signatures/records may be removed
  or changed under developer direction. No unbound manifest or supported API
  behavior break is needed to achieve the feature.
- In-repository consumers: all constructors/bind calls, test helpers, generation
  assembly, prompt/action callers, trace fixtures and authoring guidance.
- Public-surface delta: none; new implementation types stay internal.
- **Shim decision: No shim**, explicitly directed and supported by atomic internal
  updates. Do not retain obsolete constructor overloads for this change.
- **Java-to-Go boundary coordination: Not required** for the selected extensible
  tool-frame payload approach; fixture coherence checked in Java. No release
  string change because Console can retain/expose these existing generic payloads.
- **Pipeline notes alignment: Aligned** with the developer's explicit no-shim
  direction, persisted here and in research; no broader access/API scope change.

## Implementation Approach

Use small internal binding/path/projection/assembly collaborators beside current
input and planning code. Reuse generation resolution, recursive schema nodes,
mission ownership, accepted task results and existing validation/failure lifecycle.
Do not make bound fields mutable metadata or business input identifiers a source
selector. Making no framework change leaves authoritative transfer model-authored;
model-written references or a generic expression store would add avoidable trust,
state and syntax. Explicit declarations plus existing mission authority meet the
ticket with less scope. Remove any superseded internal wiring and constructors
atomically; no unrelated technical-debt cleanup is planned.

## Phase 1: Compile declarations and separate model contracts

Changes: `YamlSkillManifest`, `YamlSkillCatalog`, `AllowedSkillConstraint`,
`YamlSkillDefinition`, `SkillGenerationManager`, internal input projection/path
types, and `LoomspanJacksonCodecs` as needed for strict duplicate-key handling.
Compile complete-generation shape/dependency checks. Add the effective contract
to `BoundCapability`; update every internal constructor/caller atomically.

Automated success criteria:
- [x] Catalog/generation positive and rejection tests pass.
- [x] Projection tests establish nested requiredness and structural exclusions.
- [x] `LoomspanPublicSurfaceArchitectureTest` passes.

## Phase 2: Enforce declaration dependencies and model action boundaries

Changes: `DefaultPlanningService`, existing/new focused plan validator,
`StepPromptBuilder`, `StepActionValidator`, and step engine preflight. Require
unique producer tasks, explicit direct edges and earlier units, using the same
total planning correction. Render effective contracts consistently and reject
overrides before invocation, including generic/open branches.

Automated success criteria:
- [x] Planner tests prove corrected/exhausted feedback and no invalid plan storage.
- [x] Action/prompt tests prove `{}`, partial objects, open siblings, forbidden
  destinations and consistent corrective requests.
- [x] Existing grouping/assigned-task tests pass without global serialization.

## Phase 3: Assemble exact invocation-local inputs and accepted-result sources

Changes: `ExecutionCoordinator`, `MissionContext`, `CapabilityBindingFactory`,
`DefaultCapabilityInvoker`, step engine snapshot handoff, internal assembler,
result decoder, `CapabilityExecutionRouter`, `SkillInputValidator` and callers.
Retain parent source snapshots and decode accepted results once in existing
entries. Assemble before dispatch; validate bound subtrees without coercion or
rewriting. Keep normal authentication, generation and limit paths.

Automated success criteria:
- [x] Exact/copy/missing/null/conflict/number/date/decoder unit tests pass.
- [x] Offline public-path model/Java/REST integration delivers exact values.
- [x] Nested/concurrent parent and reload isolation pass; failed/corrected/late
  work supplies no accepted source; existing forwarding tests pass.

## Phase 4: Record provenance, document and verify the complete capability

Changes: existing tool-frame evidence fields and failure attribution, targeted
trace tests/current fixtures if needed, README and authoring topics listed above.
Use one fixture-backed complete example; do not claim live reliability gains.

Automated success criteria:
- [x] Canonical evidence distinguishes raw arguments and delivered input and
  links exact sources without duplicate payload storage.
- [x] Current trace reader/projector/corpus suites pass and journal exception
  stays unchanged.
- [x] All changed authoring claims map to source and focused tests.
- [x] Focused suites, public architecture and full offline Maven suite pass as
  specified in the testing plan; `git diff --check` passes.

## Testing Strategy

The dedicated testing artifact defines red test, unit matrices, deterministic
public integration, exact commands and exit criteria. Use local model/HTTP fakes,
real generation capture, latch/barrier concurrency assertions and child counters.
No paid provider call or live comparison is required. No optional developer check
is required for correctness.

## Performance Considerations

Compile paths/projections once per captured generation or parent-tool binding.
Decode accepted results once, retain raw evidence only in existing owners, and
copy selected values per consumer for isolation. Cost is proportional to selected
payload and contract size; no implicit truncation. Large source evidence remains
in the receiving model's normal input, so do not claim lower receiving tokens.

## Migration Notes

No application migration: bindings are opt-in. Internal constructors/types and
fixtures update in one change with no compatibility shims. New manifests use the
single documented pointer syntax. Existing PR 20 String forwarding and `ref://`
resolution remain unchanged.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-21-declared-child-input-bindings.md`.
- Research: `ai/thoughts/research/2026-10-04-PR-21-declared-child-input-bindings.md`.
- Design lens: `ai/thoughts/framework-feature-design-lens.md`.
- Testing: `ai/thoughts/plans/2026-10-04-PR-21-declared-child-input-bindings-testing.md`.

## Step 4 Implementation Decisions and Verification (2026-10-05)

- Added internal `ObjectFieldPath`, binding/declaration/projection collaborators,
  `DeepInputValues`, accepted-result decoder, assembler and plan dependency validator.
  No Application API or SPI was added. The architecture test's technically-public
  internal reasons map was updated atomically; its supported API allowlist is unchanged.
- Removed the unused internal `runtime.tool.CapabilityInvoker` interface rather
  than extending its redundant invocation surface. Bound invocations now receive
  the immutable pre-unit results explicitly; standard unbound invocations retain
  their existing argument behavior. No compatibility shim or legacy constructor
  was introduced.
- Mission input is captured exactly once before child binding. Retained accepted
  results carry one immutable decoded value and the original String; decoded data
  is excluded from serialized completed-result evidence, avoiding duplicate payloads.
- `validateExact` and `executeAssembled` name the provenance-aware receiving path.
  Bound preflight normalizes permitted unbound arguments before tool-frame capture;
  common routing revalidates full input with the same exact-path policy, keeping
  actual delivered input and canonical tool arguments aligned.
- Existing extensible frame payloads carry binding provenance. Canonical writer/
  reader tests exercise actual invoker success and source failure; no closed
  Java-to-Go envelope, compatibility marker or Go code changes are required.
- Documentation drift classification: **aligned** after updating the new topic,
  routing/coverage, seven related guides and README against implemented behavior.
  A regression loads the complete guide's three YAML declarations as one generation.
- Minimal red-first gate actually observed before parsing implementation:
  `.\mvnw.cmd '-Dtest=YamlSkillCatalogTests#acceptsParentInputBindingsWithObjectPointersForPlanningChildren' test`
  failed one assertion because `allowed_skills[0].input_bindings` was unknown.
- Initial transfer fixture failed for missing YAML array items and then duplicate
  Java/YAML test skill declarations; corrected fixtures, with all nine model/Java/
  REST combinations passing. Integrated 446-test run found two new-test issues:
  duplicate-key StreamReadException attribution and an action-correction evidence
  assertion. Both were corrected and their focused classes rerun successfully.
- Expanded declaration/planning/provenance run passed 243 tests. Isolation/guard run
  passed 14 tests, including concurrent and nested parents, captured reload,
  strict numeric-string receiving rejection, bound authorization and invocation quota.
- Protected 104-test run passed all behavioral checks but initially failed the
  internal architecture reasons map; the map was corrected before full verification.
  Final commands/results and acceptance mapping follow after required runs finish.

Optional developer checks: none. Paid evaluations, sibling replay/baseline changes,
commit, push and release actions remain outside this execution.

### Acceptance evidence map

| Ticket criterion | Implemented authority and deterministic proof |
| --- | --- |
| Both source declarations and renamed fields, complete example | Catalog/generation declaration checks, `ChildInputBindingTest`, `YamlSkillCatalogTests`, full guide example loaded by `DeclaredChildInputBindingsIntegrationTest#completeAuthoringExampleLoadsAsOneValidatedGeneration` |
| Large nested payload without generated copying | Nine model/Java/REST combinations in `DeclaredChildInputBindingsIntegrationTest#deliversLargeBoundEvidenceFromEmptyModelArguments`: 250 original records, exact BigInteger/BigDecimal and nested equality, raw empty/reasoning-only action maps |
| Projected contracts, partial/all-bound/open/corrective calls | `ChildInputBindingProjectionTest`, `StepActionValidatorTest`, compact/verbose `StepPromptBuilderTest`, actual engine bound-override correction with one accepted child call |
| Exact absent/null/array/number/date transfer and mutation isolation | `AcceptedResultDecoderTest`, `ChildInputBindingAssemblerTest`, `SkillInputValidatorTest`, `MissionContextTest`, exact receiving router guards and public zero-dispatch failure |
| Invalid declarations, ambiguity, missing/failed sources and overrides | Catalog rejection matrix, complete generation graph/path tests, dependency tests, source/conflict assembler tests, actual failure trace with no dispatch |
| Producer dependencies, grouped execution and invocation isolation | Plan uniqueness/direct-edge/earlier-unit validator, one-total planning correction, existing group pre-unit snapshot/full-join tests, `ConcurrentGroupedExecutionIntegrationTest`, concurrent/nested/reload `BindingIsolationIntegrationTest` |
| Model/Java/REST types, access and limits without inferred output schema | Nine-kind transfer matrix, bound router access-denial guard, public tool-quota counter guard and existing authentication/authorization/usage suites |
| Attributable raw/effective/provenance evidence and original results | `DeclaredChildInputBindingsTraceTest` actual invoker canonical writer/reader roundtrip, trace/corpus/journal tests; decoded retained trees excluded from serialized raw-result evidence |
| Protected unbound/ref/output forwarding/API behavior | Full offline suite, `DesignatedChildResultIntegrationTest`, reference and normal validation tests; unchanged supported allowlist and sole `RestSkillHandler` SPI |
| Offline verification and precise documentation | Local provider/REST/Java fakes, source-anchored guide with validated three-file example, routing and coverage updates; no paid calls, sibling changes or live reliability claim |

Final full command `.\mvnw.cmd test` passed **1374 tests, 0 failures,
0 errors, 2 skipped** in 3 min 21 s. Both skipped tests are existing optional
`Pr18RecordedHandoffDiagnosticTest` methods guarded by absent `pr18.diagnostic`;
no required binding or public-surface test was skipped. Log:
`target/pr21-full-tests.log`.

After that full compilation, two test-only additions exercised the complete
Markdown example and rejection of an invalid model producer candidate before
binding its accepted result. The initial final-focused run passed all behavioral
and architecture cases but failed the guide-block extraction regex on Windows
CRLF. The regex now accepts universal line breaks; its focused rerun provides
final verification of this test-only change. Production has not changed since
the successful full suite.

Final focused verification passed **458 tests, 0 failures/errors/skips** in 1 min
17 s after the CRLF fixture correction. Exact command ledger is in the testing
plan's Implementation Verification Receipt. `git diff --check` passes. No
production API/SPI signatures or bean extension points changed; all eight new
technically public types are explicitly classified as internal implementation
collaborators. All phase checkboxes reflect completed implementation/verification;
independent Step 5 review remains the pipeline completion gate.
