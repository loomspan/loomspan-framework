import { useEffect, useState } from "react";
import { getContentRange } from "../api/client";
import type { TraceFailure, TraceFrame, TraceRange, TraceRecord, TraceSource } from "../api/contracts";

const displayBytes = 1024 * 1024;
type Fields = Record<string, unknown>;
type Props = {
  traceId: string; source: TraceSource; record: TraceRecord; frame?: TraceFrame;
  failures: TraceFailure[]; verifyScope?: (response: TraceRange) => Promise<TraceRange>;
  onArtifactUnavailable?: (error: unknown) => void; onSelectFailure: (id: string) => void;
};

function object(value: unknown): Fields {
  if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error("Recorded data is not a JSON object.");
  return value as Fields;
}

function text(fields: Fields, key: string): string | undefined {
  if (!Object.hasOwn(fields, key)) return undefined;
  if (typeof fields[key] !== "string") throw new Error(`Recorded ${key} is not text.`);
  return fields[key];
}

function validate(fields: Fields, notice: boolean) {
  const keys = notice ? ["reason", "message", "originalMessageState"] : ["severity", "messageState", "dataState"];
  for (const key of keys) if (text(fields, key) === undefined) throw new Error(`Recorded ${key} is missing.`);
  for (const key of ["message", "reason", "originalMessage"]) text(fields, key);
  if (!notice) {
    if (!["INFO", "WARN", "ERROR"].includes(text(fields, "severity")!)) throw new Error("Recorded severity is invalid.");
    if (!["COMPLETE", "TRUNCATED", "UNAVAILABLE"].includes(text(fields, "messageState")!)) throw new Error("Recorded messageState is invalid.");
    if (!["ABSENT", "COMPLETE", "UNAVAILABLE", "CAPTURE_FAILED"].includes(text(fields, "dataState")!)) throw new Error("Recorded dataState is invalid.");
    if (fields.dataState === "COMPLETE" && !Object.hasOwn(fields, "data")) throw new Error("Recorded complete data is missing.");
  } else if (!["COMPLETE", "TRUNCATED", "UNAVAILABLE"].includes(text(fields, "originalMessageState")!)) throw new Error("Recorded originalMessageState is invalid.");
  return fields;
}

