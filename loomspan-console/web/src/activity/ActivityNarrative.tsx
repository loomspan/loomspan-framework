import { useCallback, useLayoutEffect, useRef, useState, type KeyboardEvent, type WheelEvent } from "react";
import type { Activity } from "../api/contracts";
import { presentActivity, formatTimestamp, formatDelta } from "./activityPresentation";

type ActivityNarrativeProps = {
  activities: Activity[];
  isLive: boolean;
  alwaysFollow?: boolean;
  ariaLabel?: string;
  compact?: boolean;
};

export function ActivityNarrative({
  activities,
  alwaysFollow = false,
  ariaLabel = "Activity narrative",
  compact = false,
}: ActivityNarrativeProps) {
  const [following, setFollowing] = useState(true);
  const listRef = useRef<HTMLOListElement>(null);
  const pointerScrollingRef = useRef(false);

  const isAtBottom = useCallback(() => {
    const el = listRef.current;
    if (!el) return true;
    return el.scrollHeight - el.scrollTop - el.clientHeight < 4;
  }, []);

  const scrollToBottom = useCallback(() => {
    const el = listRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, []);

  useLayoutEffect(() => {
    if (alwaysFollow || following) {
      scrollToBottom();
    }
  }, [activities, alwaysFollow, following, scrollToBottom]);

  const handleScroll = useCallback(() => {
    if (!alwaysFollow && pointerScrollingRef.current && !isAtBottom() && following) {
      setFollowing(false);
    }
  }, [alwaysFollow, isAtBottom, following]);

  const handleWheel = useCallback((event: WheelEvent<HTMLOListElement>) => {
    if (!alwaysFollow && event.deltaY < 0) setFollowing(false);
  }, [alwaysFollow]);

  const handleKeyDown = useCallback((event: KeyboardEvent<HTMLOListElement>) => {
    const scrollsBackward = event.key === "ArrowUp" || event.key === "PageUp" ||
      event.key === "Home" || (event.key === " " && event.shiftKey);
    if (!alwaysFollow && scrollsBackward) setFollowing(false);
  }, [alwaysFollow]);

  const handleFollowToggle = useCallback(() => {
    setFollowing((prev) => {
      if (!prev) {
        scrollToBottom();
      }
      return !prev;
    });
  }, [scrollToBottom]);

  return (
    <div className={`activity-narrative${compact ? " compact" : ""}`}>
      <div className="activity-narrative-controls">
        {!alwaysFollow && (
          <button
            type="button"
            className="follow-toggle"
            onClick={handleFollowToggle}
            aria-pressed={following}
            aria-label={following ? "Pause auto-scroll" : "Resume auto-scroll"}
          >
            {following ? "⏸ Pause" : "▶ Follow"}
          </button>
        )}
        <span className="activity-count" aria-live="polite">
          {activities.length} event{activities.length !== 1 ? "s" : ""}
        </span>
      </div>
      <ol
        ref={listRef}
        onScroll={handleScroll}
        onWheel={handleWheel}
        onKeyDown={handleKeyDown}
        onPointerDown={() => { pointerScrollingRef.current = true; }}
        onPointerUp={() => { pointerScrollingRef.current = false; }}
        onPointerCancel={() => { pointerScrollingRef.current = false; }}
        onLostPointerCapture={() => { pointerScrollingRef.current = false; }}
        className="activity-narrative-list"
        aria-label={ariaLabel}
        role="log"
        aria-live="polite"
        aria-relevant="additions"
      >
        {activities.length === 0 && (
          <li className="activity-narrative-empty">No activity yet.</li>
        )}
        {activities.map((activity, index) => {
          const p = presentActivity(activity);
          const previous = index === 0 ? null : activities[index - 1];
          const delta = previous ? formatDelta(previous.timestamp, activity.timestamp) : null;
          return (
            <li
              key={activity.cursor}
              className={`activity-narrative-item ${p.severity}${p.isTerminal ? " terminal" : ""}${p.isFrameBoundary ? " frame-boundary" : ""}`}
              aria-label={`${p.severity === "normal" ? "Normal" : p.severity === "warning" ? "Warning" : "Error"} activity: ${p.label}`}
            >
              <span className="activity-narrative-time" aria-hidden="true">
                {formatTimestamp(activity.timestamp)}
              </span>
              <span className="activity-narrative-delta" aria-hidden="true">
                {delta ?? ""}
              </span>
              <span className="activity-narrative-kind">{p.label}</span>
              <span className="activity-narrative-body">
                {p.headline && (
                  <span className="activity-narrative-headline">{p.headline}</span>
                )}
                {activity.summary !== p.label && (
                  <span className="activity-narrative-summary">{activity.summary}</span>
                )}
                {p.facts.map((fact) => (
                  <span
                    key={fact.label}
                    className="activity-narrative-fact"
                    {...(fact.title ? { title: fact.title } : {})}
                  >
                    <span className="activity-narrative-fact-label">{fact.label}</span>
                    <span className="activity-narrative-fact-value">{fact.value}</span>
                  </span>
                ))}
              </span>
              {p.outcome && (
                <span className="activity-narrative-outcome">Outcome: {p.outcome}</span>
              )}
              {p.artifactAvailable && (
                <span className="activity-narrative-artifact">Artifact available</span>
              )}
              {p.scope && (
                <span
                  className="activity-narrative-meta"
                  {...(p.scopeTitle ? { title: p.scopeTitle } : {})}
                >
                  {p.scope}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </div>
  );
}
