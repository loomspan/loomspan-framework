# Terminal skill contract failure diagnosis implementation plan

## Overview

Add an optional structured diagnosis to the supported Java failure path. A caller can retain its requested root name while reporting the canonical skill whose input schema, input transform, output transform, or output schema caused terminal failure. Direct invocation, admission-time rejection, and admitted invocation expose the same values with diagnostics set to `NEVER`.

This plan governs Step 4 of the developer-approved Full 5-Step Pipeline. The initial checkout is `main` at `a65cdaa029c346b3301dd9506c7f1741c11f96cd`. The ticket was the only untracked file. Research and these plans are pipeline artifacts. Preserve any subsequent unrelated developer changes.

## Current state analysis

- `DefaultSkillTemplate.java:156` validates root input before a session exists. Runtime routing and assembled child validation already have the receiving capability and the validator result.
- `StepActionValidator.java:170` discards typed child argument issues after formatting correction feedback. Each action has a fresh `StepValidationResult`, which is the appropriate lifetime for this verdict.
- `YamlSkillDefinition.java:27` separates input admission/evaluation and output evaluation, with canonical identity and Framework/JSLT explanations.
- `OutputSchemaValidator.java:43` has the original validator pointer, keyword and explanation. `OutputSchemaValidationIssue.java:25` bounds diagnostic text and uses `$` at the root. Those diagnostic fields cannot supply every valid public JSON Pointer after construction.
- Executor and ordinary model output exhaustion already have typed output failures. Planning and binding paths reduce some actual schema verdicts to generic exceptions (`StepLoopMissionExecutionEngine.java:516`, `:579`, `:1015`, `:1406`).
- `LoomspanSession.java:148` owns a locked throwable identity registry and causal wrapper links. `ExecutionCoordinator.java:223` selects primary cancellation before root completion. Accepted task order, classification, and lifecycle precedence already select terminal failure (`StepLoopMissionExecutionEngine.java:740`).
- `DefaultSkillTemplate.java:257` receives the completed session in its root completion callback, before the outer failure handler. Existing plain `SkillException` and authorization failures retain identity. Callback errors after execution failure are suppressed by the runner.

## Desired end state

Applications catch `SkillException` and call `getFailureDiagnosis()`. Present diagnosis contains a canonical skill name, one of four stages, a human-readable explanation, and immutable schema issues where applicable. Absence means the selected terminal cause has no diagnosis for these stages. An earlier rejected model attempt or a losing parallel branch cannot populate the terminal diagnosis.

Schema issue paths are JSON Pointers with the empty string at the root, `~0` and `~1` escaping, and exact key characters. Codes and explanations come from the validator that rejected the candidate. Transform diagnoses have an empty issue list and preserve the Framework/JSLT explanation, including authored errors.

Verify through supported invocation entry points and focused internal tests, then the complete Maven suite and a fresh independent review. Full test details are in the companion testing plan.

## Scope boundaries

The change reports the four contract stages. Current schema acceptance, validation order, retry counters, recoverable failure classification, cancellation precedence, executor call counts, binding authority, authorization, and diagnostic retention remain the behavioral baseline. Declaration loading, transform compilation, unavailable sources, ordinary handler failures, linter/evidence failures, and Sidecar HTTP representation are outside the four-stage diagnosis. The implementation needs no trace protocol or Console change.

## Material design decisions

### Supported Java values and compatibility

Add three public top-level types in `ai.loomspan.api` and deliberately allowlist them.

```java
public enum SkillContractStage {
    INPUT_SCHEMA, INPUT_TRANSFORM, OUTPUT_TRANSFORM, OUTPUT_SCHEMA
}

public record SkillContractIssue(String path, String code, String message) { }

public record SkillFailureDiagnosis(
        String skillName,
        SkillContractStage stage,
        String message,
        List<SkillContractIssue> issues) { }
```

Require non-null stage, canonical nonblank skill name and nonblank explanation in Framework-produced values. Copy issue lists defensively and normalize a null list to empty, consistent with existing API values. Require issue path/code/message to be non-null, permitting empty root path. Preserve exact supplied pointer/code/message text. Do not trim, truncate, rewrite, or infer sensitivity from these values. The canonical identity comes from the receiving capability or publishing definition.

