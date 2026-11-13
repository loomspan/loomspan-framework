# PR 30.3.2 — Migrate planning and step execution contracts

## Outcome

Apply the standard/native schema machinery to planning and the agreed step execution boundaries. Remove superseded planning parser fallbacks/aliases. Preserve semantic plan checks and execution ownership. Include step-action decimal precision and explicit-null toolArguments correction.

## Scope and sequencing

**Dependencies:** PR 30.3.1, PR 30.2.3, PR 30.2.4 and PR 30.2.5.

**Expected build state:** Planning and step execution should compile and pass focused checks. Remaining TypeSafe/catalog consumers may still prevent a clean full build.

PRs 30.1.2–30.1.5 restore the provider transition; PRs 30.2.2–30.3.4 restore schema consumers by their owned boundaries; PR 30.4.3 reruns all blocked integrated checks.

Own planner and step consumers of the existing shared native-schema path. Preserve existing final-phase tool rejection, action validation, planning correction and step-loop ownership. Do not revive the withdrawn broad StepAction/native-schema finding or invent an additional action response protocol. Ordinary advisor repair is PR 30.3.3.

Execute in the agreed numerical order unless working on an independent prerequisite. Dependencies describe technical prerequisites, not permission to duplicate another ticket's implementation.

## Shared migration constraints

Exactly three chat integrations remain: shared OpenAI Responses for OpenAI, Meta,
OpenRouter and Ollama; native Anthropic; native Gemini. TypeSafe remains a separate
native evaluator. Retain Spring AI ChatClient/tool-loop ownership. No second adapter,
transport, agent loop, capability registry, conversation database, protocol selector,
automatic fallback or invented public API is authorized.

Canonical inputs, prompts, outputs, tool arguments, traces and diagnostics retain
content fidelity. Do not classify sensitivity, scan secrets, mask or sanitize content.
Enforce authentication, authorization and explicit access boundaries. Existing size
limits and explicit truncation are resource controls. Preserve only the existing
ExecutionJournalProjector field-name redaction exception for the derived journal and
SkillTemplate observer; do not broaden it to canonical traces, Console or invoke results.

The supported Java API is the closed allowlist of public top-level ai.loomspan.api
types in LoomspanPublicSurfaceArchitectureTest. Internal public types are implementation
details; autoconfigure signatures are not an SPI. User-visible keys/behavior require
deliberate migration. Do not expose internal/autoconfigure types in public signatures.
Run LoomspanPublicSurfaceArchitectureTest after production type changes; record it as
blocked if intentional intermediate breakage prevents execution and rerun on restoration.

The accepted standard profile is Draft 2020-12 with NetworkNT Jackson 3 (3.0.9 is the
verified starting candidate, not an irrevocable final pin). Complete contracts have
object roots; property trees support ordinary types, explicit nullable type arrays,
scalar enums/descriptions, homogeneous nested arrays, boolean openness, standard formats
and minimum/maximum/minLength/maxLength/minItems/maxItems. Ordinary inputs additionally
support typed maps and unconstrained {} leaves. Native output/planning typed maps,
general unions/composition, references, defaults, arbitrary patterns and other unapproved
features remain excluded. Internally generated false property schemas preserve binding
ownership. Exact names, standard omission/null/openness and no coercion apply. No
automatic all-required conversion, null insertion, closure, wrapping or map reshaping.
Native strict tools are deferred; retain non-strict schemas, local pre-dispatch validation
and authorization. Retain standard local checks even when a provider ignores assertions.

Deferred Java declaration exploration (Optional wrappers, polymorphic inputs,
array-shaped DTOs and delegating creators) is excluded. Do not restore removed probes
or invent their restrictions. The retained map-key, JsonUnwrapped and UUID corrections
remain owned by PR 30.2.2. Native generated attachments are excluded.

No live provider calls, model downloads, external issue/PR submissions or test-suite
implementation are authorized by this ticket. All verification here is deterministic
or local build/startup. The neighboring suite owns later deployed/live acceptance;
missing live evidence is not permission to weaken Framework requirements.

