import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { getContentRange, getRawRecordRange } from "../api/client";
import type { TraceFrame, TraceRange, TraceRecord, TraceSource } from "../api/contracts";
import { TraceRecords } from "./TraceRecords";

vi.mock("../api/client", () => ({ getContentRange: vi.fn(), getRawRecordRange: vi.fn(), getTraceRecords: vi.fn() }));
const read = vi.mocked(getContentRange);
const payload = { severity: "ERROR", message: "  caught request failure\n雪 <script>bad()</script>  ", messageState: "COMPLETE", dataState: "COMPLETE", data: { outcome: "FAILED", retry: true, usage: 999999, unknown: null } };
function record(data: unknown = payload, changes: Partial<TraceRecord> = {}): TraceRecord {
  const json = JSON.stringify(data);
  return { sequence: 4, type: "EXECUTOR_DIAGNOSTIC_RECORDED", frameId: "executor-child", parentFrameId: "root", frameType: "ROOT_MISSION", route: "default-lookup", timestampMillis: 10, threadName: "worker", representation: "LOGICAL", isChunk: false, isEnvelope: false,
    content: { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: new TextEncoder().encode(json).length, available: true, complete: true, inlineEligibility: true, inlineContent: json, contentRef: "data-4" }, ...changes };
}
const frame = { frameId: "executor-child", parentFrameId: "root", frameType: "ROOT_MISSION", route: "default-lookup", openedTimestampMillis: 1, closedTimestampMillis: 20, inclusiveDurationMillis: 19, outcome: "SUCCEEDED" } as TraceFrame;
function props(current = record(), source: TraceSource = "IMPORTED", scopeGeneration = 0) {
  return { traceId: "trace-1", records: [current], source, scopeGeneration, frames: [frame], failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() };
}
function range(content: string, changes: Partial<TraceRange> = {}): TraceRange {
  return { source: "IMPORTED", targetScopeId: undefined, content, contentType: "application/json", encoding: "TEXT", actualStart: 0, actualEnd: content.length, totalLength: content.length, hasMore: false, nextCursor: null, ...changes };
}
async function expand(label = "Diagnostic details") {
  fireEvent.click(screen.getByRole("button", { name: label }));
  return screen.findByRole("region", { name: `${label} for record 4` });
}
beforeEach(() => vi.resetAllMocks());

test("keeps authored ERROR and misleading JSON separate from successful Framework facts, lazily and inertly", async () => {
  render(<TraceRecords {...props()} />);
  expect(read).not.toHaveBeenCalled();
  expect(getRawRecordRange).not.toHaveBeenCalled();
  const row = screen.getByRole("row", { name: "Record 4, EXECUTOR_DIAGNOSTIC_RECORDED" });
  expect(row).not.toHaveClass("trace-record-error");
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent("Authored severity"));
  expect(detail).toHaveTextContent("SUCCEEDED");
  expect(detail).toHaveTextContent("COMPLETE");
  expect(within(detail).getByText(payload.message, { exact: true, normalizer: (value) => value })).toBeVisible();
  expect(detail.querySelector("script")).toBeNull();
  expect(screen.queryByRole("button", { name: "View error" })).toBeNull();
  const json = within(detail).getByText(/"usage":999999/);
  expect(json.closest("details")).not.toHaveAttribute("open");
  expect(read).not.toHaveBeenCalled();
});

test.each(["ABSENT", "UNAVAILABLE", "CAPTURE_FAILED"])("shows recorded %s data state without inventing missing capture reasons", async (dataState) => {
  render(<TraceRecords {...props(record({ severity: "WARN", messageState: "UNAVAILABLE", dataState }))} />);
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent(dataState));
  expect(within(detail).queryByText("Recorded reason")).toBeNull();
  expect(within(detail).queryByText("Supplied JSON")).toBeNull();
  expect(within(detail).queryByText("Recorded message")).toBeNull();
});

test("distinguishes explicit null JSON and recorded message truncation", async () => {
  render(<TraceRecords {...props(record({ severity: "INFO", message: "retained prefix", messageState: "TRUNCATED", dataState: "COMPLETE", data: null, reason: "MESSAGE_LIMIT" }))} />);
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent("MESSAGE_LIMIT"));
  expect(within(detail).getByText("null", { exact: true })).toBeInTheDocument();
  expect(detail).toHaveTextContent("TRUNCATED");
});

test("shows a complete retained system notice without severity or recovery claims", async () => {
  render(<TraceRecords {...props(record({ reason: "DATA_LIMIT", message: "capture limited", originalMessage: "  original 雪  ", originalMessageState: "TRUNCATED" }, { type: "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED" }))} />);
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent("Framework capture notice"));
  expect(within(detail).queryByText("Authored severity")).toBeNull();
  expect(detail).toHaveTextContent("DATA_LIMIT");
  expect(within(detail).getByText("  original 雪  ", { normalizer: (value) => value })).toBeVisible();
  expect(detail).not.toHaveTextContent("recoverable");
});

test("joins chunked DATA via ordinary refs and BASE64 byte continuations", async () => {
  const current = record();
  delete current.content!.inlineContent;
  const bytes = new TextEncoder().encode(JSON.stringify(payload));
  const split = bytes.indexOf(0xe9) + 1;
  read.mockResolvedValueOnce(range(btoa(String.fromCharCode(...bytes.slice(0, split))), { encoding: "BASE64", hasMore: true, nextCursor: "next" }))
    .mockResolvedValueOnce(range(btoa(String.fromCharCode(...bytes.slice(split))), { encoding: "BASE64" }));
  render(<TraceRecords {...props(current)} />);
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent("Authored severity"));
  expect(within(detail).getByText(payload.message, { normalizer: (value) => value })).toBeVisible();
  expect(read).toHaveBeenNthCalledWith(1, "trace-1", "data-4", undefined, "IMPORTED");
  expect(read).toHaveBeenNthCalledWith(2, "trace-1", "data-4", "next", "IMPORTED");
  expect(getRawRecordRange).not.toHaveBeenCalled();
});

