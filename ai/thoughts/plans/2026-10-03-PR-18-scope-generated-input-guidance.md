# PR 18 Generated Input Guidance Implementation Plan

## Overview

Clarify each resolved object contract in generated tool-argument guidance so a
closed root cannot appear to prohibit fields inside an open child. Preserve
schema interpretation and validation. The developer approved the Full 5-Step
Pipeline with `proceed`; this plan governs Steps 4 and 5 without another routine
approval gate.

## Current State Analysis

`SkillInputPromptRenderer#renderToolArgumentsExample` renders declared properties
without distinguishing optional fields. Compact presentation omits object rules;
verbose presentation uses the ambiguous root sentence and stops rules beyond
depth three. `nestedSchema` unwraps only one array layer. The resolved immutable
schema already provides required fields, openness, item contracts and additional
value contracts; no second schema interpretation is needed.

The actual request path is `StepPromptBuilder` -> `SkillPromptComposer` ->
`ModelInteractionRequest` in `StepLoopMissionExecutionEngine`. Validation
correction forces verbose argument guidance while retaining the rejected action
and feedback. Existing tests capture requests at that model boundary. Historical
evidence supports a plausible presentation problem, not proven causal attribution.

## Desired End State

Both detail modes state required/optional declared fields and additional-property
permissions at each object location. Examples explicitly describe declared
structure and may include optional fields; an empty open example does not mean
the value must be empty. Closed rules enumerate only the corresponding object's
allowed keys. Open rules affirm additional fields, distinguishing unrestricted
JSON values from values governed by an additional schema. Child requirements
apply when their containing object is supplied; optional containers do not become
required. Schema-constrained values retain their own nested rules.

Boundary information remains complete through arrays, consecutive arrays,
additional-schema objects and presentation thresholds. Generic contracts remain
generic and strict empty objects remain no-argument contracts. Author instructions
select business data; openness does not mandate copying all evidence.

### Key Discoveries

- Renderer source: `src/main/java/ai/loomspan/internal/runtime/input/SkillInputPromptRenderer.java:15`, `:99`, `:122`, `:180`.
- Resolved-contract lookup and detail selection: `src/main/java/ai/loomspan/internal/runtime/step/StepPromptBuilder.java:212`, `:248`.
- Existing semantic authority: `SkillInputSchemaNode#allowsAdditionalProperties`, `SkillInputValidator#validateObject` (`src/main/java/ai/loomspan/internal/runtime/input/SkillInputValidator.java:127`).
- Request/correction integration: `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:790`, `:895`.
- Request capture seam: `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:2860`.

## What We're NOT Doing

No schema/default/validation, API/SPI, authorization, reference-resolution,
tool-invocation, provider implementation or automatic-data-forwarding changes.
No experimental skill edits, accepted replay replacements, business-check
relaxation, broad benchmark, release or deployment. Description rendering is an
optional ticket suggestion and is deferred to keep the correction focused.
Preserve supplied content and diagnostic evidence. The unrelated existing
`ExecutionJournalProjector` exception stays unchanged.

## Skill-Authoring Documentation Impact

**Impact**: Affected.

- **Rationale**: Authors need accurate expectations of how unchanged contracts
  guide models, including examples, optionality and scoped openness.
- **Documents to update**: `agent-skills/loomspan-docs/references/skill-authoring/input-contracts.md` and its README coverage row.
- **Supporting evidence**: Renderer, focused `StepPromptBuilderTest`, new recursive
  rendering tests, existing resolver/validator tests, correction request capture.
- **Coverage table update**: Required: name scoped object guidance and illustrative
  required/optional examples in the Input contracts row; retain the documented
  limitation on complete pure-YAML schema coverage.
- **LLM-first usability**: Add a concise generated-guidance subsection separating
  runtime enforcement, illustration and business field selection; use the existing
  route and stable source/test anchors rather than duplicate schema tutorials.
- **Drift classification**: aligned for existing reflected schema semantics. The
  current topic does not promise affirmative open-object guidance. This is an
  authorized clarification, not a newly discovered schema discrepancy.
- **Version alignment**: Checked-out skill metadata and Maven dependency are both
  `1.0.0-beta.8-SNAPSHOT`.

## Contract and Compatibility Impact

| Surface | Impact and supporting evidence | Planned compatibility treatment |
| --- | --- | --- |
| Application API | No signatures change; closed `ai.loomspan.api` allowlist is authoritative. | Preserve and run `LoomspanPublicSurfaceArchitectureTest`. |
| Supported SPI | No `RestSkillHandler` changes or new extension points. | Preserve sole supported SPI. |
| Configuration and manifest contracts | Resolved required/default/additional-property semantics are inputs; prompt presentation changes. | Preserve keys, schemas, normalization and validation outcomes. |
| Persisted or serialized contracts | No durable format changes; accepted replay fixtures and experimental skills are fixed source evidence. | Leave sources intact; write separate diagnostic outputs. |
| Ephemeral diagnostic formats | Model prompts intentionally change in argument-guidance section only. | Retain complete requests, responses and failures; no format/marker changes. |
| Internal or accidentally exposed implementation | Renderer recursion and obsolete root wording change; helper visibility remains internal. | Atomic update of renderer, callers if necessary, tests and guidance. |

