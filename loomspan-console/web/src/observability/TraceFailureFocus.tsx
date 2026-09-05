import type { TraceAnalysisSummary, TraceFailure, TraceFrame } from "../api/contracts";
import { formatDuration } from "../duration";

export function TraceFailureFocus({ summary, failure, frame, onView }: {
  summary: TraceAnalysisSummary;
  failure?: TraceFailure;
  frame?: TraceFrame;
  onView: (view: "timeline" | "usage" | "records") => void;
}) {
  if ((summary.outcome !== "FAILED" && summary.outcome !== "ABORTED") || !summary.terminalFailureId) return null;
  return <section className="failure-focus" aria-labelledby="failure-focus-title">
    <h4 id="failure-focus-title">Terminal failure evidence</h4>
    <dl className="status-grid">
      <div><dt>Outcome</dt><dd>{summary.outcome}</dd></div>
      <div><dt>Terminal failure ID</dt><dd><code>{summary.terminalFailureId ?? "unavailable"}</code></dd></div>
      <div><dt>Record</dt><dd>{failure ? `${failure.recordType} sequence ${failure.sequence}` : "Loading or unavailable"}</dd></div>
      <div><dt>Frame</dt><dd><code>{failure?.frameId || "unattributed"}</code></dd></div>
      <div><dt>Route</dt><dd>{failure?.route || frame?.route || "unavailable"}</dd></div>
      <div><dt>Recorded skill names</dt><dd>{frame?.skillNames?.join(", ") || "unavailable"}</dd></div>
      <div><dt>Attempt</dt><dd><code>{failure?.attemptId || frame?.attemptIds?.join(", ") || "unavailable"}</code></dd></div>
      <div><dt>Retry sequence</dt><dd><code>{failure?.retrySequenceId || frame?.retrySequenceIds?.join(", ") || "unavailable"}</code></dd></div>
      <div><dt>Validation</dt><dd>{failure?.validationStatus || frame?.validationStatuses?.join(", ") || "unavailable"}</dd></div>
      <div><dt>Frame duration</dt><dd>{frame?.inclusiveDurationMillis == null ? "unavailable or incomplete" : formatDuration(frame.inclusiveDurationMillis)}</dd></div>
      <div><dt>Frame usage</dt><dd>{frame?.inclusiveUsage ? `${frame.inclusiveUsage.totalUnits} total units${frame.inclusiveUsageComplete ? "" : " (incomplete)"}` : "unavailable"}</dd></div>
      <div><dt>Evidence gaps</dt><dd>{summary.gapCount} gaps; {summary.uncertaintyCount} uncertainties</dd></div>
    </dl>
    <p>This view relates directly recorded evidence. It does not identify root cause.</p>
    <div className="trace-actions" aria-label="Related failure evidence">
      <button type="button" onClick={() => onView("timeline")}>Show in timeline</button>
      <button type="button" onClick={() => onView("usage")}>Show usage</button>
      <button type="button" onClick={() => onView("records")}>Show records</button>
    </div>
  </section>;
}