Add `Optional<SkillFailureDiagnosis> getFailureDiagnosis()` to `SkillException`, backed by an immutable nullable diagnosis. Keep its existing `(String)` and `(String, Throwable)` constructors and delegate them to an additive `(String, Throwable, SkillFailureDiagnosis)` constructor with an absent diagnosis. Keep `SkillInputValidationException(String, List<SkillInputValidationIssue>)` and `getIssues()` intact. Add a constructor that also accepts a diagnosis for Framework root input rejection and runtime receiving failures. Existing issue lists and original validation issue values remain usable. No `(String, SkillFailureDiagnosis)` overload is added, so existing `(message, null)` calls stay unambiguous.

Existing plain application `SkillException` instances keep passthrough identity when the selected diagnosis is absent and their own diagnosis is absent. A Framework-created receiving input failure with matching selected diagnosis can also pass through unchanged. When selected diagnosis requires enrichment, or an application reuses an exception carrying diagnosis from an earlier invocation, create a new immutable facade `SkillException` with the selected value and original throwable as cause. This narrow conditional wrapping implements the new terminal contract while preserving the existing undecorated failure path and the public input exception for genuine current receiving failures. Keep authorization exceptions, fatal `Error`, successful-observer errors, and suppression precedence as they currently work.

### Origin ownership and exception reuse

Diagnosis is accepted only from the boundary that produced the current contract verdict. An arbitrary application-thrown `SkillException` with a diagnosis is not proof of a Framework contract failure. An exception object can be caught and reused by a handler in a later or concurrent invocation.

Use an internal carrier with immutable diagnosis plus the originating session's unique identity for execution-time boundaries. Runtime input failures can use an internal subtype of the public input exception. Existing internal output failures can implement the same carrier. Transform and planner exhaustion failures can use narrow internal exceptions retaining their current IllegalArgumentException/IllegalStateException or RuntimeException classification as appropriate. Give the carrier an ownership identity from the current session at construction. For YAML transforms, attribute in `YamlSkillDefinition`, then bind origin ownership in the coordinator's narrow transform call/catch boundary, where the session is explicit. Generic failure recording must ignore carrier diagnosis owned by another session and must ignore diagnosis on an application-created public exception. Pre-session root input failures expose their immediate diagnosis directly and do not establish execution-time ownership.

The immutable facade wrapper has no mutable attachment operation or public/internal mutation bridge. Session-local ownership prevents stale values on reused exceptions, and invocation-local facade capture prevents concurrent invocations from changing one another's public diagnosis. Add explicit later-invocation and concurrent reuse regression tests.

### Causal propagation and root publication

Extend the existing `LoomspanSession.RecordedFailure` with optional diagnosis. During the existing locked `recordFailure` walk, use an owned originating carrier or the existing recorded causal link. Transparent wrappers reuse the recorded value. Ordinary wrappers inherit diagnosis along the same cause linkage used by canonical failure recording. Suppressed exceptions and unrelated failures never participate. Preserve the existing cycle/depth guard and first recorded causal link semantics.

Retain the selected root diagnosis using the already chosen `terminalFailureId`, looking up that ID in the existing identity registry. This is an identity lookup, with no new competing failure selector or global failure table. Snapshot the selection independently of trace append, journal projection, retention, or observer success. Wire it at the top-level coordinator completion and the session-runner fallback completion. Snapshot even when selected diagnosis is absent, so the facade never falls back to an unrelated propagated exception. A primary cancellation ID/cause supplied by the lifecycle therefore controls diagnosis exactly as it controls canonical completion.

The template's root completion callback captures the session-selected diagnosis into invocation-local state before invoking the optional observer. Its outer catch uses that snapshot to construct or retain the public failure. Capture precedes mapper/observer work so those failures cannot erase or replace the terminal diagnosis. If the invocation fails before a session, use the immediate input contract diagnosis. A failure before ordinary completion inside an execution session must still go through the runner's existing fallback selection. Retain diagnosis despite optional diagnostic failures without changing which execution failure propagates.

### Exact schema issues and composition

Keep existing `OutputSchemaValidationIssue` diagnostic path and bounds. Extend the internal `OutputSchemaValidationResult` to carry exact `List<SkillContractIssue>` alongside diagnostic issues, populated from the same validator errors in a single validation pass. Malformed JSON produces root pointer `""`, code `invalid_json`, and the original parser explanation for the new issue. Existing bounded diagnostic reason/fragments remain governed by existing behavior. Update internal constructors and callers atomically, including test doubles. Internal signatures need no compatibility overloads.

