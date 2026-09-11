import { useEffect, useRef, useState } from "react";
import { getContentRange } from "../api/client";
import type { TraceAttemptDiagnosticDescriptor, TraceAttemptDiagnosticPayload, TraceRange, TraceSource } from "../api/contracts";

type Props = {
  traceId: string;
  source?: TraceSource;
  recordSequence: number;
  contentRef: string;
  scopeGeneration?: number;
  verifyScope?: (response: TraceRange) => Promise<TraceRange>;
  onArtifactUnavailable?: (error: unknown) => void;
  ariaLabel?: string;
};

type LoadedDiagnostics = {
  payload: TraceAttemptDiagnosticPayload;
  decodedBytes: number[];
  retainedBytes: number;
};

function decodeRangeBytes(range: TraceRange): Uint8Array {
  if (range.encoding === "TEXT") {
    return new TextEncoder().encode(range.content);
  }
  try {
    const binary = atob(range.content);
    return Uint8Array.from(binary, (character) => character.charCodeAt(0));
  } catch {
    throw new Error("Attempt diagnostic content contained invalid base64 data.");
  }
}

function parseDescriptor(value: unknown, index: number): TraceAttemptDiagnosticDescriptor {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error(`Diagnostic ${index + 1} is not an object.`);
  }
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.kind !== "string" || candidate.kind.trim() === "") {
    throw new Error(`Diagnostic ${index + 1} has no valid kind.`);
  }
  if (typeof candidate.contentType !== "string" || candidate.contentType.trim() === "") {
    throw new Error(`Diagnostic ${index + 1} has no valid content type.`);
  }
  if (typeof candidate.text !== "string") {
    throw new Error(`Diagnostic ${index + 1} has no valid text.`);
  }
  if (typeof candidate.truncated !== "boolean") {
    throw new Error(`Diagnostic ${index + 1} has no valid truncation state.`);
  }
  if (!Number.isSafeInteger(candidate.captureLimitBytes) || (candidate.captureLimitBytes as number) < 0) {
    throw new Error(`Diagnostic ${index + 1} has no valid capture bound.`);
  }
  return candidate as TraceAttemptDiagnosticDescriptor;
}

function parsePayload(bytes: Uint8Array): LoadedDiagnostics {
  let value: unknown;
  try {
    const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    value = JSON.parse(text) as unknown;
  } catch {
    throw new Error("Attempt diagnostic content is not complete valid UTF-8 JSON.");
  }
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("Attempt diagnostic content is not an object.");
  }
  const diagnostics = (value as Record<string, unknown>).diagnostics;
  if (!Array.isArray(diagnostics)) {
    throw new Error("Attempt diagnostic content has no diagnostics array.");
  }
  const parsed = diagnostics.map(parseDescriptor);
  return {
    payload: { diagnostics: parsed },
    decodedBytes: parsed.map((diagnostic) => new TextEncoder().encode(diagnostic.text).length),
    retainedBytes: bytes.length,
  };
}

function kindLabel(kind: string) {
  return kind === "JAVA_STACK_TRACE" ? "Java stack trace"
    : kind === "LOOMSPAN_PROVIDER_GUIDANCE" ? "Loomspan provider guidance"
    : `Text diagnostic (${kind})`;
}

