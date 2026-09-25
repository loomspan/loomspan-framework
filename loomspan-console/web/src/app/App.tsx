import { type ReactNode, useEffect } from "react";
import { NavLink, Outlet } from "react-router";
import { PairingPage } from "../security/PairingPage";
import { useBrowserSession } from "../security/BrowserSessionProvider";
import { TargetProvider, useTarget } from "../target/TargetProvider";
import { ObservabilityProvider, useObservability } from "../observability/ObservabilityProvider";
import { ActivityProvider } from "../activity/ActivityProvider";
import type { InstanceStatus } from "../api/contracts";
import logo from "../assets/loomspan-logo.png";

export function App() {
  const session = useBrowserSession();
  const brand = (
    <h1 className="brand">
      <img src={logo} alt="loomspan Console" width={520} height={134} />
    </h1>
  );
  if (session.status !== "paired") {
    return (
      <main className="auth-shell" id="main-content">
        <div className="auth-card">
          {brand}
          <p className="product">Developer console</p>
          <PairingPage />
        </div>
      </main>
    );
  }
  return (
    <div className="app-frame">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <header className="sidebar">
        {brand}
        <p className="product">Developer console</p>
        <nav className="global-nav" aria-label="Console">
          <NavItem to="/" end icon={icons.overview}>Overview</NavItem>
          <NavItem to="/target" icon={icons.target}>Target</NavItem>
          <p className="nav-label">Observability</p>
          <NavItem to="/skills" icon={icons.skills}>Skills</NavItem>
          <NavItem to="/active-executions" icon={icons.activity}>Active Executions</NavItem>
          <NavItem to="/traces" icon={icons.traces}>Traces</NavItem>
          <NavItem to="/trace-storage" icon={icons.storage}>Trace Storage</NavItem>
          <p className="nav-label">Configuration</p>
          <NavItem to="/settings/mcp" icon={icons.settings}>Settings</NavItem>
        </nav>
        <footer className="sidebar-footer">
          <span className="workspace-path">
            <span className="sidebar-footer-label">Verified workspace</span>
            <code title={session.bootstrap.workspacePath}>{session.bootstrap.workspacePath}</code>
          </span>
          <span className="build-meta">
            <span className="sidebar-footer-label">Console</span>
            <code data-testid="console-version">{session.bootstrap.consoleVersion}</code>
          </span>
        </footer>
      </header>
      <main className="shell-main" id="main-content">
        <TargetProvider initial={session.bootstrap.target} defaults={session.bootstrap.targetFormDefaults}>
          <ObservabilityProvider>
            <ActivityProvider>
              <ConsoleWorkspace />
            </ActivityProvider>
          </ObservabilityProvider>
        </TargetProvider>
      </main>
    </div>
  );
}

function NavItem({ to, end, icon, children }: { to: string; end?: boolean; icon: ReactNode; children: string }) {
  return (
    <NavLink to={to} end={end}>
      <svg className="icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">{icon}</svg>
      <span>{children}</span>
    </NavLink>
  );
}

const icons = {
  overview: <path d="M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z" />,
  target: (
    <>
      <circle cx="12" cy="12" r="8" />
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
    </>
  ),
  skills: (
    <>
      <path d="M12 3 2 8l10 5 10-5z" />
      <path d="m2 13 10 5 10-5" />
    </>
  ),
  activity: <path d="M3 12h4l3-8 4 16 3-8h4" />,
  traces: (
    <>
      <circle cx="5" cy="6" r="2" />
      <circle cx="5" cy="18" r="2" />
      <path d="M5 8v8M10 6h10M10 12h8M10 18h10M5 12h2" />
    </>
  ),
  storage: (
    <>
      <ellipse cx="12" cy="5" rx="8" ry="3" />
      <path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5" />
      <path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3" />
    </>
  ),
  settings: (
    <>
      <path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1" />
      <circle cx="15" cy="6" r="2" />
      <circle cx="9" cy="12" r="2" />
      <circle cx="17" cy="18" r="2" />
    </>
  ),
};

function ConsoleWorkspace() {
  const { target } = useTarget();
  const { instance, loadInstance } = useObservability();
  const established =
    target.status.targetAuthentication === "ESTABLISHED" &&
    target.status.javaGoCompatibility === "COMPATIBLE";

  useEffect(() => {
    if (established && instance === null) void loadInstance();
  }, [established, instance, loadInstance]);

  const status = instance?.status as InstanceStatus | undefined;
  return (
    <>
      <aside className="global-context" aria-label="Current target and live context">
        <strong className="context-address">{target.address ?? "No target selected"}</strong>
        <ContextChip label="Connection" value={target.status.targetConnection} />
        <ContextChip label="Authentication" value={target.status.targetAuthentication} />
        <ContextChip label="Compatibility" value={target.status.javaGoCompatibility} />
        <ContextChip label="Runtime" value={target.status.runtimeIdentity} />
        <ContextChip
          label="Instance"
          value={status?.instanceId ?? target.status.instanceId ?? "Not established"}
        />
        {target.unencrypted && (
          <strong className="context-warning">Unencrypted target connection</strong>
        )}
        <NavLink className="context-link" to="/active-executions">
          Active executions: {status?.activeExecutionCount ?? "Unavailable"}
        </NavLink>
      </aside>
      <Outlet />
    </>
  );
}

const chipTones: Record<string, string> = {
  ESTABLISHED: "positive",
  COMPATIBLE: "positive",
  REACHABLE: "positive",
  AVAILABLE: "positive",
  SELECTED: "positive",
  INCOMPATIBLE: "negative",
  BLOCKED: "negative",
  UNAVAILABLE: "negative",
  REQUIRED: "caution",
  UNKNOWN: "caution",
  NOT_CHECKED: "caution",
};

function ContextChip({ label, value }: { label: string; value: string }) {
  return (
    <span className="context-chip" data-tone={chipTones[value] ?? "neutral"}>
      <span className="context-chip-label">{label}</span>
      {value}
    </span>
  );
}

export function NotFound() {
  return (
    <section aria-labelledby="not-found-title" className="foundation-card">
      <p className="eyebrow">Not found</p>
      <h2 id="not-found-title">This Console route does not exist</h2>
      <p>Check the address and return to the Console Overview.</p>
    </section>
  );
}
