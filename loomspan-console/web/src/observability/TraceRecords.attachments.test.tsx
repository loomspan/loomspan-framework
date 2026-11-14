import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import { getContentRange, getRawRecordRange, getTraceRecords } from "../api/client";
import type { TraceRange, TraceRecord } from "../api/contracts";
import { TraceRecords } from "./TraceRecords";

vi.mock("../api/client", () => ({ getContentRange: vi.fn(), getRawRecordRange: vi.fn(), getTraceRecords: vi.fn() }));
const descriptor = { attachment: true, fieldPath: "/files/0", name: "<img src=x>雪", contentType: "application/pdf", mediaType: "PDF", source: "ref://documents/file", sizeBytes: 0, sha256: "hash", metadata: { Authorization: "kept", token: "verbatim", nested: { evidence: "x", "x-loomspan-evidence": "y" } } };
function record(payload: unknown = { attachments: [descriptor] }): TraceRecord {
  return { sequence: 7, type: "FRAME_METADATA_RECORDED", frameId: "mission", parentFrameId: "", frameType: "ROOT_MISSION", route: "skill", threadName: "worker", timestampMillis: 7, representation: "LOGICAL", isChunk: false, isEnvelope: false,
    content: { role: "DATA", contentType: "application/json", encoding: "UTF8", retainedBytes: 2, available: true, complete: true, inlineEligibility: true, inlineContent: JSON.stringify(payload) } };
}
function props(value = record()) { return { traceId: "trace-1", records: [value], failures: [], onSelectRecord: vi.fn(), onSelectFailure: vi.fn(), onContent: vi.fn() }; }
function range(content: string, overrides: Partial<TraceRange> = {}): TraceRange { return { source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: content.length, totalLength: content.length, contentType: "application/json", encoding: "TEXT", content, hasMore: false, nextCursor: null, ...overrides }; }
beforeEach(() => vi.resetAllMocks());

test("renders recorded inline attachments only after explicit selection", async () => {
  render(<TraceRecords {...props()} />);
  expect(screen.queryByRole("region", { name: "Attachments for record 7" })).toBeNull();
  expect(getContentRange).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  const detail = await screen.findByRole("region", { name: "Attachments for record 7" });
  expect(await within(detail).findByText(JSON.stringify({ attachments: [descriptor] }), { exact: true, normalizer: (text) => text })).toBeInTheDocument();
  expect(detail.querySelector("img,a,iframe,script")).toBeNull();
  expect(getContentRange).not.toHaveBeenCalled();
  expect(getRawRecordRange).not.toHaveBeenCalled();
  expect(getTraceRecords).not.toHaveBeenCalled();
});

