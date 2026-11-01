# Planner Failure Recovery

## Status and scope

Follow-up design work after [Configured Java Skill Executors](configured-java-skill-executors.md). Improve planner recovery using that enhancement's shared Java execution and designated recoverable-exception contract. This document preserves agreed direction and open proposals; it is not a finalized implementation plan or authorization to implement unresolved behavior.

Apply the [framework feature design lens](../framework-feature-design-lens.md). Prefer the simplest complete final architecture and shared semantic ownership over smaller patches or duplicate recovery paths. Framework has no customers or deployments: breaking changes and temporarily broken intermediate builds across tickets and commits are permitted. Do not add compatibility shims merely to preserve obsolete internal behavior. Deliberately supported API additions still require explicit allowlisting, documentation, and supported-surface tests.

## Boundary with the executor enhancement

The prerequisite enhancement owns executor configuration, shared SkillMethod/SkillExecutor execution and output rules, the designated recoverable exception, accurate failure recording, and REST removal. It must also replace standard tool calling's broad runtime-exception conversion with explicit classification: only designated recoverable failures become model feedback; terminal failures propagate.

Its completed integration has these behaviors:

| Route | Behavior after the prerequisite enhancement |
|---|---|
| Direct invocation | Propagate recoverable and terminal exceptions to the application caller. |
| Standard model tool calling | Provide designated recoverable failure feedback to the model; propagate terminal failures. |
| Explicit step planning | Terminate the mission on tool failure, preserving recoverable classification and message. No post-execution correction loop yet. |

This follow-up adds planner capabilities on top of that contract. It does not require speculative return wrappers or recovery subsystems in the executor enhancement. Recheck source and tests after the prerequisite is complete rather than treating pre-enhancement research as the final baseline.

## Shared feedback contract inherited from the prerequisite

Feedback identifies the failed tool call, explicitly states that it failed and correction is permitted, and preserves the application-supplied recoverable message unchanged. Attempts remain recorded as failures. Feedback is not successful output and cannot satisfy downstream input bindings. Preserve exceptions and causes in diagnostics; do not substitute a stack trace or cause message for deliberate correction feedback.

## Agreed recovery direction

Share recoverable-failure classification and feedback across standard model tool calling and explicit step planning while preserving each mode's existing planning authority.

- Standard model tool calling may respond to recoverable feedback by correcting arguments, selecting another currently available tool, or changing its approach within existing authorization and execution boundaries. Do not add a same-tool restriction solely to match step planning.
- Explicit step planning may correct arguments for the same assigned task and tool. The accepted plan, task identity, and tool assignment remain unchanged; recovery does not authorize replacing tools, adding/removing tasks, or restructuring dependencies. Broader replanning is not selected.

Failed attempts remain failures under the shared feedback contract. Preserve previously successful work, and do not satisfy a downstream dependency with failure feedback. These orchestration differences reflect the existing planning modes, not different SkillMethod and SkillExecutor exception contracts. Share classification and feedback ownership rather than implementing separate Java failure policies.

Correction limits are settled below. Exhaustion behavior, nested propagation, and parallel-task coordination remain to be settled. No automatic replay of a failed operation is selected.

## Agreed execution limits

Reuse existing execution limits to bound recovery. Do not add a recovery-specific quota or per-task retry ceiling. Every correction model call and tool reattempt must use normal metered paths; failed attempts consume budgets, and recovery does not reset or refund counters or restart the parent timeout.

- Session quotas use captured entry-generation settings and shared, lock-protected accumulated counters across nested calls and parallel branches. Defaults are 64 skill invocations, 128 tool invocations, 64 model calls, 192 provider attempts, and 200,000 usage units. Zero disables an individual quota.
- `DefaultCapabilityInvoker` records tool invocation before dispatch. `MissionWorkExecutor` and the step engine record mission starts before work. Failed attempts therefore consume invocation budgets; recovery must not bypass these paths or reset/refund counters.
- `ProviderAttemptCallAdvisor` reserves a physical provider attempt before sending each request and accounts for model responses and usage afterward. Model/usage limits are post-response checks, not exact preflight cost reservations. Existing provider transport retries are distinct from application tool correction.
- Both modes have a positive mission timeout (60 seconds by default). The parent timeout continues across child calls; recovery must remain within that original parent lifetime. Timeout bounds Framework waiting/admission, not guaranteed physical termination of arbitrary external work.
- Step planning's `max_steps` allocates planned task slots and final synthesis when needed; it does not count every correction model call or tool attempt inside a task. Existing malformed-action correction has its own one-retry cap, which is not a general recoverable-tool-failure budget.

Preserve existing step-slot semantics and bound in-task correction through shared tool/model/provider/skill/usage quotas and the continuing mission timeout. All quota, timeout, cancellation, and shutdown failures remain terminal and must bypass recoverable feedback. With numerical quotas disabled, timeout remains the bound; no independent per-task attempt ceiling is implied. Validation should cover repeated recoverable failures in both modes, accounting across parallel/nested attempts, and terminal limit propagation through the standard tool manager.

