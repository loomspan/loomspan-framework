import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { getRawRecordRange, getTraceRecords } from "../api/client";
import type { TraceRange, TraceRecord } from "../api/contracts";
import { TraceRecords } from "./TraceRecords";

vi.mock("../api/client", () => ({
  getContentRange: vi.fn(),
  getRawRecordRange: vi.fn(),
  getTraceRecords: vi.fn(),
}));

const getRawRecordRangeMock = vi.mocked(getRawRecordRange);
const getTraceRecordsMock = vi.mocked(getTraceRecords);

function record(type = "FRAME_OPENED"): TraceRecord {
  return {
    sequence: 7,
    type,
    frameId: "frame-1",
    parentFrameId: "",
    frameType: "MODEL_CALL",
    route: "skill#model",
    threadName: "worker",
    timestampMillis: 7,
    representation: "LOGICAL",
    isChunk: false,
    isEnvelope: false,

  };
}

function range(content: string, overrides: Partial<TraceRange> = {}): TraceRange {
  return {
    source: "TARGET",
    targetScopeId: "scope-1",
    actualStart: 0,
    actualEnd: content.length,
    totalLength: content.length,
    contentType: "application/json",
    encoding: "TEXT",
    content,
    hasMore: false,
    nextCursor: null,
    ...overrides,
  };
}

function renderRecord(value: TraceRecord, onSelectPlan = vi.fn()) {
  render(<TraceRecords traceId="trace-1" records={[value]} failures={[]} onSelectRecord={vi.fn()} onSelectFailure={vi.fn()} onSelectPlan={onSelectPlan} onContent={vi.fn()} />);
  return onSelectPlan;
}

beforeEach(() => {
  getRawRecordRangeMock.mockReset();
  getTraceRecordsMock.mockReset();
});

test.each([
  ["PLAN_CREATED", true, ["Read raw record", "Read content", "View finalized plan"]],
  ["PLAN_UPDATED", true, ["Read raw record", "Read content", "View diff", "View plan change"]],
  ["PLAN_UPDATED", false, ["Read raw record", "View diff", "View plan change"]],
  ["TOOL_CALL_STARTED", true, ["Read raw record", "Read content", "Tool input"]],
  ["MODEL_RESPONSE_RECEIVED", true, ["Read raw record", "Response"]],
  ["MODEL_ATTEMPT_FAILED", true, ["Read raw record", "Attempt diagnostics"]],
] as const)("orders generic actions before special actions for %s (content: %s)", (type, hasContent, expected) => {
  const value = record(type);
  if (type.startsWith("PLAN_")) value.plan = { planId: "plan-1", capabilityName: "investigateNetwork" };
  if (hasContent) value.content = { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 2, available: true, complete: true, inlineEligibility: true, contentRef: "content-7" };
  renderRecord(value);
  const row = screen.getAllByRole("row")[1];
  expect(within(row).getAllByRole("button").map((button) => button.textContent)).toEqual(expected);
});

