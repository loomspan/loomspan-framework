import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import plansFixture from "../../../browser-fixtures/trace-analysis/plans.json";
import canonicalFixture from "../../../../loomspan-console-fixtures/expected/canonical-concurrent-contract.json";
import nestedFixture from "../../../../loomspan-console-fixtures/expected/nested-assignment-shadowing.json";
import type { PlanSummary } from "../api/contracts";
import { TracePlans } from "./TracePlans";

const plans = plansFixture.items as PlanSummary[];

type FixtureProjection = (typeof nestedFixture.planProjections)[number];
function fromProjection(projection: FixtureProjection, transitions: typeof nestedFixture.planTransitions): PlanSummary {
  return {
    planId: projection.planId, capabilityName: projection.planId, createdAt: "fixture", status: projection.status,
    creationSequence: projection.creationSequence, traceRootFrameId: "root", missionFrameId: "mission", planningFrameId: "planning", attemptId: "", retrySequenceId: "",
    tasks: projection.tasks.map((task, index) => ({ stepNumber: index + 1, taskId: task.taskId, title: task.taskId, status: task.status, capabilityName: null, intent: null, dependsOn: [], expectedOutputs: [], parallelGroup: projection.executionUnits.find((unit) => unit.taskIds.includes(task.taskId))?.parallelGroup ?? null, note: null, assignedFrameId: task.assignedFrameId, effectiveConcurrency: task.effectiveConcurrency, failureIds: task.failureIds })),
    executionUnits: projection.executionUnits.map((unit, position) => ({ position, ...unit })),
    transitions: transitions.filter((transition) => transition.planId === projection.planId).map(({ sequence, kind, taskIds, parallelGroup, effectiveConcurrency = null, outcome = "" }) => ({ sequence, kind, taskIds, parallelGroup, effectiveConcurrency, outcome })),
  };
}

test("renders canonical plan units, task facts, and overlap independently", () => {
  render(<TracePlans plans={plans} onRecord={vi.fn()} onFailure={vi.fn()} />);
  expect(screen.getByRole("heading", { name: "planner</script> plan" })).toBeInTheDocument();
  expect(screen.getByText(/^Unit 0;/)).toBeInTheDocument();
  expect(screen.queryByText(/^Unit 3;/)).toBeNull();
  expect(screen.getByText(/effective concurrency enabled; observed overlap not observed/)).toBeInTheDocument();
  expect(screen.getByText(/effective concurrency disabled; observed overlap unknown/)).toBeInTheDocument();
  expect(screen.getByRole("heading", { name: "Step 3: ungrouped" })).toBeInTheDocument();
  expect(screen.getAllByText("Unassigned").length).toBeGreaterThan(0);
  expect(document.querySelector("script")).toBeNull();
});

