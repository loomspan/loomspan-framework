import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import {
  BrowserAPIError,
  acquireArtifact,
  getActiveExecutionDetail,
} from "../api/client";
import type { ActiveExecution, AcquiredArtifact } from "../api/contracts";
import { useTarget } from "../target/TargetProvider";
import {
  recoverObservabilityError,
  requireCurrentTargetScope,
} from "./scope";
import { useScopeBoundRoute } from "./useScopeBoundRoute";
import { useOptionalActivity } from "../activity/ActivityProvider";
import { ActivityNarrative } from "../activity/ActivityNarrative";
import { ActiveBranches } from "../activity/ActiveBranches";
import { CurrentExecutionSummary } from "../activity/CurrentExecutionSummary";
import { ActiveExecutionUsage } from "./ActiveExecutionUsage";
import { scopeBoundPath } from "./scope";
import { useOptionalObservability } from "./ObservabilityProvider";
import { useBrowserSession } from "../security/BrowserSessionProvider";

export function ActiveExecutionDetailView() {
  const { sessionId } = useParams();
  const { target, scopeGeneration, refresh } = useTarget();
  const observability = useOptionalObservability();
  const baselineExecution = observability?.activeExecutions.items.find(
    (item) => (item as ActiveExecution).sessionId === sessionId,
  ) as ActiveExecution | undefined;
  const [execution, setExecution] = useState<ActiveExecution | null>(
    () => baselineExecution ?? null,
  );
  const selectedExecution =
    execution?.sessionId === sessionId ? execution : (baselineExecution ?? null);
  const [error, setError] = useState<BrowserAPIError | null>(null);
  const [loading, setLoading] = useState(true);
  const [observationEnded, setObservationEnded] = useState(false);
  const [acquiring, setAcquiring] = useState(false);
  const [acquireError, setAcquireError] = useState<BrowserAPIError | null>(null);
  const [acquired, setAcquired] = useState<AcquiredArtifact | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const refreshTarget = useRef(refresh);
  refreshTarget.current = refresh;
  const session = useBrowserSession();
  const routeIsCurrent = useScopeBoundRoute();
  const activity = useOptionalActivity();
  const selectedActivity = (activity?.activities ?? []).filter((item) => item.sessionId === sessionId);
  const terminalActivity = selectedActivity.findLast((item) =>
    item.kind === "TRACE_COMPLETED" || item.kind === "EXECUTION_OBSERVATION_ENDED"
  );
  const terminalActivityRef = useRef(terminalActivity);
  terminalActivityRef.current = terminalActivity;
  const artifactAvailable =
    terminalActivity?.kind === "TRACE_COMPLETED" &&
    terminalActivity.details?.applicationTraceAvailability === "AVAILABLE";
  const finalizationFailed =
    terminalActivity?.kind === "EXECUTION_OBSERVATION_ENDED" &&
    terminalActivity.details?.applicationTraceUnavailableReason === "CORE_FINALIZATION_FAILED";
  const traceID = selectedExecution?.traceId ?? terminalActivity?.traceId;
  const traceScopeID =
    selectedExecution?.targetScopeId ?? target.status.targetScopeId;
  const terminalFailureID = typeof terminalActivity?.details?.terminalFailureId === "string"
    ? terminalActivity.details.terminalFailureId
    : null;
  const traceInspectionPath = traceID
    ? `${scopeBoundPath(`/traces/${encodeURIComponent(traceID)}`, traceScopeID)}${terminalFailureID ? `&failureId=${encodeURIComponent(terminalFailureID)}` : ""}`
    : null;
  useEffect(() => {
    heading.current?.focus();
  }, []);

  useEffect(() => {
    if (terminalActivity) setObservationEnded(true);
  }, [terminalActivity?.cursor]);

  useEffect(() => {
    if (!selectedExecution && baselineExecution) setExecution(baselineExecution);
  }, [baselineExecution, selectedExecution]);

  useEffect(() => {
    if (!sessionId || !routeIsCurrent) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    if (execution?.sessionId !== sessionId) {
      setExecution(baselineExecution ?? null);
      setObservationEnded(false);
    }
    getActiveExecutionDetail(sessionId)
      .then(async (e) => {
        await requireCurrentTargetScope(e.targetScopeId, target.status.targetScopeId, refreshTarget.current);
        if (!cancelled) {
          setExecution(e);
          if (!terminalActivityRef.current) setObservationEnded(false);
        }
      })
      .catch(async (err) => {
        const recovered = await recoverObservabilityError(err, refreshTarget.current);
        if (cancelled) return;
        if (
          recovered.code === "NOT_FOUND" &&
          (selectedExecution || terminalActivityRef.current)
        ) {
          setObservationEnded(true);
        } else {
          setError(recovered);
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [routeIsCurrent, sessionId, scopeGeneration, target.status.targetScopeId, activity?.baselineObservedAt]);

  const handleAcquire = async () => {
    if (!traceID) return;
    const security = session.getSecurity();
    if (!security) {
      setAcquireError(new BrowserAPIError("SESSION_REQUIRED", "Pairing is required.", 401));
      return;
    }
    setAcquiring(true);
    setAcquireError(null);
    setAcquired(null);
    try {
      const result = await acquireArtifact(traceID, security);
      setAcquired(result);
    } catch (err) {
      const recovered = await recoverObservabilityError(err, refreshTarget.current);
      setAcquireError(recovered);
    } finally {
      setAcquiring(false);
    }
  };

  return (
    <section aria-labelledby="active-execution-detail-title" className="overview-card">
      <p className="eyebrow">Operational views</p>
      <div className="view-header">
        <h2 id="active-execution-detail-title" ref={heading} tabIndex={-1}>Active Execution Detail</h2>
        <Link className="view-back-link" to="/active-executions">Back to Active Executions</Link>
      </div>

      {error && (
        <div className="target-error" role="alert">
          <strong>{error.message}</strong>
        </div>
      )}

      {loading && <p role="status" className="observability-note">Loading execution detail…</p>}

      {(selectedExecution || selectedActivity.length > 0) && (
        <>
          <div className="selected-live-execution view-subsection" aria-label="Selected execution live activity">
            <h3>Live activity</h3>
            {activity?.continuity?.reset && (
              <p className="activity-notice" role="status">Activity continuity reset: {activity.continuity.reset.cause}</p>
            )}
            {terminalActivity && (
              <p className="activity-notice" role="status">
                {terminalActivity.kind === "TRACE_COMPLETED"
                  ? "Execution completed. Context is preserved."
                  : finalizationFailed
                    ? "Execution observation ended without an outcome because trace finalization failed."
                    : "Execution observation ended before a trustworthy outcome was available."}
              </p>
            )}
            {observationEnded && !terminalActivity && (
              <p className="activity-notice" role="status">
                This execution is no longer present in the active baseline. No
                terminal activity was observed, so outcome and trace
                availability are unknown.
              </p>
            )}
            {artifactAvailable && traceID && (
              <div className="trace-acquire-section">
                <div className="view-actions">
                  {traceInspectionPath && (
                    <Link className="trace-download-link" to={traceInspectionPath}>
                      Inspect trace
                    </Link>
                  )}
                  <button
                    type="button"
                    onClick={() => void handleAcquire()}
                    disabled={acquiring}
                  >
                    {acquiring ? "Acquiring…" : "Acquire for analysis"}
                  </button>
                </div>
                {acquireError && (
                  <div className="target-error" role="alert">
                    <strong>{acquireError.message}</strong>
                  </div>
                )}
                {acquired && (
                  <div className="activity-notice" role="status">
                    <p>Artifact acquired successfully.</p>
                    <dl className="status-grid">
                      <div><dt>Handle</dt><dd>{acquired.artifactHandle}</dd></div>
                      <div><dt>Local bytes</dt><dd>{String(acquired.localBytes)}</dd></div>
                    </dl>
                    {traceInspectionPath && <p><Link className="trace-download-link" to={traceInspectionPath}>Open focused explorer</Link></p>}
                  </div>
                )}
              </div>
            )}
            <CurrentExecutionSummary
              execution={selectedExecution}
              activities={selectedActivity}
              observedAt={
                activity?.baselineObservedAt ?? activity?.continuity?.observedAt
              }
              connected={activity?.connected ?? false}
              observationEnded={observationEnded}
            />
            <ActiveBranches branches={selectedExecution?.activeBranches} />
            <ActivityNarrative activities={selectedActivity} isLive={activity?.connected ?? false} />
          </div>

          {selectedExecution && (
            <>
              <ActiveExecutionUsage
                usage={selectedExecution.usage}
                limits={selectedExecution.configuredLimits}
              />

              <details className="fact-disclosure">
                <summary>Snapshot diagnostics</summary>
                <dl className="status-grid" aria-label="Snapshot diagnostics">
                  <div><dt>Last canonical sequence</dt><dd>{String(selectedExecution.lastCanonicalSequence)}</dd></div>
                  <div><dt>Active branches</dt><dd>{String(selectedExecution.activeBranches.length)}</dd></div>
                </dl>
              </details>
            </>
          )}
        </>
      )}
    </section>
  );
}
