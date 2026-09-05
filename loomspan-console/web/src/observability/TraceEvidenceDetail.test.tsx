import { render, screen } from "@testing-library/react";
import { expect, test, vi } from "vitest";
import type { TraceRange } from "../api/contracts";
import { TraceEvidenceDetail } from "./TraceEvidenceDetail";

function range(content: string, overrides: Partial<TraceRange> = {}): TraceRange {
  const bytes = new TextEncoder().encode(content).length;
  return { source: "TARGET", targetScopeId: "scope-1", actualStart: 0, actualEnd: bytes,
    totalLength: bytes, contentType: "application/json", encoding: "TEXT", content,
    hasMore: false, nextCursor: null, ...overrides };
}

function show(value: TraceRange) {
  const rendered = render(<TraceEvidenceDetail range={value} onNext={vi.fn()} onClear={vi.fn()} />);
  return { ...rendered, output: () => rendered.container.querySelector("pre")?.textContent };
}

test.each(["application/json", "Application/JSON; charset=utf-8", "application/problem+json"])(
  "formats complete %s content with nested arrays and objects", (contentType) => {
    const value = { planId: "plan-1", tasks: [{ title: "Café", status: "PENDING", dependsOn: [] }], extra: {} };
    const content = JSON.stringify(value);
    const { output } = show(range(content, { contentType }));
    expect(output()).toBe(JSON.stringify(value, null, 2));
    expect(screen.getByText(/Text bytes/)).toHaveTextContent(`of ${new TextEncoder().encode(content).length}`);
  },
);

test("preserves number spelling, duplicate keys, key order, escapes, and inert strings", () => {
  const content = '{"2":9007199254740993,"1":1e400,"2":-0,"text":"\\u0041 \\\" [,{}:] </script><img src=x>"}';
  const { output, container } = show(range(content));
  expect(output()).toBe('{\n  "2": 9007199254740993,\n  "1": 1e400,\n  "2": -0,\n  "text": "\\u0041 \\\" [,{}:] </script><img src=x>"\n}');
  expect(container.querySelector("script, img")).toBeNull();
});

test.each([
  { contentType: "text/plain" },
  { encoding: "BASE64" as const },
  { actualStart: 10, actualEnd: 19, totalLength: 19 },
  { actualEnd: 9, totalLength: 20, hasMore: true, nextCursor: "next" },
  { actualEnd: 9, totalLength: 20 },
])("leaves non-JSON, encoded, and partial ranges unchanged: %j", (overrides) => {
  const content = '{"a":  1}';
  expect(show(range(content, overrides)).output()).toBe(content);
});

test("leaves malformed JSON unchanged", () => {
  expect(show(range('{"a":')).output()).toBe('{"a":');
});

test("updates the formatted output when a different record is selected", () => {
  const view = show(range('{"a":1}'));
  view.rerender(<TraceEvidenceDetail range={range('{"b":2}')} onNext={vi.fn()} onClear={vi.fn()} />);
  expect(view.output()).toBe('{\n  "b": 2\n}');
});
