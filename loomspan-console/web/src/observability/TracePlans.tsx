import { useEffect, useRef } from "react";
import type { PlanSummary, PlanTransition } from "../api/contracts";

function mode(value: boolean | null) {
  return value === true ? "enabled" : value === false ? "disabled" : "not applicable";
}

function overlap(value: boolean | null) {
  return value === true ? "observed" : value === false ? "not observed" : "unknown";
}

function valueOrUnavailable(value: string | null) {
  return value == null || value === "" ? "Unavailable" : value;
}

function transitionState(transition: PlanTransition) {
  if (transition.kind === "ADMISSION") return "Pending → In progress";
  if (transition.kind === "JOIN" && transition.outcome === "COMPLETED") return "In progress → Completed";
  if (transition.kind === "JOIN" && transition.outcome === "FAILED") return "In progress → Failed";
  return "Unavailable";
}

export function TracePlans({ plans, selectedPlanId, selectedTransitionSequence, onRecord, onFailure }: {
  plans: PlanSummary[];
  selectedPlanId?: string;
  selectedTransitionSequence?: number;
  onRecord: (sequence: number) => void;
  onFailure: (failureId: string) => void;
}) {
  const planRefs = useRef(new Map<string, HTMLElement>());
  const transitionRefs = useRef(new Map<string, HTMLElement>());
  useEffect(() => {
    if (!selectedPlanId) return;
    const transition = selectedTransitionSequence === undefined ? undefined : transitionRefs.current.get(`${selectedPlanId}\u0000${selectedTransitionSequence}`);
    const target = transition ?? planRefs.current.get(selectedPlanId);
    target?.focus({ preventScroll: true });
    target?.scrollIntoView?.({ block: "start" });
  }, [plans, selectedPlanId, selectedTransitionSequence]);
  if (plans.length === 0) return <p>No finalized plan evidence is available.</p>;
  return <div className="finalized-plans">
    {plans.map((plan) => {
      const tasks = new Map(plan.tasks.map((task) => [task.taskId, task]));
      const selectedPlan = selectedPlanId === plan.planId;
      return <article className="finalized-plan" key={plan.planId} aria-labelledby={`plan-heading-${plan.creationSequence}`} aria-current={selectedPlan ? "true" : undefined} tabIndex={selectedPlan ? -1 : undefined} ref={(node) => { if (node) planRefs.current.set(plan.planId, node); else planRefs.current.delete(plan.planId); }}>
        <h4 id={`plan-heading-${plan.creationSequence}`}>{plan.capabilityName || "Unknown capability"} plan</h4>
        <dl className="finalized-plan-facts trace-facts">
          <div><dt>Plan</dt><dd className="trace-identifier">{plan.planId}</dd></div>
          <div><dt>Status</dt><dd>{plan.status}</dd></div>
          <div><dt>Created</dt><dd className="trace-identifier">{plan.createdAt}</dd></div>
          <div><dt>Creation record</dt><dd><button type="button" onClick={() => onRecord(plan.creationSequence)}>Record {plan.creationSequence}</button></dd></div>
        </dl>
        <h5>Execution units</h5>
        <ol className="finalized-plan-units">
          {plan.executionUnits.map((unit) => <li key={unit.position} className="finalized-plan-unit">
            <p className="finalized-plan-unit-summary">Unit {unit.position}; group {valueOrUnavailable(unit.parallelGroup)}; effective concurrency {mode(unit.effectiveConcurrency)}; observed overlap {overlap(unit.observedOverlap)}.</p>
            <ol className="finalized-plan-tasks">
              {unit.taskIds.map((taskId) => {
                const task = tasks.get(taskId);
                if (!task) return <li key={taskId} className="finalized-plan-task finalized-plan-task-unavailable">Task {taskId}: diagnostic evidence unavailable.</li>;
                return <li key={task.taskId} className="finalized-plan-task">
                  <h6>Step {task.stepNumber}: {task.title}</h6>
                  <dl className="finalized-plan-task-row">
                    <div><dt>Task:</dt><dd className="trace-identifier">{task.taskId}</dd></div>
                    <div><dt>Capability:</dt><dd>{valueOrUnavailable(task.capabilityName)}</dd></div>
                    <div><dt>Status:</dt><dd>{task.status}</dd></div>
                    <div><dt>Failures:</dt><dd>{task.failureIds.length ? task.failureIds.map((failureId, index) => <span key={failureId}>{index > 0 && ", "}<button type="button" onClick={() => onFailure(failureId)}>{failureId}</button></span>) : "None"}</dd></div>
                    <div><dt>Dependencies:</dt><dd>{task.dependsOn.length ? task.dependsOn.join(", ") : "None"}</dd></div>
                  </dl>
                  <dl className="finalized-plan-task-row">
                    <div><dt>Group:</dt><dd>{valueOrUnavailable(task.parallelGroup)}</dd></div>
                    <div><dt>Effective concurrency:</dt><dd>{mode(task.effectiveConcurrency)}</dd></div>
                    <div><dt>Assigned frame:</dt><dd>{task.assignedFrameId || "Unassigned"}</dd></div>
                  </dl>
                  <dl className="finalized-plan-task-details">
                    <div><dt>Intent:</dt><dd>{valueOrUnavailable(task.intent)}</dd></div>
                    <div><dt>Expected outputs:</dt><dd>{task.expectedOutputs.length ? task.expectedOutputs.join(", ") : "None"}</dd></div>
                    <div><dt>Note:</dt><dd>{valueOrUnavailable(task.note)}</dd></div>
                  </dl>
                </li>;
              })}
            </ol>
          </li>)}
        </ol>
        <h5>Transitions</h5>
        {plan.transitions.length === 0 ? <p>No transitions recorded.</p> : <ol className="finalized-plan-transitions">
          {plan.transitions.map((transition) => {
            const selectedTransition = selectedPlan && selectedTransitionSequence === transition.sequence;
            return <li className="finalized-plan-transition" key={transition.sequence} aria-current={selectedTransition ? "true" : undefined} tabIndex={selectedTransition ? -1 : undefined} ref={(node) => {
              const key = `${plan.planId}\u0000${transition.sequence}`;
              if (node) transitionRefs.current.set(key, node); else transitionRefs.current.delete(key);
            }}>
              <dl className="finalized-plan-transition-facts trace-facts">
                <div><dt>Source record</dt><dd><button type="button" onClick={() => onRecord(transition.sequence)}>Record {transition.sequence}</button></dd></div>
                <div><dt>Kind</dt><dd>{valueOrUnavailable(transition.kind)}</dd></div>
                <div><dt>Group</dt><dd>{valueOrUnavailable(transition.parallelGroup)}</dd></div>
                <div><dt>Effective concurrency</dt><dd>{mode(transition.effectiveConcurrency)}</dd></div>
                <div><dt>Outcome</dt><dd>{valueOrUnavailable(transition.outcome)}</dd></div>
              </dl>
              <ol className="finalized-plan-transition-tasks">
                {transition.taskIds.map((taskId) => {
                  const task = tasks.get(taskId);
                  return <li key={taskId}>
                    <span className="finalized-plan-transition-task">{task ? <><strong>{task.title}</strong> <span className="trace-identifier">({task.taskId})</span></> : <>Task {taskId}: diagnostic evidence unavailable</>}</span>
                    <span className="finalized-plan-transition-state">{task ? transitionState(transition) : "Unavailable"}</span>
                  </li>;
                })}
              </ol>
            </li>;
          })}
        </ol>}
      </article>;
    })}
  </div>;
}
