# PR 15 — Preserve objective meaning in step prompts

## Outcome

Give step-execution models clear mission context and an unambiguous assigned
action without rewriting or discarding objective text. Construct appropriate
framework-owned instruction text directly instead of trying to repair it with
capability-name substitutions.

PR 15 is the developer-assigned PR identifier for this ticket.

## Requirements

1. Remove objective rewriting based on capability-name matching. Preserve
   substantive objective text, including skill-name references, occurrences
   inside other words, and text following a familiar generated prefix. Do not
   infer from objective wording that any part of it is safe to discard.
2. Construct framework-generated step-context wording explicitly at its owning
   boundary. Keep the distinction between overall mission context and the
   coordinator-assigned child task understandable. The step model must still
   receive the exact assigned task, tool, and argument contract, with explicit
   instruction not to invoke the parent mission skill. Exact wording and helper
   structure are implementation choices; broad string replacement is excluded.
3. Apply the objective-preservation behavior consistently to assigned-step
   prompts, step user messages, and final-response prompts. Keep canonical
   mission input and complete prior-task evidence available through their
   existing paths, without duplicating or rewriting business values. Final
   synthesis must retain its existing prohibition on further tool calls.
4. Keep this a bounded internal prompting correction. Preserve public invocation
   behavior, input/output contracts, validation, routing, authorization, retry
   and execution limits, and trace semantics. Add no supported API, SPI,
   configuration key, sensitivity classifier, or general prompt-rewriting
   subsystem. Remove obsolete internal replacement behavior coherently without
   a compatibility shim.

## Acceptance criteria

- [ ] Substantive objectives reach assigned-step, step user-message, and
  final-response prompts without capability-name substitutions or loss of text
  after a generated-looking prefix. Legitimate references and substring matches
  remain intact.
- [ ] Ordinary `SkillTemplate` invocations produce clear step-context wording
  directly, without the current awkward `Execute skill the mission` result or
  reliance on recognizing an obsolete `Execute YAML skill` prefix.
- [ ] Assigned-task prompts retain the exact child task/tool/argument contract
  and prohibition on calling the parent; final-response prompts retain the
  prohibition on all tool calls. Mission input and prior-task evidence remain
  available and unchanged, without duplicate canonical-input blocks.
- [ ] Offline regression evidence confirms the prompt behavior and preserved
  execution safeguards. The supported Java surface remains unchanged, with a
  passing `LoomspanPublicSurfaceArchitectureTest` when production types change.
- [ ] The obsolete internal substitution path is removed without an alias,
  fallback, or shim. Journal redaction and unrelated prompting, attachment,
  observation, and Console behavior remain outside this change.

## Context and scope

`MissionInputMessageFormatter.sanitizeObjective` currently replaces the parent
capability's name with `the mission`. A special case recognizes objectives
starting with `Execute YAML skill '<name>' using the provided mission input
object.` and replaces the entire objective, including any suffix, with
`Use the provided mission inputs.` The general replacement also matches names
inside unrelated text.

Ordinary `SkillTemplate` invocation supplies structured business input;
`CapabilityExecutionRouter` generates an objective using `Execute skill`, not
`Execute YAML skill`. Thus the special case misses the current generated
wording, while generic replacement produces `Execute skill the mission ...`.
The structured business input itself is not rewritten by this formatter.

`StepPromptBuilder` uses the rewritten objective for assigned steps, step user
messages, and final synthesis. Planning's `buildUserMessage` path preserves the
objective. A test deliberately requires removal of the parent name, and the
step prompt explicitly prohibits calling the parent. These establish deliberate
behavior and suggest an attempt to prevent parent-tool confusion; they do not
establish improved model accuracy. Preserve that clear action boundary without
assuming name suppression is necessary or beneficial.

Reuse the existing prompt construction and input/evidence paths. This is not a
general skill-prompt redesign or a change to how developers invoke skills. The
known, supported `ExecutionJournalProjector` redaction exception is unrelated
and must remain intact. PR 14 addresses correction feedback and JSON examples;
PR 15 has no intended dependency on it, though both may touch `StepPromptBuilder`.
Preserve either ticket's changes when integrating the other. No paid model
calls are required; offline prompt evidence must not be described as proof of
improved live-model accuracy.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The intended behavior and direction are settled and bounded to
  internal objective construction and step-prompt formatting. Targeted
  investigation, offline verification, and independent review should provide
  sufficient assurance.
- **Reassessment triggers:** Upgrade to the Full 5-Step Pipeline if the current
  checkout reveals material unresolved design choices, supported-contract
  changes, changes to authorization or execution lifecycle, serialized or
  external protocols, or a need for broad shared prompting changes.

## Pipeline notes

- Removing capability-name suppression and whole-objective prefix replacement
  is intentional. Existing tests requiring those internal transformations must
  not be treated as a compatibility promise or a reason to preserve the old
  behavior. The parent-tool prohibition and assigned-action contract remain
  required.
