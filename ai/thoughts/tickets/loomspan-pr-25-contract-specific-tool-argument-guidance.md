# PR 25 — Make generated tool-argument guidance match the effective contract

## Outcome

Generated assigned-task instructions should tell the model precisely whether it must supply arguments, may supply optional arguments, or must leave toolArguments empty. Derive that guidance from the effective model argument contract after input bindings, without new skill-author configuration or changes to validation semantics.

Remove the generic footer that says an empty toolArguments object is only illustrative and instructs the model to replace it when an empty object is genuinely valid or required. The complete generated prompt must give one consistent account of model versus Framework argument ownership.

PR 25 is the user-selected proposed PR number, not a claim that a GitHub PR already exists. This request creates a ticket only; it does not start the pipeline or implementation.

## Context

Retained PR22/23 MiMo and Luna reference-workflow prompts correctly say that Framework supplies bound fields and the model must provide only unbound arguments. However, the same prompts end with:

> The empty toolArguments object is illustrative only. Replace it with the real arguments required by the assigned tool contract above; include every required argument.

That language implies an empty example needs filling even when no input remains required or permitted. This is avoidable ambiguity. Luna required a correction for malformed optional candidate-reasoning JSON; MiMo required a correction for an unsupported top-level uncategorized field. These observations do not establish that the footer caused either failure. Success for this ticket is contract-consistent guidance, not a promised model-error reduction.

PR24 is currently in progress and addresses automatic direct dispatch when no model contribution is possible. PR25 complements it: optional and partially bound tasks still require model dispatch, and conservative fallback paths may retain dispatch even when empty arguments are valid. Do not restore a model call just to show improved guidance. Do not modify or interrupt PR24's in-progress changes; integrate against its resulting contract/projection semantics and verify compatibility before completing PR25.

## Requirements

### 1. Contract-specific instructions

Use the effective model-facing argument contract, including reserved bound destinations, rather than the original full receiving schema or the apparent emptiness of a rendered example. Distinguish these cases:

| Proven contract condition | Required message meaning | Suggested wording |
| --- | --- | --- |
| No model-supplied arguments permitted; empty model arguments are valid | Require an empty argument object and explain Framework ownership. | Use toolArguments: {} exactly. Framework supplies all arguments through bindings. Do not reproduce bound fields. |
| No model arguments required, but optional contributions are allowed; empty model arguments are valid | Explicitly state that {} is valid. Optional content remains optional under the contract. | toolArguments: {} is valid. Include only optional contributions needed for this task. Framework supplies bound fields; do not reproduce them. |
| Required unbound arguments remain | Identify the required model-owned fields and require them, without asking for bound fields. | Supply the following required unbound arguments: [the actual contract-derived fields]. Framework supplies bound fields; omit those fields from your response. |

The bracketed description in the last example describes generated content, not literal prompt text. Exact prose and formatting are implementation choices; these distinctions and consistent ownership are required.

Adapt the explanations to the actual contract. For example, an explicit zero-input tool with no bindings should say the tool takes no arguments, not falsely claim Framework supplies them through bindings. A partially bound tool must not be described as entirely Framework-owned.

### 2. Conservative handling of schema details

- Optional fields count as permitted model contributions even when every required field is bound. Do not equate an empty required list with a prohibition on model arguments.
- Distinguish an explicit closed empty contract from an absent, generic or unconstrained schema. Unknown does not mean zero-input.
- Assert that {} is valid only when supported by actual effective-contract semantics. Where richer/conditional shapes cannot be summarized faithfully, retain truthful neutral guidance to follow the effective contract and supply only unbound arguments. Do not invent required fields or promise that {} is valid.
- Describe nested required fields with enough path/context to avoid ambiguity. Do not claim an optional ancestor is required merely because its child has a conditional requirement when that ancestor is present. Preserve absent/null/empty-object distinctions.
- Respect open additional properties and any restrictions on their values. Do not describe an open object as a closed list of declared optional fields.
- Bound destinations remain prohibited model overrides, including within otherwise open objects. Examples and corrections must not instruct the model to reproduce them.
- Reuse authoritative contract/projection semantics. PR24's direct-dispatch eligibility can inform the no-contribution case, but direct-dispatch ineligibility by itself does not prove arguments are required: hooks, conservative shape handling or optional contributions can retain model dispatch.

### 3. Optional reasoning and authored skill instructions

In the equipment reference example, compareOptions accepts optional context content through an open object; the parent skill convention is to put new candidate reasoning under context.candidateReasoning. This name is not a schema-declared property merely because authored prompt text mentions it.

