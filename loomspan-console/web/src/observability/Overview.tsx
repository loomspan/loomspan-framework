import { useEffect, useRef } from "react";
import { Link, useLocation } from "react-router";
import { useObservability } from "./ObservabilityProvider";
import type { InstanceStatus } from "../api/contracts";
import { useTarget } from "../target/TargetProvider";
import { Overview as TargetOverview } from "../target/Overview";
import { LiveActivity } from "../activity/LiveActivity";

export function ObservabilityOverview() {
  const { instance, loadInstance } = useObservability();
  const { target } = useTarget();
  const location = useLocation();
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  const status = instance?.status as InstanceStatus | undefined;
  const error = instance?.error;
  const loading = instance?.loading ?? false;
  const established =
    target.status.targetAuthentication === "ESTABLISHED" &&
    target.status.javaGoCompatibility === "COMPATIBLE" &&
    target.status.runtimeIdentity === "ESTABLISHED";

  return (
    <section aria-labelledby="observability-overview-title" className="overview-card">
      <p className="eyebrow">Operational views</p>
      <div className="view-header">
        <h2 id="observability-overview-title" ref={heading} tabIndex={-1}>Instance Overview</h2>
        {established && (
          <div className="overview-refresh">
            {status && <span className="overview-observed">Observed {status.observedAt}</span>}
            <button type="button" disabled={loading} onClick={() => void loadInstance()}>
              Refresh
            </button>
          </div>
        )}
      </div>
      {Boolean((location.state as { staleTargetScope?: boolean } | null)?.staleTargetScope) && (
        <div className="target-error" role="alert">
          <strong>The selected target changed. The previous view was discarded.</strong>
        </div>
      )}
      {!established && <TargetOverview />}

      {error && (
        <div className="target-error" role="alert">
          <strong>{error.message}</strong>
        </div>
      )}

      {loading && <p role="status" className="observability-note">Loading instance overview…</p>}

      {status && (
        <>
          <nav className="observability-nav" aria-label="Operational views">
            <CatalogLink to="/skills" label="Skill Catalog" count={status.registeredSkillCount} noun="registered skill" />
            <CatalogLink to="/active-executions" label="Active Executions" count={status.activeExecutionCount} noun="active execution" />
            <CatalogLink to="/traces" label="Trace Catalog" count={status.catalogedTraceCount} noun="cataloged trace" />
          </nav>

          {!status.liveMonitoringAvailable && (
            <p className="observability-note">Live monitoring is unavailable for this instance.</p>
          )}

          <details className="fact-disclosure">
            <summary>Instance configuration</summary>
            <dl className="status-grid" aria-label="Instance configuration">
              <Fact name="Compatibility" value={status.consoleCompatibilityVersion} />
              <Fact name="Trace persistence" value={status.tracePersistencePolicy} />
              <Fact name="Completion grace TTL" value={status.completionGraceTtl} />
              <Fact name="Trace catalog metadata TTL" value={status.traceCatalogMetadataTtl} />
            </dl>
            <p className="observability-note">
              Catalog metadata TTL and core file retention are independent. Neither provides cross-restart history.
            </p>
          </details>

          <LiveActivity />
        </>
      )}
    </section>
  );
}

function CatalogLink({ to, label, count, noun }: { to: string; label: string; count: number; noun: string }) {
  return (
    <Link to={to} aria-label={`${label}, ${count} ${noun}${count === 1 ? "" : "s"}`}>
      <span>{label}</span>
      <span className="observability-nav-count">{count}</span>
    </Link>
  );
}

function Fact({ name, value }: { name: string; value: string }) {
  return (
    <div>
      <dt>{name}</dt>
      <dd>{value}</dd>
    </div>
  );
}
