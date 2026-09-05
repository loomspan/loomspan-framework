import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
} from "react";
import {
  BrowserAPIError,
  getObservabilityInstance,
  listActiveExecutions,
  listSkills,
  listTraces,
} from "../api/client";
import type {
  ActiveExecution,
  ActivePage,
  InstanceStatus,
  Page,
  SkillSummary,
  Trace,
} from "../api/contracts";
import { useTarget } from "../target/TargetProvider";
import {
  initialObservabilityState,
  observabilityReducer,
  type ObservabilityState,
} from "./reducer";
import {
  recoverObservabilityError,
  requireCurrentTargetScope,
} from "./scope";

type ObservabilityContextValue = ObservabilityState & {
  loadInstance(): Promise<void>;
  loadSkills(cursor?: string): Promise<void>;
  loadActiveExecutions(cursor?: string): Promise<void>;
  loadTraces(cursor?: string): Promise<void>;
};

const ObservabilityContext = createContext<ObservabilityContextValue | undefined>(undefined);

export function ObservabilityProvider({ children }: { children: ReactNode }) {
  const { target, scopeGeneration, refresh } = useTarget();
  const [state, dispatch] = useReducer(observabilityReducer, initialObservabilityState);
  const generationRef = useRef(0);
  const firstRender = useRef(true);
  const instanceRequestRef = useRef(0);
  const skillsRequestRef = useRef(0);
  const activeRequestRef = useRef(0);
  const tracesRequestRef = useRef(0);
  const targetScopeID = target.status.targetScopeId;

  const requireCurrentScope = useCallback(async (responseScopeID: string) => {
    await requireCurrentTargetScope(responseScopeID, targetScopeID, refresh);
  }, [refresh, targetScopeID]);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    generationRef.current++;
    dispatch({ type: "reset" });
  }, [scopeGeneration]);

  const loadInstance = useCallback(async () => {
    const gen = generationRef.current;
    const requestID = ++instanceRequestRef.current;
    dispatch({ type: "instance-loading" });
    try {
      const status = await getObservabilityInstance();
      await requireCurrentScope(status.targetScopeId);
      if (gen !== generationRef.current || requestID !== instanceRequestRef.current) return;
      dispatch({ type: "instance-success", status });
    } catch (error) {
      const recovered = await recoverObservabilityError(error, refresh);
      if (gen !== generationRef.current || requestID !== instanceRequestRef.current) return;
      dispatch({
        type: "instance-error",
        error: recovered,
      });
    }
  }, [refresh, requireCurrentScope]);

  const loadSkills = useCallback(async (cursor?: string) => {
    const gen = generationRef.current;
    const requestID = ++skillsRequestRef.current;
    dispatch({ type: "skills-loading" });
    try {
      let page: Page<SkillSummary>;
      let append = cursor != null;
      try {
        page = await listSkills(cursor);
      } catch (error) {
        if (!cursor || !isExpiredCursor(error)) throw error;
        page = await listSkills();
        append = false;
      }
      await requireCurrentScope(page.targetScopeId);
      if (gen !== generationRef.current || requestID !== skillsRequestRef.current) return;
      dispatch({ type: "skills-success", targetScopeId: page.targetScopeId, items: page.items, hasMore: page.hasMore, nextCursor: page.nextCursor, observedAt: page.observedAt, append });
    } catch (error) {
      const recovered = await recoverObservabilityError(error, refresh);
      if (gen !== generationRef.current || requestID !== skillsRequestRef.current) return;
      dispatch({
        type: "skills-error",
        error: recovered,
      });
    }
  }, [refresh, requireCurrentScope]);

  const loadActiveExecutions = useCallback(async (cursor?: string) => {
    const gen = generationRef.current;
    const requestID = ++activeRequestRef.current;
    dispatch({ type: "active-loading" });
    try {
      let page: ActivePage;
      let append = cursor != null;
      try {
        page = await listActiveExecutions(cursor);
      } catch (error) {
        if (!cursor || !isExpiredCursor(error)) throw error;
        page = await listActiveExecutions();
        append = false;
      }
      await requireCurrentScope(page.targetScopeId);
      if (gen !== generationRef.current || requestID !== activeRequestRef.current) return;
      dispatch({
        type: "active-success",
        targetScopeId: page.targetScopeId,
        items: page.items as ActiveExecution[],
        hasMore: page.hasMore,
        nextCursor: page.nextCursor,
        resumeCursor: page.resumeCursor,
        observedAt: page.observedAt,
        append,
      });
    } catch (error) {
      const recovered = await recoverObservabilityError(error, refresh);
      if (gen !== generationRef.current || requestID !== activeRequestRef.current) return;
      dispatch({
        type: "active-error",
        error: recovered,
      });
    }
  }, [refresh, requireCurrentScope]);

  const loadTraces = useCallback(async (cursor?: string) => {
    const gen = generationRef.current;
    const requestID = ++tracesRequestRef.current;
    dispatch({ type: "traces-loading" });
    try {
      let page: Page<Trace>;
      let append = cursor != null;
      try {
        page = await listTraces(cursor);
      } catch (error) {
        if (!cursor || !isExpiredCursor(error)) throw error;
        page = await listTraces();
        append = false;
      }
      await requireCurrentScope(page.targetScopeId);
      if (gen !== generationRef.current || requestID !== tracesRequestRef.current) return;
      dispatch({ type: "traces-success", targetScopeId: page.targetScopeId, items: page.items, hasMore: page.hasMore, nextCursor: page.nextCursor, observedAt: page.observedAt, append });
    } catch (error) {
      const recovered = await recoverObservabilityError(error, refresh);
      if (gen !== generationRef.current || requestID !== tracesRequestRef.current) return;
      dispatch({
        type: "traces-error",
        error: recovered,
      });
    }
  }, [refresh, requireCurrentScope]);

  const value = useMemo<ObservabilityContextValue>(
    () => ({
      ...state,
      loadInstance,
      loadSkills,
      loadActiveExecutions,
      loadTraces,
    }),
    [state, loadInstance, loadSkills, loadActiveExecutions, loadTraces],
  );

  return (
    <ObservabilityContext.Provider value={value}>
      {children}
    </ObservabilityContext.Provider>
  );
}

export function useObservability() {
  const value = useContext(ObservabilityContext);
  if (!value) throw new Error("ObservabilityProvider is missing");
  return value;
}

export function useOptionalObservability() {
  return useContext(ObservabilityContext);
}

function isExpiredCursor(error: unknown) {
  return error instanceof BrowserAPIError &&
    error.code === "STALE_CURSOR";
}
