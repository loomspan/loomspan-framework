export type TraceExplorerView = "timeline" | "plans" | "usage" | "records";

const views = new Set<TraceExplorerView>(["timeline", "plans", "usage", "records"]);

// Explorer coordinates are conveniences for a current target scope only. This
// parser deliberately drops malformed selections rather than applying them to
// an unrelated trace after a navigation or refresh.
export function readTraceExplorerState(params: URLSearchParams) {
  const view = params.get("view");
  const recordSequence = params.get("recordSequence");
  const planId = params.get("planId");
  const transitionSequence = params.get("transitionSequence");
  const positiveInteger = (value: string | null) => value !== null && /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value));
  const hasNonblankPlan = Boolean(planId?.trim());
  const planValid = !params.has("planId") || hasNonblankPlan;
  const transitionValid = !params.has("transitionSequence") || (hasNonblankPlan && positiveInteger(transitionSequence));
  return {
    view: views.has(view as TraceExplorerView) ? view as TraceExplorerView : "timeline" as TraceExplorerView,
    frameId: params.get("frameId") || undefined,
    failureId: params.get("failureId") || undefined,
    recordSequence: positiveInteger(recordSequence) ? Number(recordSequence) : undefined,
    planId: hasNonblankPlan ? planId! : undefined,
    transitionSequence: positiveInteger(transitionSequence) && hasNonblankPlan ? Number(transitionSequence) : undefined,
    valid: (!view || views.has(view as TraceExplorerView)) && (!recordSequence || positiveInteger(recordSequence)) && planValid && transitionValid,
  };
}

export function setTraceExplorerSelection(params: URLSearchParams, values: Record<string, string | number | undefined>) {
  const next = new URLSearchParams(params);
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined) next.delete(key); else next.set(key, String(value));
  }
  return next;
}
