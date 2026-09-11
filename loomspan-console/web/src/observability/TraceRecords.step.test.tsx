import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { getRawRecordRange } from "../api/client";
import type { TraceRange, TraceRecord } from "../api/contracts";
import { TraceRecords } from "./TraceRecords";

vi.mock("../api/client", () => ({
  getContentRange: vi.fn(),
  getRawRecordRange: vi.fn(),
  getTraceRecords: vi.fn(),
}));

const getRawRecordRangeMock = vi.mocked(getRawRecordRange);

const stepRecord: TraceRecord = {
  sequence: 88,
  type: "STEP_STARTED",
  frameId: "step-frame",
  parentFrameId: "root-frame",
  frameType: "STEP_EXECUTION",
  route: "handleBilling#step-1",
  threadName: "worker",
  timestampMillis: 88,
  representation: "LOGICAL",
  isChunk: false,
  isEnvelope: false,

};

function range(content: string): TraceRange {
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
  };
}

function renderStep() {
  render(<TraceRecords traceId="trace-1" records={[stepRecord]} failures={[]} onSelectRecord={vi.fn()} onSelectFailure={vi.fn()} onContent={vi.fn()} />);
}

beforeEach(() => getRawRecordRangeMock.mockReset());

test("shows authoritative step-start facts without attributing a future task", async () => {
  getRawRecordRangeMock.mockResolvedValue(range(JSON.stringify({
    recordType: "STEP_STARTED",
    route: "handleBilling#step-1",
    metadata: { stepNumber: 1, readyTasks: 3 },
    data: { planStatus: "VALID" },
  })));
  renderStep();

  expect(screen.queryByRole("region", { name: "Step details for record 88" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Step details" }));

  const details = await screen.findByRole("region", { name: "Step details for record 88" });
  expect(within(details).getByText("handleBilling")).toBeVisible();
  expect(within(details).getByText("3")).toBeVisible();
  expect(details).toHaveTextContent("Plan status");
  expect(details).toHaveTextContent("valid");
  expect(details).toHaveTextContent("No task or action has been selected yet");
  expect(details).toHaveTextContent("STEP_ACTION_PROPOSED");
  expect(getRawRecordRangeMock).toHaveBeenCalledWith("trace-1", 88, undefined, "TARGET");
});

test("reports invalid step facts instead of guessing", async () => {
  getRawRecordRangeMock.mockResolvedValue(range(JSON.stringify({
    metadata: { stepNumber: 1 },
    data: { planStatus: "VALID" },
  })));
  renderStep();

  fireEvent.click(screen.getByRole("button", { name: "Step details" }));

  expect(await screen.findByRole("alert")).toHaveTextContent("Step details could not be displayed");
  expect(screen.queryByText("No task or action has been selected yet")).toBeNull();
});

test("selects the nearest nested step using loaded records even without a frames page", () => {
  const outer = { ...stepRecord, sequence: 1 };
  const inner = { ...stepRecord, sequence: 2, frameId: "inner-step", parentFrameId: outer.frameId };
  const request = { ...inner, sequence: 3, type: "MODEL_REQUEST_SENT", frameType: "MODEL_CALL", frameId: "model", parentFrameId: inner.frameId };
  const failed = { ...inner, sequence: 4, type: "STEP_FAILED" };
  const records = [outer, inner, request, failed];
  render(<TraceRecords records={records} selectedRecordSequence={3} failures={[]} onSelectRecord={vi.fn()} onSelectFailure={vi.fn()} onContent={vi.fn()} />);
  expect(document.querySelector("#trace-record-1")).not.toHaveClass("trace-record-related");
  expect(document.querySelector("#trace-record-2")).toHaveClass("trace-record-related");
  expect(document.querySelector("#trace-record-3")).toHaveClass("trace-record-step-context");
  expect(document.querySelector("#trace-record-3")).toHaveAttribute("aria-current", "true");
  expect(document.querySelector("#trace-record-4")).toHaveClass("trace-record-related", "trace-record-error");
});

test("missing or cyclic ancestry leaves the clicked record selected without guessing a step", () => {
  const request = { ...stepRecord, sequence: 89, type: "MODEL_REQUEST_SENT", frameType: "MODEL_CALL", frameId: "model", parentFrameId: "missing" };
  const props = { selectedRecordSequence: 89, failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() };
  const { rerender } = render(<TraceRecords {...props} records={[stepRecord, request]} />);
  expect(document.querySelector("#trace-record-89")).toHaveAttribute("aria-current", "true");
  expect(document.querySelectorAll(".trace-record-related, .trace-record-step-context")).toHaveLength(0);
  rerender(<TraceRecords {...props} records={[stepRecord, { ...request, parentFrameId: request.frameId }]} />);
  expect(document.querySelectorAll(".trace-record-related, .trace-record-step-context")).toHaveLength(0);
});
