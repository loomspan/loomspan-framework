import { useEffect, useMemo, useRef } from "react";
import type { TraceRange } from "../api/contracts";

function formatContent(range: TraceRange): string {
  const mediaType = range.contentType.split(";", 1)[0].trim().toLowerCase();
  if (range.encoding !== "TEXT" || range.actualStart !== 0 || range.actualEnd !== range.totalLength || range.hasMore ||
      !(mediaType === "application/json" || mediaType.endsWith("+json"))) return range.content;
  try {
    JSON.parse(range.content);
  } catch {
    return range.content;
  }

  // Change only whitespace: parsing and reserializing would round large numbers
  // and discard duplicate keys in the recorded evidence.
  const tokens = range.content.match(/"(?:\\[\s\S]|[^"\\])*"|[^\s{}\[\],:]+|[{}\[\],:]/g) ?? [];
  let depth = 0;
  let formatted = "";
  const newline = () => "\n" + "  ".repeat(depth);
  tokens.forEach((token, index) => {
    if (token === "{" || token === "[") {
      formatted += token;
      depth++;
      if (tokens[index + 1] !== "}" && tokens[index + 1] !== "]") formatted += newline();
    } else if (token === "}" || token === "]") {
      depth--;
      if (tokens[index - 1] !== "{" && tokens[index - 1] !== "[") formatted += newline();
      formatted += token;
    } else if (token === ",") {
      formatted += token + newline();
    } else {
      formatted += token === ":" ? ": " : token;
    }
  });
  return formatted;
}

export function TraceEvidenceDetail({ range, pending = false, error, label = "Evidence content", onNext, onClear }: { range?: TraceRange; pending?: boolean; error?: string; label?: string; onNext: () => void; onClear: () => void }) {
  const detail = useRef<HTMLElement>(null);
  const visible = Boolean(range || pending || error);
  const content = useMemo(() => range ? formatContent(range) : undefined, [range]);

  useEffect(() => {
    if (visible) detail.current?.focus();
  }, [error, pending, range, visible]);

  if (!visible) return null;
  return <section ref={detail} className="trace-evidence-detail" aria-label={label} aria-busy={pending} tabIndex={-1}>
    {pending && !range && <p role="status">Reading content…</p>}
    {error && <p className="target-error" role="alert">{error}</p>}
    {range && <>
      <p className="trace-evidence-meta">{range.encoding === "BASE64" ? "Base64-encoded" : "Text"} bytes {range.actualStart}–{range.actualEnd} of {range.totalLength} ({range.contentType})</p>
      <pre>{content}</pre>
    </>}
    <div className="trace-detail-actions">
      {range?.hasMore && range.nextCursor && <button type="button" disabled={pending} onClick={onNext}>{pending ? "Reading…" : "Read next range"}</button>}
      <button type="button" onClick={onClear}>Clear content</button>
    </div>
  </section>;
}