test.each(["source", "generation", "trace"])("discards a pending detail after %s changes", async (change) => {
  let resolve!: (value: TraceRange) => void;
  read.mockReturnValue(new Promise((done) => { resolve = done; }));
  const current = record(); delete current.content!.inlineContent;
  const initial = props(current, "TARGET");
  const view = render(<TraceRecords {...initial} />);
  await expand();
  await waitFor(() => expect(read).toHaveBeenCalledTimes(1));
  view.rerender(<TraceRecords {...initial} source={change === "source" ? "IMPORTED" : "TARGET"} scopeGeneration={change === "generation" ? 1 : 0} traceId={change === "trace" ? "trace-2" : "trace-1"} />);
  await act(async () => resolve(range(JSON.stringify(payload))));
  expect(screen.queryByText(payload.message, { normalizer: (value) => value })).toBeNull();
  expect(screen.getByRole("button", { name: "Diagnostic details" })).toHaveAttribute("aria-expanded", "false");
});

test.each(["ARTIFACT_EXPIRED", "NOT_FOUND"])("reports %s as presentation failure while preserving recorded outcome", async (code) => {
  const onArtifactUnavailable = vi.fn();
  read.mockRejectedValue(Object.assign(new Error("gone"), { code }));
  const current = record(); delete current.content!.inlineContent;
  render(<TraceRecords {...props(current)} onArtifactUnavailable={onArtifactUnavailable} />);
  const detail = await expand();
  expect(await within(detail).findByRole("alert")).toHaveTextContent("Detail could not be displayed: gone");
  expect(onArtifactUnavailable).toHaveBeenCalledTimes(1);
  expect(detail).toHaveTextContent("SUCCEEDED");
  expect(detail).not.toHaveTextContent("CAPTURE_FAILED");
});

test("bounds detail work and keeps the existing ranged content action", async () => {
  const current = record(); current.content!.retainedBytes = 1024 * 1024 + 1;
  render(<TraceRecords {...props(current)} />);
  const detail = await expand();
  expect(await within(detail).findByRole("alert")).toHaveTextContent("limited to 1 MiB");
  expect(screen.getByRole("button", { name: "Read content" })).toBeVisible();
  expect(read).not.toHaveBeenCalled();
});

test.each(["FRAME_OPENED", "FRAME_CLOSED", "FRAME_METADATA_RECORDED"])("presents retained %s boundary evidence and recorded failure", async (type) => {
  const current = record({ executorName: "local", executorConfigGenerationId: "generation-7", missionInput: { id: 42 }, executorOutput: "  completed\n雪  " }, { type });
  render(<TraceRecords {...props(current)} failures={[{ classification: "RECOVERABLE", failureId: "failure-1", frameId: "executor-child", sequence: 3 } as never]} />);
  const detail = await expand("Frame details");
  await waitFor(() => expect(detail).toHaveTextContent("generation-7"));
  expect(detail).toHaveTextContent("Executor: local");
  expect(within(detail).getByText("Recorded input")).toBeInTheDocument();
  expect(within(detail).getByText("  completed\n雪  ", { normalizer: (value) => value })).toBeVisible();
  expect(within(detail).getByRole("button", { name: "RECOVERABLE: failure-1" })).toBeVisible();
});

test("retains JSON numeric lexemes beyond JavaScript integer precision", async () => {
  const current = record();
  current.content!.inlineContent = '{ "severity":"INFO", "message":"number", "messageState":"COMPLETE", "dataState":"COMPLETE", "data": { "units":9007199254740993, "ratio":1.0000000000000001 } }';
  render(<TraceRecords {...props(current)} />);
  const detail = await expand();
  await waitFor(() => expect(detail).toHaveTextContent("Authored severity"));
  expect(within(detail).getByText(current.content!.inlineContent, { normalizer: (value) => value })).toBeInTheDocument();
  expect(detail).not.toHaveTextContent("9007199254740992");
});

test("preserves one chronological row per concurrent event without eager content reads", () => {
  const records = [record(payload, { sequence: 4 }), record(payload, { sequence: 5, threadName: "another-worker" }), record({ reason: "DATA_LIMIT", message: "notice", originalMessageState: "UNAVAILABLE" }, { sequence: 6, type: "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED" })];
  render(<TraceRecords {...props()} records={records} />);
  const rows = screen.getAllByRole("row").filter((row) => row.hasAttribute("aria-label"));
  expect(rows.map((row) => row.getAttribute("aria-label"))).toEqual(["Record 4, EXECUTOR_DIAGNOSTIC_RECORDED", "Record 5, EXECUTOR_DIAGNOSTIC_RECORDED", "Record 6, EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED"]);
  expect(read).not.toHaveBeenCalled();
});

test("rejects repeated content continuation as presentation failure", async () => {
  const current = record(); delete current.content!.inlineContent;
  read.mockResolvedValue(range("{", { hasMore: true, nextCursor: "same" }));
  render(<TraceRecords {...props(current)} />);
  const detail = await expand();
  expect(await within(detail).findByRole("alert")).toHaveTextContent("Content continuation was invalid");
  expect(read).toHaveBeenCalledTimes(2);
  expect(detail).toHaveTextContent("SUCCEEDED");
});
