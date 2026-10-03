# PR 17 — Preserve null mission inputs without crashing tracing

## Outcome

Framework trace preparation must preserve explicit null values in mission inputs without throwing a NullPointerException or preventing a child skill from reaching its normal input validation/execution boundary. A model-generated null must not turn diagnostic capture into the cause of mission failure.

## Requirements

- Preserve null values and their containing structure when preparing mission inputs for canonical traces, including nested maps and collections. Do not drop fields, substitute empty values, stringify nulls, or rewrite execution inputs to avoid the exception.
- Retain existing input validation and skill behavior: accepting null for tracing does not make a missing required business input valid. Invalid inputs must follow the normal validation/error path rather than fail inside trace preparation.
- Preserve existing trace structure and non-null content, explicit resource limits, and snapshot isolation from subsequent input mutation. No new supported API or configuration is requested.
- Limit this ticket to the Framework null-handling defect and directly related regression coverage. Model planning, evidence transfer, citation quality, provider limits, and acceptance-suite business rules are outside scope. Fixing this defect does not imply that the originating model evaluation passes.

## Acceptance criteria

- [x] A nested child invocation with `context.serviceHistory: null` no longer throws from trace preparation; its null field survives in the canonical trace.
- [x] Explicit nulls in nested map values and collection elements retain their positions and structure without a trace-induced exception. Non-null content remains unchanged, and later input mutation does not alter the captured snapshot.
- [x] Inputs rejected by existing validation still produce the normal validation failure; inputs accepted by that validation reach the intended skill. Neither case is replaced by a trace-preparation NullPointerException.
- [x] Existing non-null tracing and execution behavior remains intact, with no new API/configuration contract, content masking, or relaxed business validation.
- [x] Regression verification can reproduce the null-handling condition deterministically without a live provider call. Results distinguish this repaired Framework behavior from the separate model-quality failures in the source run.

## Context and observed evidence

On 2026-10-03, the equipment reference suite evaluated `openai/gpt-oss-120b` on the embedded Java integration with medium reasoning. In the priority scenario, the model emitted a `CALL_TOOL` action for `compareOptions` whose `toolArguments.context` contained `serviceHistory: null` and `referenceEvidence: []`. The child call failed before its comparison model responsibility was reached.

The captured stack is:

```text
java.lang.NullPointerException
    at java.util.Objects.requireNonNull
    at java.util.ImmutableCollections$MapN.<init>
    at java.util.Map.ofEntries
    at java.util.Map.copyOf
    at ai.loomspan.internal.core.ExecutionCoordinator.traceSafeNode(ExecutionCoordinator.java:441)
    at ai.loomspan.internal.core.ExecutionCoordinator.lambda$traceSafeNode$6(ExecutionCoordinator.java:436)
    at ai.loomspan.internal.core.ExecutionCoordinator.traceSafeNode(ExecutionCoordinator.java:433)
    at ai.loomspan.internal.core.ExecutionCoordinator.traceSafeMissionInput(ExecutionCoordinator.java:398)
    at ai.loomspan.internal.core.ExecutionCoordinator.executeBound(ExecutionCoordinator.java:142)
```

`Map.copyOf` rejects null values. These anchors describe the installed artifact in the captured run; the pipeline must verify the current checkout rather than assume line numbers or implementation remain identical. The particular replacement collection strategy is an implementation choice, not a ticket requirement.

Retained local evidence in the sibling `loomspan-sidecar-test-suite` repository:

- Suite report: `evidence/evaluate-suite-20261003-143258-f3da12/report.json`.
- Priority provider journal: `evidence/live-20261003-143937-d35931/journal.json`; the final model response contains the null-bearing `compareOptions` call.
- Canonical trace: `evidence/live-20261003-143937-d35931/java/traces/loomspan-trace-d97a9b45-1356-47c8-a374-8883539baf91.ndjson`; record/line 386 contains the error and stack, and records 388 and 397 show propagation through `planResolution` and `resolveEquipment`.

