import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter, useLocation, useSearchParams } from "react-router";
import { beforeEach, expect, test, vi } from "vitest";

const api = vi.hoisted(() => ({
  BrowserAPIError: class BrowserAPIError extends Error { constructor(readonly code: string, message: string) { super(message); } },
  getTraceAnalysisSummary: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", traceId: "trace-1", sessionId: "session-1", outcome: "FAILED", terminalFailureId: null, recordCount: 1, frameCount: 1, rootFrameIds: ["f-1"], usageComplete: false }),
  getTraceFrames: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [{ frameId: "f-1", parentFrameId: null, childFrameIds: [], frameType: "SKILL", route: "hello", inclusiveDurationMillis: null, selfDurationMillis: null, directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true, descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsageComplete: true, inclusiveUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsageComplete: true, outcome: null, attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [] }], hasMore: false, nextCursor: null }),
  getTraceRecords: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 1, type: "PAYLOAD", frameId: "f-1", route: "hello", timestampMillis: 1, representation: "LOGICAL", content: { role: "RECONSTRUCTED", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "p-1" } }], hasMore: false, nextCursor: null }),
  getTracePlans: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceUsage: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", attributed: { totalUnits: 4 } }),
  getTraceAttempts: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceRetries: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceFailures: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceValidationLinks: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  listSkills: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceGaps: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getTraceUncertainties: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  searchTraceEvidence: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: false, nextCursor: null }),
  getContentRange: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: 4, totalLength: 4, contentType: "text/plain", encoding: "TEXT", content: "<a>x</a>", hasMore: false, nextCursor: null }),
  getRawRecordRange: vi.fn().mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: 2, totalLength: 2, contentType: "application/x-ndjson", encoding: "TEXT", content: "{}", hasMore: false, nextCursor: null }),
}));
const targetState = vi.hoisted(() => ({ target: { status: { source: "TARGET", targetScopeId: "scope-1" } }, scopeGeneration: 0, refresh: vi.fn() }));
vi.mock("../api/client", () => api);
vi.mock("../target/TargetProvider", () => ({ useTarget: () => targetState }));

import { TraceExplorer } from "./TraceExplorer";
import plansFixture from "../../../browser-fixtures/trace-analysis/plans.json";

beforeEach(() => {
  vi.clearAllMocks();
  targetState.target.status.targetScopeId = "scope-1";
	targetState.scopeGeneration = 0;
});

function LocationProbe() { const location = useLocation(); return <output aria-label="location">{location.search}</output>; }

function TransitionSelectionProbe({ sequence }: { sequence: number }) {
  const [params, setParams] = useSearchParams();
  return <button type="button" onClick={() => {
    const next = new URLSearchParams(params);
    next.set("transitionSequence", String(sequence));
    setParams(next);
  }}>Select transition {sequence}</button>;
}

test("loads timeline and deliberately reads inert evidence", async () => {
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  expect(api.getContentRange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  const recordRow = await screen.findByRole("row", { name: "Record 1, PAYLOAD" });
  fireEvent.click(screen.getByRole("button", { name: "Read content" }));
  await screen.findByText("<a>x</a>");
  const contentRegion = screen.getByRole("region", { name: "Content for record 1" });
  expect(contentRegion).toHaveFocus();
  expect(contentRegion.closest("tr")?.previousElementSibling).toBe(recordRow);
  expect(screen.getByRole("button", { name: "Hide content" })).toHaveAttribute("aria-expanded", "true");
  expect(screen.queryByRole("link", { name: "x" })).toBeNull();
  fireEvent.click(screen.getByRole("tab", { name: "Usage" }));
  await screen.findByLabelText("Usage facts");
});

test("uses compact frame duration in records", async () => {
  api.getTraceFrames.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ frameId: "f-1", parentFrameId: null, childFrameIds: [], frameType: "SKILL", route: "hello", inclusiveDurationMillis: 75_432, selfDurationMillis: null }], hasMore: false, nextCursor: null });
  api.getTraceRecords.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 1, type: "STEP_COMPLETED", frameId: "f-1", route: "hello", timestampMillis: 1, representation: "LOGICAL" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);

  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  expect(await screen.findByRole("row", { name: "Record 1, STEP_COMPLETED" })).toHaveTextContent("1m 15.4s");
});

test("shows developer-relevant records by default and can load all records", async () => {
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);

  await screen.findByRole("row", { name: "Record 1, PAYLOAD" });
  const initialFilter = api.getTraceRecords.mock.calls[0][2];
  expect(initialFilter.types).toContain("MODEL_REQUEST_SENT");
  expect(initialFilter.types).toContain("TOOL_CALL_FAILED");
  expect(initialFilter.types).toContain("TRACE_COMPLETED");
  expect(initialFilter.types).not.toContain("FRAME_OPENED");
  expect(initialFilter.types).not.toContain("STEP_ACTION_VALIDATED");

  const toggle = screen.getByRole("checkbox", { name: "Show all records" });
  expect(toggle).not.toBeChecked();
  fireEvent.click(toggle);

  await vi.waitFor(() => expect(api.getTraceRecords).toHaveBeenCalledTimes(2));
  expect(api.getTraceRecords).toHaveBeenLastCalledWith("trace-1", undefined, {}, "TARGET");
  expect(toggle).toBeChecked();
});

