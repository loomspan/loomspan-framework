import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { BrowserAPIError, getSkillDetail } from "../api/client";
import type { SkillDetail } from "../api/contracts";
import { useTarget } from "../target/TargetProvider";
import {
  recoverObservabilityError,
  requireCurrentTargetScope,
} from "./scope";
import { useScopeBoundRoute } from "./useScopeBoundRoute";
import { formatJavaSkillMethod } from "./javaSkillLocation";

export function SkillDetailView() {
  const { registeredName } = useParams();
  const { target, scopeGeneration, refresh } = useTarget();
  const [detail, setDetail] = useState<SkillDetail | null>(null);
  const [error, setError] = useState<BrowserAPIError | null>(null);
  const [loading, setLoading] = useState(true);
  const heading = useRef<HTMLHeadingElement>(null);
  const refreshTarget = useRef(refresh);
  refreshTarget.current = refresh;
  const routeIsCurrent = useScopeBoundRoute();

  useEffect(() => {
    heading.current?.focus();
  }, []);

  useEffect(() => {
    if (!registeredName || !routeIsCurrent) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    setDetail(null);
    getSkillDetail(registeredName)
      .then(async (d) => {
        await requireCurrentTargetScope(d.targetScopeId, target.status.targetScopeId, refreshTarget.current);
        if (!cancelled) setDetail(d);
      })
      .catch(async (err) => {
        const recovered = await recoverObservabilityError(err, refreshTarget.current);
        if (cancelled) return;
        setError(recovered);
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [registeredName, routeIsCurrent, scopeGeneration, target.status.targetScopeId]);

  return (
    <section aria-labelledby="skill-detail-title" className="overview-card">
      <p className="eyebrow">Operational views</p>
      <div className="view-header">
        <h2 id="skill-detail-title" ref={heading} tabIndex={-1}>Skill Detail</h2>
        <Link className="view-back-link" to="/skills">Back to Skill Catalog</Link>
      </div>

      {error && (
        <div className="target-error" role="alert">
          <strong>{error.message}</strong>
        </div>
      )}

      {loading && <p role="status" className="observability-note">Loading skill detail…</p>}

      {detail && (
        <>
          <dl className={`status-grid skill-detail-facts${detail.source === "JAVA" ? " java-skill-detail-facts" : ""}`}>
            <div>
              <dt>Registered name</dt>
              <dd>{detail.registeredName}</dd>
            </div>
            <div><dt>Source</dt><dd>{detail.source === "JAVA" ? "Java" : "YAML"}</dd></div>
            {detail.source === "JAVA" ? <>
              <div><dt>Bean</dt><dd><code>{detail.beanName}</code></dd></div>
              <div className="skill-detail-method"><dt>Method</dt><dd><code>{formatJavaSkillMethod(detail.method)}</code></dd></div>
            </> : <div>
              <dt>Source path</dt>
              <dd><code className="source-path">{detail.sourcePath}</code></dd>
            </div>}
          </dl>

          {detail.source === "YAML" && <section className="view-subsection" aria-labelledby="skill-yaml-title">
            <h3 id="skill-yaml-title">Skill YAML</h3>
            <pre className="yaml-block" aria-label="Skill YAML source">{detail.yaml}</pre>
          </section>}
        </>
      )}
    </section>
  );
}