Carry this typed verdict into `LoomspanOutputSchemaValidationException` on terminal rejection. Ordinary retries keep each attempt's verdict local and discard it on correction. Extend `StepValidationResult` with optional diagnosis so child argument and final output verdicts survive formatting. Successful and unrelated rejection factories return no diagnosis. On exhaustion, construct an owned terminal failure from the current result. The previous attempt's diagnosis must never survive a parse failure, wrong-tool rejection, evidence failure, or linter failure.

`OutputBindingComposition` needs the owning skill identity in its source-selection constructor. Atomically update internal call sites. For selected destination validation, the validator sees a generated `/value` wrapper. Map the exact issue pointer to the published destination by replacing its leading `/value` with `binding.destination().pointer()`, preserving the remainder. A root issue on that generated wrapper maps to the destination. Attribute the failure to the skill publishing the assembled output, not the producer whose value had already been accepted. Keep existing textual diagnostic locations intact.

Carry actual composition schema verdicts through projected contribution, fully bound output, and complete assembled output validation. Missing binding sources, evidence checks, output policy checks, and synthetic `binding_model_override` rejections carry no schema diagnosis unless the terminal rejection is an actual validator verdict. No schema reevaluation is needed merely to construct the report.

## Skill-authoring documentation impact

**Affected.** Authors and application developers can identify the failing skill and contract stage without retained diagnostics. This changes debugging and failure interpretation guidance while using existing transform/schema behavior.

- Update root `README.md` with the supported types, a minimal catch example, stage meanings, and selected-cause absence semantics.
- Update `agent-skills/loomspan-docs/references/java-api/observation-and-errors.md`, its `README.md`, and `compatibility-and-boundaries.md` for the new public signatures, exact values, type inventories and source anchors.
- Update `java-api/invocation.md` with direct/handoff availability. Correct its narrow existing claim that executor schemas provide no execution-time enforcement guarantee. `ExecutionCoordinator#publishExecutorResult` and `ExecutorOutputSchemaIntegrationTest` establish enforcement. Keep its separate provider-answer-schema limitation accurate.
- Update `skill-authoring/executor-skills.md` and `traces-and-debugging.md` with concise routing to public diagnosis and the distinction between terminal selection and recorded rejected attempts. Update the skill-authoring README's relevant coverage cells and Java API failure coverage for the new executable evidence.
- Use the repository-bundled `loomspan-docs` skill as the router. Its version matches the checkout's `1.0.0-beta.10-SNAPSHOT`. Current error taxonomy and executor behavior are aligned. The executor-schema sentence above is documentation drift, resolvable from current source and tests. No unresolved semantic drift remains.
- Preserve progressive disclosure by defining the public contract in the Java API error topic and linking from executor/debugging topics. Claims must name focused new/updated tests and matching production anchors. Apply the LLM-first acceptance questions from the skill-authoring README.

## Contract and compatibility impact

| Surface | Impact and evidence | Treatment |
| --- | --- | --- |
| Application API | SkillException accessor/constructor, input exception constructor, three new values, direct/handoff failure behavior. Closed allowlist and supported-surface tests are authority. | Additive source/binary shape. Preserve legacy constructors and issue accessor. Conditional immutable wrapping described above is limited to the newly diagnosed cases and stale diagnosis suppression. |
| Supported SPI | Repository exposes no supported Java SPI or bean-replacement surface. | Add no extension point, bean override, or public carrier interface. |
| Configuration and manifest contracts | Existing schemas, transforms, budgets, bindings and diagnostics retention are exercised. | Reporting addition. Preserve syntax, defaults, admission and executor behavior. |
| Persisted or serialized contracts | Sidecar response representation is consumer work. Public values are Java application values. | No HTTP, SSE, NDJSON or durable format change. No compatibility marker bump. |
| Ephemeral diagnostic formats | Existing canonical terminal IDs, wrapper links and outcomes are the causal authority. Existing diagnostic paths are bounded. | Keep current diagnostic writer/reader/projector coherence and paths. Keep new exact issue list internal to verdicts/public failure construction, avoiding incidental new serialized fields. |
| Internal or accidentally exposed implementation | Validator results, carrier, typed exhaustion, session records, composition constructors and coordinator/template plumbing. | Update atomically. Add exact technically-public internal allowlist entries only for necessary cross-package collaboration. No internal compatibility shims. |

Protected consumers are applications using the existing allowlisted API. In-repository callers, constructors, exception assertions, output-result test doubles, architecture allowlists and distributed documentation inventories must be updated together. Public signatures expose only API or JDK types. No new Spring extension bean is required.

**Shim decision.** Keep the two existing `SkillException` constructors and existing input exception constructor as permanent delegating entry points because they are supported Application API. Add no temporary migration machinery. Internal constructors change atomically.

