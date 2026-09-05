import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import type { TraceFailure, TraceRecord } from "../api/contracts";
import { TraceRecords } from "./TraceRecords";

const failure: TraceFailure = {
  failureId: "failure-15",
  terminal: true,
  sequence: 15,
  timestampMillis: 15,
  recordType: "ERROR_RECORDED",
  frameId: "frame-1",
  route: "planning-model",
  attemptId: "attempt-3",
  retrySequenceId: "retry-1",
  validationStatus: "",
};

function record(sequence: number, type: string, failureId?: string, validationStatus?: string): TraceRecord {
  return {
    sequence,
    type,
    failureId,
    validationStatus,
    frameId: "frame-1",
    parentFrameId: "",
    frameType: "MODEL_CALL",
    route: "planning-model",
    threadName: "worker",
    timestampMillis: sequence,
    representation: "LOGICAL",
    isChunk: false,
    isEnvelope: true,
    content: type === "MODEL_ATTEMPT_FAILED" ? { role: "RECONSTRUCTED", contentType: "application/json", encoding: "UTF8", retainedBytes: 12000, available: true, complete: true, inlineEligibility: false, contentRef: "opaque-attempt" } : undefined,

  };
}

test("distinguishes recoverable warnings from failure records and removes detached fact indexes", () => {
  const selectFailure = vi.fn();
  render(
    <TraceRecords
      traceId="trace-1"
      records={[
        record(14, "MODEL_ATTEMPT_FAILED"),
        record(15, "ERROR_RECORDED"),
        record(16, "PLAN_VALIDATION_FAILED"),
        record(17, "STEP_FAILED", "failure-15"),
        record(18, "FRAME_CLOSED", "failure-15"),
        record(19, "STRUCTURED_OUTPUT_RECORDED", undefined, "retrying"),
        record(20, "STRUCTURED_OUTPUT_RECORDED", undefined, "exhausted"),
        record(21, "LINTER_RECORDED", undefined, "retrying"),
        record(22, "STRUCTURED_OUTPUT_RECORDED", undefined, "passed"),
        record(23, "STEP_FAILED", "failure-15", "retrying"),
      ]}
      failures={[failure]}
      selectedFailureId="failure-15"
      onSelectRecord={vi.fn()}
      onSelectFailure={selectFailure}
      onContent={vi.fn()}
    />,
  );

  const errorRow = screen.getByRole("row", { name: "Failure: record 15, ERROR_RECORDED" });
  const attemptRow = screen.getByRole("row", { name: "Retry or warning: record 14, MODEL_ATTEMPT_FAILED" });
  const badPlanRow = screen.getByRole("row", { name: "Retry or warning: record 16, PLAN_VALIDATION_FAILED" });
  const relatedFailureRow = screen.getByRole("row", { name: "Failure: record 17, STEP_FAILED" });
  const relatedClosedFrameRow = screen.getByRole("row", { name: "Failure: record 18, FRAME_CLOSED" });
  const retryingOutcomeRow = screen.getByRole("row", { name: "Retry or warning: record 19, STRUCTURED_OUTPUT_RECORDED" });
  const exhaustedOutcomeRow = screen.getByRole("row", { name: "Retry or warning: record 20, STRUCTURED_OUTPUT_RECORDED" });
  const linterRetryRow = screen.getByRole("row", { name: "Retry or warning: record 21, LINTER_RECORDED" });
  const passedOutcomeRow = screen.getByRole("row", { name: "Record 22, STRUCTURED_OUTPUT_RECORDED" });
  const warningStatusErrorRow = screen.getByRole("row", { name: "Failure: record 23, STEP_FAILED" });
  expect(errorRow).toHaveClass("trace-record-error");
  expect(errorRow).toHaveAccessibleName("Failure: record 15, ERROR_RECORDED");
  expect(attemptRow).toHaveClass("trace-record-warning");
  expect(badPlanRow).toHaveClass("trace-record-warning");
  expect(relatedFailureRow).toHaveClass("trace-record-error");
  expect(relatedClosedFrameRow).toHaveClass("trace-record-error");
  expect(retryingOutcomeRow).toHaveClass("trace-record-warning");
  expect(exhaustedOutcomeRow).toHaveClass("trace-record-warning");
  expect(linterRetryRow).toHaveClass("trace-record-warning");
  expect(passedOutcomeRow).not.toHaveClass("trace-record-warning");
  expect(passedOutcomeRow).not.toHaveClass("trace-record-error");
  expect(warningStatusErrorRow).toHaveClass("trace-record-error");
  expect(attemptRow).toHaveAccessibleName("Retry or warning: record 14, MODEL_ATTEMPT_FAILED");
  expect(screen.getByRole("button", { name: "Attempt diagnostics" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Read content" })).toBeNull();
  expect(errorRow.querySelectorAll("td")[3]).toHaveTextContent(/^—$/);
  expect(screen.queryByRole("heading", { name: "Attempts, retries, and validation" })).toBeNull();
  expect(screen.queryByRole("heading", { name: "Failures and uncertainty" })).toBeNull();
  expect(screen.queryByRole("heading", { name: "Payloads" })).toBeNull();

  const action = screen.getByRole("button", { name: "View error" });
  expect(screen.getAllByRole("button", { name: "View error" })).toHaveLength(1);
  expect(action).toHaveAttribute("aria-pressed", "true");
  fireEvent.click(action);
  expect(selectFailure).toHaveBeenCalledWith("failure-15");
});
