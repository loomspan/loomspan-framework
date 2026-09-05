import { useEffect, useRef } from "react";
import { Link } from "react-router";
import { useObservability } from "./ObservabilityProvider";
import type { SkillSummary } from "../api/contracts";
import { scopeBoundPath } from "./scope";
import { formatJavaSkillMethod } from "./javaSkillLocation";

export function SkillCatalog() {
  const { skills, loadSkills } = useObservability();
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    heading.current?.focus();
  }, []);

  useEffect(() => {
    if (!skills.loaded && !skills.loading && !skills.error) {
      void loadSkills();
    }
  }, [skills, loadSkills]);

  return (
    <section aria-labelledby="skill-catalog-title" className="overview-card">
      <p className="eyebrow">Operational views</p>
      <div className="view-header">
        <h2 id="skill-catalog-title" ref={heading} tabIndex={-1}>Skill Catalog</h2>
        <button type="button" disabled={skills.loading} onClick={() => void loadSkills()}>
          Refresh
        </button>
      </div>

      {skills.error && (
        <div className="target-error" role="alert">
          <strong>{skills.error.message}</strong>
          <div className="view-actions">
            <button type="button" disabled={skills.loading} onClick={() => void loadSkills()}>
              Retry
            </button>
          </div>
        </div>
      )}

      {skills.loading && <p role="status" className="observability-note">Loading skills…</p>}

      {skills.loaded && !skills.loading && skills.items.length === 0 && !skills.error && (
        <p className="empty-state">No skills are registered.</p>
      )}

      {skills.items.length > 0 && (
        <div className="observability-table-region" role="region" aria-label="Skill catalog table" tabIndex={0}>
          <table className="observability-table skill-catalog-table">
          <thead>
            <tr>
              <th scope="col">Registered name</th>
              <th scope="col">Source</th>
              <th scope="col">Location</th>
            </tr>
          </thead>
          <tbody>
            {skills.items.map((skill) => {
              const s = skill as SkillSummary;
              return (
                <tr key={s.registeredName}>
                  <td className="identifier-cell">
                    <Link to={scopeBoundPath(`/skills/${encodeURIComponent(s.registeredName)}`, skills.targetScopeId)}>{s.registeredName}</Link>
                  </td>
                  <td>{s.source === "JAVA" ? "Java" : "YAML"}</td>
                  <td className="path-cell"><code className="source-path">{s.source === "JAVA" ? formatJavaSkillMethod(s.method) : s.sourcePath}</code></td>
                </tr>
              );
            })}
          </tbody>
          </table>
        </div>
      )}

      {skills.hasMore && skills.nextCursor && (
        <div className="view-actions">
          <button type="button" disabled={skills.loading} onClick={() => void loadSkills(skills.nextCursor ?? undefined)}>
            Load more
          </button>
        </div>
      )}
    </section>
  );
}