**Java-to-Go coordination.** Not required for this Java failure contract. Keep consumed diagnostic fields and compatibility marker unchanged. There are no ticket Pipeline notes authorizing a broader protocol change.

## Implementation approach and phases

### Phase 1 — Public values, exact verdict data, and failure ownership

- [x] Add the three supported values, optional accessor and compatible constructor delegation.
- [x] Add internal owned failure representation and update architecture classifications.
- [x] Preserve exact output issue values from the validator into its result and terminal exception. Keep existing diagnostics fields separate.
- [x] Extend API value, validator, and architecture tests. Establish the smallest failing diagnosis assertion before adding behavior where practical.

Automated checks use `mvn "-Dtest=ApplicationApiValueTest,OutputSchemaValidatorTest,LoomspanPublicSurfaceArchitectureTest" test`. Require immutable lists, valid root/escaped/exact pointers, unchanged legacy constructors and no internal type leak.

### Phase 2 — Input and transform boundaries

- [x] Add immediate diagnosis at root validator rejection for both preparation paths and handoff preparation, using original issues and canonical receiver name.
- [x] Add owned input diagnosis at runtime routing and assembled child validation. Keep original public input issues and validation behavior.
- [x] Preserve receiving child diagnosis on planner argument schema rejection and use only the current rejection on correction exhaustion.
- [x] Attribute runtime input/output transform errors in YAML definitions and bind ownership at the narrow execution boundary. Preserve JSLT and authored explanations and existing call counts.
- [x] Verify normal invalid input still has public input exception catch compatibility. Keep existing null-overload behavior. For explicit null rejection that occurs without a completed validator verdict, expose receiving INPUT_SCHEMA context with the existing empty issues, rather than fabricate a validator code.

Use the input, transform and planner groups in the testing plan. Require corrected or unrelated planner failures to have no diagnosis from a rejected attempt.

### Phase 3 — Terminal output and canonical selected failure

- [x] Carry executor, ordinary-model, planner, projected/assembled output and selected-bound-destination schema verdicts to terminal failures.
- [x] Extend existing session records and canonical cause walk with owned diagnosis.
- [x] Snapshot the already-selected root failure diagnosis independently of diagnostics, including primary cancellation and runner fallback completion.
- [x] Capture selected diagnosis in the facade callback before optional observer work and expose it through both direct and admitted invocation. Apply immutable conditional wrapping and stale-value suppression.
- [x] Add deterministic accepted-order parallel tests, causal wrapper tests, unrelated terminal/cancellation controls, diagnostics failure/NEVER checks and exception reuse tests.

Require facade diagnosis and canonical selected terminal ID to agree whenever trace evidence is retained. Require diagnostics NEVER to expose the same public value. Preserve dependent-child suppression and retry/executor invocation counts.

### Phase 4 — Documentation and complete verification

- [x] Update every documentation path listed in the impact assessment and matching coverage inventories.
- [x] Map every ticket acceptance criterion to passing executable evidence in implementation results.
- [x] Run `mvn test` after targeted checks, inspect failures for attribution, and run `git diff --check`.
- [x] Run `LoomspanPublicSurfaceArchitectureTest` after the final production type changes, either explicitly or in the complete suite, and record actual results.
- [x] Recheck public surface, Spring extension exposure, exact pointers, content fidelity and unchanged canonical diagnostic fields across the full diff.

The independent review stage follows implementation. Completion requires a fresh clean review with sufficient verification.

## Testing strategy

Use existing fake models and local protocol-compatible providers. Extend focused tests close to the validator, facade, planner, coordinator and existing executor integration fixtures. Add a supported-surface failure scenario whose consumer assertions import only supported API types. Deterministic latches control parallel failure record order and canonical winner. No live provider, network credentials, Sidecar, or Console environment is needed. Exact commands and acceptance mapping are in `2026-11-18-PR-32-terminal-skill-failure-diagnosis-testing.md`.

## Performance and lifecycle considerations

Reuse the existing session identity registry and lock. Store immutable issue lists only on actual failed verdicts and their originating throwable/record. Avoid validating twice or retaining raw candidates in new public values. Selected diagnosis snapshot must not retain the whole session. Origin ownership uses a stable session identity value, avoiding retention of session state by exceptions. Corrected attempts remain subject to their existing local lifetimes.

## Migration and optional developer checks

No data migration or external consumer change is needed for the Framework deliverable. Sidecar may separately map the supported Java value into its response. Optional developer checks are none. All acceptance criteria have executable local verification.

