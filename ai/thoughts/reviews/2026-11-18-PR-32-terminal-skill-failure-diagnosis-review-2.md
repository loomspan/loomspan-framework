## Code Review Findings

No actionable findings.

## Findings Resolved in This Context

None. This review changed only this review document.

## Open Questions and Assumptions

None requiring a developer decision.

## Review Scope and Method

Reviewed `main` at `a65cdaa029c346b3301dd9506c7f1741c11f96cd` against its working tree. There were no staged or committed branch changes. Scope includes 46 modified tracked files and the four new production files, plus the ticket, research, implementation plan and testing plan. Earlier review documents were excluded and their contents were not read. The original checkout attribution is recorded in the ticket. No unrelated developer changes were identified.

Reconstructed all changed production paths before requirements conformance. Inspected untracked API values and `ContractFailures`, the public exception constructors and accessor, both root preparation forms, handoff delegation, router and assembled child rejection, planner action correction, YAML runtime transforms, all output validation/assembly boundaries, the session identity registry, canonical terminal selection, runner fallback and facade completion. Read connected failure policy, work execution and lifecycle paths to check cancellation and wrapper behavior. Reviewed every changed test and the relevant existing fixtures, including exact call-count, observer and authorization assertions.

The defect review covered correctness, source/binary/behavioral compatibility, concurrency and invocation isolation, diagnostics independence, content fidelity, access boundaries, resource lifetime and documentation. Supported status comes from root `AGENTS.md`, the closed architecture allowlist and the README. Internal Java visibility was treated according to that policy.

## Verification Results

Fresh verification used an isolated exact source copy at `C:/Users/mgiacomi/AppData/Local/Temp/loomspan-pr32-review2-4d4326de3f7d4b70b6dd6064b8d9e20f`. This avoids the IDE/JDT shared-output interference described in the implementation plan. The copy included tracked and untracked non-ignored source inputs and excluded earlier review documents. No IDE or build configuration was changed.

- PASS — `mvn -q "-Dtest=ApplicationApiValueTest,LoomspanPublicSurfaceArchitectureTest,OutputSchemaValidatorTest,LoomspanSessionTest,ExecutorOutputSchemaIntegrationTest" test` in the isolated copy. All 116 tests passed, including 13 API value checks, 8 architecture checks, 19 validator checks, 29 session checks and 47 executor output integration cases.
- PASS — `mvn -q test` in the isolated copy. Surefire reports contain 252 suites and 2,370 tests, with zero failures, zero errors and two skips. All eight `LoomspanPublicSurfaceArchitectureTest` checks passed after the final production edits.
- The two skips are the existing opt-in `Pr18RecordedHandoffDiagnosticTest` cases. Their reported reason is that the `pr18.diagnostic` system property is absent. They do not exercise this ticket's contract.
- PASS — `git -c core.safecrlf=false diff --check` in the checkout.
- PASS — SHA-256 comparison of all 54 changed/untracked non-review files against the verified copy found zero differences.
- PASS — The Java API index contains exactly 30 unique supported type entries. The count and names agree with the architecture allowlist. Searches found no obsolete twenty-seven-type inventory or old executor-schema enforcement assertion in the affected references.

## Requirements and Plan Conformance

Every ticket acceptance criterion is implemented and has sufficient executable evidence.