## Acceptance criteria

- [ ] Planning uses its own framework-owned native contract and JSON-only parser while all existing capability/dependency/task/evidence/binding semantic checks remain effective.
- [ ] Step toolArguments and finalResponse decimals remain exact; explicit null argument roots fail before dispatch and use existing bounded correction, while omitted and nested-null controls retain their semantics.
- [ ] Initial, correction, continuation and override requests retain the intended schema/options/caps and isolation; required work and final-phase tool prohibition remain enforced.
- [ ] Every applicable mandatory case in the detailed contract below has executable evidence, or an explicitly identified downstream integration owner and blocked-check record consistent with this ticket's build allowance; no in-scope required behavior is silently deferred.
- [ ] Owned fixtures and callers migrate with the behavior; preservation controls and the public-surface check are run when buildable, and all failures/blocked checks are reported accurately.

**Verification emphasis:** Planner schema versus final-output schema, initial/correction/override captures, semantic rejection, exact action values, null-argument rejection before effects, final-phase tool prohibition, bindings, evidence and required-work regressions.

## Planning contract and preservation boundary

Reuse DefaultPlanningService, ExecutionPlan and PlanTask. Existing initial content
contains capabilityName, createdAt, status VALID and tasks containing taskId, title,
status PENDING, capabilityName, intent, dependsOn, expectedOutputs, parallelGroup and
note. Runtime generates planId. Derive the native contract from this existing behavior;
do not expose all later execution statuses just because they exist in a Java enum.
Requiredness must reflect existing parser/constructor semantics. Any deliberate
all-required/nullable choice for this framework-owned contract is explicit, not an
automatic rewrite of user schemas. Do not import the historical pattern proposal into
v1 or create new product policy from an untraced parser assumption.

Keep PlanStructureValidator, PlanTaskConstraintValidator, PlanInputBindingDependencyValidator
and PlanOutputBindingProducerValidator authority: unique task IDs, allowed capabilities,
dependency ordering/cycles, contiguous parallel units, task counts, binding producers
and evidence coverage. Structured output is not a useful/valid plan guarantee. Retain
planning control-tool authorization and failure handling. Planning descriptors remain
separate from execution attachment media. Separate child interactions and model overrides
must not inherit mutable continuation state accidentally.

## Deterministic option fixtures for later acceptance

Use the agreed selected routes as offline fixture identities: direct gpt-6.1-terra,
OpenRouter openai/gpt-6.1-terra restricted to standard endpoint tag openai with fallback
disabled and require_parameters enabled, Meta muse-spark-1.3-contributor (all explicit
medium); Ollama qwen3.5:4b-q4_K_M with enabled boolean thinking, not a fabricated medium;
Anthropic claude-sonnet-5-5 native output effort medium; Gemini gemini-3.8-flash native
thinking level MEDIUM. These are accepted fixtures, not fresh availability claims.
The inspected Ollama baseline was 0.40.16 with digest
2a654d98e6fba55d452b7043684e9b57a947e393bbffa62485a7aac05ee4eefd.

Capture unchanged total generation caps (reasoning plus visible output): 1024 for a
small structured result, 4096 for tool-chain/planning requests and 2048 for attachment
requests. Cover initial, continuation, planner and model-override option combination
as those consumers become available. Phase 1 proves adapter/factory behavior; PRs
30.3.1/30.3.2 prove migrated consumer wiring; PR 30.4.3 reconciles the full matrix.
Preserve legacy-model behavior. Unsupported options fail without paid sends, implicit
fallback, increased limits or invented settings. Routing support must use existing
extension facilities, not endpoint/model guessing or another provider abstraction.

The later suite uses a 180-second physical request boundary, 210-second proxy boundary,
900-second mission boundary and 960-second runner deadline. Preserve configurable
timeout semantics; do not mistake socket inactivity for a total elapsed deadline.
Suite overlays, reservation ledgers and runner changes belong to the suite, not here.
Its total ceiling is USD 5, 81 physical requests (69 cloud), zero automatic transport
retries/schema repairs in the initial live batch, and separate paid-run authorization.
These are not new production defaults. No sends are authorized here.

