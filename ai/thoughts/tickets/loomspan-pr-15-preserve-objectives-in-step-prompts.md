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

- [x] Substantive objectives reach assigned-step, step user-message, and
  final-response prompts without capability-name substitutions or loss of text
  after a generated-looking prefix. Legitimate references and substring matches
  remain intact.
- [x] Ordinary `SkillTemplate` invocations produce clear step-context wording
  directly, without the current awkward `Execute skill the mission` result or
  reliance on recognizing an obsolete `Execute YAML skill` prefix.
- [x] Assigned-task prompts retain the exact child task/tool/argument contract
  and prohibition on calling the parent; final-response prompts retain the
  prohibition on all tool calls. Mission input and prior-task evidence remain
  available and unchanged, without duplicate canonical-input blocks.
- [x] Offline regression evidence confirms the prompt behavior and preserved
  execution safeguards. The supported Java surface remains unchanged, with a
  passing `LoomspanPublicSurfaceArchitectureTest` when production types change.
- [x] The obsolete internal substitution path is removed without an alias,
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

## Execution notes

- **Selected route:** User approved Fast-Track 2-Step Pipeline — Implementation
  & Review. Step 4 implemented the change on 2026-10-01; independent Step 5
  review remains required. Initial checkout was clean, and all current changes
  are ticket-scoped. No research or plan artifacts were created.
- **Internal correction and compatibility:** `CapabilityExecutionRouter` now
  constructs `Fulfill the mission for skill '<name>' using the provided mission
  input object.` directly. Its unused objective-helper argument was removed.
  `StepPromptBuilder` passes substantive objective text directly to assigned,
  user, and final prompts and labels overall assigned-step context as
  non-actionable. `MissionInputMessageFormatter.buildMissionContext` and its
  `sanitizeObjective` replacement implementation were removed together; all
  callers were updated, with no shim. These are internal implementation
  surfaces under the closed API allowlist. Supported API/SPI signatures,
  configuration/manifest contracts, Spring extension points, serialized
  schemas, Console protocols/compatibility markers, and Java-to-Go boundaries
  are unchanged. Existing routing, access checks, validation, limits, retry,
  attachment, observation, and trace machinery are unchanged; generated
  objective content reflects the intentional wording correction. PR 14's JSON
  examples and correction feedback remain intact.
- **Input and evidence ownership:** System prompts contain the objective and
  existing complete prior-task evidence. Canonical business input still uses
  the existing user-message/materialization path exactly once per request;
  it is not serialized into the generated objective or copied into a system
  canonical-input block. Exact assigned task/tool/argument guidance, the parent
  prohibition, and final synthesis's all-tool prohibition remain explicit.
- **Documentation assessment:** Focused skill-authoring impact concerns mission
  context versus assigned action and objective fidelity. Applied the
  same-checkout `agent-skills/loomspan-docs/SKILL.md` (matching the Maven
  `1.0.0-beta.8-SNAPSHOT` version); updated `planning-concurrency.md` with
  implementation/test anchors and the existing README prompt coverage row.
  Existing assignment/input/evidence guidance was aligned with executable
  behavior; objective-preservation coverage was incomplete (documentation
  drift), now addressed. No unresolved discrepancy or broader design decision
  was found. The bounded internal correction remains eligible for fast-track.
- **Acceptance evidence:**
  - `StepPromptBuilderTest#preservesCompleteObjectiveAcrossAssignedFinalAndUserPrompts`
    covers quoted and unquoted skill references, embedded substrings, suffixes
    after both old generated-looking prefixes, Unicode/newlines, and the new
    wording, with absent/empty/structured mission input. It asserts the exact
    child task/tool/argument contract, parent/all-tool prohibitions, unchanged
    structured values, and canonical-input block counts. Existing complete
    task-evidence regression remains passing.
  - `StepLoopMissionExecutionEngineTest#usesCanonicalMissionInputForPlanningAndStepUserMessages`
    captures actual assigned and final engine requests, retaining the complete
    generated-prefix objective and suffix with one canonical input block each.
  - `PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests`
    uses ordinary `SkillTemplate.invoke()` with a local HTTP model stub to
    assert generated mission wording in assigned/final system and user
    messages, canonical-input single delivery, preserved prior-task results,
    and action prohibitions. `CapabilityExecutionRouterTest` verifies generated
    wording separately from canonical business input.
  - `mvn -o "-Dtest=StepPromptBuilderTest,CapabilityExecutionRouterTest,StepLoopMissionExecutionEngineTest,StepActionValidatorTest,StepActionCorrectionTest,LoomspanPublicSurfaceArchitectureTest" test *> target/pr15-focused-tests.log`
    passed: 133 tests, zero failures/errors/skips. Includes architecture,
    exact-assignment validation/correction, execution limits, and loop safeguards.
  - `mvn -o "-Dtest=PlannerEvidenceFlowIntegrationTest,DefaultSkillTemplateTest,PlanningServiceTest,DefaultAccessGuardTest,ExecutionCoordinatorTest,ExecutionJournalProjectorTest,DefaultMissionInputMaterializerTest" test *> target/pr15-integration-safeguards.log`
    passed: 110 tests, zero failures/errors/skips. Includes invocation,
    authorization, planning/routing, input/attachment handling, and the existing
    journal-projection exception.
  - `git diff --check` passed. Full `mvn -o test` was not run; the focused offline
    suites establish the bounded acceptance criteria. No paid/live model calls
    were made, and offline fidelity evidence is not a claim of improved model
    accuracy. No optional developer observation is required.