export function TraceExecutorDetail({ traceId, source, record, frame, failures, verifyScope, onArtifactUnavailable, onSelectFailure }: Props) {
  const [state, setState] = useState<{ fields?: Fields; raw?: string; error?: string }>({});
  const diagnostic = record.type === "EXECUTOR_DIAGNOSTIC_RECORDED";
  const notice = record.type === "EXECUTOR_DIAGNOSTIC_SYSTEM_NOTICE_RECORDED";
  const content = record.content;
  useEffect(() => {
    let active = true;
    setState({});
    const load = async () => {
      if (!content?.available) throw new Error("Recorded DATA content is unavailable.");
      if (!content.complete) throw new Error("Recorded DATA content is incomplete.");
      if (content.retainedBytes > displayBytes) throw new Error("Detail display is limited to 1 MiB. Use Read content for bounded ranges of the retained evidence.");
      let raw = content.inlineContent;
      if (raw === undefined) {
        if (!content.contentRef) throw new Error("Recorded DATA content has no content reference.");
        const parts: Uint8Array[] = [];
        const seen = new Set<string>();
        let bytes = 0;
        let cursor: string | undefined;
        do {
          if (!active) return;
          const response = await getContentRange(traceId, content.contentRef, cursor, source);
          if (!active) return;
          const range = verifyScope ? await verifyScope(response) : response;
          if (!active) return;
          const part = range.encoding === "BASE64"
            ? Uint8Array.from(atob(range.content), (character) => character.charCodeAt(0))
            : new TextEncoder().encode(range.content);
          bytes += part.length;
          if (bytes > displayBytes) throw new Error("Detail display is limited to 1 MiB. Use Read content for bounded ranges of the retained evidence.");
          parts.push(part);
          if (!range.hasMore) break;
          if (!range.nextCursor || seen.has(range.nextCursor)) throw new Error("Content continuation was invalid.");
          seen.add(range.nextCursor);
          cursor = range.nextCursor;
        } while (true);
        const joined = new Uint8Array(bytes);
        let offset = 0;
        for (const part of parts) { joined.set(part, offset); offset += part.length; }
        raw = new TextDecoder("utf-8", { fatal: true }).decode(joined);
      }
      if (new TextEncoder().encode(raw).length > displayBytes) throw new Error("Detail display is limited to 1 MiB. Use Read content for bounded ranges of the retained evidence.");
      const fields = object(JSON.parse(raw));
      if (diagnostic || notice) validate(fields, notice);
      if (active) setState({ fields, raw });
    };
    void load().catch((error: unknown) => {
      if (!active) return;
      if (error instanceof Error && "code" in error && (error.code === "ARTIFACT_EXPIRED" || error.code === "NOT_FOUND")) onArtifactUnavailable?.(error);
      setState({ error: error instanceof Error ? `Detail could not be displayed: ${error.message}` : "Detail could not be displayed." });
    });
    return () => { active = false; };
  }, [traceId, source, content, diagnostic, notice, verifyScope, onArtifactUnavailable]);
  const fields = state.fields;
  return <>
    <dl className="trace-step-facts">
      <div><dt>Recorded frame</dt><dd>{record.frameId}</dd></div>
      {record.parentFrameId && <div><dt>Parent frame</dt><dd>{record.parentFrameId}</dd></div>}
      <div><dt>Event timestamp</dt><dd>{record.timestampMillis}</dd></div>
      {frame && <><div><dt>Frame opened</dt><dd>{frame.openedTimestampMillis}</dd></div>
        {frame.closedTimestampMillis != null && <div><dt>Frame closed</dt><dd>{frame.closedTimestampMillis}</dd></div>}
        {frame.inclusiveDurationMillis != null && <div><dt>Recorded duration (ms)</dt><dd>{frame.inclusiveDurationMillis}</dd></div>}
        {frame.outcome != null && <div><dt>Framework-recorded outcome</dt><dd>{frame.outcome}</dd></div>}</>}
      {failures.filter((failure) => failure.frameId === record.frameId).map((failure) => <div key={failure.failureId}><dt>Framework-recorded failure</dt><dd><button type="button" onClick={() => onSelectFailure(failure.failureId)}>{failure.classification}: {failure.failureId}</button></dd></div>)}
    </dl>
    {state.error ? <p role="alert">{state.error}</p> : !fields ? <p role="status">Loading recorded details…</p> : diagnostic || notice ? <>
      <h5>{notice ? "Framework capture notice" : "Executor-authored diagnostic"}</h5>
      <dl className="trace-step-facts">
        {diagnostic && <><div><dt>Authored severity</dt><dd>{String(fields.severity)}</dd></div><div><dt>Message state</dt><dd>{String(fields.messageState)}</dd></div><div><dt>Data state</dt><dd>{String(fields.dataState)}</dd></div></>}
        {typeof fields.reason === "string" && <div><dt>Recorded reason</dt><dd>{fields.reason}</dd></div>}
        {notice && <div><dt>Original message state</dt><dd>{String(fields.originalMessageState)}</dd></div>}
      </dl>
      {typeof fields.message === "string" && <><h5>Recorded message</h5><pre>{fields.message}</pre></>}
      {typeof fields.originalMessage === "string" && <><h5>Recorded original message</h5><pre>{fields.originalMessage}</pre></>}
      {diagnostic && fields.dataState === "COMPLETE" && <details><summary>Supplied JSON</summary><p>The retained DATA envelope contains the supplied JSON without numeric or whitespace reformatting.</p>{fields.data === null && <p>Supplied JSON value: <code>null</code></p>}<pre>{state.raw}</pre></details>}
      {(!diagnostic || fields.dataState !== "COMPLETE") && <details><summary>Retained DATA JSON</summary><pre>{state.raw}</pre></details>}
    </> : <>
      {typeof fields.executorName === "string" && <p>Executor: {fields.executorName}</p>}
      {typeof fields.executorConfigGenerationId === "string" && <p>Executor configuration generation: {fields.executorConfigGenerationId}</p>}
      {Object.hasOwn(fields, "missionInput") && <details><summary>Recorded input</summary><p>Input is shown in its retained DATA envelope.</p><pre>{state.raw}</pre></details>}
      {Object.hasOwn(fields, "executorOutput") && <><h5>Recorded executor output</h5><pre>{typeof fields.executorOutput === "string" ? fields.executorOutput : JSON.stringify(fields.executorOutput, null, 2)}</pre></>}
      <details><summary>Retained DATA JSON</summary><pre>{state.raw}</pre></details>
    </>}
  </>;
}
