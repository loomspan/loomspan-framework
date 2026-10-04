# PR 18 Generated Input Guidance Testing Plan

## Change Summary

Generated illustrations and rules will accurately describe required/optional
properties and additional fields at their own object locations in compact,
verbose and corrective requests. Runtime contracts and business inputs are fixed.
The approved profile is `full`; no developer decision remains for these tests.

## Impacted Areas

- `SkillInputPromptRenderer`, `StepPromptBuilder`, engine model-request composition.
- Rendering and builder assertions; engine correction/request captures.
- Existing resolver, validator, action-validator and public-surface regressions.
- Input-contract authoring topic/README coverage.
- Separate diagnostic evidence and controlled live-provider handoff attempt.

## Risk Assessment

The largest risk is fixing the root wording while compact/deep/array/typed-map
children still omit boundaries. Optional parents must not become mandatory because
their children have required keys. Open unrestricted nodes, typed maps and
unconstrained leaves must stay distinct. Empty open objects must not become
no-argument claims. Unknown root/closed-child fields must remain rejected, and
additional-schema restrictions must still apply only to undeclared fields.

Protected surfaces: existing application API/SPI, schema defaults/validation,
references, authorization, explicit invocation and input/evidence fidelity. The
old global prompt sentence is obsolete internal presentation; update assertions
without retaining a fallback. No durable format, public signature, Console or
Java-to-Go protocol changes are planned. Existing journal-projector behavior is
unrelated. No new sensitivity-based rewriting tests are appropriate.

## Existing Test Coverage

- `StepPromptBuilderTest`: resolved-contract precedence, nested verbose rules,
  omitted-keyword openness, typed maps, omitted array items, unconstrained values,
  mission-input and prior-result fidelity and exact assignment.
- `SkillInputContractResolverTest`: schema round trips and generic/strict-empty identity.
- `SkillInputValidatorTest`: unknown keys, null/JSON-kind fidelity, typed extra
  values, generic arrays, references, attachments and immutable normalization.
- `StepActionValidatorTest`: exact assignment and validation before side effects.
- `StepLoopMissionExecutionEngineTest`: fake `ModelInteraction` request capture and
  complete corrective action behavior.
- `LoomspanPublicSurfaceArchitectureTest`: supported API and accidental extension boundaries.

Missing coverage: affirmative nested permissions in both modes, optional example
fields, complete traversal past depth three/consecutive arrays, and the recorded
handoff's full revised request. Existing tests cannot establish live accuracy.

## Bug Reproduction / Failing Test First

- **Type**: unit.
- **Location**: new `src/test/java/ai/loomspan/internal/runtime/input/SkillInputPromptRendererTest.java`, or an equivalent focused case in `StepPromptBuilderTest`.
- **Arrange**: explicit closed root with required `shipmentId` and `details`;
  `details` is open with required `routingEvidence` and optional `note`. Use a
  schema unrelated to equipment/model names.
- **Act**: render COMPACT and VERBOSE.
- **Assert**: root restriction says top level/root only; `details` affirmatively
  permits additional fields; its required and optional keys are distinguished;
  illustrative examples are not exhaustive; global sentence is absent.
- **Pre-fix failure**: compact has no scoped permission/requiredness, verbose has
  ambiguous global closure and no affirmative open rule. Save exact red command
  and failure before editing production code; do not infer red from later results.

## Tests to Add/Update

### 1) Scoped requiredness and illustrative examples

- **Type/location**: renderer unit test and builder integration assertions.
- **Proves**: closed root/open child, open root/closed child, closed empty child,
  empty unrestricted open child, omitted openness default, required vs optional
  properties, optional parent with required children only when supplied.
- **Fixtures**: small resolved contracts/JSON schemas using shipment/catalog names.
- **Mocks**: none in renderer; existing bound-capability mocks in builder.
- **Surface/expectation**: internal presentation changes, protected schema semantics.

### 2) Recursive arrays and schema-constrained additional values