test("loads plans lazily, displays assigned frames as text, and synchronizes record selection", async () => {
  api.getTracePlans.mockResolvedValueOnce({ ...plansFixture, source: "TARGET", targetScopeId: "scope-1" });
  const framePage = {
    source: "TARGET", targetScopeId: "scope-1", hasMore: false, nextCursor: null,
    items: [
      { frameId: "f-1", parentFrameId: null, childFrameIds: ["frame-1"], frameType: "SKILL", route: "hello", inclusiveDurationMillis: null, selfDurationMillis: null, directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true, descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsageComplete: true, inclusiveUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsageComplete: true, outcome: null, attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [] },
      { frameId: "frame-1", parentFrameId: "f-1", childFrameIds: [], frameType: "STEP_EXECUTION", route: "step", inclusiveDurationMillis: null, selfDurationMillis: null, directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true, descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsageComplete: true, inclusiveUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsageComplete: true, outcome: null, attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [] },
    ],
  };
  api.getTraceFrames.mockResolvedValue(framePage);
  api.getTraceRecords
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", hasMore: false, nextCursor: null, items: [{ sequence: 8, type: "PLAN_UPDATED", frameId: "planning-frame", route: "plan", timestampMillis: 8, representation: "LOGICAL" }] });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  expect(api.getTracePlans).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("tab", { name: "Plans" }));
  expect(await screen.findByRole("heading", { name: "planner</script> plan" })).toBeInTheDocument();
  expect(api.getTracePlans).toHaveBeenCalledWith("trace-1", undefined, undefined, "TARGET");
  expect(screen.getByText("frame-1").tagName).toBe("DD");
  expect(screen.queryByRole("button", { name: "frame-1" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Record 8" }));
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=planning-frame"));
  expect(screen.getByLabelText("location")).toHaveTextContent("view=records");
  expect(screen.getByLabelText("location")).toHaveTextContent("recordSequence=8");
});

test("plan update records open and focus the exact authoritative transition", async () => {
  api.getTraceRecords.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", hasMore: false, nextCursor: null, items: [{ sequence: 9, type: "PLAN_UPDATED", frameId: "planning-frame", route: "plan", timestampMillis: 9, representation: "LOGICAL", plan: { planId: "plan-1" } }] });
  api.getTracePlans.mockResolvedValueOnce({ ...plansFixture, source: "TARGET", targetScopeId: "scope-1" });
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);

  const action = await screen.findByRole("button", { name: "View plan change plan-1 from record 9" });
  fireEvent.keyDown(action, { key: "Enter" });
  fireEvent.click(action);

  await vi.waitFor(() => expect(api.getTracePlans).toHaveBeenCalledWith("trace-1", undefined, undefined, "TARGET"));
  expect(screen.getByLabelText("location")).toHaveTextContent("view=plans");
  expect(screen.getByLabelText("location")).toHaveTextContent("planId=plan-1");
  expect(screen.getByLabelText("location")).toHaveTextContent("transitionSequence=9");
  const transition = (await screen.findByRole("button", { name: "Record 9" })).closest("li")!;
  await vi.waitFor(() => expect(transition).toHaveFocus());
  expect(transition).toHaveAttribute("aria-current", "true");
  expect(api.getRawRecordRange).not.toHaveBeenCalled();
  expect(api.getContentRange).not.toHaveBeenCalled();
});

test("plan creation records open and focus the exact authoritative plan", async () => {
  api.getTraceRecords.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", hasMore: false, nextCursor: null, items: [{ sequence: 7, type: "PLAN_CREATED", frameId: "planning-frame", route: "plan", timestampMillis: 7, representation: "LOGICAL", plan: { planId: "plan-1" } }] });
  const other = { ...structuredClone(plansFixture.items[0]), planId: "other", capabilityName: "other-planner", creationSequence: 20 };
  api.getTracePlans.mockResolvedValueOnce({ ...plansFixture, items: [...plansFixture.items, other], source: "TARGET", targetScopeId: "scope-1" });
  render(<MemoryRouter initialEntries={["/?view=records&planId=old-plan&transitionSequence=9"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);

  fireEvent.click(await screen.findByRole("button", { name: "View finalized plan plan-1 from record 7" }));

  await vi.waitFor(() => expect(api.getTracePlans).toHaveBeenCalledWith("trace-1", undefined, undefined, "TARGET"));
  const plan = (await screen.findByRole("heading", { name: "planner</script> plan" })).closest("article")!;
  await vi.waitFor(() => expect(plan).toHaveFocus());
  expect(plan).toHaveAttribute("aria-current", "true");
  expect(screen.getByRole("heading", { name: "other-planner plan" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  fireEvent.click(await screen.findByRole("button", { name: "View finalized plan plan-1 from record 7" }));
  expect(screen.getByRole("heading", { name: "other-planner plan" })).toBeInTheDocument();
  expect(api.getTracePlans).toHaveBeenCalledTimes(1);
  expect(screen.getByLabelText("location")).toHaveTextContent("view=plans");
  expect(screen.getByLabelText("location")).toHaveTextContent("planId=plan-1");
  expect(screen.getByLabelText("location")).not.toHaveTextContent("transitionSequence");
});

test("direct exact plan URLs focus the requested plan and clear unavailable transitions", async () => {
  api.getTracePlans.mockResolvedValue({ ...plansFixture, source: "TARGET", targetScopeId: "scope-1" });
  render(<MemoryRouter initialEntries={["/?view=plans&planId=plan-1&transitionSequence=99"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);

  await vi.waitFor(() => expect(api.getTracePlans).toHaveBeenCalledWith("trace-1", undefined, undefined, "TARGET"));
  const plan = (await screen.findByRole("heading", { name: "planner</script> plan" })).closest("article")!;
  await vi.waitFor(() => expect(plan).toHaveFocus());
  expect(plan).toHaveAttribute("aria-current", "true");
  await vi.waitFor(() => expect(screen.getByLabelText("location")).not.toHaveTextContent("transitionSequence"));
  expect(screen.getByLabelText("location")).toHaveTextContent("planId=plan-1");
});

test("same-plan URL changes validate the current transition without refetching", async () => {
  api.getTracePlans.mockResolvedValue({ ...plansFixture, source: "TARGET", targetScopeId: "scope-1" });
  render(<MemoryRouter initialEntries={["/?view=plans&planId=plan-1&transitionSequence=9"]}><TraceExplorer traceId="trace-1" /><LocationProbe /><TransitionSelectionProbe sequence={99} /></MemoryRouter>);

  const transition = (await screen.findByRole("button", { name: "Record 9" })).closest("li")!;
  await vi.waitFor(() => expect(transition).toHaveFocus());
  fireEvent.click(screen.getByRole("button", { name: "Select transition 99" }));

  await vi.waitFor(() => expect(screen.getByLabelText("location")).not.toHaveTextContent("transitionSequence"));
  expect(screen.getByLabelText("location")).toHaveTextContent("planId=plan-1");
  expect(api.getTracePlans).toHaveBeenCalledTimes(1);
});

test("loads a plan failure and focuses the existing failure panel", async () => {
  api.getTracePlans.mockResolvedValueOnce({ ...plansFixture, source: "TARGET", targetScopeId: "scope-1" });
  api.getTraceFailures.mockResolvedValueOnce({
    source: "TARGET",
    targetScopeId: "scope-1",
    items: [{ failureId: "failure-1", terminal: false, sequence: 9, frameId: "f-1" }],
    hasMore: false,
    nextCursor: null,
  });
  render(<MemoryRouter initialEntries={["/?view=plans"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "failure-1" }));
  const panel = await screen.findByLabelText("Trace failure details");
  await vi.waitFor(() => expect(panel).toHaveFocus());
});

test("loads all plan pages and focuses a later plan without hiding earlier plans", async () => {
  const first = structuredClone(plansFixture.items[0]);
  const second = { ...structuredClone(plansFixture.items[0]), planId: "plan-2", capabilityName: "second-planner", creationSequence: 20 };
  api.getTracePlans
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [first], hasMore: true, nextCursor: "plans-next" })
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [second], hasMore: false, nextCursor: null });
  render(<MemoryRouter initialEntries={["/?view=plans&planId=plan-2"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByRole("heading", { name: "planner</script> plan" });
  const selected = (await screen.findByRole("heading", { name: "second-planner plan" })).closest("article")!;
  await vi.waitFor(() => expect(selected).toHaveFocus());
  expect(screen.queryByRole("button", { name: "Load more plans" })).toBeNull();
  expect(api.getTracePlans).toHaveBeenLastCalledWith("trace-1", "plans-next", undefined, "TARGET");
  expect(screen.getAllByRole("article").map((article) => article.querySelector("h4")?.textContent)).toEqual(["planner</script> plan", "second-planner plan"]);
});

test("ignores stale plan failures after the trace changes", async () => {
  let rejectStale: ((reason: Error) => void) | undefined;
  const current = { ...structuredClone(plansFixture.items[0]), planId: "plan-2", capabilityName: "current-planner", creationSequence: 20 };
  api.getTracePlans
    .mockImplementationOnce(() => new Promise((_resolve, reject) => { rejectStale = reject; }))
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [current], hasMore: false, nextCursor: null });
  const view = render(<MemoryRouter initialEntries={["/?view=plans"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await vi.waitFor(() => expect(api.getTracePlans).toHaveBeenCalledWith("trace-1", undefined, undefined, "TARGET"));

  view.rerender(<MemoryRouter initialEntries={["/?view=plans"]}><TraceExplorer traceId="trace-2" /></MemoryRouter>);
  expect(await screen.findByRole("heading", { name: "current-planner plan" })).toBeInTheDocument();
  rejectStale?.(new Error("stale plan failure"));

  await vi.waitFor(() => expect(screen.queryByText("stale plan failure")).toBeNull());
  expect(screen.getByRole("heading", { name: "current-planner plan" })).toBeInTheDocument();
});

test("loads the full timeline by default without a separate hierarchy tab", async () => {
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  expect(await screen.findByRole("tree", { name: "Trace timeline" })).toBeInTheDocument();
  expect(screen.queryByRole("tab", { name: "Hierarchy" })).toBeNull();
  expect(screen.getByRole("tab", { name: "Timeline" })).toHaveAttribute("aria-selected", "true");
  expect(api.getTraceFrames).toHaveBeenCalledWith("trace-1", undefined, {}, "CANONICAL", "TARGET", "DETAILED", 1000);
});

test("timeline replaces selected-frame detail with every canonical frame page", async () => {
  const frame = (frameId: string, route: string, openedTimestampMillis: number) => ({
    frameId, parentFrameId: null, childFrameIds: [], frameType: "SKILL", route,
    openedTimestampMillis, closedTimestampMillis: openedTimestampMillis + 5,
    inclusiveDurationMillis: 5, selfDurationMillis: 5,
    directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true,
    descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsageComplete: true,
    inclusiveUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsageComplete: true,
    skillNames: [], outcome: "completed", attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [],
  });
  const one = frame("frame-1", "one", 1);
  const two = frame("frame-2", "two", 10);
  const page = (items: unknown[], hasMore = false, nextCursor: string | null = null) => ({ source: "TARGET", targetScopeId: "scope-1", items, hasMore, nextCursor });
  api.getTraceFrames
    .mockResolvedValueOnce(page([one, two]))
    .mockResolvedValueOnce(page([one]))
    .mockResolvedValueOnce(page([one], true, "timeline-next"))
    .mockResolvedValueOnce(page([two]));
  render(<MemoryRouter initialEntries={["/?view=plans&frameId=frame-1"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);

  await vi.waitFor(() => expect(api.getTraceFrames).toHaveBeenCalledWith("trace-1", undefined, { frameIds: ["frame-1"] }, "CANONICAL", "TARGET", "DETAILED"));
  fireEvent.click(screen.getByRole("tab", { name: "Timeline" }));
  await screen.findByRole("button", { name: "two" });
  expect(api.getTraceFrames).toHaveBeenCalledWith("trace-1", undefined, {}, "CANONICAL", "TARGET", "DETAILED", 1000);
  expect(api.getTraceFrames).toHaveBeenCalledWith("trace-1", "timeline-next", {}, "CANONICAL", "TARGET", "DETAILED", 1000);
  expect(within(screen.getByLabelText("Trace timeline")).getByRole("button", { name: "one" }).closest(".trace-timeline-row")).toHaveAttribute("aria-current", "true");
  expect(screen.queryByRole("button", { name: "Load more timeline frames" })).toBeNull();
});

test("usage operations deep-link to and focus their exact model response record", async () => {
  const rootFrame = { frameId: "root", parentFrameId: null, childFrameIds: ["model-frame"], frameType: "SKILL", route: "handleIncident", openedTimestampMillis: 1, closedTimestampMillis: 30, inclusiveDurationMillis: 29, selfDurationMillis: 1, directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true, descendantUsage: { promptUnits: 2, completionUnits: 2, totalUnits: 4 }, descendantUsageComplete: true, inclusiveUsage: { promptUnits: 2, completionUnits: 2, totalUnits: 4 }, inclusiveUsageComplete: true, outcome: null, attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [] };
  const modelFrame = { ...rootFrame, frameId: "model-frame", parentFrameId: "root", childFrameIds: [], frameType: "MODEL_CALL", route: "handleIncident#planning-model", openedTimestampMillis: 10, closedTimestampMillis: 20, directUsage: { promptUnits: 2, completionUnits: 2, totalUnits: 4 }, descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsage: { promptUnits: 2, completionUnits: 2, totalUnits: 4 } };
  const response = { sequence: 23, type: "MODEL_RESPONSE_RECEIVED", frameId: "model-frame", route: modelFrame.route, timestampMillis: 20, representation: "LOGICAL", content: { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "response-payload" } };
  const page = (items: unknown[], hasMore = false, nextCursor: string | null = null) => ({ source: "TARGET", targetScopeId: "scope-1", items, hasMore, nextCursor });
  api.getTraceFrames.mockResolvedValueOnce(page([rootFrame])).mockResolvedValueOnce(page([modelFrame]));
  api.getTraceUsage.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", attributed: modelFrame.directUsage, unattributed: rootFrame.directUsage, unframedAttributed: rootFrame.directUsage, terminal: modelFrame.directUsage });
  api.getTraceRecords.mockResolvedValueOnce(page([], true, "responses-next")).mockResolvedValueOnce(page([response])).mockResolvedValueOnce(page([response]));

  render(<MemoryRouter initialEntries={["/?view=usage"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  const operation = await screen.findByRole("link", { name: "handleIncident · planning · model" });
  fireEvent.click(operation);

  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("view=records"));
  expect(screen.getByLabelText("location")).toHaveTextContent("frameId=model-frame");
  expect(screen.getByLabelText("location")).toHaveTextContent("recordSequence=23");
  const selectedResponse = await screen.findByRole("row", { name: "Record 23, MODEL_RESPONSE_RECEIVED" });
  await vi.waitFor(() => expect(selectedResponse).toHaveFocus());
  expect(api.getTraceRecords).toHaveBeenCalledWith("trace-1", undefined, { types: ["MODEL_RESPONSE_RECEIVED"] }, "TARGET");
  expect(api.getTraceRecords).toHaveBeenCalledWith("trace-1", "responses-next", { types: ["MODEL_RESPONSE_RECEIVED"] }, "TARGET");
});

test("continues a finite payload range using the returned opaque cursor", async () => {
  api.getContentRange.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: 2, totalLength: 4, contentType: "text/plain", encoding: "TEXT", content: "one", hasMore: true, nextCursor: "next-1" }).mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", actualStart: 2, actualEnd: 4, totalLength: 4, contentType: "text/plain", encoding: "TEXT", content: "two", hasMore: false, nextCursor: null });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  await screen.findByRole("row", { name: "Record 1, PAYLOAD" });
  fireEvent.click(screen.getByRole("button", { name: "Read content" }));
  await screen.findByText("one");
  fireEvent.click(screen.getByRole("button", { name: "Read next range" }));
  await screen.findByText("two");
  expect(api.getContentRange).toHaveBeenLastCalledWith("trace-1", "p-1", "next-1", "TARGET");
});

test("artifact expiration during a range clears stale content and reports the precise error", async () => {
  api.getContentRange.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: 2, totalLength: 4, contentType: "text/plain", encoding: "TEXT", content: "one", hasMore: true, nextCursor: "next-1" }).mockRejectedValueOnce(new api.BrowserAPIError("ARTIFACT_EXPIRED", "The local artifact expired."));
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  await screen.findByRole("row", { name: "Record 1, PAYLOAD" });
  fireEvent.click(screen.getByRole("button", { name: "Read content" }));
  await screen.findByText("one");
  fireEvent.click(screen.getByRole("button", { name: "Read next range" }));
  await screen.findByText("The local artifact expired.");
  expect(screen.queryByText("one")).toBeNull();
});

test("search is deliberate and resolves page-local content descriptors", async () => {
  api.searchTraceEvidence.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 1, recordType: "MODEL_RESPONSE_RECEIVED", frameId: "f-1", matchOffset: 2, matchLength: 4, searchedField: "payload", contentId: "c1" }], contentDescriptors: [{ contentId: "c1", contentRef: "opaque-match-content" }], search: { workComplete: true, caseSensitive: true, searchedFields: ["payload"], limitations: [] }, hasMore: false, nextCursor: null });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  await screen.findByRole("heading", { name: "Records" });
  expect(api.searchTraceEvidence).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText("Literal search"), { target: { value: "needle" } });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  await screen.findByText("1 literal matches");
  fireEvent.click(screen.getByRole("button", { name: "MODEL_RESPONSE_RECEIVED record 1" }));
  expect(screen.getByRole("region", { name: "Literal search results" })).toHaveTextContent("payload bytes 2–6");
  fireEvent.click(screen.getByRole("button", { name: "Read match content" }));
  await vi.waitFor(() => expect(api.getContentRange).toHaveBeenCalledWith("trace-1", "opaque-match-content", undefined, "TARGET"));
  expect(api.searchTraceEvidence).toHaveBeenCalledWith("trace-1", "needle", undefined, "TARGET");
  fireEvent.click(screen.getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("region", { name: "Literal search results" })).toBeNull();
  expect(screen.getByLabelText("Literal search")).toHaveValue("needle");
});

test("rebases repeated page-local content IDs across search continuation pages", async () => {
  const search = { workComplete: false, caseSensitive: true, searchedFields: ["content"], limitations: [] };
  api.searchTraceEvidence
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 1, recordType: "STRUCTURED_OUTPUT_RECORDED", matchOffset: 0, matchLength: 6, searchedField: "content", contentId: "c1" }], contentDescriptors: [{ contentId: "c1", contentRef: "first-page-content" }], search, hasMore: true, nextCursor: "search-next" })
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 1, recordType: "STRUCTURED_OUTPUT_RECORDED", matchOffset: 14, matchLength: 6, searchedField: "content", contentId: "c1" }], contentDescriptors: [{ contentId: "c1", contentRef: "second-page-content" }], search: { ...search, workComplete: true }, hasMore: false, nextCursor: null });

  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  fireEvent.change(screen.getByLabelText("Literal search"), { target: { value: "needle" } });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  fireEvent.click(await screen.findByRole("button", { name: "Load more matches" }));

  await vi.waitFor(() => expect(screen.getAllByRole("button", { name: "Read match content" })).toHaveLength(2));
  const reads = screen.getAllByRole("button", { name: "Read match content" });
  fireEvent.click(reads[0]);
  await vi.waitFor(() => expect(api.getContentRange).toHaveBeenCalledWith("trace-1", "first-page-content", undefined, "TARGET"));
  await screen.findByText("<a>x</a>");
  fireEvent.click(screen.getAllByRole("button", { name: "Read match content" })[1]);
  await vi.waitFor(() => expect(api.getContentRange).toHaveBeenCalledWith("trace-1", "second-page-content", undefined, "TARGET"));
});

