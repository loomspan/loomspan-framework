---
date: 2026-10-03T22:52:10-07:00
researcher: Codex
git_commit: b32c5d50ad1eff336d4c14bc9a4ca9cd521f48d0
branch: main
repository: loomspan-framework
topic: "PR 18: object boundaries in generated input guidance"
tags: [research, codebase, input-contracts, model-guidance, planning]
status: complete
last_updated: 2026-10-03
last_updated_by: Codex
---

# Research: PR 18 generated input guidance

**Date:** 2026-10-03T22:52:10-07:00  
**Researcher:** Codex (model identity unavailable; Unknown)  
**Git Commit:** b32c5d50ad1eff336d4c14bc9a4ca9cd521f48d0  
**Branch:** main  
**Repository:** loomspan-framework

## Research question and authorized scope

Implement `ai/thoughts/tickets/loomspan-pr-18-scope-generated-input-guidance.md`
using `ai/commands/0_run_pipeline.md`. This artifact documents current behavior
for pipeline Step 1, selected profile `full`. The developer approved the
recommended **Full 5-Step Pipeline** with “proceed” after Step 0. That approval
includes the ticket's requested controlled GLM comparison attempt; it does not
authorize changing experimental skills, accepted replays, business checks,
providers, supported APIs, or automatic data forwarding.

Checkout attribution at research start: no tracked changes; only the supplied
ticket was untracked. The ticket is user-provided work to preserve. This step
adds only this research artifact and does not change implementation or source
evidence. Sibling-suite captures are diagnostic source inputs and remain
immutable. No tests, deployments, or provider calls were executed in this step.

## Summary

`StepPromptBuilder` takes the called capability's resolved input contract,
selects compact or verbose presentation, and delegates to
`SkillInputPromptRenderer`. Compact guidance currently consists of a recursive
illustrative shape; verbose guidance adds required/type rules, stops recursive
rules beyond depth three, and closes the root with the exact sentence “Do not
add fields not shown above.” Neither mode explicitly communicates unrestricted
open-object permission, and both show optional and required declared properties
in the same shape.

The same contract feeds deterministic input validation independently of the
renderer. Existing source already distinguishes closed, unrestricted open, and
schema-constrained additional properties; a prompt change can operate on those
resolved nodes without changing interpretation or execution boundaries. The
recorded GLM evidence and experimental YAML files exist locally, including full
Framework traces and provider journal captures. Live access has not been tested
here; the existing suite live runner requires a named credential and restores
provider-disabled services after deployment experiments.

## Investigation checklist

- [x] Read ticket and pipeline/shared protocols before broader discovery.
- [x] Trace resolved-contract rendering and ordinary/corrective assignment flow.
- [x] Inventory validator, schema defaults, references, focused tests and API classification.
- [x] Locate retained experiment inputs, actual captures, and live-run prerequisites.
- [x] Consult version-aligned `loomspan-docs` routing and input-contract topic.
- [x] Run `bash ai/scripts/spec_metadata.sh` before creating this artifact.

## Detailed findings

### Contract resolution and validation

- `SkillInputContractResolver#resolveYamlCapability` uses an explicit manifest
  contract when present and generic-object fallback otherwise (lines 37–50).
  `resolveFromToolSchema` recognizes empty open top-level schemas as generic
  (lines 53–66). Java/YAML explicit contracts retain their own kind.
- `fromJsonNode` records boolean and schema-valued `additionalProperties`
  separately (lines 155–173); a missing type becomes `ANY_TYPE`, not `object`.
  `fromManifest` carries YAML boolean additional-property metadata and items
  (lines 84–108). This YAML manifest supports boolean additional properties,
  while reflected/tool JSON contracts can carry a schema constraint.
- `SkillInputSchemaNode#allowsAdditionalProperties` (lines 86–89) returns true
  unless explicitly false, or when an additional-properties schema exists.
  Thus omitted keywords are open by current interpretation. Nodes are immutable
  records with copied properties/required lists, preventing mutation by rendering.