The same evaluation's baseline completed but failed evidence-preservation and citation checks. The priority run also had missing evidence and a response ending with `finish_reason: length` before the final tool call. These observations are distinct from the immediate trace-preparation exception. No claim is made that fixing null handling resolves them. Preserve the original run evidence.

PR 17 is the user-assigned ticket identifier; this document does not establish that a GitHub pull request has been created. This ticket authorizes no pipeline execution or implementation by itself.

## Execution profile

- **Recommended:** Fast-Track 2-Step Pipeline — Implementation & Review
- **Confidence:** medium
- **Rationale:** The observed failure and intended behavior are bounded: null-preserving trace preparation without weakening validation or changing a supported contract. Targeted investigation, deterministic regression verification, and independent review should be sufficient; current-checkout scope has not been researched for this ticket.
- **Reassessment triggers:** Use the full pipeline if current-checkout triage discovers that the fix requires changes to persisted trace contracts, supported API, execution lifecycle/concurrency, or broader cross-cutting behavior beyond null-preserving trace preparation.

## Execution notes

- The developer approved Fast-Track 2-Step Pipeline — Implementation & Review on 2026-10-03. Initial Git state contained only this untracked ticket; no unrelated implementation changes were present. Research and plan artifacts are intentionally absent for this route.
- Current-checkout reconnaissance confirmed `ExecutionCoordinator#traceSafeNode` used null-rejecting `Map.copyOf` and `List.copyOf` for newly allocated schema-guided diagnostic containers. They now use null-tolerant unmodifiable wrappers around those fresh containers. Execution input, validation, schema traversal, attachment handling, and explicit resource limits are unchanged.
- Surface classification: the changed helper is internal implementation and its output is a current-run diagnostic snapshot. No supported Application API/SPI signature, Spring extension point, configuration/manifest behavior, serialized field/schema, or Console compatibility marker changes. No shim, Java-to-Go counterpart, or new abstraction is needed; the existing snapshot builder is reused. Fast-track remains appropriate.
- Skill-authoring impact is limited to trace/debugging guidance. Consulted the same-checkout `agent-skills/loomspan-docs/SKILL.md`, its skill-authoring index, `source-verification.md`, `input-contracts.md`, and `traces-and-debugging.md`. Existing documented validation and diagnostic semantics are aligned; the trace guide and coverage index now explicitly describe null fidelity without weakening business validation.
- Acceptance verification is provider-free: `ExecutionCoordinatorTest#nestedChildPreservesNullContextThroughValidationExecutionAndCanonicalTrace` exercises real child tool binding/routing/validation and asserts accepted `context.serviceHistory: null` reaches the child and survives canonical trace capture. `nullBearingMapAndListSnapshotsRemainImmutableAndIsolatedFromExecutionInputMutation` directly exercises schema-guided map/list snapshots with null array positions, unchanged non-null content, recursive visited-container immutability, original execution-input identity, and mutation isolation. Direct coordinator invocation isolates trace capture from the validator's rejection of null typed object array items; it does not make those items valid business input. `nestedChildRequiredNullStillFailsNormalValidationBeforeSkillExecution` verifies the existing `missing_required` issue and that the child does not execute.
- The originating live provider evaluation and sibling evidence remain unchanged. These regressions establish the repaired Framework boundary only; they make no model-quality, evidence/citation, or business-suite success claim.
- Implementation verification passed: `mvn "-Dtest=ExecutionCoordinatorTest" test -q` (26 tests); `mvn "-Dtest=ExecutionCoordinatorTest,SkillInputValidatorTest,CapabilityExecutionRouterTest,LoomspanPublicSurfaceArchitectureTest" test -q` (53 tests, including 8 public-surface architecture tests); `mvn test -q *> target/pr17-full-test.log` (1,253 tests across 165 suites, zero failures/errors/skips); `git diff --check`. Full ticket-scoped diff review confirmed no new public type/signature or Spring extension point. Acceptance criteria are checked on implementation evidence; the fresh Step 5 independent review remains required for pipeline completion.