test("continues record and literal-search pages with their returned cursors", async () => {
  const page = (items: unknown[], hasMore: boolean, nextCursor: string | null) => ({ source: "TARGET", targetScopeId: "scope-1", items, hasMore, nextCursor });
  api.getTraceRecords.mockResolvedValueOnce(page([{ sequence: 1, type: "PAYLOAD", frameId: "f-1", route: "hello", timestampMillis: 1, representation: "LOGICAL", content: { role: "RECONSTRUCTED", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "p-1" } }], true, "records-next")).mockResolvedValueOnce(page([{ sequence: 2, type: "EVENT", frameId: "f-2", route: "next", timestampMillis: 2, representation: "LOGICAL",  }], false, null));
  api.searchTraceEvidence.mockResolvedValueOnce({ ...page([], true, "search-next"), contentDescriptors: [], search: { workComplete: false, caseSensitive: true, searchedFields: ["metadata", "content"], limitations: [] } }).mockResolvedValueOnce({ ...page([], false, null), contentDescriptors: [], search: { workComplete: true, caseSensitive: true, searchedFields: ["metadata", "content"], limitations: [] } });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  await screen.findByRole("button", { name: "Load more records" });
  fireEvent.click(screen.getByRole("button", { name: "Load more records" }));
  await screen.findByRole("row", { name: "Record 2, EVENT" });
  const recordFilter = api.getTraceRecords.mock.calls.at(-1)?.[2];
  expect(api.getTraceRecords).toHaveBeenLastCalledWith("trace-1", "records-next", recordFilter, "TARGET");
  expect(recordFilter.types).toContain("MODEL_RESPONSE_RECEIVED");
  expect(recordFilter.types).not.toContain("FRAME_CLOSED");
  fireEvent.change(screen.getByLabelText("Literal search"), { target: { value: "needle" } });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  await screen.findByRole("button", { name: "Load more matches" });
  fireEvent.click(screen.getByRole("button", { name: "Load more matches" }));
  await vi.waitFor(() => expect(api.searchTraceEvidence).toHaveBeenLastCalledWith("trace-1", "needle", "search-next", "TARGET"));
});

