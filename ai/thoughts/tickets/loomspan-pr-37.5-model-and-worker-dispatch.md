# PR 37.5 — Bind provider functions and plan-worker calls to exact targets

## Outcome

Execute the child selected for an invocation through each of the two model-facing protocols.

## Requirements

- The provider adapter renders the validated pair as `namespace--local-name` and binds that exact name to its resolved callback for the request. Dispatch returned names by exact lookup in the callbacks offered for this invocation. Reject an unoffered name without parsing, reverse translation or global lookup.
- Plans and worker prompts carry `namespace:local-name`. The worker's structured `CALL_TOOL.toolName` must match the assigned qualified reference exactly. Preserve raw provider requests, responses and traces verbatim. Record the resolved Loomspan identity with a colon in the separate resolved-skill field. Update runtime, planning and tracing examples for both protocols.

## Acceptance criteria

- [x] An end-to-end provider test offers child `order-support:find-order` as `order-support--find-order`, returns the offered name and executes the right skill. A colon spelling and an unoffered, otherwise valid function name fail dispatch.
- [x] A worker test verifies the qualified assignment in the prompt, accepts that exact structured action and executes the assigned skill. A different target or provider spelling cannot dispatch.
- [x] Raw provider evidence shows the names actually exchanged. Resolved fields and plans show the colon spelling. Runtime documentation and fixtures illustrate both paths.

## Context

Follows PR 37.4 in the local PR 37 series. PR 37.6 owns Console-facing contracts and displays. See the [portable naming proposal](../future/portable-skill-naming.md).

## Execution profile

- **Recommended:** Full 5-Step Pipeline
- **Confidence:** high
- **Rationale:** Provider dispatch, worker actions and trace records cross protocol boundaries.
- **Reassessment triggers:** None.

## Pipeline notes

PR 37 is a destructive replacement across undeployed products. Remove superseded naming behavior without aliases, shims or migration instructions. Write current-state documentation. Intermediate commits stay local and may break later consumers. Record deferred checks for PR 37.8 and verify this ticket's behavior as far as the local build allows.


## Completion record

### Implementation and acceptance evidence

Full pipeline Step 4 completed against the explicit component APIs from PR 37.4.
The developer authorized destructive replacement, current-state guidance and no
compatibility shims. No developer decision was required during implementation.

| Acceptance | Implemented behavior and verified evidence |
| --- | --- |
| Native provider selection | `SpringAiToolCallbackAdapter` derives `namespace--local-name` from retained metadata components. `RequestBoundToolCallingManager` checks exact request callback membership before side effects and delegates the original response. It contains no skill parser, reverse translation, global lookup or response reconstruction. `RequestBoundToolCallingManagerTest` covers valid, unoffered, colon, changed-case, whitespace, null/empty and prior-request selections, including all-generation preflight. |
| Real native invocation | `NamespaceExecutionAuthorityIntegrationTest#exactProviderOfferExecutesResolvedChildAndKeepsRawEvidence` publishes `order-support:find-order`, an unoffered sibling and a same-local callable in `orders`. It verifies the actual wire definition, exact captured target, continuation name and separate resolved `capabilityName`. Colon and registered-but-unoffered native selections execute no child. |
| Assigned worker | `NamespaceExecutionAuthorityIntegrationTest#plannedAssignedAndAutomaticCallsUseExactQualifiedIdentity` replaces the preceding canonical-name fixture. It verifies the `order-support:find-order` prompt, a native-free worker request, rejection/correction of provider spelling, exact accepted action and actual captured execution. `StepActionValidatorTest` covers a different visible target and wrong assigned task. The automatic route also passes. |
| Nonmutating protocols | `PlanStructureValidator` validates qualified values without writing them into the candidate tree. `StepAction.validateSelection()` validates the existing action without rebuilding it or its argument map. `PlanStructureValidatorTest` and `StepActionSelectionTest` cover unchanged accepted data, qualified hyphenated components, 64-character provider pair limits, malformed/case/alternate spellings and overlength pairs. |
| Raw and resolved evidence | The same native invocation verifies original argument whitespace, Unicode, nested nulls and ordinary values containing both spellings. Finalized NDJSON retains the exact accepted and rejected provider name and argument string. Lifecycle metadata uses `order-support:find-order`. Manager tests retain response/message/rich-part identity, and trace/physical-attempt suites preserve their arbitrary forensic data. |
| Existing safeguards | Real official-adapter, output repair, status, quota, argument object/precision, Framework inability binding, complete-response preflight, captured generation, caller authority and parallel isolation families all pass in the combined gate. |