test("shows assigned frames as text and retains record and failure callbacks", () => {
  const onRecord = vi.fn();
  const onFailure = vi.fn();
  render(<TracePlans plans={plans} onRecord={onRecord} onFailure={onFailure} />);
  expect(screen.getByText("frame-1").tagName).toBe("DD");
  expect(screen.queryByRole("button", { name: "frame-1" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Record 8" }));
  fireEvent.click(screen.getByRole("button", { name: "failure-1" }));
  expect(onRecord).toHaveBeenCalledWith(8);
  expect(onFailure).toHaveBeenCalledWith("failure-1");
});

test("groups short task facts into two rows and gives descriptions full-width lines", () => {
  const value = structuredClone(plans[0]);
  value.tasks[0].intent = "Extract structured trip preferences";
  value.tasks[0].note = "Completed tool understandPreferences";
  render(<TracePlans plans={[value]} onRecord={vi.fn()} onFailure={vi.fn()} />);
  const task = screen.getByRole("heading", { name: "Step 1: first" }).closest("li")!;
  const rows = task.querySelectorAll(".finalized-plan-task-row");
  expect(rows).toHaveLength(2);
  expect(Array.from(rows[0].querySelectorAll("dt"), (label) => label.textContent)).toEqual(["Task:", "Capability:", "Status:", "Failures:", "Dependencies:"]);
  expect(Array.from(rows[1].querySelectorAll("dt"), (label) => label.textContent)).toEqual(["Group:", "Effective concurrency:", "Assigned frame:"]);
  const details = task.querySelector(".finalized-plan-task-details")!;
  expect(Array.from(details.querySelectorAll("dt"), (label) => label.textContent)).toEqual(["Intent:", "Expected outputs:", "Note:"]);
  expect(Array.from(details.querySelectorAll("dd"), (value) => value.textContent)).toEqual(["Extract structured trip preferences", "answer", "Completed tool understandPreferences"]);
});

test("keeps hostile plan strings inert", () => {
  const hostile = "</script><img onerror=alert(1)>";
  const value = structuredClone(plans[0]);
  value.capabilityName = hostile;
  value.tasks[0].title = hostile;
  render(<TracePlans plans={[value]} onRecord={vi.fn()} onFailure={vi.fn()} />);
  expect(screen.getAllByText((text) => text.includes(hostile)).length).toBeGreaterThan(0);
  expect(document.querySelector("script")).toBeNull();
  expect(document.querySelector("img")).toBeNull();
  expect(document.querySelector("a")).toBeNull();
});

test("keeps nested mission boundaries and cleanup work in shared fixture order", () => {
  const nestedPlans = nestedFixture.planProjections.map((projection) => fromProjection(projection, nestedFixture.planTransitions));
  const concurrent = fromProjection(canonicalFixture.planProjections[0] as FixtureProjection, canonicalFixture.planTransitions as typeof nestedFixture.planTransitions);
  render(<TracePlans plans={[...nestedPlans, concurrent]} onRecord={vi.fn()} onFailure={vi.fn()} />);
  expect(screen.getAllByRole("article").map((article) => article.querySelector("h4")?.textContent)).toEqual(["outer-plan plan", "inner-plan plan", "concurrent-plan plan"]);
  expect(screen.getByRole("heading", { name: "Step 6: task-c" })).toBeInTheDocument();
  expect(screen.getByText(/effective concurrency enabled; observed overlap observed/)).toBeInTheDocument();
  expect(screen.getByText(/effective concurrency disabled; observed overlap not observed/)).toBeInTheDocument();
});

test("presents authoritative task state changes and retains transition facts", () => {
  const value = structuredClone(plans[0]);
  value.transitions.push({ sequence: 10, kind: "JOIN", taskIds: ["task-2"], parallelGroup: "disabled", effectiveConcurrency: false, outcome: "FAILED" });
  render(<TracePlans plans={[value]} onRecord={vi.fn()} onFailure={vi.fn()} />);

  const admission = screen.getByRole("button", { name: "Record 8" }).closest("li")!;
  expect(within(admission).getByText("first").parentElement).toHaveTextContent("first (task-1)");
  expect(within(admission).getByText("Pending → In progress")).toBeInTheDocument();
  expect(admission).toHaveTextContent("batch");
  expect(admission).toHaveTextContent("enabled");

  const completed = screen.getByRole("button", { name: "Record 9" }).closest("li")!;
  expect(within(completed).getByText("In progress → Completed")).toBeInTheDocument();
  expect(completed).toHaveTextContent("COMPLETED");

  const failed = screen.getByRole("button", { name: "Record 10" }).closest("li")!;
  expect(within(failed).getByText("dispatch only").parentElement).toHaveTextContent("dispatch only (task-2)");
  expect(within(failed).getByText("In progress → Failed")).toBeInTheDocument();
  expect(failed).toHaveTextContent("FAILED");
});

test("focuses only the selected authoritative plan or transition", async () => {
  const second = { ...structuredClone(plans[0]), planId: "plan-2", capabilityName: "second", creationSequence: 20 };
  const view = render(<TracePlans plans={[plans[0], second]} selectedPlanId="plan-1" onRecord={vi.fn()} onFailure={vi.fn()} />);
  const selectedPlan = screen.getAllByRole("article")[0];
  await vi.waitFor(() => expect(selectedPlan).toHaveFocus());
  expect(selectedPlan).toHaveAttribute("aria-current", "true");
  expect(screen.getAllByRole("article")[1]).not.toHaveAttribute("aria-current");

  const transition = within(selectedPlan).getByRole("button", { name: "Record 9" }).closest("li")!;
  transition.scrollIntoView = vi.fn();
  view.rerender(<TracePlans plans={[plans[0], second]} selectedPlanId="plan-1" selectedTransitionSequence={9} onRecord={vi.fn()} onFailure={vi.fn()} />);
  await vi.waitFor(() => expect(transition).toHaveFocus());
  expect(transition.scrollIntoView).toHaveBeenCalledWith({ block: "start" });
  expect(transition).toHaveAttribute("aria-current", "true");
  expect(within(selectedPlan).getByRole("button", { name: "Record 8" }).closest("li")).not.toHaveAttribute("aria-current");
  const otherPlan = screen.getAllByRole("article")[1];
  otherPlan.scrollIntoView = vi.fn();
  view.rerender(<TracePlans plans={[plans[0], second]} selectedPlanId="plan-2" onRecord={vi.fn()} onFailure={vi.fn()} />);
  await vi.waitFor(() => expect(otherPlan).toHaveFocus());
  expect(otherPlan.scrollIntoView).toHaveBeenCalledWith({ block: "start" });
  expect(screen.getAllByRole("article")).toHaveLength(2);
});