test("invalid frame record failure view and plan parameters are removed", async () => {
  render(<MemoryRouter initialEntries={["/?view=invalid&recordSequence=zero&frameId=f&failureId=x&planId=plan-1&transitionSequence=zero"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  expect(screen.getByLabelText("location")).toHaveTextContent("");
});

test("response scope mismatch clears explorer selection and refreshes target", async () => {
  api.getTraceAnalysisSummary.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-old" });
  render(<MemoryRouter initialEntries={["/?view=records&frameId=f-1&recordSequence=1&failureId=bad"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await vi.waitFor(() => expect(targetState.refresh).toHaveBeenCalled());
  await vi.waitFor(() => expect(screen.getByLabelText("location")).not.toHaveTextContent("frameId"));
  expect(screen.getByLabelText("location")).not.toHaveTextContent("recordSequence");
  expect(screen.getByLabelText("location")).not.toHaveTextContent("failureId");
});

test("imported evidence does not reset or refetch when the target rotates", async () => {
	api.getTraceAnalysisSummary.mockResolvedValueOnce({ source: "IMPORTED", traceId: "trace-1", sessionId: "session-1", outcome: "FAILED", terminalFailureId: null, recordCount: 1, frameCount: 1, rootFrameIds: ["f-1"], usageComplete: false });
	api.getTraceFrames.mockResolvedValueOnce({ source: "IMPORTED", items: [{ frameId: "f-1", parentFrameId: null, childFrameIds: [], frameType: "SKILL", route: "hello", inclusiveDurationMillis: null, selfDurationMillis: null, directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, directUsageComplete: true, descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsageComplete: true, inclusiveUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, inclusiveUsageComplete: true, outcome: null, attemptIds: [], retrySequenceIds: [], validationStatuses: [], failureIds: [] }], hasMore: false, nextCursor: null });
	const view = render(<MemoryRouter><TraceExplorer traceId="trace-1" source="IMPORTED" /></MemoryRouter>);
	await screen.findByText(/FAILED/);
	expect(api.getTraceAnalysisSummary).toHaveBeenCalledTimes(1);
	expect(api.getTraceFrames).toHaveBeenCalledTimes(2);

	targetState.target.status.targetScopeId = "scope-2";
	targetState.scopeGeneration = 1;
	view.rerender(<MemoryRouter><TraceExplorer traceId="trace-1" source="IMPORTED" /></MemoryRouter>);
	await vi.waitFor(() => expect(screen.getByText(/FAILED/)).toBeInTheDocument());
	expect(api.getTraceAnalysisSummary).toHaveBeenCalledTimes(1);
	expect(api.getTraceFrames).toHaveBeenCalledTimes(2);
});

test("records omit detached fact indexes and do not load their collections", async () => {
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  fireEvent.click(screen.getByRole("tab", { name: "Records" }));
  await screen.findByRole("heading", { name: "Records" });
  expect(screen.queryByRole("heading", { name: "Attempts, retries, and validation" })).toBeNull();
  expect(screen.queryByRole("heading", { name: "Failures and uncertainty" })).toBeNull();
  expect(screen.queryByRole("heading", { name: "Payloads" })).toBeNull();
  expect(api.getTraceAttempts).not.toHaveBeenCalled();
  expect(api.getTraceRetries).not.toHaveBeenCalled();
  expect(api.getTraceValidationLinks).not.toHaveBeenCalled();
  expect(api.getTraceGaps).not.toHaveBeenCalled();
  expect(api.getTraceUncertainties).not.toHaveBeenCalled();
});

test("failure deep links continue pages until the selected fact is found", async () => {
  api.getTraceFailures
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [], hasMore: true, nextCursor: "failures-next" })
    .mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "failure-late", terminal: false }], hasMore: false, nextCursor: null });
  render(<MemoryRouter initialEntries={["/?view=records&failureId=failure-late"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  await screen.findByRole("heading", { name: "Recovered failure" });
  expect(api.getTraceFailures).toHaveBeenLastCalledWith("trace-1", "failures-next", "TARGET");
});

test("failure selection follows its linked frame", async () => {
  api.getTraceFailures.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "failure-1", terminal: false, sequence: 2, frameId: "f-1" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter initialEntries={["/?failureId=failure-1"]}><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("failureId=failure-1"));
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=f-1"));
});

test("frame selection follows its first linked failure", async () => {
  api.getTraceFrames.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ frameId: "f-1", parentFrameId: null, childFrameIds: [], frameType: "SKILL", route: "hello", inclusiveDurationMillis: null, selfDurationMillis: null, failureIds: ["failure-1"] }], hasMore: false, nextCursor: null });
  api.getTraceFailures.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "failure-1", terminal: false, sequence: 2, frameId: "f-1" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "hello" }));
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=f-1"));
  expect(screen.getByLabelText("location")).toHaveTextContent("failureId=failure-1");
});

