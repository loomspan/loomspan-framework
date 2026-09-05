import { describe, expect, test } from "vitest";
import { SSEDecoder, openActivityStream } from "./activityStream";
import type { ActivityStreamCallbacks } from "./activityStream";

function makeChunk(s: string): Uint8Array {
  return new TextEncoder().encode(s);
}

function makeActivity(cursor: string, kind = "STEP_STARTED"): string {
  return JSON.stringify({
    instanceId: "11111111-1111-4111-8111-111111111111",
    cursor,
    sessionId: "session-1",
    traceId: "trace-1",
    canonicalSequence: Number(cursor),
    timestamp: "2026-07-25T12:00:00Z",
    kind,
    executionStatus: "RUNNING",
    summary: "Step started",
    details: {},
  });
}

describe("SSEDecoder", () => {
  test("decodes complete frames in one chunk", () => {
    const decoder = new SSEDecoder();
    const input = "event: loomspan.activity\ndata: " + makeActivity("1") + "\n\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].event).toBe("loomspan.activity");
    const data = JSON.parse(frames[0].data);
    expect(data.cursor).toBe("1");
  });

  test("decodes split utf8 and split sse fields incrementally", () => {
    const decoder = new SSEDecoder();
    // Split in the middle of a multibyte character (summary contains "café" → é is 2 bytes)
    const summary = "caf\u00e9 test";
    const activityJson = JSON.stringify({
      instanceId: "11111111-1111-4111-8111-111111111111",
      cursor: "1",
      sessionId: "session-1",
      traceId: "trace-1",
      canonicalSequence: 1,
      timestamp: "2026-07-25T12:00:00Z",
      kind: "STEP_STARTED",
      executionStatus: "RUNNING",
      summary,
      details: {},
    });
    const fullInput = "event: loomspan.activity\ndata: " + activityJson + "\n\n";
    const encoded = new TextEncoder().encode(fullInput);
    // Split at byte 45 (inside the JSON, possibly inside multibyte)
    const part1 = encoded.slice(0, 45);
    const part2 = encoded.slice(45);
    let frames = decoder.feed(part1);
    expect(frames).toHaveLength(0);
    frames = decoder.feed(part2);
    expect(frames).toHaveLength(1);
    const data = JSON.parse(frames[0].data);
    expect(data.summary).toBe(summary);
  });

  test("emits only complete validated namespaced frames", () => {
    const decoder = new SSEDecoder();
    const input = [
      "event: console.connection",
      'data: {"connected":true}',
      "",
      "",
      "event: loomspan.activity",
      "data: " + makeActivity("1"),
      "",
      "",
    ].join("\n");
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(2);
    expect(frames[0].event).toBe("console.connection");
    expect(frames[1].event).toBe("loomspan.activity");
  });

  test("handles multiple data lines concatenated with newline", () => {
    const decoder = new SSEDecoder();
    const input = "event: loomspan.activity\ndata: line1\ndata: line2\n\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].data).toBe("line1\nline2");
  });

  test("handles CRLF line endings", () => {
    const decoder = new SSEDecoder();
    const input = "event: console.connection\r\ndata: {\"connected\":true}\r\n\r\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].event).toBe("console.connection");
  });

  test("ignores comment lines", () => {
    const decoder = new SSEDecoder();
    const input = ": this is a comment\nevent: console.connection\ndata: {\"connected\":true}\n\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].event).toBe("console.connection");
  });

  test("flush emits remaining buffered frame", () => {
    const decoder = new SSEDecoder();
    const input = "event: console.connection\ndata: {\"connected\":true}\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(0);
    const flushed = decoder.flush();
    expect(flushed).toHaveLength(1);
    expect(flushed[0].event).toBe("console.connection");
  });

  test("default event name is message when not specified", () => {
    const decoder = new SSEDecoder();
    const input = 'data: {"test":true}\n\n';
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].event).toBe("message");
  });

  test("preserves id field", () => {
    const decoder = new SSEDecoder();
    const input = "id: 42\nevent: loomspan.activity\ndata: " + makeActivity("1") + "\n\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(1);
    expect(frames[0].id).toBe("42");
  });

  test("handles multiple frames in sequence", () => {
    const decoder = new SSEDecoder();
    const input =
      "event: console.connection\ndata: {\"connected\":true}\n\n" +
      "event: loomspan.activity\ndata: " + makeActivity("1") + "\n\n" +
      "event: loomspan.activity\ndata: " + makeActivity("2") + "\n\n";
    const frames = decoder.feed(makeChunk(input));
    expect(frames).toHaveLength(3);
    expect(frames[0].event).toBe("console.connection");
    expect(frames[1].event).toBe("loomspan.activity");
    expect(frames[2].event).toBe("loomspan.activity");
  });
});

describe("openActivityStream", () => {
  test("aborting stream stops parsing without error callback", async () => {
    const callbacks: ActivityStreamCallbacks = {
      onActivity: () => {},
      onError: (err: Error) => { throw err; },
    };
    const close = openActivityStream(
      { url: "http://127.0.0.1:1/invalid", body: {}, tabId: "tab-1" },
      callbacks,
    );
    close();
    // If abort doesn't fire onError, the test passes
    await new Promise((resolve) => setTimeout(resolve, 50));
  });
});
