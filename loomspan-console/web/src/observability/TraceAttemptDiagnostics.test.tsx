import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, test, vi } from "vitest";
import * as api from "../api/client";
import type { TraceRange } from "../api/contracts";
import { TraceAttemptDiagnostics } from "./TraceAttemptDiagnostics";

vi.mock("../api/client", () => ({ getContentRange: vi.fn() }));

function range(content: string, actualStart: number, totalLength: number, hasMore = false, nextCursor: string | null = null): TraceRange {
  return { source: "TARGET", targetScopeId: "scope-1", actualStart, actualEnd: actualStart + new TextEncoder().encode(content).length, totalLength, contentType: "application/json", encoding: "TEXT", content, hasMore, nextCursor };
}

function base64Range(bytes: Uint8Array, actualStart: number, totalLength: number, hasMore = false, nextCursor: string | null = null): TraceRange {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return { source: "TARGET", targetScopeId: "scope-1", actualStart, actualEnd: actualStart + bytes.length, totalLength, contentType: "application/json", encoding: "BASE64", content: btoa(binary), hasMore, nextCursor };
}

const payload = JSON.stringify({ diagnostics: [
  { kind: "JAVA_STACK_TRACE", contentType: "text/plain; charset=utf-8", text: "wrapper\n<script>alert(1)</script>\nCaused by: timeout", truncated: true, captureLimitBytes: 1048576 },
  { kind: "PROVIDER_ERROR", contentType: "application/json", text: "{\"message\":\"overloaded\"}", truncated: false, captureLimitBytes: 4096 },
] });

beforeEach(() => vi.resetAllMocks());

test("loads every range and renders stack and provider diagnostics in recorded order", async () => {
  const split = Math.floor(payload.length / 2);
  const first = payload.slice(0, split);
  const second = payload.slice(split);
  const total = new TextEncoder().encode(payload).length;
  vi.mocked(api.getContentRange)
    .mockResolvedValueOnce(range(first, 0, total, true, "next-1"))
    .mockResolvedValueOnce(range(second, new TextEncoder().encode(first).length, total));

  render(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={6} contentRef="opaque" />);

  expect(await screen.findByText("Java stack trace")).toBeInTheDocument();
  expect(api.getContentRange).toHaveBeenNthCalledWith(1, "trace-1", "opaque", undefined, "TARGET");
  expect(api.getContentRange).toHaveBeenNthCalledWith(2, "trace-1", "opaque", "next-1", "TARGET");
  const headings = screen.getAllByRole("heading", { level: 6 });
  expect(headings.map((heading) => heading.textContent)).toEqual(["Java stack trace", "Text diagnostic (PROVIDER_ERROR)"]);
  expect(screen.getByText("Yes")).toBeInTheDocument();
  expect(screen.getByText("1048576 bytes")).toBeInTheDocument();
  expect(screen.getByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument();
  expect(document.querySelector("script")).toBeNull();
  expect(screen.getByText('{"message":"overloaded"}')).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: "Collapse diagnostics" }));
  expect(screen.queryByText("Java stack trace")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Expand diagnostics" }));
  expect(screen.getByText("Java stack trace")).toBeInTheDocument();
});

test("reconstructs a valid UTF-8 payload when a continuation splits a multibyte rune", async () => {
  const prefix = '{"diagnostics":[{"kind":"JAVA_STACK_TRACE","contentType":"text/plain; charset=utf-8","text":"wrapper ';
  const suffix = '😀 localized timeout","truncated":false,"captureLimitBytes":1048576}]}';
  const unicodePayload = prefix + "x".repeat(65535 - new TextEncoder().encode(prefix).length) + suffix;
  const bytes = new TextEncoder().encode(unicodePayload);
  const emojiStart = bytes.findIndex((byte, index) => byte === 0xf0 && bytes[index + 1] === 0x9f);
  expect(emojiStart).toBe(65535);
  vi.mocked(api.getContentRange)
    .mockResolvedValueOnce(base64Range(bytes.slice(0, 65536), 0, bytes.length, true, "split-1"))
    .mockResolvedValueOnce(base64Range(bytes.slice(65536), 65536, bytes.length));

  render(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={6} contentRef="unicode" />);

  expect(await screen.findByText(/wrapper .* localized timeout/)).toHaveTextContent("😀 localized timeout");
  expect(api.getContentRange).toHaveBeenCalledTimes(2);
});

test("reports malformed, invalid-base64, repeated-cursor, and range load failures without partial evidence", async () => {
  vi.mocked(api.getContentRange).mockResolvedValueOnce({ ...range("ignored", 0, 7), encoding: "BASE64", content: "not valid base64***" });
  const { rerender } = render(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={1} contentRef="invalid-base64" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("invalid base64");

  vi.mocked(api.getContentRange)
    .mockResolvedValueOnce(range("{", 0, 2, true, "repeat"))
    .mockResolvedValueOnce(range("}", 1, 2, true, "repeat"));
  rerender(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={2} contentRef="repeat" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("invalid or repeated");

  vi.mocked(api.getContentRange).mockResolvedValueOnce(range("{}", 0, 2));
  rerender(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={3} contentRef="malformed" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("no diagnostics array");

  vi.mocked(api.getContentRange).mockRejectedValueOnce(new Error("range unavailable"));
  rerender(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={4} contentRef="failure" />);
  expect(await screen.findByRole("alert")).toHaveTextContent("range unavailable");
  expect(screen.queryByText("Java stack trace")).toBeNull();
});

test("discards stale target responses after scope changes before verification", async () => {
  let resolveOld!: (value: TraceRange) => void;
  vi.mocked(api.getContentRange)
    .mockReturnValueOnce(new Promise((resolve) => { resolveOld = resolve; }))
    .mockResolvedValueOnce(range(payload, 0, new TextEncoder().encode(payload).length));
  const verifyScope = vi.fn(async (value: TraceRange) => value);
  const { rerender } = render(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={1} contentRef="opaque" scopeGeneration={1} verifyScope={verifyScope} />);
  rerender(<TraceAttemptDiagnostics traceId="trace-1" recordSequence={1} contentRef="opaque" scopeGeneration={2} verifyScope={verifyScope} />);
  await act(async () => resolveOld(range(payload.replace("wrapper", "stale"), 0, new TextEncoder().encode(payload.replace("wrapper", "stale")).length)));
  await screen.findByText("Java stack trace");
  await waitFor(() => expect(api.getContentRange).toHaveBeenCalledTimes(2));
  expect(screen.queryByText(/stale/)).toBeNull();
  expect(verifyScope).not.toHaveBeenCalledWith(expect.objectContaining({ content: expect.stringContaining("stale") }));
});

test("keeps imported diagnostics across unrelated target generations", async () => {
  vi.mocked(api.getContentRange).mockResolvedValueOnce({ ...range(payload, 0, new TextEncoder().encode(payload).length), source: "IMPORTED", targetScopeId: undefined });
  const { rerender } = render(<TraceAttemptDiagnostics traceId="trace-1" source="IMPORTED" recordSequence={1} contentRef="opaque" scopeGeneration={1} />);
  expect(await screen.findByText("Java stack trace")).toBeInTheDocument();
  rerender(<TraceAttemptDiagnostics traceId="trace-1" source="IMPORTED" recordSequence={1} contentRef="opaque" scopeGeneration={2} />);
  expect(api.getContentRange).toHaveBeenCalledTimes(1);
  expect(screen.getByText("Java stack trace")).toBeInTheDocument();
});
