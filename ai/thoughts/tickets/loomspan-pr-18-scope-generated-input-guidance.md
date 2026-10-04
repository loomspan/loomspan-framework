# PR 18 — Express input-contract boundaries accurately in generated model guidance

## Outcome

Generate tool-input guidance that clearly distinguishes restrictions on an object
from permissions inside its nested objects. A model should be able to construct a
valid, complete handoff without interpreting an illustrative argument shape as a
prohibition on additional fields that the called skill's contract permits.

The developer uses weaker models to expose opportunities for general Framework
accuracy improvements. This change must follow declared contract semantics and
work across models, providers and business domains. It must not encode a workaround
for a particular model or the equipment-service demonstration.

PR 18 is the identifier supplied by the developer for this ticket. Creating this
file does not create or update a GitHub pull request.

## Requirements

- Derive guidance from the called capability's resolved input contract. State
  required fields and additional-property rules at the object location to which
  they apply. A closed root object must not be described in language that also
  appears to close a permitted nested object.
- Explicitly communicate when an object permits additional properties. Distinguish
  unrestricted additional properties from additional values constrained by a
  supported schema. Do not relax restrictions on closed objects elsewhere in the
  same contract.
- Present generated examples as illustrations of declared structure. When an object
  permits unlisted fields, its example must not imply that the listed fields are
  exhaustive. An open object rendered as an empty object must not imply that its
  actual value must be empty. Preserve the distinction between required and optional
  declared fields.
- Keep guidance consistent in ordinary assignments and corrective requests,
  including compact and verbose presentations where used. Necessary object-boundary
  information must not become misleading merely because the schema crosses a
  presentation-size or depth threshold.
- Explain only what the contract establishes. Permitting extra fields does not
  identify which extra business fields are necessary and must not become an
  instruction to copy all available context. Skill instructions remain responsible
  for undeclared business requirements. Generic advice to populate permitted objects
  using those instructions is acceptable.
- Preserve existing input-schema interpretation, defaults, validation, reference
  resolution, tool invocation and authorization behavior. This is a prompt-rendering
  change, not a new schema feature or a relaxation of runtime validation. Preserve
  input, evidence and diagnostic content; do not rewrite supplied data.
- Keep existing skill files, accepted replay fixtures and business acceptance
  criteria intact for verification. Changes to the experimental skill contracts
  are not part of this Framework ticket.

Exact wording and rendering layout are implementation choices. For the observed
contract, an acceptable meaning is:

> At the top level, only `caseId`, `assetId`, and `context` are allowed.
> Inside `context`, the five listed fields are required. Additional fields are
> allowed there. The example shows declared fields; it is not an exhaustive list
> of fields permitted inside `context`. Populate open objects with the actual data
> required by the skill instructions.

Generate names, paths and permissions from the contract rather than hardcoding
this example. The five fields in the experiment were `equipmentAssessment`,
`assetContext`, `serviceHistory`, `referenceEvidence` and `serviceTerms`.

## Acceptance criteria

- [ ] For a closed root containing an open nested object, generated guidance
  unambiguously restricts root keys while allowing additional nested fields and
  identifying the nested required fields.
- [ ] Other supported combinations of open and closed objects, including objects
  within arrays and schema-constrained additional properties, receive accurate
  rules at their respective paths. Examples distinguish optional from required
  fields and do not present open objects as necessarily empty or exhaustive.
- [ ] Compact, verbose and correction-path guidance preserve those meanings.
  Generalized examples with unrelated field names establish that behavior is not
  specific to the equipment scenario or any model.
- [ ] The same valid and invalid inputs retain their prior validation outcomes;
  prompt clarification does not change schema semantics, tool execution, data
  content, reference resolution, permissions or the supported public API surface.
- [ ] An actual Framework-generated request demonstrates the revised guidance for
  the recorded open-context handoff, with evidence retained showing what was sent.
- [ ] A controlled GLM handoff comparison is attempted under the developer's
  authorization to try the change. Hold the input contract, skill instructions,
  supplied evidence and model/reasoning settings fixed between old and revised
  guidance; use repeated calls rather than drawing an accuracy conclusion from
  one response. Report argument validity and required evidence/context retention,
  preserving failures and actual requests/responses. If provider access or another
  external prerequisite prevents the experiment, report it as unverified rather
  than substituting replay for live model behavior.