- **Evidence of supported contracts**: User AGENTS instructions, design lens,
  architecture allowlists, README and unchanged validator/resolver behavior.
- **Intentional compatibility changes**: None to supported contracts. Old prompt
  wording is intentionally replaced, not retained as a fallback.
- **In-repository consumers to update**: Existing prompt assertions, focused new
  tests, input-contract topic and README coverage row.
- **Public-surface delta**: None; no application type, signature or Spring
  replacement contract is added.
- **Shim decision**: **No shim.** Internal renderer wording has no protected
  application compatibility contract.
- **Java-to-Go boundary coordination**: **Not required.** No REST/SSE, acquisition,
  problem, trace record schema or consumed NDJSON boundary changes.
- **Pipeline notes alignment**: **No notes.** Requirements expressly authorize
  rendering clarification and live diagnostic attempts, with no semantic break.

## Implementation Approach

Reuse the resolved nodes and existing renderer. A shared recursive traversal emits
essential object semantics in both compact and verbose presentations. Verbose
presentation may additionally show scalar/type/enum details. Removing the old
depth-limited object walk and single-array unwrapping prevents contradictory or
missing rules without adding a schema authority, propagation mechanism or public
concept. Paths identify the root explicitly and accumulate property, array-item
and arbitrary-key locations. Escape/delimit unusual property names where needed
so a literal dotted key cannot be mistaken for nested structure.

Prefer a shape header and object-scoped required/optional lists over comments
inside JSON-like examples. Keep placeholders illustrative. An open object with
no declared fields can still display an empty illustrative shape if its nearby
rule explicitly permits nonempty values and states that unlisted fields may be
included. If using an additional-key placeholder, explain that it illustrates
permission and does not itself become a required field.

Alternatives considered: changing only the root sentence leaves compact, open
and deep contracts inaccurate; emitting descriptions is not necessary for object
boundaries; using provider/business-specific instructions violates the scope;
copying all context would create new dataflow semantics. No framework change
would retain the observed ambiguity. The shared walk is the smallest complete
design. No other dead code was identified for this scope.

## Phase 1: Scoped Guidance and Deterministic Regression Coverage

### Changes Required

1. Update `SkillInputPromptRenderer` to label illustrative structure, convey
   object-local required/optional fields and closed/unrestricted/constrained
   additional-field semantics, and traverse all supported arrays/maps without
   semantic depth loss. Remove superseded global wording and recursion helpers.
2. Keep `StepPromptBuilder` selection thresholds unless wiring genuinely requires
   a change; essential information must not depend on those thresholds.
3. Add focused rendering regressions and update obsolete string assertions in
   `StepPromptBuilderTest`. Exercise unrelated business names, deep alternating
   boundaries, compact/verbose selection, arrays and typed additional schemas.
4. Capture ordinary and validation-corrective `ModelInteractionRequest` content in
   engine tests. Assert retained evidence and the same object rules before and
   after correction; reject before tool side effects and accept a valid retry.
5. Document the implemented guidance in the input-contract topic and coverage row.

### Success Criteria

- [x] Minimal closed-root/open-child regression fails on the unmodified renderer.
- [x] Rendering, builder, resolver, validator, action-validator and correction
  tests pass after the fix; unchanged valid/invalid inputs retain their outcomes.
- [x] Architecture allowlist test passes; no schema or supported surface delta.
- [x] Authoring claims match implemented behavior and focused test anchors.

## Phase 2: Actual Request Evidence and Controlled GLM Diagnostic

### Changes Required

Use a separate evidence directory, for example
`ai/thoughts/evidence/2026-10-03-PR-18-generated-input-guidance/`. Record source
paths, revision, checksums and exact invocation commands. Preserve source captures.

Select retained baseline trace sequence 480 (`planResolution#step-5-model`,
`task-compare-options` -> `compareOptions`) as the primary fixed handoff. It is the
observed omitted-context handoff and has an actual full request. Sequence 190 is
optional supplementary evidence, not a new experiment requirement.

Capture an actual Framework request for that recorded contract and assigned
handoff through the engine's model boundary. A test/diagnostic harness may admit
the recorded assignment with its fixed mission input and prior-task results,
using a fake model so no live dependency is needed to save the full revised
request. Load the unchanged retained YAML contract as diagnostic data. Save
complete system/user messages and input contract, with source attribution.
Renderer-only text does not satisfy this acceptance criterion.

For live comparison, use the retained full historical request as the common
envelope. Replace exactly its delimited TOOL ARGUMENT SHAPE section with the
Framework-generated revised section. Assert/hash that all remaining message
content, authored instructions, supplied evidence, contract and provider settings
are identical between arms. Keep `z-ai/glm-5.3-flash`, medium reasoning and all
available historical sampling/output settings fixed; record unknown original
settings explicitly and hold chosen settings constant in the new pair.