Evidence: `DefaultSessionUsageService`, `ProviderAttemptCallAdvisor`, `DefaultCapabilityInvoker`, `MissionWorkExecutor`, `StepLoopMissionExecutionEngine`, and `LoomspanProperties.Session`; existing `SessionUsageServiceTest` covers shared entry quotas and provider-attempt reservation. This investigation used source inspection, not newly executed recovery tests.

## Investigation before the executor enhancement

The following describes the source inspected before implementation of the executor enhancement. Recheck the resulting implementation before planning this follow-up; the prerequisite is explicitly intended to change the first two behaviors.

Source inspection found two different layers of exception-to-text behavior:

- `SkillMethodBeanPostProcessor.invokeSkillMethod` converts most caught runtime exceptions to ordinary result text through `DefaultLoomspanExceptionTransformer`. Security failures and direct `IllegalArgumentException` are rethrown; serialization failures are wrapped and thrown. Converted exceptions never reach the surrounding execution failure path, and tool/task processing treats their text as a successful result. Direct Java invocation also returns that error text.
- Standard model tool calling uses `SpringAiToolCallbackAdapter` and the default tool manager configured by `SpringAiChatClientAssembler`. The pinned Spring AI 2.0.0 `FunctionToolCallback` wraps callback exceptions in `ToolExecutionException`; its default manager processes runtime causes into tool response text. Thus an ordinary runtime failure propagated by Loomspan can be recorded as a child/tool failure while still reaching the model as feedback for continuation. This default is broad, with no explicit Loomspan exception exclusions configured; it must not be mistaken for a deliberate recoverability policy.
- `StepLoopMissionExecutionEngine` records assigned task failures and invokes `propagateFirstFailure` after folding outcomes, in both sequential and parallel execution. A thrown ordinary tool failure ends that mission rather than prompting the model to recover. By contrast, error text returned by the Java transformer is folded as task success.

Consequently removing Java exception-to-text conversion alone does not unify orchestration behavior: direct invocation fails, standard model calling can continue with failure feedback, and explicit step planning terminates. Planner recovery, truthful failure reporting, and treatment of authorization/cancellation/limits must be assessed separately from Java method invocation. No new recovery subsystem or retry policy is selected. These findings are from repository and pinned dependency source inspection, not a new runtime test.

## Work owned by this follow-up

- Add post-execution argument correction to explicit step planning using the shared recoverable classification and feedback.
- Preserve each planning mode's authority while making failure handling consistent and useful to models.
- Decide how a planning skill reports that it could not complete its objective without representing that outcome as success.
- Decide nested recovery and escalation, including how a parent can try another approach without blindly replaying completed child work.
- Define partial-progress and evidence handling, correction termination, and parallel-task coordination.
- Verify repeated recoverable failures, terminal controls, and shared accounting across both planning modes.

These are topics to design, not permission to add a new state machine, public API, or replanning subsystem without evaluating simpler extensions of existing owners.

## Open proposal for planning skill inability

A proposed explicit unsuccessful planning outcome would let a model choose to report inability before exhausting execution limits. The model could continue with a plausible permitted correction, finish successfully after meeting the objective another way, or state that it cannot complete the objective with its tools, information, and authority. Framework would recognize an explicit validated action rather than infer failure from prose.

Proposed parent feedback includes why the objective could not be completed, what was attempted and learned, and known completed effects or partial progress. Framework would attach invocation identity and links to evidence. Model summaries must not become authoritative proof of external effects; preserve uncertainty about operations whose effects are unknown.

This is a proposal, not an accepted return protocol, schema, or requirement to implement a particular action. Decide whether it fits existing planning outcomes and how it differs from terminal failure. Exhausted quotas and cancellation remain terminal controls, not a mechanism for requesting parent assistance.

## Open questions for the next discussion

1. How should a planning skill explicitly report inability, and what evidence and partial-progress information should its parent receive?
2. When should nested recovery stay with the immediate planner, and when may unresolved recoverable outcomes reach a parent? The earlier immediate-parent-only restriction was not accepted. Giving higher-level models useful opportunities to recover is a design goal, not permission for automatic child replay.
3. Does the agreed same-task/same-tool scope for step correction cover the required cases? Broader plan restructuring remains unselected and must be discussed explicitly if needed.
4. How should recovery interact with parallel siblings, successful prior work, dependency readiness, and failures with uncertain external effects?
5. What happens when a planner declines further correction while execution limits remain? Exact unsuccessful completion and nested propagation semantics remain open.
6. Which existing owners should carry recoverable classification, failure evidence, and correction state? Avoid multiple authorities or reconstructing failure semantics from incidental text or ordering.

## Verification direction

Test model opportunities and execution truth together: recoverable attempts are recorded as failed, correction can succeed, completed work remains intact, and failed feedback never becomes dependency data. Cover both modes, nested calls, parallel tasks, terminal exceptions, authorization, cancellation, quota exhaustion, and continued parent deadlines. No recovery-specific quota is selected. Exact test and ticket breakdown follows the remaining design decisions.