test("expands authoritative plan field changes without reading plan content", () => {
  const value = {
    ...record("PLAN_UPDATED"), plan: { planId: "plan-1", capabilityName: "investigateNetwork" },
    planUpdate: { previousSequence: 3, availability: "AVAILABLE" as const, changes: [
      { taskId: "task-1", field: "status" as const, before: "PENDING", after: "IN_PROGRESS" },
      { taskId: "task-1", field: "note" as const, before: null, after: "<img src=x>\nhello" },
    ] },
  };
  renderRecord(value);
  fireEvent.click(screen.getByRole("button", { name: "View diff" }));
  const detail = screen.getByRole("region", { name: "Plan diff for record 7" });
  expect(detail).toHaveTextContent("Skill: investigateNetwork, Plan: plan-1");
  expect(detail).toHaveTextContent("Record 3 → Record 7");
  expect(detail).toHaveTextContent("tasks[task-1].status");
  expect(detail.querySelector("dd")?.textContent).toBe('"PENDING" → "IN_PROGRESS"');
  expect(within(detail).queryByText("Before", { exact: true })).toBeNull();
  expect(within(detail).queryByText("After", { exact: true })).toBeNull();
  expect(detail.querySelector("pre")).toBeNull();
  expect(within(detail).getAllByRole("img", { name: "changed to" })).toHaveLength(2);
  expect(document.querySelector("img")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Hide diff" }));
  expect(screen.queryByRole("region", { name: "Plan diff for record 7" })).toBeNull();
  expect(getRawRecordRangeMock).not.toHaveBeenCalled();
  expect(getTraceRecordsMock).not.toHaveBeenCalled();
});

test("loads all ranges and pretty prints the complete raw record envelope", async () => {
  const rawRecord = JSON.stringify({ traceId: "trace-1", sequence: 7, recordType: "FRAME_OPENED", metadata: { route: "skill#model" }, data: null });
  const split = 38;
  getRawRecordRangeMock
    .mockResolvedValueOnce(range(rawRecord.slice(0, split), { actualEnd: split, totalLength: rawRecord.length, hasMore: true, nextCursor: "next" }))
    .mockResolvedValueOnce(range(rawRecord.slice(split), { actualStart: split, actualEnd: rawRecord.length, totalLength: rawRecord.length }));
  renderRecord(record());

  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));

  const detail = await screen.findByRole("region", { name: "Raw record 7" });
  const output = within(detail).getByText((_, element) => element?.tagName === "PRE");
  expect(output).toHaveTextContent(JSON.stringify(JSON.parse(rawRecord), null, 2), { normalizeWhitespace: false });
  expect(getRawRecordRangeMock).toHaveBeenNthCalledWith(1, "trace-1", 7, undefined, "TARGET");
  expect(getRawRecordRangeMock).toHaveBeenNthCalledWith(2, "trace-1", 7, "next", "TARGET");
});

test("renders validation warning status without reading raw record content", () => {
  const value = record("STRUCTURED_OUTPUT_RECORDED");
  value.validationStatus = "retrying";
  renderRecord(value);

  expect(screen.getByRole("row", { name: "Retry or warning: record 7, STRUCTURED_OUTPUT_RECORDED" })).toHaveClass("trace-record-warning");
  expect(getRawRecordRangeMock).not.toHaveBeenCalled();
});

test("switches from the response view to the raw envelope on the same row", async () => {
  const rawRecord = JSON.stringify({ traceId: "trace-1", sequence: 7, recordType: "MODEL_RESPONSE_RECEIVED", data: { content: "model output" } });
  getRawRecordRangeMock.mockResolvedValue(range(rawRecord));
  const value = record("MODEL_RESPONSE_RECEIVED");
  value.content = { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, inlineContent: JSON.stringify({ content: "model output" }) };
  renderRecord(value);

  fireEvent.click(screen.getByRole("button", { name: "Response" }));
  expect(await screen.findByRole("region", { name: "Model response for record 7" })).toHaveTextContent("model output");
  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));

  const raw = await screen.findByRole("region", { name: "Raw record 7" });
  expect(raw).toHaveTextContent("traceId");
  expect(screen.queryByRole("region", { name: "Model response for record 7" })).toBeNull();
});

test("reports malformed raw JSON without displaying partial content", async () => {
  getRawRecordRangeMock.mockResolvedValue(range("{not-json"));
  renderRecord(record());

  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Raw record could not be displayed");
  expect(screen.queryByText("{not-json")).toBeNull();
});

test("plan creation navigation coexists with generic raw forensics", async () => {
  getRawRecordRangeMock.mockResolvedValue(range(JSON.stringify({ recordType: "PLAN_CREATED", data: { planId: "plan-1", tasks: [{ taskId: "task-1" }] } })));
  const value = record("PLAN_CREATED");
  value.plan = { planId: "plan-1", capabilityName: "investigateNetwork" };
  const onSelectPlan = renderRecord(value);

  fireEvent.click(screen.getByRole("button", { name: "View finalized plan plan-1 from record 7" }));
  expect(onSelectPlan).toHaveBeenCalledWith("plan-1");
  expect(getRawRecordRangeMock).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));
  expect(await screen.findByRole("region", { name: "Raw record 7" })).toHaveTextContent("plan-1");
  expect(getTraceRecordsMock).not.toHaveBeenCalled();
});

