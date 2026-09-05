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

The coordinator partitions the accepted plan once, visits execution units in task-list order, and assigns every task to exactly its accepted `taskId` and `capabilityName`. A worker may correct an invalid action only for that same assignment; it cannot select another ready task or synthesize the final response. Final synthesis is offered only after every accepted task is complete.

| Contract | Current behavior |
| --- | --- |
| Unit admission | The complete unit width plus one reserved final-synthesis step MUST fit before any member is admitted. |
| Step cost | Each assigned task costs one `max_steps` slot. Final synthesis costs one slot. Corrections for the same assignment remain within that assigned step. |
| Execution order | Units execute in task-list order. Members of a valid group overlap only when effective concurrency is `true`; ungrouped tasks and disabled groups serialize. |
| Enabled-group admission | Every member becomes `IN_PROGRESS` in one plan transition before any member is submitted. |
| Normal join | Loomspan waits for every started member's ordinary success or failure outcome. One member failure does not cancel a started sibling. |
| State folding | After the full normal join, every outcome is folded in task-list order and one complete joined plan is published. Completion timing does not determine parent state. |
| Mission-wide termination | Owning timeout, caller interruption, or dispatch failure stops later admission, requests interruption of submitted work, and uses one bounded 250 ms logical-cleanup grace. |
| Cutoff folding | Outcomes available at the logical cutoff are folded in task-list order. Admitted tasks without an outcome become `FAILED`, never-admitted tasks remain `PENDING`, and an existing plan becomes `STALE`. |
| `concurrency: false` | Serialization is the final contract. |
| `concurrency: true` | A valid non-null group is dispatched concurrently. This setting records eligibility; actual trace starts and ends establish observed overlap. |

Workers use isolated branches and cannot mutate parent plan, evidence, summary, last-result, or retained diagnostics before join. Successful siblings still contribute their results when another member fails. If multiple members fail, the earliest failure in task-list order is primary. Later units and final synthesis begin only after a successful full join and observe sequential-equivalent evidence, summary, last result, and diagnostics.

Mission-wide cleanup is logical rather than transactional. During its bounded
grace, already-started workers may finish and publish ordinary Loomspan facts.
At cutoff, Loomspan closes remaining logical branch frames and rejects later
state, trace, failure, usage, metric, and outcome writes from that mission and
its descendants. A capability that ignores Java interruption MAY continue its
own external work after Loomspan has finalized the run. Loomspan does not roll
back, compensate, or claim to stop those external side effects.

A non-null `parallelGroup` remains a positive author safety assertion, not proof that tasks overlapped. Authors MUST group only work whose capability calls, shared resources, and external side effects are safe to run concurrently.

## Authoring procedure

1. Use `planning_mode: true` only when the skill needs framework-managed decomposition.
2. Omit `concurrency` for the default-enabled behavior, declare `true` for clarity, or declare `false` when authored semantics require forced serialization.
3. Keep the visible child surface narrow and use exact capability names.
4. Treat every group as an explicit safety claim; leave uncertain tasks ungrouped.
5. Add `dependsOn` for causal and dataflow requirements even though those edges do not themselves authorize overlap.
6. Budget one step per generated task plus one final-synthesis step; grouped units are admitted atomically against that budget.
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
