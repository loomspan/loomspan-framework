import { fireEvent, render, screen, within } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { expect, test, vi } from "vitest";
import { TraceTimeline } from "./TraceTimeline";
import { TraceUsage } from "./TraceUsage";
import { TraceRecords } from "./TraceRecords";
import { TraceEvidenceDetail } from "./TraceEvidenceDetail";
import type { TraceRecord } from "../api/contracts";

const frame = {
  frameId: "frame-1",
  parentFrameId: null,
  childFrameIds: [],
  frameType: "SKILL",
  route: "hello",
  openedTimestampMillis: 10,
  closedTimestampMillis: 15,
  inclusiveDurationMillis: 5,
  selfDurationMillis: null,
  directUsage: { promptUnits: 1, completionUnits: 1, totalUnits: 2 },
  directUsageComplete: true,
  descendantUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
  descendantUsageComplete: true,
  inclusiveUsage: { promptUnits: 1, completionUnits: 1, totalUnits: 2 },
  inclusiveUsageComplete: true,
  skillNames: [],
  outcome: null,
  attemptIds: [],
  retrySequenceIds: [],
  validationStatuses: [],
  failureIds: [],
};
test("timeline select returned frames without recalculating them", () => {
  const select = vi.fn();
  const second = { ...frame, frameId: "frame-2", route: "second" };
  const { rerender } = render(
    <TraceTimeline frames={[frame, second]} onSelect={select} />,
  );
  fireEvent.click(screen.getByRole("button", { name: "hello" }));
  fireEvent.keyDown(screen.getByRole("button", { name: "hello" }), {
    key: "ArrowDown",
  });
  expect(screen.getByRole("button", { name: "second" })).toHaveFocus();
  expect(select).toHaveBeenCalledWith("frame-1");
  rerender(<TraceTimeline frames={[frame]} onSelect={select} />);
  fireEvent.click(screen.getByRole("button", { name: "hello" }));
  expect(select).toHaveBeenLastCalledWith("frame-1");
  expect(
    screen.getByRole("img", { name: "5ms, self timing unavailable" }),
  ).toBeInTheDocument();
  expect(screen.queryByRole("tooltip")).toBeNull();
});
test("timeline bar tooltip uses milliseconds below one second and seconds otherwise", () => {
  render(
    <TraceTimeline
      frames={[{ ...frame, inclusiveDurationMillis: 119535 }]}
      onSelect={vi.fn()}
    />,
  );
  fireEvent.pointerEnter(document.querySelector('[data-frame-id="frame-1"]') as Element);
  expect(screen.getByRole("tooltip")).toHaveTextContent("Duration: 1m 59.5s");
  fireEvent.pointerLeave(document.querySelector('[data-frame-id="frame-1"]') as Element);
  expect(screen.queryByRole("tooltip")).toBeNull();

  render(<TraceTimeline frames={[frame]} onSelect={vi.fn()} />);
  fireEvent.pointerEnter(document.querySelectorAll('[data-frame-id="frame-1"]')[1] as Element);
  expect(screen.getByRole("tooltip")).toHaveTextContent("Duration: 5ms");
});
test("timeline groups children under their parents without metadata text", () => {
  const child = { ...frame, frameId: "child", parentFrameId: "frame-1", route: "child-route", planId: "plan-1", taskId: "task-2", stepNumber: 2, parallelGroup: "batch", effectiveConcurrency: true };
  const unassigned = { ...frame, frameId: "coordinator", parentFrameId: null, route: "coordinator", openedTimestampMillis: 1, closedTimestampMillis: 2, planId: null, taskId: null, stepNumber: null, parallelGroup: null, effectiveConcurrency: null };
  render(<TraceTimeline frames={[child, unassigned, frame]} onSelect={vi.fn()} />);
  const rows = screen.getByLabelText("Trace timeline").querySelectorAll(".trace-timeline-row");
  expect(rows).toHaveLength(3);
  expect(rows[0]).toHaveTextContent(/^coordinator$/);
  expect(rows[1]).toHaveTextContent(/hello$/);
  expect(rows[2]).toHaveTextContent(/^child-route$/);
  const labels = Array.from(rows, (row) => row.querySelector(".trace-timeline-label") as HTMLElement);
  expect(labels.map((label) => label.style.getPropertyValue("--trace-depth"))).toEqual(["0", "0", "1"]);
});
test("timeline bars expose retry warnings and failures with distinct states", () => {
  const retry = { ...frame, frameId: "retry", frameType: "RETRY", route: "retry", openedTimestampMillis: 15, closedTimestampMillis: 20 };
  const failed = { ...frame, frameId: "failed", route: "failed", openedTimestampMillis: 20, closedTimestampMillis: 25, outcome: "failed", failureIds: ["failure-1"] };
  render(<TraceTimeline frames={[frame, retry, failed]} onSelect={vi.fn()} />);
  expect(document.querySelectorAll(".trace-timeline-bar-normal")).toHaveLength(1);
  expect(document.querySelectorAll(".trace-timeline-bar-warning")).toHaveLength(1);
  expect(document.querySelectorAll(".trace-timeline-bar-error")).toHaveLength(1);
  expect(screen.getByRole("img", { name: /retry or warning/ })).toBeInTheDocument();
  expect(screen.getByRole("img", { name: /error or failure/ })).toBeInTheDocument();
  fireEvent.pointerEnter(document.querySelector('[data-frame-id="failed"]') as Element);
  expect(screen.getByRole("tooltip")).toHaveTextContent("Error or failure");
});
test("timeline supports semantic expansion and parent keyboard navigation", () => {
  const child = {
    ...frame,
    frameId: "frame-2",
    parentFrameId: "frame-1",
    route: "child",
  };
  const root = { ...frame, childFrameIds: ["frame-2"] };
  render(<TraceTimeline frames={[root, child]} onSelect={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: "Collapse hello" }));
  expect(screen.queryByRole("button", { name: "child" })).toBeNull();
  const rootButton = screen.getByRole("button", { name: "hello" });
  rootButton.focus();
  fireEvent.keyDown(rootButton, { key: "ArrowRight" });
  const childButton = screen.getByRole("button", { name: "child" });
  fireEvent.keyDown(rootButton, { key: "ArrowRight" });
  expect(childButton).toHaveFocus();
  fireEvent.keyDown(childButton, { key: "ArrowLeft" });
  expect(rootButton).toHaveFocus();
});
test("timeline collapses descendants without rescaling bars and navigates only visible frames", () => {
  const child = { ...frame, frameId: "child", parentFrameId: frame.frameId, route: "child" };
  const leaf = { ...frame, frameId: "leaf", parentFrameId: "child", route: "leaf", openedTimestampMillis: 1, closedTimestampMillis: 100, inclusiveDurationMillis: 99 };
  const sibling = { ...frame, frameId: "sibling", route: "sibling" };
  render(<TraceTimeline frames={[frame, child, leaf, sibling]} onSelect={vi.fn()} />);
  const rootButton = screen.getByRole("button", { name: "hello" });
  const siblingButton = screen.getByRole("button", { name: "sibling" });
  const siblingHitTarget = document.querySelector('[data-frame-id="sibling"]') as HTMLElement;
  const originalWidth = siblingHitTarget.parentElement?.querySelector(".trace-timeline-bar")?.getAttribute("width");
  fireEvent.click(screen.getByRole("button", { name: "Collapse hello" }));
  expect(screen.getByRole("treeitem", { name: "hello" })).toHaveAttribute("aria-expanded", "false");
  expect(screen.queryByRole("button", { name: "leaf" })).toBeNull();
  expect(siblingHitTarget.parentElement?.querySelector(".trace-timeline-bar")).toHaveAttribute("width", originalWidth);
  rootButton.focus();
  fireEvent.keyDown(rootButton, { key: "ArrowDown" });
  expect(siblingButton).toHaveFocus();
  fireEvent.keyDown(siblingButton, { key: "ArrowUp" });
  expect(rootButton).toHaveFocus();
  fireEvent.keyDown(rootButton, { key: "End" });
  expect(siblingButton).toHaveFocus();
  fireEvent.keyDown(siblingButton, { key: "Home" });
  expect(rootButton).toHaveFocus();
  fireEvent.keyDown(rootButton, { key: "ArrowRight" });
  expect(screen.getByRole("treeitem", { name: "leaf" })).toHaveAttribute("aria-level", "3");
  fireEvent.keyDown(rootButton, { key: "ArrowLeft" });
  expect(screen.queryByRole("button", { name: "leaf" })).toBeNull();
});