test("plan updates navigate by exact plan and record sequence without reading content", () => {
  const value = record("PLAN_UPDATED");
  value.plan = { planId: "</button><img src=x>", capabilityName: "<img src=skill>" };
  value.content = { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "plan-content" };
  const onSelectPlan = renderRecord(value);

  fireEvent.click(screen.getByRole("button", { name: "View plan change </button><img src=x> from record 7" }));

  expect(onSelectPlan).toHaveBeenCalledWith("</button><img src=x>", 7);
  expect(screen.getByRole("button", { name: "Read content" })).toBeInTheDocument();
  expect(getRawRecordRangeMock).not.toHaveBeenCalled();
  expect(getTraceRecordsMock).not.toHaveBeenCalled();
  expect(document.querySelector("img")).toBeNull();
});

test.each(["missing", "limited", "unchanged", "changed"])("renders %s plan comparison evidence distinctly", (kind) => {
  const value = record("PLAN_UPDATED");
  value.plan = { planId: "plan-1", capabilityName: "investigateNetwork" };
  if (kind === "limited") value.planUpdate = { previousSequence: 3, availability: "LIMIT_EXCEEDED" };
  if (kind === "unchanged") value.planUpdate = { previousSequence: 3, availability: "AVAILABLE", changes: [] };
  if (kind === "changed") value.planUpdate = { previousSequence: 3, availability: "AVAILABLE", changes: [
    { field: "status", before: "VALID", after: "STALE" },
    { taskId: "task-z", field: "status", before: "IN_PROGRESS", after: "FAILED" },
    { taskId: "task-z", field: "note", before: null, after: "null" },
    { taskId: "task-a", field: "note", before: "old", after: "<script>alert(1)</script>\n" + "long ".repeat(1000) },
  ] };
  value.content = { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, inlineContent: "not a plan", contentRef: "ref" };
  const onSelectPlan = renderRecord(value);
  const button = screen.getByRole("button", { name: "View diff" });
  expect(button).toHaveAttribute("aria-expanded", "false");
  fireEvent.click(button);
  const region = screen.getByRole("region", { name: "Plan diff for record 7" });
  expect(button).toHaveAttribute("aria-expanded", "true");
  expect(button.getAttribute("aria-controls")).toBe(region.id);
  if (kind === "missing" || kind === "limited") expect(region).toHaveTextContent("Plan diff unavailable");
  if (kind === "unchanged") expect(region).toHaveTextContent("No changes to plan status, task status, or task note");
  if (kind === "changed") {
    expect(region).toHaveTextContent('"FAILED"');
    expect(within(region).getByText("null", { exact: true })).toBeInTheDocument();
    expect(within(region).getByText('"null"', { exact: true })).toBeInTheDocument();
    expect(region.querySelector("script")).toBeNull();
    expect(region.querySelectorAll(".trace-plan-field-value")[7].textContent).toBe('"<script>alert(1)</script>\n' + "long ".repeat(1000) + '"');
  }
  fireEvent.click(screen.getByRole("button", { name: "View plan change plan-1 from record 7" }));
  expect(onSelectPlan).toHaveBeenCalledWith("plan-1", 7);
  expect(screen.getByRole("button", { name: "Read content" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Read raw record" })).toBeInTheDocument();
  expect(getRawRecordRangeMock).not.toHaveBeenCalled();
  expect(getTraceRecordsMock).not.toHaveBeenCalled();
});

test("resets diff expansion when source, generation, or record evidence disappears", () => {
  const value = record("PLAN_UPDATED");
  value.plan = { planId: "plan-1", capabilityName: "investigateNetwork" };
  value.planUpdate = { previousSequence: 3, availability: "AVAILABLE", changes: [] };
  const props = { traceId: "trace-1", records: [value], failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() };
  const view = render(<TraceRecords {...props} />);
  fireEvent.click(screen.getByRole("button", { name: "View diff" }));
  view.rerender(<TraceRecords {...props} source="IMPORTED" />);
  expect(screen.queryByRole("region", { name: "Plan diff for record 7" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "View diff" }));
  view.rerender(<TraceRecords {...props} source="IMPORTED" scopeGeneration={2} />);
  expect(screen.queryByRole("region", { name: "Plan diff for record 7" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "View diff" }));
  view.rerender(<TraceRecords {...props} source="IMPORTED" scopeGeneration={2} records={[]} />);
  view.rerender(<TraceRecords {...props} source="IMPORTED" scopeGeneration={2} />);
  expect(screen.queryByRole("region", { name: "Plan diff for record 7" })).toBeNull();
});