- **Type/location**: renderer unit tests.
- **Proves**: arrays of objects, arrays of arrays of objects, typed arbitrary-key
  objects with nested closed/open children, constrained scalar/enum extra values,
  additional schemas containing arrays. Traverse at least five levels so old
  depth cutoff fails; assert both modes preserve every object's boundary.
- **Fixtures**: explicit arbitrary-key and item nodes; include ANY_TYPE leaf and
  array without item schema. Include a literal dotted property name to ensure
  paths remain distinguishable from nested properties.
- **Mocks**: none.
- **Surface/expectation**: internal presentation, unchanged manifest/schema contracts.

### 3) Presentation thresholds and special identities

- **Type/location**: `StepPromptBuilderTest`.
- **Proves**: ordinary selection and forced verbose retain equivalent essential
  rules; schemas on either side of depth/property thresholds remain accurate;
  generic contract suppresses structured guidance; strict empty contract still
  requires an empty argument object; resolved metadata remains authoritative.
- **Fixtures/mocks**: existing `mockTool` helpers; small schemas at 6/7 properties
  and existing depth-selection boundary.
- **Surface/expectation**: internal detail policy, protected defaults and invocation.

### 4) Ordinary and corrective actual requests

- **Type/location**: engine integration test in `StepLoopMissionExecutionEngineTest`.
- **Proves**: capture complete model requests; ordinary and corrective messages
  contain scoped root/open-child guidance; rejected candidate and supplied evidence
  remain intact; valid extra nested values are passed unchanged on retry; invalid
  root/closed-child input executes no tool before correction.
- **Fixtures**: open-child schema and supplied string/null/list/object evidence;
  queued invalid action, valid corrected action and final response.
- **Mocks**: existing fake `ModelInteraction` and test execution tooling.
- **Surface/expectation**: current-request diagnostic fidelity, existing authorization,
  validation and explicit execution behavior.

### 5) Same validator outcomes and supported surfaces

- **Type/location**: existing resolver/validator/action-validator tests; add only
  missing focused cases for the rendering regression's precise schema.
- **Proves**: extra root rejected, extra open child accepted with exact values,
  missing required nested field rejected, invalid typed additional value rejected,
  valid typed value accepted, declared typed field unaffected by extra-value rule.
  Existing reference and assignment tests still pass. Architecture allowlist
  remains closed with no internal type leaks or new extension points.
- **Mocks**: existing helpers only.
- **Surface/expectation**: protected runtime/schema/API/SPI paths; no semantic break.

### 6) Recorded handoff request and controlled live diagnostic

- **Type/location**: opt-in diagnostic harness/test plus separate evidence outputs;
  production Maven tests must not require the sibling checkout or credentials.
- **Proves**: actual Framework-generated request at model boundary for retained
  `task-compare-options` contract. Saved full request shows root closure, all five
  required context fields, nested openness and the actual supplied evidence.
- **Fixtures**: immutable retained experimental YAML and baseline trace sequence
  480, identified by path, sequence, assignment and hashes. Use fixed mission
  input/prior task results instead of rerunning a changing mission.
- **Mocks**: fake model for Framework request generation; live provider for the
  separately labeled comparison. A fake/replay response is never live evidence.
- **Controls**: take the retained full request as common envelope, replace only
  the exact argument-guidance section with generated revised text; assert all
  other messages, contract, authored instructions, evidence and settings match.
  Record settings unknown historically; choose one fixed value for both arms.
- **Repeated calls**: three old and three revised calls, alternating arms, finite
  timeouts. Save outbound bodies, responses, provider/parse errors and counts.
- **Scoring**: structural validity uses unchanged resolved validator/assignment;
  required context retention uses source evidence and unchanged authored business
  requirements. Report exact retained/missing values and identities, five schema
  fields, complete assessment/history/references, operating values, issued quotes,
  entitlements/resources and continuity offers separately from structural success.
- **External blockage**: absent credential/provider availability produces an
  unverified live result with prerequisite evidence; deterministic and actual
  Framework-request checks still run. No deployment is needed for direct diagnostic
  calls; if a stack is altered, restore and verify provider-disabled normal services.
