import type { TraceFrame, TraceRecord } from "../api/contracts";

type ContextFrame = Pick<TraceFrame, "frameId" | "parentFrameId" | "frameType" | "route">;

// Format labels emitted by the current runtime. This is presentation of the
// recorded route, not a lookup of plan task positions or provider attempts.
function routeContext(frame: ContextFrame): { skill?: string; phase: string } | undefined {
  const route = frame.route;
  const step = frame.frameType === "STEP_EXECUTION"
    ? /^(.*)#step-(\d+)$/.exec(route)
    : frame.frameType === "MODEL_CALL" ? /^(.*)#step-(\d+)-model$/.exec(route) : null;
  if (step && Number.isSafeInteger(Number(step[2])) && Number(step[2]) > 0) {
    return { skill: step[1] || undefined, phase: `Step ${Number(step[2])}` };
  }
  const phase = frame.frameType === "MODEL_CALL"
    ? /^(.*)#(planning|mission)-model$/.exec(route)
    : frame.frameType === "PLANNING" ? /^(.*)#(planning)$/.exec(route) : null;
  if (phase) return { skill: phase[1] || undefined, phase: phase[2] === "planning" ? "Planning" : "Mission" };
  return undefined;
}

export function recordContext(record: TraceRecord, frames: ReadonlyMap<string, ContextFrame>): { label: string; fullPath: string } {
  const labels: string[] = [];
  const visited = new Set<string>();
  let frame: ContextFrame | undefined = record;
  let child: ContextFrame | undefined;
  let skill: string | undefined;
  let phase: string | undefined;
  let missingAncestry = false;
  let missingPhase: string | undefined;

  while (frame) {
    if (frame.frameId && visited.has(frame.frameId)) {
      missingAncestry = true;
      break;
    }
    if (frame.frameId) visited.add(frame.frameId);
    const isSkill = frame.frameType === "ROOT_MISSION" || frame.frameType === "SKILL_EXECUTION";
    // A nested mission's immediately enclosing skill frame is its invocation
    // wrapper, not an additional parent skill. Preserve genuine recursion.
    const isMissionWrapper = frame.frameType === "SKILL_EXECUTION"
      && child?.frameType === "ROOT_MISSION" && frame.route === child.route;
    if (isSkill && !isMissionWrapper) {
      labels.unshift([skill || frame.route || "Skill unavailable", phase ?? missingPhase].filter(Boolean).join(" / "));
      skill = undefined;
      phase = undefined;
      missingPhase = undefined;
    } else if (!isSkill) {
      const context = routeContext(frame);
      skill ??= context?.skill;
      phase ??= context?.phase;
      if (frame.frameType === "STEP_EXECUTION") missingPhase = "Step unavailable";
      else if (frame.frameType === "PLANNING") phase ??= "Planning";
      else if (frame.frameType === "MODEL_CALL") missingPhase ??= "Model context unavailable";
    }
    if (!frame.parentFrameId) break;
    child = frame;
    frame = frames.get(frame.parentFrameId);
    if (!frame) missingAncestry = true;
  }
  if (skill || phase) labels.unshift([skill || "Skill unavailable", phase ?? missingPhase].filter(Boolean).join(" / "));
  if (labels.length === 0) return { label: "Context unavailable", fullPath: "Context unavailable" };
  const label = labels[labels.length - 1];
  if (missingAncestry) labels.unshift("Parent context unavailable");
  return { label, fullPath: labels.join(" → ") };
}