Framework guidance must not promote that convention into a required or declared schema field, fabricate a candidateReasoning field for unrelated skills, or instruct the model to omit a contribution that authored task instructions actually request. Explain what the contract allows while preserving the skill/task's instructions about useful content. An optional-only contract permits {}, but that does not override a task instruction to provide a particular optional contribution.

No new argument-generation phase, reasoning-removal experiment, per-model wording or per-skill mode is part of this ticket.

### 4. Consistency across the complete generated prompt

Replace the contradictory generic footer rather than appending a correct instruction while leaving the conflicting instruction present. Keep the action envelope, assigned task identity, exact tool selection and raw-JSON requirements intact.

Apply consistent argument-ownership guidance to normal dispatch prompts and correction/verbose-guidance variants that can be produced for the same assignment. Examples, schema summaries, required-field lists, binding explanations and footer must agree. Required-input examples must not imply {} is a complete valid answer; optional-input examples must not imply that optional content is mandatory.

Preserve user/skill-authored prose, canonical input and source evidence. Do not rewrite source data or sanitize content to resolve instruction disagreements. This is a change to Framework-generated guidance, governed by repository data-fidelity rules.

### 5. Scope and compatibility

This is a prompt-generation change. Keep receiving schemas, binding declarations and assembly, validators, correction budgets, tool execution, authorization, scheduling, direct-dispatch policy, public APIs and serialized contracts unchanged. No new public configuration or extension SPI is needed.

The generated prompt text intentionally changes; update affected prompt expectations without rewriting historical captures. Update relevant Framework skill-author guidance to explain the distinction between required, optional and Framework-owned input. No changes to the sibling reference-suite skills, scenarios, evaluator or replay fixtures are authorized here.

Use offline prompt/contract verification. Paid evaluations are not required, and no measured error-rate improvement should be claimed without separate empirical evidence.

## Acceptance criteria

- [ ] Fully bound closed and explicit zero-input examples produce accurate no-contribution guidance when a prompt is rendered; neither instructs the model to fill an empty example. A zero-input tool without bindings does not claim nonexistent bindings.
- [ ] Optional-only and open-context examples explicitly permit {} when the effective contract does, preserve optional contributions, and do not fabricate declared/required fields from prompt conventions such as candidateReasoning.
- [ ] Partially bound examples identify actual required unbound fields, exclude bound destinations, and do not present {} as a sufficient response when required input remains.
- [ ] Nested optional/required structures, bound destinations inside open objects, absent/generic schemas, and unsupported summaries cannot produce false claims about required fields, allowed fields or empty-object validity. Guidance agrees with the existing validator, without changing what it accepts.
- [ ] Normal and correction/verbose variants have no contradictory generic footer, field summary or example. Assigned task/tool identity and JSON-envelope rules remain intact; canonical input and authored/source prose are preserved.
- [ ] With PR24 integrated, eligible direct dispatch remains direct; retained model paths receive correct guidance without changes to eligibility or execution behavior. No new skill-author flag, schema declaration, public API or configuration is introduced.
- [ ] Offline verification demonstrates these prompt/contract relationships and confirms unchanged validation behavior. Documentation explains ownership and optionality without making model-reliability claims; historical evidence and sibling reference-suite contracts remain unchanged.

## Implementation orientation

Known areas from the preceding investigation include StepPromptBuilder's assigned-step prompt, its effective model argument schema rendering, and correction-time verbose argument guidance. ChildInputBindingProjection and the effective input contract are the semantic source. PR24 may refactor these areas while running; verify the resulting checkout and reuse its authoritative semantics rather than duplicating a schema classifier. These are orientation hints, not prescribed file changes or a substitute for current-checkout triage.

Supporting retained evidence in the sibling repository:

- C:/opendev/code/loomspan-sidecar-test-suite/evidence/live-20261005-140826-7cfb36/journal.json — MiMo priority request 03e0246edbdb4ffe9a33c1fa48217ebc contains the conflicting footer and an effective contract allowing optional unbound context.
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/pr23-mimo-20261005/priority-correction-review.json — valid JSON with unsupported uncategorized field, followed by successful correction.
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/pr23-adoption-20261005/summary.md — Luna's recovered optional-reasoning serialization error and limits of causal attribution.

The ticket is independently actionable if those optional evidence references are unavailable.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** Intent is settled and scope is bounded to generated guidance, its offline verification and documentation. No execution, security, lifecycle, public API or schema semantics should change. Independent review is warranted to catch mismatches between schema meaning, examples and correction prompts.
- **Reassessment triggers:** Upgrade to Full 5-Step Pipeline if PR24's resulting checkout makes this require changes to eligibility, validators, binding semantics, execution behavior or supported contracts, or if material design uncertainty cannot be resolved within bounded prompt-generation work. Do not broaden scope silently.
