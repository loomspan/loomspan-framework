# PR 24 — Dispatch fully bound planned tasks without a redundant model call

## Outcome

When an accepted plan assigns a ready task to a specific capability and its input contract leaves no possible model contribution, Framework should execute the assigned capability without asking the parent model to emit the already determined CALL_TOOL action.

Infer eligibility from existing input contracts and bindings. Do not require a new per-skill execution mode, flag, duplicate schema, or author-maintained declaration of eligibility. The author-facing rule is: bindings supply arguments; if the contract leaves nothing for the model to supply, Framework constructs the assigned call itself. Merely binding all required fields is insufficient when optional model input remains possible.

The model still generates the plan. A model-backed child still performs its own planning or reasoning. This optimization removes only the parent's dispatch-only model interaction; it does not replace business reasoning or change what an admitted task means.

PR 24 is the user-selected proposed PR identifier, not a claim that a GitHub PR already exists.

## Background and motivation

The Java reference equipment-service workflow now uses PR21 input bindings and PR22/23 output assembly. Its accepted plans already identify the assigned capability and dependencies. At the assigned-step boundary, current validation requires CALL_TOOL for exactly the assigned task/capability; it does not offer the worker an independent choice to skip the task, select another tool, or return the final mission result. When all possible arguments are Framework-owned, another provider interaction has no remaining legitimate decision to make.

Offline measurements from four retained PR22/23 Java/medium scenarios found eight deterministic dispatch actions per scenario with empty model arguments and closed effective contracts allowing no model-supplied values:

| Scenario | Dispatch calls / all calls | Dispatch input / output tokens | Reported dispatch cost | Share of scenario cost | Overlap-adjusted request footprint |
| --- | --- | --- | --- | --- | --- |
| MiMo baseline | 8 / 15 | 44,243 / 585 | $0.01974626 | 35.8% | 28.8s |
| MiMo priority | 8 / 16 | 45,164 / 532 | $0.0197886156 | 31.6% | 28.1s |
| Luna baseline | 8 / 16 | 36,390 / 410 | $0.00472965 | 27.1% | 9.2s |
| Luna priority | 8 / 15 | 36,754 / 413 | $0.004776575 | 32.0% | 10.1s |

Repeated input context dominates these costs. The group consumes 34–39% of scenario tokens. The eight capabilities are assetContext, serviceHistory, referenceEvidence, serviceTerms, entitlements, serviceResources, continuityOptions, and quoteOptions. These names are examples, never an implementation allowlist.

A broader group of 9–10 observed empty actions costs 37–48%, but includes optional-input dispatch into model-backed skills. Observing an empty action in a past run is not proof of eligibility. The known Luna malformed optional-reasoning action and MiMo unsupported-field correction are outside the eight-call group; no reliability improvement has been demonstrated by these samples.

Time footprints merge overlapping provider interactions; they are not measured speedups or critical-path predictions. Reported costs are captured provider metadata, not repriced estimates. Sol is the user's preferred historical comparison model, but its dispatch cost share has not been measured here. Do not assume exactly 30% savings for Sol or use these samples as reliability evidence.

## Required behavior

### R1. Automatic, conservative eligibility

Determine eligibility for the particular parent-to-child capability binding using the admitted execution's actual receiving contract and declared bindings. A child may be eligible under one parent and require model arguments under another. Use the same contract semantics that govern model argument validation; do not create an independent interpretation that can drift from them.

Direct dispatch is eligible only when Framework can establish both:

- The assigned task and capability are fixed by an accepted plan and are ready under existing dependency/scheduling rules.
- No model-authored value or meaningful presence/omission choice remains in the effective argument contract after bindings are accounted for, and empty model arguments can enter the normal assembly path.

Support the clear case of a closed object whose possible input fields are all bound. An explicit closed zero-input contract also has no model contribution; absence of a schema is not a zero-input contract.

An optional unbound property still permits a contribution and must retain model dispatch. So must open additional properties, generic/unspecified contracts, or schema features for which absence of meaningful contribution cannot be proven. Do not infer eligibility from required-field coverage alone, tool name, model identity, historical empty outputs, examples, natural-language prompts, or an assumption that the model probably will not use an optional field. Do not synthesize constants, defaults, nulls or empty values merely to make a partially bound contract qualify.

Nested bindings require particular care: an empty ancestor object and an absent ancestor can have different meaning, as can an absent property and null. Treat nested scaffolding as contribution-free only if the contract and existing assembler prove it carries no remaining choice and produces the same complete input. Otherwise use the existing model path. Schema constructs outside the supported proof remain ineligible; the PR need not implement a general JSON Schema satisfiability solver.