- **Surface/expectation**: current-run diagnostic coherence/fidelity. Existing skill
  files, replay fixtures and business acceptance criteria remain byte-identical.

## Authoring Claims Requiring Evidence

Document examples as illustrative, distinguish optionality, and explain scoped
closed/open/constrained permissions. Tests 1-4 establish these presentation claims;
tests 5 establish unchanged enforcement. State business fields are chosen by skill
instructions and are not inferred or automatically forwarded. Preserve pure-YAML
coverage limitations. Update the README coverage row after these checks.

## How to Run

Use Java 21+ and the repository Maven wrapper in PowerShell:

```powershell
./mvnw.cmd '-Dtest=SkillInputPromptRendererTest' test
./mvnw.cmd '-Dtest=StepPromptBuilderTest,SkillInputContractResolverTest,SkillInputValidatorTest,StepActionValidatorTest,StepLoopMissionExecutionEngineTest,LoomspanPublicSurfaceArchitectureTest' test
./mvnw.cmd test
git diff --check
```

If rendering cases remain in the builder test instead of a new class, substitute
that actual test name and record the exact command. For red evidence run the
minimal new test method with `-Dtest=ClassName#methodName` before production edits.

The opt-in harness command will be recorded in the evidence manifest alongside
its artifact path. It requires the retained sibling evidence; live calls also
require authorized provider configuration/credential (existing suite convention:
`LOOMSPAN_OPENROUTER_API_KEY`). Record prerequisite presence without printing the
credential. Use real requests/responses as diagnostics; no content rewriting.

## Exit Criteria

- [x] Minimal red test actually failed on original renderer.
- [x] All targeted tests and architecture test pass post-fix.
- [x] Final repository regression command passes, or actual environment/unrelated
  failures are precisely reported without claiming a pass.
- [x] Both modes cover deep/array/typed-map object boundaries and optionality.
- [x] Correction captures preserve required rules, candidates and evidence.
- [x] Existing validator/reference/authorization/input fidelity outcomes are intact.
- [x] Supported API/SPI/configuration paths remain unchanged; obsolete wording is removed.
- [x] Authoring topic and coverage row are supported by executable anchors.
- [x] Full Framework-generated recorded-handoff request is retained with provenance.
- [x] Guidance-only fixed-input controls and repeated live attempts are recorded,
  or external prerequisite blockage is clearly labeled unverified.
- [x] Structural correctness and live validity/business retention are reported
  independently; no accuracy claim is inferred from a fake or single response.
- [x] Historical source evidence/skills/replays/checks are unmodified, and any
  modified runtime stack is restored to provider-disabled normal services.

## Optional Developer Checks

None required. Broader model comparisons are outside this ticket; this small
diagnostic can inform later work without being a universal accuracy conclusion.

## References

- Implementation plan: `ai/thoughts/plans/2026-10-03-PR-18-scope-generated-input-guidance.md`.
- Ticket and research referenced in that plan.
- Shared automation and Loomspan documentation protocols.

## Step 4 verification record

Implementation and required checks completed. See `ai/thoughts/evidence/2026-10-03-PR-18-generated-input-guidance/implementation-verification.md` and the diagnostic `README.md` for exact commands, actual results, source-integrity controls, the retained invalid attempts, and recorded live outcomes. Independent Step 5 review remains required.

The opt-in diagnostic must use production YAML catalog validation/defaulting before
contract resolution. Assert a closed recorded root, an explicitly open context,
the five nested required fields, and root-extra rejection; raw YAML deserialization
is not equivalent to a loaded skill. Preserve and exclude the previous
`invalid-unnormalized-contract-attempt/` from final live scoring. The six/seven
property selection test must use a depth-two schema with six/seven total
properties and assert type-detail absence/presence, so it actually exercises
ordinary compact, ordinary verbose and forced verbose modes.

Use actual Framework step-action parsing before unchanged contract validation;
fenced JSON may be accepted by the runtime even when raw strict JSON syntax is
invalid. Record raw-syntax observations separately from Framework parsing, and
score retained values from Framework-parsed actions without rewriting responses.
