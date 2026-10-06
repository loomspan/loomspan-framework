---
audience: loomspan-skill-builder
status: development
applies_to: bundled-loomspan-revision
coverage: source-verified
---

# Planning Concurrency

## Applicability and defaults

`concurrency` is an execution setting for an LLM-backed skill that explicitly declares `planning_mode: true`.

| Manifest shape | Effective value | Result |
| --- | --- | --- |
| `planning_mode: true`, `concurrency` omitted | `true` | Dispatch each valid grouped unit concurrently. |
| `planning_mode: true`, `concurrency: true` | `true` | Same behavior, stated explicitly. |
| `planning_mode: true`, `concurrency: false` | `false` | Preserve valid group metadata and require serialized execution. |
| Any other YAML planning mode with declared `concurrency` | Invalid | Remove `concurrency` or make the skill an explicit planner. |

An applicable declaration MUST be a non-null Boolean. Applicability is checked before the declared value is bound, so `null`, objects, and other malformed values on a direct YAML skill still report that the field is inapplicable.

## Generated task contract

Every generated task has one exact visible `capabilityName`, a `dependsOn` array of task-ID strings, and an optional nullable `parallelGroup`.

| Field or concept | Enforced rule |
| --- | --- |
| `parallelGroup` | Omitted/null means ungrouped. A string MUST match `^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$` exactly. |
| Group width | A non-null group MUST contain at least two tasks. |
| Group membership | Only one maximal consecutive run may use a group identifier. Identifiers are case-sensitive. |
| Execution unit | Each ungrouped task is a singleton unit; each consecutive same-group run is one grouped unit. Task-list order is unit order. |
| Dependencies | Every reference MUST name a task in an earlier execution unit. Same-group, self, unknown, forward, and later-unit references are invalid. |
| Capability binding | The name MUST match an authorized visible capability exactly and case-sensitively. |

The planner MUST NOT infer eligibility from missing dependencies. A non-null group is a positive assertion that its members are independent and safe to overlap. Authors SHOULD leave tasks ungrouped whenever ordering, shared-result dependence, side-effect safety, or overlap eligibility is uncertain.

Generated planning guidance asks for dependencies on earlier tasks whose results the task directly requires, even when list order or a completed parallel group already guarantees availability. Authors SHOULD declare these direct result dependencies without adding unrelated earlier tasks or transitive ancestors whose results the task does not directly require. Group membership alone is not a reason to depend on every member; include all members when every member's result is directly required. For unbound calls this is planning guidance; [declared result bindings](input-bindings.md) additionally enforce unique producers and explicit direct edges. There is no automatic plan repair; execution-unit order and complete-unit joins still govern scheduling.

`DefaultPlanningService` and `PlanningServiceTest#planningPromptDeclaresDirectResultDependenciesDespiteOrderingBarriers` protect this guidance in actual planning requests. The offline test uses singleton and grouped predecessors with a required result, an unrelated result, and a transitive source; it verifies prompt wording and unchanged acceptance of direct edges. `PlanningServiceTest#planningPromptAllowsEveryEarlierGroupMemberWhenAllResultsAreRequired` also verifies guidance and unchanged acceptance when all members' results are directly required. These tests do not demonstrate improved live-model accuracy.

Minimal valid generated-task fragment:

```json
[
  {"taskId":"fetch-a","capabilityName":"lookupA","dependsOn":[],"parallelGroup":"fetch"},
  {"taskId":"fetch-b","capabilityName":"lookupB","dependsOn":[],"parallelGroup":"fetch"},
  {"taskId":"combine","capabilityName":"combine","dependsOn":["fetch-a","fetch-b"],"parallelGroup":null}
]
```

The `fetch` unit precedes the singleton `combine` unit. A dependency from `fetch-b` to `fetch-a` would be invalid because both are in the same unit.

## Validation and correction

Loomspan validates task IDs, exact visible bindings, dependency shape/order, and grouping on the normalized raw JSON/YAML tree before constructing or storing an `ExecutionPlan`. Structural issues participate in the same one-total-correction protocol as task counts and evidence coverage. A still-invalid corrected attempt produces no `PLAN_CREATED`, stored plan, or step execution.