- `SkillInputValidator#validateObject` (lines 127–170) checks object required keys,
  rejects unknown closed-object keys, validates unknown keys against the additional
  schema when present, and retains unrestricted unknown values unchanged. Declared
  fields follow their own child schema. Arrays validate each item when an item
  schema exists; otherwise values remain unchanged (lines 176–196).
- Generic contracts skip structured validation and return an immutable top-level
  copy (`validate`, lines 20–36). Existing typed coercion/date behavior, attachment
  handling, and runtime-reference support are separate validator paths.

### Renderer and presentation boundaries

- `SkillInputPromptRenderer#renderToolArgumentsExample` (lines 15–41) suppresses
  generic guidance. A closed empty object without an additional schema gets an
  explicit no-arguments instruction. All other explicit contracts get a shape.
- `renderValue` (lines 42–97) recursively prints all sorted declared properties,
  regardless of requiredness. A schema-constrained map adds a `<key>` example.
  An unrestricted open object with no declared fields prints an empty object
  across two lines. Values are placeholders; this is an argument-shape notation,
  not a complete valid JSON input. This distinction already exists in tests.
- `appendVerboseRules` (lines 99–180) emits required paths, closed-object rules,
  property types/enums and typed-map values. Its rule walk stops after depth 3.
  The shape walk has no matching depth cutoff. `nestedSchema` unwraps one array
  layer; consecutive array layers do not receive a fully recursive semantic walk.
- Root closure has no top-level qualifier (line 122). Nested closure names a path
  but refers back to “those shown above” (line 126). Open objects receive no
  positive additional-property permission. Descriptions are stored in resolved
  nodes but not rendered here; the ticket treats description rendering as optional.
- `StepPromptBuilder#formatToolArgumentGuidance` (lines 212–245) looks up the exact
  assigned tool by name and reads `tool.metadata().inputContract()`, rather than
  reparsing its published tool schema. `useVerboseDetail` (lines 248–251) chooses
  verbose when computed depth exceeds 2 or property count exceeds 6; retries may
  force verbose. `countProperties` does not descend through array nodes (lines
  280–289); `maxDepth` does traverse arrays and additional-property schemas.

### Ordinary requests, corrective requests, and evidence

- `StepLoopMissionExecutionEngine` lines 790–825 initializes compact selection,
  builds the assigned/final prompt, composes authored skill instructions via
  `SkillPromptComposer`, builds the user message, and calls the model interaction.
  Corrective requests rebuild the same prompt and append correction instructions
  plus the rejected candidate/validation feedback. Validation rejection sets
  `forceVerboseToolArgumentGuidance=true` (line 895); parse rejection alone leaves
  that flag unchanged (lines 828–848).
- `StepActionValidator#validateAssigned` checks exact task, admission status and
  exact visible capability before arguments. `validateRequiredToolArguments`
  (lines 148–194) reads the same resolved contract and invokes the input validator
  before tool execution. Existing placeholder rejection follows normalization.
- `StepPromptBuilder#appendCompletedTaskEvidence` (lines 185–195) serializes
  complete prior task results in the system prompt. `buildStepUserMessage`
  (lines 170–183) delegates complete canonical mission input to the formatter.
  The assigned action skeleton explicitly says its empty `toolArguments` object
  is illustrative (lines 107–109). These mechanisms do not automatically populate
  child tool arguments; the model chooses the actual data within contract bounds.
- `ModelInteractionRequest` carries the composed system prompt and rendered input;
  `StepLoopMissionExecutionEngineTest` already intercepts `request.systemPrompt()`
  at lines 1660, 1775, 2860 and 3017. These are executable capture seams for actual
  Framework requests; a renderer-only example would not establish the full request.

### Existing executable tests