export function TraceAttemptDiagnostics({ traceId, source = "TARGET", recordSequence, contentRef, scopeGeneration = 0, verifyScope, onArtifactUnavailable, ariaLabel }: Props) {
  const evidenceGeneration = source === "TARGET" ? scopeGeneration : 0;
  const unavailableCallback = useRef(onArtifactUnavailable);
  unavailableCallback.current = onArtifactUnavailable;
  const requestGeneration = useRef(0);
  const [loaded, setLoaded] = useState<LoadedDiagnostics>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [expanded, setExpanded] = useState(true);
  const [wrap, setWrap] = useState(true);

  useEffect(() => {
    const generation = ++requestGeneration.current;
    setLoaded(undefined);
    setError(undefined);
    setLoading(true);
    setExpanded(true);
    const load = async () => {
      const parts: Uint8Array[] = [];
      const cursors = new Set<string>();
      let cursor: string | undefined;
      let expectedStart = 0;
      let totalLength: number | undefined;
      do {
        const response = await getContentRange(traceId, contentRef, cursor, source);
        if (generation !== requestGeneration.current) return;
        const range = verifyScope ? await verifyScope(response) : response;
        if (generation !== requestGeneration.current) return;
        const bytes = decodeRangeBytes(range);
        if (range.actualStart !== expectedStart || range.actualEnd !== range.actualStart + bytes.length) {
          throw new Error("Attempt diagnostic ranges were not contiguous.");
        }
        if (totalLength !== undefined && range.totalLength !== totalLength) {
          throw new Error("Attempt diagnostic length changed while loading.");
        }
        totalLength = range.totalLength;
        parts.push(bytes);
        expectedStart = range.actualEnd;
        if (range.hasMore) {
          if (!range.nextCursor || cursors.has(range.nextCursor)) {
            throw new Error("Attempt diagnostic continuation was invalid or repeated.");
          }
          cursors.add(range.nextCursor);
          cursor = range.nextCursor;
        } else {
          if (range.nextCursor || range.actualEnd !== range.totalLength) {
            throw new Error("Attempt diagnostic content ended before it was complete.");
          }
          cursor = undefined;
        }
      } while (cursor);
      const bytes = new Uint8Array(parts.reduce((sum, part) => sum + part.length, 0));
      let offset = 0;
      for (const part of parts) {
        bytes.set(part, offset);
        offset += part.length;
      }
      if (bytes.length !== totalLength) throw new Error("Attempt diagnostic content ended before it was complete.");
      if (generation === requestGeneration.current) setLoaded(parsePayload(bytes));
    };
    void load().catch((value: unknown) => {
      if (value instanceof Error && "code" in value && (value.code === "ARTIFACT_EXPIRED" || value.code === "NOT_FOUND")) unavailableCallback.current?.(value);
      if (generation !== requestGeneration.current) return;
      setError(value instanceof Error ? value.message : "Attempt diagnostics could not be loaded.");
    }).finally(() => {
      if (generation === requestGeneration.current) setLoading(false);
    });
    return () => { requestGeneration.current += 1; };
  }, [contentRef, evidenceGeneration, source, traceId, verifyScope]);

  return <section className="attempt-diagnostics" aria-label={ariaLabel ?? `Attempt diagnostics for record ${recordSequence}`}>
    <h5>Attempt diagnostics</h5>
    {loading && <p role="status">Loading all attempt diagnostic content&hellip;</p>}
    {error && <p role="alert">{error}</p>}
    {loaded && <>
      <p className="trace-evidence-meta">{loaded.payload.diagnostics.length} diagnostic{loaded.payload.diagnostics.length === 1 ? "" : "s"} &middot; {loaded.retainedBytes} retained bytes</p>
      <div className="trace-detail-actions">
        <button type="button" aria-expanded={expanded} onClick={() => setExpanded((value) => !value)}>{expanded ? "Collapse diagnostics" : "Expand diagnostics"}</button>
        <button type="button" onClick={() => setWrap((value) => !value)}>{wrap ? "Disable wrapping" : "Enable wrapping"}</button>
      </div>
      {expanded && <ol className="attempt-diagnostic-list">{loaded.payload.diagnostics.map((diagnostic, index) => <li key={`${index}-${diagnostic.kind}`}>
        <h6>{kindLabel(diagnostic.kind)}</h6>
        <dl className="trace-facts">
          <div><dt>Kind</dt><dd><code>{diagnostic.kind}</code></dd></div>
          <div><dt>Content type</dt><dd>{diagnostic.contentType}</dd></div>
          <div><dt>Decoded bytes</dt><dd>{loaded.decodedBytes[index]}</dd></div>
          <div><dt>Truncated</dt><dd>{diagnostic.truncated ? "Yes" : "No"}</dd></div>
          <div><dt>Capture bound</dt><dd>{diagnostic.captureLimitBytes} bytes</dd></div>
        </dl>
        <pre style={{ whiteSpace: wrap ? "pre-wrap" : "pre", overflowX: "auto" }}>{diagnostic.text}</pre>
      </li>)}</ol>}
    </>}
  </section>;
}