Attempt three old and three revised live calls, alternating arms to reduce order
effects. Use finite provider timeouts and persist each outbound body, response,
error/status and timing. This is an isolated handoff diagnostic: do not rerun an
entire changing mission and describe it as a controlled comparison. Prefer direct
diagnostic provider calls using the authorized existing credential/configuration
over deploying an experimental stack. If deployment becomes necessary, use the
suite's existing restoration lifecycle and verify provider-disabled normal
services afterward.

Evaluate each parsed action against the unchanged resolved contract and assignment.
Separately compare required evidence/context with the fixed source inputs and
unchanged authored business requirements: the five required fields, complete
assessment/history/reference evidence, operating context, authoritative issued
quotes, entitlements/service resources/continuity offers and identity preservation.
Record presence and retained values, not just field counts. Do not treat schema
validity as business completeness. Do not introduce new business thresholds to
obtain a success. Preserve parse/provider/validation/retention failures.

If a credential or other external prerequisite is unavailable, record the exact
failed prerequisite and mark live accuracy unverified. The ticket permits this
outcome; it does not waive deterministic checks or actual Framework-request
evidence. Report repeated negative or mixed outcomes candidly with sample counts;
neither one handoff nor this one model establishes universal accuracy.

### Success Criteria

- [x] Full actual Framework-generated request demonstrates recorded boundaries.
- [x] Fixed-input checks establish guidance-only variation for attempted live arms.
- [x] Repeated live attempts and structural/business retention outcomes are saved,
  or external prerequisite blockage is explicitly documented as unverified.
- [x] Source evidence, experimental skills, replay fixtures and checks are intact.
- [x] Final verification record distinguishes deterministic correctness from live
  accuracy and records exact commands and failures.

## Testing Strategy

See `2026-10-03-PR-18-scope-generated-input-guidance-testing.md` for the failing
test, recursive matrix, request capture, fixed-input diagnostic and exit criteria.
Use the Maven wrapper with Java 21+. Run focused affected tests and the public
surface architecture test; run `./mvnw.cmd test` as the final repository regression
check. No separate lint/format gate is configured in the examined Maven section.

## Performance Considerations

Boundary traversal is linear in the resolved schema tree. Required prompt detail
can grow beyond the old three-level cutoff; that cost is necessary to prevent
false guidance. Do not silently drop semantics under a size threshold. No model
provider or runtime resource-limit behavior changes.

## Migration Notes

No migration or compatibility shim. Update obsolete internal prompt expectations
atomically and keep historical diagnostic inputs immutable.

## References

- Ticket: `ai/thoughts/tickets/loomspan-pr-18-scope-generated-input-guidance.md`.
- Research: `ai/thoughts/research/2026-10-03-PR-18-scope-generated-input-guidance.md`.
- Design lens: `ai/thoughts/framework-feature-design-lens.md`.
- Source evidence: sibling suite `evidence/glm-skill-contract-experiment-20261003/`
  and `evidence/live-20261003-180847-e5cb8d/java/traces/loomspan-trace-0ab800c6-ad86-485f-92a5-6c7b59b1adea.ndjson`.

## Step 4 verification record

Implementation and required checks completed. See `ai/thoughts/evidence/2026-10-03-PR-18-generated-input-guidance/implementation-verification.md` and the diagnostic `README.md` for exact commands, actual results, source-integrity controls, the retained invalid attempts, and recorded live outcomes. Independent Step 5 review remains required.

## Diagnostic contract normalization

The diagnostic must load the unchanged retained YAML through
`YamlSkillCatalog.loadSupplied` before constructing capabilities or resolving the
input contract. `YamlSkillCatalog#validateInputObjectSchema` defaults omitted YAML
object `additionalProperties` to `false`. Deserializing YAML and directly creating
`YamlSkillDefinition` bypasses that production normalization and incorrectly opens
the recorded root. Explicit nested openness remains true. Assert the closed root,
open context, five required context fields and unknown-root rejection in the
diagnostic capture/evaluator, and retain these assertions in paired controls.

The earlier UTF-8 comparison made that bypass and is preserved under
`invalid-unnormalized-contract-attempt/`; it cannot establish fixed-contract model
behavior. Final parent-directory artifacts are the separately repeated normalized
comparison. The original encoding-failure run remains separately preserved. No
source skill, replay, business criterion or production schema semantic changes
are authorized or needed for this correction.

Retained responses must also be parsed by the actual Framework step-action
parser before validation/scoring. The runtime accepts fenced JSON by unwrapping
the fence; strict Python `json.loads` on the raw content is not its acceptance
boundary. The opt-in evaluator invokes that private internal parser solely for
the diagnostic, retains parser failures, and saves derived accepted actions.
Python compares business values from those actions and reports strict raw JSON
syntax separately. This changes no production parser behavior and needs no
additional provider calls.
