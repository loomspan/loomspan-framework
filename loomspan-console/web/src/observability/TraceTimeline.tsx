import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import type { TraceFrame } from "../api/contracts";
import { formatDuration } from "../duration";

type TimelineBarState = "normal" | "warning" | "error";

function timelineBarState(frame: TraceFrame): TimelineBarState {
  if (frame.failureIds.length > 0 || frame.outcome === "failed" || frame.outcome === "aborted") return "error";
  const hasRetry = frame.frameType === "RETRY" || frame.attemptIds.length > frame.retrySequenceIds.length;
  const hasValidationWarning = frame.validationStatuses.some((status) => !["PASSED", "SUCCEEDED", "VALID"].includes(status.toUpperCase()));
  return hasRetry || hasValidationWarning ? "warning" : "normal";
}

export function TraceTimeline({ frames, selectedFrameId, onSelect }: { frames: TraceFrame[]; selectedFrameId?: string; onSelect: (frameId: string) => void }) {
  const [hoveredFrameId, setHoveredFrameId] = useState<string>();
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set());
  const { byId, children } = useMemo(() => {
    const byId = new Map(frames.map((frame) => [frame.frameId, frame]));
    const children = new Map<string, TraceFrame[]>();
    for (const frame of frames) {
      if (!frame.parentFrameId || !byId.has(frame.parentFrameId)) continue;
      const siblings = children.get(frame.parentFrameId) ?? [];
      siblings.push(frame);
      children.set(frame.parentFrameId, siblings);
    }
    return { byId, children };
  }, [frames]);
  useEffect(() => {
    const ancestors = new Set<string>();
    let parentId = selectedFrameId ? byId.get(selectedFrameId)?.parentFrameId : undefined;
    while (parentId && !ancestors.has(parentId)) {
      ancestors.add(parentId);
      parentId = byId.get(parentId)?.parentFrameId;
    }
    setCollapsed((current) => new Set([...current].filter((id) => !ancestors.has(id))));
  }, [byId, selectedFrameId]);
  const visible = useMemo(() => {
    const rows: Array<{ frame: TraceFrame; depth: number }> = [];
    const visited = new Set<string>();
    const visit = (frame: TraceFrame, depth: number) => {
      if (visited.has(frame.frameId)) return;
      visited.add(frame.frameId);
      rows.push({ frame, depth });
      if (!collapsed.has(frame.frameId)) for (const child of children.get(frame.frameId) ?? []) visit(child, depth + 1);
    };
    for (const frame of frames) if (!frame.parentFrameId || !byId.has(frame.parentFrameId)) visit(frame, 0);
    return rows;
  }, [frames, byId, children, collapsed]);
  const toggle = (frameId: string) => {
    setHoveredFrameId(undefined);
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(frameId)) next.delete(frameId); else next.add(frameId);
      return next;
    });
  };
  const move = (event: KeyboardEvent<HTMLButtonElement>, frame: TraceFrame) => {
    const buttons = [...(event.currentTarget.closest('[role="tree"]')?.querySelectorAll<HTMLButtonElement>("button[data-frame]") ?? [])];
    const index = buttons.indexOf(event.currentTarget);
    const hasChildren = children.has(frame.frameId);
    let target: HTMLButtonElement | undefined;
    switch (event.key) {
      case "Home": target = buttons[0]; break;
      case "End": target = buttons.at(-1); break;
      case "ArrowDown": target = buttons[index + 1]; break;
      case "ArrowUp": target = buttons[index - 1]; break;
      case "ArrowRight":
        if (hasChildren && collapsed.has(frame.frameId)) toggle(frame.frameId);
        else if (hasChildren) target = buttons[index + 1];
        break;
      case "ArrowLeft":
        if (hasChildren && !collapsed.has(frame.frameId)) toggle(frame.frameId);
        else target = buttons.find((button) => button.dataset.frame === frame.parentFrameId);
        break;
      default: return;
    }
    event.preventDefault();
    target?.focus();
  };
  const complete = frames.filter((frame) => frame.closedTimestampMillis != null);
  const start = complete.length ? Math.min(...complete.map((frame) => frame.openedTimestampMillis)) : 0;
  const end = complete.length ? Math.max(...complete.map((frame) => frame.closedTimestampMillis as number)) : start;
  const span = Math.max(1, end - start);
  return <div className="trace-timeline" role="tree" aria-label="Trace timeline">
    {visible.map(({ frame, depth }) => {
      const closed = frame.closedTimestampMillis;
      const available = closed != null && frame.inclusiveDurationMillis != null;
      const x = available ? ((frame.openedTimestampMillis - start) / span) * 1000 : 0;
      const width = available ? Math.max(2, ((closed - frame.openedTimestampMillis) / span) * 1000) : 0;
      const barState = timelineBarState(frame);
      const stateLabel = barState === "error" ? "Error or failure" : barState === "warning" ? "Retry or warning" : undefined;
      const tooltip = available ? `Duration: ${formatDuration(frame.inclusiveDurationMillis as number)}${stateLabel ? ` · ${stateLabel}` : ""}` : "";
      const hitWidth = Math.max(width, 14);
      const hitX = Math.min(1000 - hitWidth, Math.max(0, x - (hitWidth - width) / 2));
      const tooltipPosition = Math.min(96, Math.max(4, (x + width / 2) / 10));
      return <div className="trace-timeline-row" key={frame.frameId} role="treeitem" aria-label={frame.route || frame.frameId} aria-level={depth + 1} aria-expanded={children.has(frame.frameId) ? !collapsed.has(frame.frameId) : undefined} aria-selected={selectedFrameId === frame.frameId} aria-current={selectedFrameId === frame.frameId ? "true" : undefined}>
        <div className="trace-timeline-label" style={{ "--trace-depth": Math.min(depth, 8) } as React.CSSProperties}>
          {children.has(frame.frameId) && <button className="trace-timeline-toggle" type="button" aria-label={`${collapsed.has(frame.frameId) ? "Expand" : "Collapse"} ${frame.route || frame.frameId}`} onClick={() => toggle(frame.frameId)}>{collapsed.has(frame.frameId) ? "+" : "−"}</button>}
          <button className="trace-timeline-frame" data-frame={frame.frameId} type="button" aria-pressed={selectedFrameId === frame.frameId} onKeyDown={(event) => move(event, frame)} onClick={() => onSelect(frame.frameId)}>{frame.route || frame.frameId}</button>
        </div>
        {available ? <div className="trace-timeline-chart"><svg viewBox="0 0 1000 20" role="img" aria-label={`${formatDuration(frame.inclusiveDurationMillis as number)}, ${frame.selfDurationMillis == null ? "self timing unavailable" : `${formatDuration(frame.selfDurationMillis)} self`}${stateLabel ? `, ${stateLabel.toLowerCase()}` : ""}`} preserveAspectRatio="none"><rect className="trace-timeline-track" x="0" y="5" width="1000" height="10" /><rect className={`trace-timeline-bar trace-timeline-bar-${barState}`} x={x} y="5" width={width} height="10" /></svg><button className="trace-timeline-hit-target" data-frame-id={frame.frameId} type="button" aria-label={tooltip} style={{ left: `${hitX / 10}%`, width: `${hitWidth / 10}%` }} onPointerEnter={() => setHoveredFrameId(frame.frameId)} onPointerLeave={() => setHoveredFrameId(undefined)} onFocus={() => setHoveredFrameId(frame.frameId)} onBlur={() => setHoveredFrameId(undefined)} onClick={() => onSelect(frame.frameId)} />{hoveredFrameId === frame.frameId && <span className={`trace-timeline-tooltip trace-timeline-tooltip-${barState}`} role="tooltip" style={{ left: `${tooltipPosition}%` }}>{tooltip}</span>}</div> : <span className="trace-timeline-unavailable">Timing unavailable or incomplete</span>}
      </div>;
    })}
  </div>;
}
