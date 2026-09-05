import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import type { ActiveExecution } from "../api/contracts";
import concurrent from "../../../browser-fixtures/active-executions/concurrent-siblings.json";
import empty from "../../../browser-fixtures/active-executions/empty.json";
import crossPlan from "../../../browser-fixtures/active-executions/equal-group-different-plans.json";
import repeatedTask from "../../../browser-fixtures/active-executions/repeated-task-leaves.json";
import { ActiveBranches } from "./ActiveBranches";

const execution = (value: unknown) => value as ActiveExecution;

test("does not claim zero branches without an active snapshot", () => {
  render(<ActiveBranches branches={undefined} />);
  expect(screen.getByText("Active branch snapshot unavailable.")).toBeInTheDocument();
  expect(screen.queryByText("0 active branches")).toBeNull();
});

test("renders every canonical branch without truncation", () => {
  const { rerender } = render(<ActiveBranches branches={execution(empty).activeBranches} />);
  expect(screen.getByText("0 active branches")).toBeInTheDocument();
  const extended = structuredClone(execution(concurrent).activeBranches);
  extended[0].path = Array.from({ length: 10 }, (_, index) => ({ frameId: `long-${index}`, frameType: "SKILL", route: `route-${index}` }));
  rerender(<ActiveBranches branches={extended} />);
  expect(screen.getByText("2 active branches")).toBeInTheDocument();
  fireEvent.click(screen.getByText(/Branch 1:/));
  const path = screen.getByRole("list", { name: "Complete path for branch 1" });
  expect(within(path).getAllByRole("listitem")).toHaveLength(10);
  expect(screen.getAllByText((text) => text.includes(`plan ${extended[0].planId}`))).toHaveLength(2);
  expect(screen.queryByText("…")).not.toBeInTheDocument();
});

test.each([
  ["concurrent siblings", concurrent, true],
  ["equal group in different plans", crossPlan, false],
  ["repeated leaves for one task", repeatedTask, false],
] as const)("labels simultaneous members exactly for %s", (_name, fixture, expected) => {
  const { unmount } = render(<ActiveBranches branches={execution(fixture).activeBranches} />);
  const claims = screen.queryAllByText(/Simultaneously active group member/);
  expect(claims.length > 0).toBe(expected);
  unmount();
});

test("does not label an ineligible branch in an otherwise simultaneous group", () => {
  const branches = structuredClone(execution(concurrent).activeBranches);
  branches.push({
    ...structuredClone(branches[0]),
    taskId: "task-ineligible",
    effectiveConcurrency: false,
    path: [{ frameId: "ineligible-leaf", frameType: "STEP_EXECUTION", route: "ineligible" }],
  });

  render(<ActiveBranches branches={branches} />);
  const ineligible = screen.getByText("Branch 3: ineligible").closest("details");
  expect(ineligible).not.toBeNull();
  fireEvent.click(screen.getByText("Branch 3: ineligible"));
  expect(within(ineligible!).getByText(/concurrent execution disabled/)).toBeInTheDocument();
  expect(within(ineligible!).queryByText(/Simultaneously active group member/)).toBeNull();
});

test("renders hostile assignment strings inertly", () => {
  const hostile = "</script><img onerror=alert(1)>";
  render(<ActiveBranches branches={[{ planId: hostile, taskId: hostile, stepNumber: 1, parallelGroup: hostile, effectiveConcurrency: true, path: [{ frameId: "leaf", frameType: "SKILL", route: hostile }] }]} />);
  expect(screen.getAllByText((text) => text.includes(hostile)).length).toBeGreaterThan(0);
  expect(document.querySelector("script")).toBeNull();
  expect(document.querySelector("img")).toBeNull();
  expect(document.querySelector("a")).toBeNull();
});