Eligibility must be explainable through bounded diagnostic reason codes, including eligible, optional/unbound input remains, open/unknown contract, and unsupported or ambiguous shape. The exact internal representation is an implementation decision.

### R2. Reuse normal task execution

For eligible tasks, construct the assigned action from trusted plan identity and empty model arguments, then use the existing assigned-action validation and capability invocation path. Retain input binding assembly, complete receiving-input validation, caller identity propagation, authorization, lifecycle checks, tool/child validation, evidence recording, and plan completion behavior. Do not create a second executor or invoke underlying business methods/HTTP handlers directly around these controls.

The parent planner still runs, chooses tasks and dependencies, and has its plan validated. A directly dispatched model-backed child still runs its own required model interactions, output validation and corrections. Java and REST children retain their existing execution semantics. The rule is based on input ownership, not implementation language or whether the child itself uses a model.

The initial feature automatically applies to proven eligible assignments without author configuration. A per-skill switch is intentionally excluded. A deployment rollout switch was considered during discussion but is not required by this ticket; do not introduce one merely as a substitute for sound eligibility. If a supported compatibility contract makes a behavioral opt-out necessary, identify that concrete conflict rather than silently adding an authoring mode or bypassing the contract.

### R3. Binding failures are not model-repair opportunities

Resolve inputs at the normal invocation point from the current admitted mission input and exact accepted dependency results, not from a stale eligibility-time snapshot or model-reproduced prose. Preserve source values, optional-field presence, array order and provenance.

If an eligible task encounters a missing source path, ambiguous producer, incompatible value, failed dependency or other binding/receiving-contract error, surface the normal structured failure and do not execute the child. Do not fall back to asking a model to guess or repair Framework-owned values. Distinguish this from conservative eligibility fallback: when eligibility cannot be proven before execution, retaining today's model dispatch is correct.

### R4. Preserve lifecycle, security and side-effect semantics

Keep the existing scheduler, dependency admission, concurrency limits, joins, deterministic outcome folding, mission deadlines, cancellation, session limits, generation pinning and failure propagation. Eligibility and invocation must refer to the same pinned capability/configuration generation. Do not use this PR to increase concurrency or change plan ordering.

Recheck the normal invocation boundary so cancellation, timeout, revoked/invalid caller access, or a no-longer-executable task cannot be bypassed by taking the faster route. Preserve task identity and existing repetition/idempotency behavior. Do not introduce retries of child side effects or promise exactly-once execution.

A plan is not business authorization. Explicit approvals and receiving-API authorization continue to be enforced independently. No special treatment of a write-capable tool should weaken those requirements. Conversely, the eliminated worker interaction was not an approval gate: its current contract requires the assigned action. Conditional execution, replanning and new human approval mechanisms are out of scope.

### R5. Honest observations, hooks and accounting

Record that Framework dispatched the assigned task, why it qualified, which task/capability it invoked, the assembled inputs/binding provenance, and the actual completion or failure through normal observability. Preserve canonical data fidelity under repository AGENTS.md rules.

Do not fabricate MODEL_REQUEST_SENT/MODEL_RESPONSE_RECEIVED events, raw model responses, tokens, costs, retries, or correction attempts for the eliminated interaction. Retain actual child model events and usage. Existing task and tool observers must still see coherent start/completion/failure lifecycles. Trace consumers must distinguish Framework dispatch from a model-proposed action without treating missing parent model frames as lost evidence.

Inspect model-specific advisors/hooks, usage admission and supported observation contracts. A hook attached specifically to an actual model request naturally has no request to process when that call is eliminated; document that intentional difference. Any security, task lifecycle or tool execution checks previously reached incidentally through the model path must still be enforced at the appropriate execution boundary. Do not create a public internal-bean override SPI to achieve this. If an existing supported contract requires a semantically necessary model interaction, retain model dispatch for that case rather than silently violating it, and document the concrete exclusion.

Actual invocation/model usage decreases; plan/task limits and business authorization do not disappear. Explain the resulting trace/call-count changes in user-facing documentation and update affected supported consumers without rewriting historical artifacts.

### R6. Documentation and verification boundaries

Update the Framework skill-authoring documentation library and relevant consumer-facing documentation with the eligibility rule, a fully bound example, an optional-field counterexample, and the distinction between skipping parent dispatch and skipping child reasoning. Explain that task selection/conditional decisions and approval enforcement are not added by this feature. Authors should not need to learn a new YAML mode.

