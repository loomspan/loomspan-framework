import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Link, useSearchParams } from "react-router";
import {
  BrowserAPIError,
  getContentRange,
  getTraceAnalysisSummary,
  getTraceFailures,
  getTraceFrames,
  getTracePlans,
  getTraceRecords,
  getTraceUsage,
  listSkills,
  searchTraceEvidence,
} from "../api/client";
import type {
  TraceAnalysisPage,
  TraceAnalysisSummary,
  TraceFailure,
  TraceFrame,
  PlanSummary,
  TraceRange,
  TraceRecord,
  TraceSearchPage,
  TraceUsage as Usage,
  SkillSummary,
	TraceSource,
} from "../api/contracts";
import { useTarget } from "../target/TargetProvider";
import { TraceEvidenceDetail } from "./TraceEvidenceDetail";
import { TraceRecords } from "./TraceRecords";
import { TraceTimeline } from "./TraceTimeline";
import { TracePlans } from "./TracePlans";
import { TraceUsage } from "./TraceUsage";
import { requireCurrentTargetScope, scopeBoundPath } from "./scope";
import { TraceFailureFocus } from "./TraceFailureFocus";
import { TraceFailureDiagnostic } from "./TraceFailureDiagnostic";
import { readTraceExplorerState, setTraceExplorerSelection, type TraceExplorerView } from "./traceExplorerState";

const views: TraceExplorerView[] = ["timeline", "plans", "usage", "records"];

const defaultRecordTypes = [
  "MODEL_REQUEST_SENT",
  "MODEL_RESPONSE_RECEIVED",
  "MODEL_ATTEMPT_FAILED",
  "PLAN_CREATED",
  "PLAN_UPDATED",
  "PLAN_VALIDATION_FAILED",
  "PLAN_RETRY_REQUESTED",
  "STEP_STARTED",
  "STEP_ACTION_REJECTED",
  "STEP_COMPLETED",
  "STEP_FAILED",
  "TOOL_CALL_STARTED",
  "TOOL_CALL_COMPLETED",
  "TOOL_CALL_FAILED",
  "STRUCTURED_OUTPUT_RECORDED",
  "EVIDENCE_VALIDATION_FAILED",
  "ERROR_RECORDED",
  "TRACE_COMPLETED",
];

function appendPage<T, P extends TraceAnalysisPage<T>>(current: P, next: P): P {
  return { ...next, items: [...current.items, ...next.items] };
}

function appendSearchPage(current: TraceSearchPage, next: TraceSearchPage): TraceSearchPage {
  const prefix = `p${current.items.length}-`;
  const rebasedDescriptors = next.contentDescriptors.map((descriptor) => ({ ...descriptor, contentId: `${prefix}${descriptor.contentId}` }));
  const rebasedItems = next.items.map((item) => ({ ...item, contentId: item.contentId ? `${prefix}${item.contentId}` : undefined }));
  return { ...next, items: [...current.items, ...rebasedItems], contentDescriptors: [...current.contentDescriptors, ...rebasedDescriptors] };
}

function mergeFrames(current: TraceAnalysisPage<TraceFrame> | undefined, added: TraceFrame[]) {
  if (!current) return undefined;
  const replacements = new Map(added.map((frame) => [frame.frameId, frame]));
  const items = current.items.map((frame) => replacements.get(frame.frameId) ?? frame);
  const known = new Set(items.map((frame) => frame.frameId));
  return { ...current, items: [...items, ...added.filter((frame) => !known.has(frame.frameId))] };
}

type ExplorerProps = { traceId: string; source?: TraceSource; onArtifactUnavailable?: (error: BrowserAPIError) => void };

export function TraceExplorer(props: ExplorerProps) {
  const { target, scopeGeneration } = useTarget();
  const source = props.source ?? "TARGET";
  const identity = JSON.stringify([props.traceId, source, source === "TARGET" ? target.status.targetScopeId : "", source === "TARGET" ? scopeGeneration : 0]);
  return <TraceExplorerEvidence key={identity} {...props} />;
}