## References

- Ticket — `ai/thoughts/tickets/loomspan-pr-32-terminal-skill-failure-diagnosis.md`
- Research — `ai/thoughts/research/2026-11-18-PR-32-terminal-skill-failure-diagnosis.md`
- Policies — `AGENTS.md`, `ai/thoughts/framework-feature-design-lens.md`, `ai/commands/shared/loomspan-docs-protocol.md`
- Existing selection coverage — `StepLoopMissionExecutionEngineTest#parallelFailureSelectionUsesClassificationThenAcceptedOrder`
- Existing publication coverage — `ExecutorOutputSchemaIntegrationTest`, `ExecutorOutputTransformIntegrationTest`

## Implementation results

### Material adaptations

- Framework-produced public pointers use `CanonicalSchema.instancePointer` to rebuild RFC 6901 paths from the validator's original path tokens. A focused test demonstrated that NetworkNT's diagnostic string escapes control characters, so even the pre-bounding string is insufficient for exact public paths. Existing output diagnostic paths remain unchanged. Input issue pointers now preserve original key characters as well.
- `LoomspanSession.selectTerminalFailure` snapshots the existing selected identity before optional finalization. Root completion uses the installed primary cancellation identity when present. A framework-cutoff primary can have no failure ID while the existing completion fallback records an incidental propagated exception. That case selects absent diagnosis, preserving the authoritative cancellation decision, propagated exception and canonical IDs.
- Legacy message constructors directly call their original superclass constructor to retain inherited `Throwable.initCause` behavior. The additive constructors and immutable diagnosis values provide the new path.
- Test execution revealed IDE language servers writing Eclipse/JDT classes into the watched checkout's Maven output, including stale anonymous-class signatures and unresolved-compilation error stubs. Final verification runs against an exact source snapshot in a fresh temporary directory, avoiding shared output. IDE settings and processes remain as configured.

### Verification history

- `mvn -q -DskipTests compile` passed during the initial implementation phase.
- The initial API/validator/architecture group found the expected constructor inventory update. The next run passed public executor failure tests but exposed the exact control-containing pointer regression, which was corrected from original validator tokens.
- Expanded focused checks ran 426 tests with no assertion failures and three test-fixture binding errors in the newly added parallel test. Those tests now call the session recording method outside the execution scope.
- The first full `mvn -q test` run executed 2,362 tests with two stale-class `NoSuchMethodError` failures. A clean run confirmed JDT error-stub contamination in the shared output directory and was stopped after attribution.
- Final verification and acceptance mapping follow.


### Final verification

- PASS — `mvn -q test`, working directory `C:/Users/mgiacomi/AppData/Local/Temp/loomspan-pr32-verification-taghhxf6`. The directory is a fresh copy of every checked-out tracked and untracked non-ignored file, with no prior build output. All 252 suites completed, reporting 2,370 tests, zero failures, zero errors and two existing opt-in PR18 diagnostic skips (`pr18.diagnostic` is unset). All 8 `LoomspanPublicSurfaceArchitectureTest` checks passed after the final production edits.
- PASS — `git -c core.safecrlf=false diff --check` in the repository checkout.
- PASS — SHA-256 comparison of every changed/new production, test and documentation file against the verified snapshot found zero differences. Pipeline progress artifacts were updated after verification and do not affect runtime/test inputs.
- Prior failed checkout runs are superseded by the isolated full-suite result. The IDE/JDT output contamination was diagnosed from generated classes throwing `java.lang.Error: Unresolved compilation problems` and stale anonymous-class `NoSuchMethodError`, with live Java language servers writing to the same target directory. No IDE setting, language-server process or build configuration was changed. The contaminated Maven test processes were stopped before independent verification.
- Initial baseline evidence comes from the unchanged HEAD API source, which has neither the diagnosis accessor nor diagnosis types. A caller assertion against the planned new signatures could not compile against that baseline. After adding the signatures, the exact-pointer regression test ran red on NetworkNT's escaped newline and then passed using original tokens. The new admitted nested-null scenario passes in both retained and `NEVER` modes.

### Acceptance evidence