Demonstrate the feature with deterministic/fake-provider verification; paid model evaluations are not an acceptance prerequisite. Preserve existing source work and captured evidence. Do not change the sibling reference-suite prompts, schemas, evaluator, replay fixtures or runtime as part of this Framework ticket.

## Acceptance criteria

- [ ] **R1:** An accepted plan containing closed, fully bound tasks executes those assignments without parent dispatch model requests. The result does not depend on tool names or model identities. An explicit closed zero-input contract is distinguished from an unspecified contract.
- [ ] **R1:** Optional unbound fields, open/generic input, and unsupported or presence-sensitive schema shapes retain existing model dispatch. No fields are silently dropped or filled to qualify. The same child behaves correctly under different parent binding declarations; nested object/absence/null cases cannot cause false eligibility.
- [ ] **R2:** Planning still occurs and determines the admitted task graph. Direct and model-dispatched paths use the same validation/invocation controls and produce equivalent assembled arguments and deterministic child results for equivalent inputs. Directly dispatched model-backed children still make their own required model calls and validate their outputs.
- [ ] **R3:** Missing/ambiguous/invalid binding sources fail before child invocation with attributable diagnostics and no model guess/repair fallback. Successful bindings retain exact values and invocation-local provenance.
- [ ] **R4:** Authorization denial and explicit approval requirements are still enforced before side effects. Sequential and concurrent tasks preserve dependency/join semantics, cancellation/deadline behavior, configuration generation, failure propagation and existing duplicate-execution protections. No additional side-effect retry is introduced.
- [ ] **R5:** Traces identify Framework dispatch and its eligibility reason while retaining real task/tool lifecycle and binding evidence. There is no fictitious model call, usage or correction record. Actual child model usage remains visible. Supported observers/consumers and relevant hooks have documented, verified behavior; unknown eligibility stays conservative.
- [ ] **R6:** Documentation teaches the rule using existing contracts/bindings, includes optional-input and model-child examples, and introduces no per-skill switch or public internal extension point. Existing supported API/architecture checks pass; any required supported-surface changes receive explicit compatibility treatment.
- [ ] **R2/R5/R6:** A controlled mixed workflow demonstrates the exact reduction in parent provider-call count while retaining plan generation, noneligible dispatch, child reasoning and result correctness. No claimed timing, dollar or reliability improvement depends on a paid run or extrapolation from the four historical samples.

## Context and source hints

Dependencies: the input-binding projection/assembly semantics introduced by PR21 and the PR22/23 output-ownership contracts. Verify the current checkout rather than treating historical commit labels as acceptance.

Known source areas, as orientation only: ChildInputBindingProjection, StepActionValidator.validateAssigned, StepLoopMissionExecutionEngine's assigned-step/action path, and BoundCapability.invokeAssigned. A likely implementation is a conservative eligibility result beside the existing projected argument contract, with the direct action joining the same execution path after bypassing model generation/parsing. Exact classes, factoring, trace representation and verification commands belong to pipeline research and planning.

Evidence lives in the sibling repository, not this repository:

- C:/opendev/code/loomspan-sidecar-test-suite/evidence/dispatch-measurements-20261005/report.txt
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/dispatch-measurements-20261005/measurements.json
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/dispatch-measurements-20261005/calls.csv
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/pr23-mimo-20261005/summary.md
- C:/opendev/code/loomspan-sidecar-test-suite/evidence/pr23-adoption-20261005/summary.md

Those measurements include request identities, source hashes, provider usage, Framework timings and independent proxy timings. This ticket carries the necessary intent and measurements even if the sibling checkout is unavailable. Do not alter or regenerate original captures to satisfy tests.

Excluded: optional candidate-reasoning removal, semantic business validation improvements, provider routing/model changes, new planning or conditional-task semantics, new approvals, broader concurrency changes, replay migration, live reference-suite reruns and unrelated trace redesign. Reusing current trace facilities for honest dispatch provenance is in scope.

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** This changes a central execution path across binding contracts, task lifecycle, authorization preservation, concurrency, model hooks and trace consumers. Conservative eligibility and compatibility need research, planning, targeted verification and independent review even though the author-facing change is small.
- **Reassessment triggers:** New supported-hook dependencies, ambiguous schema/presence semantics, or incompatible trace consumers require explicit compatibility analysis and may narrow eligibility. They do not justify skipping validation or reducing independent review.

## Pipeline notes

- Eligible tasks intentionally stop producing a parent model request/response and its token/cost records. Preserve truthful task/tool observations; do not retain a dummy model interaction to make old request counts pass.
- This request creates the ticket only. It does not start the pipeline, run a paid evaluation, publish a release, commit or push changes.