test("a terminal failure does not pin navigation to its frame", async () => {
  api.getTraceAnalysisSummary.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", traceId: "trace-1", sessionId: "session-1", outcome: "FAILED", terminalFailureId: "terminal-1", recordCount: 5, frameCount: 2, rootFrameIds: ["root"], usageComplete: false });
  const framePage = { source: "TARGET", targetScopeId: "scope-1", items: [
    { frameId: "root", parentFrameId: null, childFrameIds: ["model"], frameType: "ROOT", route: "root", inclusiveDurationMillis: 5, selfDurationMillis: 4, failureIds: [] },
    { frameId: "model", parentFrameId: "root", childFrameIds: [], frameType: "MODEL_CALL", route: "model", inclusiveDurationMillis: 1, selfDurationMillis: 1, failureIds: ["terminal-1"] },
  ], hasMore: false, nextCursor: null };
  const detailedPage = { ...framePage, items: framePage.items.map((frame) => ({ ...frame, attemptIds: [], retrySequenceIds: [], validationStatuses: [] })) };
  api.getTraceFrames.mockResolvedValueOnce(framePage).mockResolvedValueOnce(detailedPage);
  api.getTraceFailures.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "terminal-1", terminal: true, sequence: 4, frameId: "model" }], hasMore: false, nextCursor: null });

  render(<MemoryRouter><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=model"));
  fireEvent.click(within(await screen.findByRole("tree")).getByRole("button", { name: "root" }));
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=root"));
  expect(screen.getByLabelText("location")).not.toHaveTextContent("failureId=");
  await new Promise((resolve) => setTimeout(resolve, 0));
  expect(screen.getByLabelText("location")).toHaveTextContent("frameId=root");
});