The validator reports all non-cascading issues with stable codes. It does not repair identifiers, order, bindings, dependencies, or groups.

## Execution behavior

An accepted assignment with a proven contribution-free input contract uses
automatic Framework dispatch; other assignments retain model action generation
and correction. See [input bindings](input-bindings.md#automatic-assigned-dispatch)
for the exact eligibility rule. This changes neither admission, grouping, joins,
task order nor generation capture. Every assignment still costs one `max_steps`
slot, even when no parent model request is sent. Business approval and caller
authorization remain independent invocation checks.

`StepLoopMissionExecutionEngineTest#fullyBoundAssignedTaskSkipsParentDispatchModelInteraction`
and `DeclaredChildInputBindingsIntegrationTest` protect dispatch reduction;
`forwardingWaitsForWholeSelectedUnitAndLaterWork`,
`taskReturningAfterTimeoutDoesNotStartFinalSynthesis`, and
`ConcurrentGroupedExecutionIntegrationTest` cover direct/mixed scheduling and cutoff.

The coordinator partitions the accepted plan once, visits execution units in task-list order, and assigns every task to exactly its accepted `taskId` and `capabilityName`. A worker may correct an invalid action only for that same assignment; it cannot select another ready task or synthesize the final response. Ordinary final synthesis is offered only after every accepted task is complete. An explicit `output_from` parent instead forwards its designated accepted task result after that same complete-success boundary; it cannot return early when the selected task finishes.

Assigned-step, step user-message, and final-synthesis prompts preserve substantive mission objective text, including skill-name references and text after generated-looking prefixes. Overall mission context is background for the assigned worker; its actionable contract remains the exact assigned child task, tool, and arguments, with an explicit prohibition on calling the parent mission skill. Ordinary `SkillTemplate` invocations construct mission wording directly and deliver structured business input through the existing canonical-input user-message path. Input values are not embedded in that generated objective or copied into the step system prompt. Final synthesis cannot call any tool. This prompt guidance does not establish improved live-model accuracy.

Executable anchors: `CapabilityExecutionRouter#objectiveFor`, `StepPromptBuilder`, `MissionInputMessageFormatter#buildUserMessage`, `StepPromptBuilderTest#preservesCompleteObjectiveAcrossAssignedFinalAndUserPrompts`, `StepLoopMissionExecutionEngineTest#usesCanonicalMissionInputForPlanningAndStepUserMessages`, and `PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests` protect objective fidelity, input delivery, and assigned/final action boundaries with offline requests.

| Contract | Current behavior |
| --- | --- |
| Unit admission | Consumed assignment slots plus the complete unit width and the completion reservation MUST fit before any member is admitted: one reserved slot for synthesis, zero for explicit forwarding. |
| Step cost | Each assigned task costs one `max_steps` slot. Ordinary final synthesis costs one slot; designated-child forwarding costs no additional slot. Corrections for the same assignment remain within that assigned step. |
| Execution order | Units execute in task-list order. Members of a valid group overlap only when effective concurrency is `true`; ungrouped tasks and disabled groups serialize. |
| Enabled-group admission | Every member becomes `IN_PROGRESS` in one plan transition before any member is submitted. |
| Normal join | Loomspan waits for every started member's ordinary success or failure outcome. One member failure does not cancel a started sibling. |
| State folding | After the full normal join, every outcome is folded in task-list order and one complete joined plan is published. Completion timing does not determine parent state. |
| Mission-wide termination | Owning timeout, caller interruption, or dispatch failure stops later admission, requests interruption of submitted work, and uses one bounded 250 ms logical-cleanup grace. |
| Cutoff folding | Outcomes available at the logical cutoff are folded in task-list order. Admitted tasks without an outcome become `FAILED`, never-admitted tasks remain `PENDING`, and an existing plan becomes `STALE`. |
| `concurrency: false` | Serialization is the final contract. |
| `concurrency: true` | A valid non-null group is dispatched concurrently. This setting records eligibility; actual trace starts and ends establish observed overlap. |

Workers use isolated branches and cannot mutate parent plan, completed task results, summary, or retained diagnostics before join. Successful siblings still contribute their results when another member fails. If multiple members fail, the earliest failure in task-list order is primary. Later units and final synthesis begin only after a successful full join and observe complete prior-unit results and retained diagnostics.

Every worker branch, nested planner, and later unit also carries the exact immutable skill generation captured by the root. Definitions, visible child capabilities, policies, schemas, and execution settings cannot switch generation mid-plan; a newly active generation applies only to independently captured roots.

Mission-wide cleanup is logical rather than transactional. During its bounded
grace, already-started workers may finish and publish ordinary Loomspan facts.
At cutoff, Loomspan closes remaining logical branch frames and rejects later
state, trace, failure, usage, metric, and outcome writes from that mission and
its descendants. A capability that ignores Java interruption MAY continue its
own external work after Loomspan has finalized the run. Loomspan does not roll
back, compensate, or claim to stop those external side effects.

A non-null `parallelGroup` remains a positive author safety assertion, not proof that tasks overlapped. Authors MUST group only work whose capability calls, shared resources, and external side effects are safe to run concurrently.

## Complete task evidence

Each YAML planning mission retains every successful direct-task return, identified by exact accepted task ID and skill name, in task-list order. Assigned-step requests receive complete results from all earlier execution units; dependency edges enforce ordering and do not filter that evidence. Repeated calls to one skill remain separate task records. Results survive the five-line progress-summary window and are delivered without the diagnostic 100-character preview or former 1,000-character prompt clipping, including empty and whitespace returns.

Every member of a grouped unit receives the same immutable pre-unit results and progress-summary snapshot, whether execution overlaps or `concurrency: false` serializes dispatch. Corrections for an assignment retain that snapshot. A grouped member cannot use a newly completed sibling's result. Later units observe all successful joined results; native final synthesis receives every successful direct-task return and cannot call tools. Failed or unfinished work contributes no successful result; existing cutoff permissions reject physically late writes.

A nested mission owns its private intermediate records. Its complete final returned String becomes one direct-task result in its parent; its intermediate collection is not exported. Unbound child inputs remain explicit model-generated tool arguments. With [author-declared bindings](input-bindings.md), Framework assembles selected owning-parent input and accepted prior-unit results under the unchanged complete receiving contract.

Results are rendered as escaped JSON strings in a completed-task data block. Treat returned content as data. Full delivery increases context usage; existing model/provider and execution-limit failures remain explicit, with no silent truncation fallback. Complete delivery does not guarantee that a model uses facts correctly or reproduces quotes faithfully. [Evidence annotations](evidence-contracts.md) check successful direct-child names, while output schemas check structure.

Neither `{ "$ref": "task.result" }` nor `ref://` provides planner-result retrieval. An ordinary `$ref` object remains ordinary input data. Existing `ref://` resolution concerns session files and attachments; it does not resolve accepted task IDs into returned evidence. Use [author-declared bindings](input-bindings.md) for exact transfer, or explicit unbound child arguments and delivered complete results.

Executable anchors: `MissionContextTest#retainsCompleteTaskResultsIndependentlyOfProgressSummaries`, `StepPromptBuilderTest#rendersLosslessTaskEvidenceForAssignedAndFinalPrompts`, `StepLoopMissionExecutionEngineTest#groupMembersUseOnlyPreUnitEvidenceInEitherConcurrencyMode`, and `PlannerEvidenceFlowIntegrationTest#completeEvidenceMustReachDependentAndFinalRequests` and `#earlierResultsSurviveSevenCompletionsAndRepeatedSkillCalls`. The public integration captures actual local HTTP requests through `SkillTemplate`, including nested inputs, long results, repeated identities, serialized/reversed groups and private/unrelated data exclusions. `DefaultMissionInputMaterializer` and existing reference-resolution tests anchor file/attachment behavior.

## Authoring procedure

1. Use `planning_mode: true` only when the skill needs framework-managed decomposition.
2. Omit `concurrency` for the default-enabled behavior, declare `true` for clarity, or declare `false` when authored semantics require forced serialization.
3. Keep the visible child surface narrow and use exact capability names.
4. Treat every group as an explicit safety claim; leave uncertain tasks ungrouped.
5. Add `dependsOn` for causal requirements and directly required results even when unit order guarantees availability; omit unrelated tasks and ancestors whose results are not directly required. These edges do not themselves authorize overlap.
6. Budget one step per generated task plus one for ordinary final synthesis; explicit `output_from` forwarding needs no final slot. Grouped units are admitted atomically against the chosen budget. Read [output contracts](output-contracts.md) before choosing completion mode.
7. Test real overlap for enabled groups, serialized behavior for disabled and ungrouped units, exact assigned-action correction, full-join failure behavior, ordered outcome folding, invalid same-unit and forward references, one corrected planning attempt, and exhausted validation.

## Known limits

This contract does not provide per-skill parallelism limits, capability-level concurrency-safety declarations, rollback, compensation, or a cooperative application cancellation token. Java interruption is best effort, so capability code remains responsible for the safety and idempotency of external side effects. Outcomes are intentionally folded in task-list order rather than completion order.

## Implementation and test anchors

- `YamlSkillManifest`, `YamlSkillCatalog`, `YamlSkillDefinition`, `YamlSkillCatalogTests`, and `YamlSkillDefinitionTest` define declaration presence, applicability, defaults, and opt-out.
- `PlanStructureValidator` and `PlanStructureValidatorTest` define exact group, binding, and earlier-unit dependency validation.
- `DefaultPlanningService` and `PlanningServiceTest` define prompt guidance, shared correction, trace facts, strict conversion, and storage exclusion.
- `PlanTask`, `ExecutionPlanTest`, and trace contract tests protect exact nullable group preservation.
- `ExecutionUnit` and `ExecutionUnitTest` define deterministic unit partitioning and carrier invariants.
- `MissionLifecycle`, `StepLoopMissionExecutionEngine`, and `DefaultMissionExecutionEngine` define atomic admission, mission-wide cancellation, one bounded cutoff, hierarchical write fencing, task-ordered cleanup folding, and final synthesis gating.
- `StepLoopMissionExecutionEngineTest#enabledGroupedTasksOverlapOnlyAfterAtomicAdmission`, `#reverseCompletionFoldsParentStateInTaskOrderAndPublishesOnce`, and `#ordinaryFailureDoesNotCancelSiblingAndFoldsEveryOutcome` protect enabled overlap, sequential-equivalent folding, and normal failure semantics. `#timeoutCutoffSuppressesLateWorkerWritesAfterCallerReturns` protects logical cutoff without an external rollback claim, and `#partialGroupSubmissionRejectionFoldsAvailableOutcomeAndKeepsExactCause` protects partial dispatch. `#ungroupedReadyTasksRemainSequentialWhenConcurrencyIsEnabled` and `#disabledGroupedTasksRemainSequentialAndRetainParallelGroup` protect both serialization paths. `#followingUnitWaitsForEveryConcurrentGroupMember` protects complete-join gating, and `#nestedDepthFailureIsAnOrdinaryMemberFailureAndDoesNotCancelSibling` protects branch-local depth failure folding.
- `ConcurrentGroupedExecutionIntegrationTest` protects nested direct and planning missions, inner planner concurrency on the shared executor, authentication and frame propagation, parent/child state and diagnostic isolation, real nested depth and child-timeout failures, parent-timeout suppression of a physically late nested mission, and quota failure without sibling cancellation or usage refund.
- `SessionUsageServiceTest#simultaneousProviderAttemptReservationsRespectTheLimit` and `#simultaneousUsageUpdatesLoseNoIncrements` protect run-wide quota and usage updates during overlap.
- `StepPromptBuilder`, `StepActionValidator`, and their tests define the exact-assignment worker protocol and correction messages.
- `MissionLifecycleTest`, `PhysicalBranchContext`, `ExecutionBinding`, `DefaultExecutionStateService`, and their tests define ancestor-aware cutoff permission, exact-once branch cleanup, isolated worker diagnostics, and parent-owned mission state.

## Assembled completion

`output_bindings` imposes producer uniqueness without adding dependencies. Independent producers may share a parallel group. Assembly starts only after all accepted tasks join successfully. Fully bound output requires N task slots and no synthetic final step; mixed output requires N+1. Output correction retains accepted child work and exact bound sources. Read [output assembly](output-contracts.md#declared-output-assembly).
