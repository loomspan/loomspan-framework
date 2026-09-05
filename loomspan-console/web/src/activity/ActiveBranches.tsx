import type { ActiveBranch } from "../api/contracts";

function countLabel(count: number) {
  return `${count} active ${count === 1 ? "branch" : "branches"}`;
}

function simultaneousGroups(branches: ActiveBranch[]) {
  const tasksByGroup = new Map<string, Set<string>>();
  for (const branch of branches) {
    if (branch.planId == null || branch.parallelGroup == null || branch.taskId == null || branch.effectiveConcurrency !== true) continue;
    const key = JSON.stringify([branch.planId, branch.parallelGroup]);
    const tasks = tasksByGroup.get(key) ?? new Set<string>();
    tasks.add(branch.taskId);
    tasksByGroup.set(key, tasks);
  }
  return new Set([...tasksByGroup].filter(([, tasks]) => tasks.size >= 2).map(([key]) => key));
}

function assignment(branch: ActiveBranch, simultaneous: boolean) {
  if (branch.taskId == null) return "Unassigned coordinator, planning, or synthesis branch.";
  const plan = `plan ${branch.planId}`;
  const step = branch.stepNumber == null ? "accepted step unavailable" : `accepted step ${branch.stepNumber}`;
  const group = branch.parallelGroup == null ? "not grouped" : `group ${branch.parallelGroup}`;
  const mode = branch.effectiveConcurrency === true
    ? "eligible for concurrent execution"
    : branch.effectiveConcurrency === false
      ? "concurrent execution disabled"
      : "effective concurrency not applicable";
  return `Task ${branch.taskId}; ${plan}; ${step}; ${group}; ${mode}.${simultaneous ? " Simultaneously active group member in this snapshot." : ""}`;
}

export function ActiveBranches({ branches }: { branches?: ActiveBranch[] }) {
  if (!branches) return <section className="active-branches" aria-labelledby="active-branches-title">
    <h4 id="active-branches-title">Active branches</h4>
    <p>Active branch snapshot unavailable.</p>
  </section>;
  const simultaneous = simultaneousGroups(branches);
  return <section className="active-branches" aria-labelledby="active-branches-title">
    <h4 id="active-branches-title">Active branches</h4>
    <p className="active-branch-count">{countLabel(branches.length)}</p>
    {branches.length === 0 ? <p>No active branch paths are present in this snapshot.</p> : <ol className="active-branch-list">
      {branches.map((branch, index) => {
        const leaf = branch.path.at(-1)!;
        const groupKey = branch.planId != null && branch.parallelGroup != null
          ? JSON.stringify([branch.planId, branch.parallelGroup])
          : "";
        const assignmentText = assignment(
          branch,
          branch.taskId != null && branch.effectiveConcurrency === true && simultaneous.has(groupKey),
        );
        return <li className="active-branch" key={leaf.frameId}>
          <details open={branches.length === 1}>
            <summary>Branch {index + 1}: {leaf.route || leaf.frameId}</summary>
            <p>{assignmentText}</p>
            <ol className="active-branch-path" aria-label={`Complete path for branch ${index + 1}`}>
              {branch.path.map((frame) => <li key={frame.frameId}>
                <span className="active-branch-frame-type">{frame.frameType}</span>
                {" — "}<span>{frame.route || frame.frameId}</span>
              </li>)}
            </ol>
          </details>
        </li>;
      })}
    </ol>}
  </section>;
}