test("timeline reveals ancestors when an externally selected frame changes", () => {
  const child = { ...frame, frameId: "child", parentFrameId: frame.frameId, route: "child" };
  const frames = [frame, child];
  const view = render(<TraceTimeline frames={frames} onSelect={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: "Collapse hello" }));
  view.rerender(<TraceTimeline frames={frames} selectedFrameId="child" onSelect={vi.fn()} />);
  expect(screen.getByRole("treeitem", { name: "hello" })).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByRole("treeitem", { name: "child" })).toHaveAttribute("aria-selected", "true");
});

test("timeline and selected-frame usage preserve unknown and incomplete returned facts", () => {
  const incomplete = {
    ...frame,
    frameId: "open",
    openedTimestampMillis: 20,
    closedTimestampMillis: null,
    inclusiveDurationMillis: null,
    directUsageComplete: false,
    inclusiveUsageComplete: false,
  };
  const { rerender } = render(
    <TraceTimeline frames={[incomplete]} onSelect={vi.fn()} />,
  );
  expect(
    screen.getByText("Timing unavailable or incomplete"),
  ).toBeInTheDocument();
  expect(screen.queryByRole("img")).toBeNull();
  rerender(
    <TraceUsage
      usage={{
        source: "TARGET" as const,
        targetScopeId: "scope-1",
        attributed: frame.inclusiveUsage,
        unattributed: frame.descendantUsage,
        unframedAttributed: frame.descendantUsage,
        terminal: frame.directUsage,
      }}
      frame={incomplete}
    />,
  );
  expect(screen.getByRole("table", { name: "Usage facts" })).toHaveTextContent(
    "Selected frame direct (incomplete)",
  );
});
test("usage preserves returned values and record-row evidence actions remain deliberate", () => {
  const payload = vi.fn();
  const selectRecord = vi.fn();
  const selectFailure = vi.fn();
  render(
    <>
      <TraceUsage
        usage={{
          source: "TARGET" as const,
          targetScopeId: "scope-1",
          attributed: { promptUnits: 1, completionUnits: 2, totalUnits: 3 },
          unattributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
          unframedAttributed: {
            promptUnits: 0,
            completionUnits: 0,
            totalUnits: 0,
          },
          terminal: { promptUnits: 1, completionUnits: 2, totalUnits: 3 },
        }}
      />
      <TraceRecords
        frames={[{ ...frame, inclusiveDurationMillis: 75_432 }]}
        records={[
          {
            sequence: 1,
            type: "PAYLOAD",
            frameId: "frame-1",
            parentFrameId: "",
            frameType: "SKILL",
            route: "",
            threadName: "worker",
            timestampMillis: 0,
            representation: "logical",
            isChunk: false,
            isEnvelope: true,
            content: { role: "RECONSTRUCTED", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "payload-1" },
          },
        ]}
        failures={[
          {
            failureId: "failure",
            terminal: true,
            sequence: 2,
            timestampMillis: 0,
            recordType: "ERROR_RECORDED",
            frameId: "frame-1",
            route: "",
            attemptId: "attempt",
            retrySequenceId: "retry",
            validationStatus: "",
          },
        ]}
        onSelectRecord={selectRecord}
        onSelectFailure={selectFailure}
        onContent={payload}
      />
    </>,
  );
  expect(screen.getByRole("table", { name: "Usage facts" })).toHaveTextContent(
    "3",
  );
  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));
  fireEvent.click(screen.getByRole("button", { name: "Read content" }));
  expect(screen.getByRole("status")).toHaveTextContent(
    "Trace context unavailable",
  );
  expect(payload).toHaveBeenCalledWith("payload-1", 1);
  expect(selectRecord).not.toHaveBeenCalled();
  const recordRow = screen.getByRole("row", { name: "Record 1, PAYLOAD" });
  const cells = within(recordRow).getAllByRole("cell");
  expect(cells[0]).toHaveTextContent(/^1$/);
  expect(cells[1]).toHaveTextContent(/^PAYLOAD$/);
  expect(cells[2]).toHaveTextContent(/^frame-1$/);
  expect(cells[3]).toHaveTextContent(/^—$/);
  expect(recordRow).toHaveTextContent("frame-1");
  expect(within(recordRow).getAllByRole("button").map((button) => button.textContent)).toEqual(["Hide raw record", "Read content"]);
  fireEvent.click(recordRow);
  expect(selectRecord).toHaveBeenCalled();
  expect(selectFailure).not.toHaveBeenCalled();
  expect(screen.queryByText("payload-2")).toBeNull();
  expect(screen.queryByText(/provider 1.*FAILED.*RATE_LIMITED/)).toBeNull();
});
test("record rows show frame duration only when the record represents an operation outcome", () => {
  const record = (sequence: number, type: string): TraceRecord => ({
    sequence,
    type,
    frameId: "frame-1",
    parentFrameId: "",
    frameType: "STEP_EXECUTION",
    route: "hello",
    threadName: "worker",
    timestampMillis: sequence,
    representation: "logical",
    isChunk: false,
    isEnvelope: false,
  });
  render(<TraceRecords
    frames={[{ ...frame, inclusiveDurationMillis: 75_432 }]}
    records={[
      record(1, "PLAN_CREATED"),
      record(2, "PLAN_UPDATED"),
      record(3, "STEP_STARTED"),
      record(4, "STEP_COMPLETED"),
      record(5, "MODEL_REQUEST_SENT"),
      record(6, "MODEL_RESPONSE_RECEIVED"),
    ]}
    failures={[]}
    onSelectRecord={vi.fn()}
    onSelectFailure={vi.fn()}
    onContent={vi.fn()}
  />);

  const duration = (sequence: number, type: string) => within(screen.getByRole("row", { name: `Record ${sequence}, ${type}` })).getAllByRole("cell")[3];
  expect(duration(1, "PLAN_CREATED")).toHaveTextContent(/^1m 15.4s$/);
  expect(duration(2, "PLAN_UPDATED")).toHaveTextContent(/^—$/);
  expect(duration(3, "STEP_STARTED")).toHaveTextContent(/^—$/);
  expect(duration(4, "STEP_COMPLETED")).toHaveTextContent(/^1m 15.4s$/);
  expect(duration(5, "MODEL_REQUEST_SENT")).toHaveTextContent(/^—$/);
  expect(duration(6, "MODEL_RESPONSE_RECEIVED")).toHaveTextContent(/^1m 15.4s$/);
});
test("selecting a step lifecycle record highlights its loaded counterpart", () => {
  const record = (sequence: number, type: string, frameId = "step-frame"): TraceRecord => ({
    sequence,
    type,
    frameId,
    parentFrameId: "root",
    frameType: "STEP_EXECUTION",
    route: "hello#step-1",
    threadName: "worker",
    timestampMillis: sequence,
    representation: "logical",
    isChunk: false,
    isEnvelope: false,
  });
  const records = [record(1, "STEP_STARTED"), record(2, "STEP_COMPLETED"), record(3, "STEP_COMPLETED", "other-step")];
  const props = { records, failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() };
  const { rerender } = render(<TraceRecords {...props} selectedRecordSequence={1} />);

  expect(screen.getByRole("row", { name: "Record 1, STEP_STARTED" })).toHaveAttribute("aria-current", "true");
  expect(screen.getByRole("row", { name: /Record 2, STEP_COMPLETED, related to selected step/ })).toHaveClass("trace-record-related");
  expect(screen.getByRole("row", { name: "Record 3, STEP_COMPLETED" })).not.toHaveClass("trace-record-related");

  rerender(<TraceRecords {...props} selectedRecordSequence={2} />);
  expect(screen.getByRole("row", { name: /Record 1, STEP_STARTED, related to selected step/ })).toHaveClass("trace-record-related");
  expect(screen.getByRole("row", { name: "Record 2, STEP_COMPLETED" })).toHaveAttribute("aria-current", "true");
});
test("selecting an overlapping step lightly highlights only records in that step", () => {
  const record = (sequence: number, type: string, frameId: string, parentFrameId: string): TraceRecord => ({
    sequence,
    type,
    frameId,
    parentFrameId,
    frameType: type.startsWith("STEP_") ? "STEP_EXECUTION" : "MODEL_CALL",
    route: "hello",
    threadName: "worker",
    timestampMillis: sequence,
    representation: "logical",
    isChunk: false,
    isEnvelope: false,
  });
  const stepA = { ...frame, frameId: "step-a", parentFrameId: "root", frameType: "STEP_EXECUTION" };
  const stepB = { ...frame, frameId: "step-b", parentFrameId: "root", frameType: "STEP_EXECUTION" };
  const modelA = { ...frame, frameId: "model-a", parentFrameId: "step-a", frameType: "MODEL_CALL" };
  const nestedModelA = { ...frame, frameId: "nested-model-a", parentFrameId: "model-a", frameType: "MODEL_CALL" };
  const modelB = { ...frame, frameId: "model-b", parentFrameId: "step-b", frameType: "MODEL_CALL" };
  const records = [
    record(1, "STEP_STARTED", "step-a", "root"),
    record(2, "STEP_STARTED", "step-b", "root"),
    record(3, "MODEL_REQUEST_SENT", "nested-model-a", "model-a"),
    record(4, "MODEL_REQUEST_SENT", "model-b", "step-b"),
    record(5, "STEP_COMPLETED", "step-a", "root"),
    record(6, "STEP_COMPLETED", "step-b", "root"),
  ];
  const props = { frames: [stepA, stepB, modelA, nestedModelA, modelB], records, failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() };
  const { rerender } = render(<TraceRecords {...props} selectedRecordSequence={1} />);

  expect(screen.getByRole("row", { name: /Record 3, MODEL_REQUEST_SENT, part of selected step/ })).toHaveClass("trace-record-step-context");
  expect(screen.getByRole("row", { name: "Record 4, MODEL_REQUEST_SENT" })).not.toHaveClass("trace-record-step-context");
  expect(screen.getByRole("row", { name: /Record 5, STEP_COMPLETED, related to selected step/ })).toHaveClass("trace-record-related");

  rerender(<TraceRecords {...props} selectedRecordSequence={2} />);
  expect(screen.getByRole("row", { name: "Record 3, MODEL_REQUEST_SENT" })).not.toHaveClass("trace-record-step-context");
  expect(screen.getByRole("row", { name: /Record 4, MODEL_REQUEST_SENT, part of selected step/ })).toHaveClass("trace-record-step-context");
  expect(screen.getByRole("row", { name: /Record 6, STEP_COMPLETED, related to selected step/ })).toHaveClass("trace-record-related");
});
test("evidence detail renders text inertly and exposes continuation", () => {
  const next = vi.fn();
  const clear = vi.fn();
  render(
    <TraceEvidenceDetail
      range={{
        source: "TARGET" as const,
        targetScopeId: "scope-1",
        actualStart: 0,
        actualEnd: 2,
        totalLength: 4,
        contentType: "text/plain",
        encoding: "TEXT",
        content: "<a>unsafe</a>",
        hasMore: true,
        nextCursor: "next",
      }}
      onNext={next}
      onClear={clear}
    />,
  );
  expect(screen.queryByRole("link")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Read next range" }));
  fireEvent.click(screen.getByRole("button", { name: "Clear content" }));
  expect(next).toHaveBeenCalled();
  expect(clear).toHaveBeenCalled();
});
test("evidence detail labels base64 without interpreting content", () => {
  render(
    <TraceEvidenceDetail
      range={{
        source: "TARGET" as const,
        targetScopeId: "scope-1",
        actualStart: 4,
        actualEnd: 8,
        totalLength: 12,
        contentType: "application/octet-stream",
        encoding: "BASE64",
        content: "AQIDBA==",
        hasMore: false,
        nextCursor: null,
      }}
      onNext={vi.fn()}
      onClear={vi.fn()}
    />,
  );
  expect(screen.getByText(/Base64-encoded bytes 4/)).toBeInTheDocument();
  expect(screen.getByText("AQIDBA==")).toBeInTheDocument();
});

test("usage compares only supported arithmetic facts and preserves zero and absent limit semantics", () => {
  const summary = {
    source: "TARGET" as const,
    targetScopeId: "scope-1",
    traceId: "trace-1",
    sessionId: "session-1",
    outcome: "SUCCEEDED",
    terminalFailureId: null,
    recordCount: 1,
    frameCount: 1,
    attemptCount: 1,
    retryCount: 0,
    validationCount: 0,
    failureCount: 0,
    payloadCount: 0,
    gapCount: 0,
    uncertaintyCount: 0,
    rootFrameIds: ["frame-1"],
    attributedUsage: { promptUnits: 1, completionUnits: 2, totalUnits: 3 },
    terminalUsage: { promptUnits: 1, completionUnits: 2, totalUnits: 3 },
    unattributedUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    unframedAttributedUsage: {
      promptUnits: 0,
      completionUnits: 0,
      totalUnits: 0,
    },
    usageComplete: true,
    configuredLimits: {
      maxSkillInvocations: 7,
      maxToolInvocations: 11,
      maxLinterRetries: 3,
      maxModelCalls: 4,
      maxProviderAttempts: 12,
      maxUsageUnits: 0,
    },
  };
  const usage = {
    source: "TARGET" as const,
    targetScopeId: "scope-1",
    attributed: summary.attributedUsage,
    unattributed: summary.unattributedUsage,
    unframedAttributed: summary.unframedAttributedUsage,
    terminal: summary.terminalUsage,
  };
  const { rerender } = render(
    <TraceUsage
      usage={usage}
      summary={summary}
      contributors={[frame]}
    />,
  );
  expect(
    screen.getByRole("region", { name: "Configured limit comparison" }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("row", { name: /Provider attempts 1 12 8%/ }),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("row", { name: /Usage units 3 0 undefined/ }),
  ).toBeInTheDocument();
  expect(screen.queryByText("Skill invocations")).toBeNull();
  expect(screen.queryByText("Tool invocations")).toBeNull();
  expect(screen.queryByText("Linter retries")).toBeNull();
  expect(screen.queryByText("Model calls")).toBeNull();
  const contributorsTable = screen.getByRole("table", { name: "Operations with directly attributed model usage" });
  expect(contributorsTable).toHaveTextContent("OperationPromptCompletionTotalShare of trace");
  expect(contributorsTable).toHaveTextContent("hello");
  expect(contributorsTable).not.toHaveTextContent("frame-1");
  expect(contributorsTable).toHaveTextContent("67%");
  expect(contributorsTable).not.toHaveTextContent("Descendants");
  expect(contributorsTable).not.toHaveTextContent("Inclusive");
  expect(screen.queryByRole("button", { name: "hello" })).toBeNull();
  rerender(
    <TraceUsage
      usage={usage}
      summary={{ ...summary, configuredLimits: null }}
    />,
  );
  expect(
    screen.getByText("Configured limit comparison unavailable."),
  ).toBeInTheDocument();
  expect(screen.queryByText(/%/)).toBeNull();
  expect(
    screen.getByText(/Monetary cost is not calculated/),
  ).toBeInTheDocument();
});

test("usage contributors show only non-overlapping direct consumption ordered by total", () => {
  const usage = {
    source: "TARGET" as const,
    targetScopeId: "scope-1",
    attributed: { promptUnits: 10, completionUnits: 5, totalUnits: 15 },
    unattributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    unframedAttributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    terminal: { promptUnits: 10, completionUnits: 5, totalUnits: 15 },
  };
  const structural = { ...frame, frameId: "root", route: "handleIncident", directUsage: { promptUnits: 0, completionUnits: 0, totalUnits: 0 }, descendantUsage: usage.attributed, inclusiveUsage: usage.attributed };
  const smaller = { ...frame, frameId: "smaller", route: "handleIncident#planning-model", directUsage: { promptUnits: 2, completionUnits: 1, totalUnits: 3 }, inclusiveUsage: { promptUnits: 2, completionUnits: 1, totalUnits: 3 } };
  const larger = { ...frame, frameId: "larger", route: "handleIncident#step-2-model", directUsage: { promptUnits: 8, completionUnits: 4, totalUnits: 12 }, inclusiveUsage: { promptUnits: 8, completionUnits: 4, totalUnits: 12 } };
  render(<TraceUsage usage={usage} contributors={[structural, smaller, larger]} />);
  const table = screen.getByRole("table", { name: "Operations with directly attributed model usage" });
  const rows = Array.from(table.querySelectorAll("tbody tr"));
  expect(rows).toHaveLength(2);
  expect(rows[0]).toHaveTextContent("handleIncident · step 2 · model");
  expect(rows[0]).toHaveTextContent("80%");
  expect(rows[1]).toHaveTextContent("handleIncident · planning · model");
  expect(table).not.toHaveTextContent("root");
});

test("usage percentages round half up to whole numbers", () => {
  render(
    <TraceUsage
      usage={{
        source: "TARGET" as const,
        targetScopeId: "scope-1",
        attributed: { promptUnits: 1, completionUnits: 0, totalUnits: 1 },
        unattributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
        unframedAttributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
        terminal: { promptUnits: 8, completionUnits: 0, totalUnits: 8 },
      }}
      contributors={[{ ...frame, directUsage: { promptUnits: 1, completionUnits: 0, totalUnits: 1 } }]}
    />,
  );
  expect(screen.getByRole("table", { name: "Operations with directly attributed model usage" })).toHaveTextContent("13%");
});

test("usage distinguishes repeated operations by chronological call number", () => {
  const usage = {
    source: "TARGET" as const,
    targetScopeId: "scope-1",
    attributed: { promptUnits: 5, completionUnits: 5, totalUnits: 10 },
    unattributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    unframedAttributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    terminal: { promptUnits: 5, completionUnits: 5, totalUnits: 10 },
  };
  const firstCall = { ...frame, frameId: "first", route: "handleIncident#planning-model", openedTimestampMillis: 10, directUsage: { promptUnits: 2, completionUnits: 2, totalUnits: 4 } };
  const secondCall = { ...frame, frameId: "second", route: "handleIncident#planning-model", openedTimestampMillis: 20, directUsage: { promptUnits: 3, completionUnits: 3, totalUnits: 6 } };
  render(<TraceUsage usage={usage} contributors={[firstCall, secondCall]} />);
  const rows = Array.from(screen.getByRole("table", { name: "Operations with directly attributed model usage" }).querySelectorAll("tbody tr"));
  expect(rows[0]).toHaveTextContent("handleIncident · planning · model · call 2 of 2");
  expect(rows[1]).toHaveTextContent("handleIncident · planning · model · call 1 of 2");
});

test("usage operation links identify the exact model response record", () => {
  const usage = {
    source: "TARGET" as const,
    targetScopeId: "scope-1",
    attributed: { promptUnits: 2, completionUnits: 2, totalUnits: 4 },
    unattributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    unframedAttributed: { promptUnits: 0, completionUnits: 0, totalUnits: 0 },
    terminal: { promptUnits: 2, completionUnits: 2, totalUnits: 4 },
  };
  const contributor = { ...frame, frameId: "model-frame", route: "handleIncident#planning-model", directUsage: usage.attributed };
  const response: TraceRecord = { sequence: 23, type: "MODEL_RESPONSE_RECEIVED", frameId: "model-frame", parentFrameId: "root", frameType: "MODEL_CALL", route: contributor.route, threadName: "main", timestampMillis: 20, representation: "LOGICAL", isChunk: false, isEnvelope: false, content: { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 1, available: true, complete: true, inlineEligibility: true, contentRef: "response-content" } };

  const recordHref = (record: TraceRecord) => `?view=records&frameId=${record.frameId}&recordSequence=${record.sequence}`;
  const { rerender } = render(<MemoryRouter><TraceUsage usage={usage} contributors={[contributor]} responseRecords={[response]} recordHref={recordHref} /></MemoryRouter>);

  expect(screen.getByRole("link", { name: "handleIncident · planning · model" })).toHaveAttribute("href", "/?view=records&frameId=model-frame&recordSequence=23");
  rerender(<MemoryRouter><TraceUsage usage={usage} contributors={[contributor]} responseRecords={[response, { ...response, sequence: 24 }]} recordHref={recordHref} /></MemoryRouter>);
  expect(screen.queryByRole("link", { name: "handleIncident · planning · model" })).toBeNull();
});
