import type { BrowserAPIError } from "../api/client";

export type ObservabilityState = {
  instance: null | { status: unknown; error?: undefined; loading?: boolean } | { error: BrowserAPIError; status?: undefined; loading?: boolean } | { loading: true; status?: undefined; error?: undefined };
  skills: { targetScopeId: string | null; items: unknown[]; hasMore: boolean; nextCursor: string | null; observedAt: string | null; loading: boolean; loaded: boolean; error?: BrowserAPIError };
  activeExecutions: { targetScopeId: string | null; items: unknown[]; hasMore: boolean; nextCursor: string | null; resumeCursor: string | null; observedAt: string | null; loading: boolean; loaded: boolean; error?: BrowserAPIError };
  traces: { targetScopeId: string | null; items: unknown[]; hasMore: boolean; nextCursor: string | null; observedAt: string | null; loading: boolean; loaded: boolean; error?: BrowserAPIError };
};

export type ObservabilityAction =
  | { type: "reset" }
  | { type: "instance-loading" }
  | { type: "instance-success"; status: unknown }
  | { type: "instance-error"; error: BrowserAPIError }
  | { type: "skills-loading" }
  | { type: "skills-success"; targetScopeId: string; items: unknown[]; hasMore: boolean; nextCursor: string | null; observedAt?: string; append?: boolean }
  | { type: "skills-error"; error: BrowserAPIError }
  | { type: "active-loading" }
  | { type: "active-success"; targetScopeId: string; items: unknown[]; hasMore: boolean; nextCursor: string | null; resumeCursor: string | null; observedAt?: string; append?: boolean }
  | { type: "active-error"; error: BrowserAPIError }
  | { type: "traces-loading" }
  | { type: "traces-success"; targetScopeId: string; items: unknown[]; hasMore: boolean; nextCursor: string | null; observedAt?: string; append?: boolean }
  | { type: "traces-error"; error: BrowserAPIError };

export const initialObservabilityState: ObservabilityState = {
  instance: null,
  skills: { targetScopeId: null, items: [], hasMore: false, nextCursor: null, observedAt: null, loading: false, loaded: false },
  activeExecutions: { targetScopeId: null, items: [], hasMore: false, nextCursor: null, resumeCursor: null, observedAt: null, loading: false, loaded: false },
  traces: { targetScopeId: null, items: [], hasMore: false, nextCursor: null, observedAt: null, loading: false, loaded: false },
};

export function observabilityReducer(
  state: ObservabilityState,
  action: ObservabilityAction,
): ObservabilityState {
  switch (action.type) {
    case "reset":
      return initialObservabilityState;
    case "instance-loading":
      if (state.instance === null) return { ...state, instance: { loading: true } };
      return { ...state, instance: { ...state.instance, loading: true } };
    case "instance-success":
      return { ...state, instance: { status: action.status } };
    case "instance-error":
      return { ...state, instance: { error: action.error } };
    case "skills-loading":
      return { ...state, skills: { ...state.skills, loading: true, error: undefined } };
    case "skills-success":
      return {
        ...state,
        skills: {
          targetScopeId: action.append ? state.skills.targetScopeId : action.targetScopeId,
          items: action.append ? [...state.skills.items, ...action.items] : action.items,
          hasMore: action.hasMore,
          nextCursor: action.nextCursor,
          observedAt: action.append ? state.skills.observedAt : (action.observedAt ?? null),
          loading: false,
          loaded: true,
        },
      };
    case "skills-error":
      return { ...state, skills: { ...state.skills, loading: false, error: action.error } };
    case "active-loading":
      return { ...state, activeExecutions: { ...state.activeExecutions, loading: true, error: undefined } };
    case "active-success":
      return {
        ...state,
        activeExecutions: {
          targetScopeId: action.append ? state.activeExecutions.targetScopeId : action.targetScopeId,
          items: action.append ? [...state.activeExecutions.items, ...action.items] : action.items,
          hasMore: action.hasMore,
          nextCursor: action.nextCursor,
          resumeCursor: action.append ? (state.activeExecutions.resumeCursor ?? action.resumeCursor) : action.resumeCursor,
          observedAt: action.append ? state.activeExecutions.observedAt : (action.observedAt ?? null),
          loading: false,
          loaded: true,
        },
      };
    case "active-error":
      return { ...state, activeExecutions: { ...state.activeExecutions, loading: false, error: action.error } };
    case "traces-loading":
      return { ...state, traces: { ...state.traces, loading: true, error: undefined } };
    case "traces-success":
      return {
        ...state,
        traces: {
          targetScopeId: action.append ? state.traces.targetScopeId : action.targetScopeId,
          items: action.append ? [...state.traces.items, ...action.items] : action.items,
          hasMore: action.hasMore,
          nextCursor: action.nextCursor,
          observedAt: action.append ? state.traces.observedAt : (action.observedAt ?? null),
          loading: false,
          loaded: true,
        },
      };
    case "traces-error":
      return { ...state, traces: { ...state.traces, loading: false, error: action.error } };
  }
}