test("deep frame selection loads complete ancestry for breadcrumbs", async () => {
  const result = (items: unknown[]) => ({ source: "TARGET", targetScopeId: "scope-1", items, hasMore: false, nextCursor: null });
  api.getTraceFrames
    .mockResolvedValueOnce(result([]))
    .mockResolvedValueOnce(result([{ frameId: "leaf", parentFrameId: "middle", childFrameIds: [], frameType: "TOOL", route: "leaf", openedTimestampMillis: 3, closedTimestampMillis: 4, inclusiveDurationMillis: 1, selfDurationMillis: 1 }]))
    .mockResolvedValueOnce(result([{ frameId: "middle", parentFrameId: "root", childFrameIds: ["leaf"], frameType: "SKILL", route: "middle", openedTimestampMillis: 2, closedTimestampMillis: 5, inclusiveDurationMillis: 3, selfDurationMillis: 2 }]))
    .mockResolvedValueOnce(result([{ frameId: "root", parentFrameId: null, childFrameIds: ["middle"], frameType: "ROOT", route: "root", openedTimestampMillis: 1, closedTimestampMillis: 6, inclusiveDurationMillis: 5, selfDurationMillis: 2 }]));
  render(<MemoryRouter initialEntries={["/?view=plans&frameId=leaf"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  const breadcrumbs = await screen.findByRole("navigation", { name: "Selected frame breadcrumbs" });
  expect(breadcrumbs).toHaveTextContent("root / middle / leaf");
});

test("tabs use roving focus and arrow key activation", async () => {
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  await screen.findByText(/FAILED/);
  const first = screen.getByRole("tab", { name: "Timeline" });
  const next = screen.getByRole("tab", { name: "Plans" });
  expect(first).toHaveAttribute("tabindex", "0");
  expect(next).toHaveAttribute("tabindex", "-1");
  first.focus();
  fireEvent.keyDown(first, { key: "ArrowRight" });
  expect(next).toHaveFocus();
  expect(next).toHaveAttribute("aria-selected", "true");
  expect(screen.getByRole("tabpanel")).toHaveAttribute("aria-labelledby", "trace-tab-plans");
});

test("expired local artifact clears explorer state and requests reacquisition", async () => {
  const unavailable = vi.fn();
  api.getTraceAnalysisSummary.mockRejectedValueOnce(new api.BrowserAPIError("ARTIFACT_EXPIRED", "The local artifact expired."));
  render(<MemoryRouter initialEntries={["/?view=records&frameId=f-1&recordSequence=1&failureId=bad"]}><TraceExplorer traceId="trace-1" onArtifactUnavailable={unavailable} /><LocationProbe /></MemoryRouter>);
  await vi.waitFor(() => expect(unavailable).toHaveBeenCalledWith(expect.objectContaining({ code: "ARTIFACT_EXPIRED" })));
  expect(screen.getByLabelText("location")).not.toHaveTextContent("frameId");
  expect(screen.getByLabelText("location")).not.toHaveTextContent("recordSequence");
  expect(screen.queryByText(/FAILED/)).toBeNull();
});

test("failure focus selects the recorded terminal failure and never loads raw payloads", async () => {
  api.getTraceAnalysisSummary.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", traceId: "trace-1", sessionId: "session-1", outcome: "FAILED", terminalFailureId: "terminal-1", recordCount: 120, frameCount: 1, attemptCount: 1, retryCount: 1, validationCount: 1, failureCount: 2, payloadCount: 1, gapCount: 1, uncertaintyCount: 1, rootFrameIds: ["f-1"], usageComplete: false, configuredLimits: null });
  api.getTraceFailures.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "recovered", terminal: false, sequence: 3, timestampMillis: 3, recordType: "ERROR_RECORDED", frameId: "", route: "", attemptId: "", retrySequenceId: "", validationStatus: "" }], hasMore: true, nextCursor: "failure-next" }).mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "terminal-1", terminal: true, sequence: 119, timestampMillis: 119, recordType: "ERROR_RECORDED", frameId: "f-1", route: "hello", attemptId: "a-1", retrySequenceId: "r-1", validationStatus: "exhausted" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter><TraceExplorer traceId="trace-1" /><LocationProbe /></MemoryRouter>);
  expect(await screen.findByRole("heading", { name: "Terminal failure evidence" })).toBeInTheDocument();
  expect(screen.getByRole("region", { name: "Trace failure details" })).toHaveClass("trace-failure-panel");
  await screen.findByText("ERROR_RECORDED sequence 119");
  await vi.waitFor(() => expect(screen.getByLabelText("location")).toHaveTextContent("frameId=f-1"));
  expect(screen.getByText(/does not identify root cause/)).toBeInTheDocument();
  expect(api.getContentRange).not.toHaveBeenCalled();
  expect(api.getRawRecordRange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Show in timeline" }));
  await vi.waitFor(() => expect(within(screen.getByRole("tree")).getByRole("button", { name: "hello" })).toHaveFocus());
});