`StepPromptBuilderTest` protects exact objective and mission-input fidelity,
complete prior-result serialization, illustrative action skeletons, resolved
contract precedence over generic publication, nested verbose rules, omitted-keyword
openness, typed maps, omitted array items, and unconstrained values. Relevant test
names begin at lines 38, 71, 167, 251, 284, 323, 357, 382, 421 and 450.
Several tests assert current wording and will be affected by rendering edits.
No direct renderer test file exists at this revision.

`SkillInputContractResolverTest` protects any-value round trips and generic versus
strict empty-object identity (lines 38 and 80). `SkillInputValidatorTest` covers
normalization/unknown keys, generic permissiveness, null fidelity, reference-backed
strings, typed additional schemas, arrays without item contracts, attachments,
unconstrained JSON values, and unsupported values (lines 20, 85, 93, 118, 150,
182, 240, 261, 309 and 338). `StepActionValidatorTest` and engine tests exercise
pre-side-effect validation and correction integration. These establish current
semantics; they are not evidence of live-model accuracy.

### Retained GLM reproduction evidence

All paths in this subsection are in `C:/opendev/code/loomspan-sidecar-test-suite/`.

- `evidence/glm-skill-contract-experiment-20261003/` contains immutable copies of
  `planResolution.yaml`, `compareOptions.yaml`, `resolveEquipment.yaml`, the proposal
  patch, proposal/restored-default hashes, `comparison.json`, and the finding file.
- `prompt-guidance-finding.txt` records actual generated shape/rules for baseline
  trace sequence 190 (`resolveEquipment#step-6-model`, planResolution handoff) and
  sequence 480 (`planResolution#step-5-model`, compareOptions handoff). Root is
  closed; context and its declared evidence objects are open. Five nested required
  fields match the ticket. The finding expressly disclaims established causation.
- The retained experimental `compareOptions.yaml` says `model: reasoning` and
  `thinking_level: medium`. Its authored instructions require complete evidence,
  authoritative quotes, exact assessment copying and preserved identities. These
  instructions are business ownership, separate from extra-field permission.
- `evidence/evaluate-suite-20261003-180815-8cb795/report.json` identifies GLM
  `z-ai/glm-5.3-flash`, medium reasoning, Java path, failure status, and paired baseline
  capture `evidence/live-20261003-180847-e5cb8d`. `comparison.json` identifies the
  priority capture `evidence/live-20261003-182133-1aa6aa`. Earlier original-contract
  run `evidence/evaluate-suite-20261003-173726-174de4/report.json` is a distinct
  experiment and cannot be a controlled baseline for a guidance-only comparison.
- Baseline capture includes `journal.json`, checksums, input/result files,
  manifest/configuration snapshots, trace index, and actual Java NDJSON trace
  `java/traces/loomspan-trace-0ab800c6-ad86-485f-92a5-6c7b59b1adea.ndjson`.
  These permit reconstruction from actual requests without modifying old captures.
- `scripts/run_suite.py#live_runtime` (lines 68–91) requires
  `LOOMSPAN_OPENROUTER_API_KEY`, rejects an already-active overlay, creates private
  config/skill copies, preserves runtime evidence, and uses `finally` restoration
  followed by `require_offline_provider`. Its ordinary suite run varies mission
  execution and outputs, so existence of this runner alone does not establish a
  controlled identical-input handoff experiment. Credential presence and provider
  availability were not queried during read-only research.

## Contract and compatibility inventory