test("retains descriptor order, direct resources, null names and absent optional fields", async () => {
  const direct = { attachment: true, fieldPath: "/nested/a~1b~0c", name: null, source: "resource", contentType: "image/png", mediaType: "IMAGE" };
  render(<TraceRecords {...props(record({ attachments: [descriptor, direct] }))} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  const sections = await screen.findAllByRole("region", { name: "Attachments for record 7" });
  const output = sections[0].querySelectorAll("pre");
  expect(output).toHaveLength(1);
  expect(JSON.parse(output[0].textContent!).attachments).toEqual([descriptor, direct]);
  fireEvent.click(screen.getByRole("button", { name: "Hide attachments" }));
  expect(screen.queryByRole("region", { name: "Attachments for record 7" })).toBeNull();
});

test("reconstructs all semantic byte ranges before decoding Unicode and verifies each imported scope", async () => {
  const value = record();
  delete value.content!.inlineContent;
  value.content!.contentRef = "logical-7";
  value.isEnvelope = true;
  const bytes = new TextEncoder().encode(JSON.stringify({ attachments: [descriptor] }));
  const split = bytes.findIndex((byte) => byte > 127) + 1;
  const base64 = (part: Uint8Array) => btoa(String.fromCharCode(...part));
  vi.mocked(getContentRange)
    .mockResolvedValueOnce(range(base64(bytes.slice(0, split)), { source: "IMPORTED", encoding: "BASE64", hasMore: true, nextCursor: "next", actualEnd: split, totalLength: bytes.length }))
    .mockResolvedValueOnce(range(base64(bytes.slice(split)), { source: "IMPORTED", encoding: "BASE64", actualStart: split, actualEnd: bytes.length, totalLength: bytes.length }));
  const verifyScope = vi.fn(async (response: TraceRange) => response);
  render(<TraceRecords {...props(value)} source="IMPORTED" verifyScope={verifyScope} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByText(JSON.stringify({ attachments: [descriptor] }), { normalizer: (text) => text })).toBeInTheDocument();
  expect(getContentRange).toHaveBeenNthCalledWith(1, "trace-1", "logical-7", undefined, "IMPORTED");
  expect(getContentRange).toHaveBeenNthCalledWith(2, "trace-1", "logical-7", "next", "IMPORTED");
  expect(verifyScope).toHaveBeenCalledTimes(2);
  expect(getRawRecordRange).not.toHaveBeenCalled();
});

test.each([
  [{}, "No attachment collection was recorded."],
  [{ attachments: [] }, "The recorded attachment collection is empty."],
])("distinguishes absent and empty collections (%j)", async (payload, message) => {
  render(<TraceRecords {...props(record(payload))} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(screen.queryByRole("alert")).toBeNull();
});

test.each(["not json", "[]", "null", '{"attachments":null}', '{"attachments":{}}', '{"attachments":[null]}', '{"attachments":[[]]}', '{"attachments":[1]}'])
("reports malformed content without partial descriptors (%s)", async (raw) => {
  const value = record(); value.content!.inlineContent = raw;
  render(<TraceRecords {...props(value)} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Attachments could not be displayed");
  expect(screen.getByRole("region", { name: "Attachments for record 7" }).querySelector("pre")).toBeNull();
});

test.each(["missing", "unavailable", "incomplete", "no-location"])("reports %s capture without treating it as an empty collection", async (kind) => {
  const value = record();
  if (kind === "missing") delete value.content;
  if (kind === "unavailable") value.content!.available = false;
  if (kind === "incomplete") value.content!.complete = false;
  if (kind === "no-location") delete value.content!.inlineContent;
  render(<TraceRecords {...props(value)} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(kind === "incomplete" ? "incomplete" : "unavailable");
  expect(getContentRange).not.toHaveBeenCalled();
});

test.each(["rejection", "continuation", "invalid-base64", "invalid-utf8", "scope"])("reports ranged %s failure without partial success", async (kind) => {
  const value = record(); delete value.content!.inlineContent; value.content!.contentRef = "logical-7";
  if (kind === "rejection") vi.mocked(getContentRange).mockRejectedValue(new Error("range unavailable"));
  else vi.mocked(getContentRange).mockResolvedValue(range(kind === "invalid-base64" ? "!" : kind === "invalid-utf8" ? "/w==" : "{}", { encoding: kind.startsWith("invalid-") ? "BASE64" : "TEXT", hasMore: kind === "continuation" }));
  const verifyScope = vi.fn(async (response: TraceRange) => { if (kind === "scope") throw new Error("scope changed"); return response; });
  render(<TraceRecords {...props(value)} verifyScope={verifyScope} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Attachments could not be displayed");
  expect(screen.getByRole("region", { name: "Attachments for record 7" }).querySelector("pre")).toBeNull();
});

test("reports expired artifacts through the existing callback", async () => {
  const value = record(); delete value.content!.inlineContent; value.content!.contentRef = "logical-7";
  const error = Object.assign(new Error("expired"), { code: "ARTIFACT_EXPIRED" });
  vi.mocked(getContentRange).mockRejectedValue(error);
  const onArtifactUnavailable = vi.fn();
  render(<TraceRecords {...props(value)} onArtifactUnavailable={onArtifactUnavailable} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("expired");
  expect(onArtifactUnavailable).toHaveBeenCalledWith(error);
});

test.each(["source", "generation", "trace", "removed"])("discards pending attachments after %s changes", async (change) => {
  const value = record(); delete value.content!.inlineContent; value.content!.contentRef = "logical-7";
  let finish!: (response: TraceRange) => void;
  vi.mocked(getContentRange).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
  const original = props(value);
  const view = render(<TraceRecords {...original} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  const changed = { ...original, ...(change === "source" ? { source: "IMPORTED" as const } : change === "generation" ? { scopeGeneration: 2 } : change === "trace" ? { traceId: "trace-2" } : { records: [] }) };
  view.rerender(<TraceRecords {...changed} />);
  finish(range(JSON.stringify({ attachments: [descriptor] })));
  await Promise.resolve();
  expect(screen.queryByRole("region", { name: "Attachments for record 7" })).toBeNull();
  if (change === "removed") view.rerender(<TraceRecords {...original} />);
  expect(screen.queryByRole("region", { name: "Attachments for record 7" })).toBeNull();
});

test("raw record inspection remains available and hides attachment details", async () => {
  vi.mocked(getRawRecordRange).mockResolvedValue(range('{"data":{"attachments":[]}}'));
  render(<TraceRecords {...props()} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  await screen.findByRole("heading", { name: "Recorded attachment descriptors (1)" });
  fireEvent.click(screen.getByRole("button", { name: "Read raw record" }));
  expect(await screen.findByRole("region", { name: "Raw record 7" })).toHaveTextContent("attachments");
  expect(screen.queryByRole("region", { name: "Attachments for record 7" })).toBeNull();
});

test("does not verify stale scope or request further ranges after attachment inspection closes", async () => {
  const value = record(); delete value.content!.inlineContent; value.content!.contentRef = "logical-7";
  let finish!: (response: TraceRange) => void;
  vi.mocked(getContentRange).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
  const verifyScope = vi.fn(async (response: TraceRange) => response);
  render(<TraceRecords {...props(value)} verifyScope={verifyScope} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  fireEvent.click(screen.getByRole("button", { name: "Hide attachments" }));
  await act(async () => { finish(range("partial", { hasMore: true, nextCursor: "next" })); });
  expect(verifyScope).not.toHaveBeenCalled();
  expect(getContentRange).toHaveBeenCalledTimes(1);
});

test("displays exact recorded numeric lexemes and string whitespace", async () => {
  const raw = '{ "attachments": [ {"metadata":{"large":9007199254740993,"decimal":0.1000000000000000000000001,"text":"  exact\\r\\n雪  "}} ] }';
  const value = record(); value.content!.inlineContent = raw;
  render(<TraceRecords {...props(value)} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByText(raw, { normalizer: (text) => text })).toBeInTheDocument();
});

test("does not load attachment content without trace context", async () => {
  render(<TraceRecords {...props()} traceId={undefined} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Trace context is unavailable");
  expect(getContentRange).not.toHaveBeenCalled();
});

test("complete inline content takes precedence over a content reference", async () => {
  const value = record(); value.content!.contentRef = "do-not-fetch";
  render(<TraceRecords {...props(value)} />);
  fireEvent.click(screen.getByRole("button", { name: "Attachments" }));
  expect(await screen.findByRole("heading", { name: "Recorded attachment descriptors (1)" })).toBeInTheDocument();
  expect(getContentRange).not.toHaveBeenCalled();
});