test("view error from the exact failure record focuses the failure panel", async () => {
  api.getTraceAnalysisSummary.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", traceId: "trace-1", sessionId: "session-1", outcome: "FAILED", terminalFailureId: "terminal-1", recordCount: 15, frameCount: 1, attemptCount: 1, retryCount: 1, validationCount: 0, failureCount: 1, payloadCount: 1, gapCount: 0, uncertaintyCount: 0, rootFrameIds: ["f-1"], usageComplete: false, configuredLimits: null });
  api.getTraceRecords.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ sequence: 15, type: "ERROR_RECORDED", frameId: "f-1", route: "hello", timestampMillis: 15, representation: "LOGICAL", content: { role: "RECONSTRUCTED", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "p-1" } }], hasMore: false, nextCursor: null });
  api.getTraceFailures.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ failureId: "terminal-1", terminal: true, sequence: 15, timestampMillis: 15, recordType: "ERROR_RECORDED", frameId: "f-1", route: "hello", attemptId: "a-1", retrySequenceId: "r-1", validationStatus: "" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);

  fireEvent.click(await screen.findByRole("button", { name: "View error" }));

  expect(screen.getByRole("region", { name: "Trace failure details" })).toHaveFocus();
});

test("selected frames link only exact current registered skill names", async () => {
	const framePage = { source: "TARGET", targetScopeId: "scope-1", items: [{ frameId: "f-1", parentFrameId: null, childFrameIds: [], frameType: "SKILL", route: "root.child", inclusiveDurationMillis: 1, selfDurationMillis: 1, skillNames: ["exact.skill", "Missing.Skill"] }], hasMore: false, nextCursor: null };
	api.getTraceFrames.mockResolvedValueOnce(framePage).mockResolvedValueOnce(framePage);
  api.listSkills.mockResolvedValueOnce({ source: "TARGET", targetScopeId: "scope-1", items: [{ registeredName: "exact.skill", source: "YAML", sourcePath: "<unsafe>" }, { registeredName: "missing.skill", source: "YAML", sourcePath: "other" }], hasMore: false, nextCursor: null });
  render(<MemoryRouter initialEntries={["/?view=plans&frameId=f-1"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  expect(await screen.findByRole("link", { name: "exact.skill" })).toHaveAttribute("href", "/skills/exact.skill?targetScopeId=scope-1");
  expect(screen.getByText("Missing.Skill").closest("li")).toHaveTextContent("not in current registered catalog");
  expect(screen.queryByRole("link", { name: "Missing.Skill" })).toBeNull();
  expect(screen.queryByText("<unsafe>")).toBeNull();
});

const recordFenceCases = ["initial", "exact", "page"].flatMap((path) => ["trace", "source", "scope", "generation"].flatMap((identity) => ["success", "error"].map((outcome) => ({ path, identity, outcome }))));
test.each(recordFenceCases)("fences $path record $outcome after $identity changes", async ({ path, identity, outcome }) => {
  let resolve!: (value: unknown) => void;
  let reject!: (error: Error) => void;
  const oldRequest = new Promise((yes, no) => { resolve = yes; reject = no; });
  let fresh = false;
  let source: "TARGET" | "IMPORTED" = "TARGET";
  const page = (items: unknown[] = [], more = false) => ({ source, targetScopeId: source === "TARGET" ? targetState.target.status.targetScopeId : undefined, items, hasMore: more, nextCursor: more ? "next" : null });
  const record = (note: string, sequence = 7) => ({ sequence, type: "PLAN_UPDATED", frameId: "", route: "", plan: { planId: "plan" }, planUpdate: { previousSequence: 3, availability: "AVAILABLE", changes: [{ field: "note", taskId: "task", before: null, after: note }] } });
  api.getTraceAnalysisSummary.mockImplementation(async () => ({ ...page(), traceId: "trace-1", outcome: "SUCCEEDED", frameCount: 0, recordCount: 2 }));
  api.getTraceFrames.mockImplementation(async () => page());
  api.getTraceFailures.mockImplementation(async () => page());
  api.getTraceRecords.mockImplementation((_trace, cursor, filter) => {
    if (fresh) return Promise.resolve(page([record("current evidence", path === "exact" ? 99 : 7)]));
    if (path === "initial" || path === "exact" && filter.minSequence || path === "page" && cursor) return oldRequest;
    return Promise.resolve(page([record("first evidence")], path === "page"));
  });
  const view = render(<MemoryRouter initialEntries={[`/?view=records${path === "exact" ? "&recordSequence=99" : ""}`]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  if (path === "page") fireEvent.click(await screen.findByRole("button", { name: "Load more records" }));
  await vi.waitFor(() => expect(api.getTraceRecords).toHaveBeenCalledTimes(path === "initial" ? 1 : 2));
  const oldPage = page([record("stale evidence", path === "exact" ? 99 : 20)]);
  fresh = true;
  if (identity === "source") source = "IMPORTED";
  if (identity === "scope") targetState.target.status.targetScopeId = "scope-2";
  if (identity === "generation") targetState.scopeGeneration++;
  view.rerender(<MemoryRouter><TraceExplorer traceId={identity === "trace" ? "trace-2" : "trace-1"} source={source} /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "View diff" }));
  await screen.findByText('"current evidence"');
  await act(async () => { if (outcome === "success") resolve(oldPage); else reject(new Error("stale failure")); });
  expect(screen.queryByText(/stale evidence|stale failure/)).toBeNull();
  expect(screen.getByText('"current evidence"')).toBeInTheDocument();
});

test.each(["ARTIFACT_EXPIRED", "NOT_FOUND"])("invalidates expanded diff and pending continuation on %s", async (code) => {
  let resolve!: (value: unknown) => void;
  const pendingPage = new Promise((yes) => { resolve = yes; });
  const record = { sequence: 7, type: "PLAN_UPDATED", frameId: "", route: "", plan: { planId: "plan" }, planUpdate: { previousSequence: 3, availability: "AVAILABLE", changes: [{ field: "note", taskId: "task", before: null, after: "visible evidence" }] } };
  const page = { source: "TARGET", targetScopeId: "scope-1", items: [record], hasMore: true, nextCursor: "next" };
  api.getTraceAnalysisSummary.mockResolvedValue({ source: "TARGET", targetScopeId: "scope-1", outcome: "SUCCEEDED", frameCount: 0, recordCount: 2 });
  api.getTraceFrames.mockResolvedValue({ ...page, items: [] });
  api.getTraceFailures.mockResolvedValue({ ...page, items: [] });
  api.getTraceRecords.mockResolvedValueOnce(page).mockImplementationOnce(() => pendingPage);
  api.searchTraceEvidence.mockRejectedValueOnce(new api.BrowserAPIError(code, "artifact gone"));
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "View diff" }));
  await screen.findByText('"visible evidence"');
  fireEvent.click(screen.getByRole("button", { name: "Load more records" }));
  fireEvent.change(screen.getByRole("textbox", { name: "Literal search" }), { target: { value: "x" } });
  fireEvent.click(screen.getByRole("button", { name: /^Search$/ }));
  await screen.findByText("artifact gone");
  await act(async () => { resolve(page); });
  expect(screen.queryByText('"visible evidence"')).toBeNull();
  expect(screen.queryByRole("button", { name: "View diff" })).toBeNull();
});