### Fixture and guidance inventory

The conversion distinguishes native function fields (`--`), generated plan and
worker selections (`:`), resolved metadata (`:`), and declarations (lowercase local
names). Targets follow the pre-series codec and earlier declaration conversions.
Selected connected fixtures required explicit annotation names, lowercase
filesystem paths and matching executor declarations. Prompt fixture readers now
accept the colon in a qualified assignment. Native failed-call feedback assertions
use the actual provider name. Source business arguments, output-property names,
configuration keys, task IDs, dependency IDs, group labels and raw forensic samples
retain their values. Known task-to-local mappings in evidence/forwarding fixtures
preserve the original task IDs while selecting renamed local declarations.

The actual invoker test class is `DefaultCapabilityInvokerTest`, located in
`ToolCallbackFactoryTest.java`. The testing-plan command uses that executable
class name. No supported API or Spring extension point was added.

| Guidance reviewed and updated | Source/test anchor |
| --- | --- |
| Root README runtime example and qualified trace labels | Native/assigned authority integration and exact public invocation signatures |
| Packaged `mental-model.md` | Adapter, request membership and both actual runtime routes |
| Packaged `planning-concurrency.md` | Valid generated plan names, exact assigned action and native-free worker request |
| Packaged `traces-and-debugging.md` | Raw function names, colon-resolved metadata, actual recorded request content and finalized trace tests |
| Skill-authoring README coverage table | Named request manager, selection and actual invocation anchors |

The checkout guide and POM are version-aligned. Compared naming examples are
**aligned** after the current-state edits. Obsolete generated-plan and trace-name
guidance was **documentation drift**. The ticket supplies the provider contract.
Product guidance contains no rollout, migration or PR narrative.

### Verification

The minimal adapter regression was run before production edits and failed as
expected. Its mocked display name was offered by the baseline adapter instead
of the provider name derived from the retained `(order-support, find-order)` pair.
The passing final suite includes this regression and the maximum-length case.

The focused adapter/manager gate passed 70 tests. The focused planning/worker gate
passed 300 tests. Intermediate broad runs identified stale declaration/path/name
fixtures, raw-versus-resolved assertion residue and transient class-output errors.
The final clean run, with Java sources frozen, supersedes those failed attempts.

Final combined verification passed **783 tests across 47 selected classes with
zero failures, errors or skips**. It includes `LoomspanPublicSurfaceArchitectureTest`.
The log is `target/pr375-verification-logs/final.log`.