function TraceExplorerEvidence({ traceId, source = "TARGET", onArtifactUnavailable }: ExplorerProps) {
  const { target, scopeGeneration, refresh } = useTarget();
  const [params, setParams] = useSearchParams();
  const setParamsRef = useRef(setParams);
  setParamsRef.current = setParams;
  const state = readTraceExplorerState(params);
  const [summary, setSummary] = useState<TraceAnalysisSummary>();
  const [frames, setFrames] = useState<TraceAnalysisPage<TraceFrame>>();
	const [detailedFrames, setDetailedFrames] = useState<TraceAnalysisPage<TraceFrame>>();
  const [timelineStatus, setTimelineStatus] = useState<"idle" | "loading" | "loaded" | "failed">("idle");
  const [records, setRecords] = useState<TraceAnalysisPage<TraceRecord>>();
  const [showAllRecords, setShowAllRecords] = useState(false);
  const [plans, setPlans] = useState<TraceAnalysisPage<PlanSummary>>();
  const [failures, setFailures] = useState<TraceAnalysisPage<TraceFailure>>();
  const [searchText, setSearchText] = useState("");
  const [searchResults, setSearchResults] = useState<TraceSearchPage>();
  const [usage, setUsage] = useState<Usage>();
  const [usageFrames, setUsageFrames] = useState<TraceAnalysisPage<TraceFrame>>();
  const [usageResponseRecords, setUsageResponseRecords] = useState<TraceRecord[]>();
  const [registeredSkills, setRegisteredSkills] = useState<Set<string>>();
  const [range, setRange] = useState<TraceRange>();
  const [rangeRequest, setRangeRequest] = useState<{ contentRef: string; recordSequence?: number }>();
  const [rangeError, setRangeError] = useState<string>();
  const [error, setError] = useState<string>();
  const [scopeMismatch, setScopeMismatch] = useState(false);
  const [pending, setPending] = useState<Set<string>>(() => new Set());
  const recordFilter = useMemo(() => showAllRecords ? {} : { types: defaultRecordTypes }, [showAllRecords]);
  const recordEpoch = useRef(0);
  const mounted = useRef(true);
  const artifactUnavailable = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const currentScopeID = target.status.targetScopeId;
  const select = useCallback((values: Record<string, string | number | undefined>) => {
    setParamsRef.current((current) => setTraceExplorerSelection(current, values), { replace: true });
  }, []);
	const verifyImportedResponse = useCallback(async <T extends { source: TraceSource; targetScopeId?: string }>(response: T) => {
		if (response.source !== "IMPORTED" || response.targetScopeId) {
			setScopeMismatch(true);
			select({ frameId: undefined, recordSequence: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
		}
		return response;
	}, [select]);
	const verifyTargetResponse = useCallback(async <T extends { source: TraceSource; targetScopeId?: string }>(response: T) => {
		if (response.source !== "TARGET" || !response.targetScopeId || response.targetScopeId !== currentScopeID) {
      setScopeMismatch(true);
      select({ frameId: undefined, recordSequence: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
    }
		await requireCurrentTargetScope(response.targetScopeId ?? "", currentScopeID, refresh);
    return response;
	}, [currentScopeID, refresh, select]);
	const verifyScope = source === "TARGET" ? verifyTargetResponse : verifyImportedResponse;
	const evidenceScopeKey = source === "TARGET" ? currentScopeID : "";
  const plansRequestKey = `${traceId}:${source}:${evidenceScopeKey}:${source === "TARGET" ? scopeGeneration : 0}`;
  const plansRequestKeyRef = useRef(plansRequestKey);
  plansRequestKeyRef.current = plansRequestKey;
  const reportError = useCallback((value: unknown, artifactLookup = false) => {
    if (!mounted.current) return;
    const normalized = value instanceof Error ? value : new Error("The trace evidence could not be loaded.");
    const browserError = normalized instanceof BrowserAPIError || "code" in normalized ? normalized as BrowserAPIError : undefined;
    if (browserError?.code === "TARGET_CHANGED") {
      select({ frameId: undefined, recordSequence: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
    }
    if (browserError && (browserError.code === "ARTIFACT_EXPIRED" || (artifactLookup && browserError.code === "NOT_FOUND"))) {
      recordEpoch.current++;
      artifactUnavailable.current = true;
      select({ view: undefined, frameId: undefined, recordSequence: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
      setSummary(undefined);
      setFrames(undefined);
      setDetailedFrames(undefined);
      setTimelineStatus("idle");
      setRecords(undefined);
      setPlans(undefined);
      setRange(undefined);
      onArtifactUnavailable?.(browserError);
    }
    setError(normalized.message);
  }, [onArtifactUnavailable, select]);
  const begin = (key: string) => setPending((value) => new Set(value).add(key));
  const end = (key: string) => setPending((value) => { const next = new Set(value); next.delete(key); return next; });

  useEffect(() => {
    if (!state.valid) select({ view: undefined, frameId: undefined, recordSequence: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
  }, [select, state.valid]);
  useEffect(() => {
		if (!state.valid || (source === "TARGET" && !currentScopeID)) return;
    let stopped = false;
    setSummary(undefined);
    setFrames(undefined);
		setDetailedFrames(undefined);
    setTimelineStatus("idle");
    setRecords(undefined);
    setPlans(undefined);
    setFailures(undefined);
    setSearchResults(undefined);
    setUsage(undefined);
    setUsageFrames(undefined);
    setUsageResponseRecords(undefined);
    setRegisteredSkills(undefined);
    setRange(undefined);
    setRangeRequest(undefined);
    setRangeError(undefined);
    setError(undefined);
    setScopeMismatch(false);
		Promise.all([getTraceAnalysisSummary(traceId, source).then(verifyScope), getTraceFrames(traceId, undefined, {}, "CANONICAL", source, "COMPACT", 1000).then(verifyScope)])
      .then(([summaryResult, framePage]) => { if (!stopped) { setSummary(summaryResult); setFrames(framePage); } })
      .catch((value) => { if (!stopped) reportError(value, true); });
    return () => { stopped = true; };
	}, [evidenceScopeKey, reportError, source === "TARGET" ? scopeGeneration : 0, state.valid, traceId, verifyScope, source]);

  // Publication belongs to this keyed evidence component and its artifact epoch.
  const runRecordRequest = useCallback((key: string, work: (current: () => boolean) => Promise<void>) => {
    const epoch = recordEpoch.current;
    const current = () => mounted.current && !artifactUnavailable.current && recordEpoch.current === epoch;
    if (!current()) return;
    begin(key);
    void work(current).catch((value) => { if (current()) reportError(value, true); })
      .finally(() => { if (current()) end(key); });
  }, [reportError]);
  const verifyRecordScope = useCallback(async (page: { source: TraceSource; targetScopeId?: string }, current: () => boolean) => {
    if (!current()) return false;
    const matches = page.source === source && (source === "TARGET" ? page.targetScopeId === currentScopeID : !page.targetScopeId);
    if (!matches) {
      try { await verifyScope(page); }
      catch (value) { if (current()) reportError(value, true); }
      if (current()) { recordEpoch.current++; setRecords(undefined); }
      return false;
    }
    await verifyScope(page);
    return current();
  }, [currentScopeID, reportError, source, verifyScope]);
  const loadRecords = useCallback(() => {
    if (scopeMismatch || records || pending.has("record-facts") || artifactUnavailable.current) return;
    runRecordRequest("record-facts", async (current) => {
      const [recordPage, failurePage] = await Promise.all([
        getTraceRecords(traceId, undefined, recordFilter, source), getTraceFailures(traceId, undefined, source),
      ]);
      if (!current()) return;
      if (!await verifyRecordScope(recordPage, current) || !await verifyRecordScope(failurePage, current)) return;
      setRecords(recordPage);
      setFailures(failurePage);
    });
  }, [pending, recordFilter, records, runRecordRequest, scopeMismatch, source, traceId, verifyRecordScope]);
  const loadUsage = useCallback(() => {
    if (!scopeMismatch && !usage && !pending.has("usage")) {
      begin("usage");
      const loadResponseRecords = async () => {
        const items: TraceRecord[] = [];
        let cursor: string | undefined;
        do {
          const page = await getTraceRecords(traceId, cursor, { types: ["MODEL_RESPONSE_RECEIVED"] }, source).then(verifyScope);
          items.push(...page.items);
          cursor = page.hasMore && page.nextCursor ? page.nextCursor : undefined;
        } while (cursor);
        return items;
      };
      void Promise.all([
        getTraceUsage(traceId, source).then(verifyScope),
        getTraceFrames(traceId, undefined, {}, "USAGE_DESC", source, "DETAILED").then(verifyScope),
        loadResponseRecords(),
      ]).then(([usageResult, contributorPage, responseRecords]) => {
        setUsage(usageResult);
        setUsageFrames(contributorPage);
        setUsageResponseRecords(responseRecords);
      }).catch((value) => reportError(value, true)).finally(() => end("usage"));
    }
  }, [pending, reportError, scopeMismatch, source, traceId, usage, verifyScope]);
  const loadPlans = useCallback(() => {
    const pendingKey = `plans:${plansRequestKey}`;
    if (scopeMismatch || plans || pending.has(pendingKey)) return;
    begin(pendingKey);
    const loadAll = async () => {
      let cursor: string | undefined;
      const cursors = new Set<string>();
      let result: TraceAnalysisPage<PlanSummary> | undefined;
      do {
        const page = await getTracePlans(traceId, cursor, undefined, source);
        if (plansRequestKeyRef.current !== plansRequestKey) return;
        const verified = await verifyScope(page);
        if (plansRequestKeyRef.current !== plansRequestKey) return;
        result = result ? appendPage(result, verified) : verified;
        if (!verified.hasMore) break;
        if (!verified.nextCursor || cursors.has(verified.nextCursor)) throw new Error("Plan continuation was invalid.");
        cursor = verified.nextCursor;
        cursors.add(cursor);
      } while (true);
      setPlans(result);
    };
    void loadAll().catch((value) => {
      if (plansRequestKeyRef.current === plansRequestKey) reportError(value, true);
    }).finally(() => end(pendingKey));
  }, [pending, plans, plansRequestKey, reportError, scopeMismatch, source, traceId, verifyScope]);
  useEffect(() => setPlans(undefined), [plansRequestKey]);
  useEffect(() => {
    if (!plans || !state.planId) return;
    const exact = plans.items.find((plan) => plan.planId === state.planId);
    if (!exact) {
      select({ planId: undefined, transitionSequence: undefined });
      return;
    }
    if (state.transitionSequence !== undefined && !exact.transitions.some((transition) => transition.sequence === state.transitionSequence)) {
      select({ transitionSequence: undefined });
    }
  }, [plans, select, state.planId, state.transitionSequence]);
	const loadDetailedFrames = useCallback(() => {
		if (scopeMismatch || timelineStatus !== "idle" || pending.has("detailed-frames")) return;
		setTimelineStatus("loading");
		begin("detailed-frames");
		const loadAll = async () => {
      const items: TraceFrame[] = [];
      let cursor: string | undefined;
      let lastPage: TraceAnalysisPage<TraceFrame> | undefined;
      do {
        const page = await getTraceFrames(traceId, cursor, {}, "CANONICAL", source, "DETAILED", 1000).then(verifyScope);
        items.push(...page.items);
        lastPage = page;
        if (!page.hasMore) break;
        if (!page.nextCursor || page.nextCursor === cursor) throw new Error("Timeline continuation was invalid.");
        cursor = page.nextCursor;
      } while (true);
      if (!lastPage) return;
      setDetailedFrames({ ...lastPage, items, hasMore: false, nextCursor: null });
      setTimelineStatus("loaded");
    };
    void loadAll()
			.catch((value) => { setTimelineStatus("failed"); reportError(value, true); }).finally(() => end("detailed-frames"));
	}, [pending, reportError, scopeMismatch, source, timelineStatus, traceId, verifyScope]);
  useEffect(() => {
    if (!summary) return;
    if (state.view === "records") loadRecords();
    if (state.view === "usage") loadUsage();
		if (state.view === "timeline") loadDetailedFrames();
    if (state.view === "plans") loadPlans();
	}, [loadDetailedFrames, loadPlans, loadRecords, loadUsage, state.view, summary]);

  const effectiveFailureId = state.failureId
    ?? (state.frameId ? undefined : summary?.terminalFailureId)
    ?? undefined;
  useEffect(() => {
    if (!summary || !effectiveFailureId || failures || scopeMismatch || state.view === "records") return;
    begin("failure-focus");
    void getTraceFailures(traceId, undefined, source).then(verifyScope).then(setFailures)
      .catch((value) => reportError(value, true)).finally(() => end("failure-focus"));
  }, [effectiveFailureId, failures, reportError, scopeMismatch, state.view, summary, traceId, verifyScope]);

	const compactSelectedFrame = frames?.items.find((frame) => frame.frameId === state.frameId);
	const selectedFrame = detailedFrames?.items.find((frame) => frame.frameId === state.frameId)
		?? usageFrames?.items.find((frame) => frame.frameId === state.frameId)
		?? compactSelectedFrame;
  const selectedFailure = failures?.items.find((failure) => failure.failureId === effectiveFailureId);
  const selectFrame = useCallback((frameId: string) => {
    const frame = frames?.items.find((item) => item.frameId === frameId)
      ?? detailedFrames?.items.find((item) => item.frameId === frameId)
      ?? usageFrames?.items.find((item) => item.frameId === frameId);
    select({ frameId, failureId: frame?.failureIds?.[0] });
  }, [frames, detailedFrames, select, usageFrames]);
	useEffect(() => setRegisteredSkills(undefined), [source === "TARGET" ? scopeGeneration : 0, selectedFrame?.frameId, source]);
	useEffect(() => {
		if (scopeMismatch || !state.frameId || !compactSelectedFrame || detailedFrames?.items.some((frame) => frame.frameId === state.frameId) || pending.has("selected-frame-detail")) return;
		begin("selected-frame-detail");
		void getTraceFrames(traceId, undefined, { frameIds: [state.frameId] }, "CANONICAL", source, "DETAILED").then(verifyScope).then((page) => {
			setDetailedFrames((current) => current ? mergeFrames(current, page.items) : page);
		}).catch((value) => reportError(value, true)).finally(() => end("selected-frame-detail"));
	}, [compactSelectedFrame, detailedFrames, pending, reportError, scopeMismatch, source, state.frameId, traceId, verifyScope]);
  useEffect(() => {
    if (effectiveFailureId && selectedFailure?.frameId && selectedFailure.frameId !== state.frameId) {
      select({ frameId: selectedFailure.frameId, failureId: effectiveFailureId });
    }
  }, [effectiveFailureId, select, selectedFailure, state.frameId]);

  useEffect(() => {
	const names = selectedFrame?.skillNames ?? [];
	if (names.length === 0 || registeredSkills || scopeMismatch) return;
	if (source === "IMPORTED") {
		setRegisteredSkills(new Set());
		return;
	}
    let stopped = false;
    const load = async () => {
      const found = new Set<string>();
      let cursor: string | undefined;
      do {
		const page = await listSkills(cursor, 100);
		await requireCurrentTargetScope(page.targetScopeId, currentScopeID, refresh);
        for (const skill of page.items as SkillSummary[]) {
          if (names.includes(skill.registeredName)) found.add(skill.registeredName);
        }
        cursor = page.hasMore && page.nextCursor ? page.nextCursor : undefined;
      } while (cursor && found.size < names.length);
      if (!stopped) setRegisteredSkills(found);
    };
    void load().catch((value) => { if (!stopped) reportError(value); });
    return () => { stopped = true; };
	}, [currentScopeID, refresh, registeredSkills, reportError, scopeMismatch, selectedFrame, source]);
  const loadAncestry = useCallback(async (startingFrame: TraceFrame) => {
    const known = new Map((frames?.items ?? []).map((frame) => [frame.frameId, frame]));
    known.set(startingFrame.frameId, startingFrame);
    const added = [startingFrame];
    const visited = new Set<string>();
    let current: TraceFrame | undefined = startingFrame;
    while (current?.parentFrameId && !visited.has(current.parentFrameId)) {
      const parentFrameId: string = current.parentFrameId;
      visited.add(parentFrameId);
      let parent = known.get(parentFrameId);
      if (!parent) {
        const page: TraceAnalysisPage<TraceFrame> = await getTraceFrames(traceId, undefined, { frameIds: [parentFrameId] }, "CANONICAL", source).then(verifyScope);
        parent = page.items[0];
        if (!parent) break;
        known.set(parent.frameId, parent);
        added.push(parent);
      }
      current = parent;
    }
    setFrames((value) => mergeFrames(value, added));
    return startingFrame;
  }, [frames, source, traceId, verifyScope]);
  useEffect(() => {
    if (scopeMismatch || !frames || !state.frameId || selectedFrame || pending.has("deep-frame")) return;
    begin("deep-frame");
    void getTraceFrames(traceId, undefined, { frameIds: [state.frameId] }, "CANONICAL", source).then(verifyScope).then((page) => {
      const frame = page.items[0];
      if (frame) return loadAncestry(frame);
      select({ frameId: undefined, failureId: undefined });
      return undefined;
    }).catch((value) => reportError(value, true)).finally(() => end("deep-frame"));
  }, [frames, loadAncestry, pending, reportError, scopeMismatch, select, selectedFrame, state.frameId, traceId, verifyScope]);
  useEffect(() => {
    if (scopeMismatch || !records || !state.recordSequence || records.items.some((record) => record.sequence === state.recordSequence) || pending.has("deep-record")) return;
    runRecordRequest("deep-record", async (current) => {
      const page = await getTraceRecords(traceId, undefined, { minSequence: state.recordSequence, maxSequence: state.recordSequence }, source);
      if (!current()) return;
      if (!await verifyRecordScope(page, current)) return;
      const record = page.items[0];
      if (record) setRecords((previous) => previous ? { ...previous, items: [...previous.items, record] } : page);
      else select({ recordSequence: undefined });
    });
  }, [pending, records, runRecordRequest, scopeMismatch, select, source, state.recordSequence, traceId, verifyRecordScope]);
  useEffect(() => {
    if (!records || !state.recordSequence || state.frameId) return;
    const record = records.items.find((candidate) => candidate.sequence === state.recordSequence);
    if (record?.frameId) select({ recordSequence: record.sequence, frameId: record.frameId, failureId: undefined });
  }, [records, select, state.frameId, state.recordSequence]);
  useEffect(() => {
    if (!scopeMismatch && failures && state.failureId && !failures.items.some((failure) => failure.failureId === state.failureId) && !failures.hasMore) {
      select({ failureId: undefined });
    }
  }, [failures, scopeMismatch, select, state.failureId]);
  const breadcrumbs = useMemo(() => {
    if (!selectedFrame || !frames) return [];
    const byID = new Map(frames.items.map((frame) => [frame.frameId, frame]));
    const result: TraceFrame[] = [];
    const visited = new Set<string>();
    let current: TraceFrame | undefined = selectedFrame;
    while (current && !visited.has(current.frameId)) {
      visited.add(current.frameId);
      result.unshift(current);
      current = current.parentFrameId ? byID.get(current.parentFrameId) : undefined;
    }
    return result;
  }, [frames, selectedFrame]);
  const readContent = (contentRef: string, cursor?: string, recordSequence?: number) => {
    if (pending.has("range")) return;
    setRangeRequest({ contentRef, recordSequence });
    setRangeError(undefined);
    if (!cursor) setRange(undefined);
    begin("range");
    void getContentRange(traceId, contentRef, cursor, source).then(verifyScope).then((result) => {
      setRange(result);
    }).catch((value) => {
      setRangeError(value instanceof Error ? value.message : "The content could not be read.");
      reportError(value, false);
    }).finally(() => end("range"));
  };
  const nextRange = () => {
    if (!range?.hasMore || !range.nextCursor) return;
    if (rangeRequest) readContent(rangeRequest.contentRef, range.nextCursor, rangeRequest.recordSequence);
  };
  const clearRange = () => { setRange(undefined); setRangeRequest(undefined); setRangeError(undefined); };
  const rangeBelongsToRecord = rangeRequest?.recordSequence !== undefined;
  const search = () => {
    if (!searchText || pending.has("search")) return;
    begin("search");
    void searchTraceEvidence(traceId, searchText, undefined, source).then(verifyScope).then(setSearchResults).catch((value) => reportError(value, true)).finally(() => end("search"));
  };
  const loadMoreRecords = () => {
    if (!records?.hasMore || !records.nextCursor || pending.has("records")) return;
    const cursor = records.nextCursor;
    runRecordRequest("records", async (current) => {
      const page = await getTraceRecords(traceId, cursor, recordFilter, source);
      if (!current()) return;
      if (!await verifyRecordScope(page, current)) return;
      setRecords((previous) => previous ? { ...page, items: [...previous.items, ...page.items.filter((item) => !previous.items.some((old) => old.sequence === item.sequence))] } : page);
    });
  };
  useEffect(() => {
    if (!failures || !effectiveFailureId || failures.items.some((failure) => failure.failureId === effectiveFailureId) || !failures.hasMore || !failures.nextCursor || pending.has("deep-failure")) return;
    begin("deep-failure");
    void getTraceFailures(traceId, failures.nextCursor, source).then(verifyScope).then((next) => setFailures(appendPage(failures, next))).catch((value) => reportError(value, true)).finally(() => end("deep-failure"));
  }, [effectiveFailureId, failures, pending, reportError, source, traceId, verifyScope]);
  const handleTabKey = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let nextIndex: number | undefined;
    if (event.key === "ArrowRight" || event.key === "ArrowDown") nextIndex = (index + 1) % views.length;
    if (event.key === "ArrowLeft" || event.key === "ArrowUp") nextIndex = (index - 1 + views.length) % views.length;
    if (event.key === "Home") nextIndex = 0;
    if (event.key === "End") nextIndex = views.length - 1;
    if (nextIndex === undefined) return;
    event.preventDefault();
    const nextView = views[nextIndex];
    select({ view: nextView });
    const tabs = event.currentTarget.closest('[role="tablist"]')?.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    tabs?.[nextIndex]?.focus();
  };
  const [failureFocusRequest, setFailureFocusRequest] = useState<{ view: TraceExplorerView; token: number }>();
  const [failurePanelFocusRequest, setFailurePanelFocusRequest] = useState(0);
  const showFailureView = useCallback((view: TraceExplorerView) => {
    setFailureFocusRequest((current) => ({ view, token: (current?.token ?? 0) + 1 }));
    select({ view, failureId: effectiveFailureId });
  }, [effectiveFailureId, select]);
  const viewFailure = useCallback((failureId: string) => {
    select({ failureId });
    setFailurePanelFocusRequest((request) => request + 1);
  }, [select]);
  const viewRecord = useCallback((sequence: number) => {
    select({ view: "records", recordSequence: sequence, frameId: undefined, failureId: undefined, planId: undefined, transitionSequence: undefined });
  }, [select]);
  const viewPlan = useCallback((planId: string, transitionSequence?: number) => {
    select({ view: "plans", planId, transitionSequence, recordSequence: undefined, frameId: undefined, failureId: undefined });
  }, [select]);
  useEffect(() => {
    if (!failureFocusRequest || failureFocusRequest.view !== state.view) return;
    if (state.view === "timeline" && timelineStatus !== "loaded") return;
    const frameButton = state.view === "timeline" && state.frameId
      ? [...document.querySelectorAll<HTMLButtonElement>("button[data-frame]")]
        .find((button) => button.dataset.frame === state.frameId)
      : undefined;
    (frameButton ?? document.getElementById(`trace-panel-${state.view}`))?.focus();
    setFailureFocusRequest(undefined);
  }, [failureFocusRequest, detailedFrames, timelineStatus, state.frameId, state.view]);
  useEffect(() => {
    if (failurePanelFocusRequest === 0) return;
    const panel = document.getElementById("trace-failure-panel");
    if (!panel) return;
    panel.focus();
    setFailurePanelFocusRequest(0);
  }, [failurePanelFocusRequest, selectedFailure]);
  const hasFailurePanel = Boolean(((summary?.outcome === "FAILED" || summary?.outcome === "ABORTED") && summary.terminalFailureId) || selectedFailure);

  return <section className="trace-explorer" aria-labelledby="trace-explorer-title">
    <h3 id="trace-explorer-title">Trace explorer</h3>
    <div aria-live="polite" aria-atomic="true">{error && <p className="target-error" role="alert">{error}</p>}</div>
    {!summary ? <p role="status">Loading trace evidence&hellip;</p> : <>
      <p className="trace-explorer-summary">{summary.outcome} &middot; {summary.frameCount} frames &middot; {summary.recordCount} records{!summary.usageComplete && " · usage incomplete"}</p>
      {hasFailurePanel && <section id="trace-failure-panel" className="trace-failure-panel" aria-label="Trace failure details" tabIndex={-1}>
        <TraceFailureFocus summary={summary} failure={selectedFailure} frame={selectedFrame} onView={showFailureView} />
        <TraceFailureDiagnostic traceId={traceId} source={source} failure={selectedFailure} scopeGeneration={source === "TARGET" ? scopeGeneration : 0} verifyScope={verifyScope} />
      </section>}
      {breadcrumbs.length > 0 && <nav className="trace-breadcrumbs" aria-label="Selected frame breadcrumbs">{breadcrumbs.map((frame, index) => <span key={frame.frameId}>{index > 0 && " / "}<button type="button" onClick={() => selectFrame(frame.frameId)}>{frame.route || frame.frameId}</button></span>)}</nav>}
      {selectedFrame && <section className="trace-frame-skills" aria-labelledby="selected-frame-skills"><h4 id="selected-frame-skills">Recorded skill names</h4>{(selectedFrame.skillNames?.length ?? 0) === 0 ? <p className="trace-step-note">No recorded skill name is associated with this frame.</p> : <ul>{selectedFrame.skillNames.map((name) => <li key={name}>{registeredSkills?.has(name) ? <Link to={scopeBoundPath(`/skills/${encodeURIComponent(name)}`, currentScopeID)}>{name}</Link> : <><code>{name}</code> <span className="trace-step-note">not in current registered catalog</span></>}</li>)}</ul>}</section>}
      <div role="tablist" aria-label="Trace evidence views">{views.map((view, index) => <button id={`trace-tab-${view}`} aria-controls={`trace-panel-${view}`} key={view} type="button" role="tab" tabIndex={state.view === view ? 0 : -1} aria-selected={state.view === view} onKeyDown={(event) => handleTabKey(event, index)} onClick={() => select({ view })}>{view[0].toUpperCase() + view.slice(1)}</button>)}</div>
      <div id={`trace-panel-${state.view}`} className="trace-panel" role="tabpanel" aria-labelledby={`trace-tab-${state.view}`} tabIndex={0}>
		{state.view === "timeline" && (timelineStatus === "loaded"
          ? <TraceTimeline frames={detailedFrames?.items ?? []} selectedFrameId={state.frameId} onSelect={selectFrame} />
          : timelineStatus === "failed" ? <p>Timeline could not be loaded.</p> : <p role="status">Loading full timeline&hellip;</p>)}
        {state.view === "plans" && (plans
          ? <TracePlans plans={plans.items} selectedPlanId={state.planId} selectedTransitionSequence={state.transitionSequence} onRecord={viewRecord} onFailure={viewFailure} />
          : <p role="status">Loading plans&hellip;</p>)}
        {state.view === "usage" && <TraceUsage usage={usage} frame={selectedFrame} summary={summary} contributors={usageFrames?.items} responseRecords={usageResponseRecords} recordHref={(record) => {
          const target = setTraceExplorerSelection(params, { view: "records", frameId: record.frameId || undefined, recordSequence: record.sequence, failureId: undefined });
          return `?${target.toString()}`;
        }} />}
        {state.view === "records" && <>
          <form className="trace-search" onSubmit={(event) => { event.preventDefault(); search(); }}><label>Literal search <input value={searchText} onChange={(event) => setSearchText(event.target.value)} /></label><button type="submit" disabled={!searchText || pending.has("search")}>Search</button></form>
          <label className="trace-record-visibility"><input type="checkbox" checked={showAllRecords} disabled={pending.has("record-facts") || pending.has("records") || pending.has("deep-record")} onChange={(event) => { setRecords(undefined); setShowAllRecords(event.target.checked); }} /> Show all records</label>
          {searchResults && <section className="trace-search-results" aria-label="Literal search results"><div className="trace-search-results-header"><p role="status">{searchResults.items.length} literal matches</p><button type="button" disabled={pending.has("search") || pending.has("search-page")} onClick={() => setSearchResults(undefined)}>Close</button></div>{searchResults.search && <p className="trace-step-note">{searchResults.search.workComplete ? "Search complete" : "Search has more work"}; {searchResults.search.caseSensitive ? "case-sensitive" : "case-insensitive"}; fields {searchResults.search.searchedFields.join(", ")}{searchResults.search.limitations.map((value) => `; ${value.code}: ${value.message}`).join("")}</p>}<ol>{searchResults.items.map((match) => {
            const contentRef = match.contentId ? searchResults.contentDescriptors.find((descriptor) => descriptor.contentId === match.contentId)?.contentRef : undefined;
            return <li key={`${match.sequence}-${match.searchedField}-${match.matchOffset}`}><button type="button" onClick={() => select({ view: "records", recordSequence: match.sequence, frameId: match.frameId || undefined, failureId: undefined })}>{match.recordType} record {match.sequence}</button> &middot; {match.searchedField} bytes {match.matchOffset}&ndash;{match.matchOffset + match.matchLength}{contentRef && <> &middot; <button type="button" onClick={() => readContent(contentRef)}>Read match content</button></>}</li>;
          })}</ol>{searchResults.hasMore && <button type="button" disabled={pending.has("search-page")} onClick={() => {
            if (!searchResults.nextCursor || pending.has("search-page")) return;
            begin("search-page");
            void searchTraceEvidence(traceId, searchText, searchResults.nextCursor, source).then(verifyScope).then((next) => setSearchResults(appendSearchPage(searchResults, next))).catch((value) => reportError(value, true)).finally(() => end("search-page"));
          }}>Load more matches</button>}</section>}
          <TraceRecords onArtifactUnavailable={(value) => reportError(value, true)} traceId={traceId} source={source} scopeGeneration={source === "TARGET" ? scopeGeneration : 0} verifyScope={verifyScope} records={records?.items ?? []} frames={frames?.items ?? []} failures={failures?.items ?? []} selectedRecordSequence={state.recordSequence} selectedFailureId={state.failureId} activeContentRecordSequence={rangeRequest?.recordSequence} contentRange={range} contentPending={pending.has("range")} contentError={rangeError} onSelectRecord={(record) => select({ recordSequence: record.sequence, frameId: record.frameId || undefined, failureId: undefined })} onSelectFailure={viewFailure} onSelectPlan={viewPlan} onContent={(contentRef, recordSequence) => readContent(contentRef, undefined, recordSequence)} onNextContent={nextRange} onClearContent={clearRange} />
          <div className="trace-continuations" role="group" aria-label="Additional evidence pages">
            {records?.hasMore && <button type="button" disabled={pending.has("records")} onClick={loadMoreRecords}>Load more records</button>}
          </div>
        </>}
      </div>
      {!rangeBelongsToRecord && <TraceEvidenceDetail range={range} pending={pending.has("range")} error={rangeError} onNext={nextRange} onClear={clearRange} />}
    </>}
  </section>;
}
