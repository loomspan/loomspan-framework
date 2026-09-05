import { expect, test } from "vitest";
import {
  initialObservabilityState,
  observabilityReducer,
} from "./reducer";
import { BrowserAPIError } from "../api/client";

test("reducer resets to initial state", () => {
  const state = observabilityReducer(
    { ...initialObservabilityState, instance: { status: { foo: "bar" } as never } },
    { type: "reset" },
  );
  expect(state).toEqual(initialObservabilityState);
});

test("reducer handles instance success", () => {
  const status = { instanceId: "test-id" };
  const state = observabilityReducer(initialObservabilityState, {
    type: "instance-success",
    status,
  });
  expect(state.instance).toEqual({ status });
});

test("reducer handles instance error", () => {
  const error = new BrowserAPIError("TARGET_UNAVAILABLE", "fail", 503);
  const state = observabilityReducer(initialObservabilityState, {
    type: "instance-error",
    error,
  });
  expect(state.instance).toEqual({ error });
});

test("reducer instance-loading preserves existing status and sets loading flag", () => {
  const status = { instanceId: "test-id" };
  const stateWithStatus = observabilityReducer(initialObservabilityState, {
    type: "instance-success",
    status,
  });
  const state = observabilityReducer(stateWithStatus, { type: "instance-loading" });
  expect(state.instance).toEqual({ status, loading: true });
});

test("reducer instance-loading on null state sets loading", () => {
  const state = observabilityReducer(initialObservabilityState, { type: "instance-loading" });
  expect(state.instance).toEqual({ loading: true });
});

test("reducer handles skills success with pagination", () => {
  const items = [{ registeredName: "Skill1" }];
  const state = observabilityReducer(initialObservabilityState, {
    type: "skills-success",
    targetScopeId: "scope-1",
    items,
    hasMore: true,
    nextCursor: "cursor-1",
  });
  expect(state.skills.items).toEqual(items);
  expect(state.skills.hasMore).toBe(true);
  expect(state.skills.nextCursor).toBe("cursor-1");
  expect(state.skills.loading).toBe(false);
  expect(state.skills.loaded).toBe(true);
});

test("reducer handles active executions success with resumeCursor", () => {
  const items = [{ sessionId: "s1" }];
  const state = observabilityReducer(initialObservabilityState, {
    type: "active-success",
    targetScopeId: "scope-1",
    items,
    hasMore: false,
    nextCursor: null,
    resumeCursor: "resume-1",
  });
  expect(state.activeExecutions.items).toEqual(items);
  expect(state.activeExecutions.resumeCursor).toBe("resume-1");
  expect(state.activeExecutions.loading).toBe(false);
});

test("active pagination preserves first-page resume cursor and observation time", () => {
  const first = observabilityReducer(initialObservabilityState, {
    type: "active-success",
    targetScopeId: "scope-1",
    items: [{ sessionId: "s1" }],
    hasMore: true,
    nextCursor: "next",
    resumeCursor: "baseline",
    observedAt: "2026-07-27T00:00:00Z",
  });
  const second = observabilityReducer(first, {
    type: "active-success",
    targetScopeId: "scope-1",
    items: [{ sessionId: "s2" }],
    hasMore: false,
    nextCursor: null,
    resumeCursor: null,
    observedAt: "2026-07-27T00:00:01Z",
    append: true,
  });
  expect(second.activeExecutions.resumeCursor).toBe("baseline");
  expect(second.activeExecutions.observedAt).toBe("2026-07-27T00:00:00Z");
});

test("reducer appends items on skills-success with append flag", () => {
  const firstItems = [{ registeredName: "Skill1" }];
  const stateWithFirst = observabilityReducer(initialObservabilityState, {
    type: "skills-success",
    targetScopeId: "scope-1",
    items: firstItems,
    hasMore: true,
    nextCursor: "cursor-1",
  });
  const nextItems = [{ registeredName: "Skill2" }];
  const stateWithSecond = observabilityReducer(stateWithFirst, {
    type: "skills-success",
    targetScopeId: "scope-1",
    items: nextItems,
    hasMore: false,
    nextCursor: null,
    append: true,
  });
  expect(stateWithSecond.skills.items).toEqual([...firstItems, ...nextItems]);
  expect(stateWithSecond.skills.hasMore).toBe(false);
  expect(stateWithSecond.skills.loading).toBe(false);
});

test("reducer active-success without append replaces items", () => {
  const firstItems = [{ sessionId: "s1" }];
  const stateWithFirst = observabilityReducer(initialObservabilityState, {
    type: "active-success",
    targetScopeId: "scope-1",
    items: firstItems,
    hasMore: true,
    nextCursor: "c1",
    resumeCursor: "r1",
  });
  const resumedItems = [{ sessionId: "s2" }];
  const stateAfterResume = observabilityReducer(stateWithFirst, {
    type: "active-success",
    targetScopeId: "scope-1",
    items: resumedItems,
    hasMore: false,
    nextCursor: null,
    resumeCursor: "r2",
    append: false,
  });
  expect(stateAfterResume.activeExecutions.items).toEqual(resumedItems);
  expect(stateAfterResume.activeExecutions.resumeCursor).toBe("r2");
});

test("reducer appends items on traces-success with append flag", () => {
  const firstItems = [{ traceId: "t1" }];
  const stateWithFirst = observabilityReducer(initialObservabilityState, {
    type: "traces-success",
    targetScopeId: "scope-1",
    items: firstItems,
    hasMore: true,
    nextCursor: "c1",
  });
  const nextItems = [{ traceId: "t2" }];
  const stateWithSecond = observabilityReducer(stateWithFirst, {
    type: "traces-success",
    targetScopeId: "scope-1",
    items: nextItems,
    hasMore: false,
    nextCursor: null,
    append: true,
  });
  expect(stateWithSecond.traces.items).toEqual([...firstItems, ...nextItems]);
});

test("reducer handles traces error", () => {
  const error = new BrowserAPIError("NOT_FOUND", "not found", 404);
  const state = observabilityReducer(initialObservabilityState, {
    type: "traces-error",
    error,
  });
  expect(state.traces.error).toBe(error);
  expect(state.traces.loading).toBe(false);
});