test.each(["TARGET", "IMPORTED"] as const)("clears expanded comparison on mismatched %s record page", async (source) => {
  const envelope = { source, targetScopeId: source === "TARGET" ? "scope-1" : undefined, hasMore: true, nextCursor: "next" };
  const record = { sequence: 7, type: "PLAN_UPDATED", frameId: "", plan: { planId: "plan" }, planUpdate: { previousSequence: 3, availability: "AVAILABLE", changes: [] } };
  api.getTraceAnalysisSummary.mockResolvedValue({ ...envelope, outcome: "SUCCEEDED", frameCount: 0, recordCount: 2 });
  api.getTraceFrames.mockResolvedValue({ ...envelope, items: [] });
  api.getTraceFailures.mockResolvedValue({ ...envelope, items: [] });
  api.getTraceRecords.mockResolvedValueOnce({ ...envelope, items: [record] }).mockResolvedValueOnce({ ...envelope, source: source === "TARGET" ? "IMPORTED" : "TARGET", items: [record] });
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" source={source} /></MemoryRouter>);
  fireEvent.click(await screen.findByRole("button", { name: "View diff" }));
  expect(screen.getByRole("region", { name: "Plan diff for record 7" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Load more records" }));
  await vi.waitFor(() => expect(screen.queryByRole("region", { name: "Plan diff for record 7" })).toBeNull());
  expect(screen.queryByRole("button", { name: "View diff" })).toBeNull();
});

test.each(["TARGET", "IMPORTED"].flatMap((source) => ["ARTIFACT_EXPIRED", "NOT_FOUND"].flatMap((code) => ["raw", "model", "diagnostics"].map((reader) => ({ source, code, reader })))))("invalidates diff on $source $reader evidence $code", async ({ source, code, reader }) => {
  let reject!: (error: Error) => void;
  const request = new Promise((_, no) => { reject = no; });
  const evidenceSource = source as "TARGET" | "IMPORTED";
  const envelope = { source: evidenceSource, targetScopeId: source === "TARGET" ? "scope-1" : undefined, hasMore: false, nextCursor: null };
  const record = { sequence: 7, type: "PLAN_UPDATED", frameId: "", plan: { planId: "plan" }, planUpdate: { previousSequence: 3, availability: "AVAILABLE", changes: [{ taskId: "task", field: "note", before: null, after: "visible evidence" }] } };
  const model = { sequence: 8, type: reader === "diagnostics" ? "MODEL_ATTEMPT_FAILED" : "MODEL_RESPONSE_RECEIVED", frameId: "", content: { contentRef: "model-content" } };
  api.getTraceAnalysisSummary.mockResolvedValue({ ...envelope, outcome: "SUCCEEDED", frameCount: 0, recordCount: 2 });
  api.getTraceFrames.mockResolvedValue({ ...envelope, items: [] });
  api.getTraceFailures.mockResolvedValue({ ...envelope, items: [] });
  api.getTraceRecords.mockResolvedValue({ ...envelope, items: [record, model] });
  if (reader === "raw") api.getRawRecordRange.mockImplementationOnce(() => request);
  else api.getContentRange.mockImplementationOnce(() => request);
  const unavailable = vi.fn();
  render(<MemoryRouter initialEntries={["/?view=records"]}><TraceExplorer traceId="trace-1" source={evidenceSource} onArtifactUnavailable={unavailable} /></MemoryRouter>);
  await screen.findByRole("button", { name: "View diff" });
  fireEvent.click(reader === "raw" ? screen.getAllByRole("button", { name: "Read raw record" })[0] : screen.getByRole("button", { name: reader === "diagnostics" ? "Attempt diagnostics" : "Response" }));
  fireEvent.click(screen.getByRole("button", { name: "View diff" }));
  expect(screen.getByText('"visible evidence"')).toBeInTheDocument();
  await act(async () => { reject(new api.BrowserAPIError(code, "artifact gone")); });
  expect(unavailable).toHaveBeenCalledWith(expect.objectContaining({ code }));
  expect(screen.queryByText('"visible evidence"')).toBeNull();
  expect(screen.queryByRole("button", { name: "View diff" })).toBeNull();
});