| Ticket requirement | Passing executable evidence |
| --- | --- |
| Root admission, generated Java input, bound receiver and public input exception compatibility | `ExecutorOutputSchemaIntegrationTest#rootInputDiagnosisIsAvailableFromDirectAndHandoffPreparation`, `SupportedSurfaceIntegrationTest#javaFoundationUsesOnlySupportedTypesAndKeepsExecutorsReferenceOnly`, `DeclaredChildInputBindingsIntegrationTest#executorLeafReceivesDynamicBindingsWithExactReceiverAndDirectValidation`, `DefaultSkillTemplateTest#invalidInputDoesNotInvokeObserver` |
| Undispatched planner argument schema exhaustion, correction and unrelated terminal rejection | `StepLoopMissionExecutionEngineTest#assignedChildSchemaDiagnosisBelongsOnlyToTheTerminalVerdict`, `StepActionValidatorTest#missingRequiredArgumentsFromConcreteToolSchemaRejected` |
| Admitted parent publishes transformed-null child diagnosis with `NEVER`, exact location/code and skipped dependent | `ExecutorOutputSchemaIntegrationTest#admittedParentExposesSelectedChildDiagnosisWithoutObserver` runs with `NEVER` and `ALWAYS`, comparing the selected canonical error identity in retained mode |
| Executor, ordinary model, planning and assembled output, retries, malformed JSON and exact pointers | `ExecutorOutputSchemaIntegrationTest#invalidCandidatesFailOnceWithLocationAndConstraint`, `#ordinaryModelPublishesOnlyTheTerminalSchemaVerdict`, `StepLoopMissionExecutionEngineTest#exactFinalBoundaryCorrectsOrExhaustsWithoutReplayingCompletedWork`, `DeclaredOutputBindingsOrdinaryIntegrationTest#dynamicBoundTypeMismatchFailsBeforeProviderAndWithoutAssembly`, `DeclaredOutputBindingsPlanningIntegrationTest#executorPassthroughIsEnforcedOnlyByConsumingBindingContract`, `OutputBindingCompositionTest#selectedDestinationSchemaDiagnosisUsesPublishedPointer`, `OutputSchemaValidatorTest#publicIssuesPreserveRootEscapingAndLongControlContainingPropertyNames`, `OutputSchemaCallAdvisorTest#throwsLoomspanOutputSchemaValidationExceptionWhenRetriesExhausted` |
| Non-object input transform, malformed output JSON, authored transform error and unchanged executor counts | `ExecutorInputTransformIntegrationTest#rejectsUnsupportedResolvedInputAndBadResultsBeforeTheExecutorRuns`, `ExecutorOutputTransformIntegrationTest#transformFailureIsTerminalAfterExactlyOneExecutorCall`, and existing transform/schema publication matrices |
| Reverse recorded/accepted order, causal wrapper, unrelated failure and cancellation absence | `ExecutorOutputSchemaIntegrationTest#publicParallelDiagnosisMatchesAcceptedOrderDespiteReverseFailureRecording`, `#selectedCancellationKeepsDiagnosisAbsentAfterSchemaFailureEvidence`, `StepLoopMissionExecutionEngineTest#parallelContractDiagnosisUsesAcceptedOrderAndCausalWrapper`, `LoomspanSessionTest#selectedDiagnosisFollowsOwnedCauseAndIgnoresSuppressedUnrelatedAndForeignFailures`, `#primaryCancellationWithoutIdentityPreventsIncidentalSchemaDiagnosis` |
| README, supported signature inventory and ordinary API entry points | `ApplicationApiValueTest` (13 checks), `LoomspanPublicSurfaceArchitectureTest` (8 checks), `SupportedSurfaceIntegrationTest`, direct/handoff/NEVER integration assertions and documentation inspection |
| Diagnostic independence, legacy Throwable semantics and stale/concurrent exception reuse | `LoomspanSessionTest#selectedDiagnosisSurvivesDiagnosticFinalizationFailure`, `ApplicationApiValueTest#legacyMessageConstructorsRetainThrowableCauseInitialization`, `ExecutorOutputSchemaIntegrationTest#publicFailureSnapshotsIgnoreReusedOrApplicationSuppliedDiagnosis` |

### Final review of implementation scope

The API adds exactly three deliberately allowlisted public types. No public signature leaks internal or autoconfiguration types. No Spring replacement surface, trace compatibility marker or consumed protocol changed. The existing journal projection exception is preserved. Documentation drift about executor output schema enforcement is corrected in the invocation reference and the matching README sentence. Java API and authoring routing/coverage now point to the exact supported failure contract and focused tests. The LLM-first acceptance questions are satisfied by the routed error topic, its applicability/stage/absence rules, evidence and next-topic links.

No unresolved required check or optional developer check remains for Step 4. Independent Step 5 review establishes pipeline completion.
