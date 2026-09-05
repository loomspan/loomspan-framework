import { useEffect, useRef } from "react";
import { Link } from "react-router";
import { useObservability } from "./ObservabilityProvider";
import type { ActiveExecution } from "../api/contracts";
import { scopeBoundPath } from "./scope";
import { useOptionalActivity } from "../activity/ActivityProvider";

export function ActiveExecutions() {
  const { activeExecutions, loadActiveExecutions } = useObservability();
  const heading = useRef<HTMLHeadingElement>(null);
  const recentCompletions = useOptionalActivity()?.recentCompletions ?? [];

  useEffect(() => {
    heading.current?.focus();
  }, []);

  useEffect(() => {
    if (!activeExecutions.loaded && !activeExecutions.loading && !activeExecutions.error) {
      void loadActiveExecutions();
    }
  }, [activeExecutions, loadActiveExecutions]);

  return (
    <section aria-labelledby="active-executions-title" className="overview-card">
      <p className="eyebrow">Operational views</p>
      <div className="view-header">
        <h2 id="active-executions-title" ref={heading} tabIndex={-1}>Active Executions</h2>
        <button type="button" disabled={activeExecutions.loading} onClick={() => void loadActiveExecutions()}>
          Refresh
        </button>
      </div>

      {activeExecutions.error && (
        <div className="target-error" role="alert">
          <strong>{activeExecutions.error.message}</strong>
          <div className="view-actions">
            <button type="button" disabled={activeExecutions.loading} onClick={() => void loadActiveExecutions()}>
              Retry
            </button>
          </div>
        </div>
      )}

      {activeExecutions.loading && <p role="status" className="observability-note">Loading active executions…</p>}

      {activeExecutions.loaded && !activeExecutions.loading && activeExecutions.items.length === 0 && !activeExecutions.error && (
        <p className="empty-state">No active executions.</p>
      )}

      {activeExecutions.items.length > 0 && (
        <div className="observability-table-region" role="region" aria-label="Active executions table" tabIndex={0}>
          <table className="observability-table active-executions-table">
          <thead>
            <tr>
              <th scope="col">Session</th>
              <th scope="col">Entry skill</th>
              <th scope="col">Status</th>
              <th scope="col">Phase</th>
              <th scope="col">Summary</th>
              <th scope="col" className="numeric-cell">Branches</th>
              <th scope="col">Updated</th>
            </tr>
          </thead>
          <tbody>
            {activeExecutions.items.map((exec) => {
              const e = exec as ActiveExecution;
              return (
                <tr key={e.sessionId}>
                  <td className="identifier-cell">
                    <Link to={scopeBoundPath(`/active-executions/${encodeURIComponent(e.sessionId)}`, activeExecutions.targetScopeId)}>{e.sessionId}</Link>
                  </td>
                  <td className="identifier-cell">{e.entrySkill}</td>
                  <td><span className="status-chip">{e.status}</span></td>
                  <td className="identifier-cell">{e.phase}</td>
                  <td className="summary-cell">{e.summary}</td>
                  <td className="numeric-cell">{e.activeBranches.length}</td>
                  <td className="identifier-cell">{e.updatedAt}</td>
                </tr>
              );
            })}
          </tbody>
          </table>
        </div>
      )}

      {activeExecutions.hasMore && activeExecutions.nextCursor && (
        <div className="view-actions">
          <button type="button" disabled={activeExecutions.loading} onClick={() => void loadActiveExecutions(activeExecutions.nextCursor ?? undefined)}>
            Load more
          </button>
        </div>
      )}

      <section className="view-subsection" aria-labelledby="recent-completions-title">
        <h3 id="recent-completions-title">Recent completions</h3>
        <p className="observability-note">Temporary current-run activity; finalized trace availability is authoritative.</p>
        {recentCompletions.length === 0 ? (
          <p className="empty-state">No recent completions.</p>
        ) : (
          <ul className="recent-completion-list" aria-label="Temporary recent completions">
            {recentCompletions.map((completion) => (
              <li key={`${completion.instanceId}:${completion.cursor}`}>
                <span className="recent-completion-session">{completion.sessionId}</span>
                {" — "}
                <span className="recent-completion-summary">{completion.summary}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

    </section>
  );
}
