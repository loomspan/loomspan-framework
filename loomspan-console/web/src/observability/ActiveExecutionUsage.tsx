import type { ConfiguredLimits, Usage } from "../api/contracts";

function proportion(observed: number, limit: number) {
  if (limit === 0) return "undefined";
  return `${Number(((observed / limit) * 100).toFixed(2))}%`;
}

function BoundedRow({ label, observed, limit }: { label: string; observed: number; limit: number }) {
  return (
    <tr>
      <th scope="row">{label}</th>
      <td className="numeric-cell">{observed}</td>
      <td className="numeric-cell">{limit}</td>
      <td className="numeric-cell">{proportion(observed, limit)}</td>
    </tr>
  );
}

export function ActiveExecutionUsage({ usage, limits }: { usage: Usage; limits: ConfiguredLimits }) {
  const measured =
    usage.exactModelResponses + usage.heuristicModelResponses + usage.unavailableModelResponses;
  const exact = usage.heuristicModelResponses === 0 && usage.unavailableModelResponses === 0;

  return (
    <section className="view-subsection" aria-labelledby="active-execution-usage-title">
      <h3 id="active-execution-usage-title">Usage</h3>
      <p className="observability-note">
        Observed counters against the limits configured at run start.
      </p>
      <div
        className="observability-table-region"
        role="region"
        aria-label="Usage against configured limits"
        tabIndex={0}
      >
        <table className="observability-table usage-table" aria-label="Observed usage against configured limits">
          <thead>
            <tr>
              <th scope="col">Counter</th>
              <th scope="col" className="numeric-cell">Observed</th>
              <th scope="col" className="numeric-cell">Configured limit</th>
              <th scope="col" className="numeric-cell">Proportion</th>
            </tr>
          </thead>
          <tbody>
            <BoundedRow label="Skill invocations" observed={usage.skillInvocations} limit={limits.maxSkillInvocations} />
            <BoundedRow label="Tool invocations" observed={usage.toolInvocations} limit={limits.maxToolInvocations} />
            <BoundedRow label="Linter retries" observed={usage.linterRetries} limit={limits.maxLinterRetries} />
            <BoundedRow label="Model calls" observed={usage.modelCalls} limit={limits.maxModelCalls} />
            <BoundedRow label="Provider attempts" observed={usage.providerAttempts} limit={limits.maxProviderAttempts} />
            <BoundedRow label="Usage units" observed={usage.usageUnits} limit={limits.maxUsageUnits} />
          </tbody>
        </table>
      </div>

      <p className="observability-note">Unit totals, which carry no configured limit of their own.</p>
      <div
        className="observability-table-region"
        role="region"
        aria-label="Usage unit totals"
        tabIndex={0}
      >
        <table className="observability-table usage-table" aria-label="Observed usage units">
          <thead>
            <tr>
              <th scope="col">Units</th>
              <th scope="col" className="numeric-cell">Observed</th>
            </tr>
          </thead>
          <tbody>
            <tr><th scope="row">Prompt units</th><td className="numeric-cell">{usage.promptUnits}</td></tr>
            <tr><th scope="row">Completion units</th><td className="numeric-cell">{usage.completionUnits}</td></tr>
          </tbody>
        </table>
      </div>

      {measured === 0 ? (
        <p className="observability-note">No model responses have been measured yet.</p>
      ) : exact ? (
        <p className="observability-note">
          All model responses reported exact usage ({measured} measured).
        </p>
      ) : (
        <p className="usage-quality-notice" role="status">
          These counts are not exact. Model responses measured: {measured}; heuristic:{" "}
          {usage.heuristicModelResponses}; usage unavailable:{" "}
          {usage.unavailableModelResponses}.
        </p>
      )}
    </section>
  );
}
