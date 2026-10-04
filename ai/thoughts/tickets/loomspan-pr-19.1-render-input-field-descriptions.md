# PR 19.1 — Render authored input descriptions in model guidance

## Outcome

Make authored input-schema descriptions visible to the model when Framework
describes a structured child/tool input contract. Field names, types and required
lists describe shape; descriptions explain meaning, units, provenance and intended
use. Authors should not have to duplicate those explanations in parent prompts.

## Requirements

- Include nonblank descriptions from the resolved input contract in actual
  assigned-tool argument guidance, in ordinary and corrective requests, under
  both compact and verbose rendering. Associate each description unambiguously
  with its schema path, including root/object descriptions, nested properties,
  array item schemas and described typed additional-value schemas.
- Preserve authored description content. Use formatting or escaping that keeps
  multiline text, punctuation and literal path characters understandable without
  changing their meaning. Do not summarize, classify, mask or sanitize descriptions.
- Keep requiredness, optionality, types, enums and open/closed object rules
  distinct from descriptive prose. Descriptions do not create validation rules,
  defaults, data bindings, or permission to invent absent input values. Do not
  infer descriptions for nodes that have none or repeat an ancestor description
  at every descendant. Avoid needless repetition between the example and rules.
- Apply the behavior to supported contracts through their shared resolved
  representation, without domain, model or provider special cases. Retain generic
  and no-argument contract behavior except where a described structured node
  already participates in rendered guidance.
- Preserve input validation, normalization, tool execution, planning/scheduling,
  public API, configuration and serialized contracts. This ticket does not add
  schema keywords, automatic input repair, evidence transfer, optional-field
  policy, or changes to application skill contracts.

## Acceptance criteria

- [x] Actual ordinary and corrective model requests contain the supplied
  descriptions at the correct paths for both compact and verbose structured
  input guidance, not merely in a separately published schema.
- [x] Nested objects, array items, typed additional values, optional ancestors
  and literal dotted keys retain correct path association. Multiline descriptions
  retain their authored content; absent descriptions produce no invented text.
- [x] Existing structural guidance remains correct and descriptions do not alter
  acceptance/rejection of the same inputs or the other contracts listed above.
- [x] Undescribed, generic and no-argument cases retain coherent behavior; no
  application-specific terms or model/provider exceptions enter production guidance.
- [x] Documentation states the actual model-visible behavior and distinguishes
  explanation from enforced constraints. Verification distinguishes deterministic
  prompt coverage from any optional stochastic accuracy experiment; live model
  access or demonstrated accuracy gains are not prerequisites for completion.

## Context

An application strengthened receiving YAML skill schemas on installed Framework
PR 19 (`79bdbe8`, implementation `c5d1864`). Direct validator checks rejected
missing required fields and wrong shapes correctly. Actual step prompts displayed
nested properties and required/optional lists but omitted schema descriptions.
The provider request contained only messages, model and reasoning configuration,
so the missing descriptions were not available through another tool-schema field.

This is an observed guidance omission, not proof that adding descriptions fixes
model accuracy. Structural schemas also cannot prove that values match earlier
results or that every original record was copied. Keep those limitations separate.

Reproduction evidence is in sibling `loomspan-sidecar-test-suite`, under
`evidence/strong-schema-experiment-20261004/`: candidate `skills/*.yaml`,
`baseline-assessment-step-prompt.txt`, rendered guidance and
`framework-guidance-findings.json`. A likely source area is
`SkillInputPromptRenderer` and its integration into `StepPromptBuilder`; verify
these hints against the current checkout rather than treating them as a plan.

The developer reports GitHub PR 19 remains open and explicitly assigned **19.1**
as this follow-up ticket identifier. It is a local correlation label, not a claim
that GitHub has a fractional pull-request number. Work should build on PR 19's
guidance without removing it. The separately requested general authoring guidance
is an authorized documentation edit, not evidence that this renderer change is
already implemented.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** high
- **Rationale:** The desired behavior and scope are settled and bounded to input
  guidance. Targeted implementation verification and independent review provide
  assurance for model-facing text and its runtime integration.
- **Reassessment triggers:** Missing description information requiring a public
  or serialized-contract change, materially different semantics, or broader
  production changes require scope review and consideration of the Full 5-Step Pipeline.

## Execution notes

- On 2026-10-04 the developer approved the Fast-Track 2-Step Pipeline —
  Implementation & Review by replying “proceed”. Step 4 is ticket-led; no research,
  implementation-plan or testing-plan artifact is required. Independent Step 5
  review remains required.
- Initial working-tree changes belong to the developer: modified
  `agent-skills/loomspan-docs/references/skill-authoring/README.md` and
  `input-contracts.md`, plus untracked `input-contract-design.md` and this ticket.
  Preserve the existing authoring-design routing and prose. Ticket work extends
  only the existing generated-guidance subsection and its coverage entry; the new
  design topic is unchanged.
- Targeted reconnaissance confirmed Java JSON-schema and YAML manifest resolution
  already preserve description strings in `SkillInputSchemaNode`. The omission
  is confined to `SkillInputPromptRenderer`; `StepPromptBuilder` consumes the
  resolved metadata contract for actual assigned requests. No resolver, validator,
  execution, scheduling, configuration, manifest syntax or serialized change is
  needed.
- Affected production surface is internal prompt rendering. The author-facing
  improvement exposes existing description metadata without changing any supported
  Java API/SPI, Spring extension point, access boundary, lifecycle or Java-to-Go
  protocol. No shim or compatibility-marker change is appropriate. Fast-track
  eligibility remains established by this bounded scope.
- Render descriptions separately from illustrative values and structural rules,
  once per described node, with JSON-quoted paths and original text. JSON escaping
  preserves multiline content and keys containing punctuation or literal path
  characters without masking, rewriting, summarizing or truncating authored text.
  Generic contracts continue to omit structured guidance; a described strict-empty
  root retains its no-argument rule and receives its own description.
- Existing correction behavior always forces verbose detail. Verification covers
  compact and verbose ordinary requests, actual verbose corrective requests, and
  all described paths in both renderer modes; this ticket does not change detail
  selection or invent a compact correction execution mode.
- Skill-authoring impact is affected: update the generated-guidance topic and
  README coverage using the same-checkout `loomspan-docs` router and source
  verification protocol. Drift classification is aligned for existing structural
  guidance; description rendering was an observed omission expressly authorized
  by this ticket, and the updated guidance documents the implemented behavior.
  Prompt coverage and validation equivalence are deterministic guarantees;
  optional stochastic accuracy comparisons require separate scenario/model
  evidence and are not completion gates.
- Step 4 verification: `.\mvnw.cmd '-Dtest=SkillInputPromptRendererTest,StepPromptBuilderTest,StepLoopMissionExecutionEngineTest,SkillInputValidatorTest,SkillInputContractResolverTest,LoomspanPublicSurfaceArchitectureTest' test`
  passed with 123 tests, zero failures/errors/skips. Earlier attempts exposed two
  new-test compilation mistakes (helper arity and generic-list inference), both
  corrected before the passing run. `git diff --check` passed. Full-suite and live
  model accuracy runs were not needed for this bounded renderer change; the latter
  remains an optional developer experiment. Acceptance checkmarks record Step 4
  implementation evidence; fresh independent Step 5 assurance remains outstanding.