| Requirement | Reviewed implementation and verification |
| --- | --- |
| Pre-session, generated Java and bound child input failures | `DefaultSkillTemplate` builds diagnosis from the immediate validator result with the captured canonical identity. `ContractFailures.Input` preserves the public input exception and original issues for runtime receiving boundaries. `rootInputDiagnosisIsAvailableFromDirectAndHandoffPreparation`, `SupportedSurfaceIntegrationTest` and `DeclaredChildInputBindingsIntegrationTest` cover these normal entry points and prevent handler entry after rejection. |
| Planner argument schema rejection before dispatch | `StepActionValidator` retains the current validation verdict in `StepValidationResult`. Exhaustion carries only that verdict. `assignedChildSchemaDiagnosisBelongsOnlyToTheTerminalVerdict` covers exhaustion, correction and a later unrelated parse failure, with zero dispatch for failed cases. Fresh per-attempt validation prevents stale diagnosis. |
| Admitted parent, transformed null, skipped dependent and diagnostics NEVER | `admittedParentExposesSelectedChildDiagnosisWithoutObserver` exercises the handoff/invoke path in both NEVER and ALWAYS modes. It asserts child identity, OUTPUT_SCHEMA, `/repeatContactProbability`, `type`, explanation and exactly one producer call. Retained mode checks the selected canonical failure identity. The fixture uses `default-triage` as its child name and demonstrates the ticket's canonical child attribution independently of the requested parent name. |
| Executor, ordinary model, planning and assembled output failures | The output validator retains exact issues from the same validator pass. Executor publication, ordinary advisor exhaustion, planning final validation and binding composition preserve the verdict at their boundaries. `ordinaryModelPublishesOnlyTheTerminalSchemaVerdict`, planning engine tests, both output binding integration suites and `OutputSchemaCallAdvisorTest` cover terminal/corrected output, assembly, malformed JSON and validator codes. |
| JSON Pointer fidelity | `CanonicalSchema.instancePointer` builds from original validator tokens. `OutputSchemaValidatorTest` checks root, escaped and long control-containing keys. Existing bounded diagnostic paths retain their representation. `selectedDestinationSchemaDiagnosisUsesPublishedPointer` verifies rebasing from the private `/value` wrapper to the publishing destination. |
| Input/output transform stages and explanations | `YamlSkillDefinition` creates the transform verdict at the failing runtime boundary. Narrow coordinator catches bind the current session owner. The original handler remains between the transforms. Input/output transform integration tests verify INPUT_TRANSFORM before executor entry, OUTPUT_TRANSFORM after one call, malformed output and authored JSLT text. Successful transforms reach the existing output schema boundary. |
| Canonical order, causal wrappers, unrelated winners and cancellation | The existing task selection and failure IDs remain the authority. `LoomspanSession.recordFailure` enriches its existing identity/cause registry. `publicParallelDiagnosisMatchesAcceptedOrderDespiteReverseFailureRecording` verifies reverse event order and an unrelated winning failure through the facade. `parallelContractDiagnosisUsesAcceptedOrderAndCausalWrapper` and session tests cover causal wrappers. Cancellation tests verify absence despite recorded schema evidence, including the existing primary-cancellation-without-ID case. |
| Supported Java signature and documentation | `SkillException.getFailureDiagnosis()` returns only supported API/JDK types. Three immutable public values are deliberately allowlisted. Legacy constructors and input `getIssues()` remain usable. API value tests protect defensive copying, exact text and inherited `initCause` behavior. README and routed references document all four stages and absence semantics. |

Partial requirements are none. Missing requirements are none.

The plan's implementation checkmarks were checked against source and tests. The exact-pointer helper is supported by a concrete control-character regression. Conditional immutable facade wrapping preserves plain failure passthrough and enriches selected contract failures. Session ownership and concurrent reuse tests prevent a handler from supplying a stale or application-created diagnosis. The facade captures the selected value before mapping/observer work. Existing observer, fatal-error, authorization and suppression tests remain passing.

The primary cancellation snapshot uses the lifecycle's existing identity, including absence when framework shutdown installed a primary cause before an ID. It does not change failure IDs, cancellation selection or propagated exceptions. Runner fallback snapshots before optional trace work. Registry state exists independently of trace retention and diagnostic writes.

Compatibility assessment follows the plan's surface inventory. Application API changes are additive. The existing message constructors retain their original superclass behavior. All affected internal constructors/callers update atomically. No unsupported SPI, bean replacement surface, compatibility shim or public internal-type leak was introduced. Manifest/configuration behavior and executor call counts remain covered by the full suite. The change adds no persisted protocol, HTTP/SSE/NDJSON field or Console compatibility marker. Java-to-Go coordination is unnecessary for this Framework Java contract.

## Skill-Authoring Documentation Impact

Affected. Authors can identify their failing contract stage through the Java failure path even without retained diagnostics.

Used the repository-bundled `agent-skills/loomspan-docs/SKILL.md` as a documentation router after executable inspection. Its `1.0.0-beta.10-SNAPSHOT` version matches `pom.xml`. Reviewed root README, the Java API index, compatibility boundaries, invocation and observation/errors topics, and the changed executor/debugging guidance and skill-authoring routing/coverage. Applied the source-verification and LLM-first acceptance criteria.

Classification is aligned. The public error topic defines applicability, immutable values, exact pointers, null-input exception and absence semantics. Executor and trace topics route to that contract with actual focused test anchors. The API inventory and coverage tables match the source. The invocation reference accurately distinguishes authored executor publication validation, accepted forwarding and annotation-only Java output. No unresolved documentation drift remains.

Content fidelity and explicit access boundaries are preserved. Existing output diagnostic bounds remain independent of exact public issue values. The change introduces no sensitivity classification or content rewriting. The established journal projection exception remains covered by existing tests.

Coverage table is current. LLM-first usability passes because the routed topic provides the contract, applicability, limitations, evidence and adjacent-topic links needed to use the getter correctly.

## Residual Risks and Optional Developer Checks

The isolated-copy method avoids the observed local shared-output interference. Hash verification establishes that the tested source matches the checkout. The two existing opt-in diagnostic tests were skipped for their declared property condition. All ticket acceptance checks ran successfully with local model/provider fixtures and require no external service.

Optional developer checks are none. There is no unresolved required verification or product decision.

## Disposition

Approve. This fresh review found no actionable findings, made no implementation-artifact changes and completed sufficient independent verification.