## Detailed accepted contract and evidence

The following bounded extracts carry the applicable decisions, exact acceptance cases
and historical code/test anchors into this ticket. Scope ownership above determines
which cross-boundary statements are implementation here versus preservation context.
Statements about earlier research runs remain historical; do not claim they were rerun.
References to future tickets in retained text mean requirements carried by this ticket
set, not permission to postpone this ticket's owned work. Older proposals do not override
accepted decisions or the explicit exclusions above.

### Contract 1

**Step-action decimal precision — agreed audit correction:** decode model-supplied
step-action decimals losslessly before validation, tool execution or final-response
serialization. Cover both toolArguments values and finalResponse trees. Keep the
correction local to this decoding boundary, using the existing arbitrary-precision
approach rather than changing unrelated shared mapper behavior. Preserve action
validation, authorization, correction budgets and the agreed parser migration.

Add focused step-engine tests with long fractional/exponent-form values, including
nested tool arguments and final-response fields. Assert exact values delivered to
callbacks and returned after final serialization, using precision-preserving assertions.
Include migrated numeric-boundary checks that reject the original out-of-range value
before callbacks or final acceptance; rounding must not make it valid. Retain bound
child-result precision and existing correction regressions. A local probe invoking
the actual action parser and final serializer changed 0.12345678901234567890123456789
to 0.12345678901234568 in both action kinds. This reproduces local precision loss;
full-engine acceptance and live behavior remain unverified. Implementation and focused
tests remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1223`
  (`parseStepAction`) reads StepAction using the schema mapper; the tree-reading branch
  at line 1231 uses that mapper too. Production wiring supplies codecs.schemaTree()
  at `src/main/java/ai/loomspan/autoconfigure/LoomspanAutoConfiguration.java:442`;
  `src/main/java/ai/loomspan/internal/serialization/LoomspanJacksonCodecs.java:39`
  builds it without lossless decimal decoding.
- `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:171`
  validates already-decoded arguments. The engine passes those arguments onward at
  line 1179 and serializes final-response trees for validation/return at lines 991
  and 1051 (`serializeFinalResponse` at line 1474).
- `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:480`
  (`assembledOutputPoliciesValidateExactBoundDecimals`) covers decimals originating
  in bound child results, not model action JSON. Its model-owned contribution is a
  string. The test at line 1359 (`retriesFinalResponseUntilOutputSchemaPasses`) covers
  correction with string-valued results, not numeric fidelity. These are inspected
  tests, not newly run acceptance results.

### Contract 2

**Step-action null toolArguments — agreed audit correction:** reject explicitly null
toolArguments in a CALL_TOOL action before it becomes an empty map. Preserve valid {},
permitted null fields inside argument objects, existing omission behavior and bounded
step-action correction. Detect explicit null at decoding while its distinction from
omission is still available; do not globally reject internal null maps or change public
invocation behavior. Ordinary callback ingress fixes do not cover this separate path.

Add a focused step-engine test proving a CALL_TOOL with toolArguments:null invokes no
tool, enters existing bounded correction, and executes once after a valid {} correction
for a tool accepting empty arguments. Cover correction exhaustion with no side effects;
retain nested-null and omitted-argument behavior controls. A local decoding/validation
probe accepted both explicit null and {} against a closed empty-object contract.
Dispatch normalization was source-inspected, not exercised in a full engine reproduction.
Implementation, focused engine verification and live acceptance remain pending.

Evidence anchors:

- `src/main/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngine.java:1223`
  (`parseStepAction`) binds JSON to StepAction, collapsing explicit null and omission;
  `src/main/java/ai/loomspan/internal/runtime/step/StepActionValidator.java:161`
  (`validateRequiredToolArguments`) substitutes Map.of() before ownership checks and
  does so again at line 170 for schema validation. The engine substitutes Map.of()
  at line 1179 before invocation.
- `src/test/java/ai/loomspan/internal/runtime/step/StepLoopMissionExecutionEngineTest.java:3382`
  (`preservesExplicitNullArgumentsInTheStepLoopToolPath`) supplies {"rawText":null},
  a valid object, rather than a null argument root. Preserve this nested-null behavior;
  it does not establish enforcement of the object-root requirement.

### Contract 3

#### TBD-09: Unsupported schemas/models and failure timing

**Status: Resolved — failure policy agreed.** Use the following boundaries without
introducing capability discovery, implicit fallback, or a competing retry system.

| Failure | Required timing and behavior |
| --- | --- |
| Invalid declaration or feature outside the approved v1 profile | Reject during skill loading or Java declaration registration, with skill/declaration identity, schema location and reason. Include unsupported roots, keywords and format names. |
| Known incompatibility with the selected driver | Reject before sending the affected model request using explicit evidenced driver rules. Do not infer capabilities from URLs/model names. If the driver is selected only at execution time, do not pretend this was verifiable at registration. |
| Provider rejects the schema or does not support requested native structured output | Fail the call clearly. Do not retry the unchanged rejected schema, experiment with keyword removal, switch provider/protocol, or downgrade to JSON-only/prompt-only output. |
| Generated output violates the canonical contract | Use the bounded output-correction path; fail if correction is exhausted. Never accept invalid output. Correction history and side-effect handling remain TBD-11. |
| Transient transport/provider failure | Retain the existing transport-retry policy, separate from schema correction. Do not classify deterministic schema incompatibility as transient. |
| Validation cannot complete | Report an operational failure, not an invalid-instance verdict or partially accepted result. |

Loading/registration performs local checks only: do not make live model calls to
probe compatibility, add automatic startup capability discovery, or introduce a
runtime capability registry. Document tested provider/model/route combinations and
the scope of that evidence. Untested combinations may be attempted; neither local
schema validity nor endpoint accessibility promises native structured-output support.
A provider accepting a request alone is not proof it enforces the schema.

Use existing error/outcome mechanisms. Include skill/declaration identity, driver
and model when available, relevant schema/instance location, and original provider
error details when available. Preserve diagnostic content fidelity. Distinguish
invalid declarations, known/provider-reported incompatibility, invalid generated
output, and operational failures. Do not invent a new public exception hierarchy,
error API, or configuration surface without demonstrating a concrete need and
bringing it back for discussion. Unrecognized provider errors retain their original
details; do not guess a capability diagnosis from incidental message text.

"Before sending" means before the affected request, not before every previous step
in a multi-step execution. Do not promise rollback of already executed tools or add
compensating actions. Existing authentication/authorization failures remain separate;
this policy does not authorize retries or fallback for them.

#### Mandatory ticket handoff and acceptance

Tickets must reproduce the failure table, local-only registration rule, diagnostic
requirements, and no-fallback/no-rollback boundaries. Each affected path must identify
its existing error/outcome and retry owner. Do not leave the implementing LLM to
choose failure timing, invent error categories/public types, or treat every provider
400/error as either retryable or a schema incompatibility. Evidence-backed error
classification and any unresolved provider behavior must be explicit in the ticket.

Required acceptance cases: invalid declarations fail without network calls; known
driver incompatibilities send no affected request; explicit schema/support rejection
is surfaced once without correction/transport retries or fallback; transient failures
retain the existing retry policy; canonical-invalid generated output follows bounded
correction and fails on exhaustion; incomplete validation is never accepted. Verify
useful original diagnostics and that earlier successful tool actions are not falsely
reported as rolled back. Keep unsupported-route evidence distinct from approved
keyword mappings under TBD-07. TBD-11/12 continue to own correction history and
refusal/incomplete-response behavior respectively.

## Retained technical context

Historical research establishes the stated adapter/library behavior and limitations.
Broader historical fixtures do not expand the accepted v1 profile.

| Surface | Repository evidence | Findings and migration relevance |
| --- | --- | --- |
| Tool exposure/execution | [BoundCapability](../../../src/main/java/ai/loomspan/internal/runtime/tool/BoundCapability.java), [SpringAiToolCallbackAdapter](../../../src/main/java/ai/loomspan/internal/springai/SpringAiToolCallbackAdapter.java), [ExecutionCoordinator](../../../src/main/java/ai/loomspan/internal/core/ExecutionCoordinator.java) | Schema strings cross capability metadata into provider tools; local validation and canonical authorization remain essential. The same child contract is also described to planning/step execution. |
| Planner control tool | [PlannerInabilityTool](../../../src/main/java/ai/loomspan/internal/springai/PlannerInabilityTool.java) | Handwritten JSON tool schema plus dedicated argument/control handling. Include in serializer fixtures; it is not a skill output schema. |
| Planning generation | [DefaultPlanningService](../../../src/main/java/ai/loomspan/internal/runtime/planning/DefaultPlanningService.java), [ExecutionPlan](../../../src/main/java/ai/loomspan/internal/core/ExecutionPlan.java), [PlanTask](../../../src/main/java/ai/loomspan/internal/core/PlanTask.java) | Currently a prompt example, JSON/YAML parser, fence unwrapping, status aliases, and tree-to-record conversion. Runtime generates `planId`. Initial contract requests root `capabilityName`, `createdAt`, `status: VALID`, and tasks with `taskId`, `title`, `status: PENDING`, `capabilityName`, `intent`, `dependsOn`, `expectedOutputs`, `parallelGroup`, `note`. |
| Planning semantics | [PlanStructureValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanStructureValidator.java), [PlanTaskConstraintValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanTaskConstraintValidator.java), [PlanInputBindingDependencyValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanInputBindingDependencyValidator.java), [PlanOutputBindingProducerValidator](../../../src/main/java/ai/loomspan/internal/runtime/planning/PlanOutputBindingProducerValidator.java) | Unique task IDs, allowed capabilities, dependency ordering/cycles, contiguous parallel units, task counts, binding producers/dependencies, and evidence coverage are not replaced by structural validity. Initial plan schema must not permit every later execution status simply because the record enum does. |

## Pipeline notes

- Destructive updates are encouraged: this repository is in development with no
  deployments to support. Remove superseded implementations in place; do not add
  legacy readers, aliases, dormant paths, compatibility shims or fallback solely to
  keep intermediate commits building. Respect the supported public API boundary above.
- The developer commits between tickets without syncing. The stated intermediate
  build failures are authorized; identify each affected consumer/test and restoration
  owner. Do not mark blocked tests passed, remove assertions or skip required checks
  into success. Final integrated acceptance requires green checks.
- Preserve unrelated worktree changes and retained probes. Resource stream closure
  in SkillMethodBeanPostProcessor is already repaired, with success/failure/nested
  tests; preserve it rather than treating it as missing migration implementation.
- Source anchors and retained reports below are historical navigation/evidence, not
  fresh passing-test claims. Verify hints against the current checkout. Before claiming
  a new defect, trace callers, validation, execution and tests against the accepted
  requirement. Routine implementation choices are autonomous; broader product or
  architecture departures require a concrete evidence-backed question.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This ticket changes or provides outstanding assurance for a cross-cutting migration involving serialized contracts, external protocols or execution lifecycle behavior. Settled intent does not eliminate integration research, test planning or independent review.
- **Reassessment triggers:** A materially changed scope or checkout should be reassessed under the shared protocol; this recommendation does not waive any required review or verification.

## Reference navigation

- [Agreed overview](../phases/migration-ticket-overview.md)
- [Coverage ledger](../phases/migration-ticket-coverage.md)
- [Migration roadmap](../phases/spring-ai-responses-migration.md)
- [Historical schema research](../phases/standard-json-schema-research.md)
- [TBD-06 reproduction and retained evidence](../../../docs/maintainers/probes/tbd-06/README.md)