```powershell
.\mvnw.cmd clean test '-Dtest=BindingIsolationIntegrationTest,DeclaredChildInputBindingsIntegrationTest,DeclaredOutputBindingsOrdinaryIntegrationTest,DeclaredOutputBindingsPlanningIntegrationTest,DefaultCapabilityInvokerTest,DesignatedChildResultIntegrationTest,EvidencePlanningIntegrationTest,ExecutionTraceContractTest,ExecutorAccountingIntegrationTest,ExecutorApplicationIntegrationTest,ExecutorInputTransformIntegrationTest,ExecutorOutputSchemaIntegrationTest,ExecutorOutputTransformIntegrationTest,ExplicitJavaFailureHandlingIntegrationTest,JavaSkillIntegrationTest,LoomspanPublicSurfaceArchitectureTest,ModelAttemptCallAdvisorIntegrationTest,NamespaceExecutionAuthorityIntegrationTest,NamespaceStartupIntegrationTest,OrdinaryFinalOutputRepairIntegrationTest,OrdinaryFinalOutputRepairLifecycleTest,OrdinaryFinalOutputRepairTest,OrdinaryNativeOutputIntegrationTest,OrdinaryToolArgumentsTest,PlanInputBindingDependencyValidatorTest,PlanOutputBindingProducerValidatorTest,PlanStructureValidatorTest,PlanTaskConstraintValidatorTest,PlannerEvidenceFlowIntegrationTest,PlanningNativeOutputIntegrationTest,PlanningOutputContractTest,PlanningServiceTest,PlanningSuccessfulSkillCreditTest,ProviderResponseStatusIntegrationTest,PublicSkillReloadIntegrationTest,RequestBoundToolCallingManagerTest,SharedDeclarativeAuthorizationIntegrationTest,SharedResponsesProtocolIntegrationTest,SpringAiChatClientAssemblerIntegrationTest,SpringAiToolCallbackAdapterTest,StepActionCorrectionTest,StepActionSelectionTest,StepActionTest,StepActionValidatorTest,StepLoopMissionExecutionEngineTest,StepPromptBuilderTest,SupportedSurfaceIntegrationTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Mockito 5.24.0 agent loading remains a command-local Windows workaround. No build
configuration was introduced. Whole test compilation passed using the following
command, with its log at `target/pr375-test-compile.log`.

```powershell
.\mvnw.cmd test-compile '-DskipTests'
```

`git -c core.safecrlf=false diff --check` and a README LF byte check passed.
The final field audit restored the unrelated provider-model fixture literal
`default-model`. It retains its original value in the completed diff. A fresh
supplemental verification passed **25 tests with zero failures, errors or skips**
after that test-only correction, in `target/pr375-final-fidelity.log`.

```powershell
.\mvnw.cmd test '-Dtest=SharedResponsesProtocolIntegrationTest' '-DfailIfNoTests=false' '-DargLine=-javaagent:C:/Users/mgiacomi/.m2/repository/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar'
```

Production code is unchanged from the clean 783-test gate.

### Earlier deferrals and remaining checks

| Prior obligation | Disposition |
| --- | --- |
| PR 37.3 child-input/output/forwarding, accounting, application executor, transforms, schema, failure handling, Java and evidence-flow integration families | Their full classes pass in the 783-test final gate. |
| PR 37.4 supported-surface planner, namespace assigned/parallel authority, supplied-model publication and retained connection/handoff methods | Complete `SupportedSurfaceIntegrationTest`, `NamespaceExecutionAuthorityIntegrationTest`, `PublicSkillReloadIntegrationTest` and `NamespaceStartupIntegrationTest` pass. |
| Framework native/worker protocol and canonical trace evidence | Adapter/manager, real three-driver loops, planner, engine, canonical trace and attempt-recording suites pass in the same clean final tree. |
| `NamespaceObservationIntegrationTest`, `ConsoleRestFixtureCorpusTest`, `ConsoleTraceFixtureCorpusTest`, Console Go/UI contracts and stored corpus | NOT RUN here. These coordinated Console boundary checks belong to PR 37.6, with final integration in PR 37.8. |
| `ExecutorGenerationExecutionIntegrationTest#workedVariableCorpusReachesProviderAndActualExecutor`, `StartupVariableResolutionIntegrationTest` and the shared `read_record` worked fixture | NOT RUN here. The remaining shared variable fixture reconciliation belongs to PR 37.8. |
| Complete Framework and Console builds, other compiled tests and repository-wide/downstream documentation audit | NOT RUN here. PR 37.7 owns the documentation audit and PR 37.8 owns integrated closure. |

No required PR 37.5 acceptance check remains open. Optional developer checks are none.
