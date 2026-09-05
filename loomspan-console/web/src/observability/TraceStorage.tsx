import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  BrowserAPIError,
  clearAllUnusedArtifacts,
  clearExpiredArtifacts,
  getStorageSnapshot,
	importTraceFile,
  removeArtifact,
} from "../api/client";
import type { StorageSnapshot } from "../api/contracts";
import { useBrowserSession } from "../security/BrowserSessionProvider";

type ImportResult = {
	name: string;
	status: "waiting" | "importing" | "imported" | "failed";
	traceId?: string;
	error?: BrowserAPIError;
};

export function TraceStorage() {
  const session = useBrowserSession();
	const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<StorageSnapshot | null>(null);
  const [error, setError] = useState<BrowserAPIError | null>(null);
  const [loading, setLoading] = useState(true);
	const [actionError, setActionError] = useState<BrowserAPIError | null>(null);
	const [confirmRemove, setConfirmRemove] = useState<string | null>(null);
	const [importing, setImporting] = useState(false);
	const [importProgress, setImportProgress] = useState<{ current: number; total: number } | null>(null);
	const [importResults, setImportResults] = useState<ImportResult[]>([]);
	const [dragActive, setDragActive] = useState(false);
  const [confirmClear, setConfirmClear] = useState<"expired" | "all-unused" | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
	const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  const loadSnapshot = useCallback(async () => {
    const security = session.getSecurity();
    if (!security) {
      setError(new BrowserAPIError("SESSION_REQUIRED", "Pairing is required.", 401));
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await getStorageSnapshot(security);
      setSnapshot(result);
    } catch (err) {
		setError(err instanceof BrowserAPIError ? err : new BrowserAPIError("CONSOLE_ERROR", "Storage could not be loaded.", 500));
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void loadSnapshot();
  }, [loadSnapshot]);

  const handleRemove = useCallback(async (traceId: string, source: "TARGET" | "IMPORTED") => {
    const security = session.getSecurity();
    if (!security) return;
    setActionError(null);
    try {
		await removeArtifact(traceId, source, security);
      setConfirmRemove(null);
      await loadSnapshot();
    } catch (err) {
		setActionError(err instanceof BrowserAPIError ? err : new BrowserAPIError("CONSOLE_ERROR", "The artifact could not be removed.", 500));
    }
  }, [session, loadSnapshot]);

  const handleClearExpired = useCallback(async () => {
    const security = session.getSecurity();
    if (!security) return;
    setActionError(null);
    try {
      await clearExpiredArtifacts(security);
      setConfirmClear(null);
      await loadSnapshot();
    } catch (err) {
		setActionError(err instanceof BrowserAPIError ? err : new BrowserAPIError("CONSOLE_ERROR", "Artifacts could not be cleared.", 500));
    }
  }, [session, loadSnapshot]);

  const handleClearAllUnused = useCallback(async () => {
    const security = session.getSecurity();
    if (!security) return;
    setActionError(null);
    try {
      await clearAllUnusedArtifacts(security);
      setConfirmClear(null);
      await loadSnapshot();
    } catch (err) {
		setActionError(err instanceof BrowserAPIError ? err : new BrowserAPIError("CONSOLE_ERROR", "Artifacts could not be cleared.", 500));
    }
  }, [session, loadSnapshot]);

	const handleImport = useCallback(async (files: File[]) => {
		const security = session.getSecurity();
		if (!security || files.length === 0 || importing) return;
		setImporting(true);
		setActionError(null);
		setImportResults(files.map((file) => ({ name: file.name, status: "waiting" })));
		const completed: ImportResult[] = [];
		try {
			for (let index = 0; index < files.length; index += 1) {
				const file = files[index];
				setImportProgress({ current: index + 1, total: files.length });
				setImportResults((current) => current.map((result, resultIndex) => resultIndex === index ? { ...result, status: "importing" } : result));
				try {
					const imported = await importTraceFile(file, security);
					const result: ImportResult = { name: file.name, status: "imported", traceId: imported.traceId };
					completed.push(result);
					setImportResults((current) => current.map((value, resultIndex) => resultIndex === index ? result : value));
				} catch (err) {
					const error = err instanceof BrowserAPIError ? err : new BrowserAPIError("CONSOLE_ERROR", "The trace file could not be imported.", 500);
					const result: ImportResult = { name: file.name, status: "failed", error };
					completed.push(result);
					setImportResults((current) => current.map((value, resultIndex) => resultIndex === index ? result : value));
				}
			}
			if (files.length === 1 && completed[0]?.status === "imported" && completed[0].traceId) {
				navigate(`/traces/imported/${encodeURIComponent(completed[0].traceId)}`);
			} else {
				await loadSnapshot();
			}
		} finally {
			setImporting(false);
			setImportProgress(null);
		}
	}, [importing, loadSnapshot, navigate, session]);

  return (
    <section aria-labelledby="trace-storage-title" className="overview-card">
      <p className="eyebrow">Artifact cache</p>
      <div className="view-header">
        <h2 id="trace-storage-title" ref={heading} tabIndex={-1}>Trace Storage</h2>
        <Link className="view-back-link" to="/traces">Back to Trace Catalog</Link>
      </div>

	  <div className="trace-import">
		<p className="observability-note">Trace files may contain sensitive diagnostics and application paths. Only complete files from this exact Loomspan version can be opened.</p>
		<div
			className={`trace-import-drop-zone${dragActive ? " trace-import-drop-zone-active" : ""}`}
			role="group"
			aria-label="Trace file import"
			onDragEnter={(event) => { event.preventDefault(); if (!importing) setDragActive(true); }}
			onDragOver={(event) => { event.preventDefault(); event.dataTransfer.dropEffect = "copy"; }}
			onDragLeave={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragActive(false); }}
			onDrop={(event) => {
				event.preventDefault();
				setDragActive(false);
				if (!importing) void handleImport(Array.from(event.dataTransfer.files));
			}}
		>
			<p>Drag and drop one or more <code>.ndjson</code> trace files here, or</p>
			<button type="button" disabled={importing} onClick={() => fileInput.current?.click()}>{importing ? "Importing Trace File…" : "Import Trace File"}</button>
			<input
				ref={fileInput}
				id="trace-file"
				type="file"
				accept="application/x-ndjson,.ndjson"
				multiple
				hidden
				aria-label="Trace files"
				onChange={(event) => {
					const input = event.currentTarget;
					const files = Array.from(input.files ?? []);
					if (files.length > 0) void handleImport(files).finally(() => { input.value = ""; });
				}}
			/>
		</div>
		{importProgress && <p className="observability-note" role="status">Importing {importProgress.current} of {importProgress.total}…</p>}
		{importResults.length > 0 && <section className="import-results" aria-label="Trace import results" aria-live="polite">
			<h3>Import results</h3>
			<ul className="import-result-list">{importResults.map((result, index) => <li key={`${result.name}-${index}`} className={`import-result import-result-${result.status}`} role={result.status === "failed" ? "alert" : undefined}>
				<strong className="import-result-name">{result.name}</strong>: {result.status === "waiting" ? "Waiting" : result.status === "importing" ? "Importing…" : result.status === "imported" ? `Imported as ${result.traceId}` : result.error?.message}
				{result.error?.details?.expectedCompatibilityVersion && result.error.details?.observedCompatibilityVersion && <span> Expected {result.error.details.expectedCompatibilityVersion}; observed {result.error.details.observedCompatibilityVersion}.</span>}
			</li>)}</ul>
		</section>}
	  </div>

      {error && (
        <div className="target-error" role="alert">
          <strong>{error.message}</strong>
        </div>
      )}

      {actionError && (
        <div className="target-error" role="alert">
          <strong>{actionError.message}</strong>
		  {actionError.details?.expectedCompatibilityVersion && actionError.details?.observedCompatibilityVersion && (
			<p>Expected {actionError.details.expectedCompatibilityVersion}; observed {actionError.details.observedCompatibilityVersion}.</p>
		  )}
        </div>
      )}

      {loading && <p className="observability-note">Loading storage snapshot…</p>}

      {snapshot && (
        <section className="view-subsection" aria-labelledby="storage-snapshot-title">
          <h3 id="storage-snapshot-title">Stored artifacts</h3>
          <dl className="status-grid">
            <div><dt>Workspace</dt><dd>{snapshot.workspaceLabel}</dd></div>
            <div><dt>Maximum bytes</dt><dd>{snapshot.unlimited ? "Unlimited" : String(snapshot.maxBytes)}</dd></div>
            <div><dt>Idle TTL</dt><dd>{snapshot.neverExpire ? "Never" : snapshot.idleTtl}</dd></div>
            <div><dt>Charged bytes</dt><dd>{String(snapshot.chargedBytes)}</dd></div>
            <div><dt>Acquired count</dt><dd>{String(snapshot.acquiredCount)}</dd></div>
          </dl>

          <div className="storage-actions">
            {confirmClear === "expired" ? (
              <>
                <button
                  type="button"
                  onClick={() => void handleClearExpired()}
                  aria-label="Confirm clearing expired artifacts"
                >
                  Confirm clear expired
                </button>
                <button type="button" onClick={() => setConfirmClear(null)}>
                  Cancel
                </button>
              </>
            ) : confirmClear === "all-unused" ? (
              <>
                <button
                  type="button"
                  onClick={() => void handleClearAllUnused()}
                  aria-label="Confirm clearing all unused artifacts"
                >
                  Confirm clear all unused
                </button>
                <button type="button" onClick={() => setConfirmClear(null)}>
                  Cancel
                </button>
              </>
            ) : (
              <>
                <button type="button" onClick={() => setConfirmClear("expired")}>
                  Clear expired
                </button>
                <button type="button" onClick={() => setConfirmClear("all-unused")}>
                  Clear all unused
                </button>
              </>
            )}
          </div>

          {snapshot.entries.length === 0 ? (
            <p className="empty-state">No artifacts are currently stored.</p>
          ) : (
            <div className="observability-table-region" role="region" aria-label="Stored artifacts table" tabIndex={0}>
            <table className="storage-table">
              <thead>
                <tr>
				  <th scope="col">Trace ID</th>
				  <th scope="col">Source</th>
                  <th scope="col">Session ID</th>
                  <th scope="col">Outcome</th>
                  <th scope="col" className="numeric-cell">Local bytes</th>
                  <th scope="col">App availability at acquisition</th>
                  <th scope="col">Local</th>
                  <th scope="col">Active pin</th>
                  <th scope="col">Acquired at</th>
                  <th scope="col">Last used</th>
                  <th scope="col">Expires at</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {snapshot.entries.map((entry) => (
				  <tr key={`${entry.source}:${entry.traceId}`}>
                    <td className="identifier-cell">
					  <Link to={entry.source === "IMPORTED" ? `/traces/imported/${encodeURIComponent(entry.traceId)}` : `/traces/${encodeURIComponent(entry.traceId)}`}>
                        {entry.traceId}
                      </Link>
					</td>
					<td><span className="status-chip">{entry.source === "IMPORTED" ? "Imported" : "Target"}</span></td>
                    <td className="identifier-cell">{entry.sessionId}</td>
                    <td><span className="status-chip">{entry.outcome}</span></td>
                    <td className="numeric-cell">{String(entry.localBytes)}</td>
					<td>{entry.applicationAvailability ?? "Not applicable"}</td>
                    <td>{entry.localAvailable ? "Yes" : "No"}</td>
                    <td>{entry.activePin ? "Yes" : "No"}</td>
                    <td className="identifier-cell">{entry.acquiredAt}</td>
                    <td className="identifier-cell">{entry.lastUsedAt}</td>
                    <td className="identifier-cell">{entry.hasIdleExpiry ? entry.expiresAt : "Never"}</td>
                    <td className="storage-row-actions">
                      {entry.activePin ? (
                        <span className="storage-in-use" aria-label="Cannot remove: artifact is in use">In use</span>
					  ) : confirmRemove === `${entry.source}:${entry.traceId}` ? (
                        <>
                          <button
                            type="button"
							onClick={() => void handleRemove(entry.traceId, entry.source)}
                            aria-label={`Confirm removal of ${entry.traceId}`}
                          >
                            Confirm
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmRemove(null)}
                          >
                            Cancel
                          </button>
                        </>
                      ) : (
                        <button
                          type="button"
						  onClick={() => setConfirmRemove(`${entry.source}:${entry.traceId}`)}
                        >
                          Remove
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </section>
      )}
    </section>
  );
}