| Category | Current evidence and exposure |
| --- | --- |
| Application API | Supported allowlisted `ai.loomspan.api` types are separate from the renderer/step builder. No ticket-scoped signature change is identified. |
| Supported SPI | `RestSkillHandler` remains the sole supported Java SPI; no renderer replacement contract exists. |
| Configuration and manifest contracts | Boolean YAML defaults and reflected/tool schemas determine the rules; these semantics remain the ticket's protected inputs. No new schema feature or config key is requested. |
| Persisted or serialized contracts | Accepted suite replay fixtures and diagnostic source captures exist; ticket explicitly excludes changing them. No durable new contract is implied by prompt wording. |
| Ephemeral diagnostic formats | Actual model prompts and requests are diagnostic content; retain fidelity and failure visibility. No Console REST/SSE/acquisition/NDJSON marker change follows from wording-only work. |
| Internal or accidentally exposed implementation | Renderer, contract/schema nodes, resolver, validator, model-interaction request and bound capabilities are public for internal package collaboration. Builder/validator are package-private. Architecture test line 201 explicitly classifies the renderer as internal collaboration. |

Public declarations, constructors and interfaces in these internal packages are
technical exposure only. Renderer is instantiated privately in `StepPromptBuilder`,
not a supported replaceable Spring bean. No affected Spring conditional bean or
supported extension contract was found. In-repository consumers of the renderer
are the step builder; tests consume generated prompts. `LoomspanPublicSurfaceArchitectureTest`
is the executable API classification authority and is required after production
changes. Existing internal types do not need consumer compatibility shims.

## Documentation alignment and authoring impact

Applied the repository skill `agent-skills/loomspan-docs/SKILL.md` after executable
inventory, then read the skill-authoring README routing, `input-contracts.md`, and
`source-verification.md`. Skill metadata and `pom.xml` agree on
`1.0.0-beta.8-SNAPSHOT`; docs and code are from this same checkout.

**Drift classification: aligned** for the documented reflected input semantics:
generic maps have open keys, typed maps constrain every entry, DTOs are closed,
unconstrained leaves render as any JSON value, and descriptions do not create
enforced domain schemas. The topic currently documents examples/verbose rules but
does not describe affirmative path-scoped permissions or optional-field examples.
Complete pure-YAML schema syntax is explicitly outside this topic's coverage.
The ticket supplies the authorized prompt-clarification requirement; the observed
global-sounding sentence is not a separately discovered schema-semantic discrepancy.

Authoring impact exists in what a skill author can expect the model to be told
about an unchanged input contract. The input-contract topic is the relevant routed
document; documentation can describe implemented prompt guidance while distinguishing
schema enforcement from illustration, and openness from business field selection.
The README coverage row is currently initial/source-verified and includes planner
guidance. Source and focused tests must ground any exact updated claim.

## Architecture and historical context

The framework keeps one resolved contract as the source for publication,
validation and rendering. Runtime validation, visible-tool authorization and exact
coordinator assignment remain independent of presentation. Open objects permit
extra data; they do not infer business requirements, transport upstream results
automatically, or authorize commitments. The ticket frames weaker-model outcomes
as diagnostic evidence for generic accuracy work, with no model-specific behavior.

No earlier research/plan artifact for this renderer was found in the currently
empty research/plan directories. The ticket and sibling captures provide historical
context. `ai/thoughts/framework-feature-design-lens.md` supplies the classification
above and preserves prompt/data fidelity. The existing journal-projector exception
is unrelated to this rendering path and remains outside scope.

## Open questions for planning and verification

1. Exact rendering layout remains a ticket-authorized implementation choice:
   how required/optional fields and object-scoped permissions appear in both modes,
   and how nested array/additional-schema traversal retains boundary information
   across size/depth thresholds. The resolved schema already contains the needed
   contract facts; no additional schema interpretation is needed.
2. Which actual retained handoff request(s) will supply the fixed full authored
   prompt, evidence and settings for repeated old/revised GLM calls? Planning must
   distinguish this isolated argument-generation diagnostic from re-running an
   entire changing mission and retain actual request/response pairs and failures.
3. Whether live-provider credentials/access are available remains unverified.
   The ticket explicitly permits an external-prerequisite failure to be reported
   as unverified; deterministic checks and actual Framework-request capture remain
   separate required evidence.

These questions are within authorized planning judgment; no developer decision is
needed to finish this descriptive research step.