- [ ] Report deterministic contract/rendering correctness separately from live
  accuracy results. An inconclusive or negative model result remains a valid
  finding; do not weaken business checks or introduce model-specific exceptions
  to obtain a pass. No universal accuracy claim follows from this one model.

## Context and evidence

At Framework commit `b32c5d50ad1eff336d4c14bc9a4ca9cd521f48d0`, an equipment-suite
experiment added five required evidence fields to otherwise open child input
contexts. The richer schema triggered verbose tool guidance containing:

> Do not add fields not shown above.

That restriction was generated for the closed root argument object, while nested
`context.additionalProperties` remained `true`. The example displayed only the
five declared context fields and did not explain that additional context fields
were permitted.

In both experimental Java scenarios with OpenRouter `z-ai/glm-5.3-flash`, medium
reasoning, the comparison call contained exactly those five fields. It omitted
operating values, issued quotes, entitlements, service resources and continuity
offers that were available to the assigning model and allowed inside context.
The comparison child reported missing authoritative quotes, returned `undecided`
with an empty quotes array, and later parent results included quotes again.
Complete history and manufacturer guidance did survive this experiment, unlike
the earlier baseline.

This is evidence of a plausible guidance problem, not established causation. The
earlier experiment changed both the skill schema and authored prompt, as well as
automatically changing rendering detail. The earlier handoff to planResolution
also retained extra fields, showing that the wording did not always prevent their
transfer. Earlier planning failures cannot simply be attributed to a later
handoff prompt. The requested controlled comparison should isolate the generated
guidance rather than repeat those confounded changes.

The experimental skills were saved separately and previous suite defaults were
restored. Historical approved replay inputs use different assessment names and
sometimes source envelopes; do not overwrite those fixtures to accommodate the
experimental schema. Use the retained experimental schema as a fixed diagnostic
input where needed, not as a new suite-wide contract. Provider access was disabled
after the previous run; preserve runtime evidence and restore provider-disabled
normal services after any new live experiment that changes the deployed stack.

Relevant source hints, subject to verification by the pipeline:

- `SkillInputPromptRenderer` renders examples and verbose rules. Its root-level
  restriction omits an explicit top-level qualifier; open-object permissions are
  not positively described. It also does not emit schema field descriptions in
  this guidance. Description rendering is a possible supporting refinement, not
  a separately required feature in this ticket.
- `StepPromptBuilder` selects compact versus verbose argument guidance using
  schema depth and property count.

Retained local evidence in the sibling test-suite checkout:

- `C:/opendev/code/loomspan-sidecar-test-suite/evidence/glm-skill-contract-experiment-20261003/prompt-guidance-finding.txt`
  contains actual generated guidance excerpts, including baseline trace sequences
  190 and 480.
- `C:/opendev/code/loomspan-sidecar-test-suite/evidence/glm-skill-contract-experiment-20261003/`
  contains the experimental YAML copies, proposal patch and before/after findings.
- `C:/opendev/code/loomspan-sidecar-test-suite/evidence/evaluate-suite-20261003-180815-8cb795/report.json`
  links the experimental baseline and priority captures, journals and Framework
  traces. The suite completed both workflows but failed business validation.
- `C:/opendev/code/loomspan-sidecar-test-suite/evidence/evaluate-suite-20261003-173726-174de4/report.json`
  records the earlier run with the original skill contracts.

The ticket is self-contained for implementation intent; the local captures supply
reproduction data. Keep source evidence immutable. A broad multi-model benchmark,
automatic evidence forwarding, new reference mechanisms, new public APIs, changes
to model providers, releases and deployment beyond local verification are outside
this ticket. Further model comparisons may follow; do not optimize wording solely
for GLM's measured behavior.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** The intended correction is clear, but generated guidance influences
  tool calls across skills and models. The observed tradeoff between structural
  validity and evidence retention warrants research, compatibility analysis,
  deliberate verification design and independent review of this shared production
  behavior.
- **Reassessment triggers:** Current-checkout investigation may establish that a
  narrower implementation and verification scope qualifies for a lighter profile;
  the pipeline makes that decision. Any proposed schema-semantic, API,
  authorization or automatic-data-forwarding change exceeds this ticket's scope.
