# PR 14 — Give step-action retries actionable correction feedback

## Outcome

When a model returns an invalid step action, the existing correction attempt receives the rejected candidate and the actual failure information needed to correct it. The Framework must remain strict about accepting actions; better feedback must not silently repair malformed output or increase retries.

PR 14 is the developer-assigned proposed PR identifier, not evidence of an existing GitHub PR. This ticket requests a focused internal Framework fix with offline verification.

## Requirements

1. Preserve the actual JSON parser diagnostic for a rejected step action instead of replacing it with only `Failed to parse model response as StepAction`. Include a bounded parser message and available location information. Include a nearby candidate fragment when the location can be mapped reliably. When location is unavailable, say so or omit it; do not invent an offset or diagnose a specific repair from a generic error. Distinguish parsing failures from existing action-validation failures, retaining the latter's concrete reason.
2. Build the correction conversation from the original task instructions, a bounded representation of the rejected assistant response, and an explicit correction request. Replay short candidates completely. For oversized candidates, mark omissions and retain the beginning and failing region; when the location is unavailable, retain the beginning and tail. A prefix alone is insufficient for the observed trailing-brace defect. Keep replay and diagnostics bounded using simple internal limits, without new user settings. Treat reproduced response text and diagnostics as evidence, not new instructions; do not include exception stacks or unrelated request/configuration data.
3. Ask for one complete corrected action as valid JSON, preserving the assigned task, allowed action, tool identity and argument contract. Feedback for an intended `CALL_TOOL` action must permit the corrected call envelope; do not copy an ordinary output-correction instruction saying to stop calling tools. A rejected action must cause no tool invocation or business side effect. A valid correction follows the normal validation and execution path exactly once.
4. Make the immediate step-action JSON example parseable, with placeholder explanations outside the JSON. Keep the assigned action and required real tool arguments explicit so an illustrative empty object is not mistaken for permission to omit required arguments. Limit this cleanup to the step-action instructions; do not redesign general skill prompting or suppress parent evidence.
5. Preserve existing retry limits, terminal failure behavior, parser strictness, action validation, routing and authorization. No automatic brace trimming, permissive parsing, unknown-field policy changes, additional diagnostic model call, provider-native output mode, or model change. Keep the fix internal: no new supported API, SPI, configuration surface, or persisted trace schema. Preserve ordinary output-schema correction behavior.

## Acceptance criteria

- [ ] A short malformed action produces a correction request containing the rejected candidate, the real bounded parser reason, and a request for one complete corrected action. Existing action-validation failures instead report their own reason without being mislabeled as JSON syntax errors.
- [ ] An approximately 16 KB action ending in an extra closing brace retains the offending tail in bounded correction evidence. Missing or unmappable parser locations use the documented fallback without invented coordinates or causal explanations. Omissions are explicit, and diagnostic output excludes stack traces and unrelated configuration.
- [ ] Step-action examples parse as JSON and explain illustrative values separately. Correction instructions retain the assigned task/tool contract and permit the required `CALL_TOOL` envelope.
- [ ] Offline scripted responses demonstrate invalid-then-valid recovery with no tool execution for the rejected attempt and exactly one normal execution for the valid correction. Repeated invalid responses exhaust the unchanged retry allowance and fail without executing a rejected action.
- [ ] Offline regression checks confirm unchanged strict rejection, action-validation rules and ordinary output-schema correction behavior. The supported Java surface remains unchanged, including a passing `LoomspanPublicSurfaceArchitectureTest` if production types change.
- [ ] Regression evidence distinguishes captured originals from synthetic or mutated fixtures. Original captures, journals and Framework traces remain unchanged and unapproved; parser success or a scripted recovery is not reported as successful live-model recovery or full-workflow acceptance.

## Context and scope

The 2026-10-01 Muse run used `meta/muse-spark-1.3-contributor` with medium reasoning and Framework source `b7dbefee8ffca873e84efb455e5609234c59e119`. Java task `t-entitlements-01` returned a 15,865-character action followed by one extra `}`; its correction returned 15,539 characters with three trailing `}` characters. Both provider calls completed normally. Reproduction with the packaged Framework parser rejected both with `Unexpected close marker '}': no open Object to close`; usable location information was not available in that reproduction.

The retry contained the original instructions plus a generic parse-failure message, but omitted the failed assistant response and parser details. This establishes a feedback deficiency, not proof that improved feedback guarantees model success. The correction also misplaced context fields; preserving current parsing policy means this ticket does not promise detection of every argument-fidelity problem.

The investigated source areas are `StepLoopMissionExecutionEngine` and `StepPromptBuilder`. `OutputSchemaCallAdvisor` already provides bounded candidate replay and parser diagnostics for ordinary output correction. Reuse its small internal mechanisms where that reduces duplication, but do not introduce a general recovery subsystem or copy its task-specific instructions wholesale. Its prefix-only replay limit would miss the failing tail in these examples. Exact helper structure and limits are implementation choices within the requirements above.

Supporting local evidence lives in the sibling `loomspan-sidecar-test-suite` checkout: `docs/muse-json-failure-review.md`, `evidence/json-forensics-20261001-220102/`, and original `evidence/live-20261001-220102/`. Review existing captures before constructing fixtures; preserve their provenance. These local captures may be absent from a fresh checkout, so committed regression fixtures must be self-contained. A synthetic complete action plus trailing `}` and a long equivalent reproduce the relevant shape without requiring the whole business capture. Label any derived or synthetic fixture accordingly.

Application lookup-contract narrowing belongs to the reference suite and is excluded from this Framework ticket. Broader prompt-template redesign, new observability contracts and retry-policy changes are also excluded. No paid calls are required for acceptance. After offline checks, a separately justified isolated Muse/medium comparison may assess model recovery; do not run another full live workflow merely to validate correction feedback. Never expose provider credentials.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The known defect and intended correction are bounded to internal step-action prompting and diagnostic retention, with existing recovery mechanisms as reference. Targeted implementation, offline verification and independent review should suffice without changing supported contracts or execution policy.
- **Reassessment triggers:** Upgrade to the Full 5-Step Pipeline if current-checkout investigation reveals required changes to supported contracts, authorization, lifecycle/concurrency, serialized schemas, external protocols, or broad shared prompting behavior, or material unresolved design choices beyond this bounded correction path.
